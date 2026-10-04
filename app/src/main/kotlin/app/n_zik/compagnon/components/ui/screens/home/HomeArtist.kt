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
import app.n_zik.compagnon.bridge.library.Artist
import app.n_zik.compagnon.bridge.library.CollectionFilter
import app.n_zik.compagnon.components.ButtonsRow
import app.n_zik.compagnon.components.LocalMenuState
import app.n_zik.compagnon.components.items.ArtistItem
import app.n_zik.compagnon.components.menu.artist.LocalArtistItemMenu
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
import app.n_zik.compagnon.generated.resources.all
import app.n_zik.compagnon.generated.resources.artists
import app.n_zik.compagnon.generated.resources.favorites
import app.n_zik.compagnon.generated.resources.no_items
import app.n_zik.compagnon.generated.resources.people
import app.n_zik.compagnon.utils.onSecondaryClick
import org.jetbrains.compose.resources.stringResource

/**
 * Port of `HomeArtists` (phone's `app/n_zik/android/components/ui/screens/home/HomeArtist.kt`, header 479-585,
 * grid 587-…): as `HomeAlbums`, with `ArtistItem` (round thumbnail) and `LocalArtistItemMenu`.
 * Chips: "All" (`library`) and "Favorites" (`bookmarked`). Dropped: as `HomeAlbums`.
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
    val state by lists.artists.state.collectAsState()
    val artistType by lists.artists.query.collectAsState()

    val buttonsList = listOf(
        CollectionFilter.Library to stringResource(Res.string.all),
        CollectionFilter.Bookmarked to stringResource(Res.string.favorites),
    )
    LoadMoreEffect(lists.artists, state, { lazyGridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 })

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
                            // The phone's default toolbar (no contract route: shown, without action), then the Compagnon's refresh
                            TabToolBar.Buttons(HomeToolbars.albumsOrArtists() + Refresh(lists.artists::reload), disableAnimation = true)
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
                                    currentValue = artistType,
                                    onValueUpdate = { lists.artists.setQuery(it) },
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
                                        LocalArtistItemMenu(artist, it, bookmarked = artist.isBookmarked || artistType == CollectionFilter.Bookmarked).MenuComponent()
                                    }
                                }
                            }
                            ArtistItem(
                                artist = artist,
                                thumbnailSizeDp = HOME_ITEM_SIZE_SMALL,
                                thumbnailSizePx = GRID_THUMBNAIL_SIZE_PX,
                                alternative = true,
                                // Contract 1.3 `isBookmarked`, in every filter (a 1.2 phone only gives it through the filter)
                                likeState = if (artist.isBookmarked || artistType == CollectionFilter.Bookmarked) true else null,
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
                        PagedStatus(state, onRetry = lists.artists::retry)
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
