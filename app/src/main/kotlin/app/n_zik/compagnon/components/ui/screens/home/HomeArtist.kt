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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import app.n_zik.compagnon.bridge.library.Artist
import app.n_zik.compagnon.bridge.library.ArtistSort
import app.n_zik.compagnon.bridge.library.ArtistsQuery
import app.n_zik.compagnon.bridge.library.CollectionFilter
import app.n_zik.compagnon.bridge.state.SessionContract
import app.n_zik.compagnon.components.ButtonsRow
import app.n_zik.compagnon.components.LocalMenuState
import app.n_zik.compagnon.components.Sort
import app.n_zik.compagnon.components.artistSortOptions
import app.n_zik.compagnon.components.items.ArtistItem
import app.n_zik.compagnon.components.menu.artist.LocalArtistItemMenu
import app.n_zik.compagnon.components.navigation.header.TabToolBar
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
import app.n_zik.compagnon.generated.resources.artists
import app.n_zik.compagnon.generated.resources.dice
import app.n_zik.compagnon.generated.resources.disliked
import app.n_zik.compagnon.generated.resources.enqueue
import app.n_zik.compagnon.generated.resources.export_outline
import app.n_zik.compagnon.generated.resources.export_playlist
import app.n_zik.compagnon.generated.resources.favorites
import app.n_zik.compagnon.generated.resources.info_lock_unlock_reorder_songs
import app.n_zik.compagnon.generated.resources.info_shuffle
import app.n_zik.compagnon.generated.resources.item_select
import app.n_zik.compagnon.generated.resources.locked
import app.n_zik.compagnon.generated.resources.no_items
import app.n_zik.compagnon.generated.resources.people
import app.n_zik.compagnon.generated.resources.play_next
import app.n_zik.compagnon.generated.resources.play_skip_forward
import app.n_zik.compagnon.generated.resources.randomizer
import app.n_zik.compagnon.generated.resources.resize
import app.n_zik.compagnon.generated.resources.search
import app.n_zik.compagnon.generated.resources.search_circle
import app.n_zik.compagnon.generated.resources.shuffle
import app.n_zik.compagnon.generated.resources.size
import app.n_zik.compagnon.generated.resources.unchecked_outline
import app.n_zik.compagnon.utils.ChipSort
import app.n_zik.compagnon.uiRoundnessShape
import app.n_zik.compagnon.utils.LocalPreferences
import app.n_zik.compagnon.utils.onSecondaryClick
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Port of `HomeArtists` (phone's `app/n_zik/android/components/ui/screens/home/HomeArtist.kt`, header
 * 433-585, grid 587-…): as `HomeAlbums`, with `ArtistItem` (round thumbnail) and `LocalArtistItemMenu`.
 *
 * Chips: the phone's chips in the phone's order — "All" (`library`), "Favorites" (`bookmarked`),
 * "Disliked" (`disliked`, contract 1.6 — inert on an older phone, which filters that tab on its own
 * database). A click switches the list's `filter`; the chip is not persisted, and the phone's own
 * visibility/order preferences are dropped, as on the other tabs.
 *
 * Toolbar: the phone's toolbar of the active tab — the same twelve buttons for the three chips, in the
 * phone's order (`HomeArtistsToolbarSettingsDialog.allButtonIds`), with the phone's show conditions
 * (position lock only while the chip's sort is `Custom`, no YouTube sync on the PC) — then the
 * Compagnon's "Refresh". Wired: sort and refresh; the rest (search, randomizer, shuffle, item selector,
 * play next, enqueue, add to playlist, export, item size) are placeholders without a contract route —
 * the phone's search, shuffle, play next and enqueue act on the artist's own songs, which the contract
 * does not serve, and a click does nothing.
 *
 * Sort: the phone keeps one sort per tab — the chip's sort and direction live in the user settings
 * (`chipSorts`, key `artists:<chip>`), applied to the list on every change. On a phone without
 * `library.sort` (contract < 1.6) the arrow is inert (the phone sorts its tabs itself).
 *
 * Dropped (contract v1): as `HomeAlbums`.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeArtists(
    lists: LibraryLists,
    actions: LibraryActions,
    live: Boolean,
    onArtistClick: (Artist) -> Unit,
) {
    val lazyGridState = rememberLazyGridState()
    val menuState = LocalMenuState.current
    val preferences = LocalPreferences.current
    val settings = preferences?.settings?.collectAsState()?.value
    var chip by remember { mutableStateOf(ArtistsChip.All) }
    val chipSort = settings?.chipSorts?.get(chip.key) ?: ChipSort()
    val activeList = lists.activeArtistsList()
    val state by activeList.state.collectAsState()
    val artistQuery by activeList.query.collectAsState()
    val sortsOnPhone = SessionContract.FEATURE_LIBRARY_SORT in lists.features

    /** The chip's list query: its own sort (the persisted [sort]) and its `filter`. */
    fun applyChipQuery(target: ArtistsChip, sort: ChipSort) {
        activeList.setQuery(ArtistsQuery(target.wireFilter, sort.artistSort, sort.reverse))
    }

    /** The phone's chip change; "Disliked" is inert on a phone without `library.sort`. */
    fun selectChip(target: ArtistsChip) {
        if (target == chip) return
        if (!sortsOnPhone && target == ArtistsChip.Disliked) return
        chip = target
        lists.setActiveArtistsChip(target)
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
            artistSortOptions,
            chipSort.artistSort,
            chipSort.reverse,
            onSortBy = { sort -> applyChipSort(chipSort.copy(sort = sort.wire)) },
            onSortDirection = { reverse -> applyChipSort(chipSort.copy(reverse = reverse)) },
        )
    } else {
        InertSort(menuState, artistSortOptions)
    }

    // The phone's toolbar of the active tab, in the phone's order, with the phone's show conditions
    // (position lock only while the chip's sort is Custom); the Compagnon's "Refresh" is added at the
    // end. Wired: sort, refresh. Placeholders (no contract route, a click does nothing): the rest.
    val buttons = buildList<Button> {
        ARTISTS_TOOLBAR_BUTTON_IDS.forEach { id ->
            when (id) {
                "sort" -> add(sortButton)
                "position_lock" -> if (chipSort.artistSort == ArtistSort.Custom) {
                    add(InertButton(Res.drawable.locked, Res.string.info_lock_unlock_reorder_songs))
                }
                "search" -> add(InertButton(Res.drawable.search_circle, Res.string.search))
                "randomizer" -> add(InertButton(Res.drawable.dice, Res.string.randomizer))
                "shuffle" -> add(InertButton(Res.drawable.shuffle, Res.string.info_shuffle))
                "item_selector" -> add(InertButton(Res.drawable.unchecked_outline, Res.string.item_select))
                "play_next" -> add(InertButton(Res.drawable.play_skip_forward, Res.string.play_next))
                "enqueue" -> add(InertButton(Res.drawable.enqueue, Res.string.enqueue))
                "add_to_playlist" -> add(InertButton(Res.drawable.add_in_playlist, Res.string.add_to_playlist))
                "export_dialog" -> add(InertButton(Res.drawable.export_outline, Res.string.export_playlist))
                "item_size" -> add(InertButton(Res.drawable.resize, Res.string.size))
            }
        }
        add(Refresh(lists::reloadActiveArtists))
    }

    // The user's chip order (the phone's `ArtistsType` labels)
    val chips = ArtistsChip.entries.map { it to stringResource(chipLabel(it)) }

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
                                TabHeader(stringResource(Res.string.artists)) {
                                    HeaderInfo((state.total ?: state.items.size).toString(), Res.drawable.people)
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
                    modifier = Modifier.background(colorPalette().background0).fillMaxSize(),
                    contentPadding = PaddingValues(top = headerPadding, bottom = Dimensions.bottomSpacer),
                ) {
                    items(
                        items = state.items.withIndex().toList(),
                        key = { (index, artist) -> "$index:${artist.id}" },
                    ) { (_, artist) ->
                        Box(modifier = Modifier) {
                            val menu = actions.collectionActions(CollectionHeader.OfArtist(artist).ref, live)
                            val openMenu = menu?.let {
                                {
                                    menuState.display {
                                        LocalArtistItemMenu(artist, it, bookmarked = artist.isBookmarked || artistQuery.filter == CollectionFilter.Bookmarked).MenuComponent()
                                    }
                                }
                            }
                            ArtistItem(
                                artist = artist,
                                thumbnailSizeDp = HOME_ITEM_SIZE_SMALL,
                                thumbnailSizePx = GRID_THUMBNAIL_SIZE_PX,
                                alternative = true,
                                // Contract 1.3 `isBookmarked`, in every filter (a 1.2 phone only gives it through the filter)
                                likeState = if (artist.isBookmarked || artistQuery.filter == CollectionFilter.Bookmarked) true else null,
                                modifier = Modifier.clip(uiRoundnessShape())
                                    .onSecondaryClick(openMenu)
                                    .combinedClickable(
                                        onClick = { onArtistClick(artist) },
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

/** The phone's `ArtistsType` label of the [chip]. */
private fun chipLabel(chip: ArtistsChip): StringResource = when (chip) {
    ArtistsChip.All -> Res.string.all
    ArtistsChip.Liked -> Res.string.favorites
    ArtistsChip.Disliked -> Res.string.disliked
}

/** The phone's default order of its Artists toolbar (`HomeArtistsToolbarSettingsDialog.allButtonIds`). */
private val ARTISTS_TOOLBAR_BUTTON_IDS = listOf(
    "sort", "position_lock", "search", "sync", "randomizer", "shuffle", "item_selector",
    "play_next", "enqueue", "add_to_playlist", "export_dialog", "item_size",
)
