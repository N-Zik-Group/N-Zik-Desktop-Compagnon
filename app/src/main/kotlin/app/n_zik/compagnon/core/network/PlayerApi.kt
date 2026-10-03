package app.n_zik.compagnon.core.network

import app.n_zik.compagnon.bridge.library.LibraryContract
import app.n_zik.compagnon.bridge.pairing.ApiError
import app.n_zik.compagnon.bridge.state.CommandResponse
import app.n_zik.compagnon.bridge.state.Track

/** Outcome of a §9 command over REST. */
sealed interface CommandResult {
    /** `200`: applied by the phone's choke point. */
    data class Ok(val response: CommandResponse) : CommandResult

    /** Any other answer; decided from [error]`.code`, never from its message. */
    data class Error(val status: Int, val error: ApiError?) : CommandResult

    data object Unreachable : CommandResult
}

/** What an artwork belongs to; each kind has its own route (contract §10.1). */
enum class ArtworkKind {
    /** `GET /api/v1/artwork/{trackId}`; also a playlist's visual, through its `artworkTrackId`. */
    Track,

    /** `GET /api/v1/library/albums/{albumId}/artwork`. */
    Album,

    /** `GET /api/v1/library/artists/{artistId}/artwork`. */
    Artist,
}

/**
 * Cache key of an artwork: the same id under two kinds is two different images, and so is the same image
 * asked at two sizes. [size] is the side asked for (`size` of contract §10.1), bounded to 64–1200 px.
 */
data class ArtworkKey(val kind: ArtworkKind, val id: String, val size: Int = LibraryContract.ARTWORK_SIZE_PX) {
    companion object {
        fun track(trackId: String, size: Int = LibraryContract.ARTWORK_SIZE_PX) = ArtworkKey(ArtworkKind.Track, trackId, bounded(size))
        fun album(albumId: String, size: Int = LibraryContract.ARTWORK_SIZE_PX) = ArtworkKey(ArtworkKind.Album, albumId, bounded(size))
        fun artist(artistId: String, size: Int = LibraryContract.ARTWORK_SIZE_PX) = ArtworkKey(ArtworkKind.Artist, artistId, bounded(size))

        /** Contract §10.1: 64–1200 px. */
        fun bounded(size: Int): Int = size.coerceIn(LibraryContract.ARTWORK_SIZE_MIN_PX, LibraryContract.ARTWORK_SIZE_MAX_PX)
    }
}

/** Outcome of an artwork route (contract §10). */
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

    /** The image of [key] through its route, the id percent-encoded. */
    suspend fun artwork(address: ServerAddress, deviceToken: String, key: ArtworkKey): ArtworkResult
}
