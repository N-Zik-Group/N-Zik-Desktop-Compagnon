package app.n_zik.compagnon.components.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.zIndex
import app.n_zik.compagnon.bridge.library.PagedList
import app.n_zik.compagnon.bridge.library.PagedState
import app.n_zik.compagnon.bridge.library.SongsQuery
import app.n_zik.compagnon.bridge.state.Track
import app.n_zik.compagnon.components.LocalMenuState
import app.n_zik.compagnon.components.SongItem
import app.n_zik.compagnon.components.menu.song.SongItemMenu
import app.n_zik.compagnon.components.styling.Dimensions
import app.n_zik.compagnon.components.tab.Search
import app.n_zik.compagnon.colorPalette
import app.n_zik.compagnon.generated.resources.Res
import app.n_zik.compagnon.generated.resources.no_items
import org.jetbrains.compose.resources.stringResource

/**
 * Port of the list of `HomeSongs` (phone's `app/n_zik/android/components/ui/screens/home/HomeSongs.kt`
 * 528-689): a `LazyColumn` of `SongItem` under the header, "No items" when empty.
 *
 * A click plays the loaded list from that track (`queue/play`, the phone's `forcePlayAtIndex`); a long
 * press (right click) opens `SongItemMenu`. Pages are read while scrolling (contract §1), with the
 * Compagnon's loading / error + "Retry" row at the end.
 * Dropped: swipe actions (play next / download / enqueue), drag to reorder (custom sort), the "unmatched"
 * alert, the play-time overlay of the play-time sort (the contract gives no play time), haptics.
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
                    val menu = actions.trackActions({ list.state.value.items }, index, song.id, live)
                    SongItem(
                        song = song,
                        onLongClick = menu?.let { { menuState.display { SongItemMenu(song, it).MenuComponent() } } },
                        onClick = {
                            search.hideIfEmpty()
                            if (live && actions.available) actions.playFrom(list.state.value.items, index, song.id)
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
