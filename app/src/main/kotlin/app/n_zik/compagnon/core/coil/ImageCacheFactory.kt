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
import androidx.compose.ui.layout.ContentScale
import app.n_zik.compagnon.LocalPlayerRepository
import app.n_zik.compagnon.bridge.state.SessionContract
import app.n_zik.compagnon.core.network.ArtworkKey
import app.n_zik.compagnon.generated.resources.Res
import app.n_zik.compagnon.generated.resources.ic_launcher_box
import app.n_zik.compagnon.generated.resources.loader
import app.n_zik.compagnon.thumbnailShape
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
}
