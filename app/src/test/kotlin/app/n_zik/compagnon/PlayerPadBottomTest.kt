package app.n_zik.compagnon

import androidx.compose.ui.unit.dp
import app.n_zik.compagnon.enums.NavigationBarPosition
import app.n_zik.compagnon.enums.NavigationBarType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * The mini-player's bottom padding under the navigation bar ([playerPadBottomDp], the
 * `playerPadBottom` branch table of `MainActivity`), branch by branch, with its exact values.
 */
class PlayerPadBottomTest {

    @Test
    fun `over the settings page's own bar it is the floating icon-only bar plus its padding`() {
        // 64 (icon-only bar) + 25 (padding) + 4 (gap); the position is irrelevant here
        assertEquals(93.dp, playerPadBottomDp(true, true, NavigationBarPosition.Left, NavigationBarType.IconAndText))
    }

    @Test
    fun `over the floating icon-only bar it is its height plus its padding`() {
        // 64 (bar) + 25 (padding) + 4 (gap)
        assertEquals(93.dp, playerPadBottomDp(true, false, NavigationBarPosition.BottomFloating, NavigationBarType.IconOnly))
    }

    @Test
    fun `over the floating bar with labels it is its height plus its padding`() {
        // 84 (bar) + 25 (padding) + 4 (gap)
        assertEquals(113.dp, playerPadBottomDp(true, false, NavigationBarPosition.BottomFloating, NavigationBarType.IconAndText))
    }

    @Test
    fun `over the fixed bottom bar it is the standard nav bar height`() {
        // 64 (standard nav bar height) + 4 (gap)
        assertEquals(68.dp, playerPadBottomDp(true, false, NavigationBarPosition.Bottom, NavigationBarType.IconOnly))
    }

    @Test
    fun `over the other fixed bars it is a flat 5 dp`() {
        // The type is irrelevant here
        assertEquals(5.dp, playerPadBottomDp(true, false, NavigationBarPosition.Top, NavigationBarType.IconOnly))
        assertEquals(5.dp, playerPadBottomDp(true, false, NavigationBarPosition.Left, NavigationBarType.IconAndText))
        assertEquals(5.dp, playerPadBottomDp(true, false, NavigationBarPosition.Right, NavigationBarType.IconOnly))
    }

    @Test
    fun `without a bar it is the floating bar's padding`() {
        // 25 (the floating bar's padding); any page is irrelevant here
        assertEquals(25.dp, playerPadBottomDp(false, true, NavigationBarPosition.BottomFloating, NavigationBarType.IconOnly))
        assertEquals(25.dp, playerPadBottomDp(false, false, NavigationBarPosition.BottomFloating, NavigationBarType.IconAndText))
    }

    @Test
    fun `without a bar over a fixed position it is a flat 5 dp`() {
        // 5 dp (a fixed bar's position: bottom, and the other fixed bars alike)
        assertEquals(5.dp, playerPadBottomDp(false, false, NavigationBarPosition.Bottom, NavigationBarType.IconOnly))
        assertEquals(5.dp, playerPadBottomDp(false, false, NavigationBarPosition.Top, NavigationBarType.IconAndText))
    }
}
