package app.n_zik.compagnon.components.player

import androidx.compose.material3.SwipeToDismissBoxValue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * The mapping of the mini-player's swipe direction to its action ([miniPlayerSwipeAction]) — the PC's
 * Essential mini-player (the phone's default): a swipe to the right (StartToEnd) likes the current
 * track, one to the left (EndToStart) seeks to the next, and at rest (Settled) nothing happens.
 */
class MiniPlayerSwipeActionTest {

    @Test
    fun `a right swipe likes the current song`() {
        assertEquals(MiniPlayerSwipeAction.LikeCurrentSong, miniPlayerSwipeAction(SwipeToDismissBoxValue.StartToEnd))
    }

    @Test
    fun `a left swipe seeks to the next song`() {
        assertEquals(MiniPlayerSwipeAction.SeekToNext, miniPlayerSwipeAction(SwipeToDismissBoxValue.EndToStart))
    }

    @Test
    fun `no action while the box is at rest`() {
        assertNull(miniPlayerSwipeAction(SwipeToDismissBoxValue.Settled))
    }
}
