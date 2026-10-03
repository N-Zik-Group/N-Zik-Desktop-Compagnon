package app.n_zik.compagnon.bridge

import app.n_zik.compagnon.bridge.state.ModesChangedMessage
import app.n_zik.compagnon.bridge.state.PlaybackChangedMessage
import app.n_zik.compagnon.bridge.state.PongMessage
import app.n_zik.compagnon.bridge.state.ServerClock
import app.n_zik.compagnon.bridge.state.ServerMessage
import app.n_zik.compagnon.bridge.state.SnapshotMessage
import app.n_zik.compagnon.bridge.state.StopCode
import app.n_zik.compagnon.core.network.ServerAddress
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.respondText
import io.ktor.server.routing.routing
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import java.net.ServerSocket
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * [BridgeSession] against a real Ktor server (CIO + WebSockets on a loopback port) playing the
 * phone's side of contract §6–§7: snapshot then deltas, every close code, upgrade refusals, ping.
 * Backoff waits are recorded instead of slept (virtual time).
 */
class BridgeSessionTest {

    private val token = "T".repeat(43)
    private lateinit var server: EmbeddedServer<*, *>
    private var port = 0

    /** Answer to the upgrade request instead of the WebSocket, when set: status + JSON body. */
    @Volatile private var refusal: Pair<HttpStatusCode, String>? = null

    /** What the phone does once the WebSocket is open; [autoPong] answers every `ping`. */
    @Volatile private var script: suspend DefaultWebSocketServerSession.() -> Unit = {}
    @Volatile private var autoPong = true

    private val upgrades = AtomicInteger()
    private val authHeaders: MutableList<String?> = Collections.synchronizedList(mutableListOf())
    private val received = Channel<String>(Channel.UNLIMITED)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val sleeps = Channel<Long>(Channel.UNLIMITED)
    private val messages: MutableList<ServerMessage> = Collections.synchronizedList(mutableListOf())
    private val revocation = FakeRevocation()

    private class FakeRevocation : SessionRevocation {
        @Volatile var confirm = true
        val revokedNow = AtomicInteger()
        val confirmCalls = AtomicInteger()
        override suspend fun revokeNow() {
            revokedNow.incrementAndGet()
        }
        override suspend fun confirmUpgradeRevoked(): Boolean {
            confirmCalls.incrementAndGet()
            return confirm
        }
    }

    @BeforeEach
    fun startServer() {
        server = embeddedServer(CIO, port = 0, host = "127.0.0.1") {
            install(WebSockets)
            intercept(ApplicationCallPipeline.Plugins) {
                authHeaders.add(call.request.headers[HttpHeaders.Authorization])
                val (status, body) = refusal ?: return@intercept
                call.respondText(body, ContentType.Application.Json, status)
                finish()
            }
            routing {
                webSocket("/api/v1/ws") {
                    upgrades.incrementAndGet()
                    val reader = launch {
                        for (frame in incoming) {
                            if (frame !is Frame.Text) continue
                            val text = frame.readText()
                            received.send(text)
                            if (autoPong && text.contains("\"ping\"")) {
                                val clientTime = Regex("\"clientTimeMs\":(\\d+)").find(text)!!.groupValues[1]
                                val now = System.currentTimeMillis()
                                send(Frame.Text("""{"type":"pong","clientTimeMs":$clientTime,"serverReceiveTimeMs":$now,"serverSendTimeMs":$now}"""))
                            }
                        }
                    }
                    script()
                    reader.join()
                }
            }
        }.start(wait = false)
        port = runBlocking { server.engine.resolvedConnectors().first().port }
    }

    @AfterEach
    fun stop() {
        scope.cancel()
        server.stop(0, 500)
    }

