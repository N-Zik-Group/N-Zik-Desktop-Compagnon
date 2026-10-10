package app.n_zik.compagnon.components.ui.screens.home

import androidx.compose.ui.graphics.ImageBitmap
import app.n_zik.compagnon.bridge.ConnectionState
import app.n_zik.compagnon.bridge.command.PlayWindow
import app.n_zik.compagnon.bridge.library.Album
import app.n_zik.compagnon.bridge.library.AlbumLike
import app.n_zik.compagnon.bridge.library.Artist
import app.n_zik.compagnon.bridge.library.ArtistFollow
import app.n_zik.compagnon.bridge.library.CollectionKind
import app.n_zik.compagnon.bridge.library.CollectionRef
import app.n_zik.compagnon.bridge.library.DislikeMode
import app.n_zik.compagnon.bridge.library.LibraryCache
import app.n_zik.compagnon.bridge.library.LibraryRepository
import app.n_zik.compagnon.bridge.library.AlbumsQuery
import app.n_zik.compagnon.bridge.library.ArtistsQuery
import app.n_zik.compagnon.bridge.library.Page
import app.n_zik.compagnon.bridge.library.PagedList
import app.n_zik.compagnon.bridge.library.PagedState
import app.n_zik.compagnon.bridge.library.Playlist
import app.n_zik.compagnon.bridge.library.PlaylistSongsQuery
import app.n_zik.compagnon.bridge.library.PlaylistsQuery
import app.n_zik.compagnon.bridge.library.RewindState
import app.n_zik.compagnon.bridge.library.SongFilter
import app.n_zik.compagnon.bridge.library.SongsQuery
import app.n_zik.compagnon.bridge.state.ListAction
import app.n_zik.compagnon.bridge.state.ListRef
import app.n_zik.compagnon.bridge.state.PlayerNotice
import app.n_zik.compagnon.bridge.library.toListRef
import app.n_zik.compagnon.bridge.state.PlayerRepository
import app.n_zik.compagnon.bridge.state.PlayerState
import app.n_zik.compagnon.bridge.state.AudioOutput
import app.n_zik.compagnon.bridge.state.QueuePosition
import app.n_zik.compagnon.bridge.state.RepeatMode
import app.n_zik.compagnon.bridge.state.Track
import app.n_zik.compagnon.bridge.state.TrackLike
import app.n_zik.compagnon.bridge.state.withTrackLike
import app.n_zik.compagnon.core.network.ArtworkKey
import app.n_zik.compagnon.core.network.LibraryResult
import app.n_zik.compagnon.core.network.WriteResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LibraryActionsTest {

    private sealed interface Sent {
        data class Play(val ids: List<String>, val startIndex: Int, val total: Int) : Sent
        data class Add(val ids: List<String>, val position: QueuePosition, val total: Int) : Sent
        data class WholeList(val list: ListRef, val action: ListAction, val startIndex: Int, val startTrackId: String?) : Sent
    }

    /** Records `queue/play` / `queue/add`; every other command is unused here. */
    private class FakePlayer(
        override val features: Set<String> = setOf("playback", "queue"),
        initial: PlayerState? = null,
    ) : PlayerRepository {
        val sent = mutableListOf<Sent>()
        private val mutableState = MutableStateFlow(initial)
        override val state: StateFlow<PlayerState?> = mutableState
        /** Every like patch, in order (the optimistic one, then the confirmed or the rollback). */
        val likePatches = mutableListOf<Pair<String, TrackLike>>()
        override fun patchTrackLike(trackId: String, like: TrackLike) {
            likePatches += trackId to like
            mutableState.value = mutableState.value?.withTrackLike(trackId, like)
        }
        override val connection: StateFlow<ConnectionState> = MutableStateFlow(ConnectionState.Live)
        override val notices: SharedFlow<PlayerNotice> = MutableSharedFlow()
        override val libraryChanged: SharedFlow<String> = MutableSharedFlow()
        override fun serverNowMs(): Long = 0
        override suspend fun artwork(key: ArtworkKey): ImageBitmap? = null
        override fun cachedArtwork(key: ArtworkKey): ImageBitmap? = null
        override suspend fun play() = Unit
        override suspend fun pause() = Unit
        override suspend fun seek(positionMs: Long) = Unit
        override suspend fun next() = Unit
        override suspend fun previous() = Unit
        override suspend fun setSpeed(speed: Float) = Unit
        override suspend fun setRepeat(mode: RepeatMode) = Unit
        override suspend fun setShuffle(enabled: Boolean) = Unit
        override suspend fun setAudioOutput(output: AudioOutput) = Unit
        override fun inKickWindow(): Boolean = false
        override suspend fun jump(index: Int, trackId: String) = Unit
        override suspend fun remove(index: Int, trackId: String) = Unit
        override suspend fun move(fromIndex: Int, toIndex: Int, trackId: String) = Unit
        override suspend fun clearQueue() = Unit
        override suspend fun playTracks(trackIds: List<String>, startIndex: Int, total: Int) {
            sent += Sent.Play(trackIds, startIndex, total)
        }
        override suspend fun addTracks(trackIds: List<String>, position: QueuePosition, total: Int) {
            sent += Sent.Add(trackIds, position, total)
        }
        override suspend fun playList(list: ListRef, action: ListAction, startIndex: Int, startTrackId: String?) {
            sent += Sent.WholeList(list, action, startIndex, startTrackId)
        }
        override fun start() = Unit
        override fun reconnect() = Unit
        override fun close() = Unit
    }

    /** A collection of [size] tracks "t0", "t1"…, served by pages; or always [failure] when set. */
    private class FakeLibrary(private val size: Int, private val failure: LibraryResult<Track>? = null) : LibraryRepository {
        override val features: Set<String> = setOf("library.albums")
        override suspend fun songs(offset: Int, limit: Int, query: SongsQuery): LibraryResult<Track> =
            failure ?: LibraryResult.Ok(Page((offset until minOf(offset + limit, size)).map { Track("t$it") }, size, offset, limit))
        override suspend fun playlists(offset: Int, limit: Int, query: PlaylistsQuery): LibraryResult<Playlist> = error("unused")
        override suspend fun albums(offset: Int, limit: Int, query: AlbumsQuery): LibraryResult<Album> = error("unused")
        override suspend fun artists(offset: Int, limit: Int, query: ArtistsQuery): LibraryResult<Artist> = error("unused")
        override suspend fun collectionSongs(
            collection: CollectionRef,
            offset: Int,
            limit: Int,
            query: PlaylistSongsQuery?,
        ): LibraryResult<Track> =
            failure ?: LibraryResult.Ok(Page((offset until minOf(offset + limit, size)).map { Track("t$it") }, size, offset, limit))

        // §10.2 writes (since 1.7): the requested state is recorded, the answer is configurable
        val songLikes = mutableListOf<Pair<String, TrackLike>>()
        var songLikeAnswer: WriteResult = WriteResult.SongLike(TrackLike.Liked)
        override suspend fun songLike(songId: String, state: TrackLike): WriteResult {
            songLikes += songId to state
            return songLikeAnswer
        }

        val albumBookmarks = mutableListOf<Pair<String, Boolean>>()
        override suspend fun albumBookmark(albumId: String, bookmarked: Boolean): WriteResult {
            albumBookmarks += albumId to bookmarked
            return WriteResult.AlbumBookmark(bookmarked)
        }

        val artistFollows = mutableListOf<Pair<String, ArtistFollow>>()
        override suspend fun artistFollow(artistId: String, state: ArtistFollow): WriteResult {
            artistFollows += artistId to state
            return WriteResult.ArtistFollow(state)
        }

        val playlistPins = mutableListOf<Pair<String, Boolean>>()
        override suspend fun playlistPin(playlistId: String, pinned: Boolean): WriteResult {
            playlistPins += playlistId to pinned
            return WriteResult.PlaylistPin(pinned)
        }

        val albumLikes = mutableListOf<Pair<String, AlbumLike>>()
        override suspend fun albumLike(albumId: String, state: AlbumLike): WriteResult {
            albumLikes += albumId to state
            return WriteResult.AlbumLike(state)
        }

        val playlistBookmarks = mutableListOf<Pair<String, Boolean>>()
        override suspend fun playlistBookmark(playlistId: String, bookmarked: Boolean): WriteResult {
            playlistBookmarks += playlistId to bookmarked
            return WriteResult.PlaylistBookmark(bookmarked)
        }

        // §10 (since 1.7.1): the cache bar is not exercised by these actions
        override suspend fun cacheSpace(): LibraryCache? = null

        // §10 (since 1.7.2): the rewind / "disliked" mode reads are not exercised by these actions
        override suspend fun rewindState(): RewindState? = null
        override suspend fun dislikeMode(): DislikeMode? = null
    }

    private val album = CollectionRef(CollectionKind.Album, "MPREb_x")

    @Test
    fun `Play on an album of 12 tracks sends the 12 ids from index 0`() = runTest {
        val player = FakePlayer()
        val actions = LibraryActions(player, FakeLibrary(12), this) {}
        actions.collectionActions(album, live = true)!!.onPlay()
        advanceUntilIdle()
        assertEquals(listOf<Sent>(Sent.Play(List(12) { "t$it" }, 0, 12)), player.sent)
    }

    @Test
    fun `a collection over 500 tracks hands its real size for the notice`() = runTest {
        val player = FakePlayer()
        val actions = LibraryActions(player, FakeLibrary(730), this) {}
        val menu = actions.collectionActions(CollectionRef(CollectionKind.Artist, "UCx"), live = true)!!
        menu.onPlay()
        menu.onEnqueue()
        advanceUntilIdle()
        val play = player.sent[0] as Sent.Play
        assertEquals(501, play.ids.size)
        assertEquals(730, play.total)
        assertEquals(Sent.Add(List(501) { "t$it" }, QueuePosition.End, 730), player.sent[1])
    }

    @Test
    fun `Shuffle sends the same ids mixed from index 0`() = runTest {
        val player = FakePlayer()
        val actions = LibraryActions(player, FakeLibrary(12), this) {}
        actions.collectionActions(album, live = true)!!.onShuffle!!()
        advanceUntilIdle()
        val play = player.sent.single() as Sent.Play
        assertEquals(0, play.startIndex)
        assertEquals(List(12) { "t$it" }.sorted(), play.ids.sorted())
    }

    @Test
    fun `track menu adds next or at the end`() = runTest {
        val player = FakePlayer()
        val actions = LibraryActions(player, FakeLibrary(0), this) {}
        val tracks = listOf(Track("a"), Track("b"))
        val menu = actions.trackActions(MutableStateFlow(PagedState(items = tracks)), 1, "b", live = true)!!
        menu.onPlayNext()
        menu.onEnqueue()
        advanceUntilIdle()
        assertEquals(listOf<Sent>(Sent.Add(listOf("b"), QueuePosition.Next, 1), Sent.Add(listOf("b"), QueuePosition.End, 1)), player.sent)
    }

    @Test
    fun `a track menu sends nothing once the list changed under it`() = runTest {
        val player = FakePlayer()
        val actions = LibraryActions(player, FakeLibrary(0), this) {}
        val tracks = MutableStateFlow(PagedState(items = listOf(Track("a"), Track("b"))))
        val menu = actions.trackActions(tracks, 1, "b", live = true)!!
        // A reload put another track at index 1
        tracks.value = PagedState(items = listOf(Track("a"), Track("x")))
        menu.onPlay()
        menu.onPlayNext()
        menu.onEnqueue()
        advanceUntilIdle()
        assertEquals(emptyList<Sent>(), player.sent)
    }

    @Test
    fun `without the queue feature the menus open with inert entries, as on the phone`() = runTest {
        val actions = LibraryActions(FakePlayer(features = setOf("playback")), FakeLibrary(12), this) {}
        assertNull(actions.collectionActions(album, live = true))
        assertFalse(actions.collectionMenuActions(album, live = true).enabled)
        assertFalse(actions.trackActions(MutableStateFlow(PagedState(items = emptyList())), 0, "a", live = true)!!.enabled)
    }

    // ---- Contract 1.10.0 (`queue.fullList`): the phone builds the queue from the WHOLE list ----

    private val fullListFeatures = setOf("playback", "queue", "queue.fullList")

    @Test
    fun `a click on a list of 2000 with 100 loaded hands the whole list to the phone`() = runTest {
        val player = FakePlayer(fullListFeatures)
        val actions = LibraryActions(player, FakeLibrary(2_000), this) {}
        val loaded = List(100) { Track("t$it") }
        val ref = SongsQuery(text = null, filter = SongFilter.Liked).toListRef()
        actions.playFrom(loaded, 42, "t42", ref)
        advanceUntilIdle()
        assertEquals(listOf<Sent>(Sent.WholeList(ref, ListAction.Play, 42, "t42")), player.sent)
    }

    @Test
    fun `shuffle, play next and enqueue go to the phone's own entries with the feature`() = runTest {
        val player = FakePlayer(fullListFeatures)
        val actions = LibraryActions(player, FakeLibrary(0), this) {}
        val ref = SongsQuery().toListRef()
        actions.playShuffled(emptyList(), 0, ref)
        actions.addAll(emptyList(), QueuePosition.Next, 0, ref)
        actions.addAll(emptyList(), QueuePosition.End, 0, ref)
        advanceUntilIdle()
        assertEquals(
            listOf<Sent>(
                Sent.WholeList(ref, ListAction.Shuffle, 0, null),
                Sent.WholeList(ref, ListAction.Next, 0, null),
                Sent.WholeList(ref, ListAction.End, 0, null),
            ),
            player.sent,
        )
    }

    @Test
    fun `without the feature the loaded pages are sent as before`() = runTest {
        val player = FakePlayer()
        val actions = LibraryActions(player, FakeLibrary(0), this) {}
        val loaded = listOf(Track("a"), Track("b"))
        actions.playFrom(loaded, 1, "b", SongsQuery().toListRef())
        actions.addAll(loaded, QueuePosition.End, 2, SongsQuery().toListRef())
        advanceUntilIdle()
        assertEquals(listOf<Sent>(Sent.Play(listOf("a", "b"), 1, 2), Sent.Add(listOf("a", "b"), QueuePosition.End, 2)), player.sent)
    }

    @Test
    fun `a PC-only list without a reference keeps the loaded pages even with the feature`() = runTest {
        val player = FakePlayer(fullListFeatures)
        val actions = LibraryActions(player, FakeLibrary(0), this) {}
        actions.playFrom(listOf(Track("a")), 0, "a", listRef = null)
        advanceUntilIdle()
        assertEquals(listOf<Sent>(Sent.Play(listOf("a"), 0, 1)), player.sent)
    }

    @Test
    fun `collection actions name the collection to the phone with the feature`() = runTest {
        val player = FakePlayer(fullListFeatures)
        val actions = LibraryActions(player, FakeLibrary(12), this) {}
        val menu = actions.collectionActions(album, live = true)!!
        menu.onPlay()
        menu.onShuffle!!()
        advanceUntilIdle()
        val ref = ListRef(kind = "album", id = "MPREb_x")
        assertEquals(listOf<Sent>(Sent.WholeList(ref, ListAction.Play, 0, null), Sent.WholeList(ref, ListAction.Shuffle, 0, null)), player.sent)
    }

    @Test
    fun `list references carry the route parameters`() {
        val songs = SongsQuery("abc", SongFilter.Top, app.n_zik.compagnon.bridge.library.SongSort.PlayCount, true, app.n_zik.compagnon.bridge.library.TopPeriod.Week).toListRef()
        assertEquals(ListRef("songs", null, "top", "playCount", true, "week", "abc"), songs)
        val playlist = CollectionRef(CollectionKind.Playlist, "7").toListRef(PlaylistSongsQuery(text = "x"))
        assertEquals(ListRef("playlist", "7", null, "custom", false, null, "x"), playlist)
    }

    @Test
    fun `a tab is hidden without its library feature, in the phone's order`() {
        assertEquals(
            listOf(LibraryTab.Songs, LibraryTab.Albums, LibraryTab.Playlists),
            LibraryTab.visible(setOf("library.playlists", "library.songs", "library.albums", "queue")),
        )
        assertEquals(emptyList<LibraryTab>(), LibraryTab.visible(setOf("playback", "queue")))
    }

    /** Texts come from the compose resources, read on a real I/O thread: wait for them in real time. */
    private suspend fun awaitMessages(messages: List<String>) = withContext(Dispatchers.Default) {
        withTimeoutOrNull(5_000) { while (messages.isEmpty()) delay(10) }
        delay(50)
    }

    private fun tracks(n: Int) = List(n) { Track("t$it") }

    @Test
    fun `a click on the 3rd of 300 loaded tracks sends the 300 ids from index 2`() = runTest {
        val player = FakePlayer()
        LibraryActions(player, FakeLibrary(0), this) {}.playFrom(tracks(300), 2)
        advanceUntilIdle()
        assertEquals(listOf<Sent>(Sent.Play(List(300) { "t$it" }, 2, 300)), player.sent)
    }

    @Test
    fun `a click on the 650th of 900 hands every id, the window of 500 keeps the track`() = runTest {
        val player = FakePlayer()
        LibraryActions(player, FakeLibrary(0), this) {}.playFrom(tracks(900), 649)
        advanceUntilIdle()
        val play = player.sent.single() as Sent.Play
        assertEquals(900, play.ids.size)
        assertEquals(649, play.startIndex)
        // What playTracks then sends (its notice is tested in RemotePlayerRepositoryTest)
        val window = PlayWindow.around(play.ids, play.startIndex)
        assertEquals(500, window.trackIds.size)
        assertEquals("t649", window.trackIds[window.startIndex])
    }

    @Test
    fun `a click never plays another track when the list changed meanwhile`() = runTest {
        val player = FakePlayer()
        val actions = LibraryActions(player, FakeLibrary(0), this) {}
        val now = listOf(Track("x"), Track("t0"), Track("t1"))
        actions.playFrom(now, 1, expectedId = "t1")
        actions.playFrom(now, 0, expectedId = "gone")
        advanceUntilIdle()
        assertEquals(listOf<Sent>(Sent.Play(listOf("x", "t0", "t1"), 2, 3)), player.sent)
    }

    @Test
    fun `collection read failures and an empty collection give one message and send nothing`() = runTest {
        val failures = listOf(
            LibraryResult.NotFound,
            LibraryResult.Unreachable,
            LibraryResult.OtherActive("PC-2"),
            LibraryResult.Failed(503, "PLAYER_UNAVAILABLE"),
            null,
        )
        for (failure in failures) {
            val player = FakePlayer()
            val messages = mutableListOf<String>()
            val actions = LibraryActions(player, FakeLibrary(0, failure), this) { messages += it }
            actions.collectionActions(album, live = true)!!.onPlay()
            advanceUntilIdle()
            awaitMessages(messages)
            assertEquals(emptyList<Sent>(), player.sent, "$failure")
            assertEquals(1, messages.size, "$failure")
        }
    }

    @Test
    fun `the toolbar shuffles the loaded tracks and adds them all`() = runTest {
        val player = FakePlayer()
        val messages = mutableListOf<String>()
        val actions = LibraryActions(player, FakeLibrary(0), this) { messages += it }
        actions.playShuffled(tracks(12))
        actions.addAll(tracks(3), QueuePosition.Next)
        actions.playShuffled(emptyList())
        advanceUntilIdle()
        awaitMessages(messages)
        assertEquals(List(12) { "t$it" }.sorted(), (player.sent[0] as Sent.Play).ids.sorted())
        assertEquals(Sent.Add(List(3) { "t$it" }, QueuePosition.Next, 3), player.sent[1])
        assertEquals(2, player.sent.size)
        assertEquals(1, messages.size)
    }

    // ---- §10.2 writes (since 1.7): the explicit state, the confirmed `200` patches the lists ----

    @Test
    fun `a confirmed dislike on the All chip drops the row from the list`() = runTest {
        val fake = FakeLibrary(12)
        fake.songLikeAnswer = WriteResult.SongLike(TrackLike.Disliked)
        val lists = LibraryLists(fake, this)
        val actions = LibraryActions(FakePlayer(), fake, this, lists = lists) {}
        lists.songs.loadMore()
        advanceUntilIdle()

        // The phone's home tabs hide their disliked rows in every chip but the Disliked one
        actions.likeSong("t5", TrackLike.Disliked)
        advanceUntilIdle()
        assertEquals(listOf("t5" to TrackLike.Disliked), fake.songLikes)
        assertEquals(11, lists.songs.state.value.items.size)
        assertEquals(11, lists.songs.state.value.total)
        assertTrue(lists.songs.state.value.items.none { it.id == "t5" })
    }

    @Test
    fun `the Disliked chip keeps its row, flipping the state to red`() = runTest {
        val fake = FakeLibrary(12)
        fake.songLikeAnswer = WriteResult.SongLike(TrackLike.Disliked)
        val lists = LibraryLists(fake, this)
        val actions = LibraryActions(FakePlayer(), fake, this, lists = lists) {}
        lists.songs.setQuery(SongsQuery(filter = SongFilter.Disliked))
        lists.songs.loadMore()
        advanceUntilIdle()

        actions.likeSong("t5", TrackLike.Disliked)
        advanceUntilIdle()
        val disliked = lists.songs.state.value.items.first { it.id == "t5" }
        assertEquals(TrackLike.Disliked, disliked.like)
        assertFalse(disliked.isLiked)
        assertEquals(12, lists.songs.state.value.items.size)
    }

    @Test
    fun `a confirmed like write drops the row from the membership chip its state no longer matches`() = runTest {
        val fake = FakeLibrary(12)
        fake.songLikeAnswer = WriteResult.SongLike(TrackLike.Disliked)
        val lists = LibraryLists(fake, this)
        val actions = LibraryActions(FakePlayer(), fake, this, lists = lists) {}
        lists.songs.setQuery(SongsQuery(filter = SongFilter.Liked))
        lists.songs.loadMore()
        advanceUntilIdle()

        actions.likeSong("t5", TrackLike.Disliked)
        advanceUntilIdle()
        assertEquals(11, lists.songs.state.value.items.size)
        assertEquals(11, lists.songs.state.value.total)
        assertTrue(lists.songs.state.value.items.none { it.id == "t5" })
    }

    @Test
    fun `a registered detail list keeps its rows, flipping the state`() = runTest {
        val fake = FakeLibrary(12)
        fake.songLikeAnswer = WriteResult.SongLike(TrackLike.Disliked)
        val lists = LibraryLists(fake, this)
        val actions = LibraryActions(FakePlayer(), fake, this, lists = lists) {}
        // A detail screen's track list, registered as rememberCollectionSongs does
        val detail = PagedList(Unit, this) { _, offset, limit ->
            fake.collectionSongs(album, offset, limit, null)
        }
        lists.registerTrackList(detail)
        detail.loadMore()
        advanceUntilIdle()

        actions.likeSong("t5", TrackLike.Disliked)
        advanceUntilIdle()
        // The phone's detail screens keep their disliked rows (only the flag flips, in red)
        val row = detail.state.value.items.first { it.id == "t5" }
        assertEquals(TrackLike.Disliked, row.like)
        assertEquals(12, detail.state.value.items.size)
        assertEquals(12, detail.state.value.total)
    }

    @Test
    fun `a confirmed bookmark, follow and pin write sends its target state`() = runTest {
        val fake = FakeLibrary(12)
        val lists = LibraryLists(fake, this)
        val actions = LibraryActions(FakePlayer(), fake, this, lists = lists) {}

        actions.bookmarkAlbum("MPREb_x", true)
        actions.followArtist("UC1", ArtistFollow.Followed)
        actions.pinPlaylist("7", true)
        advanceUntilIdle()
        assertEquals(listOf("MPREb_x" to true), fake.albumBookmarks)
        assertEquals(listOf("UC1" to ArtistFollow.Followed), fake.artistFollows)
        assertEquals(listOf("7" to true), fake.playlistPins)
    }

    // ---- The player's heart: a like alone moves no phone player event ----

    private fun queued(): PlayerState = PlayerState(
        queue = listOf(Track("t1"), Track("t5"), Track("t5"), Track("t7")),
        currentIndex = 1,
        currentTrackId = "t5",
    )

    @Test
    fun `a like write patches the player's current track and its queue items at once`() = runTest {
        val fake = FakeLibrary(12)
        fake.songLikeAnswer = WriteResult.SongLike(TrackLike.Liked)
        val player = FakePlayer(initial = queued())
        val actions = LibraryActions(player, fake, this) {}

        actions.likeSong("t5", TrackLike.Liked)
        // Before the phone answers: the heart already reads the new like
        val optimistic = player.state.value!!
        assertEquals(TrackLike.Liked, optimistic.currentTrack?.like)
        assertTrue(optimistic.currentTrack!!.isLiked)
        assertEquals(listOf(TrackLike.Neutral, TrackLike.Liked, TrackLike.Liked, TrackLike.Neutral), optimistic.queue.map { it.like })

        advanceUntilIdle()
        assertEquals(listOf("t5" to TrackLike.Liked, "t5" to TrackLike.Liked), player.likePatches)
        assertEquals(TrackLike.Liked, player.state.value!!.currentTrack?.like)
    }

    @Test
    fun `a failed like write rolls the player's heart back`() = runTest {
        val fake = FakeLibrary(12)
        fake.songLikeAnswer = WriteResult.Failed(503, "SERVER_STOPPING")
        val player = FakePlayer(initial = queued().withTrackLike("t5", TrackLike.Disliked))
        val messages = mutableListOf<String>()
        val actions = LibraryActions(player, fake, this) { messages += it }

        actions.likeSong("t5", TrackLike.Liked)
        assertEquals(TrackLike.Liked, player.state.value!!.currentTrack?.like)
        advanceUntilIdle()
        awaitMessages(messages)
        assertEquals(TrackLike.Disliked, player.state.value!!.currentTrack?.like)
        assertEquals(listOf("t5" to TrackLike.Liked, "t5" to TrackLike.Disliked), player.likePatches)
    }

    @Test
    fun `the confirmation is reported once the phone answered, never on a failure`() = runTest {
        val fake = FakeLibrary(12)
        fake.songLikeAnswer = WriteResult.SongLike(TrackLike.Liked)
        val actions = LibraryActions(FakePlayer(initial = queued()), fake, this) {}
        val confirmed = mutableListOf<TrackLike>()

        actions.likeSong("t5", TrackLike.Liked) { confirmed += it }
        // The phone toasts after its DB write: nothing before the answer
        assertTrue(confirmed.isEmpty())
        advanceUntilIdle()
        assertEquals(listOf(TrackLike.Liked), confirmed)

        fake.songLikeAnswer = WriteResult.Failed(503, "SERVER_STOPPING")
        val messages = mutableListOf<String>()
        val failing = LibraryActions(FakePlayer(initial = queued()), fake, this) { messages += it }
        failing.likeSong("t5", TrackLike.Disliked) { confirmed += it }
        advanceUntilIdle()
        awaitMessages(messages)
        assertEquals(listOf(TrackLike.Liked), confirmed)
    }

    @Test
    fun `a failed tap does not roll back a later tap's heart`() = runTest {
        val fake = FakeLibrary(12)
        fake.songLikeAnswer = WriteResult.Failed(503, "SERVER_STOPPING")
        val player = FakePlayer(initial = queued())
        val messages = mutableListOf<String>()
        val actions = LibraryActions(player, fake, this) { messages += it }

        actions.likeSong("t5", TrackLike.Liked)
        // A second tap before the first answer: the heart shows it
        player.patchTrackLike("t5", TrackLike.Disliked)
        advanceUntilIdle()
        awaitMessages(messages)
        assertEquals(TrackLike.Disliked, player.state.value!!.currentTrack?.like)
    }

    @Test
    fun `a like of a track outside the queue leaves the player state alone`() = runTest {
        val fake = FakeLibrary(12)
        fake.songLikeAnswer = WriteResult.SongLike(TrackLike.Liked)
        val player = FakePlayer(initial = queued())
        val actions = LibraryActions(player, fake, this) {}

        actions.likeSong("t9", TrackLike.Liked)
        advanceUntilIdle()
        assertTrue(player.likePatches.isEmpty())
        assertEquals(queued(), player.state.value)
    }

    @Test
    fun `write failures say so and leave the list untouched`() = runTest {
        val failures = listOf(
            WriteResult.NotFound,
            WriteResult.Unreachable,
            WriteResult.OtherActive("PC-2"),
            WriteResult.Failed(503, "SERVER_STOPPING"),
        )
        for (failure in failures) {
            val fake = FakeLibrary(12)
            fake.songLikeAnswer = failure
            val lists = LibraryLists(fake, this)
            val messages = mutableListOf<String>()
            val actions = LibraryActions(FakePlayer(), fake, this, lists = lists) { messages += it }
            lists.songs.loadMore()
            advanceUntilIdle()

            actions.likeSong("t5", TrackLike.Liked)
            advanceUntilIdle()
            awaitMessages(messages)
            assertEquals(12, lists.songs.state.value.items.size, "$failure")
            assertEquals(1, messages.size, "$failure")
            messages.clear()
        }
    }
}
