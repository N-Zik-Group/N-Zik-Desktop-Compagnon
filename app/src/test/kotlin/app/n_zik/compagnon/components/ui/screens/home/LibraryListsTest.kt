package app.n_zik.compagnon.components.ui.screens.home

import app.n_zik.compagnon.bridge.library.Album
import app.n_zik.compagnon.bridge.library.Artist
import app.n_zik.compagnon.bridge.library.CollectionFilter
import app.n_zik.compagnon.bridge.library.CollectionRef
import app.n_zik.compagnon.bridge.library.LibraryRepository
import app.n_zik.compagnon.bridge.library.Page
import app.n_zik.compagnon.bridge.library.Playlist
import app.n_zik.compagnon.bridge.library.SongsQuery
import app.n_zik.compagnon.bridge.state.Track
import app.n_zik.compagnon.core.network.LibraryResult
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LibraryListsTest {

    /** Records the songs queries and the playlist reads; the mosaic read fails while [mosaicFails]. */
    private class FakeLibrary : LibraryRepository {
        val songQueries = mutableListOf<SongsQuery>()
        val collectionReads = mutableListOf<CollectionRef>()
        var playlistReads = 0
        var mosaicFails = false

        override val features: Set<String> = setOf("library.songs", "library.playlists")
        override suspend fun songs(offset: Int, limit: Int, query: SongsQuery): LibraryResult<Track> {
            songQueries += query
            return LibraryResult.Ok(Page(emptyList(), 0, offset, limit))
        }
        override suspend fun playlists(offset: Int, limit: Int): LibraryResult<Playlist> {
            playlistReads++
            return LibraryResult.Ok(Page(emptyList(), 0, offset, limit))
        }
        override suspend fun albums(offset: Int, limit: Int, filter: CollectionFilter): LibraryResult<Album> = error("unused")
        override suspend fun artists(offset: Int, limit: Int, filter: CollectionFilter): LibraryResult<Artist> = error("unused")
        override suspend fun collectionSongs(collection: CollectionRef, offset: Int, limit: Int): LibraryResult<Track> {
            collectionReads += collection
            return if (mosaicFails) LibraryResult.Unreachable else LibraryResult.Ok(Page(listOf(Track("t1")), 1, offset, limit))
        }
    }

    @Test
    fun `text typed within 300 ms gives one songs call with the last text`() = runTest {
        val library = FakeLibrary()
        val lists = LibraryLists(library, this)
        lists.onSongsSearch("a")
        advanceTimeBy(100)
        lists.onSongsSearch("ab")
        advanceTimeBy(100)
        lists.onSongsSearch("abc")
        advanceUntilIdle()
        assertEquals(listOf("abc"), library.songQueries.map { it.text })
        assertEquals("abc", lists.songsSearch.value)
    }

    @Test
    fun `blank text gives a null query`() = runTest {
        val library = FakeLibrary()
        val lists = LibraryLists(library, this)
        lists.onSongsSearch("ab")
        advanceUntilIdle()
        lists.onSongsSearch("   ")
        advanceUntilIdle()
        assertEquals(listOf("ab", null), library.songQueries.map { it.text })
    }

    @Test
    fun `a failed mosaic read is asked again, a successful one is not`() = runTest {
        val library = FakeLibrary()
        val lists = LibraryLists(library, this)
        library.mosaicFails = true
        lists.loadPlaylistFirstTracks("12")
        advanceUntilIdle()
        assertTrue(lists.playlistFirstTracks.value.isEmpty())

        library.mosaicFails = false
        lists.loadPlaylistFirstTracks("12")
        advanceUntilIdle()
        lists.loadPlaylistFirstTracks("12")
        advanceUntilIdle()
        assertEquals(2, library.collectionReads.size)
        assertEquals(listOf("t1"), lists.playlistFirstTracks.value["12"]?.map { it.id })
    }

    @Test
    fun `reloadPlaylists forgets the mosaics and reads the playlists again`() = runTest {
        val library = FakeLibrary()
        val lists = LibraryLists(library, this)
        lists.loadPlaylistFirstTracks("12")
        advanceUntilIdle()
        assertEquals(1, lists.playlistFirstTracks.value.size)

        lists.reloadPlaylists()
        advanceUntilIdle()
        assertTrue(lists.playlistFirstTracks.value.isEmpty())
        assertEquals(1, library.playlistReads)

        // The mosaic is read again after the reload
        lists.loadPlaylistFirstTracks("12")
        advanceUntilIdle()
        assertEquals(2, library.collectionReads.size)
    }
}
