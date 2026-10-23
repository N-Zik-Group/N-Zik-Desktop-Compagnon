package app.n_zik.compagnon.components.dialog.common

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.n_zik.compagnon.colorPalette
import app.n_zik.compagnon.components.theme.ColorPalette
import app.n_zik.compagnon.typography
import app.n_zik.compagnon.uiRoundnessShape
import app.n_zik.compagnon.utils.medium
import app.n_zik.compagnon.utils.semiBold
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/**
 * One pill button for [ButtonRow] (port of the phone's
 * `app/n_zik/android/components/dialog/common/ButtonRow.kt` — PC: [icon] is a Compose-resources
 * [DrawableResource] instead of the phone's `Int` res id, drawn fixed-size before the label).
 *
 * [text] renders on a single line — a label wider than its cell is bounded to it and scrolled
 * with a continuous marquee (it may be empty for icon-only pills); [primary] / [danger] pick the
 * accent and the red cancel pill; a disabled [enabled] pill stays visible
 * ([ColorPalette.textDisabled] text) but does not fire [onClick]; [textColor] overrides the
 * resolved label color (e.g. a dimmed reset).
 */
data class DialogButton(
    val text: String,
    val onClick: () -> Unit,
    val primary: Boolean = false,
    val danger: Boolean = false,
    val enabled: Boolean = true,
    val icon: DrawableResource? = null,
    val textColor: Color? = null,
)

/**
 * The pure icon-only decision of a [DialogButton] (port of the phone's `ButtonRow`, issue #896):
 * an icon + an empty label renders a small pill centered in its cell, WITHOUT the marquee (an
 * empty label has nothing to scroll). Extracted so the decision is testable without a Compose UI
 * harness (this repo has none).
 */
internal fun isIconOnly(button: DialogButton): Boolean = button.icon != null && button.text.isEmpty()

/**
 * Overflow-safe row of pill buttons — the single source of every PC dialog button (port of the
 * phone's `ButtonRow`, issue #896).
 *
 * Every present button gets an equally weighted cell (the phone's `ConfirmDialog` distribution,
 * `weight(1f)`), so all pills stay visible and tappable on any screen width and any locale. The
 * pill wraps its content and is centered in its cell: a short label renders a wrap-content pill,
 * and only a label wider than its cell stretches the pill to the cell, bounded to it and scrolled
 * (continuous marquee, same pattern as [CheckboxDialog]). An icon-only button renders a small
 * pill centered in its cell instead.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ButtonRow(
    buttons: List<DialogButton>,
    modifier: Modifier = Modifier,
) {
    val palette = colorPalette()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        buttons.forEach { button ->
            val iconOnly = isIconOnly(button)
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    modifier = pillModifier(button, palette),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    button.icon?.let { iconRes ->
                        Icon(
                            painter = painterResource(iconRes),
                            contentDescription = null,
                            tint = resolvedColor(button, palette),
                            modifier = Modifier.size(16.dp),
                        )
                        if (!iconOnly) {
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                    }
                    // The weighted label makes the pill fill its cell; without it
                    // (icon-only) the pill wraps its content and stays centered.
                    if (!iconOnly) {
                        BasicText(
                            text = button.text,
                            maxLines = 1,
                            style = (if (button.primary) typography().xs.semiBold else typography().xs.medium)
                                .copy(
                                    color = resolvedColor(button, palette),
                                    textAlign = TextAlign.Center,
                                ),
                            modifier = Modifier
                                .weight(1f)
                                .basicMarquee(iterations = Int.MAX_VALUE),
                        )
                    }
                }
            }
        }
    }
}

/**
 * The pure surface-fill decision of a [DialogButton] (port of the phone's `ButtonRow`): primary →
 * `accent`, danger → `red`, else the neutral `background2`. Extracted (and `internal`) so the
 * decision is testable without a Compose UI harness (this repo has none).
 */
internal fun pillSurfaceColor(button: DialogButton, palette: ColorPalette): Color =
    when {
        button.primary -> palette.accent
        button.danger -> palette.red
        else -> palette.background2
    }

/** The pill surface of every [DialogButton]: rounded clip, role fill, tap and padding. */
@Composable
internal fun pillModifier(button: DialogButton, palette: ColorPalette): Modifier =
    Modifier
        .clip(uiRoundnessShape())
        .background(pillSurfaceColor(button, palette))
        .clickable(enabled = button.enabled, onClick = button.onClick)
        .padding(horizontal = 16.dp, vertical = 10.dp)

/**
 * The pure label-color decision of a [DialogButton] (port of the phone's `ButtonRow`): an
 * explicit [DialogButton.textColor] wins; otherwise disabled → `textDisabled`, primary →
 * `onAccent`, danger → white, secondary → `text`. Extracted (and `internal`) so the decision is
 * testable without a Compose UI harness (this repo has none).
 */
internal fun resolvedColor(button: DialogButton, palette: ColorPalette): Color =
    button.textColor ?: when {
        !button.enabled -> palette.textDisabled
        button.primary -> palette.onAccent
        button.danger -> Color.White
        else -> palette.text
    }
