package app.n_zik.compagnon.components.player.timeline

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * [ProgressPercentage.safeValue]: a NaN input returns the zero fallback, an out-of-range input is
 * clamped to 0-1, an in-range value is kept.
 */
class SeekBarVisualizerTest {

    @Test
    fun `a NaN input returns the zero fallback`() {
        assertEquals(0f, ProgressPercentage.safeValue(Float.NaN).value)
    }

    @Test
    fun `an out-of-range input is clamped to 0-1`() {
        assertEquals(0f, ProgressPercentage.safeValue(-0.2f).value)
        assertEquals(1f, ProgressPercentage.safeValue(1.8f).value)
    }

    @Test
    fun `an in-range value is kept`() {
        assertEquals(0.42f, ProgressPercentage.safeValue(0.42f).value)
    }
}
