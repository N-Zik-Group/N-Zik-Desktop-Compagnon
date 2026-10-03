package app.n_zik.compagnon.components.menu.album

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
import app.n_zik.compagnon.bridge.library.Album
import app.n_zik.compagnon.components.menu.ListMenu
import app.n_zik.compagnon.components.styling.Dimensions
import app.n_zik.compagnon.components.theme.favoritesIcon
import app.n_zik.compagnon.components.tab.SongShuffler
import app.n_zik.compagnon.colorPalette
import app.n_zik.compagnon.utils.semiBold
import app.n_zik.compagnon.typography
import app.n_zik.compagnon.components.themed.Enqueue
import app.n_zik.compagnon.components.themed.HeaderIconButton
import app.n_zik.compagnon.components.themed.PlayNext
import app.n_zik.compagnon.components.ui.screens.home.ItemActions
import app.n_zik.compagnon.core.coil.ImageCacheFactory
import app.n_zik.compagnon.core.network.ArtworkKey
import app.n_zik.compagnon.generated.resources.Res
import app.n_zik.compagnon.generated.resources.bookmark
import app.n_zik.compagnon.generated.resources.playback
import app.n_zik.compagnon.thumbnailShape
import app.n_zik.compagnon.utils.secondary
import org.jetbrains.compose.resources.stringResource

/** Menu thumbnails are `Dimensions.thumbnails.album / 2` = 64 dp: `size` ≈ 2× in px. */
const val MENU_THUMBNAIL_SIZE_PX = 128

/**
 * Port of `AlbumItemMenu` (phone's `app/n_zik/android/components/menu/album/AlbumItemMenu.kt`), list style.
 *
 * Kept: the header (`AlbumItemDisplay`: drag handle, 64 dp thumbnail, title, artists, year, divider) and
 * the "Playback" section (Shuffle, Play next, Enqueue).
 * Dropped (contract v1): the bookmark / share buttons of the header, "Add to playlist", download all,
 * delete downloads, the Management section (edit title, artists, cover, ids) and the Navigation section
 * ("More of" the artists).
 */
class AlbumItemMenu(
    private val album: Album,
    private val actions: ItemActions,
    private val bookmarked: Boolean,
) {

    @Composable
    fun ListMenu() = ListMenu.Menu(title = null, showDragHandle = false) {
        ListMenu.SectionTitle(stringResource(Res.string.playback))
        actions.onShuffle?.let { SongShuffler(enabled = actions.enabled, onShuffle = it).ListMenuItem() }
        PlayNext(enabled = actions.enabled, onClick = actions.onPlayNext).ListMenuItem()
        Enqueue(enabled = actions.enabled, onClick = actions.onEnqueue).ListMenuItem()
    }

    @Composable
    private fun AlbumItemDisplay(
        album: Album,
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
                // Album's thumbnail
                Box(
                    Modifier.size(Dimensions.thumbnails.album / 2),
                ) {
                    Box(
                        modifier = Modifier
                            .size(Dimensions.thumbnails.album / 2)
                            .clip(thumbnailShape()),
                    ) {
                        ImageCacheFactory.Thumbnail(
                            key = if (album.hasArtwork) ArtworkKey.album(album.id, MENU_THUMBNAIL_SIZE_PX) else null,
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

                // Album's information
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    BasicText(
                        text = album.title,
                        style = typography().xs.semiBold.copy(
                            color = colorPalette().text,
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .basicMarquee(iterations = Int.MAX_VALUE),
                    )

                    album.artists?.let {
                        BasicText(
                            text = it,
                            style = typography().xs.semiBold.secondary.copy(
                                color = colorPalette().textSecondary,
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .basicMarquee(iterations = Int.MAX_VALUE),
                        )
                    }

                    album.year?.let {
                        BasicText(
                            text = it,
                            style = typography().xxs.semiBold.secondary.copy(
                                color = colorPalette().textSecondary,
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
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
            AlbumItemDisplay(album = album)
            ListMenu()
        }
    }
}
