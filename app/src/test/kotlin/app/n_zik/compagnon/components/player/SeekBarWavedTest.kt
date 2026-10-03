package app.n_zik.compagnon.components.player

import androidx.compose.ui.unit.Density
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.PI

class SeekBarWavedTest {

    @Test
    fun `the wave stroke and the sine length are 5 dp, the phone's 15 physical px at 3x density`() {
        assertEquals(5f, WAVE_STROKE.value)
        assertEquals(5f, WAVE_LENGTH.value)

        // The dp to px resolution itself — the mechanism behind the desktop bug — at the
        // desktop (1x) and the phone's (~3x) densities.
        with(Density(1f, 1f)) {
            assertEquals(5f, WAVE_STROKE.toPx())
            assertEquals(5f, WAVE_LENGTH.toPx())
        }
        with(Density(3f, 1f)) {
            assertEquals(15f, WAVE_STROKE.toPx())
            assertEquals(15f, WAVE_LENGTH.toPx())
        }
    }

    @Test
    fun `the wave repeats every 2pi times its wavelength`() {
        val length = WAVE_LENGTH.value
        val height = 20f
        val period = 2f * PI.toFloat() * length
        var x = 0f
        while (x <= 200f) {
            assertEquals(waveYFromX(x, length, 0.3f, height), waveYFromX(x + period, length, 0.3f, height), 1e-3f)
            x += 7f
        }
    }

    @Test
    fun `the wave reaches its exact midline and bounds`() {
        val length = WAVE_LENGTH.value
        val height = 20f

        // At progress 0: x = 0 sits on the midline, x = L·π/2 at the top, x = L·3π/2 at the bottom.
        assertEquals(height / 2f, waveYFromX(0f, length, 0f, height), 1e-3f)
        assertEquals(height, waveYFromX(length * PI.toFloat() / 2f, length, 0f, height), 1e-3f)
        assertEquals(0f, waveYFromX(length * 3f * PI.toFloat() / 2f, length, 0f, height), 1e-3f)

        var x = 0f
        while (x <= 300f) {
            val y = waveYFromX(x, length, 0.1f, height)
            assertTrue(y >= 0f && y <= height)
            x += 3f
        }
    }

    @Test
    fun `progress shifts the wave by the matching fraction of a period`() {
        val length = WAVE_LENGTH.value
        val height = 20f
        val shift = 0.25f * 2f * PI.toFloat() * length
        var x = 0f
        while (x <= 150f) {
            assertEquals(waveYFromX(x, length, 0.25f, height), waveYFromX(x + shift, length, 0f, height), 1e-3f)
            x += 5f
        }
    }

    @Test
    fun `the wave is continuous across the animation loop seam`() {
        val length = WAVE_LENGTH.value
        val height = 20f
        var x = 0f
        while (x <= 100f) {
            assertEquals(waveYFromX(x, length, 0f, height), waveYFromX(x, length, 1f, height), 1e-3f)
            x += 5f
        }
    }

    @Test
    fun `the sampling step is 1 dp, the phone's 3 physical px at 3x density`() {
        assertEquals(1f, WAVE_PATH_STEP.value)

        with(Density(1f, 1f)) {
            assertEquals(1f, WAVE_PATH_STEP.toPx())
        }
        with(Density(3f, 1f)) {
            assertEquals(3f, WAVE_PATH_STEP.toPx())
        }
    }

    @Test
    fun `the wave sampling stops strictly before the width at the given step`() {
        assertTrue(waveSampleXs(0f, 1f).isEmpty())
        assertEquals(listOf(0f), waveSampleXs(3f, 3f))
        assertEquals(listOf(0f, 1f, 2f), waveSampleXs(3f, 1f))
        assertEquals(listOf(0f, 3f, 6f, 9f), waveSampleXs(10f, 3f))
    }
}
