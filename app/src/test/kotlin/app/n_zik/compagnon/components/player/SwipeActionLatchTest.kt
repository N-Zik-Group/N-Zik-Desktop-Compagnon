package app.n_zik.compagnon.components.player

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The swipe's once-per-gesture guard ([SwipeActionLatch]): [SwipeActionLatch.tryFire] answers true
 * exactly once, then false until [SwipeActionLatch.release] rearms it — so a `confirmValueChange`
 * re-invoked on every drag delta runs its action once per gesture (issue #818 on the phone).
 */
class SwipeActionLatchTest {

    @Test
    fun `tryFire answers true exactly once, then false`() {
        val latch = SwipeActionLatch()
        assertFalse(latch.isFired)
        assertTrue(latch.tryFire())
        assertTrue(latch.isFired)
        assertFalse(latch.tryFire())
        assertFalse(latch.tryFire())
    }

    @Test
    fun `release rearms the latch for the next gesture`() {
        val latch = SwipeActionLatch()
        assertTrue(latch.tryFire())
        assertFalse(latch.tryFire())
        latch.release()
        assertFalse(latch.isFired)
        assertTrue(latch.tryFire())
    }
}
