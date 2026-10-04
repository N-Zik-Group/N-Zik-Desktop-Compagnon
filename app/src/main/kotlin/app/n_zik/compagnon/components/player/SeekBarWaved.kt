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
import app.n_zik.compagnon.utils.PHONE_REFERENCE_DENSITY
import app.n_zik.compagnon.utils.phonePx
import kotlin.math.PI
import kotlin.math.sin

/**
 * Port of `SeekBarWaved` (phone's `app/it/fast4x/rimusic/ui/components/SeekBarWaved.kt` 47), the default
 * player timeline: a sine wave over the played part (2 dp of amplitude while playing, flat while dragging or
 * paused), a 10 px wide pill scrubber growing from 15 to 20 dp while dragged. PC: the try / catch of a
 * detached Android view are dropped, and an empty duration draws an empty bar instead of failing.
 * The phone's hardcoded physical px (`SeekBarWaved.kt` 175-179 scrubber, 218-222 clip, 228 stroke, 241 step,
 * 261 wavelength) are converted with [PHONE_REFERENCE_DENSITY] (story 11c): stroke 15 px → 5 dp, wavelength
 * 15 px → 5 dp, step 3 px → 1 dp, scrubber 10 px → 3.33 dp wide with a 5 px → 1.67 dp radius and offset,
 * clip margin 50 px → 16.67 dp.
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
        color, topLeft = Offset(scrubberPosition - SCRUBBER_HALF_WIDTH.toPx(), (size.height - height.toPx()) / 2),
        size = Size(SCRUBBER_WIDTH.toPx(), height.toPx()),
        cornerRadius = CornerRadius(SCRUBBER_HALF_WIDTH.toPx()),
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
                left = -WAVE_CLIP_MARGIN.toPx(),
                right = size.width,
                top = -WAVE_CLIP_MARGIN.toPx(),
                bottom = size.height + WAVE_CLIP_MARGIN.toPx(),
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

/** The wave stroke width: the phone's 15 px (`SeekBarWaved.kt` 228). */
internal val WAVE_STROKE = phonePx(15f)

/** The wave's wavelength (period = 2π × this value): the phone's `x / 15f` (`SeekBarWaved.kt` 261). */
internal val WAVE_LENGTH = phonePx(15f)

/** The sampling step of [wavePath]: the phone's `WAVE_PATH_STEP_PX = 3f` (`SeekBarWaved.kt` 241). */
internal val WAVE_PATH_STEP = phonePx(3f)

/** The margin of the wave's clip: the phone's 50 px (`SeekBarWaved.kt` 218-222). */
internal val WAVE_CLIP_MARGIN = phonePx(50f)

/** The scrubber's width: the phone's 10 px (`SeekBarWaved.kt` 177). */
internal val SCRUBBER_WIDTH = phonePx(10f)

/** The scrubber's corner radius and its offset from the position: the phone's 5 px (`SeekBarWaved.kt` 176-178). */
internal val SCRUBBER_HALF_WIDTH = phonePx(5f)

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
