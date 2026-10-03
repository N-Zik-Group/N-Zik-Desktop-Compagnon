package app.n_zik.compagnon.bridge.state

import app.n_zik.compagnon.bridge.pairing.BridgeJson
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Contract 1.2 on the client side: `audioOutput` in the snapshot, `outputChanged`, `player/output`. */
class AudioOutputStateTest {

    private val a = Track(id = "dQw4w9WgXcQ", title = "A")

    private fun snapshot(revision: Long, output: AudioOutput = AudioOutput.Phone) =
        SnapshotMessage(revision, 1_000, listOf(a), 0, a.id, isPlaying = true, positionMs = 1, audioOutput = output)

    @Test
    fun `a 1_1 snapshot without audioOutput reads as phone`() {
        val message = ServerMessages.decode("""{"type":"snapshot","revision":3,"serverTimeMs":1,"queue":[],"currentIndex":-1,"currentTrackId":null,"isPlaying":false,"speed":1.0,"positionMs":0,"repeatMode":"off","shuffle":false}""")
        assertEquals(AudioOutput.Phone, (message as SnapshotMessage).audioOutput)
    }

    @Test
    fun `audioOutput is decoded, an unknown value reads as phone`() {
        val pc = ServerMessages.decode("""{"type":"snapshot","revision":3,"serverTimeMs":1,"audioOutput":"pc"}""")
        assertEquals(AudioOutput.Pc, (pc as SnapshotMessage).audioOutput)
        val unknown = ServerMessages.decode("""{"type":"snapshot","revision":3,"serverTimeMs":1,"audioOutput":"tv"}""")
        assertEquals(AudioOutput.Phone, (unknown as SnapshotMessage).audioOutput)
        assertEquals(AudioOutput.Pc, StateReducer.reduce(SyncState(), pc).state.player?.audioOutput)
    }

    @Test
    fun `outputChanged is a revised delta`() {
        val decoded = ServerMessages.decode("""{"type":"outputChanged","revision":8,"serverTimeMs":2,"audioOutput":"pc"}""")
        assertEquals(OutputChangedMessage(8, 2, AudioOutput.Pc), decoded)

        val synced = StateReducer.reduce(SyncState(), snapshot(7)).state
        val applied = StateReducer.reduce(synced, decoded as DeltaMessage)
        assertEquals(8L, applied.state.last)
        assertEquals(AudioOutput.Pc, applied.state.player?.audioOutput)

        val gap = StateReducer.reduce(synced, OutputChangedMessage(9, 2, AudioOutput.Pc))
        assertTrue(gap.requestSnapshot)
        assertEquals(AudioOutput.Phone, gap.state.player?.audioOutput)
    }

    @Test
    fun `other deltas keep the output`() {
        val synced = StateReducer.reduce(SyncState(), snapshot(7, AudioOutput.Pc)).state
        val paused = StateReducer.reduce(synced, PlaybackChangedMessage(8, 2, isPlaying = false, speed = 1f, positionMs = 5)).state
        assertEquals(AudioOutput.Pc, paused.player?.audioOutput)
    }

    @Test
    fun `the output command has the wire shape of contract 9`() {
        assertEquals("player/output", CommandKind.Output.route)
        assertEquals(
            """{"output":"pc","commandId":"c1"}""",
            BridgeJson.encodeToString(OutputCommandBody.serializer(), OutputCommandBody(AudioOutput.Pc, "c1")),
        )
    }
}
