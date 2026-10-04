package app.n_zik.compagnon.components.ui.screens.localplaylist

import app.n_zik.compagnon.components.themed.FloatingActionsContainerWithScrollToTop
import app.n_zik.compagnon.uiRoundnessShape
import app.n_zik.compagnon.generated.resources.smart_shuffle
import app.n_zik.compagnon.components.themed.HeaderIconButton
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import app.n_zik.compagnon.bridge.library.LibraryRepository
import app.n_zik.compagnon.bridge.library.PlaylistSongsQuery
import app.n_zik.compagnon.bridge.state.SessionContract
import app.n_zik.compagnon.components.LocalMenuState
import app.n_zik.compagnon.components.SongItem
import app.n_zik.compagnon.components.Sort
import app.n_zik.compagnon.components.SortOption
import app.n_zik.compagnon.components.playlistSongSortOptions
import app.n_zik.compagnon.components.items.playlistThumbnails
import app.n_zik.compagnon.components.menu.song.SongItemMenu
import app.n_zik.compagnon.components.navigation.header.TabToolBar
import app.n_zik.compagnon.components.styling.Dimensions
import app.n_zik.compagnon.components.tab.Locator
import app.n_zik.compagnon.components.tab.SongShuffler
import app.n_zik.compagnon.components.tab.toolbar.Button
import app.n_zik.compagnon.components.tab.toolbar.InertButton
import app.n_zik.compagnon.colorPalette
import app.n_zik.compagnon.utils.semiBold
import app.n_zik.compagnon.typography
import app.n_zik.compagnon.components.themed.Enqueue
import app.n_zik.compagnon.components.themed.HeaderWithIcon
import app.n_zik.compagnon.components.themed.IconInfo
import app.n_zik.compagnon.components.themed.PlayNext
import app.n_zik.compagnon.components.themed.Playlist
import app.n_zik.compagnon.components.ui.screens.home.CollectionHeader
import app.n_zik.compagnon.components.ui.screens.home.LibraryActions
import app.n_zik.compagnon.components.ui.screens.home.LoadMoreEffect
import app.n_zik.compagnon.components.ui.screens.home.PagedStatus
import app.n_zik.compagnon.components.ui.screens.home.rememberPlaylistSongs
import app.n_zik.compagnon.generated.resources.Res
import app.n_zik.compagnon.generated.resources.add_in_playlist
import app.n_zik.compagnon.generated.resources.add_to_favorites
import app.n_zik.compagnon.generated.resources.add_to_playlist
import app.n_zik.compagnon.generated.resources.alert
import app.n_zik.compagnon.generated.resources.delete
import app.n_zik.compagnon.generated.resources.download
import app.n_zik.compagnon.generated.resources.downloaded
import app.n_zik.compagnon.generated.resources.edit_thumbnail
import app.n_zik.compagnon.generated.resources.enqueue
import app.n_zik.compagnon.generated.resources.export_outline
import app.n_zik.compagnon.generated.resources.export_playlist
import app.n_zik.compagnon.generated.resources.heart
import app.n_zik.compagnon.generated.resources.image
import app.n_zik.compagnon.generated.resources.import_outline
import app.n_zik.compagnon.generated.resources.import_playlist
import app.n_zik.compagnon.generated.resources.info_download_all_songs
import app.n_zik.compagnon.generated.resources.info_lock_unlock_reorder_songs
import app.n_zik.compagnon.generated.resources.info_open_update_dialog
import app.n_zik.compagnon.generated.resources.info_pin_unpin_playlist
import app.n_zik.compagnon.generated.resources.info_remove_all_downloaded_songs
import app.n_zik.compagnon.generated.resources.item_select
import app.n_zik.compagnon.generated.resources.library_empty
import app.n_zik.compagnon.generated.resources.locked
import app.n_zik.compagnon.generated.resources.match_album_audio_version
import app.n_zik.compagnon.generated.resources.musical_notes
import app.n_zik.compagnon.generated.resources.pin_filled
import app.n_zik.compagnon.generated.resources.play_next
import app.n_zik.compagnon.generated.resources.play_skip_forward
import app.n_zik.compagnon.generated.resources.playlist
import app.n_zik.compagnon.generated.resources.position
import app.n_zik.compagnon.generated.resources.renumber_songs_positions
import app.n_zik.compagnon.generated.resources.refresh
import app.n_zik.compagnon.generated.resources.relative_listening_time
import app.n_zik.compagnon.generated.resources.rename_playlist
import app.n_zik.compagnon.generated.resources.rewind_top_sort
import app.n_zik.compagnon.generated.resources.reset_thumbnail
import app.n_zik.compagnon.generated.resources.search
import app.n_zik.compagnon.generated.resources.search_circle
import app.n_zik.compagnon.generated.resources.sort_album
import app.n_zik.compagnon.generated.resources.sort_album_year
import app.n_zik.compagnon.generated.resources.sort_artist
import app.n_zik.compagnon.generated.resources.sort_artist_and_album
import app.n_zik.compagnon.generated.resources.sort_custom_order
import app.n_zik.compagnon.generated.resources.sort_date_added
import app.n_zik.compagnon.generated.resources.sort_date_liked
import app.n_zik.compagnon.generated.resources.sort_date_played
import app.n_zik.compagnon.generated.resources.sort_downloaded
import app.n_zik.compagnon.generated.resources.sort_duration
import app.n_zik.compagnon.generated.resources.sort_listening_time
import app.n_zik.compagnon.generated.resources.sort_play_count
import app.n_zik.compagnon.generated.resources.sort_title
import app.n_zik.compagnon.generated.resources.title_edit
import app.n_zik.compagnon.generated.resources.trash
import app.n_zik.compagnon.generated.resources.time
import app.n_zik.compagnon.generated.resources.unchecked_outline
import app.n_zik.compagnon.thumbnailShape
import app.n_zik.compagnon.utils.ChipSort
import app.n_zik.compagnon.utils.LocalPreferences
import app.n_zik.compagnon.utils.formatAsTime
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.StringResource

