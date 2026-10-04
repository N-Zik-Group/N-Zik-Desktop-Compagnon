package app.n_zik.compagnon.components.player

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Story 11c: the seek target is held on the bar until the phone's position confirms it (phone issue #881). */
class PendingSeekScrubTest {

    @Test
    fun `the target is held while the phone still reports the old position`() {
        assertFalse(shouldReleasePendingSeekPosition(pendingTargetMs = 90_000, playerPositionMs = 30_000, heldForMs = 400))
    }

    @Test
    fun `the target is released once the position converges within the tolerance`() {
        assertTrue(shouldReleasePendingSeekPosition(90_000, 90_000 + PENDING_SEEK_SETTLE_TOLERANCE_MS, 400))
        assertTrue(shouldReleasePendingSeekPosition(90_000, 89_700, 400))
    }

    @Test
    fun `the target is released after the safety timeout when the seek is never confirmed`() {
        assertTrue(shouldReleasePendingSeekPosition(90_000, 30_000, PENDING_SEEK_RELEASE_TIMEOUT_MS))
    }

    @Test
    fun `a skip chains from the drag, then from the held target, then from the live position`() {
        assertEquals(12_000L, skipBasePosition(scrubbingMs = 12_000, pendingTargetMs = 50_000, pendingHeldForMs = 0, livePlayerPositionMs = 1_000))
        assertEquals(50_000L, skipBasePosition(scrubbingMs = null, pendingTargetMs = 50_000, pendingHeldForMs = 100, livePlayerPositionMs = 1_000))
        // Converged or timed out: the live position again
        assertEquals(50_200L, skipBasePosition(null, 50_000, 100, 50_200))
        assertEquals(1_000L, skipBasePosition(null, 50_000, PENDING_SEEK_RELEASE_TIMEOUT_MS, 1_000))
        assertEquals(1_000L, skipBasePosition(null, null, 0, 1_000))
    }
}
