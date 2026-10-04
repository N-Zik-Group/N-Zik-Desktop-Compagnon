package app.n_zik.compagnon.components.items

import androidx.compose.foundation.Image
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.n_zik.compagnon.bridge.library.Album
import app.n_zik.compagnon.bridge.library.PlaylistOrigin
import app.n_zik.compagnon.components.theme.favoritesIcon
import app.n_zik.compagnon.colorPalette
import app.n_zik.compagnon.utils.LocalPreferences
import app.n_zik.compagnon.utils.UserSettings
import app.n_zik.compagnon.utils.semiBold
import app.n_zik.compagnon.typography
import app.n_zik.compagnon.components.themed.HeaderIconButton
import app.n_zik.compagnon.core.coil.ImageCacheFactory
import app.n_zik.compagnon.core.network.ArtworkKey
import app.n_zik.compagnon.generated.resources.Res
import app.n_zik.compagnon.generated.resources.bookmark
import app.n_zik.compagnon.generated.resources.bookmark_slash
import app.n_zik.compagnon.generated.resources.cd_origin_indicator
import app.n_zik.compagnon.generated.resources.ytmusic
import app.n_zik.compagnon.thumbnailShape
import app.n_zik.compagnon.utils.secondary
import kotlinx.coroutines.flow.MutableStateFlow
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Port of `AlbumItem` (phone's `app/it/fast4x/rimusic/ui/items/AlbumItem.kt` 66 and 153).
 *
 * [likeState] is the album's bookmark state (contract 1.3 `isBookmarked`, or the `bookmarked` filter
 * for a 1.2 phone): `true` shows the `bookmark` badge, `false` the phone's `bookmark_slash` (since
 * 1.7.1, the contract's `isDisliked`), `null` hides it. The YouTube Music origin badge (the phone's
 * `isYoutubeAlbum`, since 1.7.1 the contract's [PlaylistOrigin.Ytmusic]): `ytmusic` 40 dp in red over
 * white at the top start of the thumbnail clip.
 */
@Composable
fun AlbumItem(
    album: Album,
    thumbnailSizePx: Int,
    thumbnailSizeDp: Dp,
    modifier: Modifier = Modifier,
    alternative: Boolean = false,
    yearCentered: Boolean? = true,
    showAuthors: Boolean? = false,
    showInfo: Boolean = true,
    likeState: Boolean? = null,
    thumbnailOverlay: @Composable () -> Unit = {},
) {
    // The PC's "Disable scrolling text" (the phone's `disableScrollingTextKey`, its `AlbumItem.kt`
    // 231, 242): the title / authors marquees are dropped when set
    val preferences = LocalPreferences.current
    val settings by (preferences?.settings ?: remember { MutableStateFlow(UserSettings()) }).collectAsState()
    val marquee: Modifier = if (settings.disableScrollingText) Modifier else Modifier.basicMarquee(iterations = Int.MAX_VALUE)

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
                    .clip(thumbnailShape()),
            ) {
                ImageCacheFactory.Thumbnail(
                    key = if (album.hasArtwork) ArtworkKey.album(album.id, thumbnailSizePx) else null,
                    contentScale = if (alternative) ContentScale.FillWidth else ContentScale.Crop,
                )
                thumbnailOverlay()

                // The phone's `isYoutubeAlbum` badge (since 1.7.1 the contract's Ytmusic origin): red over
                // white, 40 dp, top start inside the thumbnail clip
                if (album.origin == PlaylistOrigin.Ytmusic) {
                    Image(
                        painter = painterResource(Res.drawable.ytmusic),
                        colorFilter = ColorFilter.tint(Color.Red.copy(alpha = 0.75f).compositeOver(Color.White)),
                        contentDescription = stringResource(Res.string.cd_origin_indicator),
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .size(40.dp)
                            .padding(all = 5.dp),
                    )
                }
            }

            if (likeState != null) {
                HeaderIconButton(
                    onClick = {},
                    icon = if (likeState) Res.drawable.bookmark else Res.drawable.bookmark_slash,
                    color = if (likeState) colorPalette().favoritesIcon else colorPalette().red,
                    iconSize = 12.dp,
                    modifier = Modifier.align(Alignment.BottomStart)
                        .absoluteOffset(x = (-8).dp),
                )
            }
        }
        if (showInfo) {
            ItemInfoContainer(
                horizontalAlignment = if (yearCentered == true) Alignment.CenterHorizontally else Alignment.Start,
            ) {
                BasicText(
                    text = album.title,
                    style = typography().xs.semiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = marquee,
                )

                if (!alternative || showAuthors == true) {
                    album.artists?.let { authors ->
                        BasicText(
                            text = authors,
                            style = typography().xs.semiBold.secondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = marquee,
                        )
                    }
                }

                BasicText(
                    text = album.year ?: "",
                    style = typography().xxs.semiBold.secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(top = 4.dp),
                )
            }
        }
    }
}
