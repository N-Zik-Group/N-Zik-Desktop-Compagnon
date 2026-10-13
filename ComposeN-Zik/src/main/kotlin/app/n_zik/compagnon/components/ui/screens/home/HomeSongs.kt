package app.n_zik.compagnon.components.ui.screens.home

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.lazy.itemsIndexed
import app.n_zik.compagnon.bridge.state.ListRef
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import app.n_zik.compagnon.bridge.library.PagedList
import app.n_zik.compagnon.bridge.library.PagedState
import app.n_zik.compagnon.bridge.library.SongFilter
import app.n_zik.compagnon.bridge.library.SongSort
import app.n_zik.compagnon.bridge.library.SongsQuery
import app.n_zik.compagnon.bridge.state.QueuePosition
import app.n_zik.compagnon.bridge.state.SongRecommendations
import app.n_zik.compagnon.bridge.state.Track
import app.n_zik.compagnon.bridge.state.formattedTotalPlayTime
import app.n_zik.compagnon.bridge.state.unmatched
import app.n_zik.compagnon.components.LocalMenuState
import app.n_zik.compagnon.components.SongItem
import app.n_zik.compagnon.components.menu.song.SongItemMenu
import app.n_zik.compagnon.components.player.rotateTrackLike
import app.n_zik.compagnon.components.songSortOverlay
import app.n_zik.compagnon.components.styling.Dimensions
import app.n_zik.compagnon.components.tab.ItemSelector
import app.n_zik.compagnon.components.tab.Search
import app.n_zik.compagnon.components.themed.SwipeablePlaylistRow
import app.n_zik.compagnon.colorPalette
import app.n_zik.compagnon.enums.PlaylistSwipeAction
import app.n_zik.compagnon.generated.resources.*
import app.n_zik.compagnon.utils.Toaster
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Port of the list of `HomeSongs` (phone's `app/n_zik/android/components/ui/screens/home/HomeSongs.kt`
 * 528-689): a `LazyColumn` of `SongItem` under the header, "No items" when empty.
 *
 * A click plays the list from that track — since contract 1.10.0 (`queue.fullList`) the phone's WHOLE
 * list through its own click (`stopRadio` + `forcePlayAtIndex`), before the loaded pages (`queue/play`); a long
 * press (right click) opens `SongItemMenu`. Pages are read while scrolling (contract §1), with the
 * Compagnon's loading / error + "Retry" row at the end.
 *
 * Since 1.7.1: the phone's "unmatched" alert (phone's `HomeSongs.kt` 588-596, the click's toast as on
 * the phone) and the listening-sort overlays (phone's `HomeSongs.kt` 600-632): the play count of the
 * `PlayCount` sort, the total play time of the `PlayTime` / `RelativePlayTime` sorts in the phone's
 * `formattedTotalPlayTime` format (`45m` / `2h` / `3d`), and the rank on the Top chip.
 *
 * Since 1.12.0 (`library.recommendations`): the phone's smart-shuffle recommendations of the same chip
 * are inserted at the phone's positions ([songRows]), with the phone's `smart_shuffle` indicator; a click
 * plays that track alone (it is not in the phone's list), a right click opens the song menu on it. The
 * rows carry the "in a playlist" indicator (`Track.inPlaylist`) and, while [itemSelector] is active, the
 * phone's selection box (unchecked, inert).
 *
 * Spec `spec-fill-pc-ui-gaps-home` #60 / #62: the phone's swipe actions of the rows (its
 * `SwipeablePlaylistItem`, the phone's `playlistSwipeLeftActionKey` / `playlistSwipeRightActionKey`,
 * the phone's `isSwipeToActionEnabled` toggle) — a swipe to the right plays the track next, one to
 * the left rotates its like (the phone's "disliked" mode deciding the rotation); the download
 * swipe is a no-op (no download on the PC).
 *
 * Spec #63: the phone's reorder handle of an unlocked, custom-sorted, ascending list off the Top
 * chip (its `HomeSongs.kt` 660-681) with its 24 dp row spacer — its drag is inert on the PC (no
 * contract route for the list order): one toast per gesture (the phone's
 * `info_lock_unlock_reorder_songs`), nothing is persisted.
 *
 * Adaptations: a click outside a `Live` session does nothing (no command can reach the phone); the top
 * fade has no system-bar inset (none on the desktop).
 * Dropped: the actual reorder (no contract route), haptics.
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
    /** Since 1.10.0 (`queue.fullList`): the whole list the click hands to the phone; `null` for a PC-only chip. */
    listRef: () -> ListRef? = { null },
    /** Since 1.12.0: the phone's smart-shuffle recommendations, inserted when [chipFilter] is the phone's tab. */
    recommendations: SongRecommendations? = null,
    /** The chip's wire filter; `null` for a PC-only chip (no recommendation inserted). */
    chipFilter: SongFilter? = null,
    /** The search text, applied to the inserted recommendations (the loaded rows come filtered by the phone). */
    searchText: String? = null,
    /** The screen's selection toggle: an unchecked box on every row while active. */
    itemSelector: ItemSelector? = null,
    /** Spec #60: the rows' left swipe action (the phone's `playlistSwipeLeftActionKey`). */
    swipeLeftAction: PlaylistSwipeAction = PlaylistSwipeAction.Favourite,
    /** Spec #60: the rows' right swipe action (the phone's `playlistSwipeRightActionKey`). */
    swipeRightAction: PlaylistSwipeAction = PlaylistSwipeAction.PlayNext,
    /** Spec #62: the phone's `isSwipeToActionEnabled`: off makes the rows non-swipeable. */
    swipeEnabled: Boolean = true,
    /** Spec #60: the phone's "disliked" mode (its `DislikeMode.songs`) deciding the [Favourite] rotation. */
    likeRotationEnabled: Boolean = true,
    /** Spec #63: the phone's reorder-handle visibility (its `HomeSongs.kt` 660 condition). */
    showReorderHandle: Boolean = false,
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
                // The phone's display list: the loaded rows with its recommendations inserted (since 1.12.0)
                items = songRows(state.items, recommendations, chipFilter, searchText),
                // The phone's keys: the track id, duplicates dropped (`distinctBy`)
                key = { _, row -> row.track.id },
            ) { displayIndex, row ->
                val song = row.track
                // A recommendation has no index in the loaded list: its menu and click act on it alone
                val index = row.loadedIndex
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .zIndex(2f),
                ) {
                    val menu = if (index == null) actions.singleTrackActions(song, live) else actions.trackActions(list.state, index, song.id, live, listRef())
                    val playback = live && actions.available
                    // The phone's swipe actions of the rows (its `SwipeablePlaylistItem`, its
                    // `HomeSongs.kt` 570-602)
                    SwipeablePlaylistRow(
                        song = song,
                        swipeLeftAction = swipeLeftAction,
                        swipeRightAction = swipeRightAction,
                        swipeEnabled = swipeEnabled,
                        onPlayNext = { if (playback) actions.add(song, QueuePosition.Next) },
                        onEnqueue = { if (playback) actions.add(song, QueuePosition.End) },
                        // The phone's like rotation (the "disliked" mode decides it), inert without
                        // `library.write`
                        onFavourite = { rotateTrackLike(song, actions.takeIf { it.canWrite }, likeRotationEnabled) },
                    ) {
                        // Phone's `HomeSongs.kt` 587: the rows animate their placement
                        SongItem(
                            song = song,
                            modifier = Modifier.animateItem(),
                            isRecommended = row.isRecommended,
                            itemSelector = itemSelector,
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
                                    // The phone's rank is the row's place in its display list
                                    rank = if (isTop) displayIndex + 1 else null,
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
                                // The phone's spacer of the reorder handle (its `HomeSongs.kt` 573)
                                if (showReorderHandle) Box(Modifier.width(24.dp))
                            },
                            onLongClick = menu?.let { { menuState.display { SongItemMenu(song, it).MenuComponent() } } },
                            onClick = {
                                search.hideIfEmpty()
                                if (song.unmatched()) {
                                    Toaster.w(Res.string.playback_blocked_match_first)
                                } else if (live && actions.available) {
                                    if (index == null) {
                                        actions.playSingle(song)
                                    } else {
                                        actions.playFrom(list.state.value.items, index, song.id, listRef())
                                    }
                                }
                            },
                        )
                    }
                    // The phone's reorder handle (its `HomeSongs.kt` 660-681): its drag is inert on
                    // the PC (no contract route for the list order) — one toast per gesture
                    if (showReorderHandle) {
                        Icon(
                            painter = painterResource(Res.drawable.reorder),
                            contentDescription = null,
                            tint = colorPalette().textSecondary,
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .pointerInput(song.id) {
                                    detectDragGesturesAfterLongPress(
                                        onDrag = { change, _ -> change.consume() },
                                        // One toast per inert drag gesture: the order is the phone's, never written here
                                        onDragEnd = { Toaster.i(Res.string.info_lock_unlock_reorder_songs) },
                                    )
                                }
                                .padding(end = 12.dp)
                                .size(20.dp),
                        )
                    }
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