/** The playlist card's cover is `Dimensions.thumbnails.playlist` = 128 dp: `size` ≈ 2× in px. */
private const val PLAYLIST_CARD_SIZE_PX = 256

/**
 * Port of `LocalPlaylistSongs` (phone's `app/it/fast4x/rimusic/ui/screens/localplaylist/LocalPlaylistSongs.kt`
 * 1164-1560).
 *
 * Kept: the `HeaderWithIcon` title (no icon); the `background1` card in thumbnail shape (16 dp sides) with
 * the `Playlist` cover of 128 dp (14 dp above, mosaic of the first four tracks or the artwork of
 * `artworkTrackId`, count pill), the info column (track count with the note icon, duration with the clock
 * icon, 10 / 5 / 30 dp spacers, 80 % or 90 % in landscape) and the column of buttons: the smart-shuffle
 * button in its 48 dp box (recommendations off: `textDisabled`, no contract route so no action), 10 dp,
 * Shuffle (`LocalPlaylistSongs.kt` 1290-1320); the `TabToolBar` in the phone's default order
 * (`LocalPlaylistToolbarSettingsDialog.allButtonIds`); the row with the phone's sort arrow at its start
 * (contract §10.1, since 1.6 `library.sort`: `sort` and `reverse` are sent to the phone, which re-sorts;
 * a phone before 1.6 has no route for the sort, so the arrow is hidden) and the locator at its end;
 * the `SongItem` list, its `Dimensions.bottomSpacer` footer and the scroll-to-top button
 * (`FloatingActionsContainerWithScrollToTop`, 1575-1583).
 * Sort: the phone's per-playlist sort (`rememberPreference("PlaylistSongsSortBy_<id>")`, default `Title`),
 * persisted in the user settings (key `playlistsongs:<id>`). As on the phone (its `PlaylistSongsSort`),
 * the arrow carries the current sort's name, and the generated `rewind-*` playlists (detected by name,
 * the phone's `RewindPlaylists.isRewind`) default to their top order — the phone's rewind-only "Rewind Top"
 * option, sent as the phone's position order (a live sort never rewrites it).
 * Toolbar: the phone's twenty-one buttons in the phone's order
 * (`LocalPlaylistToolbarSettingsDialog.allButtonIds`), with the phone's show conditions — pin off on the
 * monthly-type rewind playlists, position lock and renumber only while the sort is `Custom` and off on the
 * rewind playlists, "match" only while some track is not matched to the phone's library (the phone checks
 * its 11-digit database ids and a zero-duration sentinel the contract carries no play-time for, so only
 * the id check; the phone's `local:` on-device files stay excluded), rename off on the rewind playlists,
 * and sync / listen on YouTube only with a YouTube browse id (none on the PC). Play next and enqueue are
 * wired to the contract; the rest are placeholders without a contract route, a click does nothing.
 * Dropped (contract v1 or PC): smart recommendations (counter, related songs), bookmark, swipe actions
 * (drag to reorder), the play-time overlays, the phone's auto-sync on open.
 * Added by the Compagnon: the paging row, "Nothing here." for an empty playlist. The duration shows once
 * all tracks are loaded.
 */
