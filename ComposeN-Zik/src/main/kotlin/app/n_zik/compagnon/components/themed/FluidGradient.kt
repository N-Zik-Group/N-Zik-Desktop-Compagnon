package app.n_zik.compagnon.components.themed

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.LinearGradientShader
import androidx.compose.ui.graphics.ShaderBrush
import kotlin.math.cos
import kotlin.math.sin

/**
 * Port of `animateBrushRotation` (phone's `app/it/fast4x/rimusic/ui/components/themed/FluidGradient.kt` 109):
 * a linear gradient turning around the centre of [size] in [duration] ms. The phone rotates the shader's
 * local matrix (`Shader.setLocalMatrix`, Android only); rotating a linear gradient's matrix is the same as
 * rotating its two end points, which is what this does with the [from] / [to] points of the phone's
 * `LinearGradientShader`.
 */
@Composable
fun animateBrushRotation(
    from: Offset,
    to: Offset,
    colors: List<Color>,
    colorStops: List<Float>,
    size: Size,
    duration: Int,
    clockwise: Boolean,
): State<ShaderBrush> {
    val infiniteTransition = rememberInfiniteTransition()
    val angle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f * if (clockwise) 1f else -1f,
        animationSpec = infiniteRepeatable(
            animation = tween(duration, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "",
    )

    return remember(from, to, colors, colorStops, size) {
        derivedStateOf {
            val pivot = Offset(size.width / 2, size.height / 2)
            ShaderBrush(
                LinearGradientShader(
                    from = from.rotate(angle, pivot),
                    to = to.rotate(angle, pivot),
                    colors = colors,
                    colorStops = colorStops,
                ),
            )
        }
    }
}

private fun Offset.rotate(degrees: Float, pivot: Offset): Offset {
    val radians = Math.toRadians(degrees.toDouble())
    val cos = cos(radians).toFloat()
    val sin = sin(radians).toFloat()
    val dx = x - pivot.x
    val dy = y - pivot.y
    return Offset(pivot.x + dx * cos - dy * sin, pivot.y + dx * sin + dy * cos)
}
