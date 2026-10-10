package app.n_zik.compagnon.components.menu.artist

import app.n_zik.compagnon.generated.resources.*
import app.n_zik.compagnon.components.menu.InertMenuItem
import app.n_zik.compagnon.components.themed.IconButton
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.n_zik.compagnon.LocalLibraryActions
import app.n_zik.compagnon.artistThumbnailShape
import app.n_zik.compagnon.bridge.library.Artist
import app.n_zik.compagnon.bridge.library.ArtistFollow
import app.n_zik.compagnon.bridge.library.DislikeMode
import app.n_zik.compagnon.bridge.library.PagedState
import app.n_zik.compagnon.bridge.library.nextRotation
import app.n_zik.compagnon.bridge.library.nextToggle
import app.n_zik.compagnon.components.LocalMenuState
import app.n_zik.compagnon.components.menu.ListMenu
import app.n_zik.compagnon.components.menu.album.MENU_THUMBNAIL_SIZE_PX
import app.n_zik.compagnon.components.styling.Dimensions
import app.n_zik.compagnon.components.theme.favoritesIcon
import app.n_zik.compagnon.components.tab.SongShuffler
import app.n_zik.compagnon.components.tab.toolbar.MenuIcon
import app.n_zik.compagnon.colorPalette
import app.n_zik.compagnon.utils.LocalPreferences
import app.n_zik.compagnon.utils.semiBold
import app.n_zik.compagnon.utils.UserSettings
import app.n_zik.compagnon.typography
import app.n_zik.compagnon.components.themed.HeaderIconButton
import app.n_zik.compagnon.components.ui.screens.artist.followToast
import app.n_zik.compagnon.components.ui.screens.home.ItemActions
import app.n_zik.compagnon.core.coil.ImageCacheFactory
import app.n_zik.compagnon.core.network.ArtworkKey
import app.n_zik.compagnon.utils.secondary
import kotlinx.coroutines.flow.MutableStateFlow
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.stringResource

/**
 * Port of `LocalArtistItemMenu` (phone's `app/n_zik/android/components/menu/artist/LocalArtistItemMenu.kt`),
 * list style.
 *
 * Kept: the header (`ArtistItemDisplay`: drag handle, 64 dp round thumbnail, name, "N Songs", the 48 dp
 * column of the bookmark and share icons, divider), the Playback section ("Play all local songs" =
 * `queue/play` from the first track, Shuffle) and the Management section (change title, cover, artist
 * browse id: no contract route, shown without action; 94-105). Since 1.7, the bookmark icon (and its
 * thumbnail badge) show the like tri-state (the phone's `likeState`, its 182-196 and 242-252) —
 * `bookmark` in favoritesIcon when followed, the phone's `bookmark_slash` in red when disliked,
 * `bookmark_outline` in text when neutral — and a tap rotates it (neutral → followed → disliked →
 * neutral, the phone's `rotateLikeState`) and writes the phone's target state (`library.write`);
 * without the feature it stays an inert indicator (no icon change, no toast). Since 1.7.2 (the lists' `dislikeMode`):
 * the phone's "disliked" mode off makes the tap a binary toggle (followed → neutral, otherwise →
 * followed, the phone's `toggleBookmark`, its `LocalArtistItemMenu.kt` 255-272). Share copies the
 * artist's YouTube Music channel link to the clipboard (the desktop's share).
 */
