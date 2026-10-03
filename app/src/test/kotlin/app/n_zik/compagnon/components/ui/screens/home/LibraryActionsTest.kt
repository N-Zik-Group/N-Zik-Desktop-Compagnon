package app.n_zik.compagnon.components.ui.screens.home

import androidx.compose.ui.graphics.ImageBitmap
import app.n_zik.compagnon.bridge.ConnectionState
import app.n_zik.compagnon.bridge.command.PlayWindow
import app.n_zik.compagnon.bridge.library.Album
import app.n_zik.compagnon.bridge.library.Artist
import app.n_zik.compagnon.bridge.library.CollectionFilter
import app.n_zik.compagnon.bridge.library.CollectionKind
import app.n_zik.compagnon.bridge.library.CollectionRef
import app.n_zik.compagnon.bridge.library.LibraryRepository
import app.n_zik.compagnon.bridge.library.Page
import app.n_zik.compagnon.bridge.library.Playlist
import app.n_zik.compagnon.bridge.library.SongsQuery
import app.n_zik.compagnon.bridge.state.PlayerNotice
import app.n_zik.compagnon.bridge.state.PlayerRepository
import app.n_zik.compagnon.bridge.state.PlayerState
import app.n_zik.compagnon.bridge.state.AudioOutput
import app.n_zik.compagnon.bridge.state.QueuePosition
import app.n_zik.compagnon.bridge.state.RepeatMode
import app.n_zik.compagnon.bridge.state.Track
import app.n_zik.compagnon.core.network.ArtworkKey
import app.n_zik.compagnon.core.network.LibraryResult
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
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class LibraryActionsTest {

    private sealed interface Sent {
        data class Play(val ids: List<String>, val startIndex: Int, val total: Int) : Sent
        data class Add(val ids: List<String>, val position: QueuePosition, val total: Int) : Sent
    }

    /** Records `queue/play` / `queue/add`; every other command is unused here. */
    private class FakePlayer(override val features: Set<String> = setOf("playback", "queue")) : PlayerRepository {
        val sent = mutableListOf<Sent>()
        override val state: StateFlow<PlayerState?> = MutableStateFlow(null)
        override val connection: StateFlow<ConnectionState> = MutableStateFlow(ConnectionState.Live)
        override val notices: SharedFlow<PlayerNotice> = MutableSharedFlow()
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
        override fun start() = Unit
        override fun reconnect() = Unit
        override fun close() = Unit
    }

    /** A collection of [size] tracks "t0", "t1"…, served by pages; or always [failure] when set. */
    private class FakeLibrary(private val size: Int, private val failure: LibraryResult<Track>? = null) : LibraryRepository {
        override val features: Set<String> = setOf("library.albums")
        override suspend fun songs(offset: Int, limit: Int, query: SongsQuery): LibraryResult<Track> = error("unused")
        override suspend fun playlists(offset: Int, limit: Int): LibraryResult<Playlist> = error("unused")
        override suspend fun albums(offset: Int, limit: Int, filter: CollectionFilter): LibraryResult<Album> = error("unused")
        override suspend fun artists(offset: Int, limit: Int, filter: CollectionFilter): LibraryResult<Artist> = error("unused")
        override suspend fun collectionSongs(collection: CollectionRef, offset: Int, limit: Int): LibraryResult<Track> =
            failure ?: LibraryResult.Ok(Page((offset until minOf(offset + limit, size)).map { Track("t$it") }, size, offset, limit))
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
        val menu = actions.trackActions({ tracks }, 1, "b", live = true)!!
        menu.onPlayNext()
        menu.onEnqueue()
        advanceUntilIdle()
        assertEquals(listOf<Sent>(Sent.Add(listOf("b"), QueuePosition.Next, 1), Sent.Add(listOf("b"), QueuePosition.End, 1)), player.sent)
    }

    @Test
    fun `a track menu sends nothing once the list changed under it`() = runTest {
        val player = FakePlayer()
        val actions = LibraryActions(player, FakeLibrary(0), this) {}
        var tracks = listOf(Track("a"), Track("b"))
        val menu = actions.trackActions({ tracks }, 1, "b", live = true)!!
        // A reload put another track at index 1
        tracks = listOf(Track("a"), Track("x"))
        menu.onPlay()
        menu.onPlayNext()
        menu.onEnqueue()
        advanceUntilIdle()
        assertEquals(emptyList<Sent>(), player.sent)
    }

    @Test
    fun `without the queue feature there is no menu`() = runTest {
        val actions = LibraryActions(FakePlayer(features = setOf("playback")), FakeLibrary(12), this) {}
        assertNull(actions.collectionActions(album, live = true))
        assertNull(actions.trackActions({ emptyList() }, 0, "a", live = true))
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
}
