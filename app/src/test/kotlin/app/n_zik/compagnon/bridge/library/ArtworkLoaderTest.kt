package app.n_zik.compagnon.bridge.library

import androidx.compose.ui.graphics.ImageBitmap
import app.n_zik.compagnon.core.network.ArtworkKey
import app.n_zik.compagnon.core.network.BridgeClient
import app.n_zik.compagnon.core.network.ServerAddress
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.decodeURLPart
import io.ktor.http.headersOf
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ArtworkLoaderTest {

    private val token = "T".repeat(43)
    private val address = ServerAddress("192.168.1.14", 42420)
    private val requests = mutableListOf<HttpRequestData>()
    private val bitmaps = mutableMapOf<String, ImageBitmap>()

    /** Stands in for Skia: one distinct bitmap per payload. */
    private fun fakeDecode(bytes: ByteArray): ImageBitmap? {
        val key = String(bytes)
        if (key == "garbage") return null
        return bitmaps.getOrPut(key) { ImageBitmap(1, 1) }
    }

    private fun MockRequestHandleScope.image(bytes: ByteArray) =
        respond(bytes, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "image/jpeg"))

    private fun MockRequestHandleScope.error(code: String, status: HttpStatusCode) =
        respond("""{"code":"$code","message":"x"}""", status, headersOf(HttpHeaders.ContentType, "application/json"))

    /** Fetches run in the test's background scope: retry delays stay virtual. */
    private fun TestScope.loader(
        maxEntries: Int = 64,
        maxBytes: Long = ArtworkLoader.DEFAULT_MAX_BYTES,
        decode: (ByteArray) -> ImageBitmap? = ::fakeDecode,
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ) = ArtworkLoader(
        api = BridgeClient(MockEngine { request -> requests += request; handler(request) }),
        address = address,
        deviceToken = token,
        maxEntries = maxEntries,
        maxBytes = maxBytes,
        decode = decode,
        scope = backgroundScope,
    )

    private fun trackIdOf(request: HttpRequestData) = request.url.encodedPath.substringAfterLast('/').decodeURLPart()

    private fun k(trackId: String) = ArtworkKey.track(trackId)

    @Test
    fun `fetches with the Bearer, percent-encodes the id and caches in memory`() = runTest {
        val loader = loader { request -> image(trackIdOf(request).toByteArray()) }
        val first = loader.load(k("local:42/x y"))
        assertNotNull(first)
        val request = requests.single()
        assertEquals("Bearer $token", request.headers[HttpHeaders.Authorization])
        assertEquals("local:42/x y", trackIdOf(request))
        assertEquals("/api/v1/artwork/", request.url.encodedPath.substringBeforeLast('/') + "/")
        assertSame(first, loader.load(k("local:42/x y")))
        assertSame(first, loader.cached(k("local:42/x y")))
        assertEquals(1, requests.size)
    }

    @Test
    fun `each kind has its own route and its own cache entry`() = runTest {
        val loader = loader { request -> image(request.url.encodedPath.toByteArray()) }
        val track = loader.load(ArtworkKey.track("MPREb_x/y"))
        val album = loader.load(ArtworkKey.album("MPREb_x/y"))
        val artist = loader.load(ArtworkKey.artist("UC x"))
        assertEquals(
            listOf("/api/v1/artwork/MPREb_x%2Fy", "/api/v1/library/albums/MPREb_x%2Fy/artwork", "/api/v1/library/artists/UC%20x/artwork"),
            requests.map { it.url.encodedPath },
        )
        requests.forEach { assertEquals("544", it.url.parameters["size"]) }
        assertNotNull(track)
        assertNotNull(album)
        assertNotNull(artist)
        assertTrue(track !== album, "same id, two kinds: two images")
        assertSame(album, loader.cached(ArtworkKey.album("MPREb_x/y")))
        assertNull(loader.cached(ArtworkKey.artist("MPREb_x/y")))
        assertEquals(3, loader.size)
        loader.load(ArtworkKey.album("MPREb_x/y"))
        assertEquals(3, requests.size)
    }

    @Test
    fun `the size asked is part of the key, bounded to 64-1200`() = runTest {
        val loader = loader { request -> image((request.url.encodedPath + request.url.parameters["size"]).toByteArray()) }
        val small = loader.load(ArtworkKey.track("a", 128))
        val large = loader.load(ArtworkKey.track("a", 1200))
        assertTrue(small !== large, "same track, two sizes: two images")
        assertEquals(listOf("128", "1200"), requests.map { it.url.parameters["size"] })
        loader.load(ArtworkKey.album("b", 5000))
        loader.load(ArtworkKey.artist("c", 10))
        assertEquals(listOf("1200", "64"), requests.drop(2).map { it.url.parameters["size"] })
        assertEquals(1200, ArtworkKey.album("b", 5000).size)
        loader.load(ArtworkKey.track("a", 128))
        assertEquals(4, requests.size)
    }

    @Test
    fun `the cache holds 256 artworks by default`() {
        assertEquals(256, ArtworkLoader.DEFAULT_MAX_ENTRIES)
    }

    @Test
    fun `least recently used entries are evicted`() = runTest {
        val loader = loader(maxEntries = 2) { request -> image(trackIdOf(request).toByteArray()) }
        loader.load(k("a"))
        loader.load(k("b"))
        loader.load(k("a")) // a is now the most recent
        loader.load(k("c")) // evicts b
        assertEquals(2, loader.size)
        assertNotNull(loader.cached(k("a")))
        assertNull(loader.cached(k("b")))
        loader.load(k("b"))
        assertEquals(4, requests.size)
    }

    @Test
    fun `404 is remembered as no artwork, other failures are retried`() = runTest {
        var status = HttpStatusCode.NotFound
        val loader = loader { if (status == HttpStatusCode.NotFound) error("NOT_FOUND", status) else error("AUDIO_UPSTREAM_FAILED", status) }
        assertNull(loader.load(k("gone")))
        assertNull(loader.load(k("gone")))
        assertEquals(1, requests.size)

        status = HttpStatusCode.BadGateway
        assertNull(loader.load(k("flaky")))
        assertNull(loader.load(k("flaky")))
        // Each failed load = 1 try + {ArtworkLoader.DEFAULT_RETRY_DELAYS_MS.size} retries, and a failure is never cached
        assertEquals(1 + 2 * (1 + ArtworkLoader.DEFAULT_RETRY_DELAYS_MS.size), requests.size)
    }

    @Test
    fun `4xx answers other than 404 are not retried`() = runTest {
        val loader = loader { error("BAD_REQUEST", HttpStatusCode.BadRequest) }
        assertNull(loader.load(k("bad")))
        assertEquals(1, requests.size)
    }

    @Test
    fun `a cancelled caller does not cancel the fetch another caller waits for`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val loader = loader { request ->
            gate.await()
            image(trackIdOf(request).toByteArray())
        }
        val first = async { loader.load(k("shared")) }
        runCurrent()
        val second = async { loader.load(k("shared")) }
        runCurrent()
        first.cancel()
        runCurrent()
        gate.complete(Unit)
        assertNotNull(second.await())
        assertNotNull(loader.cached(k("shared")))
        assertEquals(1, requests.size)
    }

    @Test
    fun `undecodable bytes are not cached`() = runTest {
        val loader = loader { image("garbage".toByteArray()) }
        assertNull(loader.load(k("x")))
        assertNull(loader.load(k("x")))
        assertEquals(2, requests.size)
    }

    @Test
    fun `concurrent loads of the same track share one request`() = runTest {
        val loader = loader { request -> image(trackIdOf(request).toByteArray()) }
        val results = List(5) { async { loader.load(k("same")) } }.awaitAll()
        assertEquals(1, requests.size)
        results.forEach { assertSame(results.first(), it) }
    }

    @Test
    fun `real PNG bytes decode to a bitmap`() = runTest {
        val png = ByteArrayOutputStream().also { ImageIO.write(BufferedImage(4, 3, BufferedImage.TYPE_INT_RGB), "png", it) }.toByteArray()
        val loader = loader(decode = ArtworkLoader::decodeImage) { image(png) }
        val bitmap = loader.load(k("p"))!!
        assertEquals(4, bitmap.width)
        assertEquals(3, bitmap.height)
    }

    @Test
    fun `a failed request is retried and the image arrives`() = runTest {
        var calls = 0
        val loader = loader { request ->
            calls++
            if (calls < 3) error("AUDIO_UPSTREAM_FAILED", HttpStatusCode.BadGateway) else image(trackIdOf(request).toByteArray())
        }
        assertNotNull(loader.load(k("abc")))
        assertEquals(3, calls)
    }

    @Test
    fun `at most four artwork requests run at the same time`() = runTest {
        // The mock engine answers on several threads: atomic counters
        val running = java.util.concurrent.atomic.AtomicInteger()
        val peak = java.util.concurrent.atomic.AtomicInteger()
        val loader = loader { request ->
            peak.accumulateAndGet(running.incrementAndGet(), ::maxOf)
            kotlinx.coroutines.delay(100)
            running.decrementAndGet()
            image(trackIdOf(request).toByteArray())
        }
        (1..12).map { i -> async { loader.load(k("t$i")) } }.awaitAll()
        assertEquals(ArtworkLoader.DEFAULT_MAX_CONCURRENT, peak.get())
    }

    @Test
    fun `the cache is also bounded by pixel bytes, the least recently used going first`() = runTest {
        // 10 × 10 images: 400 bytes each; room for two
        val loader = loader(maxBytes = 900, decode = { ImageBitmap(10, 10) }) { request -> image(trackIdOf(request).toByteArray()) }
        loader.load(k("a"))
        loader.load(k("b"))
        assertEquals(800L, loader.bytes)
        loader.load(k("a")) // a is now the most recently used
        loader.load(k("c"))
        assertEquals(2, loader.size)
        assertEquals(800L, loader.bytes)
        assertNull(loader.cached(k("b")))
        assertNotNull(loader.cached(k("a")))
        assertNotNull(loader.cached(k("c")))
    }
}
