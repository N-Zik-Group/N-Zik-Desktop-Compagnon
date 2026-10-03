package app.n_zik.compagnon.components.ui.sliders

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import app.n_zik.compagnon.colorPalette
import app.n_zik.compagnon.exactUiRoundnessShape
import kotlin.math.abs
import kotlin.math.round
import kotlin.math.roundToInt

/**
 * Port of the phone's unique `Slider` (`app/n_zik/android/components/ui/sliders/Slider.kt`): ticks from the
 * step size, magnetism to the nearest step and to [defaultValue], a thumb that turns into a short bar while
 * held, a 24 dp rounded track. The phone drives Material's `SliderState` itself
 * (`rememberControlledSliderState`); here Material's value-based `Slider` gets the same animated value,
 * which draws the same.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Slider(
    isEnabled: Boolean = true,
    state: Float,
    setState: (Float) -> Unit,
    onSlideComplete: () -> Unit = {},
    range: ClosedFloatingPointRange<Float> = 0f..100f,
    stepSize: Float = 0f,
    defaultValue: Float? = null,
    drawValuePoints: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isDragged by interactionSource.collectIsDraggedAsState()
    val isPressed by interactionSource.collectIsPressedAsState()
    val isInteracting = isDragged || isPressed

    val thumbWidth by animateDpAsState(
        targetValue = if (isInteracting) 12.dp else 4.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow,
        ),
    )

    val thumbHeight by animateDpAsState(
        targetValue = if (isInteracting) 24.dp else 32.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow,
        ),
    )

    val stepCount = if (stepSize > 0f) {
        ((range.endInclusive - range.start) / stepSize).roundToInt() - 1
    } else 0
    val actualSteps = if (stepCount > 0) stepCount else 0

    val rangeSize = range.endInclusive - range.start
    val magneticThreshold = if (actualSteps > 0) {
        minOf(rangeSize * 0.02f, stepSize * 0.15f)
    } else 0f

    val animatedValue by animateFloatAsState(
        targetValue = state,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow,
        ),
    )

    fun normalize(value: Float): Float {
        if (stepSize <= 0f) return value.coerceIn(range.start, range.endInclusive)
        val fraction = (value - range.start) / stepSize
        return (range.start + round(fraction) * stepSize)
            .coerceIn(range.start, range.endInclusive)
    }

    androidx.compose.material3.Slider(
        value = animatedValue.coerceIn(range.start, range.endInclusive),
        enabled = isEnabled,
        valueRange = range,
        onValueChange = { newValue ->
            val normalized = normalize(newValue)
            var finalValue = if (abs(newValue - normalized) <= magneticThreshold) normalized else newValue

            if (defaultValue != null) {
                val defaultThreshold = rangeSize * 0.02f
                if (abs(newValue - defaultValue) <= defaultThreshold) {
                    finalValue = defaultValue
                }
            }
            if (isInteracting) {
                setState(finalValue)
            }
        },
        onValueChangeFinished = {
            onSlideComplete()
        },
        modifier = modifier,
        interactionSource = interactionSource,
        thumb = {
            Box(
                modifier = Modifier
                    .size(width = thumbWidth, height = thumbHeight)
                    .background(
                        color = if (isEnabled) colorPalette().onAccent else colorPalette().text.copy(alpha = 0.4f),
                        shape = exactUiRoundnessShape(),
                    ),
            )
        },
        track = {
            Box(contentAlignment = Alignment.CenterStart) {
                val fraction = if (range.endInclusive > range.start) {
                    ((state - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)
                } else 0f

                // Track Container
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(24.dp)
                        .clip(exactUiRoundnessShape())
                        .background(if (isEnabled) colorPalette().text.copy(alpha = 0.2f) else colorPalette().text.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    // Active Track
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction)
                            .height(24.dp)
                            .clip(exactUiRoundnessShape())
                            .background(if (isEnabled) colorPalette().accent else colorPalette().text.copy(alpha = 0.4f)),
                    )
                }

                if (actualSteps > 0) {
                    val accentColor = colorPalette().accent
                    val passedColor = colorPalette().text.copy(alpha = 0.5f)

                    Canvas(modifier = Modifier.fillMaxWidth().height(4.dp)) {
                        val currentFraction = if (range.endInclusive > range.start) {
                            (state - range.start) / (range.endInclusive - range.start)
                        } else 0f

                        val trackWidth = size.width
                        val numIntervals = actualSteps + 1

                        val trackStart = 0f
                        val activeWidth = trackWidth

                        var drawStep = 1
                        if (numIntervals > 10) {
                            var bestDivisor = -1
                            for (step in 2..numIntervals) {
                                val count = numIntervals / step
                                if (numIntervals % step == 0 && count in 4..10) {
                                    bestDivisor = step
                                    break
                                }
                            }
                            drawStep = if (bestDivisor != -1) bestDivisor else (numIntervals / 8.0).roundToInt().coerceAtLeast(1)
                        }

                        for (i in 0..numIntervals) {
                            if (i % drawStep == 0 || i == numIntervals) {
                                val fractionAt = i.toFloat() / numIntervals
                                val x = trackStart + (fractionAt * activeWidth)
                                val isPassed = fractionAt <= currentFraction
                                drawCircle(
                                    color = if (isPassed) passedColor else accentColor,
                                    radius = 2.dp.toPx(),
                                    center = Offset(x = x, y = size.height / 2),
                                )
                            }
                        }
                    }
                } else if (drawValuePoints) {
                    val accentColor = colorPalette().accent
                    val passedColor = colorPalette().text.copy(alpha = 0.5f)

                    Canvas(modifier = Modifier.fillMaxWidth().height(4.dp)) {
                        val currentFraction = if (range.endInclusive > range.start) {
                            (state - range.start) / (range.endInclusive - range.start)
                        } else 0f

                        val trackWidth = size.width
                        val trackStart = 0f
                        val activeWidth = trackWidth

                        val pointsToDraw = mutableSetOf<Float>()
                        if (defaultValue != null) {
                            val defaultFraction = ((defaultValue - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)
                            pointsToDraw.add(defaultFraction)
                        }

                        pointsToDraw.forEach { fractionAt ->
                            val x = trackStart + (fractionAt * activeWidth)
                            val isPassed = fractionAt <= currentFraction
                            drawCircle(
                                color = if (isPassed) passedColor else accentColor,
                                radius = 2.dp.toPx(),
                                center = Offset(x = x, y = size.height / 2),
                            )
                        }
                    }
                }
            }
        },
    )
}
