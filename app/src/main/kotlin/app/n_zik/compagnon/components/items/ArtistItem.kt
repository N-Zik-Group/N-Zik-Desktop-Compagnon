package app.n_zik.compagnon.components.items

import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.n_zik.compagnon.artistThumbnailShape
import app.n_zik.compagnon.bridge.library.Artist
import app.n_zik.compagnon.components.theme.favoritesIcon
import app.n_zik.compagnon.colorPalette
import app.n_zik.compagnon.utils.semiBold
import app.n_zik.compagnon.typography
import app.n_zik.compagnon.components.themed.HeaderIconButton
import app.n_zik.compagnon.core.coil.ImageCacheFactory
import app.n_zik.compagnon.core.network.ArtworkKey
import app.n_zik.compagnon.generated.resources.Res
import app.n_zik.compagnon.generated.resources.bookmark

/**
 * Port of `ArtistItem` (phone's `app/it/fast4x/rimusic/ui/items/ArtistItem.kt` 61 and 141). The phone's
 * local artists pass `subscribersCount = null`, so no second line. [likeState] as in `AlbumItem`.
 * Dropped: the YouTube Music origin badge (`isYoutubeArtist`, not in the contract).
 */
@Composable
fun ArtistItem(
    artist: Artist,
    thumbnailSizePx: Int,
    thumbnailSizeDp: Dp,
    modifier: Modifier = Modifier,
    alternative: Boolean = false,
    showName: Boolean = true,
    likeState: Boolean? = null,
    thumbnailOverlay: @Composable () -> Unit = {},
) {
    ItemContainer(
        alternative = alternative,
        thumbnailSizeDp = thumbnailSizeDp,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .then(if (alternative) Modifier.fillMaxWidth().aspectRatio(1f) else Modifier.size(thumbnailSizeDp)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(artistThumbnailShape()),
            ) {
                ImageCacheFactory.Thumbnail(
                    key = if (artist.hasArtwork) ArtworkKey.artist(artist.id, thumbnailSizePx) else null,
                    contentScale = if (alternative) ContentScale.FillWidth else ContentScale.Crop,
                )
                thumbnailOverlay()
            }

            if (likeState == true) {
                HeaderIconButton(
                    onClick = {},
                    icon = Res.drawable.bookmark,
                    color = colorPalette().favoritesIcon,
                    iconSize = 12.dp,
                    modifier = Modifier.align(Alignment.BottomStart)
                        .absoluteOffset(x = (-8).dp),
                )
            }
        }

        if (showName) {
            ItemInfoContainer(
                horizontalAlignment = if (alternative) Alignment.CenterHorizontally else Alignment.Start,
            ) {
                BasicText(
                    text = artist.name,
                    style = typography().xs.semiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .basicMarquee(iterations = Int.MAX_VALUE),
                )
            }
        }
    }
}
