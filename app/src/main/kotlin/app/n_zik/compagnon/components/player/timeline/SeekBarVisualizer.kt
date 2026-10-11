package app.n_zik.compagnon.components.player.timeline

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import kotlin.math.abs
import kotlin.math.absoluteValue
import kotlin.math.roundToInt
import kotlin.random.Random

private const val waveWidthPercentOfSpaceAvailable = 0.5f

private const val minWaveHeightFraction = 0.05f
private const val maxWaveHeightFractionForSideWaves = 0.1f
private const val maxWaveHeightFraction = 0.85f

/**
 * Port of `ProgressPercentage` (phone's `SeekBarVisualizer.kt` 355-384): a clamped 0-1 playback
 * progress. Dropped: the AndroidX `@FloatRange` lint annotation (no runtime effect).
 */
@JvmInline
value class ProgressPercentage(
    val value: Float,
) {
    init {
        require(value in 0.0f..1.0f) {
            "Progress percentage must be within 0.0f inclusive to 1.0f inclusive. Value: $value"
        }
    }

    val isDone: Boolean
        get() = value == 1f

    companion object {
        fun safeValue(float: Float): ProgressPercentage {
            if (float.isNaN()) return ProgressPercentage(0f)
            return ProgressPercentage(float.coerceIn(0f, 1f))
        }

        fun of(
            current: Dp,
            target: Dp,
        ): ProgressPercentage {
            return ProgressPercentage(
                (current / target).coerceIn(0f, 1f),
            )
        }
    }
}

/** Port of `WaveInteraction` (phone's `SeekBarVisualizer.kt` 386-388). */
fun interface WaveInteraction {
    fun onInteraction(horizontalProgressPercentage: ProgressPercentage)
}

/**
 * Port of `SeekBarVisualizer` (phone's
 * `app/n_zik/android/extensions/audiobar/views/SeekBarVisualizer.kt` 65-286) — the
 * [app.n_zik.compagnon.enums.PlayerTimelineType.VisualizerBar] timeline: pill waves across the
 * width, played pills in the accent, a 2 dp thumb while paused, tap / drag seeking. The phone
 * drives the pill heights from a live mic FFT snapshot when the permission is granted; the PC
 * renders only the static branch (the spec allows no real visualizer, FFT or microphone): each
 * pill has one random height per recomposition identity, same envelope as the phone's
 * no-waveform fallback. Dropped: the audio session provider, the microphone permission
 * request / text, the FFT snapshot loop and its decay-to-flat loop.
 */
@Composable
fun SeekBarVisualizer(
    isPlaying: Boolean,
    progressPercentage: () -> ProgressPercentage,
    playedColor: Color,
    notPlayedColor: Color,
    waveInteraction: WaveInteraction,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier) {
        val maxWidth = this.maxWidth
        val updatedWaveInteraction by rememberUpdatedState(waveInteraction)

        val numberOfWaves = remember(maxWidth) {
            (maxWidth / 3f).value.roundToInt() //5f default
        }
        val waveWidth = remember(maxWidth) {
            (maxWidth / numberOfWaves.toFloat()) * waveWidthPercentOfSpaceAvailable
        }

        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(maxHeight)
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        updatedWaveInteraction.onInteraction(
                            ProgressPercentage.of(current = offset.x.toDp(), target = maxWidth),
                        )
                    }
                }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures { change: PointerInputChange, dragAmount: Float ->
                        // Do not trigger on minuscule movements
                        if (dragAmount.absoluteValue < 1f) return@detectHorizontalDragGestures
                        updatedWaveInteraction.onInteraction(
                            ProgressPercentage.of(
                                current = change.position.x.toDp(),
                                target = maxWidth,
                            ),
                        )
                    }
                }
        ) {
            repeat(numberOfWaves) { waveIndex ->
                AudioWavePill(
                    progressPercentage = progressPercentage,
                    numberOfWaves = numberOfWaves,
                    waveIndex = waveIndex,
                    playedColor = playedColor,
                    notPlayedColor = notPlayedColor,
                    modifier = Modifier.width(waveWidth),
                )
            }
        }

        if (!isPlaying) {
            val thumbWidth = 2.dp
            val offsetDp = (maxWidth * progressPercentage().value) - (thumbWidth / 2)
            val maxOffset = if (maxWidth > thumbWidth) maxWidth - thumbWidth else 0.dp
            val safeOffset = offsetDp.coerceIn(0.dp, maxOffset)

            Surface(
                shape = CircleShape,
                color = playedColor,
                modifier = Modifier
                    .offset(x = safeOffset)
                    .width(thumbWidth)
                    .fillMaxHeight(),
            ) {}
        }
    }
}

/**
 * Port of the phone's `AudioWavePill` (same file, 292-353) in its static-only branch: one random
 * height per pill (the phone's no-waveform fallback), snapped up instantly and animated down
 * linearly over 90 ms, filled while the progress has passed it.
 */
@Composable
private fun AudioWavePill(
    progressPercentage: () -> ProgressPercentage,
    numberOfWaves: Int,
    waveIndex: Int,
    playedColor: Color,
    notPlayedColor: Color,
    modifier: Modifier = Modifier,
) {
    val height = remember(waveIndex, numberOfWaves) {
        val wavePosition = waveIndex + 1
        val centerPoint = numberOfWaves / 2
        val distanceFromCenterPoint = abs(centerPoint - wavePosition)
        val percentageToCenterPoint = ((centerPoint - distanceFromCenterPoint).toFloat() / centerPoint)
        val maxHeightFraction = lerp(
            maxWaveHeightFractionForSideWaves,
            maxWaveHeightFraction,
            percentageToCenterPoint,
        )
        val validMaxHeightFraction = if (maxHeightFraction.isNaN()) 0.1f else maxHeightFraction
        if (validMaxHeightFraction <= minWaveHeightFraction) {
            validMaxHeightFraction
        } else {
            Random.nextDouble(minWaveHeightFraction.toDouble(), validMaxHeightFraction.toDouble()).toFloat()
        }
    }

    val animatedHeight = remember { Animatable(minWaveHeightFraction) }

    LaunchedEffect(height) {
        if (height >= animatedHeight.value) {
            animatedHeight.snapTo(height)
        } else {
            animatedHeight.animateTo(
                targetValue = height,
                animationSpec = tween(
                    durationMillis = 90,
                    easing = LinearEasing,
                ),
            )
        }
    }

    val currentProgressPercentage by rememberUpdatedState(progressPercentage)

    val hasPlayedThisWave by remember {
        derivedStateOf { currentProgressPercentage().value * numberOfWaves > waveIndex }
    }
    Surface(
        shape = CircleShape,
        color = if (hasPlayedThisWave) playedColor else notPlayedColor,
        modifier = modifier.fillMaxHeight(fraction = animatedHeight.value),
    ) {}
}
