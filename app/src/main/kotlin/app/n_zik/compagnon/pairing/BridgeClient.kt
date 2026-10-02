package app.n_zik.compagnon.pairing

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.timeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import app.n_zik.compagnon.player.ArtworkResult
import app.n_zik.compagnon.player.CommandResponse
import app.n_zik.compagnon.player.CommandResult
import app.n_zik.compagnon.player.PlayerApi
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.encodeURLPathPart
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import java.util.logging.Logger

/** Phone address received by pairing (contract §1: never a hard-coded port). */
data class ServerAddress(val ip: String, val port: Int) {
    val apiBase: String get() = "http://$ip:$port${BridgeContract.API_PREFIX}"
}

/** Outcome of `GET /api/v1/meta` (contract §5). */
sealed interface MetaResult {
    data class Ok(val meta: MetaResponse) : MetaResult
    /** Major version other than 1: "phone version incompatible", nothing paired. */
    data class Incompatible(val contractVersion: String) : MetaResult
    data object Unreachable : MetaResult
    data class Failed(val status: Int, val code: String?) : MetaResult
}

/** Outcome of `POST /api/v1/pairing/validate` (contract §4.5). */
sealed interface ValidateResult {
    data class Ok(val response: ValidateResponse) : ValidateResult
    /** `403 PAIRING_REJECTED`: "code invalid or expired". */
    data object Rejected : ValidateResult
    /** `429 RATE_LIMITED`: wait [retryAfterMs]. */
    data class RateLimited(val retryAfterMs: Long) : ValidateResult
    data object Unreachable : ValidateResult
    data class Failed(val status: Int, val code: String?) : ValidateResult
}

/** Outcome of the light Bearer probe, `GET /api/v1/library/songs?limit=1`. */
sealed interface ProbeResult {
    data object Ok : ProbeResult
    /** `401 DEVICE_REVOKED`: to be confirmed by [RevocationPolicy] before anything is erased. */
    data object Revoked : ProbeResult
    /** `409 CONFLICT_ACTIVE_CLIENT`: another PC holds the session (contract §6.2). */
    data class OtherActive(val deviceName: String?) : ProbeResult
    data object Unreachable : ProbeResult
    /** Any other answer, including a `401` that is not `DEVICE_REVOKED`: never erases anything. */
    data class Failed(val status: Int, val code: String?) : ProbeResult
}

/** The pairing-side bridge calls, behind an interface so the state machine is testable without a network. */
interface BridgeApi {
    suspend fun meta(address: ServerAddress): MetaResult
    suspend fun validate(address: ServerAddress, request: ValidateRequest): ValidateResult
    suspend fun probe(address: ServerAddress, deviceToken: String): ProbeResult
}

/**
 * Ktor client of the phone's bridge. Errors are mapped from the contract §3 `code`, never from
 * `message`. The device token travels only in the `Authorization` header and is never logged.
 */
class BridgeClient(engine: HttpClientEngine = CIO.create()) : BridgeApi, PlayerApi, AutoCloseable {
    private val log = Logger.getLogger("BridgeClient")

    private val http = HttpClient(engine) {
        expectSuccess = false
        install(ContentNegotiation) { json(BridgeJson) }
        install(HttpTimeout) {
            connectTimeoutMillis = CONNECT_TIMEOUT_MS
            requestTimeoutMillis = REQUEST_TIMEOUT_MS
            socketTimeoutMillis = REQUEST_TIMEOUT_MS
        }
    }

    override suspend fun meta(address: ServerAddress): MetaResult {
        val response = call(address, "meta") { http.get("${address.apiBase}/meta") } ?: return MetaResult.Unreachable
        if (response.status != HttpStatusCode.OK) return MetaResult.Failed(response.status.value, response.errorBody()?.code)
        val meta = response.decode(MetaResponse.serializer()) ?: return MetaResult.Failed(response.status.value, null)
        return if (PairingRules.isSupportedContractVersion(meta.contractVersion)) MetaResult.Ok(meta)
        else MetaResult.Incompatible(meta.contractVersion)
    }

