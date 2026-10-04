package app.n_zik.compagnon.components.ui.screens.home

import androidx.compose.ui.Alignment
import app.n_zik.compagnon.generated.resources.yt_playlists
import app.n_zik.compagnon.generated.resources.rewind
import app.n_zik.compagnon.generated.resources.pinned_playlists
import app.n_zik.compagnon.generated.resources.all
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import app.n_zik.compagnon.components.ButtonsRow
import app.n_zik.compagnon.components.tab.toolbar.HomeToolbars
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
 * Toolbar: the phone's default buttons (sort, search, shuffle, item selector, play next, enqueue, add to
 * playlist, import, export, delete, item size: no contract route, shown without action), then the
 * Compagnon's "Refresh". The phone's `ButtonsRow` of playlist types (All, Pinned, Rewind, YT/YTM, its
 * default order, `HomeLibrary.kt` 403-440 and 643-657) is shown with "All" selected: the contract has no
 * playlist type, so the other chips have no effect.
 * Dropped (contract v1): sync and its progress, drag to reorder, the overlays of the count / play-count /
 * listening-time sorts, the Rewind month / year row (only under the Rewind chip).
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
    // The phone's playlist types in their default order; only "All" exists in the contract
    val playlistTypeChips = listOf(
        PLAYLIST_TYPE_ALL to stringResource(Res.string.all),
        "pinned_playlists" to stringResource(Res.string.pinned_playlists),
        "rewind" to stringResource(Res.string.rewind),
        "yt_playlists" to stringResource(Res.string.yt_playlists),
    )
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
                            // The phone's default toolbar (no contract route: shown, without action), then the Compagnon's refresh
                            TabToolBar.Buttons(HomeToolbars.playlists() + Refresh(lists::reloadPlaylists), disableAnimation = true)
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
                                Box {
                                    ButtonsRow(
                                        chips = playlistTypeChips,
                                        currentValue = PLAYLIST_TYPE_ALL,
                                        onValueUpdate = {},
                                        modifier = Modifier.padding(end = 12.dp),
                                    )
                                }
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

/** The "All" chip of the playlist types, the only one the contract can list. */
private const val PLAYLIST_TYPE_ALL = "all"
