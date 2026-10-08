package app.n_zik.compagnon.components.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import app.n_zik.compagnon.bridge.library.PagedList
import app.n_zik.compagnon.bridge.library.PagedState
import app.n_zik.compagnon.bridge.library.SongSort
import app.n_zik.compagnon.bridge.library.SongsQuery
import app.n_zik.compagnon.bridge.state.Track
import app.n_zik.compagnon.bridge.state.formattedTotalPlayTime
import app.n_zik.compagnon.bridge.state.unmatched
import app.n_zik.compagnon.components.LocalMenuState
import app.n_zik.compagnon.components.SongItem
import app.n_zik.compagnon.components.menu.song.SongItemMenu
import app.n_zik.compagnon.components.songSortOverlay
import app.n_zik.compagnon.components.styling.Dimensions
import app.n_zik.compagnon.components.tab.Search
import app.n_zik.compagnon.colorPalette
import app.n_zik.compagnon.generated.resources.*
import app.n_zik.compagnon.utils.Toaster
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Port of the list of `HomeSongs` (phone's `app/n_zik/android/components/ui/screens/home/HomeSongs.kt`
 * 528-689): a `LazyColumn` of `SongItem` under the header, "No items" when empty.
 *
 * A click plays the loaded list from that track (`queue/play`, the phone's `forcePlayAtIndex`); a long
 * press (right click) opens `SongItemMenu`. Pages are read while scrolling (contract §1), with the
 * Compagnon's loading / error + "Retry" row at the end.
 *
 * Since 1.7.1: the phone's "unmatched" alert (phone's `HomeSongs.kt` 588-596, the click's toast as on
 * the phone) and the listening-sort overlays (phone's `HomeSongs.kt` 600-632): the play count of the
 * `PlayCount` sort, the total play time of the `PlayTime` / `RelativePlayTime` sorts in the phone's
 * `formattedTotalPlayTime` format (`45m` / `2h` / `3d`), and the rank on the Top chip.
 *
 * Dropped: swipe actions (play next / download / enqueue), drag to reorder (custom sort), haptics.
 */
@Composable
fun HomeSongs(
    list: PagedList<SongsQuery, Track>,
    state: PagedState<Track>,
    lazyListState: LazyListState,
    search: Search,
    actions: LibraryActions,
    live: Boolean,
    headerPadding: Dp,
    /** Since 1.7.1: the chip's sort, for the phone's listening-sort overlays. */
    sort: SongSort = SongSort.Title,
    /** Since 1.7.1: the phone's Top chip, for the phone's rank overlay (the row's index + 1). */
    isTop: Boolean = false,
) {
    val menuState = LocalMenuState.current
    LoadMoreEffect(list, state, { lazyListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 })

    val showNoItems = !state.loading && state.error == null && state.items.isEmpty() && state.total != null

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = lazyListState,
            contentPadding = PaddingValues(top = headerPadding, bottom = Dimensions.bottomSpacer),
            modifier = Modifier
                .background(colorPalette().background0)
                .fillMaxSize(),
        ) {
            itemsIndexed(
                items = state.items,
                key = { index, song -> "$index:${song.id}" },
            ) { index, song ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .zIndex(2f),
                ) {
                    val menu = actions.trackActions(list.state, index, song.id, live)
                    // Phone's `HomeSongs.kt` 587: the rows animate their placement
                    SongItem(
                        song = song,
                        modifier = Modifier.animateItem(),
                        // The phone's listening-sort overlays (contract 1.7.1, phone's `HomeSongs.kt` 600-632),
                        // the rank on the Top chip
                        thumbnailOverlay = {
                            songSortOverlay(
                                text = when (sort) {
                                    SongSort.PlayCount -> song.playCount.toString()
                                    SongSort.PlayTime,
                                    SongSort.RelativePlayTime,
                                    -> song.formattedTotalPlayTime
                                    else -> null
                                },
                                rank = if (isTop) index + 1 else null,
                            )
                        },
                        // The phone's orange alert of a track not matched to the phone's library
                        // (contract 1.7.1, phone's `HomeSongs.kt` 588-596)
                        trailingContent = {
                            if (song.unmatched()) {
                                Icon(
                                    painter = painterResource(Res.drawable.alert),
                                    contentDescription = stringResource(Res.string.unmatched_song),
                                    tint = Color(0xFFFF9800),
                                    modifier = Modifier
                                        .padding(start = 8.dp)
                                        .size(18.dp),
                                )
                            }
                        },
                        onLongClick = menu?.let { { menuState.display { SongItemMenu(song, it).MenuComponent() } } },
                        onClick = {
                            search.hideIfEmpty()
                            if (song.unmatched()) {
                                Toaster.w(Res.string.playback_blocked_match_first)
                            } else if (live && actions.available) {
                                actions.playFrom(list.state.value.items, index, song.id)
                            }
                        },
                    )
                }
            }
            item(key = "status") {
                PagedStatus(state, onRetry = list::retry)
            }
        }

        if (showNoItems) {
            NoItems(stringResource(Res.string.no_items))
        }
    }
}
