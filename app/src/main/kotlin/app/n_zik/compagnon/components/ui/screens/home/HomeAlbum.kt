package app.n_zik.compagnon.components.ui.screens.home

import app.n_zik.compagnon.components.tab.toolbar.HomeToolbars
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import app.n_zik.compagnon.bridge.library.Album
import app.n_zik.compagnon.bridge.library.CollectionFilter
import app.n_zik.compagnon.components.ButtonsRow
import app.n_zik.compagnon.components.LocalMenuState
import app.n_zik.compagnon.components.items.AlbumItem
import app.n_zik.compagnon.components.menu.album.AlbumItemMenu
import app.n_zik.compagnon.components.navigation.header.TabToolBar
import app.n_zik.compagnon.components.styling.Dimensions
import app.n_zik.compagnon.components.styling.HOME_ITEM_SIZE_SMALL
import app.n_zik.compagnon.components.tab.Refresh
import app.n_zik.compagnon.components.tab.TabHeader
import app.n_zik.compagnon.colorPalette
import app.n_zik.compagnon.uiRoundnessShape
import app.n_zik.compagnon.components.themed.FloatingActionsContainerWithScrollToTop
import app.n_zik.compagnon.components.themed.HeaderInfo
import app.n_zik.compagnon.generated.resources.Res
import app.n_zik.compagnon.generated.resources.album
import app.n_zik.compagnon.generated.resources.albums
import app.n_zik.compagnon.generated.resources.all
import app.n_zik.compagnon.generated.resources.favorites
import app.n_zik.compagnon.generated.resources.no_items
import app.n_zik.compagnon.thumbnailShape
import app.n_zik.compagnon.utils.onSecondaryClick
import org.jetbrains.compose.resources.stringResource

/** Grid cells are `HomeItemSize.SMALL` = 100 dp: `size` ≈ 2× in px (contract §10.1). */
const val GRID_THUMBNAIL_SIZE_PX = 256

/**
 * Port of `HomeAlbums` (phone's `app/n_zik/android/components/ui/screens/home/HomeAlbum.kt`, header 497-603,
 * grid 605-789).
 *
 * Kept: the collapsible header (`TabHeader` "Albums" + count, `TabToolBar`, chips), the adaptive grid of
 * `AlbumItem` (alternative, with authors) at the default `HomeItemSize.SMALL` (100 dp), "No items", the
 * scroll-to-top button. Chips: "All" (`library`) and "Favorites" (`bookmarked`), the phone's order.
 * A click opens the album, a long press (right click) opens `AlbumItemMenu`.
 * Toolbar: the phone's default buttons ([app.n_zik.compagnon.components.tab.toolbar.HomeToolbars]: sort,
 * search, randomizer, shuffle, item selector, play next, enqueue, add to playlist, export, item size; no
 * contract route, shown without action), then the Compagnon's "Refresh". The bookmark badge comes from
 * contract 1.3 `isBookmarked`, in every filter.
 * Dropped (contract v1): position lock, YouTube sync and its filter chip and progress, the "Disliked" chip,
 * pull-to-refresh, drag to reorder, the play-count / listening-time overlays.
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
    val state by lists.albums.state.collectAsState()
    val albumType by lists.albums.query.collectAsState()

    val buttonsList = listOf(
        CollectionFilter.Library to stringResource(Res.string.all),
        CollectionFilter.Bookmarked to stringResource(Res.string.favorites),
    )
    LoadMoreEffect(lists.albums, state, { lazyGridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 })

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
                            // The phone's default toolbar (no contract route: shown, without action), then the Compagnon's refresh
                            TabToolBar.Buttons(HomeToolbars.albumsOrArtists() + Refresh(lists.albums::reload), disableAnimation = true)
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
                                    chips = buttonsList,
                                    currentValue = albumType,
                                    onValueUpdate = { lists.albums.setQuery(it) },
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
                    contentPadding = PaddingValues(top = headerPadding, bottom = Dimensions.bottomSpacer),
                    modifier = Modifier.background(colorPalette().background0).fillMaxSize(),
                ) {
                    items(
                        items = state.items.withIndex().toList(),
                        key = { (index, album) -> "$index:${album.id}" },
                    ) { (_, album) ->
                        Box(modifier = Modifier) {
                            val menu = actions.collectionActions(CollectionHeader.OfAlbum(album).ref, live)
                            val openMenu = menu?.let {
                                {
                                    menuState.display {
                                        AlbumItemMenu(album, it, bookmarked = album.isBookmarked || albumType == CollectionFilter.Bookmarked).MenuComponent()
                                    }
                                }
                            }
                            AlbumItem(
                                alternative = true,
                                showAuthors = true,
                                album = album,
                                thumbnailSizeDp = HOME_ITEM_SIZE_SMALL,
                                thumbnailSizePx = GRID_THUMBNAIL_SIZE_PX,
                                // Contract 1.3 `isBookmarked`, in every filter (a 1.2 phone only gives it through the filter)
                                likeState = if (album.isBookmarked || albumType == CollectionFilter.Bookmarked) true else null,
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
                        PagedStatus(state, onRetry = lists.albums::retry)
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
