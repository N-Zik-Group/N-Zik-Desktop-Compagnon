package app.n_zik.compagnon.components.dialog.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.n_zik.compagnon.bridge.state.LocalUiSettings
import app.n_zik.compagnon.colorPalette
import app.n_zik.compagnon.components.theme.ColorPalette
import app.n_zik.compagnon.components.theme.ModernBlackColorPalette
import app.n_zik.compagnon.components.theme.PureBlackColorPalette
import app.n_zik.compagnon.typography
import app.n_zik.compagnon.uiRoundnessShape
import app.n_zik.compagnon.utils.semiBold

/** The card's [sizeIn] bounds of the [DialogFrame]. */
internal data class FrameCardBounds(val maxWidth: Dp, val maxHeight: Dp)

/** The fraction of the window height the [DialogFrame] card never grows past (the phone's `MAX_HEIGHT_*`). */
internal const val MAX_HEIGHT_FRACTION = 0.8f

/**
 * The frame's pure bounds decision (the PC port of the phone's `DialogFrame`, spec
 * `spec-dialog-framework-port`): a fixed [maxWidth] cap (560 dp default, 420 dp for the two
 * updater dialogs) + the [MAX_HEIGHT_FRACTION] (80 %) cap of the window's height. Extracted so
 * the decision is testable without a Compose UI harness (this repo has none).
 */
internal fun dialogFrameBounds(maxWidth: Dp, windowHeightDp: Dp): FrameCardBounds =
    FrameCardBounds(maxWidth = maxWidth, maxHeight = windowHeightDp * MAX_HEIGHT_FRACTION)

/**
 * The frame's pure card-color decision (the PC port of the phone's `DialogFrame`, spec
 * `spec-dialog-framework-port`): the pitch-black branch renders the gray-dark `0xFF1A1A1A` when
 * the palette is one of the pure-black ones OR the pitch-black setting is on, otherwise the
 * palette's [ColorPalette.background1]. Extracted so the decision is testable without a Compose UI
 * harness (this repo has none).
 */
internal fun dialogFrameCardColor(palette: ColorPalette, isPitchBlack: Boolean): Color =
    if (palette === PureBlackColorPalette || palette === ModernBlackColorPalette || isPitchBlack) {
        Color(0xFF1A1A1A) // gray dark for pitch black themes
    } else {
        palette.background1
    }

/**
 * Port of the phone's `DialogFrame` (the phone's
 * `app/n_zik/android/components/dialog/common/DialogFrame.kt`) — the canonical n_zik frame (issue
 * #896): a card of [uiRoundnessShape] on the pitch-black-branch `0xFF1A1A1A` / the palette's
 * `background1`, 8 dp elevation, 16 dp card + 24 dp body padding, an optional `l` semi-bold title
 * and a `testTag("dialogFrameCard")` seam.
 *
 * Every PC dialog — themed, updater, ad-hoc, checkbox — must render through this frame, and
 * [ButtonRow] is the single source of its action pills: no dialog re-declares its own card or
 * button style.
 *
 * PC deviation (the phone's bounds are monitor ratios — 0.95/0.6/0.8 x `LocalConfiguration` x
 * `isLandscape`; on a desktop, `fillMaxWidth` content would grow to 95 % of a 1920 px window):
 * the fixed [maxWidth] cap + the 80 %-of-the-window height cap, via the `LocalWindowInfo` idiom
 * [CheckboxDialog] already uses.
 *
 * As on the phone, the card is the window's root and its body column is NOT scrollable — tall
 * content scrolls in an inner column (the [DefaultDialog] idiom); a scrollable body would
 * collapse in `Modifier.weight(1f, fill = false)`.
 */
@Composable
fun DialogFrame(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    maxWidth: Dp = 560.dp,
    horizontalAlignment: Alignment.Horizontal = Alignment.CenterHorizontally,
    content: @Composable ColumnScope.() -> Unit,
) {
    val palette = colorPalette()
    // The pitch-black setting (a composition read, hoisted out of the card-color decision)
    val isPitchBlack = LocalUiSettings.current.isPitchBlack
    // The PC window bounds (the `LocalWindowInfo` idiom — the dialog window stands in for the
    // phone's screen, as [CheckboxDialog]): fixed [maxWidth] + 80 % of the window's height
    val window = LocalWindowInfo.current.containerSize
    val windowHeightDp = with(LocalDensity.current) { window.height.toDp() }
    val bounds = dialogFrameBounds(maxWidth, windowHeightDp)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Card(
            modifier = modifier
                .testTag("dialogFrameCard")
                .wrapContentSize()
                .sizeIn(maxWidth = bounds.maxWidth, maxHeight = bounds.maxHeight)
                .padding(16.dp),
            shape = uiRoundnessShape(),
            colors = CardDefaults.cardColors(
                containerColor = dialogFrameCardColor(palette, isPitchBlack),
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = horizontalAlignment,
            ) {
                title?.let {
                    Box(
                        modifier = Modifier
                            .padding(bottom = 4.dp)
                            .fillMaxWidth(0.9f),
                        contentAlignment = Alignment.Center,
                    ) {
                        BasicText(
                            text = it,
                            style = typography().l.semiBold.copy(color = palette.text),
                        )
                    }
                }
                content()
            }
        }
    }
}
