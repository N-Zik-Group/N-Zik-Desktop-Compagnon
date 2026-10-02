package app.n_zik.compagnon.player

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import app.n_zik.compagnon.pairing.ServerAddress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.logging.Logger

/**
 * Track artwork through the phone (`GET /api/v1/artwork/{trackId}`, Bearer, contract §10): decoded to
 * an [ImageBitmap] and kept in an in-memory LRU of [maxEntries]. Nothing is ever written to disk.
 * A `404` is remembered as "no artwork". The phone relays online artwork from upstream, so a queue opening
 * asks for many at once: at most [maxConcurrent] requests run together, and a failed one (timeout, `5xx`) is
 * retried after [retryDelaysMs] before giving up; a failure is never cached, so a later call retries again.
 * Fetches run in [scope], owned by the loader: a caller that goes away (cancelled) never cancels the
 * fetch the other callers of the same track are waiting for.
 */
class ArtworkLoader(
    private val api: PlayerApi,
    private val address: ServerAddress,
    private val deviceToken: String,
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
    maxConcurrent: Int = DEFAULT_MAX_CONCURRENT,
    private val retryDelaysMs: List<Long> = DEFAULT_RETRY_DELAYS_MS,
    private val decode: (ByteArray) -> ImageBitmap? = ::decodeImage,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private val log = Logger.getLogger("ArtworkLoader")

    /** `null` value = known to have no artwork. Access order: the eldest entry is the least recently used. */
    private val cache = object : LinkedHashMap<String, ImageBitmap?>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap?>?): Boolean = size > maxEntries
    }
    private val inFlight = HashMap<String, Deferred<ImageBitmap?>>()
    private val permits = Semaphore(maxConcurrent)

    /** Cached image of [trackId] without any network call; `null` when absent or not loaded yet. */
    fun cached(trackId: String): ImageBitmap? = synchronized(cache) { cache[trackId] }

    /** Number of cached entries (tests). */
    val size: Int get() = synchronized(cache) { cache.size }

    suspend fun load(trackId: String): ImageBitmap? {
        val deferred = synchronized(cache) {
            if (cache.containsKey(trackId)) return cache[trackId]
            inFlight.getOrPut(trackId) { scope.async { fetch(trackId) } }
        }
        return deferred.await()
    }

    private suspend fun fetch(trackId: String): ImageBitmap? {
        var image: ImageBitmap? = null
        try {
            var cacheable = false
            when (val result = fetchWithRetries(trackId)) {
                is ArtworkResult.Ok -> {
                    image = decode(result.bytes)
                    cacheable = image != null
                    if (image == null) log.info("Artwork of a track could not be decoded")
                }
                ArtworkResult.NotFound -> cacheable = true
                is ArtworkResult.Failed, ArtworkResult.Unreachable -> Unit
            }
            synchronized(cache) { if (cacheable) cache[trackId] = image }
        } finally {
            synchronized(cache) { inFlight.remove(trackId) }
        }
        return image
    }

    /** One request at a time per permit; only `Unreachable` and `5xx` are retried, after each delay of [retryDelaysMs]. */
    private suspend fun fetchWithRetries(trackId: String): ArtworkResult {
        var result = permits.withPermit { api.artwork(address, deviceToken, trackId) }
        for (wait in retryDelaysMs) {
            val retryable = result == ArtworkResult.Unreachable || (result is ArtworkResult.Failed && result.status >= 500)
            if (!retryable) break
            delay(wait)
            result = permits.withPermit { api.artwork(address, deviceToken, trackId) }
        }
        return result
    }

    fun clear() = synchronized(cache) { cache.clear() }

    companion object {
        const val DEFAULT_MAX_ENTRIES = 64
        const val DEFAULT_MAX_CONCURRENT = 4
        val DEFAULT_RETRY_DELAYS_MS = listOf(1_000L, 3_000L)

        /** JPEG, PNG or WebP bytes → bitmap (Skia); `null` when undecodable. */
        fun decodeImage(bytes: ByteArray): ImageBitmap? =
            runCatching { org.jetbrains.skia.Image.makeFromEncoded(bytes).toComposeImageBitmap() }.getOrNull()
    }
}
