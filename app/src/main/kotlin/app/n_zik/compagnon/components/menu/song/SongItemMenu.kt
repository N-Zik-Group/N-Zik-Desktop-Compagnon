package app.n_zik.compagnon.components.menu.song

import app.n_zik.compagnon.components.player.likeToastLabel
import app.n_zik.compagnon.components.player.likeToastMessage
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.n_zik.compagnon.LocalLibraryActions
import app.n_zik.compagnon.libraryWrites
import app.n_zik.compagnon.bridge.state.SessionContract
import app.n_zik.compagnon.bridge.state.Track
import app.n_zik.compagnon.bridge.library.DislikeMode
import app.n_zik.compagnon.bridge.library.PagedState
import app.n_zik.compagnon.bridge.state.TrackLike
import app.n_zik.compagnon.bridge.state.TrackSource
import app.n_zik.compagnon.bridge.state.displayedLike
import app.n_zik.compagnon.bridge.state.nextRotation
import app.n_zik.compagnon.bridge.state.nextToggle
import app.n_zik.compagnon.colorPalette
import app.n_zik.compagnon.components.SongItem
import app.n_zik.compagnon.components.menu.InertMenuItem
import app.n_zik.compagnon.components.menu.ListMenu
import app.n_zik.compagnon.components.menu.splitArtistNames
import app.n_zik.compagnon.components.navigation.header.TabToolBar
import app.n_zik.compagnon.components.tab.toolbar.MenuIcon
import app.n_zik.compagnon.components.theme.favoritesIcon
import app.n_zik.compagnon.components.themed.AddToFavorites
import app.n_zik.compagnon.components.themed.Enqueue
import app.n_zik.compagnon.components.themed.IconButton
import app.n_zik.compagnon.components.themed.PlayNext
import app.n_zik.compagnon.components.themed.RemoveFromQueue
import app.n_zik.compagnon.components.ui.screens.home.ItemActions
import app.n_zik.compagnon.generated.resources.*
import app.n_zik.compagnon.utils.Toaster
import app.n_zik.compagnon.utils.cleanPrefix
import kotlinx.coroutines.flow.MutableStateFlow
import org.jetbrains.compose.resources.stringResource

/**
 * Port of `SongItemMenu` (phone's `app/n_zik/android/components/menu/song/SongItemMenu.kt`), list style
 * (the phone's default `MenuStyle.List`; its grid style, served by `ui.settings` since 1.10.0, is deferred).
 *
 * The header: drag handle, the song's `SongItem` on `background1` with its trailing column (`TOOLBAR_ICON_SIZE`
 * wide: the like heart, 20 dp with 4 dp of padding, `heart` in `favoritesIcon` when liked, the phone's
 * `heart_dislike` in red when disliked (since 1.7.1, read from the track's live list — a confirmed like
 * write patches it, as on the phone) or `heart_outline` in text when neutral, then the share icon for an
 * online song, 700-760), the divider.
 * The sections in the phone's order (158-231):
 * - online song: Information (information), Playback (start radio, play next, enqueue), Listen Together,
 *   Management (change title / authors / cover, album / artist browse ids, add to favorites / to a playlist,
 *   update, delete, export cached — since 1.7.2 only with the phone's `library.ffmpeg`), Navigation (go to
 *   album, "More of" each artist);
 * - local song: Information, Management (edit metadata — since 1.7.2 only with the phone's `library.ffmpeg`),
 *   Playback (start radio, play next, enqueue, add to favorites / to a playlist), export cached (same gate).
 * Wired to the contract: play next and enqueue (`queue/add` `next` / `end`), and since 1.7 the header's
 * like heart (the phone's rotation, `POST /library/songs/{id}/like`) and "Add to favorites" (an explicit
 * `liked` write) when the phone has `library.write`. The others have no contract route: shown, without
 * action. Dropped: the Last.fm section (scrobbling off by default) and the waveform refresh (not the
 * default timeline).
 * PC only: in the queue, [onRemoveFromQueue] adds "Remove from queue" (`queue/remove`) at the end of
 * Playback, the phone's swipe having no desktop equivalent. The header's share copies the phone's link
 * (`music.youtube.com/watch?v=<id>`) to the clipboard (the desktop's stand-in for the share sheet).
 * [playerMenu] draws the player's ⋮ `PlayerMenu` sections (`PlayerItemMenu.kt` 175-246) with the same header
 * (its share shown for a local song too). Without `library.write` the header heart is an indicator (no
 * write, no toast). "Add to a playlist" is NOT LINKED (no contract route; the sub-menu mechanism exists).
 */
