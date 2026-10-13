package app.n_zik.compagnon.components.styling

import androidx.compose.ui.unit.dp
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * The home's bottom spacer formula ([bottomSpacerDp], the [Dimensions.bottomSpacer] branch table):
 * with the floating bar, the bar's height (icon-only or with labels) plus its 25 dp padding and
 * the 10 dp visual gap — the 72 dp mini-player added while a track is current; with a fixed bar,
 * a flat 100 dp whatever the position.
 */
class BottomSpacerDpTest {

    @Test
    fun `the floating icon-only bar without the mini-player is 99 dp`() {
        // 64 (bar) + 25 (padding) + 10 (visual gap)
        assertEquals(99.dp, bottomSpacerDp(isFloating = true, isIconOnly = true, miniPlayerActive = false))
    }

    @Test
    fun `the floating icon-only bar with the mini-player is 171 dp`() {
        // 64 (bar) + 25 (padding) + 72 (mini-player) + 10 (visual gap)
        assertEquals(171.dp, bottomSpacerDp(isFloating = true, isIconOnly = true, miniPlayerActive = true))
    }

    @Test
    fun `the floating bar with labels without the mini-player is 119 dp`() {
        // 84 (bar) + 25 (padding) + 10 (visual gap)
        assertEquals(119.dp, bottomSpacerDp(isFloating = true, isIconOnly = false, miniPlayerActive = false))
    }

    @Test
    fun `the floating bar with labels with the mini-player is 191 dp`() {
        // 84 (bar) + 25 (padding) + 72 (mini-player) + 10 (visual gap)
        assertEquals(191.dp, bottomSpacerDp(isFloating = true, isIconOnly = false, miniPlayerActive = true))
    }

    @Test
    fun `any fixed bar is a flat 100 dp`() {
        assertEquals(100.dp, bottomSpacerDp(isFloating = false, isIconOnly = true, miniPlayerActive = true))
        assertEquals(100.dp, bottomSpacerDp(isFloating = false, isIconOnly = false, miniPlayerActive = false))
    }
}
