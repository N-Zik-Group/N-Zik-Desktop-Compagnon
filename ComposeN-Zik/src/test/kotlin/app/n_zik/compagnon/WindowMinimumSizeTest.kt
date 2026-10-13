package app.n_zik.compagnon

import java.awt.Dimension
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class WindowMinimumSizeTest {
    @Test
    fun `a large screen gets the preferred minimum`() {
        assertEquals(Dimension(1000, 750), windowMinimumSize(Dimension(1920, 1040)))
    }

    @Test
    fun `a short scaled screen caps the minimum height only`() {
        // 1080p at 175 %: 1097 x 617 logical pixels
        assertEquals(Dimension(1000, 617), windowMinimumSize(Dimension(1097, 617)))
    }

    @Test
    fun `a narrow screen caps the minimum width only`() {
        assertEquals(Dimension(900, 750), windowMinimumSize(Dimension(900, 1200)))
    }

    @Test
    fun `the preferred minimum is Spotify's 1000 x 750`() {
        assertEquals(Dimension(1000, 750), PREFERRED_MINIMUM_WINDOW_SIZE)
    }
}
