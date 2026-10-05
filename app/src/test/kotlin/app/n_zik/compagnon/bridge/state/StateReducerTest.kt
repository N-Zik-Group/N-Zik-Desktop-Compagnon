package app.n_zik.compagnon.bridge.state

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StateReducerTest {

    private val a = Track(id = "dQw4w9WgXcQ", title = "A", durationMs = 212_000, hasArtwork = true)
    private val b = Track(id = "local:42", title = "B", source = TrackSource.Local, durationMs = 100_000)

    private fun snapshot(revision: Long, positionMs: Long = 83_000) = SnapshotMessage(
        revision = revision,
        serverTimeMs = 1_000,
        queue = listOf(a, b),
        currentIndex = 0,
        currentTrackId = a.id,
        isPlaying = true,
        speed = 1f,
        positionMs = positionMs,
    )

    private fun synced(last: Long): SyncState = StateReducer.reduce(SyncState(), snapshot(last)).state

    private fun pause(revision: Long) = PlaybackChangedMessage(revision, 2_000, isPlaying = false, speed = 1f, positionMs = 84_000)

    @Test
    fun `snapshot is applied and sets last`() {
        val result = StateReducer.reduce(SyncState(), snapshot(57))
        assertEquals(57L, result.state.last)
        assertFalse(result.state.awaitingSnapshot)
        assertFalse(result.requestSnapshot)
        assertEquals(listOf(a, b), result.state.player!!.queue)
        assertEquals(a, result.state.player!!.currentTrack)
    }

    @Test
    fun `delta in order is applied`() {
        val result = StateReducer.reduce(synced(57), pause(58))
        assertEquals(58L, result.state.last)
        assertFalse(result.state.player!!.isPlaying)
        assertEquals(84_000L, result.state.player!!.positionMs)
        assertEquals(2_000L, result.state.player!!.serverTimeMs)
        assertFalse(result.requestSnapshot)
    }

    @Test
    fun `stale delta is rejected`() {
        val state = StateReducer.reduce(synced(57), pause(58)).state
        val stale = PlaybackChangedMessage(58, 3_000, isPlaying = true, speed = 2f, positionMs = 1)
        val result = StateReducer.reduce(state, stale)
        assertSame(state, result.state)
        assertFalse(result.requestSnapshot)
        val older = StateReducer.reduce(state, pause(10))
        assertSame(state, older.state)
    }

    @Test
    fun `gap requests a snapshot and ignores deltas until it arrives`() {
        val at58 = StateReducer.reduce(synced(57), pause(58)).state
        val gap = StateReducer.reduce(at58, TrackChangedMessage(60, 3_000, currentIndex = 1, currentTrackId = b.id, positionMs = 0, isPlaying = true))
        assertTrue(gap.requestSnapshot)
        assertTrue(gap.state.awaitingSnapshot)
        assertEquals(58L, gap.state.last)
        assertEquals(a, gap.state.player!!.currentTrack)

        // Even the delta that would have been "in order" is ignored while awaiting the snapshot.
        val ignored = StateReducer.reduce(gap.state, pause(59))
        assertSame(gap.state, ignored.state)
        assertFalse(ignored.requestSnapshot)

        val recovered = StateReducer.reduce(ignored.state, snapshot(61))
        assertEquals(61L, recovered.state.last)
        assertFalse(recovered.state.awaitingSnapshot)
        assertEquals(61L, StateReducer.reduce(recovered.state, pause(61)).state.last)
        assertEquals(62L, StateReducer.reduce(recovered.state, pause(62)).state.last)
    }

    @Test
    fun `heartbeat equal to last re-aligns position, playback and speed`() {
        val state = StateReducer.reduce(synced(57), pause(58)).state
        val result = StateReducer.reduce(state, HeartbeatMessage(58, 12_000, positionMs = 93_000, isPlaying = true, speed = 1.5f))
        val player = result.state.player!!
        assertEquals(93_000L, player.positionMs)
        assertEquals(12_000L, player.serverTimeMs)
        assertTrue(player.isPlaying)
        assertEquals(1.5f, player.speed)
        assertEquals(58L, result.state.last)
        assertFalse(result.requestSnapshot)
    }

    @Test
    fun `heartbeat ahead of last requests a snapshot`() {
        val state = StateReducer.reduce(synced(57), pause(58)).state
        val result = StateReducer.reduce(state, HeartbeatMessage(59, 12_000, positionMs = 1, isPlaying = true, speed = 1f))
        assertTrue(result.requestSnapshot)
        assertTrue(result.state.awaitingSnapshot)
        assertEquals(state.player, result.state.player)
    }

    @Test
    fun `heartbeat behind last is ignored`() {
        val state = StateReducer.reduce(synced(57), pause(58)).state
        val result = StateReducer.reduce(state, HeartbeatMessage(57, 12_000, positionMs = 1, isPlaying = true, speed = 1f))
        assertSame(state, result.state)
        assertFalse(result.requestSnapshot)
    }

    @Test
    fun `unknown type with revision only counts`() {
        val state = StateReducer.reduce(synced(57), pause(58)).state
        val next = StateReducer.reduce(state, UnknownMessage("x", 59))
        assertEquals(59L, next.state.last)
        assertEquals(state.player, next.state.player)
        assertFalse(next.requestSnapshot)

        val gap = StateReducer.reduce(next.state, UnknownMessage("x", 61))
        assertTrue(gap.requestSnapshot)
        assertEquals(59L, gap.state.last)

        val old = StateReducer.reduce(next.state, UnknownMessage("x", 3))
        assertSame(next.state, old.state)
        val noRevision = StateReducer.reduce(next.state, UnknownMessage("x", null))
        assertSame(next.state, noRevision.state)
    }

    @Test
    fun `lower snapshot after a server restart is applied`() {
        val state = StateReducer.reduce(synced(57), pause(58)).state
        val result = StateReducer.reduce(state, snapshot(3, positionMs = 5))
        assertEquals(3L, result.state.last)
        assertEquals(5L, result.state.player!!.positionMs)
        assertEquals(4L, StateReducer.reduce(result.state, pause(4)).state.last)
    }

    @Test
    fun `deltas before the first snapshot are ignored`() {
        val result = StateReducer.reduce(SyncState(), pause(1))
        assertNull(result.state.player)
        assertNull(result.state.last)
        assertFalse(result.requestSnapshot)
        assertFalse(StateReducer.reduce(SyncState(), HeartbeatMessage(4, 1, 1, true, 1f)).requestSnapshot)
    }

    @Test
    fun `each delta type updates its own fields`() {
        var state = synced(10)
        state = StateReducer.reduce(state, TrackChangedMessage(11, 5_000, currentIndex = 1, currentTrackId = b.id, positionMs = 0, isPlaying = true)).state
        assertEquals(b, state.player!!.currentTrack)
        assertEquals(5_000L, state.player!!.serverTimeMs)

        state = StateReducer.reduce(state, QueueChangedMessage(12, 6_000, queue = listOf(b), currentIndex = 0, currentTrackId = b.id)).state
        assertEquals(listOf(b), state.player!!.queue)
        assertEquals(0, state.player!!.currentIndex)
        assertEquals(5_000L, state.player!!.serverTimeMs, "a queue change does not move the position anchor")

        state = StateReducer.reduce(state, ModesChangedMessage(13, 7_000, repeatMode = RepeatMode.One, shuffle = true)).state
        assertEquals(RepeatMode.One, state.player!!.repeatMode)
        assertTrue(state.player!!.shuffle)
        assertEquals(13L, state.last)
    }

    @Test
    fun `libraryChanged counts as a delta and leaves the player untouched`() {
        val state = synced(57)
        val result = StateReducer.reduce(state, LibraryChangedMessage(58, 2_000, kind = "songs"))
        assertEquals(58L, result.state.last)
        assertEquals(state.player, result.state.player)
        assertFalse(result.requestSnapshot)
        assertTrue(result.applied, "an in-order delta is applied — the PC's family reloads on it")

        // The usual delta rules: stale is rejected, a gap requests a snapshot — neither is applied
        val stale = StateReducer.reduce(result.state, LibraryChangedMessage(58, 3_000, kind = "albums"))
        assertSame(result.state, stale.state)
        assertFalse(stale.applied)
        val gap = StateReducer.reduce(result.state, LibraryChangedMessage(60, 3_000, kind = "songs"))
        assertTrue(gap.requestSnapshot)
        assertEquals(58L, gap.state.last)
        assertFalse(gap.applied)
    }

    @Test
    fun `snapshot carries the buffering state and a missing field defaults to false`() {
        // A 1.4 phone sends the field...
        val message = ServerMessages.decode(
            """{"type":"snapshot","revision":57,"serverTimeMs":1,"queue":[],"currentIndex":-1,"currentTrackId":null,
               "isPlaying":false,"isBuffering":true,"speed":1.0,"positionMs":0,"repeatMode":"off","shuffle":false}""",
        ) as SnapshotMessage
        assertTrue(message.isBuffering)
        val buffered = StateReducer.reduce(SyncState(), message).state
        assertTrue(buffered.player!!.isBuffering)

        // A ≤ 1.3 phone never sends it: the client reads `false` (contract §13)
        val legacy = StateReducer.reduce(SyncState(), snapshot(57)).state
        assertFalse(legacy.player!!.isBuffering)
    }

    @Test
    fun `playback and track deltas update the buffering state`() {
        var state = synced(57)

        // A play after a pause enters buffering with isPlaying still false (contract 1.4)
        state = StateReducer.reduce(state, PlaybackChangedMessage(58, 2_000, isPlaying = false, isBuffering = true, speed = 1f, positionMs = 83_000)).state
        assertFalse(state.player!!.isPlaying)
        assertTrue(state.player!!.isBuffering)

        state = StateReducer.reduce(state, TrackChangedMessage(59, 3_000, currentIndex = 1, currentTrackId = b.id, positionMs = 0, isPlaying = false, isBuffering = true)).state
        assertEquals(b, state.player!!.currentTrack)
        assertTrue(state.player!!.isBuffering)

        state = StateReducer.reduce(state, PlaybackChangedMessage(60, 4_000, isPlaying = true, isBuffering = false, speed = 1f, positionMs = 0)).state
        assertFalse(state.player!!.isBuffering)
    }

    @Test
    fun `snapshot carries the player's live duration and a missing field falls back to the track's`() {
        // A 1.5 phone sends the field: while loading, the player reports its C.TIME_UNSET (the phone shows --:--)
        val message = ServerMessages.decode(
            """{"type":"snapshot","revision":57,"serverTimeMs":1,
               "queue":[{"id":"dQw4w9WgXcQ","title":"A","durationMs":212000,"source":"online","isDownloaded":false,"isLiked":false,"hasArtwork":true}],
               "currentIndex":0,"currentTrackId":"dQw4w9WgXcQ","isPlaying":false,"isBuffering":true,
               "durationMs":-9223372036854775807,"speed":1.0,"positionMs":0}""",
        ) as SnapshotMessage
        assertEquals(PlayerState.DURATION_TIME_UNSET, message.durationMs)
        val loading = StateReducer.reduce(SyncState(), message).state
        assertNull(loading.player!!.playerDurationMs, "the bar must show --:-- while the player does not know the duration")

        // ...and once the stream is loaded, the player reports its duration (contract 1.5)
        val loaded = StateReducer.reduce(
            loading,
            PlaybackChangedMessage(58, 2_000, isPlaying = true, isBuffering = false, durationMs = 212_000, speed = 1f, positionMs = 0),
        ).state
        assertEquals(212_000L, loaded.player!!.playerDurationMs)

        // A ≤ 1.4 phone never sends it: the track's metadata duration stands in (contract §13)
        val legacy = StateReducer.reduce(SyncState(), snapshot(57)).state
        assertEquals(a.durationMs, legacy.player!!.playerDurationMs)
    }

    @Test
    fun `track and playback deltas update the player's live duration`() {
        // A new track loads: its duration is reset to C.TIME_UNSET until the stream is loaded
        var state = StateReducer.reduce(
            synced(57),
            TrackChangedMessage(58, 2_000, currentIndex = 1, currentTrackId = b.id, positionMs = 0, isPlaying = false, durationMs = PlayerState.DURATION_TIME_UNSET),
        ).state
        assertEquals(PlayerState.DURATION_TIME_UNSET, state.player!!.durationMs)
        assertNull(state.player!!.playerDurationMs)

        state = StateReducer.reduce(state, PlaybackChangedMessage(59, 3_000, isPlaying = true, isBuffering = false, durationMs = 100_000, speed = 1f, positionMs = 0)).state
        assertEquals(100_000L, state.player!!.playerDurationMs)
    }

    @Test
    fun `pong, error and serverStopped leave the state untouched`() {
        val state = synced(5)
        assertSame(state, StateReducer.reduce(state, PongMessage(1, 2, 3)).state)
        assertSame(state, StateReducer.reduce(state, ErrorMessage("PLAYER_REJECTED", "x", "c1")).state)
        assertSame(state, StateReducer.reduce(state, ServerStoppedMessage(StopCode.AutoStop, "x")).state)
    }

    // ---- Decoding (contract §1 tolerance, §7.5) ----

    @Test
    fun `decoder ignores unknown fields and falls back on unknown enum values`() {
        val message = ServerMessages.decode(
            """{"type":"snapshot","revision":57,"serverTimeMs":1790000000000,"extra":{"a":1},
               "queue":[{"id":"dQw4w9WgXcQ","title":"T","artists":null,"durationMs":null,"source":"cloud","isDownloaded":false,"isLiked":true,"hasArtwork":true,"newField":3}],
               "currentIndex":0,"currentTrackId":"dQw4w9WgXcQ","isPlaying":true,"speed":1.0,"positionMs":83000,"repeatMode":"shuffle-all","shuffle":false}""",
        ) as SnapshotMessage
        assertEquals(RepeatMode.Off, message.repeatMode)
        assertEquals(TrackSource.Online, message.queue.single().source)
        assertNull(message.queue.single().durationMs)
        assertEquals(57L, message.revision)
    }

    @Test
    fun `decoder maps every known type and keeps unknown ones for accounting`() {
        assertTrue(ServerMessages.decode("""{"type":"playbackChanged","revision":58,"serverTimeMs":1,"isPlaying":false,"speed":1.0,"positionMs":2}""") is PlaybackChangedMessage)
        assertTrue(ServerMessages.decode("""{"type":"trackChanged","revision":58,"serverTimeMs":1,"currentIndex":1,"currentTrackId":null,"positionMs":0,"isPlaying":true}""") is TrackChangedMessage)
        assertTrue(ServerMessages.decode("""{"type":"queueChanged","revision":58,"serverTimeMs":1,"queue":[],"currentIndex":-1,"currentTrackId":null}""") is QueueChangedMessage)
        assertTrue(ServerMessages.decode("""{"type":"modesChanged","revision":58,"serverTimeMs":1,"repeatMode":"all","shuffle":true}""") is ModesChangedMessage)
        assertTrue(ServerMessages.decode("""{"type":"libraryChanged","revision":58,"serverTimeMs":1,"kind":"playlists"}""") is LibraryChangedMessage)
        assertTrue(ServerMessages.decode("""{"type":"heartbeat","revision":57,"serverTimeMs":1,"positionMs":93000,"isPlaying":true,"speed":1.0}""") is HeartbeatMessage)
        assertEquals(PongMessage(123456, 1790000000000, 1790000000001), ServerMessages.decode("""{"type":"pong","clientTimeMs":123456,"serverReceiveTimeMs":1790000000000,"serverSendTimeMs":1790000000001}"""))
        assertEquals(ErrorMessage("PLAYER_REJECTED", "…", "7f1c"), ServerMessages.decode("""{"type":"error","code":"PLAYER_REJECTED","message":"…","commandId":"7f1c"}"""))
        assertEquals(ServerStoppedMessage(StopCode.StopUser, "m"), ServerMessages.decode("""{"type":"serverStopped","code":"WHATEVER","message":"m"}"""))
        assertEquals(UnknownMessage("x", 59), ServerMessages.decode("""{"type":"x","revision":59}"""))
        assertEquals(UnknownMessage("x", null), ServerMessages.decode("""{"type":"x","revision":"59"}"""))
        assertNull(ServerMessages.decode("not json"))
        assertNull(ServerMessages.decode("""{"type":"playbackChanged","revision":"oops"}"""))
    }

    @Test
    fun `client messages have the wire shape of contract 7_8`() {
        assertEquals("""{"clientTimeMs":42,"type":"ping"}""", ClientMessages.ping(42))
        assertEquals("""{"type":"requestSnapshot"}""", ClientMessages.requestSnapshot())
    }
}
