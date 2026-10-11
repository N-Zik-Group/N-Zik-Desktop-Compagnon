package app.n_zik.compagnon.components.player.timeline

import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.rememberTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.n_zik.compagnon.colorPalette
import kotlin.math.roundToLong

/**
 * Port of `SeekBarColored` (phone's `app/it/fast4x/rimusic/ui/components/SeekBarColored.kt`
 * 32-167): the [app.n_zik.compagnon.enums.PlayerTimelineType.ColoredBar] timeline — the bar and its
 * scrubber are tinted by quintile of the playback position (text, background0, textDisabled,
 * background2, accent). The colors are inverted versus the other bars: the full-width bar is
 * [barColor] and the played part is [backgroundColor]. Dropped: the try / catch of a detached
 * Android view.
 */
@Composable
fun SeekBarColored(
    value: Long,
    minimumValue: Long,
    maximumValue: Long,
    onDragStart: (Long) -> Unit,
    onDrag: (Long) -> Unit,
    onDragEnd: () -> Unit,
    color: Color,
    backgroundColor: Color,
    modifier: Modifier = Modifier,
    barHeight: Dp = 3.dp,
    scrubberColor: Color = color,
    scrubberRadius: Dp = 6.dp,
    shape: Shape = RectangleShape,
) {
    val isDragging = remember {
        MutableTransitionState(false)
    }

    val transition = rememberTransition(transitionState = isDragging, label = null)

    val currentBarHeight by transition.animateDp(label = "") { if (it) scrubberRadius else barHeight }
    val currentScrubberRadius by transition.animateDp(label = "") { if (it) 0.dp else scrubberRadius }

    val barColor = when {
        value >= 0 && value <= maximumValue / 5 -> colorPalette().text
        value >= maximumValue / 5 && value <= maximumValue / 4 -> colorPalette().background0
        value >= maximumValue / 4 && value <= maximumValue / 3 -> colorPalette().textDisabled
        value >= maximumValue / 3 && value <= maximumValue / 2 -> colorPalette().background2
        value >= maximumValue / 2 && value <= maximumValue -> colorPalette().accent
        else -> colorPalette().text
    }

    Box(
        modifier = modifier
            .pointerInput(minimumValue, maximumValue) {
                if (maximumValue < minimumValue) return@pointerInput

                var acc = 0f

                detectHorizontalDragGestures(
                    onDragStart = {
                        isDragging.targetState = true
                    },
                    onHorizontalDrag = { _, delta ->
                        acc += delta / size.width * (maximumValue - minimumValue)

                        if (acc !in -1f..1f) {
                            onDrag(acc.toLong())
                            acc -= acc.toLong()
                        }
                    },
                    onDragEnd = {
                        isDragging.targetState = false
                        acc = 0f
                        onDragEnd()
                    },
                    onDragCancel = {
                        isDragging.targetState = false
                        acc = 0f
                        onDragEnd()
                    },
                )
            }
            .pointerInput(minimumValue, maximumValue) {
                if (maximumValue < minimumValue) return@pointerInput

                detectTapGestures(
                    onPress = { offset ->
                        onDragStart((offset.x / size.width * (maximumValue - minimumValue) + minimumValue).roundToLong())
                    },
                    onTap = {
                        onDragEnd()
                    },
                )
            }
            .padding(horizontal = scrubberRadius)
            .drawWithContent {
                drawContent()

                val scrubberPosition = if (maximumValue < minimumValue) {
                    0f
                } else {
                    (value.toFloat() - minimumValue) / (maximumValue - minimumValue) * size.width
                }

                drawCircle(
                    color = barColor,
                    radius = currentScrubberRadius.toPx(),
                    center = center.copy(x = scrubberPosition),
                )
            }
            .height(scrubberRadius)
    ) {

        Spacer(
            modifier = Modifier
                .height(currentBarHeight)
                .fillMaxWidth()
                .background(color = barColor, shape = shape)
                .align(Alignment.Center),
        )

        Spacer(
            modifier = Modifier
                .height(currentBarHeight)
                .fillMaxWidth((value.toFloat() - minimumValue) / (maximumValue - minimumValue))
                .background(color = backgroundColor, shape = shape)
                .align(Alignment.CenterStart),
        )
    }
}
