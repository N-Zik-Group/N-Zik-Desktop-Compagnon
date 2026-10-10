package app.n_zik.compagnon

import androidx.compose.ui.graphics.Color
import app.n_zik.compagnon.bridge.state.PlaybackChangedMessage
import app.n_zik.compagnon.bridge.state.PlayerState
import app.n_zik.compagnon.bridge.state.SnapshotMessage
import app.n_zik.compagnon.bridge.state.StateReducer
import app.n_zik.compagnon.bridge.state.SyncState
import app.n_zik.compagnon.bridge.state.TrackLike
import app.n_zik.compagnon.bridge.state.UiSettings
import app.n_zik.compagnon.components.menu.splitArtistNames
import app.n_zik.compagnon.components.player.ShareLinks
import app.n_zik.compagnon.components.player.likeToastMessage
import app.n_zik.compagnon.core.navigation.BackStep
import app.n_zik.compagnon.core.navigation.backStep
import app.n_zik.compagnon.generated.resources.Res
import app.n_zik.compagnon.generated.resources.removed_from_dislikes
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The 2026-10-10 audit, pass 5: the direct corrections, each against the phone's behaviour. */
class ParityPassFiveTest {

    @Test
    fun `artist names split on the localized conjunction as a whole word`() {
        // The phone's `splitArtistNames` (`Utils.kt` 195-211): "and" inside "Grand" never splits
        assertEquals(listOf("Grand Corps Malade", "Ben Mazué"), splitArtistNames("Grand Corps Malade et Ben Mazué", listOf("et")))
        assertEquals(listOf("Grand Band", "Other"), splitArtistNames("Grand Band AND Other", listOf("and")))
        assertEquals(listOf("A", "B", "C"), splitArtistNames("A & B, C", listOf("and")))
        // Without conjunctions: `&` and `,` only
        assertEquals(listOf("A and B"), splitArtistNames("A and B"))
    }

    @Test
    fun `buffering counts as playing only while the phone should play`() {
        // The phone's ring is `isBuffering && playWhenReady`: a seek while paused buffers without it
        val pausedSeek = PlayerState(isPlaying = false, isBuffering = true, playWhenReady = false)
        assertFalse(pausedSeek.shouldBePlaying)
        assertFalse(pausedSeek.showsBuffering)
        val loading = PlayerState(isPlaying = false, isBuffering = true, playWhenReady = true)
        assertTrue(loading.shouldBePlaying)
        assertTrue(loading.showsBuffering)
        // An older phone (no playWhenReady): every buffering counts, as before 1.10.0
        assertTrue(PlayerState(isBuffering = true).shouldBePlaying)
        assertTrue(PlayerState(isBuffering = true).showsBuffering)
    }

    @Test
    fun `playWhenReady rides on the snapshot and the playback delta`() {
        val snapshot = StateReducer.reduce(SyncState(), SnapshotMessage(revision = 1, serverTimeMs = 0, playWhenReady = true)).state
        assertEquals(true, snapshot.player?.playWhenReady)
        val paused = StateReducer.reduce(
            snapshot,
            PlaybackChangedMessage(2, 0, isPlaying = false, isBuffering = true, speed = 1f, positionMs = 0, playWhenReady = false),
        ).state
        assertEquals(false, paused.player?.playWhenReady)
        assertNull(StateReducer.reduce(SyncState(), SnapshotMessage(revision = 1, serverTimeMs = 0)).state.player?.playWhenReady)
    }

    @Test
    fun `the static palette is blackened in PitchBlack`() {
        // The phone's preference listener (`MainActivity.kt` 1517-1525)
        val palette = staticColorPaletteOf(UiSettings(colorPaletteName = "Default", colorPaletteMode = "PitchBlack"), false)
        assertEquals(Color.Black, palette.background0)
        assertEquals(Color.Black, palette.background4)
        assertEquals(Color.White, palette.text)
    }

    @Test
    fun `a title click pushes home, the back returns to the page it left`() {
        assertEquals(BackStep.HomeReturn, backStep(false, false, false, false, false, homeReturnPending = true))
        // The page and the sheets above it close first
        assertEquals(BackStep.Page, backStep(false, false, false, false, true, homeReturnPending = true))
        assertNull(backStep(false, false, false, false, false))
    }

    @Test
    fun `the player's like toast is the rotation's in both modes`() {
        // `rotateSongLikeState` toasts `removed_from_dislikes` for neutral even in its toggle branch
        assertEquals(Res.string.removed_from_dislikes, likeToastMessage(TrackLike.Neutral, rotationEnabled = true))
    }

    @Test
    fun `the album share falls back to the phone's browse link`() {
        assertEquals("https://music.youtube.com/browse/MPREb_x", ShareLinks.album("MPREb_x"))
    }

    @Test
    fun `the phone's now-playing indicator defaults to Bubbles`() {
        assertEquals("Bubbles", UiSettings().nowPlayingIndicator)
    }
}
