package app.n_zik.compagnon.components.themed

import androidx.compose.material3.SwipeToDismissBoxValue
import app.n_zik.compagnon.enums.PlaylistSwipeAction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The Songs rows' mapping of the swipe direction to its action ([playlistSwipeAction]) — with the
 * phone's default settings (left = Favourite, right = PlayNext): a swipe to the left rotates the
 * track's like, one to the right plays it next, and at rest nothing happens. The direction always
 * picks the *configured* action for that side (the phone's `playlistSwipeLeftAction` /
 * `playlistSwipeRightAction` keys).
 */
class SwipeablePlaylistRowTest {

    private val left = PlaylistSwipeAction.Favourite
    private val right = PlaylistSwipeAction.PlayNext

    @Test
    fun `a left swipe rotates the track's like`() {
        assertEquals(PlaylistSwipeAction.Favourite, playlistSwipeAction(SwipeToDismissBoxValue.EndToStart, left, right))
    }

    @Test
    fun `a right swipe plays the track next`() {
        assertEquals(PlaylistSwipeAction.PlayNext, playlistSwipeAction(SwipeToDismissBoxValue.StartToEnd, left, right))
    }

    @Test
    fun `no action while the box is at rest`() {
        assertNull(playlistSwipeAction(SwipeToDismissBoxValue.Settled, left, right))
    }

    @Test
    fun `the mapping follows the configured left and right actions`() {
        assertEquals(PlaylistSwipeAction.Enqueue, playlistSwipeAction(SwipeToDismissBoxValue.EndToStart, PlaylistSwipeAction.Enqueue, right))
        assertEquals(PlaylistSwipeAction.PlayNext, playlistSwipeAction(SwipeToDismissBoxValue.StartToEnd, left, PlaylistSwipeAction.PlayNext))
    }

    // Each [playlistSwipeCallback] arm is checked by running it and recording which callback fired.
    private fun arm(action: PlaylistSwipeAction?): MutableList<String> {
        val fired = mutableListOf<String>()
        playlistSwipeCallback(
            action,
            onPlayNext = { fired.add("playNext") },
            onEnqueue = { fired.add("enqueue") },
            onFavourite = { fired.add("favourite") },
        )()
        return fired
    }

    @Test
    fun `each action picks its own callback`() {
        assertEquals(listOf("playNext"), arm(PlaylistSwipeAction.PlayNext))
        assertEquals(listOf("enqueue"), arm(PlaylistSwipeAction.Enqueue))
        assertEquals(listOf("favourite"), arm(PlaylistSwipeAction.Favourite))
    }

    @Test
    fun `download, no action and a settled box are no-ops`() {
        assertTrue(arm(PlaylistSwipeAction.Download).isEmpty())
        assertTrue(arm(PlaylistSwipeAction.NoAction).isEmpty())
        assertTrue(arm(null).isEmpty())
    }
}
