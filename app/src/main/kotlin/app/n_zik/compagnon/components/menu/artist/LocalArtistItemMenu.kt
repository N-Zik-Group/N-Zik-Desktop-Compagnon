package app.n_zik.compagnon.components.menu.artist

import app.n_zik.compagnon.generated.resources.update_artist_browse_id
import app.n_zik.compagnon.generated.resources.update_cover
import app.n_zik.compagnon.generated.resources.update_title
import app.n_zik.compagnon.generated.resources.cover_edit
import app.n_zik.compagnon.generated.resources.title_edit
import app.n_zik.compagnon.generated.resources.management
import app.n_zik.compagnon.components.menu.InertMenuItem
import app.n_zik.compagnon.generated.resources.share_social
import app.n_zik.compagnon.generated.resources.bookmark_outline
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.n_zik.compagnon.artistThumbnailShape
import app.n_zik.compagnon.bridge.library.Artist
import app.n_zik.compagnon.components.LocalMenuState
import app.n_zik.compagnon.components.menu.ListMenu
import app.n_zik.compagnon.components.menu.album.MENU_THUMBNAIL_SIZE_PX
import app.n_zik.compagnon.components.styling.Dimensions
import app.n_zik.compagnon.components.theme.favoritesIcon
import app.n_zik.compagnon.components.tab.SongShuffler
import app.n_zik.compagnon.components.tab.toolbar.MenuIcon
import app.n_zik.compagnon.colorPalette
import app.n_zik.compagnon.utils.semiBold
import app.n_zik.compagnon.typography
import app.n_zik.compagnon.components.themed.HeaderIconButton
import app.n_zik.compagnon.components.ui.screens.home.ItemActions
import app.n_zik.compagnon.core.coil.ImageCacheFactory
import app.n_zik.compagnon.core.network.ArtworkKey
import app.n_zik.compagnon.generated.resources.Res
import app.n_zik.compagnon.generated.resources.bookmark
import app.n_zik.compagnon.generated.resources.play
import app.n_zik.compagnon.generated.resources.play_all_local_songs
import app.n_zik.compagnon.generated.resources.playback
import app.n_zik.compagnon.generated.resources.songs
import app.n_zik.compagnon.utils.secondary
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.stringResource

/**
 * Port of `LocalArtistItemMenu` (phone's `app/n_zik/android/components/menu/artist/LocalArtistItemMenu.kt`),
 * list style.
 *
 * Kept: the header (`ArtistItemDisplay`: drag handle, 64 dp round thumbnail, name, "N Songs", the 48 dp
 * column of the bookmark and share icons, divider), the Playback section ("Play all local songs" =
 * `queue/play` from the first track, Shuffle) and the Management section (change title, cover, artist
 * browse id: no contract route, shown without action; 94-105). Bookmark and share have no action.
 */
class LocalArtistItemMenu(
    private val artist: Artist,
    private val actions: ItemActions,
    private val bookmarked: Boolean,
) {

    @Composable
    fun ListMenu() = ListMenu.Menu(title = null, showDragHandle = false) {
        val menuState = LocalMenuState.current
        val playAll = object : MenuIcon {
            override val iconId: DrawableResource = Res.drawable.play
            override val isEnabled: Boolean = actions.enabled
            override val menuIconTitle: String
                @Composable get() = stringResource(Res.string.play_all_local_songs)

            override fun onShortClick() {
                actions.onPlay()
                menuState.hide()
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

                    if (bookmarked) {
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
                        modifier = Modifier
                            .basicMarquee(iterations = Int.MAX_VALUE),
                    )
                    if (subscribersCount.isNotBlank()) {
                        BasicText(
                            text = subscribersCount,
                            style = typography().xxs.semiBold.secondary.copy(
                                color = colorPalette().textSecondary,
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .basicMarquee(iterations = Int.MAX_VALUE),
                        )
                    }
                }

                Column(
                    Modifier.width(48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // Contract 1.3 `isBookmarked`; no bookmark route: shown, without action
                    IconButton(
                        icon = if (bookmarked) Res.drawable.bookmark else Res.drawable.bookmark_outline,
                        color = if (bookmarked) colorPalette().favoritesIcon else colorPalette().text,
                        onClick = {},
                        modifier = Modifier
                            .padding(all = 4.dp)
                            .size(20.dp),
                    )

                    IconButton(
                        icon = Res.drawable.share_social,
                        color = colorPalette().text,
                        onClick = {},
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
