package app.n_zik.compagnon.core.network

import app.n_zik.compagnon.bridge.library.Album
import app.n_zik.compagnon.bridge.library.Artist
import app.n_zik.compagnon.bridge.library.CollectionFilter
import app.n_zik.compagnon.bridge.library.CollectionRef
import app.n_zik.compagnon.bridge.library.Page
import app.n_zik.compagnon.bridge.library.Playlist
import app.n_zik.compagnon.bridge.library.SongsQuery
import app.n_zik.compagnon.bridge.state.Track

/** Outcome of a library read (contract §10), decided from the error `code`. */
sealed interface LibraryResult<out T> {
    data class Ok<T>(val page: Page<T>) : LibraryResult<T>

    /** `404 NOT_FOUND`: unknown playlist, album or artist. */
    data object NotFound : LibraryResult<Nothing>

    /** `401 DEVICE_REVOKED`: to be confirmed by `RevocationPolicy.confirmRest`. */
    data object Revoked : LibraryResult<Nothing>

    /** `409 CONFLICT_ACTIVE_CLIENT`: another PC holds the session (contract §6.2). */
    data class OtherActive(val deviceName: String?) : LibraryResult<Nothing>

    /** Any other answer (`5xx`, `400`, undecodable body…). */
    data class Failed(val status: Int, val code: String?) : LibraryResult<Nothing>

    /** The phone did not answer. */
    data object Unreachable : LibraryResult<Nothing>
}

/** The read-only library routes of contract §10.1, implemented by `BridgeClient`. */
interface LibraryApi {
    suspend fun songs(
        address: ServerAddress,
        deviceToken: String,
        offset: Int,
        limit: Int,
        query: SongsQuery,
    ): LibraryResult<Track>

    suspend fun playlists(address: ServerAddress, deviceToken: String, offset: Int, limit: Int): LibraryResult<Playlist>

    suspend fun albums(address: ServerAddress, deviceToken: String, offset: Int, limit: Int, filter: CollectionFilter): LibraryResult<Album>

    suspend fun artists(address: ServerAddress, deviceToken: String, offset: Int, limit: Int, filter: CollectionFilter): LibraryResult<Artist>

    /** `GET /library/{playlists|albums|artists}/{id}/songs`, the id percent-encoded. */
    suspend fun collectionSongs(
        address: ServerAddress,
        deviceToken: String,
        collection: CollectionRef,
        offset: Int,
        limit: Int,
    ): LibraryResult<Track>
}
