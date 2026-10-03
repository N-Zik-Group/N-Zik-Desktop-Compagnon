package app.n_zik.compagnon.components.themed

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import app.n_zik.compagnon.components.items.PlaylistItem
import app.n_zik.compagnon.core.coil.ImageCacheFactory
import app.n_zik.compagnon.core.network.ArtworkKey

/**
 * Port of `Playlist` (phone's `app/it/fast4x/rimusic/ui/components/themed/Playlist.kt` 40), the playlist card
 * of the playlist screen: one image when [thumbnails] holds a single one, else the 2×2 mosaic.
 * Dropped: the custom thumbnail URL (not in the contract) and the "locked" badge of a non-editable
 * YouTube playlist (no origin in the contract).
 */
@Composable
fun Playlist(
    name: String,
    songCount: Int,
    thumbnails: List<ArtworkKey>,
    thumbnailSizeDp: Dp,
    modifier: Modifier = Modifier,
    alternative: Boolean = false,
    showName: Boolean = true,
) {
    PlaylistItem(
        thumbnailContent = {
            if (thumbnails.toSet().size == 1) {
                ImageCacheFactory.Thumbnail(
                    key = thumbnails.first(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                )
            } else if (thumbnails.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize(),
                ) {
                    listOf(
                        Alignment.TopStart,
                        Alignment.TopEnd,
                        Alignment.BottomStart,
                        Alignment.BottomEnd,
                    ).forEachIndexed { index, alignment ->
                        ImageCacheFactory.Thumbnail(
                            key = thumbnails.getOrNull(index),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .align(alignment)
                                .size(thumbnailSizeDp / 2),
                        )
                    }
                }
            }
        },
        songCount = songCount,
        name = name,
        thumbnailSizeDp = thumbnailSizeDp,
        modifier = modifier,
        alternative = alternative,
        showName = showName,
    )
}
