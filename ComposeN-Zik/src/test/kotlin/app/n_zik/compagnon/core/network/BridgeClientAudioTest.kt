package app.n_zik.compagnon.core.network

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/** The audio routes of contract §8 against a fake 1.2 phone. */
class BridgeClientAudioTest {

    @TempDir
    lateinit var dir: Path

    private val address = ServerAddress("192.168.1.14", 42420)
    private val token = "T".repeat(43)
    private val url = "http://192.168.1.14:42420/api/v1/audio/dQw4w9WgXcQ?t=eyJsigned"
    private val requests = mutableListOf<HttpRequestData>()

    private fun client(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) =
        BridgeClient(MockEngine { request -> requests += request; handler(request) })

    private fun MockRequestHandleScope.json(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
        respond(body, status, headersOf(HttpHeaders.ContentType, "application/json; charset=utf-8"))

    private fun MockRequestHandleScope.error(code: String, status: HttpStatusCode) = json("""{"code":"$code","message":"x"}""", status)

    @Test
    fun `forge posts the quality with the Bearer and an encoded id`() = runTest {
        val result = client {
            json("""{"trackId":"local:42","quality":"high","url":"$url","expiresAtMs":1790000432000,"durationMs":212000,"extra":1}""")
        }.forgeAudioUrl(address, token, "local:42", "high")
        val ok = result as ForgeResult.Ok
        assertEquals(url, ok.response.url)
        assertFalse(ok.response.toString().contains("eyJsigned"))
        val request = requests.single()
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("http://192.168.1.14:42420/api/v1/audio/local:42/url", request.url.toString())
        assertEquals("Bearer $token", request.headers[HttpHeaders.Authorization])
        assertEquals("""{"quality":"high"}""", String(request.body.toByteArray(), Charsets.UTF_8))
    }

    @Test
    fun `forge errors keep their code, an unreachable phone is unreachable`() = runTest {
        val notFound = client { error("NOT_FOUND", HttpStatusCode.NotFound) }.forgeAudioUrl(address, token, "x", "auto")
        assertEquals("NOT_FOUND", (notFound as ForgeResult.Error).error?.code)
        assertEquals(ForgeResult.Unreachable, client { throw IOException("refused") }.forgeAudioUrl(address, token, "x", "auto"))
    }

    @Test
    fun `probe is a HEAD without any Bearer, decided from the status`() = runTest {
        assertEquals(AudioProbe.Ok, client { respond("", HttpStatusCode.OK) }.probeAudioUrl(url))
        assertEquals(HttpMethod.Head, requests.single().method)
        assertEquals(null, requests.single().headers[HttpHeaders.Authorization])
        assertEquals(AudioProbe.Revoked, client { respond("", HttpStatusCode.Unauthorized) }.probeAudioUrl(url))
        assertEquals(AudioProbe.NotFound, client { respond("", HttpStatusCode.NotFound) }.probeAudioUrl(url))
        assertEquals(AudioProbe.UpstreamFailed, client { respond("", HttpStatusCode.BadGateway) }.probeAudioUrl(url))
        assertEquals(AudioProbe.Failed(503), client { respond("", HttpStatusCode.ServiceUnavailable) }.probeAudioUrl(url))
        assertEquals(AudioProbe.Unreachable, client { throw IOException("refused") }.probeAudioUrl(url))
    }

    @Test
    fun `a 403 is read again as a one-byte GET for its code`() = runTest {
        val expired = client { request ->
            if (request.method == HttpMethod.Head) respond("", HttpStatusCode.Forbidden)
            else error("AUDIO_URL_EXPIRED", HttpStatusCode.Forbidden)
        }.probeAudioUrl(url)
        assertEquals(AudioProbe.Expired, expired)
        assertEquals(HttpMethod.Get, requests.last().method)
        assertEquals("bytes=0-0", requests.last().headers[HttpHeaders.Range])

        val invalid = client { request ->
            if (request.method == HttpMethod.Head) respond("", HttpStatusCode.Forbidden)
            else error("AUDIO_URL_INVALID", HttpStatusCode.Forbidden)
        }.probeAudioUrl(url)
        assertEquals(AudioProbe.Invalid, invalid)

        val unreadable = client { request ->
            if (request.method == HttpMethod.Head) respond("", HttpStatusCode.Forbidden) else respond("nope", HttpStatusCode.Forbidden)
        }.probeAudioUrl(url)
        assertEquals(AudioProbe.Invalid, unreadable)
    }

    @Test
    fun `download writes the whole body, a wrong answer writes nothing usable`() = runTest {
        val target = dir.resolve("a.part")
        val body = ByteArray(200_000) { (it % 251).toByte() }
        val done = client {
            respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentLength, body.size.toString()))
        }.downloadAudio(url, target)
        assertEquals(DownloadResult.Done(body.size.toLong()), done)
        assertTrue(Files.readAllBytes(target).contentEquals(body))

        val refused = client { respond("", HttpStatusCode.Forbidden) }.downloadAudio(url, dir.resolve("b.part"))
        assertEquals(DownloadResult.Failed(403), refused)
        assertEquals(DownloadResult.Failed(null), client { throw IOException("reset") }.downloadAudio(url, dir.resolve("c.part")))
    }
}
