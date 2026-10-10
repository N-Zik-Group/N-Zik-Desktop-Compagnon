package app.n_zik.compagnon.components.ui.screens.home

import app.n_zik.compagnon.components.tab.toolbar.Randomizer
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
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
import androidx.compose.foundation.text.BasicText
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.n_zik.compagnon.bridge.library.Album
import app.n_zik.compagnon.bridge.library.AlbumSort
import app.n_zik.compagnon.bridge.library.AlbumsQuery
import app.n_zik.compagnon.bridge.library.CollectionFilter
import app.n_zik.compagnon.bridge.state.SessionContract
import app.n_zik.compagnon.components.ButtonsRow
import app.n_zik.compagnon.components.LocalMenuState
import app.n_zik.compagnon.components.Sort
import app.n_zik.compagnon.components.albumSortOptions
import app.n_zik.compagnon.components.items.AlbumItem
import app.n_zik.compagnon.components.menu.album.AlbumItemMenu
import app.n_zik.compagnon.components.navigation.header.TabToolBar
import app.n_zik.compagnon.components.styling.Dimensions
import app.n_zik.compagnon.components.styling.HomeItemSize
import app.n_zik.compagnon.components.tab.ItemSize
import app.n_zik.compagnon.components.tab.Refresh
import app.n_zik.compagnon.components.tab.TabHeader
import app.n_zik.compagnon.components.tab.toolbar.Button
import app.n_zik.compagnon.components.tab.toolbar.InertButton
import app.n_zik.compagnon.components.tab.toolbar.InertSort
import app.n_zik.compagnon.colorPalette
import app.n_zik.compagnon.components.theme.onOverlay
import app.n_zik.compagnon.components.theme.overlay
import app.n_zik.compagnon.thumbnailShape
import app.n_zik.compagnon.typography
import app.n_zik.compagnon.uiRoundnessShape
import app.n_zik.compagnon.utils.center
import app.n_zik.compagnon.utils.color
import app.n_zik.compagnon.utils.formatAsTime
import app.n_zik.compagnon.utils.semiBold
import app.n_zik.compagnon.components.themed.FloatingActionsContainerWithScrollToTop
import app.n_zik.compagnon.components.themed.HeaderInfo
import app.n_zik.compagnon.generated.resources.*
import app.n_zik.compagnon.utils.ChipSort
import app.n_zik.compagnon.utils.LocalPreferences
import app.n_zik.compagnon.utils.onSecondaryClick
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** Grid cells are `HomeItemSize.SMALL` = 100 dp: `size` ≈ 2× in px (contract §10.1). */
const val GRID_THUMBNAIL_SIZE_PX = 256

/**
 * The phone's `itemSize.size.px`: the grid thumbnails are asked at the item's own size in px (no blur on
 * the large sizes), bounded by the contract's 64-1200 px.
 */
@androidx.compose.runtime.Composable
fun gridThumbnailSizePx(itemSize: androidx.compose.ui.unit.Dp): Int =
    with(androidx.compose.ui.platform.LocalDensity.current) { itemSize.roundToPx() }.coerceIn(64, 1200)

