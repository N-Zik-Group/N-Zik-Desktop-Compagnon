package app.n_zik.compagnon.bridge.state

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ServerClockTest {

    private var mono = 10_000L
    private var wall = 1_790_000_000_000L
    private val clock = ServerClock(monotonicMs = { mono }, wallMs = { wall })

    @Test
    fun `before any pong the wall clock stands in`() {
        assertFalse(clock.isSynced)
        assertNull(clock.offsetMs)
        assertEquals(wall, clock.serverNowMs())
    }

    @Test
    fun `offset and round trip come from the pong`() {
        // Server clock = monotonic + 1_790_000_000_000; 40 ms each way; 2 ms of server processing.
        val serverOffset = 1_790_000_000_000L
        val sentAt = 10_000L
        val pong = PongMessage(clientTimeMs = sentAt, serverReceiveTimeMs = sentAt + 40 + serverOffset, serverSendTimeMs = sentAt + 42 + serverOffset)
        clock.onPong(pong, clientReceiveMs = sentAt + 82)
        assertTrue(clock.isSynced)
        assertEquals(serverOffset, clock.offsetMs)
        assertEquals(80L, clock.roundTripMs)
        mono = 20_000
        wall = 0 // ignored once synced
        assertEquals(20_000 + serverOffset, clock.serverNowMs())
    }

    @Test
    fun `the sample with the smallest round trip wins`() {
        clock.onPong(PongMessage(0, 1_000 + 300, 1_000 + 300), clientReceiveMs = 400) // rtt 400, asymmetric
        clock.onPong(PongMessage(1_000, 1_000 + 1_010, 1_000 + 1_010), clientReceiveMs = 1_020) // rtt 20
        assertEquals(20L, clock.roundTripMs)
        assertEquals(1_000L, clock.offsetMs)
    }

    @Test
    fun `inconsistent samples are dropped`() {
        clock.onPong(PongMessage(clientTimeMs = 500, serverReceiveTimeMs = 1, serverSendTimeMs = 2), clientReceiveMs = 400)
        assertFalse(clock.isSynced)
    }

    @Test
    fun `position is extrapolated while playing, with speed and bounds`() {
        val track = Track(id = "a", durationMs = 100_000)
        val state = PlayerState(queue = listOf(track), currentIndex = 0, currentTrackId = "a", isPlaying = true, speed = 1.5f, positionMs = 10_000, serverTimeMs = 1_000)
        assertEquals(10_000L, state.extrapolatedPositionMs(1_000))
        assertEquals(10_000L + 3_000, state.extrapolatedPositionMs(3_000))
        assertEquals(10_000L, state.extrapolatedPositionMs(500), "never goes back before the anchor")
        assertEquals(100_000L, state.extrapolatedPositionMs(1_000_000), "bounded by the duration")
        assertEquals(10_000L, state.copy(isPlaying = false).extrapolatedPositionMs(50_000), "paused: no extrapolation")
        assertEquals(1_000_000L, state.copy(queue = listOf(track.copy(durationMs = null)), speed = 1f, positionMs = 1_000).extrapolatedPositionMs(1_000 + 999_000))
    }

    @Test
    fun `extrapolation with the clock stays aligned with a heartbeat`() {
        clock.onPong(PongMessage(10_000, 5_000_010, 5_000_010), clientReceiveMs = 10_020) // offset 4_990_000
        val state = PlayerState(queue = listOf(Track("a", durationMs = 300_000)), currentIndex = 0, isPlaying = true, positionMs = 83_000, serverTimeMs = 5_000_000)
        mono = 10_000 + 10_000 // 10 s later on the client
        val shown = state.extrapolatedPositionMs(clock.serverNowMs())
        // Heartbeat sent at server time 5_010_000 would carry 93_000.
        assertTrue(kotlin.math.abs(shown - 93_000) <= 1_000, "shown $shown")
    }
}
