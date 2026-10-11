package app.n_zik.compagnon.enums

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The home's navigation bar positions ([NavigationBarPosition]) — the phone's `isHorizontal` (the
 * bar is horizontal in Top, Bottom, BottomFloating), `isBottomType` (the bottom positions, which
 * slide with the home's bottom bar offset) and `isFloating` (the floating bar only).
 */
class NavigationBarPositionTest {

    @Test
    fun `the horizontal positions are top, bottom and bottom floating`() {
        assertTrue(NavigationBarPosition.Top.isHorizontal)
        assertTrue(NavigationBarPosition.Bottom.isHorizontal)
        assertTrue(NavigationBarPosition.BottomFloating.isHorizontal)
        assertFalse(NavigationBarPosition.Left.isHorizontal)
        assertFalse(NavigationBarPosition.Right.isHorizontal)
    }

    @Test
    fun `the bottom positions are bottom and bottom floating`() {
        assertTrue(NavigationBarPosition.Bottom.isBottomType)
        assertTrue(NavigationBarPosition.BottomFloating.isBottomType)
        assertFalse(NavigationBarPosition.Left.isBottomType)
        assertFalse(NavigationBarPosition.Right.isBottomType)
        assertFalse(NavigationBarPosition.Top.isBottomType)
    }

    @Test
    fun `only the floating bar floats`() {
        assertTrue(NavigationBarPosition.BottomFloating.isFloating)
        assertFalse(NavigationBarPosition.Bottom.isFloating)
        assertFalse(NavigationBarPosition.Left.isFloating)
        assertFalse(NavigationBarPosition.Right.isFloating)
        assertFalse(NavigationBarPosition.Top.isFloating)
    }
}
