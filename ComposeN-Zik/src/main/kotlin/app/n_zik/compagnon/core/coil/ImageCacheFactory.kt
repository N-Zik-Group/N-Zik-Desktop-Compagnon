package app.n_zik.compagnon.core.coil

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import app.n_zik.compagnon.LocalPlayerRepository
import app.n_zik.compagnon.bridge.state.SessionContract
import app.n_zik.compagnon.core.network.ArtworkKey
import app.n_zik.compagnon.generated.resources.*
import app.n_zik.compagnon.thumbnailShape
import app.n_zik.compagnon.utils.coroutines.NzikDispatchers
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.engine.cio.endpoint
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
import java.util.logging.Logger
import javax.imageio.ImageIO
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.painterResource

/**
 * Port of `ImageCacheFactory.Thumbnail` (phone's `app/n_zik/android/core/coil/ImageCacheFactory.kt` 315).
 * The image comes from the phone (contract §10, never from Internet), through the player's in-memory
 * artwork loader. As on the phone: the `loader` drawable while loading, the image, or `ic_launcher_box`
 * when there is none ([key] `null`, `404`, no `artwork` feature). The phone's URL rewriting, disk cache
 * and YouTube fallbacks do not apply.
 */
object ImageCacheFactory {

    @Composable
    fun Thumbnail(
        key: ArtworkKey?,
        contentDescription: String? = null,
        contentScale: ContentScale = ContentScale.Crop,
        modifier: Modifier = Modifier.clip(thumbnailShape()).fillMaxSize(),
    ) {
        val repository = LocalPlayerRepository.current
        val enabled = repository != null && key != null && SessionContract.FEATURE_ARTWORK in repository.features
        var image by remember(key) { mutableStateOf<ImageBitmap?>(if (enabled) repository.cachedArtwork(key) else null) }
        var loading by remember(key) { mutableStateOf(enabled && image == null) }
        if (enabled && image == null) {
            LaunchedEffect(key) {
                image = repository.artwork(key)
                loading = false
            }
        }

        val bitmap = image
        when {
            bitmap != null -> Image(
                bitmap = bitmap,
                contentDescription = contentDescription,
                contentScale = contentScale,
                modifier = modifier,
            )
            loading -> Image(
                painter = painterResource(Res.drawable.loader),
                contentDescription = contentDescription,
                contentScale = contentScale,
                modifier = modifier.fillMaxSize(),
            )
            else -> Image(
                painter = painterResource(Res.drawable.ic_launcher_box),
                contentDescription = contentDescription,
                contentScale = contentScale,
                modifier = modifier.fillMaxSize(),
            )
        }
    }

    /**
     * Port of `ImageCacheFactory.Painter` (phone's `ImageCacheFactory.kt` 403): the image of [key] as a
     * painter, with the phone's `error` / `fallback` (`ic_launcher_box`) while there is none and no
     * `placeholder` (the phone's default). [onError] / [onSuccess] are called once the load is over.
     */
    @Composable
    fun Painter(
        key: ArtworkKey?,
        onSuccess: (() -> Unit)? = null,
        onError: (() -> Unit)? = null,
    ): Painter {
        val repository = LocalPlayerRepository.current
        val enabled = repository != null && key != null && SessionContract.FEATURE_ARTWORK in repository.features
        var image by remember(key) { mutableStateOf<ImageBitmap?>(if (enabled) repository.cachedArtwork(key) else null) }
        LaunchedEffect(key, enabled) {
            if (!enabled) {
                onError?.invoke()
                return@LaunchedEffect
            }
            val loaded = image ?: repository.artwork(key)
            image = loaded
            if (loaded != null) onSuccess?.invoke() else onError?.invoke()
        }
        val bitmap = image
        val fallback = painterResource(Res.drawable.ic_launcher_box)
        return remember(bitmap, fallback) { if (bitmap != null) BitmapPainter(bitmap) else fallback }
    }

    /**
     * The external-URL avatar painter (spec `spec-updater` AD-10 — the About page's contributors'
     * avatars, `avatars.githubusercontent.com`): unlike [Thumbnail] / [Painter] (the phone's bridge
     * artwork), this loads a PUBLIC HTTP URL. Ktor CIO GET → `ImageIO.read` → `ImageBitmap`, a small
     * in-memory cache shared by all the cards (one fetch per avatar, process lifetime).
     *
     * The fallbacks are the phone's Coil `error` / `fallback` defaults, de-Androided to the desktop's
     * own [Thumbnail] convention: [ic_launcher_box] IMMEDIATELY for a null/blank URL (no loader
     * delay — the desktop's thumbnail rule), the [loader] while loading, and [ic_launcher_box] on
     * failure (one attempt per URL — no retry, the card keeps its name, the avatar is never a hole).
     */
    @Composable
    fun ExternalAvatarPainter(url: String?): Painter {
        // The fallback (the phone's Coil error/fallback, the desktop's Thumbnail convention)
        val fallback = painterResource(Res.drawable.ic_launcher_box)
        if (url.isNullOrBlank()) return fallback
        var bitmap by remember(url) { mutableStateOf<ImageBitmap?>(AVATAR_CACHE[url]) }
        // `finished` — a failed fetch must not retry on recomposition (the fallback sticks)
        var finished by remember(url) { mutableStateOf(bitmap != null) }
        LaunchedEffect(url) {
            if (!finished) {
                bitmap = withContext(NzikDispatchers.DATA) { fetchAvatar(url) }
                finished = true
            }
        }
        // A local capture — a smart cast on the delegated `bitmap` property is not allowed in
        // the `remember` lambda
        val current = bitmap
        val loading = painterResource(Res.drawable.loader)
        return remember(current, finished, loading, fallback) {
            when {
                current != null -> BitmapPainter(current)
                finished -> fallback
                else -> loading
            }
        }
    }

    /** The avatar cache: one `ImageBitmap` per URL, never evicted (a few dozen avatars at most). */
    private val AVATAR_CACHE = HashMap<String, ImageBitmap>()

    /** The avatars' HTTP client (process lifetime, bounded timeouts — the avatars are small). */
    private val avatarClient: HttpClient by lazy {
        HttpClient(CIO.create {
            requestTimeout = 30_000L
            endpoint {
                connectTimeout = 5_000L
                socketTimeout = 30_000L
            }
        })
    }

    private suspend fun fetchAvatar(url: String): ImageBitmap? = run {
        val cached = synchronized(AVATAR_CACHE) { AVATAR_CACHE[url] }
        if (cached != null) return@run cached
        val bitmap = try {
            val response = avatarClient.get(url)
            if (response.status.value !in 200..299) null
            else ImageIO.read(response.bodyAsBytes().inputStream())?.toComposeImageBitmap()
        } catch (e: Exception) {
            Logger.getLogger("ImageCacheFactory")
                .warning("Avatar fetch failed ($url): ${e::class.simpleName} (${e.message})")
            null
        }
        if (bitmap != null) synchronized(AVATAR_CACHE) { AVATAR_CACHE[url] = bitmap }
        bitmap
    }
}
