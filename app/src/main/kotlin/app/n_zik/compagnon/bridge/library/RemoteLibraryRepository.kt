package app.n_zik.compagnon.bridge.library

import app.n_zik.compagnon.bridge.pairing.ActivePairing
import app.n_zik.compagnon.bridge.pairing.RevocationPolicy
import app.n_zik.compagnon.bridge.state.Track
import app.n_zik.compagnon.core.network.LibraryApi
import app.n_zik.compagnon.core.network.LibraryResult
import app.n_zik.compagnon.core.network.ServerAddress

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

    override suspend fun playlists(offset: Int, limit: Int): LibraryResult<Playlist> =
        guarded { api.playlists(address, deviceToken, offset, limit) }

    override suspend fun albums(offset: Int, limit: Int, filter: CollectionFilter): LibraryResult<Album> =
        guarded { api.albums(address, deviceToken, offset, limit, filter) }

    override suspend fun artists(offset: Int, limit: Int, filter: CollectionFilter): LibraryResult<Artist> =
        guarded { api.artists(address, deviceToken, offset, limit, filter) }

    override suspend fun collectionSongs(collection: CollectionRef, offset: Int, limit: Int): LibraryResult<Track> =
        guarded { api.collectionSongs(address, deviceToken, collection, offset, limit) }

    private suspend fun <T> guarded(call: suspend () -> LibraryResult<T>): LibraryResult<T> =
        revocation.confirmRest(call = call, isRevoked = { it is LibraryResult.Revoked }).result

    companion object {
        fun create(active: ActivePairing, api: LibraryApi, revocation: RevocationPolicy): RemoteLibraryRepository =
            RemoteLibraryRepository(api, active.address, active.pairing.deviceToken, active.features, revocation)
    }
}
