package app.n_zik.compagnon.components.player

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

/**
 * Port of `SeekBarWaved` (phone's `app/it/fast4x/rimusic/ui/components/SeekBarWaved.kt` 47), the default
 * player timeline: a sine wave over the played part (2 dp of amplitude while playing, flat while dragging or
 * paused), a 10 px wide pill scrubber growing from 15 to 20 dp while dragged. PC: the try / catch of a
 * detached Android view are dropped, and an empty duration draws an empty bar instead of failing.
 * PC: the wave stroke, sine and sampling step are expressed in dp (`WAVE_STROKE`, `WAVE_LENGTH`,
 * `WAVE_PATH_STEP`) rather than the phone's hardcoded physical px, so the port keeps the phone's
 * proportions at any desktop scale.
 */
@Composable
fun SeekBarWaved(
    position: () -> Float,
    onSeek: (updated: Float) -> Unit,
    modifier: Modifier = Modifier,
    onSeekStarted: (updated: Float) -> Unit = {},
    onSeekFinished: () -> Unit = {},
    color: Color,
    backgroundColor: Color = Color.Transparent,
    range: ClosedRange<Float> = 0f..100f,
    isActive: Boolean = true,
    scrubberRadius: Dp = 6.dp,
    shape: Shape = RectangleShape,
) {
    val minimumValue = range.start
    val maximumValue = range.endInclusive
    val isDragging = remember {
        MutableTransitionState(false)
    }

    val transition = rememberTransition(transitionState = isDragging, label = null)

    val currentAmplitude by transition.animateDp(label = "") { if (it || !isActive) 0.dp else 2.dp }
    val currentScrubberHeight by transition.animateDp(label = "") {
        if (it) 20.dp else 15.dp
    }

    Box(
        modifier = modifier
            .pointerInput(minimumValue, maximumValue) {
                if (maximumValue < minimumValue) return@pointerInput

                detectDrags(isDragging, maximumValue, minimumValue, onSeek, onSeekFinished)
            }
            .pointerInput(minimumValue, maximumValue) {
                detectTaps(maximumValue, minimumValue, onSeekStarted, onSeekFinished)
            }
            .padding(horizontal = scrubberRadius)
            .drawWithContent {
                drawContent()
                drawScrubber(range, position(), color, currentScrubberHeight)
            },
    ) {
        SeekBarContent(
            backgroundColor,
            amplitude = { currentAmplitude },
            position(),
            minimumValue,
            maximumValue,
            shape,
            color,
        )
    }
}

private suspend fun PointerInputScope.detectDrags(
    isDragging: MutableTransitionState<Boolean>,
    maximumValue: Float,
    minimumValue: Float,
    onSeek: (delta: Float) -> Unit,
    onSeekFinished: () -> Unit,
) {
    var acc = 0f

    detectHorizontalDragGestures(onDragStart = {
        isDragging.targetState = true
    }, onHorizontalDrag = { _, delta ->
        acc += delta / size.width * (maximumValue - minimumValue)

        if (acc !in -1f..1f) {
            onSeek(acc)
            acc -= acc
        }
    }, onDragEnd = {
        isDragging.targetState = false
        acc = 0f
        onSeekFinished()
    }, onDragCancel = {
        isDragging.targetState = false
        acc = 0f
        onSeekFinished()
    })
}

private suspend fun PointerInputScope.detectTaps(
    maximumValue: Float,
    minimumValue: Float,
    onSeekStarted: (updated: Float) -> Unit,
    onSeekFinished: () -> Unit,
) {
    if (maximumValue < minimumValue) return

    detectTapGestures(onPress = { offset ->
        val updatedOffset = (offset.x / size.width * (maximumValue - minimumValue) + minimumValue)
        onSeekStarted(updatedOffset)
    }, onTap = {
        onSeekFinished()
    })
}

private fun ContentDrawScope.drawScrubber(
    range: ClosedRange<Float>, position: Float, color: Color, height: Dp,
) {
    val minimumValue = range.start
    val maximumValue = range.endInclusive
    val scrubberPosition = if (maximumValue < minimumValue) {
        0f
    } else {
        (position - minimumValue) / (maximumValue - minimumValue) * size.width
    }

    drawRoundRect(
        color, topLeft = Offset(scrubberPosition - 5f, (size.height - height.toPx()) / 2),
        size = Size(10f, height.toPx()),
        cornerRadius = CornerRadius(5f),
    )
}

@Composable
private fun SeekBarContent(
    backgroundColor: Color,
    amplitude: () -> Dp,
    position: Float,
    minimumValue: Float,
    maximumValue: Float,
    shape: Shape,
    color: Color,
) {
    val fraction = ((position - minimumValue) / (maximumValue - minimumValue)).let { if (it.isNaN()) 0f else it.coerceIn(0f, 1f) }
    val progress by rememberInfiniteTransition().animateFloat(
        0f,
        1f,
        animationSpec = infiniteRepeatable(
            tween(2000, easing = LinearEasing),
        ), label = "",
    )
    Box(
        Modifier
            .fillMaxWidth()
            .height(6.dp),
    ) {
        Spacer(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(1f - fraction)
                .background(color = backgroundColor, shape = shape)
                .align(Alignment.CenterEnd),
        )

        Canvas(
            Modifier
                .fillMaxWidth(fraction)
                .height(amplitude())
                .align(Alignment.CenterStart),
        ) {
            clipRect(
                left = -50f,
                right = size.width,
                top = -50f,
                bottom = size.height + 50f,
            ) {
                drawPath(
                    wavePath(
                        size.copy(height = size.height * 2),
                        progress,
                        WAVE_LENGTH.toPx(),
                        WAVE_PATH_STEP.toPx(),
                    ),
                    color,
                    style = Stroke(
                        width = WAVE_STROKE.toPx(),
                        cap = StrokeCap.Round,
                    ),
                )
            }
        }
    }
}

/** The wave stroke width, in dp — the phone hardcodes 15 physical px, which is ~5 dp at its ~3× density. */
internal val WAVE_STROKE = 5.dp

/** The wave's wavelength, in dp (period = 2π × this value) — the phone hardcodes 15 physical px, which is ~5 dp at its ~3× density. */
internal val WAVE_LENGTH = 5.dp

/** The sampling step of [wavePath], in dp — the phone hardcodes 3 physical px, which is 1 dp at its ~3× density. */
internal val WAVE_PATH_STEP = 1.dp

/** The x coordinates [wavePath] samples: from 0, by [step] (px), while strictly less than [width]. */
internal fun waveSampleXs(width: Float, step: Float): List<Float> {
    val xs = mutableListOf<Float>()
    var currentX = 0f
    while (currentX < width) {
        xs += currentX
        currentX += step
    }
    return xs
}

/**
 * The wave y for an x (px): a sine of wavelength [lengthPx] (px) shifted by [progress], scaled to
 * [heightPx]. [lengthPx] must be positive (a zero-density `toPx()` would make the whole path NaN).
 */
internal fun waveYFromX(x: Float, lengthPx: Float, progress: Float, heightPx: Float): Float =
    (sin(x / lengthPx + progress * 2 * PI.toFloat()) + 1) * heightPx / 2

private fun wavePath(size: Size, progress: Float, wavelength: Float, stepPx: Float): Path {
    return Path().apply {
        moveTo(0f, waveYFromX(0f, wavelength, progress, size.height))
        for (currentX in waveSampleXs(size.width, stepPx)) {
            lineTo(currentX, waveYFromX(currentX, wavelength, progress, size.height))
        }
    }
}
