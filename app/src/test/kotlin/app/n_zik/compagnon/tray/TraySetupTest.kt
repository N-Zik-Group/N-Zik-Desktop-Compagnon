package app.n_zik.compagnon.tray

import app.n_zik.compagnon.enums.CloseBehavior
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The window's close-button (X) decision (frozen spec `spec-tray-taskbar-media-controls`, matrix rows
 * `CLOSE_MINIMIZE` / `CLOSE_QUIT`): "minimize to tray" wins only with a supported tray — on Wayland
 * the setting is hidden and the X quits the app no matter what the setting says. Plus the forward
 * tolerance of the persisted wire value (unknown / missing → the default).
 */
class TraySetupTest {

    @Test
    fun `minimize to tray wins when the tray is supported`() {
        assertTrue(shouldMinimizeOnClose(CloseBehavior.MinimizeToTray, true))
    }

    @Test
    fun `quit quits even when the tray is supported`() {
        assertFalse(shouldMinimizeOnClose(CloseBehavior.Quit, true))
    }

    @Test
    fun `minimize to tray falls back to quitting without a tray`() {
        // Wayland: no tray, the setting is hidden, the X quits the app
        assertFalse(shouldMinimizeOnClose(CloseBehavior.MinimizeToTray, false))
    }

    @Test
    fun `quit quits without a tray`() {
        assertFalse(shouldMinimizeOnClose(CloseBehavior.Quit, false))
    }

    @Test
    fun `an unknown or missing wire value reads as the default minimize to tray`() {
        assertEquals(CloseBehavior.MinimizeToTray, CloseBehavior.fromWire("bogus"))
        assertEquals(CloseBehavior.MinimizeToTray, CloseBehavior.fromWire(""))
        assertEquals(CloseBehavior.Quit, CloseBehavior.fromWire("quit"))
    }
}
