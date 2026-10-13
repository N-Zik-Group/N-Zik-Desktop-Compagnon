package app.n_zik.compagnon.bridge.pairing

import app.n_zik.compagnon.utils.coroutines.NzikDispatchers
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.withCharset
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.contentLength
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import java.security.SecureRandom
import java.util.Base64
import java.util.logging.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A `requestId` shown in the QR, and when it was shown. */
data class PairingRequest(val requestId: String, val issuedAtMs: Long)

/** 128 random bits, base64url without padding: 22 characters (contract §4.3). */
fun newRequestId(random: SecureRandom = SecureRandom()): String {
    val bytes = ByteArray(16).also(random::nextBytes)
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
}

/**
 * Life of the QR's `requestId` (contract §4.4): one current id, valid [ttlMs] after it is shown,
 * accepted at most once. Thread-safe; the HTTP handler and the rotation timer share it.
 */
class PairingRequestRegistry(
    private val clock: () -> Long,
    private val ttlMs: Long = BridgeContract.REQUEST_TTL_MS,
    private val idGenerator: () -> String = { newRequestId() },
) {
    private var current: PairingRequest? = null
    private var consumed = false
    private var closed = false

    /** New `requestId` (new QR); the previous one becomes unknown. `null` once closed or consumed. */
    @Synchronized
    fun rotate(): PairingRequest? {
        if (closed || consumed) return null
        return PairingRequest(idGenerator(), clock()).also { current = it }
    }

    @Synchronized
    fun current(): PairingRequest? = current.takeUnless { closed || consumed }

    /** `true` exactly once, for the current, unexpired, unused id; anything else is a `410`. */
    @Synchronized
    fun tryConsume(requestId: String): Boolean {
        val request = current ?: return false
        if (closed || consumed || request.requestId != requestId) return false
        if (clock() - request.issuedAtMs >= ttlMs) return false
        consumed = true
        return true
    }

    @Synchronized
    fun close() {
        closed = true
    }
}

private const val MAX_OFFER_BODY_BYTES = 16 * 1_024
private val JSON_UTF8 = ContentType.Application.Json.withCharset(Charsets.UTF_8)

/**
 * The listener's only route, `POST /nzik-pair/v1/offer` (contract §4.4): `200` once per `requestId`,
 * `410` when unknown, expired or used, `400` for an invalid body, `404` for anything else.
 * [onAccepted] runs after the `200` has been sent.
 */
fun Application.pairingOfferModule(registry: PairingRequestRegistry, onAccepted: (PairingOffer) -> Unit) {
    intercept(ApplicationCallPipeline.Call) {
        val call: ApplicationCall = context
        if (call.request.httpMethod != HttpMethod.Post || call.request.path() != BridgeContract.OFFER_PATH) {
            call.respondError(HttpStatusCode.NotFound, BridgeErrorCode.NOT_FOUND, "Unknown route")
            finish()
            return@intercept
        }
        val offer = call.readOffer()
        when {
            offer == null -> call.respondError(HttpStatusCode.BadRequest, BridgeErrorCode.BAD_REQUEST, "Invalid offer body")
            !registry.tryConsume(offer.requestId) -> call.respondError(
                HttpStatusCode.Gone,
                BridgeErrorCode.PAIRING_REQUEST_EXPIRED,
                "Pairing request unknown, expired or already used",
            )
            else -> {
                call.respondText(BridgeJson.encodeToString(OfferAccepted.serializer(), OfferAccepted()), JSON_UTF8, HttpStatusCode.OK)
                onAccepted(offer)
            }
        }
        finish()
    }
}

private suspend fun ApplicationCall.readOffer(): PairingOffer? {
    if ((request.contentLength() ?: 0L) > MAX_OFFER_BODY_BYTES) return null
    val text = runCatching { receiveText() }.getOrNull() ?: return null
    if (text.length > MAX_OFFER_BODY_BYTES) return null
    val offer = runCatching { BridgeJson.decodeFromString(PairingOffer.serializer(), text) }.getOrNull() ?: return null
    val valid = offer.v == BridgeContract.QR_VERSION &&
        offer.requestId.isNotBlank() &&
        offer.code.isNotBlank() &&
        offer.serverIps.isNotEmpty() && offer.serverIps.all(PairingRules::isIpv4) &&
        PairingRules.isValidPort(offer.serverPort) &&
        offer.serverName.isNotBlank()
    return offer.takeIf { valid }
}

private suspend fun ApplicationCall.respondError(status: HttpStatusCode, code: String, message: String) {
    respondText(BridgeJson.encodeToString(ApiError.serializer(), ApiError(code, message)), JSON_UTF8, status)
}

/** The temporary pairing listener as the state machine sees it; faked in tests. */
interface OfferListener {
    /** The `requestId` currently shown, renewed every TTL; `null` once closed or consumed. */
    val request: StateFlow<PairingRequest?>

    /** Opens the listener on an OS-chosen port and returns it. [onOffer] runs once, after the `200`. */
    suspend fun start(onOffer: (PairingOffer) -> Unit): Int

    /** Idempotent; always called when the pairing screen is left or the app quits. */
    fun close()
}

/**
 * Ktor CIO listener on an ephemeral port (contract §4.2 step 1, §4.4). It renews the `requestId`
 * every [ttlMs] on the same port, and closes itself right after the one `200`.
 */
class PairingListener(
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
    private val ttlMs: Long = BridgeContract.REQUEST_TTL_MS,
    private val idGenerator: () -> String = { newRequestId() },
    private val host: String = "0.0.0.0",
) : OfferListener {
    private val log = Logger.getLogger("PairingListener")
    private val registry = PairingRequestRegistry(clock, ttlMs, idGenerator)
    private val _request = MutableStateFlow<PairingRequest?>(null)
    override val request: StateFlow<PairingRequest?> = _request.asStateFlow()

    private var server: EmbeddedServer<*, *>? = null
    private var rotation: Job? = null

    @Volatile
    private var closed = false

    override suspend fun start(onOffer: (PairingOffer) -> Unit): Int {
        check(server == null && !closed) { "Listener already started" }
        val engine = embeddedServer(CIO, port = 0, host = host) {
            pairingOfferModule(registry) { offer ->
                close()
                scope.launch { onOffer(offer) }
            }
        }
        server = engine
        val port = withContext(NzikDispatchers.DATA) {
            engine.startSuspend(wait = false)
            engine.engine.resolvedConnectors().first().port
        }
        if (closed) {
            stopServer(engine)
            error("Listener closed while starting")
        }
        _request.value = registry.rotate()
        rotation = scope.launch {
            while (isActive) {
                delay(ttlMs)
                registry.rotate()?.let { _request.value = it }
            }
        }
        log.info("Pairing listener open on port $port")
        return port
    }

    override fun close() {
        if (closed) return
        closed = true
        registry.close()
        rotation?.cancel()
        _request.value = null
        server?.let(::stopServer)
    }

    private fun stopServer(engine: EmbeddedServer<*, *>) {
        // Grace period: the `200` that triggered the close may still be flushing.
        NzikDispatchers.fireAndForget(NzikDispatchers.DATA).launch {
            withContext(NonCancellable) { engine.stopSuspend(gracePeriodMillis = 300, timeoutMillis = 1_500) }
            log.info("Pairing listener closed")
        }
    }
}
