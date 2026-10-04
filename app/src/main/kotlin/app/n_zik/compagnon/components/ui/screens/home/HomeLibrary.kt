package app.n_zik.compagnon.components.ui.screens.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.n_zik.compagnon.bridge.library.Playlist
import app.n_zik.compagnon.bridge.library.PlaylistSort
import app.n_zik.compagnon.bridge.library.PlaylistsQuery
import app.n_zik.compagnon.bridge.library.RewindFilter
import app.n_zik.compagnon.bridge.library.RewindState
import app.n_zik.compagnon.bridge.library.SongsQuery
import app.n_zik.compagnon.bridge.state.SessionContract
import app.n_zik.compagnon.bridge.state.Track
import app.n_zik.compagnon.components.ButtonsRow
import app.n_zik.compagnon.components.LocalMenuState
import app.n_zik.compagnon.components.Sort
import app.n_zik.compagnon.components.items.PlaylistItem
import app.n_zik.compagnon.components.items.ThumbnailRenderer
import app.n_zik.compagnon.components.items.playlistThumbnails
import app.n_zik.compagnon.components.menu.ListMenu
import app.n_zik.compagnon.components.menu.playlist.LocalPlaylistItemMenu
import app.n_zik.compagnon.components.navigation.header.TabToolBar
import app.n_zik.compagnon.components.playlistSortOptions
import app.n_zik.compagnon.components.styling.Dimensions
import app.n_zik.compagnon.components.styling.HomeItemSize
import app.n_zik.compagnon.components.tab.ItemSize
import app.n_zik.compagnon.components.tab.Refresh
import app.n_zik.compagnon.components.tab.Search
import app.n_zik.compagnon.components.tab.TabHeader
import app.n_zik.compagnon.components.tab.toolbar.Button
import app.n_zik.compagnon.components.tab.toolbar.InertButton
import app.n_zik.compagnon.components.tab.toolbar.InertSort
import app.n_zik.compagnon.colorPalette
import app.n_zik.compagnon.components.theme.onOverlay
import app.n_zik.compagnon.components.theme.overlay
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
import app.n_zik.compagnon.core.coil.ImageCacheFactory
import app.n_zik.compagnon.core.network.ArtworkKey
import app.n_zik.compagnon.generated.resources.filter_by
import app.n_zik.compagnon.generated.resources.import_outline
import app.n_zik.compagnon.generated.resources.import_playlist
import app.n_zik.compagnon.generated.resources.info_lock_unlock_reorder_songs
import app.n_zik.compagnon.generated.resources.info_shuffle
import app.n_zik.compagnon.generated.resources.item_select
import app.n_zik.compagnon.generated.resources.locked
import app.n_zik.compagnon.generated.resources.no_items
import app.n_zik.compagnon.generated.resources.play_next
import app.n_zik.compagnon.generated.resources.musical_notes
import app.n_zik.compagnon.generated.resources.play_skip_forward
import app.n_zik.compagnon.generated.resources.playlist
import app.n_zik.compagnon.generated.resources.playlists
import app.n_zik.compagnon.generated.resources.pinned_playlists
import app.n_zik.compagnon.generated.resources.rewind
import app.n_zik.compagnon.generated.resources.rewind_filter_month
import app.n_zik.compagnon.generated.resources.rewind_filter_year
import app.n_zik.compagnon.generated.resources.search
import app.n_zik.compagnon.generated.resources.search_circle
import app.n_zik.compagnon.generated.resources.shuffle
import app.n_zik.compagnon.generated.resources.stat_month
import app.n_zik.compagnon.generated.resources.stat_year
import app.n_zik.compagnon.generated.resources.trash
import app.n_zik.compagnon.generated.resources.unchecked_outline
import app.n_zik.compagnon.generated.resources.yt_playlists
import app.n_zik.compagnon.thumbnailShape
import app.n_zik.compagnon.typography
import app.n_zik.compagnon.uiRoundnessShape
import app.n_zik.compagnon.utils.ChipSort
import app.n_zik.compagnon.utils.LocalPreferences
import app.n_zik.compagnon.utils.center
import app.n_zik.compagnon.utils.color
import app.n_zik.compagnon.utils.formatAsTime
import app.n_zik.compagnon.utils.onSecondaryClick
import app.n_zik.compagnon.utils.semiBold
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
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
 * the Compagnon's "Refresh". Wired: sort, refresh and item size (the per-tab grid size, a Compagnon-local
 * setting); the rest (search, shuffle, item selector, play next, enqueue, add to playlist, import,
 * export, delete playlists) are placeholders without a contract route — a click does nothing.
 *
 * Sort: the phone keeps one sort per tab — the chip's sort and direction live in the user settings
 * (`chipSorts`, key `playlists:<chip>`), applied to the list on every change and on the first
 * composition (a cold start opens in the chip's own sort). On a phone without `library.sort`
 * (contract < 1.6) the arrow is inert (the phone sorts its tabs itself).
 *
 * Kept: the collapsible header (`TabHeader` "Playlists" + count, `TabToolBar`), the adaptive grid of
 * `PlaylistItem` (2×2 mosaic of the first four tracks, read with `/songs?limit=4`, else the artwork of
 * `artworkTrackId`), "No items", the scroll-to-top button. A click opens the playlist, a long press
 * (right click) opens `LocalPlaylistItemMenu`.
 *
 * The sort overlays (contract 1.7.1, the phone's `HomeLibrary.kt` 872-919): while the chip's sort is
 * the count / play count / listening time, the sorted value is shown over the thumbnail, like on the
 * phone (the multi-select check overlay is dropped — no contract route for the item selector).
 *
 * Since 1.7.2: the phone's search (its toolbar `search` button, the header's search bar and its
 * `text`), the Rewind month / year row (only under the Rewind chip while the phone's two creation
 * toggles are on — its `GET /library/rewind` state, feature `library.rewind`; a pick is applied and
 * persisted by the phone) and the custom cover of a playlist (its `GET /library/playlists/{id}/artwork`,
 * else the mosaic).
 *
 * Dropped (contract v1): sync and its progress, drag to reorder.
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

    // The page's grid item size (the phone's `HomeItemSize`, a per-tab Compagnon-local setting)
    val itemSize = remember { mutableStateOf(HomeItemSize.fromWire(settings?.itemSizes?.get("playlists"))) }
    LaunchedEffect(settings?.itemSizes) { itemSize.value = HomeItemSize.fromWire(settings?.itemSizes?.get("playlists")) }

    // Since 1.7.2: the phone's search (its `text`, debounced in the lists)
    val playlistsSearch by lists.playlistsSearch.collectAsState()
    val search = Search(playlistsSearch, lists::onPlaylistsSearch, lazyGridState)

    // Since 1.7.2 (feature `library.rewind`): the phone's Month / Year / All row state (its two
    // creation toggles + its current filter), read on the Rewind chip and after each page (the phone
    // may have changed its filter meanwhile)
    var rewindState by remember { mutableStateOf<RewindState?>(null) }

    /** The chip's list query: its own sort (the persisted [sort]), its `filter`, the current search
     *  text and, on the Rewind chip, the phone's row filter (contract 1.7.2 — absent, the phone keeps
     *  its own setting; present, it applies and persists it). */
    fun applyChipQuery(target: PlaylistsChip, sort: ChipSort) {
        activeList.setQuery(
            PlaylistsQuery(
                filter = target.wireFilter,
                sort = sort.playlistSort,
                reverse = sort.reverse,
                rewind = if (target == PlaylistsChip.Rewind) rewindState?.filter else null,
                text = SongsQuery.normalizeText(playlistsSearch),
            ),
        )
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

    /** The phone's Month / Year / All row (contract 1.7.2): the phone applies and persists the pick. */
    fun selectRewindFilter(filter: RewindFilter) {
        val current = rewindState ?: return
        rewindState = current.copy(filter = filter)
        applyChipQuery(chip, chipSort)
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
                "search" -> add(search)
                "shuffle" -> add(InertButton(Res.drawable.shuffle, Res.string.info_shuffle))
                "item_selector" -> add(InertButton(Res.drawable.unchecked_outline, Res.string.item_select))
                "play_next" -> add(InertButton(Res.drawable.play_skip_forward, Res.string.play_next))
                "enqueue" -> add(InertButton(Res.drawable.enqueue, Res.string.enqueue))
                "add_to_playlist" -> add(InertButton(Res.drawable.add_in_playlist, Res.string.add_to_playlist))
                "import_menu" -> add(InertButton(Res.drawable.import_outline, Res.string.import_playlist))
                "export_dialog" -> add(InertButton(Res.drawable.export_outline, Res.string.export_playlist))
                "delete_playlists" -> add(InertButton(Res.drawable.trash, Res.string.delete_playlists_label))
                "item_size" -> add(
                    ItemSize.init(itemSize) { size ->
                        preferences?.update { s -> s.copy(itemSizes = s.itemSizes + ("playlists" to size.wire)) }
                    },
                )
            }
        }
        add(Refresh(lists::reloadActivePlaylists))
    }

    // The user's chip order (the phone's `PlaylistsType` labels)
    val chips = PlaylistsChip.entries.map { it to stringResource(chipLabel(it)) }

    // The persisted sort of the visible chip, applied on the first composition (a cold start opens in
    // the chip's own sort, not the list's default query)
    LaunchedEffect(lists) { applyChipQuery(chip, settings?.chipSorts?.get(chip.key) ?: ChipSort()) }

    // Since 1.7.2 (feature `library.rewind`): the phone's row state, read on the Rewind chip and after
    // each page; `null` hides the row (the phone's own `rowVisible`: both creation toggles on)
    LaunchedEffect(lists, chip, state.total) {
        rewindState = if (chip == PlaylistsChip.Rewind && SessionContract.FEATURE_LIBRARY_REWIND in lists.features) {
            lists.rewindState()
        } else {
            null
        }
    }

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

                            // Since 1.7.2 (feature `library.rewind`, the phone's `HomeLibrary.kt` 659-729):
                            // the phone's Month / Year / All row, only under the Rewind chip while its two
                            // creation toggles are on (its `rowVisible`)
                            val rewindFilterMonthLabel = stringResource(Res.string.rewind_filter_month)
                            val rewindFilterYearLabel = stringResource(Res.string.rewind_filter_year)
                            val rewindFilterAllLabel = stringResource(Res.string.all)
                            AnimatedVisibility(
                                visible = chip == PlaylistsChip.Rewind && rewindState?.rowVisible == true,
                                enter = fadeIn(animationSpec = tween(200)) + expandVertically(animationSpec = tween(200)),
                                exit = fadeOut(animationSpec = tween(200)) + shrinkVertically(animationSpec = tween(200)),
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .padding(horizontal = 16.dp)
                                        .padding(bottom = 8.dp)
                                        .fillMaxWidth(),
                                ) {
                                    FilterChip(
                                        label = {
                                            Text(
                                                text = when (rewindState?.filter) {
                                                    RewindFilter.Year -> rewindFilterYearLabel
                                                    RewindFilter.All -> rewindFilterAllLabel
                                                    else -> rewindFilterMonthLabel
                                                },
                                            )
                                        },
                                        selected = true,
                                        shape = uiRoundnessShape(),
                                        colors = FilterChipDefaults.filterChipColors(
                                            containerColor = colorPalette().background1,
                                            labelColor = colorPalette().text,
                                            selectedContainerColor = colorPalette().accent,
                                            selectedLabelColor = colorPalette().onAccent,
                                        ),
                                        onClick = {
                                            menuState.display {
                                                ListMenu.Menu(title = stringResource(Res.string.filter_by)) {
                                                    ListMenu.Entry(
                                                        text = rewindFilterMonthLabel,
                                                        icon = { RewindFilterIcon(Res.drawable.stat_month) },
                                                        onClick = {
                                                            menuState.hide()
                                                            selectRewindFilter(RewindFilter.Month)
                                                        },
                                                    )
                                                    ListMenu.Entry(
                                                        text = rewindFilterYearLabel,
                                                        icon = { RewindFilterIcon(Res.drawable.stat_year) },
                                                        onClick = {
                                                            menuState.hide()
                                                            selectRewindFilter(RewindFilter.Year)
                                                        },
                                                    )
                                                    ListMenu.Entry(
                                                        text = rewindFilterAllLabel,
                                                        icon = { RewindFilterIcon(Res.drawable.musical_notes) },
                                                        onClick = {
                                                            menuState.hide()
                                                            selectRewindFilter(RewindFilter.All)
                                                        },
                                                    )
                                                }
                                            }
                                        },
                                    )
                                }
                            }
                        }

                        search.SearchBar()
                    }
                },
            ) { headerPadding ->
                LazyVerticalGrid(
                    state = lazyGridState,
                    columns = GridCells.Adaptive(itemSize.value.dp),
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
                                    // Since 1.7.2: the phone's custom cover, else the mosaic
                                    playlistCover(preview.id) {
                                        ThumbnailRenderer(playlistThumbnails(tracks, preview.artworkTrackId, GRID_THUMBNAIL_SIZE_PX))
                                    }
                                },
                                thumbnailOverlay = {
                                    playlistSortOverlay(chipSort.playlistSort, preview)
                                },
                                songCount = preview.trackCount,
                                name = preview.name,
                                origin = preview.origin,
                                isPinned = preview.isPinned,
                                isBookmarked = preview.isBookmarked,
                                // The phone's grid cards carry no lock badge (themed card only)
                                thumbnailSizeDp = itemSize.value.dp,
                                alternative = true,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(uiRoundnessShape())
                                    .onSecondaryClick(openMenu)
                                    .combinedClickable(
                                        onClick = {
                                            search.hideIfEmpty()
                                            onPlaylistClick(preview, tracks)
                                        },
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

/**
 * The phone's sort overlay of the Playlists grid (`HomeLibrary.kt` 872-919, since 1.7.1): the sorted
 * value over the thumbnail — the track count for [PlaylistSort.SongCount], the play count for
 * [PlaylistSort.PlayCount], the listening time for [PlaylistSort.ListeningTime].
 */
@Composable
private fun playlistSortOverlay(sort: PlaylistSort, preview: Playlist) {
    val text = when (sort) {
        PlaylistSort.SongCount -> preview.trackCount.toString()
        PlaylistSort.PlayCount -> preview.playCount.toString()
        PlaylistSort.ListeningTime -> formatAsTime(preview.totalPlayTimeMs)
        else -> null
    } ?: return
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(thumbnailShape())
            .background(colorPalette().overlay),
    ) {
        BasicText(
            text = text,
            style = typography().s.semiBold.center.color(colorPalette().onOverlay),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.Center)
                .basicMarquee(iterations = Int.MAX_VALUE),
        )
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

/**
 * Since 1.7.2 (contract §10.1): the phone's custom cover of the playlist [id] (its
 * `thumbnail/playlist_<id>`), read with `GET /library/playlists/{id}/artwork`; while it is unknown and
 * a missing one (a `404`, no `artwork` feature) fall back to [mosaic], as on the phone.
 */
@Composable
private fun BoxWithConstraintsScope.playlistCover(id: String, mosaic: @Composable () -> Unit) {
    var hasCover by remember { mutableStateOf(false) }
    val painter = ImageCacheFactory.Painter(
        key = ArtworkKey.playlist(id, GRID_THUMBNAIL_SIZE_PX),
        onSuccess = { hasCover = true },
        onError = { hasCover = false },
    )
    if (hasCover) {
        Image(
            painter = painter,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    } else {
        mosaic()
    }
}

/** The phone's `RewindFilterIcon` (`HomeLibrary.kt` 198): the row filter's icon, accent on a 10% accent square. */
@Composable
private fun RewindFilterIcon(drawableRes: DrawableResource) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .background(
                color = colorPalette().accent.copy(alpha = 0.1f),
                shape = uiRoundnessShape(),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(drawableRes),
            tint = colorPalette().accent,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
    }
}
