package app.n_zik.compagnon.components.navigation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Story 11c: the header and bars hide on scroll down and come back on scroll up, wheel included. */
class BarsScrollHideTest {

    @Test
    fun `scrolling down hides then scrolling up shows the bars again`() {
        val bars = BarsScrollHide(topMaxPx = 64f, bottomMaxPx = 240f)
        assertEquals(-64f, bars.scroll(-100f))
        assertEquals(-64f, bars.top)
        assertEquals(100f, bars.bottom)
        bars.scroll(-500f)
        assertEquals(240f, bars.bottom)
        assertEquals(64f, bars.scroll(300f))
        assertEquals(0f, bars.top)
        assertEquals(0f, bars.bottom)
    }

    @Test
    fun `an upward wheel step shows hidden bars without any list scroll, a downward one does nothing`() {
        val bars = BarsScrollHide(64f, 240f)
        bars.set(-64f, 240f)
        bars.wheel(-64f)
        assertEquals(-64f, bars.top)
        bars.wheel(64f)
        assertEquals(0f, bars.top)
        assertEquals(176f, bars.bottom)
        assertTrue(bars.snapShown())
    }

    @Test
    fun `the snap follows half the header`() {
        val bars = BarsScrollHide(64f, 240f)
        bars.set(-40f, 100f)
        assertFalse(bars.snapShown())
        bars.set(-20f, 100f)
        assertTrue(bars.snapShown())
    }
}
