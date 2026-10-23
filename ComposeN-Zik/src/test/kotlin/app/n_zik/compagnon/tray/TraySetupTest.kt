package app.n_zik.compagnon.tray

import app.n_zik.compagnon.enums.CloseBehavior
import java.awt.Color
import java.awt.image.BufferedImage
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
    fun `the tray icon keeps a transparent margin around itself`() {
        // the monochrome icon is full-bleed — the margin keeps it from looking oversized next to
        // the other panel icons (live feedback 2026-10-12)
        val padded = padTrayImage(BufferedImage(30, 30, BufferedImage.TYPE_INT_RGB), 64)
        assertEquals(64, padded.width)
        assertEquals(64, padded.height)
        assertEquals(0, padded.getRGB(0, 0) and 0xff000000.toInt()) // a corner: transparent
        assertTrue(padded.getRGB(32, 32) and 0xff000000.toInt() != 0) // the center: the icon itself
    }

    @Test
    fun `the native menu icon tint replaces the color and preserves the alpha`() {
        // The row icons are monochrome glyphs: SrcAtop replaces the pixel color, keeps the
        // transparency (an opaque pixel becomes the tint, a semi-transparent one keeps its alpha)
        val image = BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB).apply {
            setRGB(0, 0, 0xFF112233.toInt())
            setRGB(1, 0, 0x80112233.toInt())
            setRGB(0, 1, 0x00112233)
        }
        tintImage(image, Color(0x4567))
        // getRGB's 0xAARRGGBB: the opaque pixel becomes the tint, the semi-transparent one keeps
        // its alpha, the transparent one is untouched
        assertEquals(0xFF004567.toInt(), image.getRGB(0, 0))
        assertEquals(0x80004567.toInt(), image.getRGB(1, 0))
        assertEquals(0x00112233, image.getRGB(0, 1))
    }

    @Test
    fun `the tray and menu icon tint follows the session color scheme`() {
        // prefer-light → the dark tint (the light menu/panel), anything else (prefer-dark, the
        // default, no value) → white (the dark menu/panel color this targets)
        assertEquals(Color(0xFF241F1F.toInt()), menuIconTintFor("prefer-light"))
        assertEquals(Color.WHITE, menuIconTintFor("prefer-dark"))
        assertEquals(Color.WHITE, menuIconTintFor("default"))
        assertEquals(Color.WHITE, menuIconTintFor(null))
    }

    @Test
    fun `the resolved menu tint is one of the two session colors`() {
        // [primeMenuTint] runs off the UI thread (from main()): on Linux it reads the host dconf
        // color-scheme, on Windows it is a no-op (no dconf — the AWT taskbar keeps the full-color

        // untinted app icon) and the tint stays the white default. Either way the tint is one of
        // the two session colors, never a third.
        primeMenuTint()
        assertTrue(menuIconTint() in listOf(Color.WHITE, Color(0xFF241F1F.toInt())))
    }

    @Test
    fun `an unknown or missing wire value reads as the default minimize to tray`() {
        assertEquals(CloseBehavior.MinimizeToTray, CloseBehavior.fromWire("bogus"))
        assertEquals(CloseBehavior.MinimizeToTray, CloseBehavior.fromWire(""))
        assertEquals(CloseBehavior.Quit, CloseBehavior.fromWire("quit"))
    }
}
