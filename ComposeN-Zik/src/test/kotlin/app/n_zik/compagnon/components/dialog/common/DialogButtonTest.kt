package app.n_zik.compagnon.components.dialog.common

import androidx.compose.ui.graphics.Color
import app.n_zik.compagnon.components.theme.DefaultDarkColorPalette
import app.n_zik.compagnon.generated.resources.Res
import app.n_zik.compagnon.generated.resources.close
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The pure decisions of [ButtonRow]'s pills (the PC port of the phone's
 * `app/n_zik/android/components/dialog/common/ButtonRow.kt`, issue #896): the role → label-color
 * resolution (secondary → `text`, primary → `onAccent`, danger → white, disabled →
 * `textDisabled`, an explicit [DialogButton.textColor] overriding any of them), the role →
 * surface-fill (primary → `accent`, danger → `red`, else `background2`) and the icon-only
 * decision (icon + empty text → the centered wrap-content pill WITHOUT the marquee).
 *
 * This repo has no Compose UI test harness (no Robolectric / `createComposeRule` — a new
 * dependency), so the brick's pure decisions are unit-tested here and its rendering contract
 * (the continuous marquee) is pinned by [DialogFrameworkSharedSourceTest].
 */
class DialogButtonTest {

    private val palette = DefaultDarkColorPalette

    @Test
    fun `the secondary pill resolves to the text color`() {
        val button = DialogButton(text = "cancel", onClick = {})
        assertEquals(palette.text, resolvedColor(button, palette))
    }

    @Test
    fun `the primary pill resolves to onAccent`() {
        val button = DialogButton(text = "confirm", onClick = {}, primary = true)
        assertEquals(palette.onAccent, resolvedColor(button, palette))
    }

    @Test
    fun `the danger pill resolves to white`() {
        val button = DialogButton(text = "cancel", onClick = {}, danger = true)
        assertEquals(Color.White, resolvedColor(button, palette))
    }

    @Test
    fun `a disabled pill resolves to textDisabled even with a role`() {
        val button = DialogButton(text = "confirm", onClick = {}, primary = true, enabled = false)
        assertEquals(palette.textDisabled, resolvedColor(button, palette))
    }

    @Test
    fun `an explicit text color overrides the resolved role`() {
        val button = DialogButton(text = "reset", onClick = {}, primary = true, textColor = Color.Black)
        assertEquals(Color.Black, resolvedColor(button, palette))
    }

    @Test
    fun `an icon with an empty text is the icon-only pill`() {
        val button = DialogButton(text = "", onClick = {}, icon = Res.drawable.close)
        assertTrue(isIconOnly(button))
    }

    @Test
    fun `an icon with a label is not icon-only`() {
        val button = DialogButton(text = "ok", onClick = {}, icon = Res.drawable.close)
        assertFalse(isIconOnly(button))
    }

    @Test
    fun `an empty text without an icon is not icon-only`() {
        val button = DialogButton(text = "", onClick = {})
        assertFalse(isIconOnly(button))
    }

    @Test
    fun `the primary pill fills with the accent`() {
        val button = DialogButton(text = "confirm", onClick = {}, primary = true)
        assertEquals(palette.accent, pillSurfaceColor(button, palette))
    }

    @Test
    fun `the danger pill fills with red`() {
        val button = DialogButton(text = "cancel", onClick = {}, danger = true)
        assertEquals(palette.red, pillSurfaceColor(button, palette))
    }

    @Test
    fun `the secondary pill fills with background2`() {
        val button = DialogButton(text = "cancel", onClick = {})
        assertEquals(palette.background2, pillSurfaceColor(button, palette))
    }
}
