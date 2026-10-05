package app.n_zik.compagnon.bridge.pairing

import app.n_zik.compagnon.bridge.state.SessionContract
import app.n_zik.compagnon.core.network.BridgeApi
import app.n_zik.compagnon.core.network.MetaResult
import app.n_zik.compagnon.core.network.ProbeResult
import app.n_zik.compagnon.core.network.ServerAddress
import app.n_zik.compagnon.core.network.ValidateResult
import app.n_zik.compagnon.enums.PairingMode
import java.util.logging.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Why the last pairing attempt failed; each one has its own UI message. */
sealed interface PairingError {
    /** `403 PAIRING_REJECTED`: "code invalid or expired". */
    data object CodeRejected : PairingError
    /** `429 RATE_LIMITED`: wait [retryAfterMs]. */
    data class RateLimited(val retryAfterMs: Long) : PairingError
    /** `contractVersion` not `1.x`: "phone version incompatible", nothing paired. */
    data class IncompatibleVersion(val contractVersion: String) : PairingError
    data object PhoneUnreachable : PairingError
    /** The credential could not be stored (Credential Manager unavailable…). */
    data object StorageFailed : PairingError
    /** The temporary listener could not be opened. */
    data object ListenerFailed : PairingError
    data class Unexpected(val status: Int, val code: String?) : PairingError
}

/** Manual pairing form (contract §4.6); the device name lives in [PairingState.Unpaired.deviceName]. */
data class ManualForm(
    val ip: String = "",
    val port: String = BridgeContract.DEFAULT_BRIDGE_PORT.toString(),
    val code: String = "",
) {
    val normalizedCode: String get() = PairingRules.normalizeCode(code)
    val portNumber: Int? get() = port.trim().toIntOrNull()?.takeIf(PairingRules::isValidPort)
    val isIpValid: Boolean get() = PairingRules.isIpv4(ip)
    val isCodeValid: Boolean get() = PairingRules.isWellFormedCode(normalizedCode)
}

/** Health of a stored pairing, checked at start-up and on "Retry". */
sealed interface PairedStatus {
    data object Checking : PairedStatus
    data object Ok : PairedStatus
    /** `meta` unreachable: offer Retry or "Edit IP" (token and port kept). Never a loss of pairing. */
    data object Unreachable : PairedStatus
    /** `409 CONFLICT_ACTIVE_CLIENT`: no automatic reconnection until the user acts. */
    data class OtherActive(val deviceName: String?) : PairedStatus
    data class Incompatible(val contractVersion: String) : PairedStatus
    /** Any other answer (another `401`, `5xx`…): nothing is erased. */
    data class Error(val status: Int, val code: String?) : PairedStatus
}

/**
 * What the player session of story 11 needs from a healthy pairing: the credential, the address that
 * answered `meta`, the phone's `features` (contract §5) and the phone's effective UI language
 * (contract 1.9.0, `ui.language`; `null` on a ≤ 1.8 phone). Memory only.
 */
data class ActivePairing(
    val pairing: StoredPairing,
    val address: ServerAddress,
    val features: Set<String>,
    val language: String? = null,
)

sealed interface PairingState {
    data object Starting : PairingState

    /**
     * Not paired: QR (listener open) or manual form. The QR stays available in manual mode.
     * [listenerUnreachable]: 60 s passed without a valid offer; [noCandidates]: no LAN address, no QR.
     */
    data class Unpaired(
        val mode: PairingMode,
        val deviceName: String,
        val candidates: List<String>,
        val port: Int? = null,
        val request: PairingRequest? = null,
        val listenerUnreachable: Boolean = false,
        val noCandidates: Boolean = false,
        val manual: ManualForm = ManualForm(),
        val error: PairingError? = null,
    ) : PairingState {
        val validDeviceName: String? get() = PairingRules.normalizeDeviceName(deviceName)

        /** QR content (contract §4.3), `null` while there is nothing valid to show. */
        val qrPayload: PairingQrPayload?
            get() {
                val requestId = request?.requestId ?: return null
                val listenerPort = port ?: return null
                val name = validDeviceName ?: return null
                if (candidates.isEmpty()) return null
                return PairingQrPayload(requestId = requestId, deviceName = name, port = listenerPort, ips = candidates)
            }
    }

