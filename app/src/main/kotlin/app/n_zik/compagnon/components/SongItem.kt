package app.n_zik.compagnon.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.Icon
import androidx.compose.material3.ripple
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absoluteOffset
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.n_zik.compagnon.LocalPlayerRepository
import app.n_zik.compagnon.bridge.state.Track
import app.n_zik.compagnon.bridge.state.TrackSource
import app.n_zik.compagnon.components.styling.Dimensions
import app.n_zik.compagnon.components.theme.favoritesIcon
import app.n_zik.compagnon.components.theme.favoritesOverlay
import app.n_zik.compagnon.colorPalette
import app.n_zik.compagnon.utils.medium
import app.n_zik.compagnon.utils.semiBold
import app.n_zik.compagnon.typography
import app.n_zik.compagnon.uiRoundnessShape
import app.n_zik.compagnon.components.themed.HeaderIconButton
import app.n_zik.compagnon.components.themed.IconButton
import app.n_zik.compagnon.components.themed.NowPlayingSongIndicator
import app.n_zik.compagnon.core.coil.ImageCacheFactory
import app.n_zik.compagnon.core.network.ArtworkKey
import app.n_zik.compagnon.generated.resources.Res
import app.n_zik.compagnon.generated.resources.download
import app.n_zik.compagnon.generated.resources.downloaded
import app.n_zik.compagnon.generated.resources.explicit
import app.n_zik.compagnon.generated.resources.heart
import app.n_zik.compagnon.generated.resources.unknown_artist
import app.n_zik.compagnon.generated.resources.unknown_title
import app.n_zik.compagnon.utils.LocalPreferences
import app.n_zik.compagnon.utils.UserSettings
import app.n_zik.compagnon.utils.cleanPrefix
import app.n_zik.compagnon.utils.formatAsDuration
import app.n_zik.compagnon.utils.hasExplicitPrefix
import app.n_zik.compagnon.utils.onSecondaryClick
import app.n_zik.compagnon.utils.secondary
import kotlinx.coroutines.flow.MutableStateFlow
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** Song thumbnails are 54 dp: `size` ≈ 2× in px (contract §10.1). */
const val SONG_THUMBNAIL_SIZE_PX = 128

/**
 * Port of `SongIndicator.ToolBarButton` (phone's `app/n_zik/android/components/SongItem.kt` 108-150): an
 * 18 dp accent icon in a clickable box (unbounded 20 dp ripple, no action), then a `padding(horizontal = 3.dp)`
 * spacer (6 dp) before the title.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongIndicator(
    icon: DrawableResource,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .size(18.dp)
            .clip(uiRoundnessShape())
            .combinedClickable(
                interactionSource = interactionSource,
                indication = ripple(
                    bounded = false,
                    radius = 20.dp,
                ),
                onClick = {},
                onLongClick = {},
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = colorPalette().accent,
        )
    }

    Spacer(Modifier.padding(horizontal = 3.dp))
}

/** Port of `SongText` (phone's `app/n_zik/android/components/SongItem.kt` 153). */
@Composable
fun SongText(
    text: String,
    style: TextStyle,
    overflow: TextOverflow = TextOverflow.Ellipsis,
    modifier: Modifier = Modifier,
) = BasicText(
    text = text,
    style = style,
    maxLines = 1,
    overflow = overflow,
    modifier = modifier,
)

