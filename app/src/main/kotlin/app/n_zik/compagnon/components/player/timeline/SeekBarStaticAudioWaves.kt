package app.n_zik.compagnon.components.player.timeline

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import app.n_zik.compagnon.colorPalette
import kotlin.math.abs
import kotlin.random.Random

/**
 * The deterministic fallback amplitudes (phone's `SeekBarStaticAudioWaves.kt` 122-150, the
 * `amplitudes == null` branch, re-seedable by track id): 150 bars with a lerp envelope from 10 %
 * at the edges to 85 % at the center, each bar 5 % plus a random share of the remaining envelope,
 * mapped to 5-100. The same media id always yields the same bars (the PC test pins that).
 */
internal fun audioWaveAmplitudes(mediaId: String): List<Int> {
    val seed = mediaId.hashCode().toLong()
    val random = Random(seed)

    return List(150) { i ->
        val wavePosition = i + 1
        val centerPoint = 150 / 2
        val distanceFromCenterPoint = abs(centerPoint - wavePosition)
        val percentageToCenterPoint = ((centerPoint - distanceFromCenterPoint).toFloat() / centerPoint)

        // Interpolate max height from 10% at edges to 85% at center
        val maxHeightFraction = lerp(
            0.1f,
            0.85f,
            percentageToCenterPoint,
        )

        val validMaxHeightFraction = if (maxHeightFraction.isNaN()) 0.1f else maxHeightFraction

        val amplitude = if (validMaxHeightFraction <= 0.05f) {
            0.05f
        } else {
            0.05f + random.nextFloat() * (validMaxHeightFraction - 0.05f)
        }

        // Map fraction to 0-100 amplitude
        (amplitude * 100f).toInt().coerceIn(5, 100)
    }
}

/**
 * Port of `SeekBarStaticAudioWaves` (phone's
 * `app/n_zik/android/extensions/audiobar/views/SeekBarStaticAudioWaves.kt`) — the
 * [app.n_zik.compagnon.enums.PlayerTimelineType.AudioWaves] timeline: 150 rounded bars across the
 * width, played bars in the accent, the rest faded, a 300 ms amplitude ramp on play / pause, and a
 * 2 dp position thumb while paused. The phone draws real extracted waveform amplitudes when it has
 * them and falls back to deterministic per-track bars otherwise; the PC renders only the fallback
 * branch (the spec allows no waveform computation on the PC — the bars are seeded by the track id
 * via [audioWaveAmplitudes]). The phone's audio session provider is dropped with it.
 */
@Composable
fun SeekBarStaticAudioWaves(
    modifier: Modifier = Modifier,
    mediaId: String,
    position: Long,
    duration: Long,
    isPlaying: Boolean,
    onPositionChange: (Long) -> Unit,
    onPositionChangeFinished: (() -> Unit)? = null,
    unplayedColor: Color = Color.Unspecified,
) {
    val displayAmplitudes = remember(mediaId) { audioWaveAmplitudes(mediaId) }

    val playedColor = colorPalette().accent
    val defaultUnplayedColor = if (unplayedColor != Color.Unspecified) unplayedColor else colorPalette().textSecondary.copy(alpha = 0.3f)

    if (displayAmplitudes.isNotEmpty()) {
        // Draw static waves
        val progress = if (duration > 0) position.toFloat() / duration.toFloat() else 0f

        val waveAmplitudeMultiplier by animateFloatAsState(
            targetValue = if (isPlaying) 1f else 0f,
            animationSpec = tween(durationMillis = 300),
        )

        val updatedOnPositionChange by rememberUpdatedState(onPositionChange)
        val updatedOnPositionChangeFinished by rememberUpdatedState(onPositionChangeFinished)

        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(30.dp)
                .pointerInput(duration) {
                    detectTapGestures(
                        onPress = { offset ->
                            val newPosition = (offset.x / size.width) * duration
                            updatedOnPositionChange(newPosition.toLong())
                        },
                        onTap = { offset ->
                            val newPosition = (offset.x / size.width) * duration
                            updatedOnPositionChange(newPosition.toLong())
                            updatedOnPositionChangeFinished?.invoke()
                        },
                    )
                }
                .pointerInput(duration) {
                    var dragPosition: Float = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            dragPosition = offset.x
                            val newPosition = (dragPosition / size.width) * duration
                            updatedOnPositionChange(newPosition.toLong())
                        },
                        onDragEnd = {
                            updatedOnPositionChangeFinished?.invoke()
                        },
                        onHorizontalDrag = { change: PointerInputChange, dragAmount: Float ->
                            change.consume()
                            dragPosition += dragAmount
                            dragPosition = dragPosition.coerceIn(0f, size.width.toFloat())
                            val newPosition = (dragPosition / size.width) * duration
                            updatedOnPositionChange(newPosition.toLong())
                        },
                    )
                }
        ) {
            Canvas(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
                val canvasWidth = size.width
                val canvasHeight = size.height
                if (canvasWidth <= 0f || canvasHeight <= 0f) return@Canvas

                val maxAmp = displayAmplitudes.maxOrNull()?.toFloat()?.coerceAtLeast(1f) ?: 1f

                val barCount = 150 // We draw 150 bars across the width
                val barWidth = canvasWidth / (barCount * 1.5f) // Leave space between bars
                val spacing = (canvasWidth - (barWidth * barCount)) / (barCount - 1).coerceAtLeast(1)

                // Group the amplitudes into 150 chunks
                val chunkSize = (displayAmplitudes.size.toFloat() / barCount).coerceAtLeast(1f)

                for (i in 0 until barCount) {
                    val startIdx = (i * chunkSize).toInt().coerceIn(0, displayAmplitudes.size - 1)
                    val endIdx = ((i + 1) * chunkSize).toInt().coerceIn(0, displayAmplitudes.size)

                    var chunkMax = 0
                    if (startIdx < endIdx) {
                        for (j in startIdx until endIdx) {
                            if (displayAmplitudes[j] > chunkMax) chunkMax = displayAmplitudes[j]
                        }
                    } else {
                        chunkMax = displayAmplitudes[startIdx]
                    }

                    // Normalize chunk amplitude between 0.1 and 1.0
                    val normalizedAmp = (chunkMax / maxAmp).coerceIn(0.1f, 1.0f)
                    val currentAmp = 0.05f + (normalizedAmp - 0.05f) * waveAmplitudeMultiplier
                    val barHeight = canvasHeight * currentAmp

                    val x = i * (barWidth + spacing)
                    val y = (canvasHeight - barHeight) / 2f

                    val isPlayed = (i.toFloat() / barCount) <= progress
                    val barColor = if (isPlayed) playedColor else defaultUnplayedColor

                    drawRoundRect(
                        color = barColor,
                        topLeft = Offset(x, y),
                        size = Size(barWidth, barHeight),
                        cornerRadius = CornerRadius(barWidth / 2, barWidth / 2),
                    )
                }

                if (!isPlaying) {
                    val thumbWidthPx = 2.dp.toPx()
                    val thumbX = (canvasWidth * progress) - (thumbWidthPx / 2f)
                    val maxThumbX = (canvasWidth - thumbWidthPx).coerceAtLeast(0f)
                    val safeThumbX = thumbX.coerceIn(0f, maxThumbX)
                    drawRoundRect(
                        color = playedColor,
                        topLeft = Offset(safeThumbX, 0f),
                        size = Size(thumbWidthPx, canvasHeight),
                        cornerRadius = CornerRadius(thumbWidthPx / 2, thumbWidthPx / 2),
                    )
                }
            }
        }
    }
}
