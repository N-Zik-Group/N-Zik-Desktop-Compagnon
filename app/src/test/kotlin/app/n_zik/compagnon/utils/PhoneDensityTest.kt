package app.n_zik.compagnon.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Story 11c: the phone's physical px become dp at its reference density 3.0. */
class PhoneDensityTest {

    @Test
    fun `phone px are divided by the reference density 3`() {
        assertEquals(3f, PHONE_REFERENCE_DENSITY)
        assertEquals(5f, phonePx(15f).value, 1e-4f)
        assertEquals(10f / 3f, phonePx(10f).value, 1e-4f)
        assertEquals(1f, phonePx(3f).value, 1e-4f)
        assertEquals(0f, phonePx(0f).value)
    }
}