    data class Validating(val manual: Boolean) : PairingState

    data class Paired(val record: PairingRecord, val status: PairedStatus) : PairingState

    /** The phone revoked this PC: credential already erased, re-pairing screen. */
    data object Revoked : PairingState
}

/**
 * Pairing state machine (story 10): `Unpaired(Qr | Manual)` → `Validating` → `Paired(Ok | Unreachable |
 * OtherActive)`; `Revoked` → `Unpaired`. The listener lives only while `Unpaired` is shown and is closed
 * on every other transition.
 *
 * Thread-safe: the methods are called from any thread — the Compose event handlers (the Swing EDT) and
 * the coroutines of [scope] (the app scope, on the data dispatcher: the listener's `onOffer`). The state
 * transitions are atomic on the [StateFlow]: the `Unpaired` → `Validating` gate is a `compareAndSet`
 * (only the caller that still sees `Unpaired` may enter `Validating`), an operation taking over a
 * cancelled one already on `Validating` continues the round (never stranding the state there),
 * the [updateUnpaired] transforms run via `update {}` (applied only while the state is still
 * `Unpaired`), and the other transitions are single value sets. The [operation] slot hands off atomically: a new operation cancels the previous one
 * and waits for it to actually stop before it runs, so at most one operation body (meta → validate →
 * store) ever runs at a time. The listener start/close is serialised on the shared [listenerJobs] lock,
 * so a close racing a start cannot miss a live listener (which would leak its open port).
 */
