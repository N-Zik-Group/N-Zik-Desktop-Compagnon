package app.n_zik.compagnon.core.network

import app.n_zik.compagnon.bridge.library.Album
import app.n_zik.compagnon.bridge.library.AlbumsQuery
import app.n_zik.compagnon.bridge.library.Artist
import app.n_zik.compagnon.bridge.library.ArtistsQuery
import app.n_zik.compagnon.bridge.library.CollectionRef
import app.n_zik.compagnon.bridge.library.LibraryCache
import app.n_zik.compagnon.bridge.library.Page
import app.n_zik.compagnon.bridge.library.Playlist
import app.n_zik.compagnon.bridge.library.PlaylistSongsQuery
import app.n_zik.compagnon.bridge.library.PlaylistsQuery
import app.n_zik.compagnon.bridge.library.AlbumLike
import app.n_zik.compagnon.bridge.library.ArtistFollow
import app.n_zik.compagnon.bridge.library.DislikeMode
import app.n_zik.compagnon.bridge.library.RewindState
import app.n_zik.compagnon.bridge.library.SongsQuery
import app.n_zik.compagnon.bridge.state.Track
import app.n_zik.compagnon.bridge.state.TrackLike
import app.n_zik.compagnon.bridge.state.ListRef
import app.n_zik.compagnon.bridge.state.UiSettings
import kotlinx.serialization.Serializable

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

/**
 * Outcome of a library write (contract §10.2, since 1.7), decided from the status and error `code`.
 * The `200` answers carry the resulting state — the source of truth of the UI's optimistic update.
 */
sealed interface WriteResult {
    /** `200` of `POST /library/songs/{id}/like`. */
    data class SongLike(val state: TrackLike) : WriteResult

    /** `200` of `POST /library/albums/{id}/bookmark`. */
    data class AlbumBookmark(val bookmarked: Boolean) : WriteResult

    /**
     * `200` of `POST /library/artists/{id}/follow`. [state] is the fully-qualified wire enum: inside this
     * class the simple name `ArtistFollow` would resolve to this class itself.
     */
    data class ArtistFollow(val state: app.n_zik.compagnon.bridge.library.ArtistFollow) : WriteResult

    /** `200` of `POST /library/playlists/{id}/pin`. */
    data class PlaylistPin(val pinned: Boolean) : WriteResult

    /**
     * `200` of `POST /library/albums/{id}/like` (since 1.7.2). [state] is the fully-qualified wire
     * enum: inside this class the simple name `AlbumLike` would resolve to this class itself.
     */
    data class AlbumLike(val state: app.n_zik.compagnon.bridge.library.AlbumLike) : WriteResult

    /** `200` of `POST /library/playlists/{id}/bookmark` (since 1.7.2). */
    data class PlaylistBookmark(val bookmarked: Boolean) : WriteResult

    /** `404 NOT_FOUND`: unknown track, album, artist or playlist. */
    data object NotFound : WriteResult

    /** `401 DEVICE_REVOKED`: to be confirmed by `RevocationPolicy.confirmRest`. */
    data object Revoked : WriteResult

    /** `409 CONFLICT_ACTIVE_CLIENT`: another PC holds the session (contract §6.2). */
    data class OtherActive(val deviceName: String?) : WriteResult

    /** Any other answer (`5xx`, `400`, undecodable body…). */
    data class Failed(val status: Int, val code: String?) : WriteResult

    /** The phone did not answer. */
    data object Unreachable : WriteResult
}

/** `200` answer of `GET /library/locate` (contract §10.4, since 1.10.0): `-1` when the track is not in the list. */
@Serializable
data class LocateAnswer(val index: Int, val total: Int = 0)

/** The `200` answer bodies of contract §10.2 (since 1.7): the resulting state. */
@Serializable
data class SongLikeAnswer(val state: TrackLike)

@Serializable
data class AlbumBookmarkAnswer(val bookmarked: Boolean)

@Serializable
data class ArtistFollowAnswer(val state: ArtistFollow)

@Serializable
data class PlaylistPinAnswer(val pinned: Boolean)

@Serializable
data class AlbumLikeAnswer(val state: AlbumLike)

@Serializable
data class PlaylistBookmarkAnswer(val bookmarked: Boolean)

