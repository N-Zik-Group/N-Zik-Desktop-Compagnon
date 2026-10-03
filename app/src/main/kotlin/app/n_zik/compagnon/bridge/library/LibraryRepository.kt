package app.n_zik.compagnon.bridge.library

import app.n_zik.compagnon.bridge.state.Track
import app.n_zik.compagnon.core.network.LibraryResult

/**
 * What the library screens read, whatever serves it. [RemoteLibraryRepository] reads the phone's
 * bridge (contract §10); nothing is ever written to the phone's library, nor kept on disk (§12).
 */
interface LibraryRepository {
    /** The phone's `features` (contract §5): a missing `library.*` hides its screen. */
    val features: Set<String>

    suspend fun songs(offset: Int, limit: Int, query: SongsQuery): LibraryResult<Track>
    suspend fun playlists(offset: Int, limit: Int): LibraryResult<Playlist>
    suspend fun albums(offset: Int, limit: Int, filter: CollectionFilter): LibraryResult<Album>
    suspend fun artists(offset: Int, limit: Int, filter: CollectionFilter): LibraryResult<Artist>
    suspend fun collectionSongs(collection: CollectionRef, offset: Int, limit: Int): LibraryResult<Track>

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
