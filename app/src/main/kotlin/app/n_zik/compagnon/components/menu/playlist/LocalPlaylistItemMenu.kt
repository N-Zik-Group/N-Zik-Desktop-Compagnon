package app.n_zik.compagnon.components.menu.playlist

import app.n_zik.compagnon.generated.resources.delete
import app.n_zik.compagnon.generated.resources.trash
import app.n_zik.compagnon.generated.resources.rename_playlist
import app.n_zik.compagnon.generated.resources.title_edit
import app.n_zik.compagnon.generated.resources.info_remove_all_downloaded_songs
import app.n_zik.compagnon.generated.resources.download
import app.n_zik.compagnon.generated.resources.downloaded
import app.n_zik.compagnon.generated.resources.open
import app.n_zik.compagnon.components.menu.InertMenuItem
import app.n_zik.compagnon.components.themed.IconButton
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.n_zik.compagnon.bridge.library.Playlist
import app.n_zik.compagnon.bridge.state.Track
import app.n_zik.compagnon.components.items.playlistThumbnails
import app.n_zik.compagnon.components.menu.ListMenu
import app.n_zik.compagnon.components.menu.album.MENU_THUMBNAIL_SIZE_PX
import app.n_zik.compagnon.components.styling.Dimensions
import app.n_zik.compagnon.components.tab.SongShuffler
import app.n_zik.compagnon.colorPalette
import app.n_zik.compagnon.utils.semiBold
import app.n_zik.compagnon.typography
import app.n_zik.compagnon.components.themed.Enqueue
import app.n_zik.compagnon.components.themed.PlayNext
import app.n_zik.compagnon.components.ui.screens.home.ItemActions
import app.n_zik.compagnon.core.coil.ImageCacheFactory
import app.n_zik.compagnon.generated.resources.Res
import app.n_zik.compagnon.generated.resources.library
import app.n_zik.compagnon.generated.resources.management
import app.n_zik.compagnon.generated.resources.playback
import app.n_zik.compagnon.generated.resources.songs
import app.n_zik.compagnon.thumbnailShape
import app.n_zik.compagnon.utils.secondary
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Port of `LocalPlaylistItemMenu` (phone's `app/n_zik/android/components/menu/playlist/LocalPlaylistItemMenu.kt`),
 * list style.
 *
 * Kept: the header (`PlaylistItemDisplay`: drag handle, 64 dp thumbnail — library icon, single image or
 * 2×2 grid —, name, "N Songs", the 48 dp trailing column with the "open" icon — a local playlist cannot be
 * bookmarked —, divider), "Playback" (Shuffle, Play next) and "Management" in the phone's order (115-133):
 * Enqueue, download, remove all downloaded songs, rename, delete. Wired to the contract: shuffle, play next,
 * enqueue; the other entries and "open" have no contract route: shown, without action.
 * Dropped: change id, listen on YouTube, auto-sync and the Navigation section (YouTube playlists only).
 */
class LocalPlaylistItemMenu(
    private val playlist: Playlist,
    private val firstTracks: List<Track>?,
    private val actions: ItemActions,
) {

    @Composable
    fun ListMenu() = ListMenu.Menu(title = null, showDragHandle = false) {
        ListMenu.SectionTitle(stringResource(Res.string.playback))
        actions.onShuffle?.let { SongShuffler(enabled = actions.enabled, onShuffle = it).ListMenuItem() }
        PlayNext(enabled = actions.enabled, onClick = actions.onPlayNext).ListMenuItem()

        ListMenu.SectionTitle(stringResource(Res.string.management))
        Enqueue(enabled = actions.enabled, onClick = actions.onEnqueue).ListMenuItem()
        InertMenuItem(Res.drawable.downloaded, Res.string.download).ListMenuItem()
        InertMenuItem(Res.drawable.download, Res.string.info_remove_all_downloaded_songs).ListMenuItem()
        InertMenuItem(Res.drawable.title_edit, Res.string.rename_playlist).ListMenuItem()
        InertMenuItem(Res.drawable.trash, Res.string.delete).ListMenuItem()
    }

    @Composable
    private fun PlaylistItemDisplay(modifier: Modifier = Modifier) {
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
                // Playlist's thumbnail
                Box(
                    Modifier.size(Dimensions.thumbnails.album / 2),
                ) {
                    val thumbnails = playlistThumbnails(firstTracks, playlist.artworkTrackId, MENU_THUMBNAIL_SIZE_PX)

                    Box(
                        modifier = Modifier
                            .size(Dimensions.thumbnails.album / 2)
                            .clip(thumbnailShape()),
                    ) {
                        if (thumbnails.isEmpty()) {
                            Image(
                                painter = painterResource(Res.drawable.library),
                                contentDescription = null,
                                colorFilter = ColorFilter.tint(colorPalette().textSecondary),
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .size(Dimensions.thumbnails.album / 4),
                            )
                        } else if (thumbnails.size == 1) {
                            ImageCacheFactory.Thumbnail(
                                key = thumbnails[0],
                                modifier = Modifier.size(Dimensions.thumbnails.album / 2),
                            )
                        } else {
                            // 4 grid
                            Row(modifier = Modifier.size(Dimensions.thumbnails.album / 2)) {
                                Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                    ImageCacheFactory.Thumbnail(key = thumbnails[0], modifier = Modifier.weight(1f).fillMaxWidth())
                                    if (thumbnails.size > 2) {
                                        ImageCacheFactory.Thumbnail(key = thumbnails[2], modifier = Modifier.weight(1f).fillMaxWidth())
                                    }
                                }
                                Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                    if (thumbnails.size > 1) {
                                        ImageCacheFactory.Thumbnail(key = thumbnails[1], modifier = Modifier.weight(1f).fillMaxWidth())
                                    }
                                    if (thumbnails.size > 3) {
                                        ImageCacheFactory.Thumbnail(key = thumbnails[3], modifier = Modifier.weight(1f).fillMaxWidth())
                                    }
                                }
                            }
                        }
                    }
                }

                // Playlist's information
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    BasicText(
                        text = playlist.name,
                        style = typography().xs.semiBold.copy(color = colorPalette().text),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE),
                    )
                    BasicText(
                        text = "${playlist.trackCount} ${stringResource(Res.string.songs)}",
                        style = typography().xs.semiBold.secondary.copy(color = colorPalette().textSecondary),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE),
                    )
                }

                // Trailing content (Bookmark & Open): a local playlist cannot be bookmarked
                Column(
                    Modifier.width(48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    IconButton(
                        icon = Res.drawable.open,
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
            PlaylistItemDisplay()
            ListMenu()
        }
    }
}
