package app.n_zik.compagnon.components.themed

import androidx.compose.material3.SwipeToDismissBoxValue
import app.n_zik.compagnon.enums.QueueSwipeAction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The queue rows' mapping of the swipe direction to its action ([queueSwipeAction]) — with the phone's
 * default settings (left = RemoveFromQueue, right = PlayNext): a swipe to the left removes the track
 * from the queue, one to the right moves it after the current track, and at rest nothing happens. The
 * direction always picks the *configured* action for that side (the phone's `queueSwipeLeftAction` /
 * `queueSwipeRightAction` keys).
 */
class SwipeableRowTest {

    private val left = QueueSwipeAction.RemoveFromQueue
    private val right = QueueSwipeAction.PlayNext

    @Test
    fun `a left swipe removes the track from the queue`() {
        assertEquals(QueueSwipeAction.RemoveFromQueue, queueSwipeAction(SwipeToDismissBoxValue.EndToStart, left, right))
    }

    @Test
    fun `a right swipe moves the track after the current one`() {
        assertEquals(QueueSwipeAction.PlayNext, queueSwipeAction(SwipeToDismissBoxValue.StartToEnd, left, right))
    }

    @Test
    fun `no action while the box is at rest`() {
        assertNull(queueSwipeAction(SwipeToDismissBoxValue.Settled, left, right))
    }

    @Test
    fun `the mapping follows the configured left and right actions`() {
        assertEquals(QueueSwipeAction.Enqueue, queueSwipeAction(SwipeToDismissBoxValue.EndToStart, QueueSwipeAction.Enqueue, right))
        assertEquals(QueueSwipeAction.Favourite, queueSwipeAction(SwipeToDismissBoxValue.StartToEnd, left, QueueSwipeAction.Favourite))
    }

    // Each [queueSwipeCallback] arm is checked by running it and recording which callback fired.
    private fun arm(action: QueueSwipeAction?): MutableList<String> {
        val fired = mutableListOf<String>()
        queueSwipeCallback(
            action,
            onPlayNext = { fired.add("playNext") },
            onEnqueue = { fired.add("enqueue") },
            onRemoveFromQueue = { fired.add("removeFromQueue") },
            onFavourite = { fired.add("favourite") },
        )()
        return fired
    }

    @Test
    fun `each action picks its own callback`() {
        assertEquals(listOf("playNext"), arm(QueueSwipeAction.PlayNext))
        assertEquals(listOf("enqueue"), arm(QueueSwipeAction.Enqueue))
        assertEquals(listOf("removeFromQueue"), arm(QueueSwipeAction.RemoveFromQueue))
        assertEquals(listOf("favourite"), arm(QueueSwipeAction.Favourite))
    }

    @Test
    fun `download, no action and a settled box are no-ops`() {
        assertTrue(arm(QueueSwipeAction.Download).isEmpty())
        assertTrue(arm(QueueSwipeAction.NoAction).isEmpty())
        assertTrue(arm(null).isEmpty())
    }
}
