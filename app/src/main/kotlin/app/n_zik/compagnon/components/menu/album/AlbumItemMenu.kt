package app.n_zik.compagnon.components.menu.album

import app.n_zik.compagnon.generated.resources.more_of
import app.n_zik.compagnon.generated.resources.people
import app.n_zik.compagnon.generated.resources.navigation
import app.n_zik.compagnon.generated.resources.update_artist_browse_id
import app.n_zik.compagnon.generated.resources.update_album_browse_id
import app.n_zik.compagnon.generated.resources.update_authors
import app.n_zik.compagnon.generated.resources.artists_edit
import app.n_zik.compagnon.generated.resources.info_remove_all_downloaded_songs
import app.n_zik.compagnon.generated.resources.download
import app.n_zik.compagnon.generated.resources.downloaded
import app.n_zik.compagnon.generated.resources.info_download_all_songs
import app.n_zik.compagnon.generated.resources.add_to_playlist
import app.n_zik.compagnon.generated.resources.add_in_playlist
import app.n_zik.compagnon.components.menu.splitArtistNames
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
 * Kept: the header (`AlbumItemDisplay`: drag handle, 64 dp thumbnail, title, artists, year, the 48 dp
 * column of the bookmark and share icons, 332-415, divider) and the sections in the phone's order (147-171):
 * Playback (Shuffle, Play next, Enqueue: wired to the contract), Management (add to playlist, download,
 * remove all downloaded songs, change authors / cover, album / artist browse ids), Navigation ("More of" each
 * artist, then "Change Title": the phone's index ranges put it there). Entries without a contract route,
 * bookmark and share are shown without action.
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

        // Section: Management
        ListMenu.SectionTitle(stringResource(Res.string.management))
        InertMenuItem(Res.drawable.add_in_playlist, Res.string.add_to_playlist).ListMenuItem()
        InertMenuItem(Res.drawable.downloaded, Res.string.info_download_all_songs).ListMenuItem()
        InertMenuItem(Res.drawable.download, Res.string.info_remove_all_downloaded_songs).ListMenuItem()
        InertMenuItem(Res.drawable.artists_edit, Res.string.update_authors).ListMenuItem()
        InertMenuItem(Res.drawable.cover_edit, Res.string.update_cover).ListMenuItem()
        InertMenuItem(Res.drawable.title_edit, Res.string.update_album_browse_id).ListMenuItem()
        InertMenuItem(Res.drawable.title_edit, Res.string.update_artist_browse_id).ListMenuItem()

        // Section: Navigation
        ListMenu.SectionTitle(stringResource(Res.string.navigation))
        val artistNames = splitArtistNames(album.artists)
        if (artistNames.size <= 1) {
            InertMenuItem(Res.drawable.people, Res.string.more_of, " ${album.artists.orEmpty()}").ListMenuItem()
        } else {
            artistNames.forEach { InertMenuItem(Res.drawable.people, Res.string.more_of, " $it").ListMenuItem() }
        }
        InertMenuItem(Res.drawable.title_edit, Res.string.update_title).ListMenuItem()
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
            AlbumItemDisplay(album = album)
            ListMenu()
        }
    }
}
