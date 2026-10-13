package app.n_zik.compagnon.components.player.timeline

import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
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
import kotlin.math.roundToLong

/**
 * Port of `SeekBarThin` (phone's `app/it/fast4x/rimusic/ui/components/SeekBar.kt` 176-306): the
 * [app.n_zik.compagnon.enums.PlayerTimelineType.ThinBar] timeline — a plain 4 dp bar with no
 * scrubber circle; the `scrubberColor` / `scrubberRadius` parameters size the optional step dots
 * from the current value to the end (the phone's `drawSteps`). Dropped: the try / catch of a
 * detached Android view.
 */
@Composable
fun SeekBarThin(
    value: Long,
    minimumValue: Long,
    maximumValue: Long,
    onDragStart: (Long) -> Unit,
    onDrag: (Long) -> Unit,
    onDragEnd: () -> Unit,
    color: Color,
    backgroundColor: Color,
    modifier: Modifier = Modifier,
    barHeight: Dp = 4.dp,
    scrubberColor: Color = color,
    scrubberRadius: Dp = 6.dp,
    shape: Shape = RectangleShape,
    drawSteps: Boolean = false,
) {
    // The phone keeps this drag state on SeekBarThin although it drives no animation there —
    // kept for parity with the phone component.
    val isDragging = remember {
        MutableTransitionState(false)
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

                if (drawSteps) {
                    for (i in value + 1..maximumValue) {
                        val stepPosition =
                            (i.toFloat() - minimumValue) / (maximumValue - minimumValue) * size.width
                        drawCircle(
                            color = scrubberColor,
                            radius = scrubberRadius.toPx() / 2,
                            center = center.copy(x = stepPosition),
                        )
                    }
                }
            }
            .height(scrubberRadius)
    ) {

        Spacer(
            modifier = Modifier
                .height(barHeight)
                .fillMaxWidth()
                .background(color = backgroundColor, shape = shape)
                .align(Alignment.Center),
        )

        Spacer(
            modifier = Modifier
                .height(barHeight)
                .fillMaxWidth((value.toFloat() - minimumValue) / (maximumValue - minimumValue))
                .background(color = color, shape = shape)
                .align(Alignment.CenterStart),
        )
    }
}
