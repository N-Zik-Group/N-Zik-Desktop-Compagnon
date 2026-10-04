package app.n_zik.compagnon.components.items

import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.painterResource
import app.n_zik.compagnon.generated.resources.bookmark
import app.n_zik.compagnon.generated.resources.cd_background_image
import app.n_zik.compagnon.generated.resources.cd_origin_indicator
import app.n_zik.compagnon.generated.resources.ic_launcher
import app.n_zik.compagnon.generated.resources.locked
import app.n_zik.compagnon.generated.resources.musical_notes
import app.n_zik.compagnon.generated.resources.pin_filled
import app.n_zik.compagnon.generated.resources.Res
import app.n_zik.compagnon.generated.resources.riplay
import app.n_zik.compagnon.generated.resources.spotify
import app.n_zik.compagnon.generated.resources.stat_month
import app.n_zik.compagnon.generated.resources.stat_year
import app.n_zik.compagnon.generated.resources.ytmusic
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.material3.Icon
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.n_zik.compagnon.bridge.library.PlaylistOrigin
import app.n_zik.compagnon.bridge.state.Track
import app.n_zik.compagnon.components.themed.HeaderIconButton
import app.n_zik.compagnon.components.theme.favoritesIcon
import app.n_zik.compagnon.components.theme.onOverlay
import app.n_zik.compagnon.components.theme.overlay
import app.n_zik.compagnon.colorPalette
import app.n_zik.compagnon.utils.LocalPreferences
import app.n_zik.compagnon.utils.UserSettings
import app.n_zik.compagnon.utils.medium
import app.n_zik.compagnon.utils.semiBold
import app.n_zik.compagnon.typography
import app.n_zik.compagnon.uiRoundnessShape
import app.n_zik.compagnon.core.coil.ImageCacheFactory
import app.n_zik.compagnon.core.network.ArtworkKey
import app.n_zik.compagnon.thumbnailShape
import app.n_zik.compagnon.utils.color
import kotlinx.coroutines.flow.MutableStateFlow

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
 * The origin icon at the top start, 40 dp with 5 dp of padding (`PlaylistItem.kt` 301-340), since 1.7
 * from the contract's [isPinned] and [origin], with the phone's own assets: the pinned playlists
 * (the `pinned:` name prefix) show the pin in accent with priority, then the origin — `ic_launcher` in
 * its own colours (local), `stat_month` / `stat_year` in accent (the monthly / yearly rewind, since
 * 1.7.1), `musical_notes` in accent (the all-time rewind, and the legacy 1.7 `rewind` wire value),
 * `ytmusic` in red (YouTube Music), `spotify` and `riplay` in their own colours. The lock badge
 * (`Playlist.kt` 101-114, since 1.7.1) — the phone's themed card only: the grid cards of the phone's
 * playlists tab carry no lock badge — is drawn when [lockBadge] is `true`: `locked` in red, 18 dp at
 * the bottom start. The bookmark badge of a bookmarked YouTube playlist
 * (`PlaylistItem.kt` 362-371): 12 dp in `favoritesIcon` at the bottom start, outside the clip,
 * like on the phone. The name honours the "Disable scrolling text" setting (the phone's
 * `disableScrollingTextKey`, its `PlaylistItem.kt` 390-401): no marquee when it is set.
 */
@Composable
fun PlaylistItem(
    thumbnailContent: @Composable BoxWithConstraintsScope.() -> Unit,
    songCount: Int?,
    name: String?,
    /** Since 1.7: the origin of the playlist, for its origin icon. */
    origin: PlaylistOrigin = PlaylistOrigin.Local,
    /** Since 1.7: a pinned playlist (the phone's `pinned:` name prefix); the pin takes priority over the origin. */
    isPinned: Boolean = false,
    /** Since 1.7: a bookmarked YouTube playlist, for its bookmark badge. */
    isBookmarked: Boolean = false,
    /**
     * Since 1.7.1: the phone's themed-card lock badge (`Playlist.kt` 101-114) — the grid cards of the
     * phone's playlists tab carry no lock badge, so it is `false` there.
     */
    lockBadge: Boolean = false,
    thumbnailSizeDp: Dp,
    modifier: Modifier = Modifier,
    alternative: Boolean = false,
    showName: Boolean = true,
    showSongsCount: Boolean = true,
    showInfo: Boolean = true,
    thumbnailOverlay: @Composable () -> Unit = {},
) {
    // The PC's "Disable scrolling text" (the phone's `disableScrollingTextKey`, its
    // `PlaylistItem.kt` 390, 401): the name's marquee is dropped when set
    val preferences = LocalPreferences.current
    val settings by (preferences?.settings ?: remember { MutableStateFlow(UserSettings()) }).collectAsState()
    val marquee: Modifier = if (settings.disableScrollingText) Modifier else Modifier.basicMarquee(iterations = Int.MAX_VALUE)

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
                        // The origin icon (contract 1.7, phone's `PlaylistItem.kt` 301-333): the pinned
                        // playlists show the pin in accent with priority (the phone's `pinned:` name
                        // prefix check), then the origin, with the phone's own assets. Since 1.7.1 the
                        // generated rewind playlists keep their period icon; the legacy `rewind` wire
                        // value (a ≤ 1.7 phone) falls back to the phone's all-time icon.
                        val (painter, tint) = when {
                            isPinned -> Res.drawable.pin_filled to colorPalette().accent
                            else -> when (origin) {
                                PlaylistOrigin.Local -> Res.drawable.ic_launcher to Color.Unspecified
                                PlaylistOrigin.RewindMonthly -> Res.drawable.stat_month to colorPalette().accent
                                PlaylistOrigin.RewindYearly -> Res.drawable.stat_year to colorPalette().accent
                                PlaylistOrigin.RewindAlltime,
                                PlaylistOrigin.Rewind,
                                -> Res.drawable.musical_notes to colorPalette().accent
                                PlaylistOrigin.Ytmusic -> Res.drawable.ytmusic to colorPalette().red
                                PlaylistOrigin.Spotify -> Res.drawable.spotify to Color.Unspecified
                                PlaylistOrigin.Ripley -> Res.drawable.riplay to Color.Unspecified
                            }
                        }
                        Icon(
                            painter = painterResource(painter),
                            contentDescription = stringResource(Res.string.cd_origin_indicator),
                            tint = tint,
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

                    // The lock badge (contract 1.7.1, phone's `Playlist.kt` 101-114): the themed card of a
                    // non-editable YouTube playlist — the red `locked` icon in an `text` circle, 18 dp, at
                    // the bottom start, inside the thumbnail clip, like on the phone
                    if (lockBadge) {
                        Image(
                            painter = painterResource(Res.drawable.locked),
                            colorFilter = ColorFilter.tint(Color.Red),
                            contentDescription = stringResource(Res.string.cd_background_image),
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .padding(all = 5.dp)
                                .background(colorPalette().text, CircleShape)
                                .padding(all = 5.dp)
                                .size(18.dp)
                                .align(Alignment.BottomStart),
                        )
                    }
                }
            }

            // The bookmark badge (contract 1.7, phone's `PlaylistItem.kt` 362-371): a bookmarked YouTube
            // playlist, 12 dp in `favoritesIcon` at the bottom start — outside the clip, like on the phone
            if (isBookmarked) {
                HeaderIconButton(
                    onClick = {},
                    icon = Res.drawable.bookmark,
                    color = colorPalette().favoritesIcon,
                    iconSize = 12.dp,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .absoluteOffset(x = (-8).dp),
                )
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
                        modifier = marquee,
                    )
                }
            }
        }
    }
}
