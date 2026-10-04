package app.n_zik.compagnon.components.menu.song

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.n_zik.compagnon.bridge.state.Track
import app.n_zik.compagnon.bridge.state.TrackSource
import app.n_zik.compagnon.colorPalette
import app.n_zik.compagnon.components.SongItem
import app.n_zik.compagnon.components.menu.InertMenuItem
import app.n_zik.compagnon.components.menu.ListMenu
import app.n_zik.compagnon.components.menu.splitArtistNames
import app.n_zik.compagnon.components.navigation.header.TabToolBar
import app.n_zik.compagnon.components.theme.favoritesIcon
import app.n_zik.compagnon.components.themed.Enqueue
import app.n_zik.compagnon.components.themed.IconButton
import app.n_zik.compagnon.components.themed.PlayNext
import app.n_zik.compagnon.components.themed.RemoveFromQueue
import app.n_zik.compagnon.components.ui.screens.home.ItemActions
import app.n_zik.compagnon.generated.resources.Res
import app.n_zik.compagnon.generated.resources.add_in_playlist
import app.n_zik.compagnon.generated.resources.add_to_favorites
import app.n_zik.compagnon.generated.resources.add_to_playlist
import app.n_zik.compagnon.generated.resources.album
import app.n_zik.compagnon.generated.resources.artists_edit
import app.n_zik.compagnon.generated.resources.cover_edit
import app.n_zik.compagnon.generated.resources.edit_metadata
import app.n_zik.compagnon.generated.resources.info_export_cached_or_downloaded_song
import app.n_zik.compagnon.generated.resources.export_outline
import app.n_zik.compagnon.generated.resources.go_to_album
import app.n_zik.compagnon.generated.resources.heart
import app.n_zik.compagnon.generated.resources.heart_outline
import app.n_zik.compagnon.generated.resources.information
import app.n_zik.compagnon.generated.resources.listen_together
import app.n_zik.compagnon.generated.resources.management
import app.n_zik.compagnon.generated.resources.more_of
import app.n_zik.compagnon.generated.resources.navigation
import app.n_zik.compagnon.generated.resources.people
import app.n_zik.compagnon.generated.resources.playback
import app.n_zik.compagnon.generated.resources.radio
import app.n_zik.compagnon.generated.resources.refresh
import app.n_zik.compagnon.generated.resources.share_social
import app.n_zik.compagnon.generated.resources.start_radio
import app.n_zik.compagnon.generated.resources.title_edit
import app.n_zik.compagnon.generated.resources.trash
import app.n_zik.compagnon.generated.resources.delete
import app.n_zik.compagnon.generated.resources.info_open_update_dialog
import app.n_zik.compagnon.generated.resources.update_album_browse_id
import app.n_zik.compagnon.generated.resources.update_artist_browse_id
import app.n_zik.compagnon.generated.resources.update_authors
import app.n_zik.compagnon.generated.resources.update_cover
import app.n_zik.compagnon.generated.resources.update_title
import app.n_zik.compagnon.utils.cleanPrefix
import org.jetbrains.compose.resources.stringResource

/**
 * Port of `SongItemMenu` (phone's `app/n_zik/android/components/menu/song/SongItemMenu.kt`), list style
 * (the phone's default `MenuStyle.List`).
 *
 * The header: drag handle, the song's `SongItem` on `background1` with its trailing column (`TOOLBAR_ICON_SIZE`
 * wide: the like heart, 20 dp with 4 dp of padding, `heart` in `favoritesIcon` when liked or `heart_outline`
 * in text, then the share icon for an online song, 700-760), the divider.
 * The sections in the phone's order (158-231):
 * - online song: Information (information), Playback (start radio, play next, enqueue), Listen Together,
 *   Management (change title / authors / cover, album / artist browse ids, add to favorites / to a playlist,
 *   update, delete, export cached), Navigation (go to album, "More of" each artist);
 * - local song: Information, Management (edit metadata), Playback (start radio, play next, enqueue, add to
 *   favorites / to a playlist), export cached.
 * Wired to the contract: play next and enqueue (`queue/add` `next` / `end`). The others have no contract
 * route: shown, without action. Dropped: the Last.fm section (scrobbling off by default) and the waveform
 * refresh (not the default timeline).
 * PC only: in the queue, [onRemoveFromQueue] adds "Remove from queue" (`queue/remove`) at the end of
 * Playback, the phone's swipe having no desktop equivalent.
 */
