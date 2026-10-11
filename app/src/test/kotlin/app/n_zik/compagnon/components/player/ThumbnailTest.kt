package app.n_zik.compagnon.components.player

import app.n_zik.compagnon.enums.PlayerSwipeSensitivity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * The pure decision of the player's cover horizontal swipe ([coverSwipeAction]): the accumulated drag
 * distance in px against the sensitivity threshold — right = previous, left = next, nothing in between
 * (the phone's `Player.kt` 1229-1234: strictly beyond the threshold, `delta > threshold` / `delta < -threshold`).
 */
class ThumbnailTest {

    private val medium = PlayerSwipeSensitivity.Medium.threshold // 75 px

    @Test
    fun `a right drag beyond the threshold plays the previous track`() {
        assertEquals(CoverSwipeDirection.Previous, coverSwipeAction(100f, medium))
    }

    @Test
    fun `a left drag beyond the threshold plays the next track`() {
        assertEquals(CoverSwipeDirection.Next, coverSwipeAction(-100f, medium))
    }

    @Test
    fun `a drag below the threshold does nothing`() {
        assertNull(coverSwipeAction(50f, medium))
        assertNull(coverSwipeAction(-50f, medium))
    }

    @Test
    fun `a drag exactly at the threshold does nothing`() {
        assertNull(coverSwipeAction(medium, medium))
        assertNull(coverSwipeAction(-medium, medium))
    }

    @Test
    fun `no drag does nothing`() {
        assertNull(coverSwipeAction(0f, medium))
    }
}
