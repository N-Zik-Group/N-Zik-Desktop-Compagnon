package app.n_zik.compagnon.platform

import app.n_zik.compagnon.bridge.ConnectionState
import app.n_zik.compagnon.bridge.state.PlayerState
import app.n_zik.compagnon.bridge.state.SessionContract
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The ⏮⏯⏭ ThumbBar specs from the phone's state (frozen spec `spec-tray-taskbar-media-controls`,
 * matrix rows `WIN_HAPPY` / `WIN_NO_SESSION` / `FEATURE_ABSENT`): the buttons are enabled only with
 * a live session AND the contract's `playback` feature, and the ⏯'s glyph follows `isPlaying`
 * (the PC invents no state — AD-1).
 */
class ThumbBarButtonsTest {

    private val playbackFeatures = setOf(SessionContract.FEATURE_PLAYBACK)

    private fun specOf(specs: List<ThumbBarSpec>, button: ThumbBarButton) =
        specs.first { it.button == button }

    @Test
    fun `a live session with the playback feature enables the three buttons`() {
        val specs = thumbBarSpecs(PlayerState(), ConnectionState.Live, playbackFeatures)
        assertEquals(listOf(ThumbBarButton.Previous, ThumbBarButton.PlayPause, ThumbBarButton.Next), specs.map { it.button })
        assertTrue(specs.all { it.enabled })
    }

    @Test
    fun `the play pause glyph follows is playing`() {
        val playing = thumbBarSpecs(PlayerState(isPlaying = true), ConnectionState.Live, playbackFeatures)
        assertEquals(PlayPauseGlyph.Pause, specOf(playing, ThumbBarButton.PlayPause).glyph)
        val paused = thumbBarSpecs(PlayerState(isPlaying = false), ConnectionState.Live, playbackFeatures)
        assertEquals(PlayPauseGlyph.Play, specOf(paused, ThumbBarButton.PlayPause).glyph)
    }

    @Test
    fun `a null state reads as not playing`() {
        // The snapshot has not arrived yet: the ⏯ shows the play glyph
        val specs = thumbBarSpecs(null, ConnectionState.Live, playbackFeatures)
        assertEquals(PlayPauseGlyph.Play, specOf(specs, ThumbBarButton.PlayPause).glyph)
    }

    @Test
    fun `a non live connection disables the buttons`() {
        for (connection in listOf<ConnectionState>(
            ConnectionState.Connecting,
            ConnectionState.Reconnecting(1000),
            ConnectionState.Kicked,
            ConnectionState.Replaced,
            ConnectionState.Revoked,
            ConnectionState.ServerStopped(null),
        )) {
            val specs = thumbBarSpecs(PlayerState(isPlaying = true), connection, playbackFeatures)
            assertFalse(specs.all { it.enabled }, "every button must be disabled while $connection is not live")
        }
    }

    @Test
    fun `a live session without the playback feature disables the buttons`() {
        val specs = thumbBarSpecs(PlayerState(isPlaying = true), ConnectionState.Live, emptySet())
        assertFalse(specs.all { it.enabled })
        // The glyph still follows the phone (the greyed ⏯ shows the right state)
        assertEquals(PlayPauseGlyph.Pause, specOf(specs, ThumbBarButton.PlayPause).glyph)
    }

    @Test
    fun `the button ids are the taskbar reported loword 1 2 3`() {
        val specs = thumbBarSpecs(PlayerState(), ConnectionState.Live, playbackFeatures)
        assertEquals(listOf(1, 2, 3), specs.map { it.button.id })
    }
}
