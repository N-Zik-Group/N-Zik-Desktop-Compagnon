package app.n_zik.compagnon.bridge.library

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import app.n_zik.compagnon.core.network.ArtworkKey
import app.n_zik.compagnon.core.network.ArtworkResult
import app.n_zik.compagnon.core.network.PlayerApi
import app.n_zik.compagnon.core.network.ServerAddress
import app.n_zik.compagnon.utils.coroutines.NzikDispatchers
import java.util.logging.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/**
 * Artwork through the phone (tracks, albums and artists, Bearer, contract §10), keyed by [ArtworkKey]: decoded to
 * an [ImageBitmap] and kept in an in-memory LRU of at most [maxEntries] entries and [maxBytes] of pixels
 * (width × height × 4 per image: the player's 1000 px and the details' 1200 px covers weigh much more than
 * a thumbnail). Nothing is ever written to disk.
 * A `404` is remembered as "no artwork". The phone relays online artwork from upstream, so a queue opening
 * asks for many at once: at most [maxConcurrent] requests run together, and a failed one (timeout, `5xx`) is
 * retried after [retryDelaysMs] before giving up; a failure is never cached, so a later call retries again.
 * Fetches run in [scope], owned by the loader: a caller that goes away (cancelled) never cancels the
 * fetch the other callers of the same artwork are waiting for.
 */
class ArtworkLoader(
    private val api: PlayerApi,
    private val address: ServerAddress,
    private val deviceToken: String,
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
    private val maxBytes: Long = DEFAULT_MAX_BYTES,
    maxConcurrent: Int = DEFAULT_MAX_CONCURRENT,
    private val retryDelaysMs: List<Long> = DEFAULT_RETRY_DELAYS_MS,
    private val decode: (ByteArray) -> ImageBitmap? = ::decodeImage,
    private val scope: CoroutineScope = NzikDispatchers.fireAndForget(NzikDispatchers.DATA),
) {
    private val log = Logger.getLogger("ArtworkLoader")

    /** `null` value = known to have no artwork. Access order: the eldest entry is the least recently used. */
    private val cache = LinkedHashMap<ArtworkKey, ImageBitmap?>(16, 0.75f, true)
    private var cachedBytes = 0L
    private val inFlight = HashMap<ArtworkKey, Deferred<ImageBitmap?>>()
    private val permits = Semaphore(maxConcurrent)

    /** Cached image of [key] without any network call; `null` when absent or not loaded yet. */
    fun cached(key: ArtworkKey): ImageBitmap? = synchronized(cache) { cache[key] }

    /** Number of cached entries (tests). */
    val size: Int get() = synchronized(cache) { cache.size }

    /** Pixel bytes of the cached images (tests). */
    val bytes: Long get() = synchronized(cache) { cachedBytes }

    /** Caches [image] for [key], then evicts the least recently used entries until both bounds hold. Under the lock. */
    private fun put(key: ArtworkKey, image: ImageBitmap?) {
        cache.put(key, image)?.let { cachedBytes -= bytesOf(it) }
        cachedBytes += bytesOf(image)
        val entries = cache.entries.iterator()
        while ((cache.size > maxEntries || cachedBytes > maxBytes) && entries.hasNext()) {
            val eldest = entries.next()
            if (eldest.key == key && cache.size == 1) break
            cachedBytes -= bytesOf(eldest.value)
            entries.remove()
        }
    }

    suspend fun load(key: ArtworkKey): ImageBitmap? {
        val deferred = synchronized(cache) {
            if (cache.containsKey(key)) return cache[key]
            inFlight.getOrPut(key) { scope.async { fetch(key) } }
        }
        return deferred.await()
    }

    private suspend fun fetch(key: ArtworkKey): ImageBitmap? {
        var image: ImageBitmap? = null
        try {
            var cacheable = false
            when (val result = fetchWithRetries(key)) {
                is ArtworkResult.Ok -> {
                    // Skia decode is CPU-bound bitmap work: off the data pool, on the media pool.
                    image = withContext(NzikDispatchers.MEDIA) { decode(result.bytes) }
                    cacheable = image != null
                    if (image == null) log.info("Artwork (${key.kind}) could not be decoded")
                }
                ArtworkResult.NotFound -> cacheable = true
                is ArtworkResult.Failed, ArtworkResult.Unreachable -> Unit
            }
            synchronized(cache) { if (cacheable) put(key, image) }
        } finally {
            synchronized(cache) { inFlight.remove(key) }
        }
        return image
    }

    /** One request at a time per permit; only `Unreachable` and `5xx` are retried, after each delay of [retryDelaysMs]. */
    private suspend fun fetchWithRetries(key: ArtworkKey): ArtworkResult {
        var result = permits.withPermit { api.artwork(address, deviceToken, key) }
        for (wait in retryDelaysMs) {
            val retryable = result == ArtworkResult.Unreachable || (result is ArtworkResult.Failed && result.status >= 500)
            if (!retryable) break
            delay(wait)
            result = permits.withPermit { api.artwork(address, deviceToken, key) }
        }
        return result
    }

    fun clear() = synchronized(cache) {
        cache.clear()
        cachedBytes = 0L
    }

    companion object {
        const val DEFAULT_MAX_ENTRIES = 256

        /** 192 MB of pixels. */
        const val DEFAULT_MAX_BYTES = 192L * 1024 * 1024

        /** Pixel bytes of an image (ARGB, 4 bytes per pixel); `0` for "no artwork". */
        fun bytesOf(image: ImageBitmap?): Long = if (image == null) 0L else image.width.toLong() * image.height * 4
        const val DEFAULT_MAX_CONCURRENT = 4
        val DEFAULT_RETRY_DELAYS_MS = listOf(1_000L, 3_000L)

        /** JPEG, PNG or WebP bytes → bitmap (Skia); `null` when undecodable. */
        fun decodeImage(bytes: ByteArray): ImageBitmap? =
            runCatching { org.jetbrains.skia.Image.makeFromEncoded(bytes).toComposeImageBitmap() }.getOrNull()
    }
}