class SongItemMenu(
    private val song: Track,
    private val actions: ItemActions,
    private val onRemoveFromQueue: (() -> Unit)? = null,
    /**
     * The player's ⋮ menu: the phone's `PlayerMenu` → `PlayerItemMenu` (`PlayerItemMenu.kt` 175-246), the
     * same header with the player's sections (equalizer, sleep timer, "Listen on"). Equalizer and sleep
     * timer are phone-side features without a contract route: shown, inert.
     */
    private val playerMenu: Boolean = false,
) {

    private val isLocal: Boolean get() = song.source == TrackSource.Local

    @Composable
    fun ListMenu() = ListMenu.Menu(title = null, showDragHandle = false) {
        val playNext = PlayNext(enabled = actions.enabled, onClick = actions.onPlayNext)
        val enqueue = Enqueue(enabled = actions.enabled, onClick = actions.onEnqueue)
        val startRadio = InertMenuItem(Res.drawable.radio, Res.string.start_radio)
        // The phone's `LikeComponent`: an explicit like (not a rotation); inert without `library.write`
        val addToFavorite: MenuIcon = libraryWrites()?.let {
            AddToFavorites(enabled = actions.enabled) {
                it.likeSong(song.id, TrackLike.Liked)
                // The phone's `LikeComponent` success toast (its `LikeComponent.kt` 39)
                Toaster.s(Res.string.done)
            }
        } ?: InertMenuItem(Res.drawable.heart, Res.string.add_to_favorites)
        val addToPlaylist = InertMenuItem(Res.drawable.add_in_playlist, Res.string.add_to_playlist)
        // Since 1.7.2 (feature `library.ffmpeg`): the phone's "edit metadata" and "export cached"
        // entries are shown only when its build ships FFmpeg, as on the phone (its `SongItemMenu.kt`
        // 545, 625)
        val hasFfmpeg = SessionContract.FEATURE_LIBRARY_FFMPEG in (LocalLibraryActions.current?.library?.features ?: emptySet())
        val exportCache = InertMenuItem(Res.drawable.export_outline, Res.string.export_cached, descriptionId = Res.string.info_export_cached_or_downloaded_song)

        // Section: Info
        ListMenu.SectionTitle(stringResource(Res.string.information))
        InertMenuItem(Res.drawable.information, Res.string.information, descriptionId = null).ListMenuItem()

        if (playerMenu) {
            val equalizer = InertMenuItem(Res.drawable.equalizer, Res.string.equalizer, descriptionId = null)
            val sleepTimer = InertMenuItem(Res.drawable.sleep, Res.string.sleep_timer, descriptionId = null)
            if (isLocal) {
                // The phone's local order: Management (edit metadata), Playback (equalizer, sleep timer,
                // favorites, playlist, album / artist ids), then delete / export
                if (hasFfmpeg) {
                    ListMenu.SectionTitle(stringResource(Res.string.management))
                    InertMenuItem(Res.drawable.cover_edit, Res.string.edit_metadata).ListMenuItem()
                }
                ListMenu.SectionTitle(stringResource(Res.string.playback))
                equalizer.ListMenuItem()
                sleepTimer.ListMenuItem()
                addToFavorite.ListMenuItem()
                addToPlaylist.ListMenuItem()
                InertMenuItem(Res.drawable.title_edit, Res.string.update_album_browse_id).ListMenuItem()
                InertMenuItem(Res.drawable.title_edit, Res.string.update_artist_browse_id).ListMenuItem()
                if (hasFfmpeg) exportCache.ListMenuItem()
            } else {
                // The phone's remote order: Playback (radio, equalizer, sleep timer), Listen Together,
                // Management (title, authors, cover, favorites, playlist, update, ids, delete), Navigation
                ListMenu.SectionTitle(stringResource(Res.string.playback))
                startRadio.ListMenuItem()
                equalizer.ListMenuItem()
                sleepTimer.ListMenuItem()
                ListMenu.SectionTitle(stringResource(Res.string.listen_together))
                InertMenuItem(Res.drawable.people, Res.string.listen_together, descriptionId = null).ListMenuItem()
                ListMenu.SectionTitle(stringResource(Res.string.management))
                InertMenuItem(Res.drawable.title_edit, Res.string.update_title).ListMenuItem()
                InertMenuItem(Res.drawable.artists_edit, Res.string.update_authors).ListMenuItem()
                InertMenuItem(Res.drawable.cover_edit, Res.string.update_cover).ListMenuItem()
                addToFavorite.ListMenuItem()
                addToPlaylist.ListMenuItem()
                InertMenuItem(Res.drawable.refresh, Res.string.update, descriptionId = Res.string.info_open_update_dialog).ListMenuItem()
                InertMenuItem(Res.drawable.title_edit, Res.string.update_album_browse_id).ListMenuItem()
                InertMenuItem(Res.drawable.title_edit, Res.string.update_artist_browse_id).ListMenuItem()
                InertMenuItem(Res.drawable.trash, Res.string.delete).ListMenuItem()
                ListMenu.SectionTitle(stringResource(Res.string.navigation))
                InertMenuItem(Res.drawable.album, Res.string.go_to_album, descriptionId = null).ListMenuItem()
                val names = splitArtistNames(cleanPrefix(song.artists.orEmpty()), listOf(stringResource(Res.string.and)))
                if (names.size <= 1) {
                    InertMenuItem(Res.drawable.people, Res.string.more_of, " ${cleanPrefix(song.artists.orEmpty())}", descriptionId = null).ListMenuItem()
                } else {
                    names.forEach { InertMenuItem(Res.drawable.people, Res.string.more_of, " $it", descriptionId = null).ListMenuItem() }
                }
                InertMenuItem(Res.drawable.play, Res.string.listen_on, descriptionId = null).ListMenuItem()
            }
            return@Menu
        }

        if (isLocal) {
            // Section: Management (the phone shows it only with its FFmpeg builds)
            if (hasFfmpeg) {
                ListMenu.SectionTitle(stringResource(Res.string.management))
                InertMenuItem(Res.drawable.cover_edit, Res.string.edit_metadata).ListMenuItem()
            }

            // Section: Playback
            ListMenu.SectionTitle(stringResource(Res.string.playback))
            startRadio.ListMenuItem()
            playNext.ListMenuItem()
            enqueue.ListMenuItem()
            addToFavorite.ListMenuItem()
            addToPlaylist.ListMenuItem()
            RemoveFromQueueItem()

            if (hasFfmpeg) exportCache.ListMenuItem()
        } else {
            // Section: Playback
            ListMenu.SectionTitle(stringResource(Res.string.playback))
            startRadio.ListMenuItem()
            playNext.ListMenuItem()
            enqueue.ListMenuItem()
            RemoveFromQueueItem()

            // Section: Listen Together
            ListMenu.SectionTitle(stringResource(Res.string.listen_together))
            InertMenuItem(Res.drawable.people, Res.string.listen_together, descriptionId = null).ListMenuItem()

            // Section: Management
            ListMenu.SectionTitle(stringResource(Res.string.management))
            InertMenuItem(Res.drawable.title_edit, Res.string.update_title).ListMenuItem()
            InertMenuItem(Res.drawable.artists_edit, Res.string.update_authors).ListMenuItem()
            InertMenuItem(Res.drawable.cover_edit, Res.string.update_cover).ListMenuItem()
            InertMenuItem(Res.drawable.title_edit, Res.string.update_album_browse_id).ListMenuItem()
            InertMenuItem(Res.drawable.title_edit, Res.string.update_artist_browse_id).ListMenuItem()
            addToFavorite.ListMenuItem()
            addToPlaylist.ListMenuItem()
            InertMenuItem(Res.drawable.refresh, Res.string.update, descriptionId = Res.string.info_open_update_dialog).ListMenuItem()
            InertMenuItem(Res.drawable.trash, Res.string.delete).ListMenuItem()
            if (hasFfmpeg) exportCache.ListMenuItem()

            // Section: Navigation
            ListMenu.SectionTitle(stringResource(Res.string.navigation))
            InertMenuItem(Res.drawable.album, Res.string.go_to_album).ListMenuItem()
            val artistNames = splitArtistNames(cleanPrefix(song.artists.orEmpty()), listOf(stringResource(Res.string.and)))
            if (artistNames.size <= 1) {
                InertMenuItem(Res.drawable.people, Res.string.more_of, " ${cleanPrefix(song.artists.orEmpty())}", descriptionId = Res.string.artists).ListMenuItem()
            } else {
                artistNames.forEach { InertMenuItem(Res.drawable.people, Res.string.more_of, " $it", descriptionId = null).ListMenuItem() }
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
                        // The player menu's header is an unconstrained Column (`PlayerItemMenu.kt` 848)
                        Column(
                            if (playerMenu) Modifier else Modifier.width(TabToolBar.TOOLBAR_ICON_SIZE),
                        ) {
                            // The like tri-state (contract 1.7), as on the phone: `heart` in `favoritesIcon`
                            // when liked, the phone's `heart_dislike` in red when disliked,
                            // `heart_outline` in text when neutral. The phone's heart rotates
                            // (neutral → liked → disliked → neutral, its default `excludeDislikedSongs`)
                            // without closing the sheet; here it writes the phone's target state. Since
                            // 1.7.1 the state follows the track's live list — the menu collects it (a
                            // confirmed write patches it, recomposing the menu, as on the phone, where
                            // the menu reads the track's own row live) — and the menu's own tap (a local
                            // optimistic update: as on the phone, the track keeps its new state in the
                            // DB even when the write drops it from the list, a chip its new state no
                            // longer matches). A `null` list (the queue, the player's own menu) keeps
                            // the track captured when the menu opened. Since 1.7.2 (feature
                            // `library.dislikeMode`): the phone's "disliked" mode off makes the tap a binary
                            // toggle (the phone's `toggleSongLikeState`, its `SongItemMenu.kt` 729-733);
                            // `null` keeps the rotation (the phone's default)
                            var likeState by remember(song.id) { mutableStateOf(song.displayedLike) }
                            val trackList by (actions.currentTrackList
                                ?: remember { MutableStateFlow(PagedState<Track>()) }).collectAsState()
                            val listedTrack = trackList.items.firstOrNull { it.id == song.id }
                            LaunchedEffect(listedTrack) {
                                if (listedTrack != null) {
                                    likeState = listedTrack.displayedLike
                                }
                            }
                            // Outside a list (the queue, the player's own menu): the player state's
                            // queued track, patched by the optimistic like and re-synced by the phone's
                            // queue delta, so a like made elsewhere while the menu is open shows too
                            val playerState by (app.n_zik.compagnon.LocalPlayerRepository.current?.state
                                ?: remember { MutableStateFlow(null) }).collectAsState()
                            val queuedLike = playerState?.queue?.firstOrNull { it.id == song.id }?.displayedLike
                            LaunchedEffect(queuedLike) {
                                if (listedTrack == null && queuedLike != null) likeState = queuedLike
                            }
                            val like = likeState
                            val writes = libraryWrites()
                            val dislikeMode by (LocalLibraryActions.current?.lists?.dislikeMode
                                ?: remember { MutableStateFlow<DislikeMode?>(null) }).collectAsState()
                            val rotationEnabled = dislikeMode?.songs != false
                            IconButton(
                                icon = when (like) {
                                    TrackLike.Liked -> Res.drawable.heart
                                    TrackLike.Disliked -> Res.drawable.heart_dislike
                                    TrackLike.Neutral -> Res.drawable.heart_outline
                                },
                                color = when (like) {
                                    TrackLike.Liked -> colorPalette().favoritesIcon
                                    TrackLike.Disliked -> colorPalette().red
                                    TrackLike.Neutral -> colorPalette().text
                                },
                                onClick = {
                                    // Without `library.write` the heart is an indicator: no write, no toast
                                    if (actions.enabled && writes != null) {
                                        val next = if (rotationEnabled) like.nextRotation() else like.nextToggle()
                                        // The phone's toast of the resulting state once its write is done (its
                                        // `YouTubeSync.kt` 106-116, 174-183): the rotation's three messages or
                                        // the toggle's two (the shared messages, title cleaned of the prefixes)
                                        val label = likeToastLabel(song.title, song.artists)
                                        writes.likeSong(song.id, next) { confirmed ->
                                            val messageId = likeToastMessage(confirmed, rotationEnabled)
                                            if (label != null) Toaster.s(messageId, label) else Toaster.s(messageId)
                                        }
                                        // The heart follows the tap even when the write drops the row
                                        // from the list (a chip its new state no longer matches)
                                        likeState = next
                                    }
                                },
                                modifier = Modifier.padding(all = 4.dp).size(20.dp),
                            )

                            // The song menu hides the share for a local song; the player menu always shows it
                            // (`PlayerItemMenu.kt` 872)
                            if (!isLocal || playerMenu) {
                                IconButton(
                                    icon = Res.drawable.share_social,
                                    color = colorPalette().text,
                                    // The phone's player menu shows it for a local track too, but its `watch?v=local:…` link is
                                    // invalid: the PC copies nothing for a local id
                                    onClick = { if (!isLocal) app.n_zik.compagnon.components.player.ShareLinks.copy(app.n_zik.compagnon.components.player.ShareLinks.song(song.id)) },
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
