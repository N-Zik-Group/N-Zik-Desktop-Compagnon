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
 * Port of `MusicAnimation` (phone's `app/it/fast4x/rimusic/ui/components/MusicAnimations.kt` 41) with its
 * default indicator, `MusicAnimationType.Bubbles`. [isPlaying] comes from the phone's state (WS) instead
 * of a local player listener.
 */
@Composable
fun MusicAnimation(
    color: Color,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    barWidth: Dp = 6.dp,
) {
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
                    drawCircle(
                        color = color,
                        radius = animatable.value * (size.height / 3),
                    )
                }
            }
        }
    }
}
