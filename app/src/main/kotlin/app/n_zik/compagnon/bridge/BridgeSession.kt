package app.n_zik.compagnon.bridge

import app.n_zik.compagnon.bridge.pairing.ApiError
import app.n_zik.compagnon.bridge.pairing.BridgeContract
import app.n_zik.compagnon.bridge.pairing.BridgeErrorCode
import app.n_zik.compagnon.bridge.pairing.BridgeJson
import app.n_zik.compagnon.bridge.state.ClientMessages
import app.n_zik.compagnon.bridge.state.PongMessage
import app.n_zik.compagnon.bridge.state.ServerClock
import app.n_zik.compagnon.bridge.state.ServerMessage
import app.n_zik.compagnon.bridge.state.ServerMessages
import app.n_zik.compagnon.bridge.state.ServerStoppedMessage
import app.n_zik.compagnon.bridge.state.SessionContract
import app.n_zik.compagnon.bridge.state.SnapshotMessage
import app.n_zik.compagnon.bridge.state.StopCode
import app.n_zik.compagnon.core.network.ServerAddress
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.url
import io.ktor.http.HttpStatusCode
import io.ktor.http.isWebsocket
import io.ktor.utils.io.InternalAPI
import io.ktor.utils.io.readBuffer
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import java.util.logging.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.io.readByteArray

/** State of the WebSocket session (contract §6.4), shown by the connection indicator. */
sealed interface ConnectionState {
    data object Connecting : ConnectionState

    /** Upgrade accepted; the first message is always a `snapshot`. */
    data object Live : ConnectionState

    /** Transient loss: next attempt in [delayMs] (1, 2, 4, 8, 16 then 30 s, without limit). */
    data class Reconnecting(val delayMs: Long) : ConnectionState

    /** `serverStopped` + `1001`, or `503 SERVER_STOPPING` at the upgrade ([code] `null`). No reconnection. */
    data class ServerStopped(val code: StopCode?) : ConnectionState

    /** `4001 KICKED`: disconnected from the phone, pairing kept, no reconnection until "Reconnect". */
    data object Kicked : ConnectionState

    /** `4000 SESSION_REPLACED`: another instance of this device took the session. No reconnection. */
    data object Replaced : ConnectionState

    /** Upgrade `409 CONFLICT_ACTIVE_CLIENT`: no reconnection until "Retry". */
    data class OtherActive(val deviceName: String?) : ConnectionState

    /** `4003`, or a confirmed `401 DEVICE_REVOKED`: the credential is erased. Terminal. */
    data object Revoked : ConnectionState
}

/** The revocation hooks of the session (contract §2, §6.4), backed by story 10's `RevocationPolicy`. */
interface SessionRevocation {
    /** Close `4003`: revoked at once. */
    suspend fun revokeNow()

    /** Upgrade `401 DEVICE_REVOKED`: confirmed over REST 2 s later; `true` when revoked (credential erased). */
    suspend fun confirmUpgradeRevoked(): Boolean
}

/** The state stream as the repository sees it; [BridgeSession] in production, a fake in tests. */
interface StateChannel {
    val connection: StateFlow<ConnectionState>
    val clock: ServerClock

    /** Opens the session; every decoded frame goes to [onMessage], in order. */
    fun start(onMessage: suspend (ServerMessage) -> Unit)

    /** Sends `requestSnapshot` on the live session (contract §7.8); no-op when none is open. */
    fun requestSnapshot()

    /** User action ("Reconnect", "Retry"): a fresh attempt, backoff reset. Ignored once revoked or closed. */
    fun reconnect()

    /** Closes the session (`1000 NORMAL`) and stops every reconnection. */
    fun close()
}

/** The phone refused the WebSocket upgrade (contract §6.1): [status] and the §3 error body when readable. */
class UpgradeRefusedException(val status: Int, val error: ApiError?) : Exception("WebSocket upgrade refused: $status ${error?.code}")

/**
 * Turns a non-`101` answer to a WebSocket upgrade into an [UpgradeRefusedException] carrying the
 * status and the error `code`, which Ktor's own handshake error does not expose.
 */
@OptIn(InternalAPI::class)
private val UpgradeRefusal = createClientPlugin("UpgradeRefusal") {
    onResponse { response ->
        if (response.call.request.url.protocol.isWebsocket() && response.status != HttpStatusCode.SwitchingProtocols) {
            // The raw body: body()/bodyAsText() would go through the WebSockets plugin, which rejects it.
            val error = runCatching {
                val text = response.rawContent.readBuffer(MAX_ERROR_BODY_BYTES).readByteArray().decodeToString()
                BridgeJson.decodeFromString(ApiError.serializer(), text)
            }.getOrNull()
            throw UpgradeRefusedException(response.status.value, error)
        }
    }
}