    override suspend fun validate(address: ServerAddress, request: ValidateRequest): ValidateResult {
        val response = call(address, "pairing/validate") {
            http.post("${address.apiBase}/pairing/validate") {
                contentType(ContentType.Application.Json)
                setBody(BridgeJson.encodeToString(ValidateRequest.serializer(), request))
            }
        } ?: return ValidateResult.Unreachable
        if (response.status == HttpStatusCode.OK) {
            val body = response.decode(ValidateResponse.serializer()) ?: return ValidateResult.Failed(200, null)
            return ValidateResult.Ok(body)
        }
        val error = response.errorBody()
        return when (error?.code) {
            BridgeErrorCode.PAIRING_REJECTED -> ValidateResult.Rejected
            BridgeErrorCode.RATE_LIMITED -> ValidateResult.RateLimited(error.retryAfterMs ?: 0L)
            else -> ValidateResult.Failed(response.status.value, error?.code)
        }
    }

    override suspend fun probe(address: ServerAddress, deviceToken: String): ProbeResult {
        val response = call(address, "library/songs") {
            http.get("${address.apiBase}${BridgeContract.PROBE_PATH}") {
                parameter("limit", 1)
                bearerAuth(deviceToken)
            }
        } ?: return ProbeResult.Unreachable
        if (response.status == HttpStatusCode.OK) return ProbeResult.Ok
        val error = response.errorBody()
        return when (error?.code) {
            BridgeErrorCode.DEVICE_REVOKED -> ProbeResult.Revoked
            BridgeErrorCode.CONFLICT_ACTIVE_CLIENT -> ProbeResult.OtherActive(error.activeDevice?.deviceName)
            else -> ProbeResult.Failed(response.status.value, error?.code)
        }
    }

    override suspend fun command(address: ServerAddress, deviceToken: String, route: String, body: String): CommandResult {
        val response = call(address, route) {
            http.post("${address.apiBase}/$route") {
                bearerAuth(deviceToken)
                contentType(ContentType.Application.Json)
                setBody(body)
            }
        } ?: return CommandResult.Unreachable
        if (response.status == HttpStatusCode.OK) {
            val decoded = response.decode(CommandResponse.serializer()) ?: return CommandResult.Error(200, null)
            return CommandResult.Ok(decoded)
        }
        return CommandResult.Error(response.status.value, response.errorBody())
    }

    override suspend fun artwork(address: ServerAddress, deviceToken: String, trackId: String): ArtworkResult {
        // Contract §1: an id in a URL path is percent-encoded.
        val response = call(address, "artwork") {
            http.get("${address.apiBase}/artwork/${trackId.encodeURLPathPart()}") {
                bearerAuth(deviceToken)
                // The phone fetches online artwork upstream before answering: allow more than a command
                timeout {
                    requestTimeoutMillis = ARTWORK_TIMEOUT_MS
                    socketTimeoutMillis = ARTWORK_TIMEOUT_MS
                }
            }
        } ?: return ArtworkResult.Unreachable
        if (response.status == HttpStatusCode.OK) {
            return runCatching { ArtworkResult.Ok(response.bodyAsBytes()) }.getOrElse { ArtworkResult.Unreachable }
        }
        val code = response.errorBody()?.code
        return if (code == BridgeErrorCode.NOT_FOUND) ArtworkResult.NotFound else ArtworkResult.Failed(response.status.value, code)
    }

    override fun close() = http.close()

    /** `null` when the phone could not be reached (refused, timeout, unknown host…). */
    private suspend fun call(address: ServerAddress, route: String, block: suspend () -> HttpResponse): HttpResponse? =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.info("${address.ip}:${address.port} $route unreachable: ${e::class.simpleName}")
            null
        }

    private suspend fun HttpResponse.errorBody(): ApiError? =
        runCatching { BridgeJson.decodeFromString(ApiError.serializer(), bodyAsText()) }.getOrNull()

    private suspend fun <T> HttpResponse.decode(serializer: kotlinx.serialization.KSerializer<T>): T? =
        runCatching { BridgeJson.decodeFromString(serializer, bodyAsText()) }.getOrNull()

    private companion object {
        const val CONNECT_TIMEOUT_MS = 3_000L
        const val REQUEST_TIMEOUT_MS = 8_000L
        const val ARTWORK_TIMEOUT_MS = 20_000L
    }
}
