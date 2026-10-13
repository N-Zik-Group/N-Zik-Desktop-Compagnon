package app.n_zik.compagnon.components.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import app.n_zik.compagnon.core.coil.ImageCacheFactory
import app.n_zik.compagnon.core.network.ArtworkKey
import app.n_zik.compagnon.thumbnailShape

/**
 * Port of `DynamicOrientationLayout` (phone's `app/n_zik/android/components/ui/screens/LayoutWithAdaptiveThumbnail.kt`).
 * The phone's `isLandscape` (width > height) is read from the space given to the screen: in a landscape
 * window the cover sits on the left half and [content] on the right, in a portrait one [content] alone
 * (it then draws the cover as its 4:3 header). [content] gets `isLandscape`.
 */
@Composable
fun DynamicOrientationLayout(
    thumbnail: ArtworkKey?,
    shape: Shape? = null,
    content: @Composable (isLandscape: Boolean) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val isLandscape = maxWidth > maxHeight
        if (isLandscape) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxWidth(.5f),
                ) {
                    ImageCacheFactory.Thumbnail(
                        key = thumbnail,
                        contentDescription = null,
                        contentScale = ContentScale.FillBounds,
                        modifier = Modifier.fillMaxSize(.5f)
                            .aspectRatio(1f)
                            .clip(shape ?: thumbnailShape()),
                    )
                }
                content(true)
            }
        } else {
            content(false)
        }
    }
}
