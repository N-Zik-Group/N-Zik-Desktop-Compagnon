package app.n_zik.compagnon.core.navigation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Story 11c: Escape and the mouse's back button close one thing per press, in the phone's order. */
class BackNavigationTest {

    @Test
    fun `each press closes the topmost open thing first`() {
        assertEquals(BackStep.Menu, backStep(menuOpen = true, panelOpen = true, queueOpen = true, playerOpen = true, pageOpen = true))
        // The sheets drawn over the pages close before the page under them
        assertEquals(BackStep.Queue, backStep(false, panelOpen = true, queueOpen = true, playerOpen = true, pageOpen = true))
        assertEquals(BackStep.Player, backStep(false, panelOpen = true, queueOpen = false, playerOpen = true, pageOpen = true))
        assertEquals(BackStep.Panel, backStep(false, panelOpen = true, queueOpen = false, playerOpen = false, pageOpen = true))
        assertEquals(BackStep.Page, backStep(false, false, false, false, pageOpen = true))
    }

    @Test
    fun `on the home with nothing open the press is not used`() {
        assertNull(backStep(false, false, false, false, false))
    }

    @Test
    fun `the dispatcher reaches the registered screen and reports an unused press`() {
        val dispatcher = BackDispatcher()
        assertFalse(dispatcher.dispatch())

        var pages = 1
        dispatcher.handler = { if (pages > 0) { pages--; true } else false }
        assertTrue(dispatcher.dispatch())
        assertEquals(0, pages)
        assertFalse(dispatcher.dispatch())
    }
}
