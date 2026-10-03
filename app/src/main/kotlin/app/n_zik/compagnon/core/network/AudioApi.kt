package app.n_zik.compagnon.core.network

import app.n_zik.compagnon.bridge.pairing.ApiError
import java.nio.file.Path
import kotlinx.serialization.Serializable

/** `POST /api/v1/audio/{trackId}/url` body (contract §8.1); [quality] is the wire value of `Quality` (§1.1). */
@Serializable
data class AudioUrlRequest(val quality: String)

/** `200` answer of the forge (contract §8.1). [url] is a transfer credential: never printed nor stored. */
@Serializable
data class AudioUrlResponse(
    val trackId: String,
    val quality: String = "auto",
    val url: String,
    val expiresAtMs: Long = 0,
    val durationMs: Long? = null,
) {
    override fun toString(): String = "AudioUrlResponse(trackId=$trackId, quality=$quality, url=***, expiresAtMs=$expiresAtMs)"
}

/** Outcome of the forge. */
sealed interface ForgeResult {
    data class Ok(val response: AudioUrlResponse) : ForgeResult

    /** Decided from [error]`.code` (`404`, `401 DEVICE_REVOKED`, `409`…). */
    data class Error(val status: Int, val error: ApiError?) : ForgeResult

    data object Unreachable : ForgeResult
}

/** What a signed audio URL answers now (contract §8.2), asked after the local player failed on it. */
sealed interface AudioProbe {
    /** The URL serves: the failure was elsewhere (decoding, a dropped connection…). */
    data object Ok : AudioProbe

    /** `403 AUDIO_URL_EXPIRED`: re-forge. */
    data object Expired : AudioProbe

    /** `403 AUDIO_URL_INVALID` (or a `403` without readable code): re-forge once, then fail. */
    data object Invalid : AudioProbe

    /** `401 DEVICE_REVOKED`: terminal, unless within the kick window (§8.4). */
    data object Revoked : AudioProbe

    /** `404 NOT_FOUND`. */
    data object NotFound : AudioProbe

    /** `502 AUDIO_UPSTREAM_FAILED`. */
    data object UpstreamFailed : AudioProbe

    data class Failed(val status: Int) : AudioProbe
    data object Unreachable : AudioProbe
}

/** Outcome of a cache download. */
sealed interface DownloadResult {
    /** The whole body is in the target file; [bytes] long. */
    data class Done(val bytes: Long) : DownloadResult
    data class Failed(val status: Int?) : DownloadResult
}

/** The audio calls of the local player (contract §8), implemented by `BridgeClient`. */
interface AudioApi {
    /** `POST /api/v1/audio/{trackId}/url` (Bearer), the id percent-encoded. */
    suspend fun forgeAudioUrl(address: ServerAddress, deviceToken: String, trackId: String, quality: String): ForgeResult

    /**
     * `HEAD` on the signed [url]. A `HEAD` answer has no body, so a `403` is asked again as a one-byte
     * `GET` (`Range: bytes=0-0`) to read its `code`.
     */
    suspend fun probeAudioUrl(url: String): AudioProbe

    /** `GET` of the whole signed [url] into [target] (cache fill); the body only, written as it arrives. */
    suspend fun downloadAudio(url: String, target: Path): DownloadResult
}
