package app.n_zik.compagnon.components.theme

import androidx.compose.ui.graphics.Color
import app.n_zik.compagnon.core.palette.PaletteBitmap
import app.n_zik.compagnon.enums.ColorPaletteMode
import app.n_zik.compagnon.enums.ColorPaletteName
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class ColorPaletteTest {

    @Test
    fun `the dynamic palette of an hsl keeps its hue and caps the saturations`() {
        val palette = dynamicColorPaletteOf(floatArrayOf(220f, 0.6f, 0.5f), isDark = true)

        assertEquals(Color.hsl(220f, 0.1f, 0.10f), palette.background0)
        assertEquals(Color.hsl(220f, 0.3f, 0.15f), palette.background1)
        assertEquals(Color.hsl(220f, 0.4f, 0.2f), palette.background2)
        assertEquals(Color.hsl(220f, 0.5f, 0.5f), palette.accent)
        assertEquals(Color.hsl(220f, 0.02f, 0.88f), palette.text)
        // Untouched roles come from the default dark palette
        assertEquals(DefaultDarkColorPalette.background3, palette.background3)
        assertEquals(true, palette.isDark)
    }

    @Test
    fun `the violet default caps the accent saturation at 0_4 in dark mode`() {
        val palette = dynamicColorPaletteOf(Color(0.54509807f, 0.36078432f, 0.9647059f), isDark = true)
        val hsl = palette.accent.hsl
        assertEquals(0.4f, hsl.saturation, 0.01f)
        assertEquals(0.5f, hsl.lightness, 0.01f)
    }

    @Test
    fun `a cover's dynamic palette follows its dominant swatch`() {
        val bitmap = PaletteBitmap(IntArray(30 * 30) { 0xFF3366CC.toInt() }, 30, 30)
        val palette = dynamicColorPaletteOf(bitmap, isDark = true)!!
        assertEquals(221f, palette.accent.hsl.hue, 1f) // the 5-bit quantized 0x3060C8

        val blackAndWhite = PaletteBitmap(IntArray(30 * 30) { if (it % 2 == 0) 0xFF000000.toInt() else -1 }, 30, 30)
        assertNull(dynamicColorPaletteOf(blackAndWhite, isDark = true))
    }

    @Test
    fun `derived colours use red on the static palettes and the accent on a dynamic one`() {
        assertEquals(DefaultDarkColorPalette.red, DefaultDarkColorPalette.favoritesIcon)
        assertEquals(DefaultDarkColorPalette.text, DefaultDarkColorPalette.collapsedPlayerProgressBar)
        assertEquals(DefaultDarkColorPalette.red.copy(alpha = 0.4f), DefaultDarkColorPalette.favoritesOverlay)

        val dynamic = dynamicColorPaletteOf(floatArrayOf(120f, 0.5f, 0.5f), isDark = true)
        assertEquals(dynamic.accent, dynamic.favoritesIcon)
        assertEquals(dynamic.accent, dynamic.collapsedPlayerProgressBar)
        assertEquals(dynamic.accent.copy(alpha = 0.4f), dynamic.favoritesOverlay)
        assertEquals(dynamic.background2, dynamic.primaryButton)
    }

    @Test
    fun `lerpTo goes from one palette to the other`() {
        val target = dynamicColorPaletteOf(floatArrayOf(120f, 0.5f, 0.5f), isDark = true)
        assertEquals(DefaultDarkColorPalette, DefaultDarkColorPalette.lerpTo(target, 0f))
        assertEquals(target, DefaultDarkColorPalette.lerpTo(target, 1f))
    }

    @Test
    fun `the static start is the default dark palette itself`() {
        val palette = colorPaletteOf(ColorPaletteName.Dynamic, ColorPaletteMode.Dark, true)
        assertSame(DefaultDarkColorPalette, palette)
        assertNotSame(DefaultDarkColorPalette, dynamicColorPaletteOf(DefaultDarkColorPalette.accent, true))
    }
}
