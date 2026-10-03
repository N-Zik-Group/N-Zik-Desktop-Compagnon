package app.n_zik.compagnon.components.ui.screens.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import app.n_zik.compagnon.bridge.library.Playlist
import app.n_zik.compagnon.bridge.state.Track
import app.n_zik.compagnon.components.LocalMenuState
import app.n_zik.compagnon.components.items.PlaylistItem
import app.n_zik.compagnon.components.items.ThumbnailRenderer
import app.n_zik.compagnon.components.items.playlistThumbnails
import app.n_zik.compagnon.components.menu.playlist.LocalPlaylistItemMenu
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
import app.n_zik.compagnon.generated.resources.no_items
import app.n_zik.compagnon.generated.resources.playlist
import app.n_zik.compagnon.generated.resources.playlists
import app.n_zik.compagnon.utils.onSecondaryClick
import org.jetbrains.compose.resources.stringResource

/**
 * Port of `HomeLibrary` (phone's `app/n_zik/android/components/ui/screens/home/HomeLibrary.kt`, header
 * 601-762, grid 764-952).
 *
 * Kept: the collapsible header (`TabHeader` "Playlists" + count, `TabToolBar`), the adaptive grid of
 * `PlaylistItem` (2×2 mosaic of the first four tracks, read with `/songs?limit=4`, else the artwork of
 * `artworkTrackId`; `background4`; count pill), "No items", the scroll-to-top button. A click opens the
 * playlist, a long press (right click) opens `LocalPlaylistItemMenu`.
 * Toolbar: only the Compagnon's "Refresh". Dropped (contract v1): the playlist-type chips (playlists,
 * pinned, monthly / rewind, YouTube…: the contract has no type), sort, search, new playlist, import /
 * export, multi-selection, delete, item size, sync and its progress, drag to reorder, the overlays of the
 * count / play-count / listening-time sorts, the origin icons.
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
    val state by lists.playlists.state.collectAsState()
    val firstTracks by lists.playlistFirstTracks.collectAsState()
    LoadMoreEffect(lists.playlists, state, { lazyGridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 })

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
                            TabToolBar.Buttons(listOf(Refresh(lists::reloadPlaylists)))
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
                        PagedStatus(state, onRetry = lists.playlists::retry)
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
