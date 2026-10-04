package app.n_zik.compagnon.components.items

import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.painterResource
import app.n_zik.compagnon.generated.resources.cd_origin_indicator
import app.n_zik.compagnon.generated.resources.ic_launcher
import app.n_zik.compagnon.generated.resources.Res
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Icon
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import app.n_zik.compagnon.bridge.state.Track
import app.n_zik.compagnon.components.theme.onOverlay
import app.n_zik.compagnon.components.theme.overlay
import app.n_zik.compagnon.colorPalette
import app.n_zik.compagnon.utils.medium
import app.n_zik.compagnon.utils.semiBold
import app.n_zik.compagnon.typography
import app.n_zik.compagnon.uiRoundnessShape
import app.n_zik.compagnon.core.coil.ImageCacheFactory
import app.n_zik.compagnon.core.network.ArtworkKey
import app.n_zik.compagnon.thumbnailShape
import app.n_zik.compagnon.utils.color

/*
 * Port of the phone's `app/it/fast4x/rimusic/ui/items/PlaylistItem.kt`.
 */

val FOUR_CORNERS = listOf(Alignment.TopStart, Alignment.TopEnd, Alignment.BottomStart, Alignment.BottomEnd)

/**
 * The visual of a playlist: the mosaic of its first four tracks when all four have an artwork (the
 * phone takes four of its tracks), else the artwork of `artworkTrackId` (contract §1.1).
 */
fun playlistThumbnails(firstTracks: List<Track>?, artworkTrackId: String?, sizePx: Int): List<ArtworkKey> {
    val mosaic = firstTracks.orEmpty().take(4)
    return if (mosaic.size == 4 && mosaic.all(Track::hasArtwork)) {
        mosaic.map { ArtworkKey.track(it.id, sizePx / 2) }
    } else {
        listOfNotNull(artworkTrackId?.let { ArtworkKey.track(it, sizePx) })
    }
}

/** Port of `ThumbnailRenderer` (`PlaylistItem.kt` 97): a 2×2 mosaic from 4 thumbnails, else the first one. */
@Composable
fun BoxWithConstraintsScope.ThumbnailRenderer(
    thumbnails: List<ArtworkKey>,
    contentScale: ContentScale = ContentScale.Crop,
) {
    if (thumbnails.size >= 4) {
        val halfWidth = maxWidth / 2
        val halfHeight = maxHeight / 2

        FOUR_CORNERS.forEachIndexed { index, corner ->
            ImageCacheFactory.Thumbnail(
                key = thumbnails[index],
                contentDescription = corner.toString(),
                contentScale = contentScale,
                modifier = Modifier.size(halfWidth, halfHeight)
                    .align(corner),
            )
        }
    } else if (thumbnails.isNotEmpty()) {
        ImageCacheFactory.Thumbnail(thumbnails.first(), "fullSizeRender", contentScale)
    }
}

/**
 * Port of `PlaylistItem` (`PlaylistItem.kt` 159 and 259): `background4` square, thumbnail, track count in a
 * pill at the bottom end, name under it.
 *
 * The origin icon at the top start, 40 dp with 5 dp of padding (`PlaylistItem.kt` 301-339): the contract
 * sends the cleaned name and no browse id, so every playlist shows the phone's local-playlist origin,
 * `ic_launcher` in its own colours. Dropped: the other origins (pinned, monthly, Rewind, YouTube Music,
 * Spotify, RiPlay: not given by the contract) and the bookmark state.
 */
@Composable
fun PlaylistItem(
    thumbnailContent: @Composable BoxWithConstraintsScope.() -> Unit,
    songCount: Int?,
    name: String?,
    thumbnailSizeDp: Dp,
    modifier: Modifier = Modifier,
    alternative: Boolean = false,
    showName: Boolean = true,
    showSongsCount: Boolean = true,
    showInfo: Boolean = true,
    thumbnailOverlay: @Composable () -> Unit = {},
) {
    ItemContainer(
        alternative = alternative,
        thumbnailSizeDp = thumbnailSizeDp,
        modifier = modifier,
    ) {
        Box {
            BoxWithConstraints(
                modifier = Modifier
                    .then(if (alternative) Modifier.fillMaxWidth().aspectRatio(1f) else Modifier.size(thumbnailSizeDp))
                    .clip(thumbnailShape())
                    .background(colorPalette().background4),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(thumbnailShape()),
                ) {
                    this@BoxWithConstraints.run {
                        thumbnailContent()
                        thumbnailOverlay()
                    }

                    if (name != null) {
                        // browseId.isNullOrEmpty(): a local playlist
                        Icon(
                            painter = painterResource(Res.drawable.ic_launcher),
                            contentDescription = stringResource(Res.string.cd_origin_indicator),
                            tint = Color.Unspecified,
                            modifier = Modifier.size(40.dp).padding(all = 5.dp),
                        )

                        songCount?.let {
                            if (showSongsCount) {
                                BasicText(
                                    text = songCount.toString(),
                                    style = typography().xxs.medium.color(colorPalette().onOverlay),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(all = 4.dp)
                                        .background(
                                            color = colorPalette().overlay,
                                            shape = uiRoundnessShape(),
                                        )
                                        .padding(all = 6.dp)
                                        .align(Alignment.BottomEnd),
                                )
                            }
                        }
                    }
                }
            }
        }

        if (showInfo) {
            ItemInfoContainer(
                horizontalAlignment = if (alternative) Alignment.CenterHorizontally else Alignment.Start,
                modifier = Modifier
                    .fillMaxSize(),
            ) {
                if (showName && name != null) {
                    BasicText(
                        text = name,
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
}