@Composable
fun LocalPlaylistSongs(
    header: CollectionHeader.OfPlaylist,
    library: LibraryRepository,
    actions: LibraryActions,
    live: Boolean,
    onMessage: (String) -> Unit,
    onBack: () -> Unit,
) {
    val playlist = header.playlist
    val lazyListState = rememberLazyListState()
    val menuState = LocalMenuState.current
    val scope = rememberCoroutineScope()
    val preferences = LocalPreferences.current
    val settings = preferences?.settings?.collectAsState()?.value
    val list = rememberPlaylistSongs(library, header.ref, onBack)
    val state by list.state.collectAsState()
    val items = state.items
    LoadMoreEffect(list, state, { lazyListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 })
    // The phone's rewind playlists keep their top order in the database: the name carries it (the
    // phone's `RewindPlaylists.isRewind`), which gates their toolbar buttons and their sort default
    val isRewind = isRewindPlaylist(playlist.name)
    // The phone's per-playlist sort (`rememberPreference("PlaylistSongsSortBy_<id>")`, default
    // `Title`; a rewind playlist with no saved sort defaults to its top order) — persisted in the
    // user settings, key `playlistsongs:<id>`
    val sortKey = "playlistsongs:${playlist.id}"
    val chipSort = settings?.chipSorts?.get(sortKey) ?: if (isRewind) ChipSort(sort = "rewindTop") else ChipSort()

    /** The phone's sort menu: the 14 wire values, plus the rewind-only "Rewind Top" (the phone's
     *  `PlaylistSongsSort.getEnumConstants`); the names are the phone's local sort names. */
    val sortOptions: List<SortOption<String>> = playlistSongSortOptions.map {
        SortOption(it.labelId, it.iconId, it.value?.wire)
    } + if (isRewind) listOf(SortOption(Res.string.rewind_top_sort, Res.drawable.position, "rewindTop")) else emptyList()

    /** The sort changed: persisted per playlist (the phone keeps one sort per playlist), then sent —
     *  "Rewind Top" asks the phone's position order (the rewind playlists' top order, never rewritten). */
    fun applySort(sort: ChipSort) {
        preferences?.update { it.copy(chipSorts = it.chipSorts + (sortKey to sort)) }
        list.setQuery(PlaylistSongsQuery(sort.playlistSongSort, sort.reverse))
    }

    // The phone's own sort (contract §10.1, since 1.6 `library.sort`): `sort` and `reverse` are sent
    // to the phone, which re-sorts. As on the phone, the arrow carries the current sort's name
    // (`PlaylistSongsSort`'s `ToolBarButton` override). A phone before 1.6 lists its tracks in
    // position order, and the contract then had no route for the sort — the arrow is hidden
    val songSort: Sort<String>? = if (SessionContract.FEATURE_LIBRARY_SORT in library.features) {
        Sort(
            menuState,
            sortOptions,
            chipSort.sort,
            chipSort.reverse,
            onSortBy = { name -> applySort(chipSort.copy(sort = name)) },
            onSortDirection = { reverse -> applySort(chipSort.copy(reverse = reverse)) },
            currentLabel = {
                BasicText(
                    text = stringResource(sortLabel(chipSort.sort)),
                    style = typography().s.semiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
        )
    } else {
        null
    }

    val collection = actions.collectionActions(header.ref, live)
    val playbackEnabled = live && collection != null
    val shuffle = SongShuffler(enabled = playbackEnabled) { collection?.onShuffle?.invoke() }
    val playNext = PlayNext(enabled = playbackEnabled) { collection?.onPlayNext?.invoke() }
    val enqueue = Enqueue(enabled = playbackEnabled) { collection?.onEnqueue?.invoke() }
    val locator = Locator(lazyListState, { list.state.value.items }, indexOffset = 1) { id ->
        scope.launch { onMessage(getString(id)) }
    }
    // The phone's "match" button appears while some track is not matched to the phone's library: the
    // phone checks its 11-digit database ids and a zero-duration sentinel the contract carries no
    // play-time for, so only the id check (the phone's `local:` on-device files stay excluded)
    val hasUnmatchedSongs = items.any { it.id.length != 11 && !it.id.startsWith("local:") }

    // The phone's toolbar in the phone's order (`LocalPlaylistToolbarSettingsDialog.allButtonIds`),
    // with the phone's show conditions (pin / position lock / renumber / rename off on the rewind
    // playlists; position lock and renumber only while the sort is `Custom`; sync and listen on
    // YouTube need a YouTube browse id: none on the PC). Play next and enqueue are wired to the
    // contract; the rest are placeholders without a contract route (no action)
    val toolbarButtons = buildList<Button> {
        LOCAL_PLAYLIST_TOOLBAR_BUTTON_IDS.forEach { id ->
            when (id) {
                "pin" -> if (!playlist.name.startsWith("rewind-monthly:", true) && !isRewind) {
                    add(InertButton(Res.drawable.pin_filled, Res.string.info_pin_unpin_playlist))
                }
                "search" -> add(InertButton(Res.drawable.search_circle, Res.string.search))
                "position_lock" -> if (chipSort.sort == "custom" && !isRewind) {
                    add(InertButton(Res.drawable.locked, Res.string.info_lock_unlock_reorder_songs))
                }
                "match" -> if (hasUnmatchedSongs) {
                    add(InertButton(Res.drawable.alert, Res.string.match_album_audio_version))
                }
                "renumber" -> if (chipSort.sort == "custom" && !isRewind) {
                    add(InertButton(Res.drawable.position, Res.string.renumber_songs_positions))
                }
                "download_all" -> add(InertButton(Res.drawable.downloaded, Res.string.info_download_all_songs))
                "delete_downloads" -> add(InertButton(Res.drawable.download, Res.string.info_remove_all_downloaded_songs))
                "item_selector" -> add(InertButton(Res.drawable.unchecked_outline, Res.string.item_select))
                "play_next" -> if (collection != null) add(playNext) else add(InertButton(Res.drawable.play_skip_forward, Res.string.play_next))
                "enqueue" -> if (collection != null) add(enqueue) else add(InertButton(Res.drawable.enqueue, Res.string.enqueue))
                "add_to_favorite" -> add(InertButton(Res.drawable.heart, Res.string.add_to_favorites))
                "add_to_playlist" -> add(InertButton(Res.drawable.add_in_playlist, Res.string.add_to_playlist))
                // "sync" and "listen_on_yt" only exist with a YouTube browse id: none on the PC
                "import_menu" -> add(InertButton(Res.drawable.import_outline, Res.string.import_playlist))
                "rename" -> if (!isRewind) add(InertButton(Res.drawable.title_edit, Res.string.rename_playlist))
                "delete" -> add(InertButton(Res.drawable.trash, Res.string.delete))
                "export" -> add(InertButton(Res.drawable.export_outline, Res.string.export_playlist))
                "thumbnail_picker" -> add(InertButton(Res.drawable.image, Res.string.edit_thumbnail))
                "reset_thumbnail" -> add(InertButton(Res.drawable.image, Res.string.reset_thumbnail))
                "update" -> add(InertButton(Res.drawable.refresh, Res.string.info_open_update_dialog))
            }
        }
    }
    val thumbnails = playlistThumbnails(header.firstTracks ?: items.take(4).takeIf { it.size == 4 }, playlist.artworkTrackId, PLAYLIST_CARD_SIZE_PX)

    Box(
        modifier = Modifier
            .background(colorPalette().background0)
            .fillMaxHeight()
            .fillMaxWidth(),
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val isLandscape = maxWidth > maxHeight
            LazyColumn(
                state = lazyListState,
                modifier = Modifier
                    .background(colorPalette().background0)
                    .fillMaxSize(),
            ) {
                item(
                    key = "header",
                    contentType = 0,
                ) {
                    Column {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth(),
                        ) {
                            HeaderWithIcon(
                                title = playlist.name,
                                iconId = Res.drawable.playlist,
                                enabled = true,
                                showIcon = false,
                                modifier = Modifier
                                    .padding(bottom = 8.dp),
                                onClick = {},
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.Start,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .background(
                                    color = colorPalette().background1,
                                    shape = thumbnailShape(),
                                ),
                        ) {
                            Playlist(
                                name = playlist.name,
                                songCount = state.total ?: playlist.trackCount,
                                thumbnails = thumbnails,
                                thumbnailSizeDp = Dimensions.thumbnails.playlist,
                                alternative = true,
                                showName = false,
                                modifier = Modifier
                                    .padding(top = 14.dp),
                            )

                            Column(
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.Start,
                                modifier = Modifier
                                    .padding(end = 10.dp)
                                    .fillMaxWidth(if (isLandscape) 0.90f else 0.80f),
                            ) {
                                Spacer(modifier = Modifier.height(10.dp))
                                IconInfo(
                                    title = (state.total ?: playlist.trackCount).toString(),
                                    icon = painterResource(Res.drawable.musical_notes),
                                )
                                Spacer(modifier = Modifier.height(5.dp))

                                IconInfo(
                                    title = if (state.endReached) formatAsTime(items.sumOf { it.durationMs ?: 0L }) else "…",
                                    icon = painterResource(Res.drawable.time),
                                )
                                Spacer(modifier = Modifier.height(30.dp))
                            }

                            if (collection != null) {
                                Column(
                                    verticalArrangement = Arrangement.Center,
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Box(
                                        modifier = Modifier.size(48.dp), // Standard IconButton size
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        // isRecommendationEnabled = false (the default)
                                        HeaderIconButton(
                                            icon = Res.drawable.smart_shuffle,
                                            enabled = true,
                                            color = colorPalette().textDisabled,
                                            modifier = Modifier.clip(uiRoundnessShape()),
                                            onClick = {},
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(10.dp))
                                    shuffle.ToolBarButton()
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        TabToolBar.Buttons(toolbarButtons)

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .padding(horizontal = 10.dp)
                                .fillMaxWidth(),
                        ) {
                            // The phone's sort arrow, at the start of the locator row (contract 1.6 `library.sort`)
                            songSort?.ToolBarButton()
                            Row(
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth(),
                            ) { locator.ToolBarButton() }
                        }
                    }
                }

                itemsIndexed(
                    items = items,
                    key = { index, song -> "$index:${song.id}" },
                ) { index, song ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .zIndex(2f),
                    ) {
                        val menu = actions.trackActions({ list.state.value.items }, index, song.id, live)
                        SongItem(
                            song = song,
                            modifier = Modifier,
                            onLongClick = menu?.let { { menuState.display { SongItemMenu(song, it).MenuComponent() } } },
                            onClick = {
                                if (live && actions.available) actions.playFrom(list.state.value.items, index, song.id)
                            },
                        )
                    }
                }

                item(key = "status") {
                    if (state.endReached && items.isEmpty()) {
                        BasicText(
                            text = stringResource(Res.string.library_empty),
                            style = typography().xs.semiBold.copy(color = colorPalette().textSecondary, textAlign = TextAlign.Center),
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                        )
                    }
                    PagedStatus(state, onRetry = list::retry)
                }

                item(
                    key = "footer",
                    contentType = 0,
                ) {
                    Spacer(modifier = Modifier.height(Dimensions.bottomSpacer))
                }
            }
        }

        FloatingActionsContainerWithScrollToTop(lazyListState = lazyListState)
    }
}

/**
 * The phone's `RewindPlaylists.isRewind` (name-based, the phone's generated names): a monthly
 * (`rewind-monthly:YYYYMM`), a yearly (`rewind-yearly:YYYY`) or the all-time (`rewind-alltime`)
 * playlist.
 */
private fun isRewindPlaylist(name: String): Boolean =
    name.startsWith("rewind-monthly:", ignoreCase = true) ||
        name.startsWith("rewind-yearly:", ignoreCase = true) ||
        name.equals("rewind-alltime", ignoreCase = true)

/** The phone's `PlaylistSongSortBy.text` of the stored sort name (its menu label). */
private fun sortLabel(name: String): StringResource = when (name) {
    "title" -> Res.string.sort_title
    "artist" -> Res.string.sort_artist
    "album" -> Res.string.sort_album
    "artistAndAlbum" -> Res.string.sort_artist_and_album
    "duration" -> Res.string.sort_duration
    "playCount" -> Res.string.sort_play_count
    "playTime" -> Res.string.sort_listening_time
    "relativePlayTime" -> Res.string.relative_listening_time
    "dateAdded" -> Res.string.sort_date_added
    "datePlayed" -> Res.string.sort_date_played
    "dateLiked" -> Res.string.sort_date_liked
    "albumYear" -> Res.string.sort_album_year
    "downloaded" -> Res.string.sort_downloaded
    "custom" -> Res.string.sort_custom_order
    else -> Res.string.rewind_top_sort
}

/** The phone's default order (`LocalPlaylistToolbarSettingsDialog.allButtonIds`). */
private val LOCAL_PLAYLIST_TOOLBAR_BUTTON_IDS = listOf(
    "pin", "search", "position_lock", "match", "renumber",
    "download_all", "delete_downloads",
    "item_selector",
    "play_next", "enqueue", "add_to_favorite", "add_to_playlist",
    "sync", "listen_on_yt",
    "import_menu", "rename", "delete", "export",
    "thumbnail_picker", "reset_thumbnail", "update",
)
