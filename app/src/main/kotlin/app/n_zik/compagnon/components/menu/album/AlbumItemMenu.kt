package app.n_zik.compagnon.components.menu.album

import app.n_zik.compagnon.generated.resources.*
import app.n_zik.compagnon.components.menu.splitArtistNames
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
import app.n_zik.compagnon.bridge.library.Album
import app.n_zik.compagnon.bridge.library.AlbumLike
import app.n_zik.compagnon.bridge.library.DislikeMode
import app.n_zik.compagnon.bridge.library.PagedState
import app.n_zik.compagnon.bridge.library.nextRotation
import app.n_zik.compagnon.bridge.library.nextToggle
import app.n_zik.compagnon.components.menu.ListMenu
import app.n_zik.compagnon.components.styling.Dimensions
import app.n_zik.compagnon.components.theme.favoritesIcon
import app.n_zik.compagnon.components.tab.SongShuffler
import app.n_zik.compagnon.colorPalette
import app.n_zik.compagnon.utils.LocalPreferences
import app.n_zik.compagnon.utils.UserSettings
import app.n_zik.compagnon.utils.semiBold
import app.n_zik.compagnon.typography
import app.n_zik.compagnon.components.themed.Enqueue
import app.n_zik.compagnon.components.themed.HeaderIconButton
import app.n_zik.compagnon.components.themed.PlayNext
import app.n_zik.compagnon.components.ui.screens.home.ItemActions
import app.n_zik.compagnon.core.coil.ImageCacheFactory
import app.n_zik.compagnon.core.network.ArtworkKey
import app.n_zik.compagnon.thumbnailShape
import app.n_zik.compagnon.utils.Toaster
import app.n_zik.compagnon.utils.secondary
import kotlinx.coroutines.flow.MutableStateFlow
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
 * artist, then "Change Title": the phone's index ranges put it there). Entries without a contract route
 * are shown without action. Since 1.7.2, the bookmark icon is the phone's tri-state (its
 * `AlbumItemMenu.kt` 332-399: `bookmark` in `favoritesIcon` when bookmarked, the phone's
 * `bookmark_slash` in red when disliked, `bookmark_outline` in text when neutral — with the phone's
 * 12 dp badge of a non-neutral album on the thumbnail): a tap rotates the state (neutral → bookmarked
 * → disliked → neutral, the phone's `rotateLikeState`) or, with the phone's "disliked" mode off
 * (feature `library.dislikeMode`), toggles it, and writes the phone's target state (`POST
 * /library/albums/{id}/like`, with the phone's toasts); a phone before 1.7.2 keeps its 1.7 binary
 * route; without the `library.write` feature it stays an inert indicator (no icon change, no toast).
 * The share icon copies the phone's fallback link (`music.youtube.com/browse/<id>`, its 405-414: the
 * album's `shareUrl` is not in the contract) to the clipboard — the desktop's share.
 */
class AlbumItemMenu(
    private val album: Album,
    private val actions: ItemActions,
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
        InertMenuItem(Res.drawable.downloaded, Res.string.download, descriptionId = Res.string.info_download_all_songs).ListMenuItem()
        InertMenuItem(Res.drawable.download, Res.string.info_remove_all_downloaded_songs).ListMenuItem()
        InertMenuItem(Res.drawable.artists_edit, Res.string.update_authors).ListMenuItem()
        InertMenuItem(Res.drawable.cover_edit, Res.string.update_cover).ListMenuItem()
        InertMenuItem(Res.drawable.title_edit, Res.string.update_album_browse_id).ListMenuItem()
        InertMenuItem(Res.drawable.title_edit, Res.string.update_artist_browse_id).ListMenuItem()

        // Section: Navigation
        ListMenu.SectionTitle(stringResource(Res.string.navigation))
        val artistNames = splitArtistNames(album.artists, listOf(stringResource(Res.string.and)))
        if (artistNames.size <= 1) {
            InertMenuItem(Res.drawable.people, Res.string.more_of, " ${album.artists.orEmpty()}", descriptionId = Res.string.artists).ListMenuItem()
        } else {
            artistNames.forEach { InertMenuItem(Res.drawable.people, Res.string.more_of, " $it", descriptionId = Res.string.artists).ListMenuItem() }
        }
        InertMenuItem(Res.drawable.title_edit, Res.string.update_title).ListMenuItem()
    }

    @Composable
    private fun AlbumItemDisplay(
        album: Album,
        modifier: Modifier = Modifier,
    ) {
        // The PC's "Disable scrolling text" (the phone's `disableScrollingTextKey`, its
        // `AlbumItemMenu.kt` 291, 303): the title / artists marquees are dropped when set
        val preferences = LocalPreferences.current
        val settings by (preferences?.settings ?: remember { MutableStateFlow(UserSettings()) }).collectAsState()
        val marquee: Modifier = if (settings.disableScrollingText) Modifier else Modifier.basicMarquee(iterations = Int.MAX_VALUE)

        // The like tri-state, since 1.7: the phone reads it live from the DB; here it is seeded from the
        // album and re-synced from the Albums list (the confirmed §10.2 writes patch it), so the icon
        // follows the tap while the menu is open
        val library = LocalLibraryActions.current
        var likeState by remember(album.id) {
            mutableStateOf(
                when {
                    album.isDisliked -> AlbumLike.Disliked
                    album.isBookmarked -> AlbumLike.Bookmarked
                    else -> AlbumLike.Neutral
                },
            )
        }
        val albumsState by (library?.lists?.albums?.state ?: remember { MutableStateFlow(PagedState<Album>()) })
            .collectAsState()
        val listedAlbum = albumsState.items.firstOrNull { it.id == album.id }
        LaunchedEffect(listedAlbum) {
            if (listedAlbum != null) {
                likeState = when {
                    listedAlbum.isDisliked -> AlbumLike.Disliked
                    listedAlbum.isBookmarked -> AlbumLike.Bookmarked
                    else -> AlbumLike.Neutral
                }
            }
        }
        val writes = library?.takeIf { it.canWrite }
        // Since 1.7.2 (feature `library.dislikeMode`): the phone's "disliked" mode off makes the tap a
        // binary toggle (the phone's `toggleBookmark`); a phone before 1.7.2 keeps its 1.7 binary route
        val dislikeMode by (library?.lists?.dislikeMode ?: remember { MutableStateFlow<DislikeMode?>(null) })
            .collectAsState()

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

                    // The phone's badge (its `AlbumItemMenu.kt` 261-275): 12 dp at the bottom start of a
                    // non-neutral album — `bookmark` in `favoritesIcon` when bookmarked, the phone's
                    // `bookmark_slash` in red when disliked
                    if (likeState != AlbumLike.Neutral) {
                        HeaderIconButton(
                            onClick = {},
                            icon = if (likeState == AlbumLike.Disliked) Res.drawable.bookmark_slash else Res.drawable.bookmark,
                            color = if (likeState == AlbumLike.Disliked) colorPalette().red else colorPalette().favoritesIcon,
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
                        modifier = marquee,
                    )

                    album.artists?.let {
                        BasicText(
                            text = it,
                            style = typography().xs.semiBold.secondary.copy(
                                color = colorPalette().textSecondary,
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = marquee,
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
                    // The like tri-state (contract 1.7.2, the phone's `AlbumItemMenu.kt` 332-399): a tap
                    // rotates the state (neutral → bookmarked → disliked → neutral, the phone's
                    // `rotateLikeState`) or, with the phone's "disliked" mode off, toggles it (the phone's
                    // `toggleBookmark`) and writes the phone's target state (`POST
                    // /library/albums/{id}/like`; a phone before 1.7.2 keeps its binary route); inert
                    // without `library.write`
                    IconButton(
                        icon = when (likeState) {
                            AlbumLike.Bookmarked -> Res.drawable.bookmark
                            AlbumLike.Disliked -> Res.drawable.bookmark_slash
                            AlbumLike.Neutral -> Res.drawable.bookmark_outline
                        },
                        color = when (likeState) {
                            AlbumLike.Bookmarked -> colorPalette().favoritesIcon
                            AlbumLike.Disliked -> colorPalette().red
                            AlbumLike.Neutral -> colorPalette().text
                        },
                        onClick = {
                            // Inert without `library.write` (no icon change, no toast: nothing is written)
                            if (actions.enabled && writes != null) {
                                val mode = dislikeMode
                                val target = if (mode == null) {
                                    likeState.nextToggle()
                                } else if (mode.albums) {
                                    likeState.nextRotation()
                                } else {
                                    likeState.nextToggle()
                                }
                                likeState = target
                                if (mode == null) {
                                    writes?.bookmarkAlbum(album.id, target == AlbumLike.Bookmarked)
                                } else {
                                    writes?.likeAlbum(album.id, target)
                                }
                                // The phone's toast (its `AlbumItemMenu.kt` 380-398)
                                val messageId = when (target) {
                                    AlbumLike.Bookmarked -> Res.string.added_to_favorites
                                    AlbumLike.Disliked -> Res.string.added_to_dislikes
                                    AlbumLike.Neutral -> Res.string.removed_from_favorites
                                }
                                if (album.title.isNotBlank()) Toaster.s(messageId, "\"${album.title}\"") else Toaster.s(messageId)
                            }
                        },
                        modifier = Modifier
                            .padding(all = 4.dp)
                            .size(20.dp),
                    )

                    // The phone's share (its 405-414): `shareUrl`, absent from the contract, so the
                    // phone's fallback `music.youtube.com/browse/<id>`, copied to the clipboard
                    IconButton(
                        icon = Res.drawable.share_social,
                        color = colorPalette().text,
                        onClick = {
                            app.n_zik.compagnon.components.player.ShareLinks.copy(
                                app.n_zik.compagnon.components.player.ShareLinks.album(album.id),
                            )
                        },
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
