package app.n_zik.compagnon.platform

import app.n_zik.compagnon.bridge.ConnectionState
import app.n_zik.compagnon.bridge.state.PlayerState
import app.n_zik.compagnon.bridge.state.SessionContract
import app.n_zik.compagnon.bridge.state.Track
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The Windows « Now Playing » panel (SMTC) view: the projection of the MPRIS snapshot (the same mapping of the phone's state on both OS — AD-1). */
class SmtcViewTest {

    private val playback = setOf(SessionContract.FEATURE_PLAYBACK)

    @Test
    fun `no session disables the panel`() {
        val view = smtcView(stoppedSnapshot())
        assertFalse(view.enabled)
        assertEquals(SMTC_STATUS_STOPPED, view.status)
        assertFalse(view.canPlay || view.canPause || view.canNext || view.canPrevious)
    }

    @Test
    fun `a playing track shows its title artist and the playback gates`() {
        val state = playing(true)
        val view = smtcView(mprisSnapshot(state, ConnectionState.Live, playback, artUrl = null))
        assertTrue(view.enabled)
        assertEquals(SMTC_STATUS_PLAYING, view.status)
        assertEquals("Title", view.title)
        assertEquals("Artist", view.artist)
        // MPRIS v2.2: `CanPlay` / `CanPause` are properties of the current track, not of the state.
        assertTrue(view.canPause)
        assertTrue(view.canPlay)
        assertTrue(view.canNext && view.canPrevious)
    }

    @Test
    fun `a paused session without the playback feature shows the status with every gate closed`() {
        val state = playing(false)
        val view = smtcView(mprisSnapshot(state, ConnectionState.Live, emptySet(), artUrl = null))
        assertTrue(view.enabled)
        assertEquals(SMTC_STATUS_PAUSED, view.status)
        assertFalse(view.canPlay || view.canPause || view.canNext || view.canPrevious)
    }

    @Test
    fun `the progress bar carries the position, the duration, the seek gate and the rate`() {
        val view = smtcView(mprisSnapshot(playing(true), ConnectionState.Live, playback, artUrl = null), positionMs = 4_000L)
        assertEquals(4_000L, view.positionMs)
        assertEquals(20_000L, view.durationMs)
        assertTrue(view.canSeek)
        assertEquals(1.0, view.rate)
        // No session: no position
        assertEquals(0L, smtcView(stoppedSnapshot(), positionMs = 4_000L).positionMs)
    }

    @Test
    fun `the buttons map the sdk codes`() {
        assertEquals(SmtcButton.Play, SmtcButton.of(0))
        assertEquals(SmtcButton.Pause, SmtcButton.of(1))
        assertEquals(SmtcButton.Next, SmtcButton.of(6))
        assertEquals(SmtcButton.Previous, SmtcButton.of(7))
        // Stop / Record / FastForward / Rewind / Channel*: not answered
        assertNull(SmtcButton.of(2))
    }

    private fun playing(isPlaying: Boolean) =
        PlayerState(queue = listOf(Track(id = "t1", title = "Title", artists = "Artist", durationMs = 20_000L)), currentIndex = 0, isPlaying = isPlaying)
}
