package app.n_zik.compagnon.components.player

import app.n_zik.compagnon.components.theme.DefaultDarkColorPalette
import app.n_zik.compagnon.components.theme.dynamicColorPaletteOf
import app.n_zik.compagnon.components.theme.lerpTo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PaletteFadeModelTest {

    private val first = DefaultDarkColorPalette
    private val second = dynamicColorPaletteOf(floatArrayOf(120f, 0.5f, 0.5f), true)
    private val third = dynamicColorPaletteOf(floatArrayOf(300f, 0.5f, 0.5f), true)

    @Test
    fun `the first target and an unchanged one start no fade`() {
        val model = PaletteFadeModel()
        assertFalse(model.retarget(first, 1f))
        assertFalse(model.retarget(first, 1f))
        assertEquals(first, model.paletteAt(0.3f))
    }

    @Test
    fun `a new target fades from the old one`() {
        val model = PaletteFadeModel()
        model.retarget(first, 1f)
        assertTrue(model.retarget(second, 1f))
        assertTrue(model.isFading)
        assertEquals(first.lerpTo(second, 0.5f), model.paletteAt(0.5f))
        model.endFade()
        assertEquals(second, model.paletteAt(0.5f))
    }

    @Test
    fun `a fast change restarts from the palette on screen`() {
        val model = PaletteFadeModel()
        model.retarget(first, 1f)
        model.retarget(second, 1f)
        assertTrue(model.retarget(third, 0.5f))
        assertEquals(first.lerpTo(second, 0.5f), model.paletteAt(0f))
    }
}
