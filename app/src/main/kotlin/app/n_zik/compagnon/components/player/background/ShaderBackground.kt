package app.n_zik.compagnon.components.player.background

import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned

/**
 * Port of `com.mikepenz.hypnoticcanvas` 0.4.1's `Modifier.shaderBackground` (the phone pins the library
 * in its `libs.versions.toml`): the [shader] drawn as the background. The phone's `appRunningInBackground`
 * guard (its `shaderCondition`) is always true on the PC — the desktop does not background its shaders.
 * (HypnoticCanvas, MIT.)
 */
@Composable
fun Modifier.shaderBackground(
    shader: Shader,
    speed: Float = 1f,
    fallback: () -> Brush = {
        Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
    },
): Modifier {
    val runtimeEffect = remember(shader) { buildEffect(shader) }
    var size: Size by remember { mutableStateOf(Size(-1f, -1f)) }
    val speedModifier = shader.speedModifier

    val time by if (runtimeEffect.supported) {
        var startMillis = remember(shader) { -1L }
        produceState(0f, speedModifier) {
            while (true) {
                withInfiniteAnimationFrameMillis {
                    if (startMillis < 0) startMillis = it
                    value = ((it - startMillis) / 16.6f) / 10f
                }
            }
        }
    } else {
        mutableStateOf(-1f)
    }

    return this then Modifier.onGloballyPositioned {
        size = Size(it.size.width.toFloat(), it.size.height.toFloat())
    }.drawBehind {
        runtimeEffect.update(shader, (time * speed * speedModifier).roundDecimals(3), size.width, size.height) // set uniforms for the shaders
        if (runtimeEffect.ready) {
            drawRect(brush = runtimeEffect.build())
        } else {
            drawRect(brush = fallback())
        }
    }
}
