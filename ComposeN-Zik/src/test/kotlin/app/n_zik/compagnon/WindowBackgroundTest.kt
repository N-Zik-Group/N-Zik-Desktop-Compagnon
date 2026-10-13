package app.n_zik.compagnon

import androidx.compose.ui.graphics.Color
import java.awt.Color as AwtColor
import javax.swing.JLayeredPane
import javax.swing.JPanel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class WindowBackgroundTest {
    @Test
    fun `an opaque color converts to the same AWT rgb with full alpha`() {
        val awt = Color(0xFF16171D).toAwt()
        assertEquals(AwtColor(0xFF16171D.toInt(), true), awt)
    }

    @Test
    fun `a color with partial alpha keeps its alpha channel`() {
        val awt = Color(0x80FF0000).toAwt()
        assertEquals(0x80, awt.alpha)
        assertEquals(0xFF, awt.red)
        assertEquals(0x00, awt.green)
        assertEquals(0x00, awt.blue)
    }

    @Test
    fun `a zero-alpha color keeps its rgb`() {
        val awt = Color(0x00FF0000).toAwt()
        assertEquals(0x00, awt.alpha)
        assertEquals(0xFF, awt.red)
        assertEquals(0x00, awt.green)
        assertEquals(0x00, awt.blue)
    }

    @Test
    fun `the tree walk paints every component below the root`() {
        val root = JPanel()
        val middle = JLayeredPane()
        val leaf = JPanel()
        root.add(middle)
        middle.add(leaf)
        val color = AwtColor(0x12, 0x34, 0x56, 0x78)
        paintComponentTree(root, color)
        assertEquals(color, root.background)
        assertEquals(color, middle.background)
        assertEquals(color, leaf.background)
    }

    @Test
    fun `the resize erase property is the AWT Windows toolkit key`() {
        assertEquals("sun.awt.erasebackgroundonresize", ERASE_BACKGROUND_ON_RESIZE_PROPERTY)
    }
}
