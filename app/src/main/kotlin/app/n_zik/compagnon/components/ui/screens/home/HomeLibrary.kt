package app.n_zik.compagnon.components.ui.screens.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
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
import androidx.compose.ui.unit.dp
import app.n_zik.compagnon.bridge.library.Playlist
import app.n_zik.compagnon.bridge.library.PlaylistSort
import app.n_zik.compagnon.bridge.library.PlaylistsQuery
import app.n_zik.compagnon.bridge.state.SessionContract
import app.n_zik.compagnon.bridge.state.Track
import app.n_zik.compagnon.components.ButtonsRow
import app.n_zik.compagnon.components.LocalMenuState
import app.n_zik.compagnon.components.Sort
import app.n_zik.compagnon.components.items.PlaylistItem
import app.n_zik.compagnon.components.items.ThumbnailRenderer
import app.n_zik.compagnon.components.items.playlistThumbnails
import app.n_zik.compagnon.components.menu.playlist.LocalPlaylistItemMenu
import app.n_zik.compagnon.components.navigation.header.TabToolBar
import app.n_zik.compagnon.components.playlistSortOptions
import app.n_zik.compagnon.components.styling.Dimensions
import app.n_zik.compagnon.components.styling.HOME_ITEM_SIZE_SMALL
import app.n_zik.compagnon.components.tab.Refresh
import app.n_zik.compagnon.components.tab.TabHeader
import app.n_zik.compagnon.components.tab.toolbar.Button
import app.n_zik.compagnon.components.tab.toolbar.InertButton
import app.n_zik.compagnon.components.tab.toolbar.InertSort
import app.n_zik.compagnon.colorPalette
import app.n_zik.compagnon.components.themed.FloatingActionsContainerWithScrollToTop
import app.n_zik.compagnon.components.themed.HeaderInfo
import app.n_zik.compagnon.generated.resources.Res
import app.n_zik.compagnon.generated.resources.add_in_playlist
import app.n_zik.compagnon.generated.resources.add_to_playlist
import app.n_zik.compagnon.generated.resources.all
import app.n_zik.compagnon.generated.resources.delete_playlists_label
import app.n_zik.compagnon.generated.resources.enqueue
import app.n_zik.compagnon.generated.resources.export_outline
import app.n_zik.compagnon.generated.resources.export_playlist
import app.n_zik.compagnon.generated.resources.import_outline
import app.n_zik.compagnon.generated.resources.import_playlist
import app.n_zik.compagnon.generated.resources.info_lock_unlock_reorder_songs
import app.n_zik.compagnon.generated.resources.info_shuffle
import app.n_zik.compagnon.generated.resources.item_select
import app.n_zik.compagnon.generated.resources.locked
import app.n_zik.compagnon.generated.resources.no_items
import app.n_zik.compagnon.generated.resources.play_next
import app.n_zik.compagnon.generated.resources.play_skip_forward
import app.n_zik.compagnon.generated.resources.playlist
import app.n_zik.compagnon.generated.resources.playlists
import app.n_zik.compagnon.generated.resources.pinned_playlists
import app.n_zik.compagnon.generated.resources.rewind
import app.n_zik.compagnon.generated.resources.resize
import app.n_zik.compagnon.generated.resources.search
import app.n_zik.compagnon.generated.resources.search_circle
import app.n_zik.compagnon.generated.resources.shuffle
import app.n_zik.compagnon.generated.resources.size
import app.n_zik.compagnon.generated.resources.trash
import app.n_zik.compagnon.generated.resources.unchecked_outline
import app.n_zik.compagnon.generated.resources.yt_playlists
import app.n_zik.compagnon.uiRoundnessShape
import app.n_zik.compagnon.utils.ChipSort
import app.n_zik.compagnon.utils.LocalPreferences
import app.n_zik.compagnon.utils.onSecondaryClick
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Port of `HomeLibrary` (phone's `app/n_zik/android/components/ui/screens/home/HomeLibrary.kt`, the
 * phone's "Playlists" tab; header 601-762, grid 764-952).
 *
 * Chips: the phone's playlist types in the phone's order (`playlistsDefaultOrder`) — "All"
 * (`all`), "Pinned" (`pinned`), "Rewind" (`rewind`), "YT/YTM" (`youtube`; the `filter` of contract §10,
 * since 1.6). A click switches the list's `filter`; on a phone without `library.sort` (contract < 1.6)
 * only "All" is served (its `/library/playlists` ignores the unknown `filter`), so the other chips are
 * inert there. The chip is not persisted, and the phone's own visibility/order preferences are dropped,
 * as on the other tabs.
 *
 * Toolbar: the phone's toolbar of the active tab — the same thirteen buttons for the four chips, in
 * the phone's order (`HomeLibraryToolbarSettingsDialog.allButtonIds`), with the phone's show
 * conditions (position lock only while the chip's sort is `Custom`, no YouTube sync on the PC) — then
 * the Compagnon's "Refresh". Wired: sort and refresh; the rest (search, shuffle, item selector, play
 * next, enqueue, add to playlist, import, export, delete playlists, item size) are placeholders
 * without a contract route — a click does nothing.
 *
 * Sort: the phone keeps one sort per tab — the chip's sort and direction live in the user settings
 * (`chipSorts`, key `playlists:<chip>`), applied to the list on every change. On a phone without
 * `library.sort` (contract < 1.6) the arrow is inert (the phone sorts its tabs itself).
 *
 * Kept: the collapsible header (`TabHeader` "Playlists" + count, `TabToolBar`), the adaptive grid of
 * `PlaylistItem` (2×2 mosaic of the first four tracks, read with `/songs?limit=4`, else the artwork of
 * `artworkTrackId`), "No items", the scroll-to-top button. A click opens the playlist, a long press
 * (right click) opens `LocalPlaylistItemMenu`.
 *
 * Dropped (contract v1): sync and its progress, drag to reorder, the overlays of the count / play-count /
 * listening-time sorts, the Rewind month / year row (only under the Rewind chip, the phone's own DataStore
 * setting: no contract route).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeLibrary(
    lists: LibraryLists,
    actions: LibraryActions,
    live: Boolean,
    onPlaylistClick: (Playlist, List<Track>?) -> Unit,
) {
    val lazyGridState = rememberLazyGridState()
    val menuState = LocalMenuState.current
    val preferences = LocalPreferences.current
    val settings = preferences?.settings?.collectAsState()?.value
    var chip by remember { mutableStateOf(PlaylistsChip.All) }
    val chipSort = settings?.chipSorts?.get(chip.key) ?: ChipSort()
    val activeList = lists.activePlaylistsList()
    val state by activeList.state.collectAsState()
    val playlistQuery by activeList.query.collectAsState()
    val firstTracks by lists.playlistFirstTracks.collectAsState()
    val sortsOnPhone = SessionContract.FEATURE_LIBRARY_SORT in lists.features

    /** The chip's list query: its own sort (the persisted [sort]) and its `filter`. */
    fun applyChipQuery(target: PlaylistsChip, sort: ChipSort) {
        activeList.setQuery(PlaylistsQuery(target.wireFilter, sort.playlistSort, sort.reverse))
    }

    /** The phone's chip change; "All" is the only `filter` a phone without `library.sort` serves —
     *  its other chips are inert there (its `/library/playlists` ignores the unknown parameter). */
    fun selectChip(target: PlaylistsChip) {
        if (target == chip) return
        if (!sortsOnPhone && target != PlaylistsChip.All) return
        chip = target
        lists.setActivePlaylistsChip(target)
        applyChipQuery(target, settings?.chipSorts?.get(target.key) ?: ChipSort())
    }

    /** The chip's sort changed: persisted (the phone keeps one sort per tab), then applied. */
    fun applyChipSort(sort: ChipSort) {
        preferences?.update { it.copy(chipSorts = it.chipSorts + (chip.key to sort)) }
        applyChipQuery(chip, sort)
    }

    // The chip's sort (the phone's per-tab sort); on a phone without `library.sort` the inert arrow
    val sortButton: Button = if (sortsOnPhone) {
        Sort(
            menuState,
            playlistSortOptions,
            chipSort.playlistSort,
            chipSort.reverse,
            onSortBy = { sort -> applyChipSort(chipSort.copy(sort = sort.wire)) },
            onSortDirection = { reverse -> applyChipSort(chipSort.copy(reverse = reverse)) },
        )
    } else {
        InertSort(menuState, playlistSortOptions)
    }

    // The phone's toolbar of the active tab, in the phone's order, with the phone's show conditions
    // (position lock only while the chip's sort is Custom); the Compagnon's "Refresh" is added at the
    // end. Wired: sort, refresh. Placeholders (no contract route, a click does nothing): the rest.
    val buttons = buildList<Button> {
        PLAYLISTS_TOOLBAR_BUTTON_IDS.forEach { id ->
            when (id) {
                "sort" -> add(sortButton)
                "position_lock" -> if (chipSort.playlistSort == PlaylistSort.Custom) {
                    add(InertButton(Res.drawable.locked, Res.string.info_lock_unlock_reorder_songs))
                }
                "search" -> add(InertButton(Res.drawable.search_circle, Res.string.search))
                "shuffle" -> add(InertButton(Res.drawable.shuffle, Res.string.info_shuffle))
                "item_selector" -> add(InertButton(Res.drawable.unchecked_outline, Res.string.item_select))
                "play_next" -> add(InertButton(Res.drawable.play_skip_forward, Res.string.play_next))
                "enqueue" -> add(InertButton(Res.drawable.enqueue, Res.string.enqueue))
                "add_to_playlist" -> add(InertButton(Res.drawable.add_in_playlist, Res.string.add_to_playlist))
                "import_menu" -> add(InertButton(Res.drawable.import_outline, Res.string.import_playlist))
                "export_dialog" -> add(InertButton(Res.drawable.export_outline, Res.string.export_playlist))
                "delete_playlists" -> add(InertButton(Res.drawable.trash, Res.string.delete_playlists_label))
                "item_size" -> add(InertButton(Res.drawable.resize, Res.string.size))
            }
        }
        add(Refresh(lists::reloadActivePlaylists))
    }

    // The user's chip order (the phone's `PlaylistsType` labels)
    val chips = PlaylistsChip.entries.map { it to stringResource(chipLabel(it)) }

    LoadMoreEffect(activeList, state, { lazyGridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 })

    Box(
        modifier = Modifier
            .background(colorPalette().background0)
            .fillMaxHeight()
            .fillMaxWidth(),
    ) {
        Column(Modifier.fillMaxSize()) {
            CollapsibleHeaderScreen(
                enabled = state.items.isNotEmpty(),
                header = { titleOffsetState, titleHeightState ->
                    Column {
                        Column {
                            CollapsibleTitleRow(titleOffsetState, titleHeightState) {
                                TabHeader(stringResource(Res.string.playlists)) {
                                    HeaderInfo((state.total ?: state.items.size).toString(), Res.drawable.playlist)
                                }
                            }
                            TabToolBar.Buttons(buttons, disableAnimation = true)
                        }

                        Column {
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .padding(horizontal = 16.dp)
                                    .padding(bottom = 8.dp)
                                    .fillMaxWidth(),
                            ) {
                                ButtonsRow(
                                    chips = chips,
                                    currentValue = chip,
                                    onValueUpdate = { selectChip(it) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                },
            ) { headerPadding ->
                LazyVerticalGrid(
                    state = lazyGridState,
                    columns = GridCells.Adaptive(HOME_ITEM_SIZE_SMALL),
                    modifier = Modifier
                        .background(colorPalette().background0)
                        .fillMaxSize(),
                    contentPadding = PaddingValues(top = headerPadding, bottom = Dimensions.bottomSpacer),
                ) {
                    items(
                        items = state.items.withIndex().toList(),
                        key = { (index, preview) -> "$index:${preview.id}" },
                    ) { (_, preview) ->
                        LaunchedEffect(preview.id) { lists.loadPlaylistFirstTracks(preview.id) }
                        val tracks = firstTracks[preview.id]
                        Box(modifier = Modifier) {
                            val menu = actions.collectionActions(CollectionHeader.OfPlaylist(preview, tracks).ref, live)
                            val openMenu = menu?.let {
                                { menuState.display { LocalPlaylistItemMenu(preview, tracks, it).MenuComponent() } }
                            }
                            PlaylistItem(
                                thumbnailContent = {
                                    ThumbnailRenderer(playlistThumbnails(tracks, preview.artworkTrackId, GRID_THUMBNAIL_SIZE_PX))
                                },
                                songCount = preview.trackCount,
                                name = preview.name,
                                thumbnailSizeDp = HOME_ITEM_SIZE_SMALL,
                                alternative = true,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(uiRoundnessShape())
                                    .onSecondaryClick(openMenu)
                                    .combinedClickable(
                                        onClick = { onPlaylistClick(preview, tracks) },
                                        onLongClick = { openMenu?.invoke() },
                                    ),
                            )
                        }
                    }
                    item(key = "status", span = { GridItemSpan(maxLineSpan) }) {
                        PagedStatus(state, onRetry = activeList::retry)
                    }
                }

                if (!state.loading && state.error == null && state.items.isEmpty() && state.total != null) {
                    NoItems(stringResource(Res.string.no_items))
                }
            }
        }

        FloatingActionsContainerWithScrollToTop(lazyGridState = lazyGridState)
    }
}

/** The phone's `PlaylistsType` label of the [chip]. */
private fun chipLabel(chip: PlaylistsChip): StringResource = when (chip) {
    PlaylistsChip.All -> Res.string.all
    PlaylistsChip.Pinned -> Res.string.pinned_playlists
    PlaylistsChip.Rewind -> Res.string.rewind
    PlaylistsChip.Youtube -> Res.string.yt_playlists
}

/** The phone's default order of its Playlists toolbar (`HomeLibraryToolbarSettingsDialog.allButtonIds`). */
private val PLAYLISTS_TOOLBAR_BUTTON_IDS = listOf(
    "sort", "position_lock", "search", "sync", "shuffle", "item_selector",
    "play_next", "enqueue", "add_to_playlist", "import_menu", "export_dialog", "delete_playlists", "item_size",
)
