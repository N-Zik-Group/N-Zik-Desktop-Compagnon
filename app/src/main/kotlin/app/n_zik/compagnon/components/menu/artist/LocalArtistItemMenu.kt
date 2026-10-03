package app.n_zik.compagnon.components.menu.artist

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
 * Kept: the header (`ArtistItemDisplay`: drag handle, 64 dp round thumbnail, name, "N Songs", divider) and
 * the "Playback" section ("Play all local songs" = `queue/play` from the first track, Shuffle).
 * Dropped (contract v1): the bookmark / share buttons of the header and the Management section (edit
 * title, cover, id).
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
