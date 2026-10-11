package app.n_zik.compagnon.utils

import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntSize
import app.n_zik.compagnon.colorPalette
import app.n_zik.compagnon.components.theme.shimmer

/**
 * Port of the phone's `shimmerEffect` (`app/it/fast4x/rimusic/utils/Modifier.kt` 25-56): a loading
 * effect that goes from top left to bottom right in 2000 ms — a diagonal [Brush.linearGradient]
 * (`background1` → `shimmer` at 30 % alpha → `background1`) translating with an animated offset, the
 * phone's `FastOutLinearInEasing` (the `initialValue` is captured before the first layout, while the
 * size is still zero: the first sweep starts at 0, as on the phone). The phone's radio-loading
 * placeholders wrap their column in the third-party `com.valentinilk.shimmer` effect (same diagonal
 * sweep); the PC reuses this port for it (no new dependency).
 */
fun Modifier.shimmer(): Modifier = composed {
    var size by remember { mutableStateOf(IntSize.Zero) }
    val transition = rememberInfiniteTransition("infiniteTransition")
    val startOffsetX by transition.animateFloat(
        initialValue = -2 * size.width.toFloat(),
        targetValue = 2 * size.width.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 2000,
                easing = FastOutLinearInEasing,
            ),
        ),
        label = "offsetXAnimatedTransition",
    )

    background(
        brush = Brush.linearGradient(
            colors = listOf(
                colorPalette().background1,
                colorPalette().shimmer.copy(alpha = .3f),
                colorPalette().background1,
            ),
            start = Offset(startOffsetX, 0f),
            end = Offset(startOffsetX + size.width.toFloat(), size.height.toFloat()),
        ),
    ).onGloballyPositioned {
        size = it.size
    }
}
