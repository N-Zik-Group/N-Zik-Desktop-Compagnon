package app.n_zik.compagnon.components.player

import androidx.compose.ui.unit.dp
import app.n_zik.compagnon.components.menu.player.PHONE_SPEED_MAX
import app.n_zik.compagnon.components.menu.player.PHONE_SPEED_MIN
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Story 11c: the full player's layout follows the phone's orientation test on the window. */
class PlayerLayoutTest {

    @Test
    fun `a window wider than tall takes the landscape layout`() {
        assertTrue(isPlayerLandscape(1100.dp, 800.dp))
    }

    @Test
    fun `a window taller than wide, or square, keeps the portrait layout`() {
        assertFalse(isPlayerLandscape(600.dp, 900.dp))
        assertFalse(isPlayerLandscape(800.dp, 800.dp))
    }

    @Test
    fun `the speed slider has the phone's range`() {
        assertEquals(0.1f, PHONE_SPEED_MIN)
        assertEquals(10f, PHONE_SPEED_MAX)
    }
}
