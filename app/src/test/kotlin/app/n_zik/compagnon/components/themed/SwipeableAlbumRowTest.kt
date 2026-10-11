package app.n_zik.compagnon.components.themed

import androidx.compose.material3.SwipeToDismissBoxValue
import app.n_zik.compagnon.enums.AlbumSwipeAction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The Albums cards' mapping of the swipe direction to its action ([albumSwipeAction]) — with the
 * phone's default settings (left = PlayNext, right = Bookmark): a swipe to the left plays the
 * album next, one to the right rotates its bookmark, and at rest nothing happens. The direction
 * always picks the *configured* action for that side (the phone's `albumSwipeLeftAction` /
 * `albumSwipeRightAction` keys).
 */
class SwipeableAlbumRowTest {

    private val left = AlbumSwipeAction.PlayNext
    private val right = AlbumSwipeAction.Bookmark

    @Test
    fun `a left swipe plays the album next`() {
        assertEquals(AlbumSwipeAction.PlayNext, albumSwipeAction(SwipeToDismissBoxValue.EndToStart, left, right))
    }

    @Test
    fun `a right swipe rotates the album's bookmark`() {
        assertEquals(AlbumSwipeAction.Bookmark, albumSwipeAction(SwipeToDismissBoxValue.StartToEnd, left, right))
    }

    @Test
    fun `no action while the box is at rest`() {
        assertNull(albumSwipeAction(SwipeToDismissBoxValue.Settled, left, right))
    }

    @Test
    fun `the mapping follows the configured left and right actions`() {
        assertEquals(AlbumSwipeAction.Enqueue, albumSwipeAction(SwipeToDismissBoxValue.EndToStart, AlbumSwipeAction.Enqueue, right))
        assertEquals(AlbumSwipeAction.PlayNext, albumSwipeAction(SwipeToDismissBoxValue.StartToEnd, left, AlbumSwipeAction.PlayNext))
    }

    // Each [albumSwipeCallback] arm is checked by running it and recording which callback fired.
    private fun arm(action: AlbumSwipeAction?): MutableList<String> {
        val fired = mutableListOf<String>()
        albumSwipeCallback(
            action,
            onPlayNext = { fired.add("playNext") },
            onEnqueue = { fired.add("enqueue") },
            onBookmark = { fired.add("bookmark") },
        )()
        return fired
    }

    @Test
    fun `each action picks its own callback`() {
        assertEquals(listOf("playNext"), arm(AlbumSwipeAction.PlayNext))
        assertEquals(listOf("enqueue"), arm(AlbumSwipeAction.Enqueue))
        assertEquals(listOf("bookmark"), arm(AlbumSwipeAction.Bookmark))
    }

    @Test
    fun `no action and a settled box are no-ops`() {
        assertTrue(arm(AlbumSwipeAction.NoAction).isEmpty())
        assertTrue(arm(null).isEmpty())
    }
}
