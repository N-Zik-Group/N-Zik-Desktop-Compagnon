package app.n_zik.compagnon.components.themed

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.star
import app.n_zik.compagnon.colorPalette
import app.n_zik.compagnon.components.styling.Dimensions
import kotlinx.coroutines.delay

/**
 * The shapes the Material 3 Expressive `LoadingIndicator` cycles through (`IndeterminateIndicatorPolygons`:
 * soft burst, 9-sided cookie, pentagon, pill, sunny, 4-sided cookie, oval), rebuilt with `graphics-shapes`
 * (the Compose Desktop Material 3 1.9 has no `LoadingIndicator`).
 */
private val LOADER_POLYGONS: List<RoundedPolygon> by lazy {
    listOf(
        RoundedPolygon.star(10, innerRadius = 0.75f, rounding = CornerRounding(0.4f)),
        RoundedPolygon.star(9, innerRadius = 0.8f, rounding = CornerRounding(0.5f)),
        RoundedPolygon(numVertices = 5, rounding = CornerRounding(0.3f)),
        RoundedPolygon(numVertices = 4, rounding = CornerRounding(1f)),
        RoundedPolygon.star(8, innerRadius = 0.8f, rounding = CornerRounding(0.15f)),
        RoundedPolygon.star(4, innerRadius = 0.6f, rounding = CornerRounding(0.5f)),
        RoundedPolygon(numVertices = 12, rounding = CornerRounding(1f)),
    ).map { it.normalized() }
}

/** The M3 `LoadingIndicator`'s morph period (650 ms) and its active shape (38 dp in a 48 dp container). */
private const val LOADER_MORPH_INTERVAL_MS = 650L
private const val LOADER_ACTIVE_RATIO = 38f / 48f

/**
 * Port of `Loader` (phone's `app/it/fast4x/rimusic/ui/components/themed/Loader.kt` 20-40): the expressive
 * loading indicator in `accent`, [size] 100 dp, centred, with the floating bar's bottom spacer under it
 * (`NavigationBarPosition.BottomFloating`, the default).
 */
@Composable
fun Loader(
    size: Dp = 100.dp,
    modifier: Modifier = Modifier.fillMaxWidth(),
) {
    val bottomPadding = Dimensions.bottomSpacer
    val color = colorPalette().accent

    var index by remember { mutableIntStateOf(0) }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(LOADER_MORPH_INTERVAL_MS)
            progress.snapTo(0f)
            progress.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 200f))
            index = (index + 1) % LOADER_POLYGONS.size
            progress.snapTo(0f)
        }
    }
    val rotation by rememberInfiniteTransition(label = "loader").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(4666, easing = LinearEasing), RepeatMode.Restart),
        label = "loaderRotation",
    )
    val morphs = remember {
        LOADER_POLYGONS.indices.map { Morph(LOADER_POLYGONS[it], LOADER_POLYGONS[(it + 1) % LOADER_POLYGONS.size]) }
    }
    val path = remember { Path() }

    Box(
        modifier = modifier.padding(bottom = bottomPadding),
    ) {
        Canvas(
            modifier = Modifier
                .align(Alignment.Center)
                .size(size),
        ) {
            val side = minOf(this.size.width, this.size.height) * LOADER_ACTIVE_RATIO
            val cubics = morphs[index].asCubics(progress.value)
            path.reset()
            var first = true
            for (cubic in cubics) {
                if (first) {
                    path.moveTo(cubic.anchor0X * side, cubic.anchor0Y * side)
                    first = false
                }
                path.cubicTo(
                    cubic.control0X * side, cubic.control0Y * side,
                    cubic.control1X * side, cubic.control1Y * side,
                    cubic.anchor1X * side, cubic.anchor1Y * side,
                )
            }
            path.close()
            rotate(rotation + progress.value * 90f) {
                translate((this.size.width - side) / 2f, (this.size.height - side) / 2f) {
                    drawPath(path, color)
                }
            }
        }
    }
}
