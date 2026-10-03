package app.n_zik.compagnon.bridge.pairing

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Wire values of the local bridge, contract v1 (`_bmad-output/specs/spec-n-zik-pc-bridge/contract/CONTRACT-v1.md`
 * in the BMAD workspace; never copied into this repository). Pairing side only (§4, §5).
 */
object BridgeContract {
    const val API_PREFIX = "/api/v1"
    const val SUPPORTED_MAJOR = "1"

    const val QR_VERSION = 1
    const val QR_TYPE = "nzik-pair"
    const val OFFER_PATH = "/nzik-pair/v1/offer"

    /** §4.4 / §14: lifetime of a `requestId` after the QR is shown. */
    const val REQUEST_TTL_MS = 120_000L

    /** §4.6 / §14: without a valid offer this long after the first QR, fall back to manual pairing. */
    const val LISTENER_UNREACHABLE_MS = 60_000L

    /** Bridge port the phone tries first (contract §11.1); only a prefill of the manual form. */
    const val DEFAULT_BRIDGE_PORT = 42420

    /** §2 / §14: a REST `401 DEVICE_REVOKED` is confirmed by one retry this much later. */
    const val REVOCATION_CONFIRM_DELAY_MS = 2_000L

    const val MAX_CANDIDATE_IPS = 4
    const val DEVICE_NAME_MAX_LENGTH = 64

    /** §4.1: code alphabet and length (after normalisation). */
    const val CODE_ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ"
    const val CODE_LENGTH = 6

    /** `GET /api/v1/library/songs?limit=1`: the light Bearer probe used at start-up. */
    const val PROBE_PATH = "/library/songs"
}

/** Error codes of contract §3 / §4.4 the client reacts to. */
object BridgeErrorCode {
    const val BAD_REQUEST = "BAD_REQUEST"
    const val NOT_FOUND = "NOT_FOUND"
    const val PAIRING_REQUEST_EXPIRED = "PAIRING_REQUEST_EXPIRED"
    const val PAIRING_REJECTED = "PAIRING_REJECTED"
    const val RATE_LIMITED = "RATE_LIMITED"
    const val DEVICE_REVOKED = "DEVICE_REVOKED"
    const val UNAUTHORIZED = "UNAUTHORIZED"
    const val CONFLICT_ACTIVE_CLIENT = "CONFLICT_ACTIVE_CLIENT"
    const val QUEUE_MISMATCH = "QUEUE_MISMATCH"
    const val PLAYER_REJECTED = "PLAYER_REJECTED"
    const val PLAYER_UNAVAILABLE = "PLAYER_UNAVAILABLE"
    const val SERVER_STOPPING = "SERVER_STOPPING"
}

/** JSON settings shared by every pairing exchange: unknown fields ignored (contract §1). */
val BridgeJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = true
}

/** Text content of the QR shown by the PC (contract §4.3). */
@Serializable
data class PairingQrPayload(
    val v: Int = BridgeContract.QR_VERSION,
    val type: String = BridgeContract.QR_TYPE,
    val requestId: String,
    val deviceName: String,
    val port: Int,
    val ips: List<String>,
) {
    fun toJson(): String = BridgeJson.encodeToString(serializer(), this)
}

/** Body of `POST /nzik-pair/v1/offer`, sent by the phone (contract §4.4). */
@Serializable
data class PairingOffer(
    val v: Int,
    val requestId: String,
    val code: String,
    val serverIps: List<String>,
    val serverPort: Int,
    val serverName: String,
)

@Serializable
data class OfferAccepted(val accepted: Boolean = true)

/** `POST /api/v1/pairing/validate` request (contract §4.5); `requestId = null` on the manual path. */
@Serializable
data class ValidateRequest(
    val code: String,
    val requestId: String?,
    val deviceName: String,
)

/** `POST /api/v1/pairing/validate` `200` response (contract §4.5). */
@Serializable
data class ValidateResponse(
    val deviceToken: String,
    val deviceId: String,
    val serverName: String,
    val serverPort: Int,
)

/** `GET /api/v1/meta` response (contract §5). */
@Serializable
data class MetaResponse(
    val contractVersion: String,
    val serverName: String,
    val serverTimeMs: Long = 0,
    val features: List<String> = emptyList(),
)

/** `activeDevice` of a `409 CONFLICT_ACTIVE_CLIENT` (contract §3, §6.2). */
@Serializable
data class ActiveDevice(val deviceId: String, val deviceName: String)

/** REST error body (contract §3). The client decides from [code], never from [message]. */
@Serializable
data class ApiError(
    val code: String,
    val message: String = "",
    val retryAfterMs: Long? = null,
    val activeDevice: ActiveDevice? = null,
    /** Current revision, carried by `409 QUEUE_MISMATCH` (contract §3). */
    val revision: Long? = null,
)

/** Contract-level helpers that are pure functions, kept here so every caller agrees. */
object PairingRules {
    private val IPV4 = Regex("""^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$""")
    private val MAJOR_ONE = Regex("""^1\.\d+""")

    /** §5: the v1 client accepts any `contractVersion` of the form `1.x`. */
    fun isSupportedContractVersion(version: String): Boolean = MAJOR_ONE.containsMatchIn(version.trim())

    /** §4.1: upper case, spaces and dashes removed. */
    fun normalizeCode(raw: String): String = raw.uppercase().filterNot { it.isWhitespace() || it == '-' }

    /** §4.1: a normalised code is 6 characters of the alphabet. */
    fun isWellFormedCode(normalized: String): Boolean =
        normalized.length == BridgeContract.CODE_LENGTH && normalized.all { it in BridgeContract.CODE_ALPHABET }

    /** §4.3 / §4.5: `deviceName` is 1–64 characters after trim; `null` when out of range. */
    fun normalizeDeviceName(raw: String): String? =
        raw.trim().takeIf { it.length in 1..BridgeContract.DEVICE_NAME_MAX_LENGTH }

    fun ipv4ToInt(ip: String): Int? {
        val parts = IPV4.matchEntire(ip.trim())?.groupValues?.drop(1)?.map { it.toInt() } ?: return null
        if (parts.any { it > 255 }) return null
        return parts.fold(0) { acc, part -> (acc shl 8) or part }
    }

    fun isIpv4(ip: String): Boolean = ipv4ToInt(ip) != null

    /** RFC 1918 (`10/8`, `172.16/12`, `192.168/16`), contract §4.3. */
    fun isPrivateIpv4(ip: String): Boolean {
        val value = ipv4ToInt(ip) ?: return false
        val first = value ushr 24
        val second = (value ushr 16) and 0xFF
        return first == 10 || (first == 172 && second in 16..31) || (first == 192 && second == 168)
    }

    fun isValidPort(port: Int): Boolean = port in 1..65_535
}
