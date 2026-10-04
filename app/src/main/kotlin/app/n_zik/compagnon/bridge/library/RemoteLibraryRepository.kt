package app.n_zik.compagnon.bridge.library

import app.n_zik.compagnon.bridge.pairing.ActivePairing
import app.n_zik.compagnon.bridge.pairing.RevocationPolicy
import app.n_zik.compagnon.bridge.state.Track
import app.n_zik.compagnon.bridge.state.TrackLike
import app.n_zik.compagnon.core.network.LibraryApi
import app.n_zik.compagnon.core.network.LibraryResult
import app.n_zik.compagnon.core.network.ServerAddress
import app.n_zik.compagnon.core.network.WriteResult

/**
 * [LibraryRepository] backed by the phone's bridge (contract §10), through story 10's client. A
 * `401 DEVICE_REVOKED` goes through [RevocationPolicy.confirmRest]: only a confirmed one erases the
 * pairing; an unconfirmed one gives back the second answer.
 */
class RemoteLibraryRepository(
    private val api: LibraryApi,
    private val address: ServerAddress,
    private val deviceToken: String,
    override val features: Set<String>,
    private val revocation: RevocationPolicy,
) : LibraryRepository {

    override suspend fun songs(offset: Int, limit: Int, query: SongsQuery): LibraryResult<Track> =
        guarded { api.songs(address, deviceToken, offset, limit, query) }

    override suspend fun playlists(offset: Int, limit: Int, query: PlaylistsQuery): LibraryResult<Playlist> =
        guarded { api.playlists(address, deviceToken, offset, limit, query) }

    override suspend fun albums(offset: Int, limit: Int, query: AlbumsQuery): LibraryResult<Album> =
        guarded { api.albums(address, deviceToken, offset, limit, query) }

    override suspend fun artists(offset: Int, limit: Int, query: ArtistsQuery): LibraryResult<Artist> =
        guarded { api.artists(address, deviceToken, offset, limit, query) }

    override suspend fun collectionSongs(
        collection: CollectionRef,
        offset: Int,
        limit: Int,
        query: PlaylistSongsQuery?,
    ): LibraryResult<Track> =
        guarded { api.collectionSongs(address, deviceToken, collection, offset, limit, query) }

    override suspend fun songLike(songId: String, state: TrackLike): WriteResult =
        guardedWrite { api.songLike(address, deviceToken, songId, state) }

    override suspend fun albumBookmark(albumId: String, bookmarked: Boolean): WriteResult =
        guardedWrite { api.albumBookmark(address, deviceToken, albumId, bookmarked) }

    override suspend fun artistFollow(artistId: String, state: ArtistFollow): WriteResult =
        guardedWrite { api.artistFollow(address, deviceToken, artistId, state) }

    override suspend fun playlistPin(playlistId: String, pinned: Boolean): WriteResult =
        guardedWrite { api.playlistPin(address, deviceToken, playlistId, pinned) }

    override suspend fun albumLike(albumId: String, state: AlbumLike): WriteResult =
        guardedWrite { api.albumLike(address, deviceToken, albumId, state) }

    override suspend fun playlistBookmark(playlistId: String, bookmarked: Boolean): WriteResult =
        guardedWrite { api.playlistBookmark(address, deviceToken, playlistId, bookmarked) }

    /**
     * The cache bar is a secondary read: a `401 DEVICE_REVOKED` simply hides it (the main library
     * reads carry the §6.2 confirmation).
     */
    override suspend fun cacheSpace(): LibraryCache? = api.cacheSpace(address, deviceToken)

    /** The rewind row and the dislike mode are secondary reads, like the cache bar. */
    override suspend fun rewindState(): RewindState? = api.rewindState(address, deviceToken)

    override suspend fun dislikeMode(): DislikeMode? = api.dislikeMode(address, deviceToken)

    private suspend fun <T> guarded(call: suspend () -> LibraryResult<T>): LibraryResult<T> =
        revocation.confirmRest(call = call, isRevoked = { it is LibraryResult.Revoked }).result

    /** The §10.2 writes go through the same `401 DEVICE_REVOKED` confirmation (contract §6.2). */
    private suspend fun guardedWrite(call: suspend () -> WriteResult): WriteResult =
        revocation.confirmRest(call = call, isRevoked = { it is WriteResult.Revoked }).result

    companion object {
        fun create(active: ActivePairing, api: LibraryApi, revocation: RevocationPolicy): RemoteLibraryRepository =
            RemoteLibraryRepository(api, active.address, active.pairing.deviceToken, active.features, revocation)
    }
}
