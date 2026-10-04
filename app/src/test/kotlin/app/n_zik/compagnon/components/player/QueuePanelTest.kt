package app.n_zik.compagnon.components.player

import app.n_zik.compagnon.MEDIA_ABSENCE_GRACE_MS
import app.n_zik.compagnon.mediaAbsencePersists
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Story 11c: the queue panel's rest toolbar, its ceiling under the header, its closing at the end of playback. */
class QueuePanelTest {

    @Test
    fun `at its 65 percent rest height the toolbar is fully shown, so its mini-player is not clipped`() {
        // Non-regression: (0.65f - 0.55f) / 0.1f is 0.9999996 in float, an alpha below 1
        assertEquals(1f, queueToolBarProgress(0.65f))
        assertEquals(1f, queueToolBarProgress(0.9f))
        assertEquals(0f, queueToolBarProgress(0.55f))
        assertEquals(0.5f, queueToolBarProgress(0.6f), 1e-4f)
        assertEquals(0f, queueToolBarProgress(0f))
    }

    @Test
    fun `pulled up, the panel stops under the 64 dp header`() {
        assertEquals((800f - 64f) / 800f, queuePanelMaxFraction(800, 64), 1e-6f)
        // 1.5x scale: header 96 px in a 1200 px window
        assertEquals(1104f / 1200f, queuePanelMaxFraction(1200, 96), 1e-6f)
        assertEquals(1f, queuePanelMaxFraction(0, 64))
    }

    @Test
    fun `the queue closes once the media stays absent past the grace`() = runTest {
        val closes = async { mediaAbsencePersists { null } }
        advanceTimeBy(MEDIA_ABSENCE_GRACE_MS + 1)
        assertTrue(closes.await())
    }

    @Test
    fun `a transient absence between two tracks keeps the queue open`() = runTest {
        var current: Any? = null
        val closes = async { mediaAbsencePersists { current } }
        advanceTimeBy(MEDIA_ABSENCE_GRACE_MS / 2)
        current = "next track"
        advanceTimeBy(MEDIA_ABSENCE_GRACE_MS)
        assertFalse(closes.await())
    }
}
