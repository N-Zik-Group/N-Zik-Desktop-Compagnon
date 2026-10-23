package app.n_zik.compagnon.components.player.background

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** [roundDecimals] (the library's `utils.round` port): the time stepped to the given number of digits, so the shader's uniforms barely change between frames. */
class ShaderTest {

    @Test
    fun `steps the value to the given number of digits`() {
        assertEquals(2f, 1.6f.roundDecimals(0))
        assertEquals(12f, 12.3f.roundDecimals(0))
        assertEquals(12.3f, 12.3f.roundDecimals(1))
        assertEquals(1.23f, 1.23f.roundDecimals(2))
    }
}
