package app.n_zik.compagnon.components.ui.screens.home

import app.n_zik.compagnon.bridge.library.Album
import app.n_zik.compagnon.bridge.library.AlbumLike
import app.n_zik.compagnon.bridge.library.AlbumsQuery
import app.n_zik.compagnon.bridge.library.Artist
import app.n_zik.compagnon.bridge.library.ArtistFollow
import app.n_zik.compagnon.bridge.library.ArtistsQuery
import app.n_zik.compagnon.bridge.library.CollectionFilter
import app.n_zik.compagnon.bridge.library.CollectionRef
import app.n_zik.compagnon.bridge.library.DislikeMode
import app.n_zik.compagnon.bridge.library.LibraryCache
import app.n_zik.compagnon.bridge.library.LibraryRepository
import app.n_zik.compagnon.bridge.library.Page
import app.n_zik.compagnon.bridge.library.Playlist
import app.n_zik.compagnon.bridge.library.PlaylistSongsQuery
import app.n_zik.compagnon.bridge.library.PlaylistsFilter
import app.n_zik.compagnon.bridge.library.PlaylistsQuery
import app.n_zik.compagnon.bridge.library.RewindState
import app.n_zik.compagnon.bridge.library.SongFilter
import app.n_zik.compagnon.bridge.library.SongsQuery
import app.n_zik.compagnon.bridge.state.SessionContract
import app.n_zik.compagnon.bridge.state.Track
import app.n_zik.compagnon.bridge.state.TrackLike
import app.n_zik.compagnon.core.network.LibraryResult
import app.n_zik.compagnon.core.network.WriteResult
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
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

        // §10.2 writes (since 1.7): the phone's resulting state, answered as sent
        override suspend fun songLike(songId: String, state: TrackLike): WriteResult = WriteResult.SongLike(state)
        override suspend fun albumBookmark(albumId: String, bookmarked: Boolean): WriteResult = WriteResult.AlbumBookmark(bookmarked)
        override suspend fun artistFollow(artistId: String, state: ArtistFollow): WriteResult = WriteResult.ArtistFollow(state)
        override suspend fun playlistPin(playlistId: String, pinned: Boolean): WriteResult = WriteResult.PlaylistPin(pinned)

        // §10.2 writes (since 1.7.2): the phone's resulting state, answered as sent
        override suspend fun albumLike(albumId: String, state: AlbumLike): WriteResult = WriteResult.AlbumLike(state)
        override suspend fun playlistBookmark(playlistId: String, bookmarked: Boolean): WriteResult =
            WriteResult.PlaylistBookmark(bookmarked)

        // §10 (since 1.7.2): the rewind / "disliked" mode reads are not exercised by these lists
        override suspend fun rewindState(): RewindState? = null
        override suspend fun dislikeMode(): DislikeMode? = null

        // §10 (since 1.7.1): the cache bar is not exercised by these lists
        override suspend fun cacheSpace(): LibraryCache? = null
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

        // §10.2 writes (since 1.7): the phone's resulting state, answered as sent
        override suspend fun songLike(songId: String, state: TrackLike): WriteResult = WriteResult.SongLike(state)
        override suspend fun albumBookmark(albumId: String, bookmarked: Boolean): WriteResult = WriteResult.AlbumBookmark(bookmarked)
        override suspend fun artistFollow(artistId: String, state: ArtistFollow): WriteResult = WriteResult.ArtistFollow(state)
        override suspend fun playlistPin(playlistId: String, pinned: Boolean): WriteResult = WriteResult.PlaylistPin(pinned)

        // §10.2 writes (since 1.7.2): the phone's resulting state, answered as sent
        override suspend fun albumLike(albumId: String, state: AlbumLike): WriteResult = WriteResult.AlbumLike(state)
        override suspend fun playlistBookmark(playlistId: String, bookmarked: Boolean): WriteResult =
            WriteResult.PlaylistBookmark(bookmarked)

        // §10 (since 1.7.2): the rewind / "disliked" mode reads are not exercised by these lists
        override suspend fun rewindState(): RewindState? = null
        override suspend fun dislikeMode(): DislikeMode? = null

        // §10 (since 1.7.1): the cache bar is not exercised by these lists
        override suspend fun cacheSpace(): LibraryCache? = null
    }

    /** Paged lists of [albumsCount] / [artistsCount] / [playlistsCount] items, served by pages like the phone. */
    private class PagedCollections(
        private val albumsCount: Int = 0,
        private val artistsCount: Int = 0,
        private val playlistsCount: Int = 0,
    ) : LibraryRepository {
        override val features: Set<String> = setOf("library.songs")
        override suspend fun albums(offset: Int, limit: Int, query: AlbumsQuery): LibraryResult<Album> {
            val items = (offset until (offset + limit).coerceAtMost(albumsCount)).map { Album("a-${it + 1}") }
            return LibraryResult.Ok(Page(items, albumsCount, offset, limit))
        }
        override suspend fun artists(offset: Int, limit: Int, query: ArtistsQuery): LibraryResult<Artist> {
            val items = (offset until (offset + limit).coerceAtMost(artistsCount)).map { Artist("ar-${it + 1}") }
            return LibraryResult.Ok(Page(items, artistsCount, offset, limit))
        }
        override suspend fun playlists(offset: Int, limit: Int, query: PlaylistsQuery): LibraryResult<Playlist> {
            val items = (offset until (offset + limit).coerceAtMost(playlistsCount)).map { Playlist("p-${it + 1}") }
            return LibraryResult.Ok(Page(items, playlistsCount, offset, limit))
        }
        override suspend fun songs(offset: Int, limit: Int, query: SongsQuery): LibraryResult<Track> =
            LibraryResult.Ok(Page(emptyList(), 0, offset, limit))
        override suspend fun collectionSongs(
            collection: CollectionRef,
            offset: Int,
            limit: Int,
            query: PlaylistSongsQuery?,
        ): LibraryResult<Track> = LibraryResult.Ok(Page(emptyList(), 0, offset, limit))

        // §10.2 writes (since 1.7): the phone's resulting state, answered as sent
        override suspend fun songLike(songId: String, state: TrackLike): WriteResult = WriteResult.SongLike(state)
        override suspend fun albumBookmark(albumId: String, bookmarked: Boolean): WriteResult = WriteResult.AlbumBookmark(bookmarked)
        override suspend fun artistFollow(artistId: String, state: ArtistFollow): WriteResult = WriteResult.ArtistFollow(state)
        override suspend fun playlistPin(playlistId: String, pinned: Boolean): WriteResult = WriteResult.PlaylistPin(pinned)

        // §10.2 writes (since 1.7.2): the phone's resulting state, answered as sent
        override suspend fun albumLike(albumId: String, state: AlbumLike): WriteResult = WriteResult.AlbumLike(state)
        override suspend fun playlistBookmark(playlistId: String, bookmarked: Boolean): WriteResult =
            WriteResult.PlaylistBookmark(bookmarked)

        // §10 (since 1.7.2): the rewind / "disliked" mode reads are not exercised by these lists
        override suspend fun rewindState(): RewindState? = null
        override suspend fun dislikeMode(): DislikeMode? = null

        // §10 (since 1.7.1): the cache bar is not exercised by these lists
        override suspend fun cacheSpace(): LibraryCache? = null
    }

    // ---- Live (contract §7.2, since 1.7.3 `library.live`) ----

    @Test
    fun `a libraryChanged reloads the loaded family only, coalesced`() = runTest {
        val library = FakeLibrary()
        val lists = LibraryLists(library, this)

        // Nothing is loaded yet: the delta has no list to re-read
        lists.onLibraryChanged("songs")
        advanceUntilIdle()
        assertEquals(0, library.songQueries.size)

        // Once the songs are loaded, a songs delta re-reads them from the first page
        lists.songs.loadMore()
        advanceUntilIdle()
        lists.onLibraryChanged("songs")
        advanceTimeBy(100)
        assertEquals(1, library.songQueries.size, "the coalescing window is still open")
        advanceUntilIdle()
        assertEquals(2, library.songQueries.size)

        // A burst of deltas coalesces into one reload per family (the families are loaded:
        // a family never read has nothing to re-read)
        lists.albums.loadMore()
        lists.artists.loadMore()
        lists.playlists.loadMore()
        advanceUntilIdle()
        val albumsBefore = library.albumReads
        val artistsBefore = library.artistReads
        val playlistsBefore = library.playlistReads
        lists.onLibraryChanged("albums")
        lists.onLibraryChanged("artists")
        lists.onLibraryChanged("playlists")
        advanceUntilIdle()
        assertEquals(albumsBefore + 1, library.albumReads)
        assertEquals(artistsBefore + 1, library.artistReads)
        assertEquals(playlistsBefore + 1, library.playlistReads)

        // A family the contract does not name reloads nothing
        val before = library.songQueries.size
        lists.onLibraryChanged("lyrics")
        advanceUntilIdle()
        assertEquals(before, library.songQueries.size)
    }

    @Test
    fun `a confirmed write patches the loaded songs in place on the All list`() = runTest {
        val library = PagedLibrary(5)
        val lists = LibraryLists(library, this)
        lists.songs.loadMore()
        advanceUntilIdle()
        assertEquals(listOf("t-1", "t-2", "t-3", "t-4", "t-5"), lists.songs.state.value.items.map { it.id })

        // A like flips the row's state in place; the row stays on the All chip
        lists.patchSongLike("t-2", TrackLike.Liked)
        val liked = lists.songs.state.value.items.first { it.id == "t-2" }
        assertEquals(TrackLike.Liked, liked.like)
        assertTrue(liked.isLiked)
        assertEquals(5, lists.songs.state.value.items.size)
        assertEquals(5, lists.songs.state.value.total)

        // The phone's home tabs hide their disliked rows in every chip but the Disliked one:
        // a dislike drops the row from the All chip
        lists.patchSongLike("t-2", TrackLike.Disliked)
        assertEquals(4, lists.songs.state.value.items.size)
        assertEquals(4, lists.songs.state.value.total)
        assertTrue(lists.songs.state.value.items.none { it.id == "t-2" })
    }

    @Test
    fun `a write drops the row from a membership chip its state no longer matches, and shrinks the total`() = runTest {
        val library = PagedLibrary(5)
        val lists = LibraryLists(library, this)

        // On the Liked chip, a song whose state leaves the chip (a dislike) drops from the list
        lists.songs.setQuery(SongsQuery(filter = SongFilter.Liked))
        lists.songs.loadMore()
        advanceUntilIdle()
        lists.patchSongLike("t-2", TrackLike.Disliked)
        assertEquals(4, lists.songs.state.value.items.size)
        assertEquals(4, lists.songs.state.value.total)
        assertTrue(lists.songs.state.value.items.none { it.id == "t-2" })

        // On the Disliked chip, the row stays while its state matches, and leaves when it rotates away
        lists.songs.setQuery(SongsQuery(filter = SongFilter.Disliked))
        lists.songs.loadMore()
        advanceUntilIdle()
        assertEquals(5, lists.songs.state.value.items.size)
        lists.patchSongLike("t-2", TrackLike.Neutral)
        assertEquals(4, lists.songs.state.value.items.size)
        assertEquals(4, lists.songs.state.value.total)
        assertTrue(lists.songs.state.value.items.none { it.id == "t-2" })
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

    // ---- Chip-aware patches of the other three entities (since 1.7) ----

    @Test
    fun `an album bookmark follows the phone's chip semantics`() = runTest {
        val lists = LibraryLists(PagedCollections(albumsCount = 5), this)
        lists.albums.loadMore()
        advanceUntilIdle()
        assertEquals(listOf("a-1", "a-2", "a-3", "a-4", "a-5"), lists.albums.state.value.items.map { it.id })

        // Library list: the row only flips its flag — even a bookmark, which clears the phone's dislike
        lists.patchAlbumBookmark("a-2", true)
        assertTrue(lists.albums.state.value.items.first { it.id == "a-2" }.isBookmarked)
        assertEquals(5, lists.albums.state.value.items.size)
        assertEquals(5, lists.albums.state.value.total)

        // On the Bookmarked chip, unbooking drops the row and shrinks the total
        lists.albums.setQuery(AlbumsQuery(filter = CollectionFilter.Bookmarked))
        lists.albums.loadMore()
        advanceUntilIdle()
        lists.patchAlbumBookmark("a-2", false)
        assertEquals(4, lists.albums.state.value.items.size)
        assertEquals(4, lists.albums.state.value.total)
        assertTrue(lists.albums.state.value.items.none { it.id == "a-2" })

        // On the Disliked chip, a bookmark drops the row: the phone's bookmarkState clears the dislike
        lists.albums.setQuery(AlbumsQuery(filter = CollectionFilter.Disliked))
        lists.albums.loadMore()
        advanceUntilIdle()
        lists.patchAlbumBookmark("a-2", true)
        assertEquals(4, lists.albums.state.value.items.size)
        assertEquals(4, lists.albums.state.value.total)
        assertTrue(lists.albums.state.value.items.none { it.id == "a-2" })
    }

    @Test
    fun `an artist follow follows the phone's chip semantics`() = runTest {
        val lists = LibraryLists(PagedCollections(artistsCount = 5), this)
        lists.artists.loadMore()
        advanceUntilIdle()
        assertEquals(listOf("ar-1", "ar-2", "ar-3", "ar-4", "ar-5"), lists.artists.state.value.items.map { it.id })

        // Library list: the row only flips its flags — the phone keeps its disliked artists, shown in red
        lists.patchArtistFollow("ar-2", ArtistFollow.Followed)
        val followed = lists.artists.state.value.items.first { it.id == "ar-2" }
        assertTrue(followed.isBookmarked)
        assertFalse(followed.isDisliked)
        lists.patchArtistFollow("ar-2", ArtistFollow.Disliked)
        val disliked = lists.artists.state.value.items.first { it.id == "ar-2" }
        assertFalse(disliked.isBookmarked)
        assertTrue(disliked.isDisliked)
        assertEquals(5, lists.artists.state.value.items.size)
        assertEquals(5, lists.artists.state.value.total)

        // On the Disliked chip, a state that leaves the chip drops the row and shrinks the total
        lists.artists.setQuery(ArtistsQuery(filter = CollectionFilter.Disliked))
        lists.artists.loadMore()
        advanceUntilIdle()
        lists.patchArtistFollow("ar-2", ArtistFollow.Neutral)
        assertEquals(4, lists.artists.state.value.items.size)
        assertEquals(4, lists.artists.state.value.total)
        assertTrue(lists.artists.state.value.items.none { it.id == "ar-2" })
    }

    @Test
    fun `a playlist pin follows the phone's chip semantics`() = runTest {
        val lists = LibraryLists(PagedCollections(playlistsCount = 5), this)
        lists.playlists.loadMore()
        advanceUntilIdle()
        assertEquals(listOf("p-1", "p-2", "p-3", "p-4", "p-5"), lists.playlists.state.value.items.map { it.id })

        // All list: the row only flips its flag
        lists.patchPlaylistPin("p-2", true)
        assertTrue(lists.playlists.state.value.items.first { it.id == "p-2" }.isPinned)
        assertEquals(5, lists.playlists.state.value.items.size)
        assertEquals(5, lists.playlists.state.value.total)

        // On the Pinned chip, unpinning drops the row and shrinks the total
        lists.playlists.setQuery(PlaylistsQuery(filter = PlaylistsFilter.Pinned))
        lists.playlists.loadMore()
        advanceUntilIdle()
        lists.patchPlaylistPin("p-2", false)
        assertEquals(4, lists.playlists.state.value.items.size)
        assertEquals(4, lists.playlists.state.value.total)
        assertTrue(lists.playlists.state.value.items.none { it.id == "p-2" })
    }
}
