package app.n_zik.compagnon.tray

import java.awt.Rectangle
import java.awt.image.BufferedImage
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** The styled tray menu's placement and the Linux tray icon's wire format. */
class TrayMenuTest {

    private val screen = Rectangle(0, 0, 1920, 1040) // a 1080p screen minus a 40 px bottom taskbar

    @Test
    fun `a click on the bottom right taskbar opens above and to the left`() {
        assertEquals(TrayAnchor(1600 - 248, 1030 - 300), trayMenuPosition(TrayAnchor(1600, 1030), 248, 300, screen))
    }

    @Test
    fun `a click on a top panel opens below`() {
        assertEquals(TrayAnchor(1600 - 248, 20), trayMenuPosition(TrayAnchor(1600, 20), 248, 300, screen))
    }

    @Test
    fun `the menu never leaves the usable area`() {
        // A Wayland host may send 0,0 — and a click in the corner must not push the menu off-screen
        assertEquals(TrayAnchor(0, 0), trayMenuPosition(TrayAnchor(0, 0), 248, 300, screen))
        assertEquals(TrayAnchor(1920 - 248, 1040 - 300), trayMenuPosition(TrayAnchor(5000, 5000), 248, 300, screen))
    }

    @Test
    fun `the height follows the rows and separators`() {
        val entry = TrayMenuEntry(1, "x", null) {}
        val withShortcuts = TrayMenuModel("t", listOf(entry), listOf(entry, entry), listOf(entry))
        val without = withShortcuts.copy(shortcuts = emptyList())
        assertEquals(TRAY_MENU_ROW_DP * 2 + TRAY_MENU_SEPARATOR_DP, trayMenuHeightDp(withShortcuts) - trayMenuHeightDp(without))
    }

    @Test
    fun `the sni pixmap is ARGB in network byte order`() {
        val image = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).apply { setRGB(0, 0, 0x80112233.toInt()) }
        val pixmap = image.toIconPixmap()
        assertEquals(1, pixmap.width)
        assertEquals(1, pixmap.height)
        assertArrayEquals(byteArrayOf(0x80.toByte(), 0x11, 0x22, 0x33), pixmap.data)
    }
}