/** The library routes of contract §10.1, implemented by `BridgeClient`. */
interface LibraryApi {
    suspend fun songs(
        address: ServerAddress,
        deviceToken: String,
        offset: Int,
        limit: Int,
        query: SongsQuery,
    ): LibraryResult<Track>

    /** `sort` and `reverse` of contract 1.6, sent when the phone has `library.sort`. */
    suspend fun playlists(address: ServerAddress, deviceToken: String, offset: Int, limit: Int, query: PlaylistsQuery): LibraryResult<Playlist>

    suspend fun albums(address: ServerAddress, deviceToken: String, offset: Int, limit: Int, query: AlbumsQuery): LibraryResult<Album>

    suspend fun artists(address: ServerAddress, deviceToken: String, offset: Int, limit: Int, query: ArtistsQuery): LibraryResult<Artist>

    /**
     * `GET /library/{playlists|albums|artists}/{id}/songs`, the id percent-encoded. [query] (a local
     * playlist's `sort` and `reverse`, contract §10.1, since 1.6) is sent only when it is not `null`.
     */
    suspend fun collectionSongs(
        address: ServerAddress,
        deviceToken: String,
        collection: CollectionRef,
        offset: Int,
        limit: Int,
        query: PlaylistSongsQuery? = null,
    ): LibraryResult<Track>

    // ---- Writes (contract §10.2, since 1.7): the explicit state, local Room only ----

    /** `POST /library/songs/{id}/like`, the id percent-encoded. */
    suspend fun songLike(address: ServerAddress, deviceToken: String, songId: String, state: TrackLike): WriteResult

    /** `POST /library/albums/{id}/bookmark`, the id percent-encoded. */
    suspend fun albumBookmark(address: ServerAddress, deviceToken: String, albumId: String, bookmarked: Boolean): WriteResult

    /** `POST /library/artists/{id}/follow`, the id percent-encoded. */
    suspend fun artistFollow(address: ServerAddress, deviceToken: String, artistId: String, state: ArtistFollow): WriteResult

    /** `POST /library/playlists/{id}/pin`, the id percent-encoded. */
    suspend fun playlistPin(address: ServerAddress, deviceToken: String, playlistId: String, pinned: Boolean): WriteResult

    /** `POST /library/albums/{id}/like` (since 1.7.2), the id percent-encoded. */
    suspend fun albumLike(address: ServerAddress, deviceToken: String, albumId: String, state: AlbumLike): WriteResult

    /** `POST /library/playlists/{id}/bookmark` (since 1.7.2), the id percent-encoded. */
    suspend fun playlistBookmark(address: ServerAddress, deviceToken: String, playlistId: String, bookmarked: Boolean): WriteResult

    /**
     * `GET /library/cache` (contract §10, since 1.7.1): the phone's disk caches, used vs configured
     * cap. `null` on any non-`200` answer (unreachable, error, revocation) — the UI hides its bar;
     * the §6.2 revocation confirmation rides on the main library reads.
     */
    suspend fun cacheSpace(address: ServerAddress, deviceToken: String): LibraryCache?

    /**
     * `GET /library/rewind` (contract §10, since 1.7.2, feature `library.rewind`): the phone's Month /
     * Year / All row state. `null` on any non-`200` answer (unreachable, error, revocation, a phone
     * before 1.7.2) — the UI hides its row.
     */
    suspend fun rewindState(address: ServerAddress, deviceToken: String): RewindState?

    /**
     * `GET /library/dislikeMode` (contract §10, since 1.7.2, feature `library.dislikeMode`): the
     * phone's "disliked" mode per collection. `null` on any non-`200` answer — the UI keeps its
     * pre-1.7.2 display (its default: the mode enabled, its chips shown).
     */
    suspend fun dislikeMode(address: ServerAddress, deviceToken: String): DislikeMode?

    /**
     * `GET /library/locate` (contract §10.4, since 1.10.0, feature `library.locate`): the position of
     * [trackId] in the WHOLE list [list]; `-1` when absent, `null` on any non-`200` answer.
     */
    suspend fun locate(address: ServerAddress, deviceToken: String, list: ListRef, trackId: String): LocateAnswer? = null

    /**
     * `GET /ui/settings` (contract §10.5, since 1.10.0, feature `ui.settings`): the phone's UI settings.
     * `null` on any non-`200` answer — the PC keeps the phone's defaults.
     */
    suspend fun uiSettings(address: ServerAddress, deviceToken: String): UiSettings? = null
}
