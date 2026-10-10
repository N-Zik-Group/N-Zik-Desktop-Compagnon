package app.n_zik.compagnon

import app.n_zik.compagnon.bridge.library.Album
import app.n_zik.compagnon.bridge.library.AlbumLike
import app.n_zik.compagnon.bridge.library.AlbumsQuery
import app.n_zik.compagnon.bridge.library.Artist
import app.n_zik.compagnon.bridge.library.ArtistFollow
import app.n_zik.compagnon.bridge.library.ArtistsQuery
import app.n_zik.compagnon.bridge.library.CollectionRef
import app.n_zik.compagnon.bridge.library.DislikeMode
import app.n_zik.compagnon.bridge.library.LibraryCache
import app.n_zik.compagnon.bridge.library.LibraryRepository
import app.n_zik.compagnon.bridge.library.Page
import app.n_zik.compagnon.bridge.library.Playlist
import app.n_zik.compagnon.bridge.library.PlaylistSongsQuery
import app.n_zik.compagnon.bridge.library.PlaylistsQuery
import app.n_zik.compagnon.bridge.library.RewindState
import app.n_zik.compagnon.bridge.library.SongsQuery
import app.n_zik.compagnon.bridge.state.Track
import app.n_zik.compagnon.bridge.state.TrackLike
import app.n_zik.compagnon.bridge.state.UiSettings
import app.n_zik.compagnon.core.network.LibraryResult
import app.n_zik.compagnon.core.network.WriteResult
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * The PC's effective UI settings fetch-and-apply (spec `spec-remove-ui-sync`): each read of
 * `GET /ui/settings` (session start, then per `libraryChanged`) applies [UiSettings.toPcEffective]
 * — only the phone's `topN` carries over, the wire's appearance fields stay at the coded defaults,
 * and a failed read keeps the last value.
 */
class UiSettingsHolderTest {

    /** The `GET /ui/settings` read answers [answer]; every other route is an inert empty page. */
    private class FakeLibrary(var answer: UiSettings? = null) : LibraryRepository {
        var settingsReads = 0

        override val features: Set<String> = setOf("ui.settings")

        override suspend fun songs(offset: Int, limit: Int, query: SongsQuery): LibraryResult<Track> =
            LibraryResult.Ok(Page(emptyList(), 0, offset, limit))
        override suspend fun playlists(offset: Int, limit: Int, query: PlaylistsQuery): LibraryResult<Playlist> =
            LibraryResult.Ok(Page(emptyList(), 0, offset, limit))
        override suspend fun albums(offset: Int, limit: Int, query: AlbumsQuery): LibraryResult<Album> =
            LibraryResult.Ok(Page(emptyList(), 0, offset, limit))
        override suspend fun artists(offset: Int, limit: Int, query: ArtistsQuery): LibraryResult<Artist> =
            LibraryResult.Ok(Page(emptyList(), 0, offset, limit))
        override suspend fun collectionSongs(
            collection: CollectionRef,
            offset: Int,
            limit: Int,
            query: PlaylistSongsQuery?,
        ): LibraryResult<Track> = LibraryResult.Ok(Page(emptyList(), 0, offset, limit))

        // §10.2 writes: the phone's resulting state, answered as sent
        override suspend fun songLike(songId: String, state: TrackLike): WriteResult = WriteResult.SongLike(state)
        override suspend fun albumBookmark(albumId: String, bookmarked: Boolean): WriteResult = WriteResult.AlbumBookmark(bookmarked)
        override suspend fun artistFollow(artistId: String, state: ArtistFollow): WriteResult = WriteResult.ArtistFollow(state)
        override suspend fun playlistPin(playlistId: String, pinned: Boolean): WriteResult = WriteResult.PlaylistPin(pinned)
        override suspend fun albumLike(albumId: String, state: AlbumLike): WriteResult = WriteResult.AlbumLike(state)
        override suspend fun playlistBookmark(playlistId: String, bookmarked: Boolean): WriteResult =
            WriteResult.PlaylistBookmark(bookmarked)

        // §10 reads: not exercised by this holder
        override suspend fun cacheSpace(): LibraryCache? = null
        override suspend fun rewindState(): RewindState? = null
        override suspend fun dislikeMode(): DislikeMode? = null

        override suspend fun uiSettings(): UiSettings? {
            settingsReads++
            return answer
        }
    }

    @Test
    fun `a fetch stores only topN, a second fetch updates it on the coded defaults`() = runTest {
        val library = FakeLibrary()
        val holder = UiSettingsHolder(library)
        assertEquals(UiSettings(), holder.settings.value) // the coded defaults until a first read

        library.answer = UiSettings(colorPaletteName = "PureBlack", topN = 50)
        holder.fetch() // session start
        assertEquals(UiSettings(topN = 50), holder.settings.value) // the appearance at the coded defaults

        library.answer = UiSettings(colorPaletteName = "ModernBlack", topN = 25)
        holder.fetch() // the libraryChanged re-fetch
        val effective = holder.settings.value
        assertEquals(25, effective.topN)
        // The wire's appearance never reaches the holder: the coded defaults stand.
        assertEquals("Dynamic", effective.colorPaletteName)
        assertEquals("Dark", effective.colorPaletteMode)
        assertEquals(25f, effective.blurStrength)
        assertEquals(2, library.settingsReads)
    }

    @Test
    fun `a failed read keeps the last value`() = runTest {
        val library = FakeLibrary(UiSettings(topN = 30))
        val holder = UiSettingsHolder(library)
        holder.fetch()
        assertEquals(30, holder.settings.value.topN)

        library.answer = null // the phone dropped the feature, or the read failed
        holder.fetch()
        assertEquals(UiSettings(topN = 30), holder.settings.value)
        assertEquals(2, library.settingsReads)
    }
}
