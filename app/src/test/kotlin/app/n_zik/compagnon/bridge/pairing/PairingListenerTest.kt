package app.n_zik.compagnon.bridge.pairing

import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PairingListenerTest {

    private val clock = AtomicLong(0)
    private var nextId = 0
    private val registry = PairingRequestRegistry(clock = clock::get, idGenerator = { "request-${nextId++}" })
    private val accepted = mutableListOf<PairingOffer>()

    private fun offerJson(requestId: String, v: Int = 1) =
        """{"v":$v,"requestId":"$requestId","code":"K7M2QX","serverIps":["192.168.1.14"],"serverPort":42420,"serverName":"Pixel 8","extra":true}"""

    private fun listenerTest(block: suspend io.ktor.server.testing.ApplicationTestBuilder.() -> Unit) = testApplication {
        application { pairingOfferModule(registry) { accepted += it } }
        block()
    }

    private suspend fun io.ktor.server.testing.ApplicationTestBuilder.postOffer(body: String) =
        client.post(BridgeContract.OFFER_PATH) {
            contentType(ContentType.Application.Json)
            setBody(body)
        }

    @Test
    fun `valid offer is accepted exactly once, then 410`() = listenerTest {
        val request = registry.rotate()!!
        val first = postOffer(offerJson(request.requestId))
        assertEquals(HttpStatusCode.OK, first.status)
        assertEquals(true, Json.parseToJsonElement(first.bodyAsText()).jsonObject["accepted"]?.jsonPrimitive?.content?.toBoolean())
        assertEquals(1, accepted.size)
        assertEquals("K7M2QX", accepted.single().code)
        assertEquals(listOf("192.168.1.14"), accepted.single().serverIps)

        val replay = postOffer(offerJson(request.requestId))
        assertEquals(HttpStatusCode.Gone, replay.status)
        assertTrue(replay.bodyAsText().contains("PAIRING_REQUEST_EXPIRED"))
        assertEquals(1, accepted.size)
    }

    @Test
    fun `unknown requestId is 410`() = listenerTest {
        registry.rotate()
        val response = postOffer(offerJson("not-the-current-one"))
        assertEquals(HttpStatusCode.Gone, response.status)
        assertTrue(accepted.isEmpty())
    }

    @Test
    fun `expired requestId is 410`() = listenerTest {
        val request = registry.rotate()!!
        clock.set(BridgeContract.REQUEST_TTL_MS)
        val response = postOffer(offerJson(request.requestId))
        assertEquals(HttpStatusCode.Gone, response.status)
        assertTrue(accepted.isEmpty())
    }

    @Test
    fun `invalid bodies are 400`() = listenerTest {
        val request = registry.rotate()!!
        val bodies = listOf(
            "not json",
            """{"v":1,"requestId":"${request.requestId}"}""",
            offerJson(request.requestId, v = 2),
            """{"v":1,"requestId":"${request.requestId}","code":"K7M2QX","serverIps":[],"serverPort":42420,"serverName":"Pixel"}""",
            """{"v":1,"requestId":"${request.requestId}","code":"K7M2QX","serverIps":["192.168.1.14"],"serverPort":0,"serverName":"Pixel"}""",
        )
        for (body in bodies) {
            val response = postOffer(body)
            assertEquals(HttpStatusCode.BadRequest, response.status, body)
            assertTrue(response.bodyAsText().contains("BAD_REQUEST"))
        }
        // The request id was not consumed by the bad bodies.
        assertEquals(HttpStatusCode.OK, postOffer(offerJson(request.requestId)).status)
    }

    @Test
    fun `any other route or method is 404`() = listenerTest {
        registry.rotate()
        assertEquals(HttpStatusCode.NotFound, client.get(BridgeContract.OFFER_PATH).status)
        assertEquals(HttpStatusCode.NotFound, client.get("/").status)
        assertEquals(HttpStatusCode.NotFound, client.post("/api/v1/meta").status)
    }

    @Test
    fun `rotation at TTL makes the previous requestId 410 and the new one valid`() = listenerTest {
        val old = registry.rotate()!!
        clock.set(BridgeContract.REQUEST_TTL_MS)
        val renewed = registry.rotate()!!
        assertNotEquals(old.requestId, renewed.requestId)
        assertEquals(HttpStatusCode.Gone, postOffer(offerJson(old.requestId)).status)
        assertEquals(HttpStatusCode.OK, postOffer(offerJson(renewed.requestId)).status)
    }

    @Test
    fun `closed registry rejects every offer`() = listenerTest {
        val request = registry.rotate()!!
        registry.close()
        assertEquals(HttpStatusCode.Gone, postOffer(offerJson(request.requestId)).status)
        assertNull(registry.current())
    }

    @Test
    fun `requestId is 128 random bits in base64url without padding`() {
        val id = newRequestId()
        assertEquals(22, id.length)
        assertTrue(id.all { it.isLetterOrDigit() || it == '-' || it == '_' })
        assertNotEquals(id, newRequestId())
    }

    @Test
    fun `real listener renews the requestId every TTL on the same port and closes after the 200`() {
        val dispatcher = StandardTestDispatcher()
        val scope = TestScope(dispatcher)
        val listener = PairingListener(
            scope = scope,
            clock = { dispatcher.scheduler.currentTime },
            idGenerator = { "id-${nextId++}" },
            host = "127.0.0.1",
        )
        scope.runTest {
            val received = mutableListOf<PairingOffer>()
            val port = listener.start { received += it }
            assertTrue(port > 0)
            assertEquals("id-0", listener.request.value?.requestId)
            advanceTimeBy(BridgeContract.REQUEST_TTL_MS + 1)
            runCurrent()
            assertEquals("id-1", listener.request.value?.requestId)

            val status = rawPost(port, offerJson("id-1"))
            assertEquals(200, status)
            runCurrent()
            assertEquals(1, received.size)
            assertNull(listener.request.value)
            listener.close()
        }
    }

    /** Same raw HTTP/1.1 request as the phone's `PairingOfferSender`. */
    private fun rawPost(port: Int, body: String): Int = java.net.Socket("127.0.0.1", port).use { socket ->
        val bytes = body.toByteArray(Charsets.UTF_8)
        val head = "POST ${BridgeContract.OFFER_PATH} HTTP/1.1\r\nHost: 127.0.0.1:$port\r\n" +
            "Content-Type: application/json; charset=utf-8\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n"
        socket.getOutputStream().apply {
            write(head.toByteArray(Charsets.US_ASCII))
            write(bytes)
            flush()
        }
        val statusLine = socket.getInputStream().bufferedReader().readLine()
        statusLine.split(' ')[1].toInt()
    }
}