    private fun session(
        address: ServerAddress = ServerAddress("127.0.0.1", port),
        pingIntervalMs: Long = 60_000,
        silenceTimeoutMs: Long = 10_000,
        clock: ServerClock = ServerClock(),
    ) = BridgeSession(
        http = BridgeSession.httpClient(ClientCIO.create()),
        address = address,
        deviceToken = token,
        scope = scope,
        revocation = revocation,
        clock = clock,
        pingIntervalMs = pingIntervalMs,
        silenceTimeoutMs = silenceTimeoutMs,
        sleep = { sleeps.send(it) },
    ).also { it.start { message -> messages += message } }

    private fun snapshot(revision: Long) =
        """{"type":"snapshot","revision":$revision,"serverTimeMs":1790000000000,"queue":[{"id":"dQw4w9WgXcQ","title":"T","artists":"A","durationMs":212000,"source":"online","isDownloaded":false,"isLiked":true,"hasArtwork":true}],"currentIndex":0,"currentTrackId":"dQw4w9WgXcQ","isPlaying":true,"speed":1.0,"positionMs":83000,"repeatMode":"off","shuffle":false}"""

    private suspend fun BridgeSession.awaitState(predicate: (ConnectionState) -> Boolean): ConnectionState =
        withTimeout(5_000) { connection.first(predicate) }

    private suspend fun awaitMessages(count: Int) = withTimeout(5_000) { while (messages.size < count) delay(10) }

    private suspend fun nextReceived(predicate: (String) -> Boolean): String = withTimeout(5_000) {
        var text: String
        do text = received.receive() while (!predicate(text))
        text
    }

    private fun closeWith(code: Short, reason: String, frames: List<String> = emptyList()): suspend DefaultWebSocketServerSession.() -> Unit = {
        send(Frame.Text(snapshot(1)))
        frames.forEach { send(Frame.Text(it)) }
        close(CloseReason(code, reason))
    }

    @Test
    fun `opens with the Bearer, receives the snapshot then the deltas in order`() = runBlocking {
        script = {
            send(Frame.Text(snapshot(57)))
            send(Frame.Text("""{"type":"playbackChanged","revision":58,"serverTimeMs":1790000001000,"isPlaying":false,"speed":1.0,"positionMs":84000}"""))
            send(Frame.Text("""{"type":"modesChanged","revision":59,"serverTimeMs":1790000002000,"repeatMode":"all","shuffle":true}"""))
            delay(5_000)
        }
        val session = session()
        session.awaitState { it == ConnectionState.Live }
        awaitMessages(3)
        val revised = messages.filter { it !is PongMessage }
        assertTrue(revised[0] is SnapshotMessage)
        assertEquals(58L, (revised[1] as PlaybackChangedMessage).revision)
        assertEquals(59L, (revised[2] as ModesChangedMessage).revision)
        assertEquals("Bearer $token", authHeaders.first())
        session.close()
    }

    @Test
    fun `pings at once on a monotonic clock and syncs the clock from the pong`() = runBlocking {
        script = { send(Frame.Text(snapshot(1))); delay(5_000) }
        val session = session(pingIntervalMs = 200)
        val first = nextReceived { it.contains("\"ping\"") }
        assertTrue(first.contains("\"clientTimeMs\":"))
        nextReceived { it.contains("\"ping\"") } // and again after the interval
        withTimeout(5_000) { while (!session.clock.isSynced) delay(10) }
        val skew = kotlin.math.abs(session.clock.serverNowMs() - System.currentTimeMillis())
        assertTrue(skew < 1_000, "skew $skew")
        session.close()
    }

    @Test
    fun `requestSnapshot is sent on the live session`() = runBlocking {
        script = { send(Frame.Text(snapshot(1))); delay(5_000) }
        val session = session()
        session.awaitState { it == ConnectionState.Live }
        session.requestSnapshot()
        assertEquals("""{"type":"requestSnapshot"}""", nextReceived { it.contains("requestSnapshot") })
        session.close()
    }