class LocalArtistItemMenu(
    private val artist: Artist,
    private val actions: ItemActions,
) {

    @Composable
    fun ListMenu() = ListMenu.Menu(title = null, showDragHandle = false) {
        val menuState = LocalMenuState.current
        val playAll = object : MenuIcon, app.n_zik.compagnon.components.tab.toolbar.Descriptive {
            override val iconId: DrawableResource = Res.drawable.play
            override val messageId: org.jetbrains.compose.resources.StringResource = Res.string.play_all_local_songs
            override val isEnabled: Boolean = actions.enabled
            override val menuIconTitle: String
                @Composable get() = stringResource(messageId)

            // The phone's long press does nothing here (`onLongClick() {}`): no help toast
            override fun onLongClick() {}

            // The phone's 343-351: an artist without songs answers "No song found" and keeps the menu open
            override fun onShortClick() {
                if (artist.trackCount > 0) {
                    actions.onPlay()
                    menuState.hide()
                } else {
                    app.n_zik.compagnon.utils.Toaster.e(Res.string.no_song_found)
                }
            }
        }
        ListMenu.SectionTitle(stringResource(Res.string.playback))
        playAll.ListMenuItem()
        actions.onShuffle?.let { SongShuffler(enabled = actions.enabled, onShuffle = it).ListMenuItem() }

        // Section: Management
        ListMenu.SectionTitle(stringResource(Res.string.management))
        InertMenuItem(Res.drawable.title_edit, Res.string.update_title).ListMenuItem()
        InertMenuItem(Res.drawable.cover_edit, Res.string.update_cover).ListMenuItem()
        InertMenuItem(Res.drawable.title_edit, Res.string.update_artist_browse_id).ListMenuItem()
    }

    @Composable
    private fun ArtistItemDisplay(
        title: String,
        subscribersCount: String,
        modifier: Modifier = Modifier,
    ) {
        // The phone's "Disable scrolling text" (the phone's `LocalArtistItemMenu.kt` 134, 212, 225): the
        // title / subscribers marquee is dropped when set
        val preferences = LocalPreferences.current
        val settings by (preferences?.settings
            ?: remember { MutableStateFlow(UserSettings()) }).collectAsState()
        val marquee: Modifier = if (settings.disableScrollingText) Modifier else Modifier.basicMarquee(iterations = Int.MAX_VALUE)
        // The follow state, since 1.7: the phone reads it live from the DB (its `likeState` flow); here
        // it is seeded from the artist and re-synced from the Artists list (the confirmed §10.2 writes
        // patch it), so the icon follows the write while the menu is open
        val library = LocalLibraryActions.current
        var followState by remember(artist.id) {
            mutableStateOf(
                when {
                    artist.isBookmarked -> ArtistFollow.Followed
                    artist.isDisliked -> ArtistFollow.Disliked
                    else -> ArtistFollow.Neutral
                },
            )
        }
        val artistsState by (library?.lists?.artists?.state ?: remember { MutableStateFlow(PagedState<Artist>()) })
            .collectAsState()
        val listedArtist = artistsState.items.firstOrNull { it.id == artist.id }
        LaunchedEffect(listedArtist) {
            if (listedArtist != null) {
                followState = when {
                    listedArtist.isBookmarked -> ArtistFollow.Followed
                    listedArtist.isDisliked -> ArtistFollow.Disliked
                    else -> ArtistFollow.Neutral
                }
            }
        }
        val writes = library?.takeIf { it.canWrite }
        // Since 1.7.2 (feature `library.dislikeMode`): the phone's "disliked" mode off makes the tap a
        // binary toggle (the phone's `toggleBookmark`); `null` keeps the rotation (the phone's default)
        val dislikeMode by (library?.lists?.dislikeMode ?: remember { MutableStateFlow<DislikeMode?>(null) })
            .collectAsState()
        val rotationEnabled = dislikeMode?.artists != false

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = modifier
                .fillMaxWidth()
                .background(colorPalette().background1),
        ) {
            ListMenu.DragHandle(Color.White)

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        vertical = Dimensions.itemsVerticalPadding,
                        horizontal = 16.dp,
                    ),
            ) {
                // Artist's thumbnail
                Box(
                    Modifier.size(Dimensions.thumbnails.album / 2),
                ) {
                    Box(
                        modifier = Modifier
                            .size(Dimensions.thumbnails.album / 2)
                            .clip(artistThumbnailShape()),
                    ) {
                        ImageCacheFactory.Thumbnail(
                            key = if (artist.hasArtwork) ArtworkKey.artist(artist.id, MENU_THUMBNAIL_SIZE_PX) else null,
                            modifier = Modifier.size(Dimensions.thumbnails.album / 2),
                        )
                    }

                    // The like tri-state badge (contract 1.7, phone's 182-196): `bookmark` in
                    // favoritesIcon when followed, the phone's `bookmark_slash` in red when disliked,
                    // nothing when neutral
                    val badge = when (followState) {
                        ArtistFollow.Followed -> colorPalette().favoritesIcon
                        ArtistFollow.Disliked -> colorPalette().red
                        ArtistFollow.Neutral -> null
                    }
                    if (badge != null) {
                        HeaderIconButton(
                            onClick = {},
                            icon = if (followState == ArtistFollow.Disliked) Res.drawable.bookmark_slash else Res.drawable.bookmark,
                            color = badge,
                            iconSize = 12.dp,
                            modifier = Modifier.align(Alignment.BottomStart)
                                .absoluteOffset(x = (-8).dp),
                        )
                    }
                }

                // Artist's information
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    BasicText(
                        text = title,
                        style = typography().xs.semiBold.copy(
                            color = colorPalette().text,
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = marquee,
                    )
                    if (subscribersCount.isNotBlank()) {
                        BasicText(
                            text = subscribersCount,
                            style = typography().xxs.semiBold.secondary.copy(
                                color = colorPalette().textSecondary,
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = marquee,
                        )
                    }
                }

                Column(
                    Modifier.width(48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // The like tri-state (contract 1.7, phone's 242-297): a tap rotates the state
                    // (neutral → followed → disliked → neutral, the phone's `rotateLikeState`) or, with
                    // the phone's "disliked" mode off (since 1.7.2), toggles it (the phone's
                    // `toggleBookmark`) and writes the phone's target state (`library.write`); without
                    // the feature it stays inert
                    IconButton(
                        // The phone's icon (its 242-247): `bookmark_slash` in red when disliked
                        icon = when (followState) {
                            ArtistFollow.Neutral -> Res.drawable.bookmark_outline
                            ArtistFollow.Disliked -> Res.drawable.bookmark_slash
                            else -> Res.drawable.bookmark
                        },
                        color = when (followState) {
                            ArtistFollow.Followed -> colorPalette().favoritesIcon
                            ArtistFollow.Disliked -> colorPalette().red
                            ArtistFollow.Neutral -> colorPalette().text
                        },
                        onClick = {
                            // Inert without `library.write` (no icon change, no toast: nothing is written)
                            if (actions.enabled && writes != null) {
                                val target = if (rotationEnabled) followState.nextRotation() else followState.nextToggle()
                                followState = target
                                writes?.followArtist(artist.id, target)
                                // The phone's toast (its `LocalArtistItemMenu.kt` 274-292)
                                followToast(target, artist.name.orEmpty())
                            }
                        },
                        modifier = Modifier
                            .padding(all = 4.dp)
                            .size(20.dp),
                    )

                    IconButton(
                        icon = Res.drawable.share_social,
                        color = colorPalette().text,
                        onClick = { app.n_zik.compagnon.components.player.ShareLinks.copy(app.n_zik.compagnon.components.player.ShareLinks.artist(artist.id)) },
                        modifier = Modifier
                            .padding(all = 4.dp)
                            .size(20.dp),
                    )
                }
            }

            HorizontalDivider(Modifier.height(1.dp))
        }
    }

    @Composable
    fun MenuComponent() {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colorPalette().background0),
        ) {
            ArtistItemDisplay(
                title = artist.name,
                subscribersCount = "${artist.trackCount} ${stringResource(Res.string.songs)}",
            )
            ListMenu()
        }
    }
}
