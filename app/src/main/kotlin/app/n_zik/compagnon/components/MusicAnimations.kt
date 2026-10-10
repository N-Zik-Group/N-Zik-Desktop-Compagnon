package app.n_zik.compagnon.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Port of `MusicAnimation` (phone's `app/it/fast4x/rimusic/ui/components/MusicAnimations.kt` 41) with the
 * phone's `nowPlayingIndicator` (`MusicAnimationType`, served by `ui.settings` since 1.10.0: Bubbles —
 * its default —, Bars, CrazyBars, CrazyPoints; Disabled draws nothing). [isPlaying] is the contract's
 * `isPlaying` (the phone's `onIsPlayingChanged`: false while paused or buffering) instead of a local
 * player listener.
 */
@Composable
fun MusicAnimation(
    color: Color,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    barWidth: Dp = 6.dp,
    cornerRadius: Dp = 8.dp,
) {
    val indicator = app.n_zik.compagnon.bridge.state.LocalUiSettings.current.nowPlayingIndicator
    if (indicator == "Disabled") return
    val animatablesWithSteps = remember {
        listOf(
            Animatable(0f) to listOf(
                0.2f, 0.8f, 0.1f, 0.1f, 0.3f, 0.1f, 0.2f, 0.8f, 0.7f, 0.2f, 0.4f, 0.9f, 0.7f,
                0.6f, 0.1f, 0.3f, 0.1f, 0.4f, 0.1f, 0.8f, 0.7f, 0.9f, 0.5f, 0.6f, 0.3f, 0.1f,
            ),
            Animatable(0f) to listOf(
                0.2f, 0.5f, 1.0f, 0.5f, 0.3f, 0.1f, 0.2f, 0.3f, 0.5f, 0.1f, 0.6f, 0.5f, 0.3f,
                0.7f, 0.8f, 0.9f, 0.3f, 0.1f, 0.5f, 0.3f, 0.6f, 1.0f, 0.6f, 0.7f, 0.4f, 0.1f,
            ),
            Animatable(0f) to listOf(
                0.6f, 0.5f, 1.0f, 0.6f, 0.5f, 1.0f, 0.6f, 0.5f, 1.0f, 0.5f, 0.6f, 0.7f, 0.2f,
                0.3f, 0.1f, 0.5f, 0.4f, 0.6f, 0.7f, 0.1f, 0.4f, 0.3f, 0.1f, 0.4f, 0.3f, 0.7f,
            ),
        )
    }

    LaunchedEffect(Unit, isPlaying) {
        if (isPlaying) {
            animatablesWithSteps.forEach { (animatable, steps) ->
                launch {
                    while (true) {
                        steps.forEach { step ->
                            animatable.animateTo(step)
                        }
                    }
                }
            }
        }
    }

    AnimatedVisibility(isPlaying) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom,
            modifier = modifier,
        ) {
            animatablesWithSteps.forEach { (animatable) ->
                Canvas(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(barWidth),
                ) {
                    when (indicator) {
                        "Bars" -> drawRoundRect(
                            color = color,
                            topLeft = androidx.compose.ui.geometry.Offset(x = 0f, y = size.height * (1 - animatable.value)),
                            size = size.copy(height = animatable.value * size.height),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadius.toPx()),
                        )
                        "CrazyBars", "CrazyPoints" -> drawLine(
                            color = color,
                            start = androidx.compose.ui.geometry.Offset(x = 0f, y = animatable.value * (size.height / 2)),
                            end = androidx.compose.ui.geometry.Offset(
                                x = animatable.value * (size.height / 2),
                                y = if (indicator == "CrazyBars") size.height else animatable.value * (size.height / 2),
                            ),
                            strokeWidth = size.width,
                        )
                        // Bubbles, the phone's default (an unknown name too)
                        else -> drawCircle(
                            color = color,
                            radius = animatable.value * (size.height / 3),
                        )
                    }
                }
            }
        }
    }
}