    @Test
    fun `serverStopped then 1001 is a stop without reconnection, Reconnect resumes with a snapshot`() = runBlocking {
        script = closeWith(1001, "SERVER_STOPPED", listOf("""{"type":"serverStopped","code":"AUTO_STOP","message":"bye"}"""))
        val session = session()
        assertEquals(ConnectionState.ServerStopped(StopCode.AutoStop), session.awaitState { it is ConnectionState.ServerStopped })
        delay(300)
        assertEquals(1, upgrades.get())
        assertTrue(sleeps.tryReceive().isFailure, "no reconnection")

        // The server restarts; the user clicks "Reconnect".
        script = { send(Frame.Text(snapshot(0))); delay(5_000) }
        messages.clear()
        session.reconnect()
        session.awaitState { it == ConnectionState.Live }
        awaitMessages(1)
        assertEquals(0L, (messages.first { it is SnapshotMessage } as SnapshotMessage).revision)
        assertEquals(2, upgrades.get())
        session.close()
    }

    @Test
    fun `4001 is kicked without reconnection`() = runBlocking {
        script = closeWith(4001, "KICKED")
        val session = session()
        assertEquals(ConnectionState.Kicked, session.awaitState { it == ConnectionState.Kicked })
        delay(300)
        assertEquals(1, upgrades.get())
        assertEquals(0, revocation.revokedNow.get(), "pairing kept")
        session.close()
    }

    @Test
    fun `4000 is replaced without reconnection`() = runBlocking {
        script = closeWith(4000, "SESSION_REPLACED")
        val session = session()
        assertEquals(ConnectionState.Replaced, session.awaitState { it == ConnectionState.Replaced })
        delay(300)
        assertEquals(1, upgrades.get())
        session.close()
    }

    @Test
    fun `1001 alone is a stop too`() = runBlocking {
        script = closeWith(1001, "SERVER_STOPPED")
        val session = session()
        assertEquals(ConnectionState.ServerStopped(null), session.awaitState { it is ConnectionState.ServerStopped })
        session.close()
    }

    @Test
    fun `4003 revokes at once`() = runBlocking {
        script = closeWith(4003, "DEVICE_REVOKED")
        val session = session()
        assertEquals(ConnectionState.Revoked, session.awaitState { it == ConnectionState.Revoked })
        assertEquals(1, revocation.revokedNow.get())
        session.reconnect()
        delay(200)
        assertEquals(ConnectionState.Revoked, session.connection.value, "terminal")
        session.close()
    }

    @Test
    fun `4008 reconnects with backoff from 1 s`() = runBlocking {
        script = closeWith(4008, "PING_TIMEOUT")
        val session = session()
        assertEquals(1_000L, withTimeout(5_000) { sleeps.receive() })
        withTimeout(5_000) { while (upgrades.get() < 2) delay(10) }
        // The previous session was live, so the backoff restarts at 1 s each time.
        assertEquals(1_000L, withTimeout(5_000) { sleeps.receive() })
        session.close()
    }

    @Test
    fun `a server dropping every session before the snapshot keeps backing off`() = runBlocking {
        script = { close(CloseReason(4008, "PING_TIMEOUT")) }
        val session = session()
        val waits = withTimeout(5_000) { List(4) { sleeps.receive() } }
        assertEquals(listOf(1_000L, 2_000, 4_000, 8_000), waits)
        assertTrue(upgrades.get() >= 4)
        session.close()
    }

    @Test
    fun `a new session forgets the previous clock samples`() = runBlocking {
        autoPong = false
        script = { send(Frame.Text(snapshot(1))); delay(5_000) }
        val clock = ServerClock()
        clock.onPong(PongMessage(clientTimeMs = clock.nowMonotonicMs(), serverReceiveTimeMs = 1, serverSendTimeMs = 1))
        assertTrue(clock.isSynced)
        val session = session(clock = clock)
        session.awaitState { it == ConnectionState.Live }
        assertTrue(!clock.isSynced, "old offset dropped when the session opened")
        session.close()
    }

