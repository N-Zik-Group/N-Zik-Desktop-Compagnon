package app.n_zik.compagnon.components.dialog.common

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.n_zik.compagnon.components.theme.DefaultDarkColorPalette
import app.n_zik.compagnon.components.theme.ModernBlackColorPalette
import app.n_zik.compagnon.components.theme.PureBlackColorPalette
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * The pure decisions of the [DialogFrame] (the PC port of the phone's
 * `app/n_zik/android/components/dialog/common/DialogFrame.kt`, issue #896): a fixed max width —
 * 560 dp default, 420 dp for the two updater dialogs — plus the 80 %-of-the-window height cap
 * (the phone's monitor-ratio bounds are NOT the PC semantics — on a desktop, `fillMaxWidth`
 * content would grow to 95 % of a 1920 px window), and the pitch-black card-color branch
 * (`0xFF1A1A1A` vs the palette's `background1`).
 *
 * This repo has no Compose UI test harness (no Robolectric / `createComposeRule` — a new
 * dependency), so the frame's pure decisions are unit-tested here (extracted as
 * [dialogFrameBounds] and [dialogFrameCardColor]) and the shared frame contract is pinned by
 * [DialogFrameworkSharedSourceTest].
 */
class DialogFrameTest {

    @Test
    fun `the default cap is 560 dp wide`() {
        val bounds = dialogFrameBounds(560.dp, 800.dp)
        assertEquals(560f, bounds.maxWidth.value, 0.001f)
    }

    @Test
    fun `the updater dialogs pass the 420 dp cap`() {
        val bounds = dialogFrameBounds(420.dp, 800.dp)
        assertEquals(420f, bounds.maxWidth.value, 0.001f)
    }

    @Test
    fun `the height cap is 80 percent of the window height`() {
        val bounds = dialogFrameBounds(560.dp, 800.dp)
        assertEquals(640f, bounds.maxHeight.value, 0.01f)
    }

    @Test
    fun `the height cap is independent of the width cap`() {
        val bounds = dialogFrameBounds(420.dp, 1000.dp)
        assertEquals(420f, bounds.maxWidth.value, 0.001f)
        assertEquals(800f, bounds.maxHeight.value, 0.01f)
    }

    @Test
    fun `the pure-black palette renders the gray-dark card`() {
        assertEquals(Color(0xFF1A1A1A), dialogFrameCardColor(PureBlackColorPalette, false))
    }

    @Test
    fun `the modern-black palette renders the gray-dark card`() {
        assertEquals(Color(0xFF1A1A1A), dialogFrameCardColor(ModernBlackColorPalette, false))
    }

    @Test
    fun `the pitch-black setting renders the gray-dark card on a non-black palette`() {
        assertEquals(Color(0xFF1A1A1A), dialogFrameCardColor(DefaultDarkColorPalette, true))
    }

    @Test
    fun `a non-black palette without pitch black renders its background1`() {
        assertEquals(
            DefaultDarkColorPalette.background1,
            dialogFrameCardColor(DefaultDarkColorPalette, false),
        )
    }
}
