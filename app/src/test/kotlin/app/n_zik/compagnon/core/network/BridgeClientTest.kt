package app.n_zik.compagnon.core.network

import app.n_zik.compagnon.bridge.pairing.ValidateRequest
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BridgeClientTest {

    private val address = ServerAddress("192.168.1.14", 42420)
    private val requests = mutableListOf<HttpRequestData>()

    private fun client(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) =
        BridgeClient(MockEngine { request -> requests += request; handler(request) })

    private fun MockRequestHandleScope.json(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
        respond(body, status, headersOf(HttpHeaders.ContentType, "application/json; charset=utf-8"))

    @Test
    fun `meta accepts any 1_x contract and ignores unknown fields`() = runTest {
        val result = client {
            json("""{"contractVersion":"1.7","serverName":"Pixel 8","serverTimeMs":1,"features":["pairing.qr"],"newField":{}}""")
        }.meta(address)
        assertTrue(result is MetaResult.Ok)
        assertEquals("http://192.168.1.14:42420/api/v1/meta", requests.single().url.toString())
    }

    @Test
    fun `meta with another major is incompatible`() = runTest {
        val result = client { json("""{"contractVersion":"2.0","serverName":"Pixel 8","serverTimeMs":1,"features":[]}""") }.meta(address)
        assertEquals(MetaResult.Incompatible("2.0"), result)
    }

    @Test
    fun `network failure is unreachable`() = runTest {
        assertEquals(MetaResult.Unreachable, client { throw IOException("refused") }.meta(address))
        assertEquals(ProbeResult.Unreachable, client { throw IOException("refused") }.probe(address, "token"))
    }

    @Test
    fun `validate sends code, nullable requestId and name, and returns the credential`() = runTest {
        val api = client {
            json("""{"deviceToken":"${"t".repeat(43)}","deviceId":"Ab3dE5fG7hI","serverName":"Pixel 8","serverPort":42420}""")
        }
        val result = api.validate(address, ValidateRequest(code = "K7M2QX", requestId = null, deviceName = "PC-SALON"))
        assertEquals("Ab3dE5fG7hI", (result as ValidateResult.Ok).response.deviceId)
        val body = String(requests.single().body.toByteArray(), Charsets.UTF_8)
        assertEquals("""{"code":"K7M2QX","requestId":null,"deviceName":"PC-SALON"}""", body)
        assertEquals("http://192.168.1.14:42420/api/v1/pairing/validate", requests.single().url.toString())
    }

    @Test
    fun `validate 403 is decided from the code`() = runTest {
        val result = client { json("""{"code":"PAIRING_REJECTED","message":"whatever"}""", HttpStatusCode.Forbidden) }
            .validate(address, ValidateRequest("K7M2QX", "rid", "PC"))
        assertEquals(ValidateResult.Rejected, result)
    }

    @Test
    fun `validate 429 carries retryAfterMs`() = runTest {
        val result = client { json("""{"code":"RATE_LIMITED","message":"slow down","retryAfterMs":42000}""", HttpStatusCode.TooManyRequests) }
            .validate(address, ValidateRequest("K7M2QX", null, "PC"))
        assertEquals(ValidateResult.RateLimited(42_000), result)
    }

    @Test
    fun `validate with an unknown code is a plain failure`() = runTest {
        val result = client { json("""{"code":"SERVER_STOPPING","message":"PAIRING_REJECTED"}""", HttpStatusCode.ServiceUnavailable) }
            .validate(address, ValidateRequest("K7M2QX", null, "PC"))
        assertEquals(ValidateResult.Failed(503, "SERVER_STOPPING"), result)
    }

    @Test
    fun `probe sends the bearer token to library songs with limit 1`() = runTest {
        val result = client { json("""{"items":[],"total":0,"offset":0,"limit":1}""") }.probe(address, "secret-token")
        assertEquals(ProbeResult.Ok, result)
        val request = requests.single()
        assertEquals("Bearer secret-token", request.headers[HttpHeaders.Authorization])
        assertEquals("/api/v1/library/songs", request.url.encodedPath)
        assertEquals("1", request.url.parameters["limit"])
    }

    @Test
    fun `probe maps 401 DEVICE_REVOKED, other 401 and 409`() = runTest {
        assertEquals(
            ProbeResult.Revoked,
            client { json("""{"code":"DEVICE_REVOKED","message":"x"}""", HttpStatusCode.Unauthorized) }.probe(address, "t"),
        )
        assertEquals(
            ProbeResult.Failed(401, "UNAUTHORIZED"),
            client { json("""{"code":"UNAUTHORIZED","message":"x"}""", HttpStatusCode.Unauthorized) }.probe(address, "t"),
        )
        assertEquals(
            ProbeResult.OtherActive("PC-BUREAU"),
            client {
                json(
                    """{"code":"CONFLICT_ACTIVE_CLIENT","message":"x","activeDevice":{"deviceId":"Zz9yX8wV7uT","deviceName":"PC-BUREAU"}}""",
                    HttpStatusCode.Conflict,
                )
            }.probe(address, "t"),
        )
    }
}
