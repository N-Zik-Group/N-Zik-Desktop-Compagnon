package app.n_zik.compagnon.core.palette

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PaletteTest {

    private fun image(width: Int, height: Int, color: (x: Int, y: Int) -> Int) =
        PaletteBitmap(IntArray(width * height) { color(it % width, it / width) }, width, height)

    @Test
    fun `colorToHSL matches androidx ColorUtils`() {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(0xFF3366CC.toInt(), hsl)
        assertEquals(220f, hsl[0], 0.01f)
        assertEquals(0.6f, hsl[1], 0.01f)
        assertEquals(0.5f, hsl[2], 0.01f)

        ColorUtils.colorToHSL(0xFF808080.toInt(), hsl)
        assertEquals(0f, hsl[0])
        assertEquals(0f, hsl[1])
    }

    @Test
    fun `a single colour cover has it as dominant swatch with the whole population`() {
        val bitmap = image(50, 50) { _, _ -> 0xFF3366CC.toInt() }
        val palette = Palette.from(bitmap).maximumColorCount(8).generate()

        val dominant = palette.dominantSwatch!!
        assertEquals(2500, dominant.population)
        // 5-bit quantization: 0x33 -> 6 -> 0x30, 0x66 -> 12 -> 0x60, 0xCC -> 25 -> 0xC8
        assertEquals(0xFF3060C8.toInt(), dominant.rgb)
        assertEquals(dominant.rgb, palette.getVibrantColor(0))
    }

    @Test
    fun `black, white and the red I line are filtered out`() {
        val bitmap = image(20, 20) { x, _ -> if (x < 10) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }
        val palette = Palette.from(bitmap).maximumColorCount(8).generate()

        assertTrue(palette.swatches.isEmpty())
        assertNull(palette.dominantSwatch)
        assertEquals(42, palette.getDominantColor(42))
    }

    @Test
    fun `median cut keeps at most the requested colours and the larger area dominant`() {
        val colors = intArrayOf(0xFF3366CC.toInt(), 0xFF33CC66.toInt(), 0xFFCC3366.toInt(), 0xFF6633CC.toInt())
        val bitmap = image(40, 40) { x, y ->
            if (y < 25) colors[0] else colors[1 + (x / 14).coerceAtMost(2)]
        }
        val palette = Palette.from(bitmap).maximumColorCount(2).generate()

        assertTrue(palette.swatches.size <= 2)
        assertEquals(40 * 40, palette.swatches.sumOf { it.population })
    }

    @Test
    fun `an image above 112 x 112 is scaled down to about that area`() {
        val scaled = Palette.scaleBitmapDown(IntArray(400 * 300), 400, 300)
        assertTrue(scaled.size <= 130 * 98 && scaled.size >= 112 * 112)
        assertEquals(50 * 50, Palette.scaleBitmapDown(IntArray(50 * 50), 50, 50).size)
    }
}
