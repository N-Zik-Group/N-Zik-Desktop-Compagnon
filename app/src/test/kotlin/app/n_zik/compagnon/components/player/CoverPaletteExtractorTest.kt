package app.n_zik.compagnon.components.player

import androidx.compose.ui.graphics.toArgb
import app.n_zik.compagnon.components.theme.DefaultDarkColorPalette
import app.n_zik.compagnon.components.theme.dynamicColorPaletteOf
import app.n_zik.compagnon.components.theme.hsl
import app.n_zik.compagnon.core.palette.PaletteBitmap
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CoverPaletteExtractorTest {

    private fun solid(color: Int) = PaletteBitmap(IntArray(40 * 40) { color }, 40, 40)

    @Test
    fun `a coloured cover gives the legacy dynamic palette`() {
        val bitmap = solid(0xFF3366CC.toInt())
        val colors = extractM3ECoverColors(bitmap, isDark = true)!!
        assertFalse(colors.allAchromatic)
        assertEquals(dynamicColorPaletteOf(bitmap, true), m3eDynamicColorPaletteOf(bitmap, true))
    }

    @Test
    fun `a gray cover is neutralized and its light ramp capped in a dark theme`() {
        val bitmap = solid(0xFFB4B4B4.toInt())
        val colors = extractM3ECoverColors(bitmap, isDark = true)!!
        assertTrue(colors.allAchromatic)

        val palette = m3eDynamicColorPaletteOf(bitmap, isDark = true)!!
        val lightest = listOf(palette.background0, palette.background1, palette.background2, palette.background3, palette.background4)
            .maxOf { it.hsl.lightness }
        assertEquals(ACHROMATIC_RAMP_LIGHT_TONE_MAX_LIGHTNESS, lightest, 0.01f)
    }

    @Test
    fun `no dominant swatch gives no palette, and the player falls back to the given palette`() {
        val bitmap = solid(0xFF000000.toInt())
        assertNull(m3eDynamicColorPaletteOf(bitmap, isDark = true))

        val result = computePlayerDynamicPalette(bitmap, true, DefaultDarkColorPalette)
        assertEquals(DefaultDarkColorPalette, result.palette)
        assertEquals(DefaultDarkColorPalette.accent.toArgb(), result.dominant)
    }

    @Test
    fun `saturate adds 0_35 in dark mode above 0_1 and darkenBy halves the colour`() {
        val source = androidx.compose.ui.graphics.Color(0xFF4D7399.toInt())
        val saturated = m3eSaturate(0xFF4D7399.toInt(), lightTheme = false)
        assertEquals(source.hsl.saturation + 0.35f, saturated.hsl.saturation, 0.02f)
        val dark = saturated.m3eDarkenBy(lightTheme = false)
        assertEquals(saturated.red * 0.5f, dark.red, 0.005f)
    }
}