class PairingController(
    private val scope: CoroutineScope,
    private val api: BridgeApi,
    private val store: CredentialStore,
    private val listenerFactory: () -> OfferListener,
    private val candidateProvider: suspend () -> List<String>,
    private val defaultDeviceName: String,
    /**
     * Persisted whenever a `meta` answers with a non-null phone language (contract 1.9.0,
     * `ui.language`): the `auto_tel` fallback of the PC's "App language" setting.
     */
    private val onPhoneLanguage: (String) -> Unit = {},
    private val listenerUnreachableMs: Long = BridgeContract.LISTENER_UNREACHABLE_MS,
    revocationConfirmDelayMs: Long = BridgeContract.REVOCATION_CONFIRM_DELAY_MS,
) {
    private val log = Logger.getLogger("PairingController")

    private val _state = MutableStateFlow<PairingState>(PairingState.Starting)
    val state: StateFlow<PairingState> = _state.asStateFlow()

    /** Reusable revocation rule (story 11 plugs WS `4003` and audio `401` into it). */
    val revocation = RevocationPolicy(onRevoked = ::onRevoked, confirmDelayMs = revocationConfirmDelayMs)

    @Volatile private var pairing: StoredPairing? = null

    /** Set whenever the state is `Paired(Ok)`, `null` otherwise. */
    @Volatile var active: ActivePairing? = null
        private set

    private val _phoneLanguage = MutableStateFlow<String?>(null)

    /**
     * The phone's effective language of the last successful `meta` (contract 1.9.0, `ui.language`):
     * `null` before the first `meta`, and when the phone does not advertise the feature (≤ 1.8)
     * or answers without the field.
     */
    val phoneLanguage: StateFlow<String?> = _phoneLanguage.asStateFlow()

    // Last phone address of this session, kept in memory only (never written once the pairing is erased):
    // it prefills the manual form after a revocation or a "Forget".
    @Volatile private var lastAddress: ServerAddress? = null
    @Volatile private var listener: OfferListener? = null
    // Guards [listener] and [listenerJobs]: the start/close handoff is serialised on this lock.
    private val listenerJobs = mutableListOf<Job>()
    private val operationLock = Any()
    @Volatile private var operation: Job? = null

    fun start() = runOperation {
        val stored = store.load()
        if (stored == null) enterUnpaired() else {
            pairing = stored
            lastAddress = ServerAddress(stored.record.serverIps.firstOrNull().orEmpty(), stored.record.serverPort)
            checkPairing()
        }
    }

    // ---- Unpaired -------------------------------------------------------------------------------

    fun setDeviceName(name: String) = updateUnpaired { it.copy(deviceName = name.take(BridgeContract.DEVICE_NAME_MAX_LENGTH)) }

    fun showManual() = updateUnpaired { it.copy(mode = PairingMode.Manual) }

    fun showQr() = updateUnpaired { if (it.noCandidates) it else it.copy(mode = PairingMode.Qr) }

    fun updateManual(form: ManualForm) = updateUnpaired { it.copy(manual = form, error = null) }

    fun dismissError() = updateUnpaired { it.copy(error = null) }

    /** "Pair" on the manual form: `meta` then `validate(requestId: null)` (contract §4.6). */
    fun submitManual() {
        val unpaired = _state.value as? PairingState.Unpaired ?: return
        val form = unpaired.manual
        val port = form.portNumber
        val name = unpaired.validDeviceName
        if (!form.isIpValid || port == null || !form.isCodeValid || name == null) return
        runOperation {
            completePairing(
                code = form.normalizedCode,
                requestId = null,
                ips = listOf(form.ip.trim()),
                port = port,
                deviceName = name,
                returnTo = unpaired.copy(error = null),
            )
        }
    }

    private fun onOffer(offer: PairingOffer) {
        val unpaired = _state.value as? PairingState.Unpaired ?: return
        val name = unpaired.validDeviceName ?: defaultDeviceName
        runOperation {
            completePairing(
                code = offer.code,
                requestId = offer.requestId,
                ips = offer.serverIps,
                port = offer.serverPort,
                deviceName = name,
                returnTo = unpaired.copy(mode = PairingMode.Qr, error = null),
            )
        }
    }

    private suspend fun enterUnpaired(previous: PairingState.Unpaired? = null, error: PairingError? = null) {
        closeListener()
        val candidates = runCatching { candidateProvider() }.getOrDefault(emptyList())
        val deviceName = previous?.deviceName ?: defaultDeviceName
        val base = PairingState.Unpaired(
            mode = previous?.mode ?: PairingMode.Qr,
            deviceName = deviceName,
            candidates = candidates,
            listenerUnreachable = previous?.listenerUnreachable ?: false,
            noCandidates = candidates.isEmpty(),
            manual = previous?.manual ?: prefilledManualForm(),
            error = error,
        )
        if (candidates.isEmpty()) {
            // Contract §4.3 step 5: no QR, straight to the manual form.
            _state.value = base.copy(mode = PairingMode.Manual)
            return
        }
        _state.value = base
        val newListener = listenerFactory()
        // Assigned under the shared lock so a concurrent [closeListener] cannot miss it.
        synchronized(listenerJobs) { listener = newListener }
        val port = try {
            newListener.start(::onOffer)
        } catch (e: Exception) {
            log.warning("Pairing listener failed to start: ${e::class.simpleName}")
            closeListener()
            _state.value = base.copy(mode = PairingMode.Manual, error = error ?: PairingError.ListenerFailed)
            return
        }
        updateUnpaired { it.copy(port = port) }
        synchronized(listenerJobs) {
            listenerJobs += scope.launch {
                newListener.request.collect { request -> if (request != null) updateUnpaired { it.copy(request = request) } }
            }
            if (!base.listenerUnreachable) {
                listenerJobs += scope.launch {
                    delay(listenerUnreachableMs)
                    // Contract §4.6: no valid offer within 60 s of the first QR → explicit message + manual form.
                    updateUnpaired { it.copy(mode = PairingMode.Manual, listenerUnreachable = true) }
                }
            }
        }
    }

    private suspend fun completePairing(
        code: String,
        requestId: String?,
        ips: List<String>,
        port: Int,
        deviceName: String,
        returnTo: PairingState.Unpaired,
    ) {
        closeListener()
        // Atomic transition: only the operation that still sees `Unpaired` may enter `Validating`,
        // so a racing caller (the listener's `onOffer` vs a user submit) cannot double-drive it.
        // An operation that takes over a cancelled one already on `Validating` continues the
        // round instead of bailing: the cancelled operation no longer drives the state, and
        // bailing here would strand it on `Validating` (nothing else may drive it from there).
        val current = _state.value
        if (current is PairingState.Unpaired) {
            if (!_state.compareAndSet(current, PairingState.Validating(manual = requestId == null))) return
        } else if (current !is PairingState.Validating) {
            return
        }

        val (address, meta) = reachMeta(ips, port)
        when (meta) {
            is MetaResult.Ok -> Unit
            is MetaResult.Incompatible -> return enterUnpaired(returnTo, PairingError.IncompatibleVersion(meta.contractVersion))
            MetaResult.Unreachable -> return enterUnpaired(returnTo, PairingError.PhoneUnreachable)
            is MetaResult.Failed -> return enterUnpaired(returnTo, PairingError.Unexpected(meta.status, meta.code))
        }
        when (val result = api.validate(address, ValidateRequest(code = code, requestId = requestId, deviceName = deviceName))) {
            is ValidateResult.Ok -> {
                val response = result.response
                val record = PairingRecord(
                    serverIps = listOf(address.ip) + ips.filter { it != address.ip },
                    serverPort = response.serverPort,
                    serverName = response.serverName,
                    deviceId = response.deviceId,
                    deviceName = deviceName,
                )
                val stored = StoredPairing(record, response.deviceToken)
                if (!store.save(stored)) return enterUnpaired(returnTo, PairingError.StorageFailed)
                pairing = stored
                val reached = ServerAddress(address.ip, response.serverPort)
                lastAddress = reached
                // Contract 1.9.0 (`ui.language`): only a completed pairing remembers the phone's
                // language — a rejected code (or a `409`) must not switch the applied language
                // mid-flow.
                val phoneLanguage = rememberPhoneLanguage(meta.meta)
                active = ActivePairing(stored, reached, meta.meta.features.toSet(), phoneLanguage)
                _state.value = PairingState.Paired(record, PairedStatus.Ok)
            }
            ValidateResult.Rejected -> enterUnpaired(returnTo, PairingError.CodeRejected)
            is ValidateResult.RateLimited -> enterUnpaired(returnTo, PairingError.RateLimited(result.retryAfterMs))
            ValidateResult.Unreachable -> enterUnpaired(returnTo, PairingError.PhoneUnreachable)
            is ValidateResult.Failed -> enterUnpaired(returnTo, PairingError.Unexpected(result.status, result.code))
        }
    }

    // ---- Paired ---------------------------------------------------------------------------------

    /** "Retry" on the unreachable / other-PC screens: a user action, never automatic. */
    fun retry() = runOperation { checkPairing() }

    /** "Edit IP": the phone's IP only; token and port are kept (contract §4.7). */
    fun editIp(ip: String) {
        val current = pairing ?: return
        val trimmed = ip.trim()
        if (!PairingRules.isIpv4(trimmed)) return
        runOperation {
            pairing = store.updateServerIps(listOf(trimmed))
                ?: current.copy(record = current.record.copy(serverIps = listOf(trimmed)))
            lastAddress = ServerAddress(trimmed, current.record.serverPort)
            checkPairing()
        }
    }

    /** "Forget this phone": token and `pairing.json` erased, back to the QR. */
    fun forget() = runOperation {
        store.clear()
        pairing = null
        active = null
        enterUnpaired()
    }

    /** "Pair again" on the re-pairing screen. */
    fun pairAgain() = runOperation { enterUnpaired() }

    private suspend fun checkPairing() {
        val current = pairing ?: return enterUnpaired()
        active = null
        closeListener()
        _state.value = PairingState.Paired(current.record, PairedStatus.Checking)
        val (address, meta) = reachMeta(current.record.serverIps, current.record.serverPort)
        // Contract 1.9.0 (`ui.language`): the phone's language is remembered on every successful
        // `meta`, whatever the health check ends up — it is the phone's, not this PC's, language
        val phoneLanguage = if (meta is MetaResult.Ok) rememberPhoneLanguage(meta.meta) else null
        val status: PairedStatus = when (meta) {
            MetaResult.Unreachable -> PairedStatus.Unreachable
            is MetaResult.Incompatible -> PairedStatus.Incompatible(meta.contractVersion)
            is MetaResult.Failed -> PairedStatus.Error(meta.status, meta.code)
            is MetaResult.Ok -> {
                val outcome = revocation.confirmRest(
                    call = { api.probe(address, current.deviceToken) },
                    isRevoked = { it is ProbeResult.Revoked },
                )
                if (outcome.revoked) return
                when (val probe = outcome.result) {
                    ProbeResult.Ok -> PairedStatus.Ok
                    is ProbeResult.OtherActive -> PairedStatus.OtherActive(probe.deviceName)
                    ProbeResult.Unreachable -> PairedStatus.Unreachable
                    is ProbeResult.Failed -> PairedStatus.Error(probe.status, probe.code)
                    ProbeResult.Revoked -> PairedStatus.Error(401, BridgeErrorCode.DEVICE_REVOKED)
                }
            }
        }
        if (status == PairedStatus.Ok && meta is MetaResult.Ok) {
            active = ActivePairing(current, address, meta.meta.features.toSet(), phoneLanguage)
        }
        _state.value = PairingState.Paired(current.record, status)
    }

    private suspend fun onRevoked() {
        store.clear()
        pairing = null
        active = null
        closeListener()
        _state.value = PairingState.Revoked
    }

    // ---- Plumbing -------------------------------------------------------------------------------

    /** Empty manual form, prefilled with the last phone address of this session when there is one. */
    private fun prefilledManualForm(): ManualForm {
        val last = lastAddress ?: return ManualForm()
        return ManualForm(ip = last.ip.takeIf(PairingRules::isIpv4).orEmpty(), port = last.port.toString())
    }

    /**
     * The phone's effective language of a successful `meta` (contract 1.9.0, `ui.language`),
     * feature-gated: `null` when the phone does not advertise the feature (≤ 1.8) or the field is
     * absent. Kept in [phoneLanguage] for the resolver, and persisted by [onPhoneLanguage] (a
     * non-null language only — the PC's own OS locale is never filed as a phone language).
     */
    private fun rememberPhoneLanguage(meta: MetaResponse): String? {
        val language = if (SessionContract.FEATURE_UI_LANGUAGE in meta.features) meta.language else null
        _phoneLanguage.value = language
        language?.let(onPhoneLanguage)
        return language
    }

    /** First candidate whose `meta` answers; the client tries the next IPs when there are several (contract §4.4). */
    private suspend fun reachMeta(ips: List<String>, port: Int): Pair<ServerAddress, MetaResult> {
        var last: Pair<ServerAddress, MetaResult> = ServerAddress(ips.firstOrNull().orEmpty(), port) to MetaResult.Unreachable
        for (ip in ips) {
            val address = ServerAddress(ip, port)
            val result = api.meta(address)
            if (result != MetaResult.Unreachable) return address to result
            last = address to result
        }
        return last
    }

    private fun updateUnpaired(transform: (PairingState.Unpaired) -> PairingState.Unpaired) {
        // Atomic transition: the transform runs only while the state is still `Unpaired`.
        _state.update { current ->
            if (current is PairingState.Unpaired) transform(current) else current
        }
    }

    /**
     * Runs [block] as the controller's single live operation. The slot hands off atomically (no
     * read-cancel-assign race across threads): the previous operation is cancelled, and the new
     * one waits for it to actually stop before running, so two operation bodies never interleave.
     */
    private fun runOperation(block: suspend () -> Unit) {
        val job: Job
        synchronized(operationLock) {
            val previous = operation
            previous?.cancel()
            job = scope.launch {
                previous?.join()
                block()
            }
            operation = job
        }
    }

    private fun closeListener() {
        // The close/null shares the lock with the [enterUnpaired] assignment (re-read inside the
        // lock): a close that races a start sees the new listener and closes it, never misses it.
        synchronized(listenerJobs) {
            listenerJobs.forEach { it.cancel() }
            listenerJobs.clear()
            val current = listener
            listener = null
            current?.close()
        }
    }

    /** App exit: the listener is always closed. */
    fun close() {
        synchronized(operationLock) { operation?.cancel() }
        closeListener()
    }
}
