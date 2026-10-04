package app.n_zik.compagnon.components.ui.screens.home

import app.n_zik.compagnon.bridge.library.Album
import app.n_zik.compagnon.bridge.library.AlbumsQuery
import app.n_zik.compagnon.bridge.library.Artist
import app.n_zik.compagnon.bridge.library.ArtistsQuery
import app.n_zik.compagnon.bridge.library.CollectionFilter
import app.n_zik.compagnon.bridge.library.CollectionRef
import app.n_zik.compagnon.bridge.library.LibraryRepository
import app.n_zik.compagnon.bridge.library.Page
import app.n_zik.compagnon.bridge.library.Playlist
import app.n_zik.compagnon.bridge.library.PlaylistSongsQuery
import app.n_zik.compagnon.bridge.library.PlaylistsQuery
import app.n_zik.compagnon.bridge.library.SongsQuery
import app.n_zik.compagnon.bridge.state.SessionContract
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
        var albumReads = 0
        var artistReads = 0

        override val features: Set<String> = setOf("library.songs", "library.playlists")
        override suspend fun songs(offset: Int, limit: Int, query: SongsQuery): LibraryResult<Track> {
            songQueries += query
            return LibraryResult.Ok(Page(emptyList(), 0, offset, limit))
        }
        override suspend fun playlists(offset: Int, limit: Int, query: PlaylistsQuery): LibraryResult<Playlist> {
            playlistReads++
            return LibraryResult.Ok(Page(emptyList(), 0, offset, limit))
        }
        override suspend fun albums(offset: Int, limit: Int, query: AlbumsQuery): LibraryResult<Album> {
            albumReads++
            return LibraryResult.Ok(Page(emptyList(), 0, offset, limit))
        }
        override suspend fun artists(offset: Int, limit: Int, query: ArtistsQuery): LibraryResult<Artist> {
            artistReads++
            return LibraryResult.Ok(Page(emptyList(), 0, offset, limit))
        }
        override suspend fun collectionSongs(
            collection: CollectionRef,
            offset: Int,
            limit: Int,
            query: PlaylistSongsQuery?,
        ): LibraryResult<Track> {
            collectionReads += collection
            return if (mosaicFails) LibraryResult.Unreachable else LibraryResult.Ok(Page(listOf(Track("t1")), 1, offset, limit))
        }
    }

    /** A songs list of [count] tracks "t-1".."t-count", served by pages like the phone. */
    private class PagedLibrary(
        private val count: Int,
        override val features: Set<String> = setOf("library.songs"),
    ) : LibraryRepository {
        val reads = mutableListOf<Pair<Int, Int>>()
        val queries = mutableListOf<SongsQuery>()

        override suspend fun songs(offset: Int, limit: Int, query: SongsQuery): LibraryResult<Track> {
            reads += offset to limit
            queries += query
            val items = (offset until (offset + limit).coerceAtMost(count)).map { Track("t-${it + 1}") }
            return LibraryResult.Ok(Page(items, count, offset, limit))
        }
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
    }

    @Test
    fun `reverse shows a short list from its end, reversed, and never sends the flag to the phone`() = runTest {
        val library = PagedLibrary(2)
        val lists = LibraryLists(library, this)
        lists.songs.setQuery(SongsQuery(reverse = true))
        advanceUntilIdle()
        // the probe (0, 1) learns the total, then the whole list (0, 2) is read and reversed
        assertEquals(listOf(0 to 1, 0 to 2), library.reads)
        assertEquals(listOf("t-2", "t-1"), lists.songs.state.value.items.map { it.id })
        assertEquals(2, lists.songs.state.value.total)
        assertTrue(lists.songs.state.value.endReached)
        assertTrue(library.queries.none { it.reverse }, "reverse is a PC display choice, not a wire parameter")
    }

    @Test
    fun `reverse walks the phone's pages from the end, reversed`() = runTest {
        val library = PagedLibrary(250)
        val lists = LibraryLists(library, this)
        lists.songs.setQuery(SongsQuery(reverse = true))
        advanceUntilIdle()
        lists.songs.loadMore()
        advanceUntilIdle()
        lists.songs.loadMore()
        advanceUntilIdle()
        assertEquals(listOf(0 to 1, 150 to 100, 50 to 100, 0 to 50), library.reads)
        val items = lists.songs.state.value.items.map { it.id }
        assertEquals(250, items.size)
        assertEquals("t-250", items.first())
        assertEquals("t-151", items[99])
        assertEquals("t-150", items[100])
        assertEquals("t-1", items.last())
        assertTrue(lists.songs.state.value.endReached)
    }

    @Test
    fun `reverse ends on the phone's first page without duplicating items`() = runTest {
        val library = PagedLibrary(150)
        val lists = LibraryLists(library, this)
        lists.songs.setQuery(SongsQuery(reverse = true))
        advanceUntilIdle()
        lists.songs.loadMore()
        advanceUntilIdle()
        assertEquals(listOf(0 to 1, 50 to 100, 0 to 50), library.reads)
        val items = lists.songs.state.value.items.map { it.id }
        assertEquals(150, items.size)
        assertEquals(150, items.distinct().size)
        assertEquals("t-150", items.first())
        assertEquals("t-1", items.last())
    }

    @Test
    fun `with the library sort feature the reverse is sent to the phone, which re-sorts`() = runTest {
        val library = PagedLibrary(2, features = setOf("library.songs", SessionContract.FEATURE_LIBRARY_SORT))
        val lists = LibraryLists(library, this)
        lists.songs.setQuery(SongsQuery(reverse = true))
        advanceUntilIdle()
        // No probe, no client-side flip: one forward read, and the flag goes to the phone
        assertEquals(listOf(0 to 100), library.reads)
        assertEquals(listOf("t-1", "t-2"), lists.songs.state.value.items.map { it.id })
        assertTrue(library.queries.single().reverse)
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

    @Test
    fun `reload re-reads the list of the tab that became visible, and only it`() = runTest {
        val library = FakeLibrary()
        val lists = LibraryLists(library, this)

        lists.reload(LibraryTab.Songs)
        advanceUntilIdle()
        assertEquals(1, library.songQueries.size)
        assertEquals(0, library.albumReads)
        assertEquals(0, library.artistReads)

        lists.reload(LibraryTab.Artists)
        advanceUntilIdle()
        assertEquals(1, library.artistReads)
        assertEquals(1, library.songQueries.size)
        assertEquals(0, library.albumReads)

        // Playlists goes through reloadPlaylists: the playlists are re-read
        lists.reload(LibraryTab.Playlists)
        advanceUntilIdle()
        assertEquals(1, library.playlistReads)
    }
}