/**
 * Port of `HomeAlbums` (phone's `app/n_zik/android/components/ui/screens/home/HomeAlbum.kt`, header
 * 440-603, grid 605-789).
 *
 * Chips: the phone's chips in the phone's order — "All" (`library`), "Favorites" (`bookmarked`),
 * "Disliked" (`disliked`, contract 1.6 — inert on an older phone, which filters that tab on its own
 * database). A click switches the list's `filter`; the chip is not persisted, and the phone's own
 * visibility/order preferences are dropped, as on the other tabs.
 *
 * Toolbar: the phone's toolbar of the active tab — the same twelve buttons for the three chips, in the
 * phone's order (`HomeAlbumsToolbarSettingsDialog.allButtonIds`), with the phone's show conditions
 * (position lock only while the chip's sort is `Custom`, no YouTube sync on the PC) — then the
 * Compagnon's "Refresh". Wired: sort, refresh, item size (the per-tab grid size, a Compagnon-local
 * setting) and the randomizer (a random album of the shown list, client-side as on the phone); the rest
 * (search — `/library/albums` has no `text` parameter —, position lock, shuffle, item selector, play next, enqueue, add to playlist,
 * export) are placeholders without a contract route —
 * the phone's search, shuffle, play next and enqueue act on the songs of all the shown (or selected) albums — a set of lists the contract cannot name in one
 * command (`/queue/list` takes one list) — and a click does nothing.
 *
 * Sort: the phone keeps one sort per tab — the chip's sort and direction live in the user settings
 * (`chipSorts`, key `albums:<chip>`), applied to the list on every change and on the first composition
 * (a cold start opens in the chip's own sort). On a phone without `library.sort` (contract < 1.6) the
 * arrow is inert (the phone sorts its tabs itself).
 *
 * The sort overlays (contract 1.7.1, the phone's `HomeAlbum.kt` 723-755): while the chip's sort is
 * the play count / listening time, the sorted value is shown over the thumbnail, like on the phone
 * (the multi-select check overlay is dropped — no contract route for the item selector).
 *
 * Dropped (contract v1): drag to reorder (the position lock is shown, inert), YouTube sync and its filter
 * chip and progress, pull-to-refresh, drag to reorder, the chip and toolbar order / visibility
 * preferences.
 * A click opens the album, a long press (right click) opens `AlbumItemMenu`. The bookmark badge comes
 * from contract 1.3 `isBookmarked`, in every filter; since 1.7.1 the contract's `isDisliked` shows
 * the phone's `bookmark_slash`.
 * Pass 5 (audit 2026-10-10): dropped too — the landscape bars toggle (`landscapeBarsToggleButton`, the
 * phone's 791: touch landscape only) and the floating multi-action icon (`showFloatingIcon`, its 798, off
 * by default; deferred). Pull-to-refresh: the toolbar's Refresh instead (no touch on the PC).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeAlbums(
    lists: LibraryLists,
    actions: LibraryActions,
    live: Boolean,
    onAlbumClick: (Album) -> Unit,
) {
    val lazyGridState = rememberLazyGridState()
    val menuState = LocalMenuState.current
    val preferences = LocalPreferences.current
    val settings = preferences?.settings?.collectAsState()?.value
    var chip by remember { mutableStateOf(AlbumsChip.All) }
    val chipSort = settings?.chipSorts?.get(chip.key) ?: ChipSort()
    val activeList = lists.activeAlbumsList()
    val state by activeList.state.collectAsState()
    val albumQuery by activeList.query.collectAsState()
    val sortsOnPhone = SessionContract.FEATURE_LIBRARY_SORT in lists.features

    // The page's grid item size (the phone's `HomeItemSize`, a per-tab Compagnon-local setting)
    val itemSize = remember { mutableStateOf(HomeItemSize.fromWire(settings?.itemSizes?.get("albums"))) }
    LaunchedEffect(settings?.itemSizes) { itemSize.value = HomeItemSize.fromWire(settings?.itemSizes?.get("albums")) }

    // Since 1.7.2 (feature `library.dislikeMode`): the phone's "disliked" mode (its `DislikeMode.Enabled`
    // per collection, read once per session by the lists) — `false` hides the Disliked chip, like on
    // the phone; `null` keeps the pre-1.7.2 display (the phone's own default: the mode enabled, the
    // chip shown)
    val dislikeMode by lists.dislikeMode.collectAsState()

    /** The chip's list query: its own sort (the persisted [sort]) and its `filter`. */
    fun applyChipQuery(target: AlbumsChip, sort: ChipSort) {
        activeList.setQuery(AlbumsQuery(target.wireFilter, sort.albumSort, sort.reverse))
    }

    /** The phone's chip change; "Disliked" is inert on a phone without `library.sort`. */
    fun selectChip(target: AlbumsChip) {
        if (target == chip) return
        if (!sortsOnPhone && target == AlbumsChip.Disliked) return
        chip = target
        lists.setActiveAlbumsChip(target)
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
            albumSortOptions,
            chipSort.albumSort,
            chipSort.reverse,
            onSortBy = { sort -> applyChipSort(chipSort.copy(sort = sort.wire)) },
            onSortDirection = { reverse -> applyChipSort(chipSort.copy(reverse = reverse)) },
        )
    } else {
        InertSort(menuState, albumSortOptions)
    }

    // The phone's toolbar of the active tab, in the phone's order, with the phone's show conditions
    // (position lock only while the chip's sort is Custom); the Compagnon's "Refresh" is added at the
    // end. Wired: sort, refresh. Placeholders (no contract route, a click does nothing): the rest.
    val buttons = buildList<Button> {
        ALBUMS_TOOLBAR_BUTTON_IDS.forEach { id ->
            when (id) {
                "sort" -> add(sortButton)
                "position_lock" -> if (chipSort.albumSort == AlbumSort.Custom) {
                    add(InertButton(Res.drawable.locked, Res.string.info_lock_unlock_reorder_songs))
                }
                // No contract search on `/library/albums` (no `text` parameter): inert, the phone's title
                "search" -> add(InertButton(Res.drawable.search_circle, Res.string.search))
                "randomizer" -> add(Randomizer({ activeList.state.value.items }, onAlbumClick))
                "shuffle" -> add(InertButton(Res.drawable.shuffle, Res.string.shuffle, descriptionId = Res.string.info_shuffle))
                "item_selector" -> add(InertButton(Res.drawable.unchecked_outline, Res.string.item_select))
                "play_next" -> add(InertButton(Res.drawable.play_skip_forward, Res.string.play_next))
                "enqueue" -> add(InertButton(Res.drawable.enqueue, Res.string.enqueue))
                "add_to_playlist" -> add(InertButton(Res.drawable.add_in_playlist, Res.string.add_to_playlist))
                "export_dialog" -> add(InertButton(Res.drawable.export_outline, Res.string.export_playlist))
                "item_size" -> add(
                    ItemSize.init(itemSize) { size ->
                        preferences?.update { s -> s.copy(itemSizes = s.itemSizes + ("albums" to size.wire)) }
                    },
                )
            }
        }
        add(Refresh(lists::reloadActiveAlbums))
    }

    // The user's chip order (the phone's `AlbumsType` labels); since 1.7.2 the phone's "disliked" mode
    // hides its Disliked chip when off (its `HomeAlbum.kt` 294)
    val chips = AlbumsChip.entries
        .filter { it != AlbumsChip.Disliked || dislikeMode?.albums != false }
        .map { it to stringResource(chipLabel(it)) }

    // The persisted sort of the visible chip, applied on the first composition (a cold start opens in
    // the chip's own sort, not the list's default query)
    LaunchedEffect(lists) { applyChipQuery(chip, settings?.chipSorts?.get(chip.key) ?: ChipSort()) }

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
                                TabHeader(stringResource(Res.string.albums)) {
                                    HeaderInfo((state.total ?: state.items.size).toString(), Res.drawable.album)
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
                    columns = GridCells.Adaptive(itemSize.value.dp),
                    contentPadding = PaddingValues(top = headerPadding, bottom = Dimensions.bottomSpacer),
                    modifier = Modifier.background(colorPalette().background0).fillMaxSize(),
                ) {
                    items(
                        items = state.items.distinctBy { it.id }.withIndex().toList(),
                        // The phone's keys: the id, duplicates dropped (`distinctBy`)
                        key = { (_, album) -> album.id },
                    ) { (_, album) ->
                        Box(modifier = Modifier) {
                            val menu: ItemActions? = actions.collectionMenuActions(CollectionHeader.OfAlbum(album).ref, live)
                            val openMenu = menu?.let {
                                {
                                    menuState.display {
                                        AlbumItemMenu(album, it).MenuComponent()
                                    }
                                }
                            }
                            AlbumItem(
                                alternative = true,
                                showAuthors = true,
                                album = album,
                                thumbnailSizeDp = itemSize.value.dp,
                                thumbnailSizePx = gridThumbnailSizePx(itemSize.value.dp),
                                thumbnailOverlay = {
                                    albumSortOverlay(albumQuery.sort, album)
                                },
                                // Contract 1.3 `isBookmarked`, in every filter (a 1.2 phone only gives it through the
                                // filter); since 1.7.1 the contract's `isDisliked` gives the phone's `bookmark_slash`
                                likeState = when {
                                    album.isDisliked -> false
                                    album.isBookmarked || albumQuery.filter == CollectionFilter.Bookmarked -> true
                                    else -> null
                                },
                                modifier = Modifier
                                    .clip(uiRoundnessShape())
                                    .onSecondaryClick(openMenu)
                                    .combinedClickable(
                                        onLongClick = { openMenu?.invoke() },
                                        onClick = { onAlbumClick(album) },
                                    )
                                    .clip(thumbnailShape()),
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

/** The phone's `AlbumsType` label of the [chip]. */
/**
 * The phone's sort overlay of the Albums grid (`HomeAlbum.kt` 723-755, since 1.7.1): the sorted value
 * over the thumbnail — the play count for [AlbumSort.PlayCount], the listening time for
 * [AlbumSort.ListeningTime].
 */
@Composable
private fun albumSortOverlay(sort: AlbumSort, album: Album) {
    val text = when (sort) {
        AlbumSort.PlayCount -> album.playCount.toString()
        AlbumSort.ListeningTime -> formatAsTime(album.totalPlayTimeMs)
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

/** The phone's `AlbumsType` label of the [chip]. */
private fun chipLabel(chip: AlbumsChip): StringResource = when (chip) {
    AlbumsChip.All -> Res.string.all
    AlbumsChip.Liked -> Res.string.favorites
    AlbumsChip.Disliked -> Res.string.disliked
}

/** The phone's default order of its Albums toolbar (`HomeAlbumsToolbarSettingsDialog.allButtonIds`). */
private val ALBUMS_TOOLBAR_BUTTON_IDS = listOf(
    "sort", "position_lock", "search", "sync", "randomizer", "shuffle", "item_selector",
    "play_next", "enqueue", "add_to_playlist", "export_dialog", "item_size",
)
