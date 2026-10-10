package app.n_zik.compagnon.components.player

import app.n_zik.compagnon.bridge.state.LocalUiSettings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
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


/**
 * Port of `BlurredCover` (phone's `app/kreate/android/screens/player/background/BlurredCover.kt` 32-129): the
 * current cover, fitted in a square of the screen's longest side, scaled to its diagonal, blurred by the
 * phone's `BlurAdjuster` strength when the cover is shown (`showthumbnail`) or `noblur` is off — the phone's
 * `showThumbnail || !noBlur` rule, no blur otherwise (lyrics and visualizer are not ported), turning in 30 s
 * when its rotating
 * cover is on, then the black backdrop. Since contract 1.10.0 (`ui.settings`) the adjuster is the phone's
 * (`blurScale`, `playerBackdrop`, `rotatingAlbumCover`, `showthumbnail`, `noblur`); its defaults otherwise
 * (strength 25, backdrop 0 %, no rotation). Drawn under the player's background (`Player.kt` 1418 and 1967).
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
        val settings = LocalUiSettings.current
        val blurRadius = if (settings.showThumbnail || !settings.noBlur) settings.blurStrength else 0f
        // The infinite transition runs only while the phone's rotating cover is on
        val angle = if (settings.rotatingAlbumCover) {
            val rotation by rememberInfiniteTransition().animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(animation = tween(durationMillis = 30000, easing = LinearEasing)),
            )
            rotation
        } else {
            0f
        }
        ImageCacheFactory.Thumbnail(
            key = key,
            contentDescription = stringResource(Res.string.cd_blurred_background),
            // The phone passes ContentScale.Fit here whatever the caller asks
            contentScale = ContentScale.Fit,
            modifier = modifier
                .fillMaxSize()
                .then(if (blurRadius > 0f) Modifier.blur(blurRadius.dp) else Modifier)
                .then(
                    if (scale != 1f || angle != 0f) {
                        Modifier.graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            rotationZ = angle
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
    val backdropColor = Color.Black.copy(alpha = (LocalUiSettings.current.playerBackdrop / 100f).coerceIn(0f, 1f))
    Box(modifier.fillMaxSize().background(backdropColor))
}