    @Test
    fun `transient losses back off 1, 2, 4, 8, 16 then 30 s without limit`() = runBlocking {
        refusal = HttpStatusCode.InternalServerError to """{"code":"INTERNAL_ERROR","message":"x"}"""
        val session = session()
        val waits = withTimeout(5_000) { List(8) { sleeps.receive() } }
        assertEquals(listOf(1_000L, 2_000, 4_000, 8_000, 16_000, 30_000, 30_000, 30_000), waits)
        session.close()
    }

    @Test
    fun `refused TCP connection is transient`() = runBlocking {
        val closedPort = ServerSocket(0).use { it.localPort }
        val session = session(address = ServerAddress("127.0.0.1", closedPort))
        assertEquals(1_000L, withTimeout(5_000) { sleeps.receive() })
        assertTrue(session.connection.value is ConnectionState.Reconnecting || session.connection.value == ConnectionState.Connecting)
        session.close()
    }

    @Test
    fun `upgrade 409 is another PC, no automatic reconnection, Retry tries again`() = runBlocking {
        refusal = HttpStatusCode.Conflict to
            """{"code":"CONFLICT_ACTIVE_CLIENT","message":"busy","activeDevice":{"deviceId":"Zz9yX8wV7uT","deviceName":"PC-BUREAU"}}"""
        val session = session()
        assertEquals(ConnectionState.OtherActive("PC-BUREAU"), session.awaitState { it is ConnectionState.OtherActive })
        delay(300)
        assertEquals(1, authHeaders.size)
        assertTrue(sleeps.tryReceive().isFailure)

        refusal = null
        script = { send(Frame.Text(snapshot(1))); delay(5_000) }
        session.reconnect()
        session.awaitState { it == ConnectionState.Live }
        session.close()
    }

    @Test
    fun `upgrade 401 DEVICE_REVOKED goes through the REST confirmation`() = runBlocking {
        refusal = HttpStatusCode.Unauthorized to """{"code":"DEVICE_REVOKED","message":"x"}"""
        revocation.confirm = true
        val session = session()
        assertEquals(ConnectionState.Revoked, session.awaitState { it == ConnectionState.Revoked })
        assertEquals(1, revocation.confirmCalls.get())
        session.close()
    }

    @Test
    fun `upgrade 401 DEVICE_REVOKED not confirmed is retried with backoff`() = runBlocking {
        refusal = HttpStatusCode.Unauthorized to """{"code":"DEVICE_REVOKED","message":"x"}"""
        revocation.confirm = false
        val session = session()
        assertEquals(1_000L, withTimeout(5_000) { sleeps.receive() })
        assertEquals(0, revocation.revokedNow.get())
        session.close()
    }

    @Test
    fun `upgrade 503 SERVER_STOPPING is a stop`() = runBlocking {
        refusal = HttpStatusCode.ServiceUnavailable to """{"code":"SERVER_STOPPING","message":"x"}"""
        val session = session()
        assertEquals(ConnectionState.ServerStopped(null), session.awaitState { it is ConnectionState.ServerStopped })
        delay(300)
        assertTrue(sleeps.tryReceive().isFailure)
        session.close()
    }

    @Test
    fun `a silent session is treated as a transient loss`() = runBlocking {
        autoPong = false
        script = { send(Frame.Text(snapshot(1))); delay(10_000) }
        val session = session(silenceTimeoutMs = 300)
        session.awaitState { it == ConnectionState.Live }
        assertEquals(1_000L, withTimeout(5_000) { sleeps.receive() })
        session.close()
    }

    @Test
    fun `close sends 1000 and stops every reconnection`() = runBlocking {
        val closeCode = Channel<Short?>(1)
        script = {
            send(Frame.Text(snapshot(1)))
            closeCode.send(closeReason.await()?.code)
        }
        val session = session()
        session.awaitState { it == ConnectionState.Live }
        session.close()
        assertEquals(1000.toShort(), withTimeout(5_000) { closeCode.receive() })
        delay(300)
        assertTrue(sleeps.tryReceive().isFailure)
        assertEquals(1, upgrades.get())
    }
}
