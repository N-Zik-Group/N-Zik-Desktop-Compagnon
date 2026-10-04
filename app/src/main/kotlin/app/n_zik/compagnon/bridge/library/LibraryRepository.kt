package app.n_zik.compagnon.bridge.library

import app.n_zik.compagnon.bridge.state.Track
import app.n_zik.compagnon.bridge.state.TrackLike
import app.n_zik.compagnon.core.network.LibraryResult
import app.n_zik.compagnon.core.network.WriteResult

/**
 * What the library screens read, whatever serves it. [RemoteLibraryRepository] reads the phone's
 * bridge (contract §10) and, since 1.7 (`library.write`), writes its explicit states (contract
 * §10.2); nothing is ever kept on disk (§12).
 */
interface LibraryRepository {
    /** The phone's `features` (contract §5): a missing `library.*` hides its screen. */
    val features: Set<String>

    suspend fun songs(offset: Int, limit: Int, query: SongsQuery): LibraryResult<Track>
    suspend fun playlists(offset: Int, limit: Int, query: PlaylistsQuery): LibraryResult<Playlist>
    suspend fun albums(offset: Int, limit: Int, query: AlbumsQuery): LibraryResult<Album>
    suspend fun artists(offset: Int, limit: Int, query: ArtistsQuery): LibraryResult<Artist>

    // ---- Writes (contract §10.2, since 1.7): the explicit state, local Room only ----

    /** `POST /library/songs/{id}/like`. */
    suspend fun songLike(songId: String, state: TrackLike): WriteResult

    /** `POST /library/albums/{id}/bookmark`. */
    suspend fun albumBookmark(albumId: String, bookmarked: Boolean): WriteResult

    /** `POST /library/artists/{id}/follow`. */
    suspend fun artistFollow(artistId: String, state: ArtistFollow): WriteResult

    /** `POST /library/playlists/{id}/pin`. */
    suspend fun playlistPin(playlistId: String, pinned: Boolean): WriteResult

    /** `POST /library/albums/{id}/like` (contract §10.2, since 1.7.2): the phone's album tri-state. */
    suspend fun albumLike(albumId: String, state: AlbumLike): WriteResult

    /** `POST /library/playlists/{id}/bookmark` (contract §10.2, since 1.7.2): the phone's header bookmark. */
    suspend fun playlistBookmark(playlistId: String, bookmarked: Boolean): WriteResult

    /**
     * `GET /library/cache` (contract §10, since 1.7.1): the phone's disk caches, used vs configured
     * cap. Shown only with the phone's `library.cache` feature; `null` hides the bar.
     */
    suspend fun cacheSpace(): LibraryCache?

    /**
     * `GET /library/rewind` (contract §10, since 1.7.2): the phone's Month / Year / All row state.
     * Shown only with the phone's `library.rewind` feature; `null` hides the row.
     */
    suspend fun rewindState(): RewindState?

    /**
     * `GET /library/dislikeMode` (contract §10, since 1.7.2): the phone's "disliked" mode per
     * collection. `null` (feature absent or a failed read) keeps the pre-1.7.2 display: the phone's
     * own default is the mode enabled.
     */
    suspend fun dislikeMode(): DislikeMode?

    /**
     * The tracks of [collection], paginated. [query] (a local playlist's `sort` and `reverse`, contract
     * 1.6 `library.sort`) is sent only when it is not `null`; `null` for albums and artists, whose
     * tracks keep the phone's fixed order.
     */
    suspend fun collectionSongs(
        collection: CollectionRef,
        offset: Int,
        limit: Int,
        query: PlaylistSongsQuery? = null,
    ): LibraryResult<Track>

    /**
     * The first tracks of [collection], read page after page, up to [max] (the default is one more than
     * `/queue/play` takes, so that a longer collection is noticed and truncated with a notice).
     */
    suspend fun collectionTracks(collection: CollectionRef, max: Int = LibraryContract.TRACK_IDS_MAX + 1): LibraryResult<Track> {
        val tracks = mutableListOf<Track>()
        var total = Int.MAX_VALUE
        while (tracks.size < minOf(total, max)) {
            val limit = minOf(LibraryContract.PAGE_SIZE, max - tracks.size)
            when (val result = collectionSongs(collection, tracks.size, limit)) {
                is LibraryResult.Ok -> {
                    total = result.page.total
                    tracks += result.page.items
                    if (result.page.items.isEmpty()) break
                }
                else -> return result
            }
        }
        return LibraryResult.Ok(Page(tracks.take(max), total.coerceAtLeast(tracks.size), 0, tracks.size))
    }
}
