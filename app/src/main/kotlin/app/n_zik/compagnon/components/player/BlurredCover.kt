package app.n_zik.compagnon.components.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import app.n_zik.compagnon.core.coil.ImageCacheFactory
import app.n_zik.compagnon.core.network.ArtworkKey
import app.n_zik.compagnon.generated.resources.*
import kotlin.math.sqrt
import org.jetbrains.compose.resources.stringResource

/** The phone's default blur strength (`BlurAdjuster`: `blurStrengthKey`, 25). */
private const val BLUR_STRENGTH = 25f

/** The phone's default backdrop (`BlurAdjuster`: `playerBackdropKey`, 0 %). */
private const val BACKDROP_PERCENT = 0f

/**
 * Port of `BlurredCover` (phone's `app/kreate/android/screens/player/background/BlurredCover.kt` 32-129) with
 * the default `BlurAdjuster` (strength 25, backdrop 0 %, no rotating cover) and the cover shown: the current
 * cover, fitted in a square of the screen's longest side, scaled to its diagonal, blurred by 25 dp, then the
 * black backdrop (transparent at 0 %). Drawn under the player's background (`Player.kt` 1418 and 1967).
 */
@Composable
fun BlurredCover(
    key: ArtworkKey?,
    contentScale: ContentScale,
    modifier: Modifier = Modifier,
) {
    BlurFilter(key, contentScale, modifier)
    Backdrop(modifier)
}

@Composable
private fun BlurFilter(
    key: ArtworkKey?,
    @Suppress("UNUSED_PARAMETER") contentScale: ContentScale,
    modifier: Modifier = Modifier,
) = BoxWithConstraints(
    modifier = Modifier.fillMaxSize(),
    contentAlignment = Alignment.Center,
) {
    val size = remember(maxWidth, maxHeight) {
        maxOf(maxWidth, maxHeight)
    }
    val scale = with(LocalDensity.current) {
        val w = maxWidth.toPx()
        val h = maxHeight.toPx()
        val s = size.toPx()

        sqrt(w * w + h * h) / s
    }
    Box(Modifier.requiredSize(size)) {
        // showThumbnail (the default): the adjuster's strength
        val blurRadius = BLUR_STRENGTH
        ImageCacheFactory.Thumbnail(
            key = key,
            contentDescription = stringResource(Res.string.cd_blurred_background),
            // The phone passes ContentScale.Fit here whatever the caller asks
            contentScale = ContentScale.Fit,
            modifier = modifier
                .fillMaxSize()
                .then(if (blurRadius > 0f) Modifier.blur(blurRadius.dp) else Modifier)
                .then(
                    if (scale != 1f) {
                        Modifier.graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        }
                    } else {
                        Modifier
                    },
                ),
        )
    }
}

@Composable
private fun Backdrop(modifier: Modifier = Modifier) {
    val backdropColor = Color.Black.copy(alpha = (BACKDROP_PERCENT / 100f).coerceIn(0f, 1f))
    Box(modifier.fillMaxSize().background(backdropColor))
}