private const val MAX_ERROR_BODY_BYTES = 64L * 1_024

/**
 * The WebSocket session of contract §6–§7: Bearer in the upgrade header, text frames, a `ping` every
 * 15 s on a monotonic clock feeding [clock], close codes and upgrade refusals classified per §6.4,
 * reconnection with backoff after a transient loss only.
 *
 * Besides the server's own `4008`, a session silent for [silenceTimeoutMs] (heartbeat every 10 s,
 * `pong` after every `ping`) is treated as a transient loss: it catches a dead Wi-Fi that TCP would
 * only notice minutes later.
 */
class BridgeSession(
    private val http: HttpClient,
    private val address: ServerAddress,
    private val deviceToken: String,
    private val scope: CoroutineScope,
    private val revocation: SessionRevocation,
    override val clock: ServerClock = ServerClock(),
    private val pingIntervalMs: Long = SessionContract.PING_INTERVAL_MS,
    private val silenceTimeoutMs: Long = SILENCE_TIMEOUT_MS,
    private val backoffMs: List<Long> = SessionContract.RECONNECT_BACKOFF_MS,
    private val sleep: suspend (Long) -> Unit = { delay(it) },
) : StateChannel {
    private val log = Logger.getLogger("BridgeSession")

    private val _connection = MutableStateFlow<ConnectionState>(ConnectionState.Connecting)
    override val connection: StateFlow<ConnectionState> = _connection.asStateFlow()

    private var onMessage: suspend (ServerMessage) -> Unit = {}
    private var loop: Job? = null
    @Volatile private var current: DefaultClientWebSocketSession? = null
    @Volatile private var closed = false
    private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val url: String get() = "ws://${address.ip}:${address.port}${BridgeContract.API_PREFIX}${SessionContract.WS_PATH}"

    override fun start(onMessage: suspend (ServerMessage) -> Unit) {
        this.onMessage = onMessage
        launchLoop()
    }

    override fun reconnect() {
        if (closed || _connection.value == ConnectionState.Revoked) return
        launchLoop()
    }

    override fun requestSnapshot() {
        val session = current ?: return
        scope.launch {
            runCatching { session.send(Frame.Text(ClientMessages.requestSnapshot())) }
                .onFailure { if (it is CancellationException) throw it }
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        val job = loop
        loop = null
        val session = current
        if (session == null) {
            job?.cancel()
            return
        }
        // Cancelling the loop would cancel the WS call at once (Ktor ties it to the caller's job) and the
        // close frame would never leave: close gracefully first, from outside the loop, then stop it.
        cleanupScope.launch {
            withTimeoutOrNull(CLOSE_TIMEOUT_MS) {
                runCatching { session.close(CloseReason(SessionContract.CLOSE_NORMAL, "NORMAL")) }
                job?.join()
            }
            job?.cancel()
        }
    }

    private fun launchLoop() {
        loop?.cancel()
        _connection.value = ConnectionState.Connecting
        loop = scope.launch { runLoop() }
    }

    private suspend fun runLoop() {
        var attempt = 0
        while (true) {
            _connection.value = ConnectionState.Connecting
            val outcome = connectOnce()
            if (closed) return
            when (outcome) {
                is Outcome.Terminal -> {
                    _connection.value = outcome.state
                    return
                }
                is Outcome.Transient -> {
                    if (outcome.wasLive) attempt = 0
                    val wait = backoffMs[attempt.coerceAtMost(backoffMs.lastIndex)]
                    attempt++
                    _connection.value = ConnectionState.Reconnecting(wait)
                    log.info("Session lost (${outcome.why}), reconnecting in $wait ms")
                    sleep(wait)
                }
            }
        }
    }

    private sealed interface Outcome {
        /** [wasLive]: the session got as far as a snapshot, so the backoff restarts from its first step. */
        data class Transient(val wasLive: Boolean, val why: String) : Outcome
        data class Terminal(val state: ConnectionState) : Outcome
    }

    /** What one live session ended with. */
    private class Ending {
        var stopped: ServerStoppedMessage? = null
        var silent = false
        var reason: CloseReason? = null

        /** A snapshot arrived: only then does the backoff restart from 1 s (a server dropping every new session backs off). */
        var gotSnapshot = false
    }

    private suspend fun connectOnce(): Outcome {
        var opened = false
        val ending = Ending()
        try {
            // webSocket {} rather than webSocketSession(): our close frame is flushed before the call is released.
            http.webSocket(request = {
                url(this@BridgeSession.url)
                bearerAuth(deviceToken)
            }) {
                opened = true
                // New session: the phone may have restarted, the previous clock samples no longer count.
                clock.reset()
                current = this
                _connection.value = ConnectionState.Live
                try {
                    runSession(this, ending)
                } finally {
                    current = null
                    withContext(NonCancellable) {
                        // Ours to close in every case (user close, silence, after the server's own close frame).
                        withTimeoutOrNull(CLOSE_TIMEOUT_MS) {
                            runCatching { close(CloseReason(SessionContract.CLOSE_NORMAL, "NORMAL")) }
                        }
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: UpgradeRefusedException) {
            return refusal(e)
        } catch (e: Exception) {
            if (!opened) return Outcome.Transient(wasLive = false, why = "connect: ${e::class.simpleName}")
            log.info("Session error: ${e::class.simpleName}")
        }
        if (ending.silent) return Outcome.Transient(wasLive = ending.gotSnapshot, why = "silent for $silenceTimeoutMs ms")
        val code = ending.reason?.code
        ending.stopped?.let { return Outcome.Terminal(ConnectionState.ServerStopped(it.code)) }
        return when (code) {
            SessionContract.CLOSE_SERVER_STOPPED -> Outcome.Terminal(ConnectionState.ServerStopped(null))
            SessionContract.CLOSE_SESSION_REPLACED -> Outcome.Terminal(ConnectionState.Replaced)
            SessionContract.CLOSE_KICKED -> Outcome.Terminal(ConnectionState.Kicked)
            SessionContract.CLOSE_DEVICE_REVOKED -> {
                revocation.revokeNow()
                Outcome.Terminal(ConnectionState.Revoked)
            }
            // 4008 PING_TIMEOUT, 1006, a close without frame, anything else: transient.
            else -> Outcome.Transient(wasLive = ending.gotSnapshot, why = "close ${code ?: "without frame"}")
        }
    }

    /** Ping loop + receive loop of one open session, until the server closes it or it falls silent. */
    private suspend fun runSession(session: DefaultClientWebSocketSession, ending: Ending) {
        try {
            coroutineScope {
                val pinger = launch {
                    while (true) {
                        try {
                            session.send(Frame.Text(ClientMessages.ping(clock.nowMonotonicMs())))
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            break
                        }
                        delay(pingIntervalMs)
                    }
                }
                while (true) {
                    val result = withTimeoutOrNull(silenceTimeoutMs) { session.incoming.receiveCatching() }
                    if (result == null) {
                        ending.silent = true
                        break
                    }
                    val frame = result.getOrNull() ?: break
                    if (frame !is Frame.Text) continue
                    val receivedAt = clock.nowMonotonicMs()
                    val message = ServerMessages.decode(frame.readText()) ?: continue
                    when (message) {
                        is PongMessage -> clock.onPong(message, receivedAt)
                        is ServerStoppedMessage -> ending.stopped = message
                        is SnapshotMessage -> ending.gotSnapshot = true
                        else -> Unit
                    }
                    onMessage(message)
                }
                pinger.cancel()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.info("Session error: ${e::class.simpleName}")
        }
        // Read before our own close, which would otherwise complete it with 1000.
        if (!ending.silent) {
            ending.reason = withTimeoutOrNull(CLOSE_TIMEOUT_MS) { runCatching { session.closeReason.await() }.getOrNull() }
        }
    }

    /** Upgrade refusals of contract §6.1 / §6.4, decided from the error `code`. */
    private suspend fun refusal(e: UpgradeRefusedException): Outcome {
        val code = e.error?.code
        return when {
            code == BridgeErrorCode.CONFLICT_ACTIVE_CLIENT ->
                Outcome.Terminal(ConnectionState.OtherActive(e.error.activeDevice?.deviceName))
            code == BridgeErrorCode.SERVER_STOPPING -> Outcome.Terminal(ConnectionState.ServerStopped(null))
            code == BridgeErrorCode.DEVICE_REVOKED ->
                if (revocation.confirmUpgradeRevoked()) Outcome.Terminal(ConnectionState.Revoked)
                else Outcome.Transient(wasLive = false, why = "401 DEVICE_REVOKED not confirmed")
            // 401 UNAUTHORIZED, 5xx…: never erases anything, retried with backoff.
            else -> Outcome.Transient(wasLive = false, why = "upgrade ${e.status} ${code ?: "without code"}")
        }
    }

    companion object {
        /** No frame at all for this long → transient loss (3 missed heartbeats, ≥ 3 `pong`s missed). */
        const val SILENCE_TIMEOUT_MS = 45_000L
        private const val CLOSE_TIMEOUT_MS = 1_000L

        /** Client for the session: WebSockets plus the upgrade refusal mapping; no request timeout on the WS. */
        fun httpClient(engine: HttpClientEngine = CIO.create { endpoint.connectTimeout = CONNECT_TIMEOUT_MS }): HttpClient =
            HttpClient(engine) {
                install(WebSockets)
                install(UpgradeRefusal)
            }

        private const val CONNECT_TIMEOUT_MS = 3_000L
    }
}