class SongItemMenu(
    private val song: Track,
    private val actions: ItemActions,
    private val onRemoveFromQueue: (() -> Unit)? = null,
) {

    private val isLocal: Boolean get() = song.source == TrackSource.Local

    @Composable
    fun ListMenu() = ListMenu.Menu(title = null, showDragHandle = false) {
        val playNext = PlayNext(enabled = actions.enabled, onClick = actions.onPlayNext)
        val enqueue = Enqueue(enabled = actions.enabled, onClick = actions.onEnqueue)
        val startRadio = InertMenuItem(Res.drawable.radio, Res.string.start_radio)
        val addToFavorite = InertMenuItem(Res.drawable.heart, Res.string.add_to_favorites)
        val addToPlaylist = InertMenuItem(Res.drawable.add_in_playlist, Res.string.add_to_playlist)
        val exportCache = InertMenuItem(Res.drawable.export_outline, Res.string.info_export_cached_or_downloaded_song)

        // Section: Info
        ListMenu.SectionTitle(stringResource(Res.string.information))
        InertMenuItem(Res.drawable.information, Res.string.information).ListMenuItem()

        if (isLocal) {
            // Section: Management
            ListMenu.SectionTitle(stringResource(Res.string.management))
            InertMenuItem(Res.drawable.cover_edit, Res.string.edit_metadata).ListMenuItem()

            // Section: Playback
            ListMenu.SectionTitle(stringResource(Res.string.playback))
            startRadio.ListMenuItem()
            playNext.ListMenuItem()
            enqueue.ListMenuItem()
            addToFavorite.ListMenuItem()
            addToPlaylist.ListMenuItem()
            RemoveFromQueueItem()

            exportCache.ListMenuItem()
        } else {
            // Section: Playback
            ListMenu.SectionTitle(stringResource(Res.string.playback))
            startRadio.ListMenuItem()
            playNext.ListMenuItem()
            enqueue.ListMenuItem()
            RemoveFromQueueItem()

            // Section: Listen Together
            ListMenu.SectionTitle(stringResource(Res.string.listen_together))
            InertMenuItem(Res.drawable.people, Res.string.listen_together).ListMenuItem()

            // Section: Management
            ListMenu.SectionTitle(stringResource(Res.string.management))
            InertMenuItem(Res.drawable.title_edit, Res.string.update_title).ListMenuItem()
            InertMenuItem(Res.drawable.artists_edit, Res.string.update_authors).ListMenuItem()
            InertMenuItem(Res.drawable.cover_edit, Res.string.update_cover).ListMenuItem()
            InertMenuItem(Res.drawable.title_edit, Res.string.update_album_browse_id).ListMenuItem()
            InertMenuItem(Res.drawable.title_edit, Res.string.update_artist_browse_id).ListMenuItem()
            addToFavorite.ListMenuItem()
            addToPlaylist.ListMenuItem()
            InertMenuItem(Res.drawable.refresh, Res.string.info_open_update_dialog).ListMenuItem()
            InertMenuItem(Res.drawable.trash, Res.string.delete).ListMenuItem()
            exportCache.ListMenuItem()

            // Section: Navigation
            ListMenu.SectionTitle(stringResource(Res.string.navigation))
            InertMenuItem(Res.drawable.album, Res.string.go_to_album).ListMenuItem()
            val artistNames = splitArtistNames(cleanPrefix(song.artists.orEmpty()))
            if (artistNames.size <= 1) {
                InertMenuItem(Res.drawable.people, Res.string.more_of, " ${cleanPrefix(song.artists.orEmpty())}").ListMenuItem()
            } else {
                artistNames.forEach { InertMenuItem(Res.drawable.people, Res.string.more_of, " $it").ListMenuItem() }
            }
        }
    }

    @Composable
    private fun RemoveFromQueueItem() {
        // PC only: the queue item's "Remove from queue" (the phone swipes it away)
        onRemoveFromQueue?.let { RemoveFromQueue(enabled = actions.enabled, onClick = it).ListMenuItem() }
    }

    @Composable
    fun MenuComponent() {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colorPalette().background0),
        ) {
            // Song info header
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.background(colorPalette().background1),
            ) {
                ListMenu.DragHandle(Color.White)
                SongItem(
                    song = song,
                    backgroundColor = Color.Transparent,
                    modifier = Modifier.padding(
                        top = 5.dp,
                        bottom = 10.dp,
                    ),
                    trailingContent = {
                        Column(
                            Modifier.width(TabToolBar.TOOLBAR_ICON_SIZE),
                        ) {
                            // `Track.isLiked` is a boolean: no disliked state in the contract
                            IconButton(
                                icon = if (song.isLiked) Res.drawable.heart else Res.drawable.heart_outline,
                                color = if (song.isLiked) colorPalette().favoritesIcon else colorPalette().text,
                                onClick = {},
                                modifier = Modifier.padding(all = 4.dp).size(20.dp),
                            )

                            if (!isLocal) {
                                IconButton(
                                    icon = Res.drawable.share_social,
                                    color = colorPalette().text,
                                    onClick = {},
                                    modifier = Modifier.padding(all = 4.dp).size(20.dp),
                                )
                            }
                        }
                    },
                )
                HorizontalDivider(Modifier.height(1.dp))
            }
            ListMenu()
        }
    }
}
