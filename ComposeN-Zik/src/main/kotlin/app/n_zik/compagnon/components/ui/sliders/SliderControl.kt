package app.n_zik.compagnon.components.ui.sliders

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.n_zik.compagnon.typography
import app.n_zik.compagnon.utils.center
import app.n_zik.compagnon.utils.semiBold

/**
 * Port of `SliderControl` (phone's `app/n_zik/android/components/ui/sliders/SliderControl.kt`): [Slider] with
 * its value shown as an overlay.
 *
 * **Inert-slider convention** (the PC's inert settings rows, spec `spec-fill-pc-ui-gaps-settings` — reference
 * implementation [ListSliderMenuItem], `PlaybackSettingsMenu.kt` L280-355): a setting with no PC key is
 * rendered as a normal row — the title and the phone's default value — with the slider itself disabled:
 * `isEnabled = false` (the track at alpha 0.5, the value dimmed) + the whole row at alpha 0.5, the value
 * fixed at the phone's own default, and the call site gating [onSlide]/[onSlideComplete] so a drag writes
 * nothing; the row's long-press toasts the phone's description.
 */
@Composable
fun SliderControl(
    state: Float,
    range: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    onSlide: (Float) -> Unit = { },
    onSlideComplete: () -> Unit = { },
    toDisplay: @Composable (Float) -> String = { it.toInt().toString() },
    stepSize: Float = 0.1f,
    defaultValue: Float? = null,
    drawValuePoints: Boolean = false,
    isEnabled: Boolean = true,
    usePadding: Boolean = true,
    showValue: Boolean = true,
) = Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = modifier.height(36.dp),
) {

    Box(
        modifier = Modifier
            .fillMaxSize(),
    ) {
        Slider(
            isEnabled = isEnabled,
            state = state,
            setState = onSlide,
            onSlideComplete = onSlideComplete,
            range = range,
            stepSize = stepSize,
            defaultValue = defaultValue,
            drawValuePoints = drawValuePoints,
            modifier = Modifier
                .height(36.dp)
                .alpha(if (isEnabled) 0.6f else 0.5f)
                .let { if (usePadding) it.padding(start = 12.dp) else it }
                .padding(vertical = 16.dp)
                .fillMaxWidth(),
        )

        if (showValue) {
            BasicText(
                text = toDisplay(state),
                style = TextStyle(
                    textAlign = TextAlign.Center,
                    color = typography().xs.semiBold.color.copy(alpha = if (isEnabled) 1.0f else 0.5f),
                    fontSize = typography().xs.semiBold.center.fontSize,
                    fontWeight = typography().xs.semiBold.center.fontWeight,
                ),
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 10.dp),
            )
        }
    }
}