/**
 * Port of `SongItem` (phone's `app/n_zik/android/components/SongItem.kt` 186).
 *
 * Kept: the 54 dp thumbnail, the now-playing animation and highlight, the 12 dp "liked" heart at
 * -8 dp bottom-left, title / artists with marquee, the duration or `--:--`, the download icon of an online
 * track (filled when the phone has it offline), the 18 dp explicit [SongIndicator] before the title (accent,
 * `Track.isExplicit` of contract 1.3), followed by its 6 dp spacer.
 * Dropped (contract v1 or PC): the disliked heart (`Track.isLiked` is a boolean), the download action and
 * its progress ring (no download in v1: the icon is information only), the "recommended" and "in a
 * playlist" indicators (not exposed by the contract), the multi-selection checkbox, the
 * haptic feedback. [onLongClick] opens the item's menu (a right click too, desktop stand-in for the long press).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongItem(
    song: Track,
    modifier: Modifier = Modifier,
    backgroundColor: Color = colorPalette().background0,
    showThumbnail: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    trailingContent: @Composable (RowScope.() -> Unit)? = null,
    thumbnailOverlay: @Composable BoxScope.() -> Unit = {},
    onClick: () -> Unit = {},
) {
    val colorPalette = colorPalette()
    val repository = LocalPlayerRepository.current
    val playerState by (repository?.state ?: remember { MutableStateFlow(null) }).collectAsState()
    val isPlaying = playerState?.currentTrackId == song.id
    // The PC's "Disable scrolling text" (the phone's `disableScrollingTextKey`, its `SongItem.kt` 204, 338-354):
    // the title / artists marquee is dropped when set
    val preferences = LocalPreferences.current
    val settings by (preferences?.settings ?: remember { MutableStateFlow(UserSettings()) }).collectAsState()
    val marquee: Modifier = if (settings.disableScrollingText) Modifier else Modifier.basicMarquee(iterations = Int.MAX_VALUE)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxWidth()
            .clip(uiRoundnessShape())
            .background(backgroundColor)
            .then(if (isPlaying) Modifier.background(colorPalette.favoritesOverlay) else Modifier)
            .clip(uiRoundnessShape())
            .onSecondaryClick(onLongClick)
            .combinedClickable(
                onClick = onClick,
                onLongClick = { onLongClick?.invoke() },
            )
            .padding(
                vertical = Dimensions.itemsVerticalPadding,
                horizontal = 16.dp,
            ),
    ) {
        // Song's thumbnail
        Box(
            Modifier.size(Dimensions.thumbnails.song),
        ) {
            if (showThumbnail) {
                ImageCacheFactory.Thumbnail(
                    key = if (song.hasArtwork) ArtworkKey.track(song.id, SONG_THUMBNAIL_SIZE_PX) else null,
                    contentScale = ContentScale.FillHeight,
                )
            }

            if (isPlaying) {
                NowPlayingSongIndicator(isPlaying = playerState?.isPlaying == true)
            }

            thumbnailOverlay()

            // Only the liked state exists in contract v1
            if (song.isLiked) {
                HeaderIconButton(
                    onClick = {},
                    icon = Res.drawable.heart,
                    color = colorPalette().favoritesIcon,
                    iconSize = 12.dp,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .absoluteOffset(x = (-8).dp),
                )
            }
        }

        // Song's information
        Column(
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.weight(1f),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Contract 1.3 `Track.isExplicit` (the phone sends the cleaned title); the prefix test keeps
                // the phone's own check (`SongItem.kt` 327) for a title that still carries `e:`
                if (song.isExplicit || song.title.hasExplicitPrefix()) {
                    SongIndicator(icon = Res.drawable.explicit)
                }
                val safeTitle = if (song.title.isBlank() || song.title == "null") stringResource(Res.string.unknown_title) else song.title
                SongText(
                    text = cleanPrefix(safeTitle),
                    style = typography().xs.semiBold,
                    modifier = Modifier.weight(1f)
                        .then(marquee),
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Cleaned like the phone's `cleanArtistsText()`: the queue's bridge metadata can carry prefixes
                val artistsText = cleanPrefix(song.artists.orEmpty())
                val safeArtists = if (artistsText.isBlank() || artistsText == "null") stringResource(Res.string.unknown_artist) else artistsText
                SongText(
                    text = safeArtists,
                    style = typography().xs.semiBold.secondary,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier.weight(1f)
                        .then(marquee),
                )

                SongText(
                    text = song.durationMs?.let(::formatAsDuration) ?: "--:--",
                    style = typography().xxs.secondary.medium,
                    modifier = Modifier.padding(top = 4.dp, start = 5.dp),
                )

                Spacer(Modifier.padding(horizontal = 4.dp))

                // Download icon when the song is NOT local: information only (no download in v1)
                if (song.source != TrackSource.Local) {
                    val color = if (song.isDownloaded) colorPalette().text else colorPalette().textDisabled
                    IconButton(
                        icon = if (song.isDownloaded) Res.drawable.downloaded else Res.drawable.download,
                        color = color,
                        enabled = false,
                        modifier = Modifier.size(20.dp),
                        onClick = {},
                    )
                }
            }
        }

        trailingContent?.invoke(this)
    }
}
