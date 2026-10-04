package app.n_zik.compagnon.components.menu.playlist

import app.n_zik.compagnon.generated.resources.delete
import app.n_zik.compagnon.generated.resources.trash
import app.n_zik.compagnon.generated.resources.rename_playlist
import app.n_zik.compagnon.generated.resources.title_edit
import app.n_zik.compagnon.generated.resources.info_remove_all_downloaded_songs
import app.n_zik.compagnon.generated.resources.download
import app.n_zik.compagnon.generated.resources.downloaded
import app.n_zik.compagnon.generated.resources.open
import app.n_zik.compagnon.generated.resources.info_pin_unpin_playlist
import app.n_zik.compagnon.generated.resources.pin_filled
import app.n_zik.compagnon.components.menu.InertMenuItem
import app.n_zik.compagnon.components.themed.HeaderIconButton
import app.n_zik.compagnon.components.themed.IconButton
import androidx.compose.foundation.layout.absoluteOffset
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.n_zik.compagnon.bridge.library.Playlist
import app.n_zik.compagnon.bridge.library.PlaylistOrigin
import app.n_zik.compagnon.bridge.state.Track
import app.n_zik.compagnon.libraryWrites
import app.n_zik.compagnon.components.items.playlistThumbnails
import app.n_zik.compagnon.components.menu.ListMenu
import app.n_zik.compagnon.components.menu.album.MENU_THUMBNAIL_SIZE_PX
import app.n_zik.compagnon.components.styling.Dimensions
import app.n_zik.compagnon.components.tab.SongShuffler
import app.n_zik.compagnon.components.tab.toolbar.MenuIcon
import app.n_zik.compagnon.components.themed.PinPlaylist
import app.n_zik.compagnon.colorPalette
import app.n_zik.compagnon.utils.LocalPreferences
import app.n_zik.compagnon.utils.semiBold
import app.n_zik.compagnon.utils.UserSettings
import kotlinx.coroutines.flow.MutableStateFlow
import app.n_zik.compagnon.typography
import app.n_zik.compagnon.components.themed.Enqueue
import app.n_zik.compagnon.components.themed.PlayNext
import app.n_zik.compagnon.components.theme.favoritesIcon
import app.n_zik.compagnon.components.ui.screens.home.ItemActions
import app.n_zik.compagnon.components.ui.screens.home.LibraryActions
import app.n_zik.compagnon.core.coil.ImageCacheFactory
import app.n_zik.compagnon.core.network.ArtworkKey
import app.n_zik.compagnon.generated.resources.Res
import app.n_zik.compagnon.generated.resources.added_to_favorites
import app.n_zik.compagnon.generated.resources.bookmark
import app.n_zik.compagnon.generated.resources.bookmark_outline
import app.n_zik.compagnon.generated.resources.cannot_bookmark_special_playlist
import app.n_zik.compagnon.generated.resources.library
import app.n_zik.compagnon.generated.resources.management
import app.n_zik.compagnon.generated.resources.playback
import app.n_zik.compagnon.generated.resources.removed_from_favorites
import app.n_zik.compagnon.generated.resources.songs
import app.n_zik.compagnon.generated.resources.update_playlist_browse_id
import app.n_zik.compagnon.thumbnailShape
import app.n_zik.compagnon.utils.Toaster
import app.n_zik.compagnon.utils.secondary
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Port of `LocalPlaylistItemMenu` (phone's `app/n_zik/android/components/menu/playlist/LocalPlaylistItemMenu.kt`),
 * list style.
 *
 * Kept: the header (`PlaylistItemDisplay`: drag handle, 64 dp thumbnail — library icon, single image or
 * 2×2 grid —, name, "N Songs", the 48 dp trailing column with the "open" icon, divider) and, in the
 * phone's order, "Playback" (Shuffle, Play next) and "Management" (Enqueue, download, remove all
 * downloaded songs, rename, delete). Wired to the contract: shuffle, play next, enqueue; and since 1.7
 * "Pin/Unpin playlist" (the phone's toolbar `PinPlaylist`, `POST /library/playlists/{id}/pin`: a binary
 * toggle of the phone's `pinned:` name prefix) as the first Management entry, inert without the
 * `library.write` feature. The other entries and "open" have no contract route: shown, without action.
 * The phone's show conditions (its 479-488): rename off on the rewind playlists (their language-neutral
 * name) and on the non-editable playlists, change id and delete off on the non-editable playlists.
 * Since 1.7.2 (the wire's `browseId`): the header's bookmark toggle (the phone's `canBeBookmarked`: a
 * playlist whose browse id is not the `modified:` prefix — its `POST /library/playlists/{id}/bookmark`,
 * with the phone's "special playlists" refusal and toasts, inert without `library.write`), the change-id
 * entry (the phone's 484-486: a bookmarked YouTube playlist or a browse id starting with its
 * `modified:` / `VL` prefix, inert — no contract route) and the custom cover over the thumbnail.
 * Dropped: listen on YouTube, auto-sync and the Navigation section (YouTube playlists only).
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
        // Pin/Unpin (contract 1.7): a binary toggle of the phone's `pinned:` name prefix
        val pin: MenuIcon = libraryWrites()?.let {
            PinPlaylist(enabled = actions.enabled) { it.pinPlaylist(playlist.id, !playlist.isPinned) }
        } ?: InertMenuItem(Res.drawable.pin_filled, Res.string.info_pin_unpin_playlist)
        pin.ListMenuItem()
        Enqueue(enabled = actions.enabled, onClick = actions.onEnqueue).ListMenuItem()
        InertMenuItem(Res.drawable.downloaded, Res.string.download).ListMenuItem()
        InertMenuItem(Res.drawable.download, Res.string.info_remove_all_downloaded_songs).ListMenuItem()
        // The phone's show conditions (its 479-488): rename off on the rewind playlists (their
        // language-neutral name), change id and delete off on the non-editable playlists
        val isRewind = playlist.origin in REWIND_ORIGINS
        if (playlist.isEditable) {
            if (!isRewind) {
                InertMenuItem(Res.drawable.title_edit, Res.string.rename_playlist).ListMenuItem()
            }
            // Since 1.7.2: change id on a bookmarked YouTube playlist or one whose browse id starts
            // with the phone's "modified:" / "VL" prefix (its 484-486); inert — no contract route
            if (playlist.isBookmarked ||
                playlist.browseId?.startsWith("modified:") == true ||
                playlist.browseId?.startsWith("VL") == true
            ) {
                InertMenuItem(Res.drawable.title_edit, Res.string.update_playlist_browse_id).ListMenuItem()
            }
            InertMenuItem(Res.drawable.trash, Res.string.delete).ListMenuItem()
        }
    }

    /**
     * The phone's header bookmark (its `LocalPlaylistItemMenu.kt` 435-463, the 1.7.2 write): the "special
     * playlists" (their `browseId` minus the `VL` prefix is `LM` or `SE`) refuse it with the phone's
     * toast; otherwise the toggle, with the phone's toasts — inert without `library.write`.
     */
    private fun onBookmarkToggle(writes: LibraryActions?) {
        if (playlist.browseId?.removePrefix("VL") in listOf("LM", "SE")) {
            Toaster.e(Res.string.cannot_bookmark_special_playlist)
            return
        }
        writes?.bookmarkPlaylist(playlist.id, !playlist.isBookmarked) ?: return
        Toaster.s(if (playlist.isBookmarked) Res.string.removed_from_favorites else Res.string.added_to_favorites)
    }

    @Composable
    private fun PlaylistItemDisplay(modifier: Modifier = Modifier) {
        // The phone's "Disable scrolling text" (the phone's `LocalPlaylistItemMenu.kt` 273, 279, 286):
        // the name / songs-count marquee is dropped when set
        val preferences = LocalPreferences.current
        val settings by (preferences?.settings
            ?: remember { MutableStateFlow(UserSettings()) }).collectAsState()
        val marquee: Modifier = if (settings.disableScrollingText) Modifier else Modifier.basicMarquee(iterations = Int.MAX_VALUE)

        // The phone's `canBeBookmarked` (its `Playlist.kt` 28): a playlist whose browse id is not the
        // "modified:" prefix (`null` counts — the header bookmark of a local playlist)
        val canBookmark = playlist.browseId?.startsWith("modified:") == false

        // The §10.2 writes (since 1.7.2), resolved here (the click callback is not composable)
        val writes = libraryWrites()

        // Since 1.7.2: the phone's custom cover (its `thumbnail/playlist_<id>`), read with
        // `GET /library/playlists/{id}/artwork`; a missing one keeps the mosaic, as on the phone
        var hasCover by remember { mutableStateOf(false) }
        val coverKey = ArtworkKey.playlist(playlist.id, MENU_THUMBNAIL_SIZE_PX)
        ImageCacheFactory.Painter(
            key = coverKey,
            onSuccess = { hasCover = true },
            onError = { hasCover = false },
        )

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
                        if (hasCover) {
                            ImageCacheFactory.Thumbnail(
                                key = coverKey,
                                modifier = Modifier.size(Dimensions.thumbnails.album / 2),
                            )
                        } else if (thumbnails.isEmpty()) {
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

                    // The phone's header bookmark badge (its `LocalPlaylistItemMenu.kt` 257-265): 12 dp in
                    // `favoritesIcon` at the bottom start, outside the clip, of a bookmarked, bookmarkeable
                    // playlist
                    if (canBookmark && playlist.isBookmarked) {
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
                        modifier = marquee,
                    )
                    BasicText(
                        text = "${playlist.trackCount} ${stringResource(Res.string.songs)}",
                        style = typography().xs.semiBold.secondary.copy(color = colorPalette().textSecondary),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = marquee,
                    )
                }

                // Trailing content (Bookmark & Open): the phone's header bookmark of a bookmarkeable
                // playlist (its `canBeBookmarked`), then "open"
                Column(
                    Modifier.width(48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (canBookmark) {
                        IconButton(
                            icon = if (playlist.isBookmarked) Res.drawable.bookmark else Res.drawable.bookmark_outline,
                            color = colorPalette().favoritesIcon,
                            onClick = { onBookmarkToggle(writes) },
                            modifier = Modifier
                                .padding(all = 4.dp)
                                .size(20.dp),
                        )
                    }

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

/**
 * The phone's generated `rewind-*` playlists: since 1.7.1 the contract's [PlaylistOrigin] carries the
 * phone's name-based `RewindPlaylists.isRewind` (monthly, yearly or all-time, plus the legacy `Rewind`
 * fallback of a phone before 1.7.1).
 */
private val REWIND_ORIGINS = setOf(
    PlaylistOrigin.Rewind,
    PlaylistOrigin.RewindMonthly,
    PlaylistOrigin.RewindYearly,
    PlaylistOrigin.RewindAlltime,
)
