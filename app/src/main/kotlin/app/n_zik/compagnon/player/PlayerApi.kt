package app.n_zik.compagnon.player

import app.n_zik.compagnon.pairing.ApiError
import app.n_zik.compagnon.pairing.ServerAddress

/** Outcome of a §9 command over REST. */
sealed interface CommandResult {
    /** `200`: applied by the phone's choke point. */
    data class Ok(val response: CommandResponse) : CommandResult

    /** Any other answer; decided from [error]`.code`, never from its message. */
    data class Error(val status: Int, val error: ApiError?) : CommandResult

    data object Unreachable : CommandResult
}

/** Outcome of `GET /api/v1/artwork/{trackId}` (contract §10). */
sealed interface ArtworkResult {
    class Ok(val bytes: ByteArray) : ArtworkResult
    data object NotFound : ArtworkResult
    data class Failed(val status: Int, val code: String?) : ArtworkResult
    data object Unreachable : ArtworkResult
}

/** The Bearer REST calls of the player (contract §9, §10), implemented by story 10's `BridgeClient`. */
interface PlayerApi {
    /** `POST /api/v1/{route}` with the JSON [body]. */
    suspend fun command(address: ServerAddress, deviceToken: String, route: String, body: String): CommandResult

    suspend fun artwork(address: ServerAddress, deviceToken: String, trackId: String): ArtworkResult
}
