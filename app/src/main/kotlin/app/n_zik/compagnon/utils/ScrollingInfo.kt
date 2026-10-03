package app.n_zik.compagnon.utils

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/* Port of the phone's `app/it/fast4x/rimusic/utils/ScrollingInfo.kt` and `SmoothScrollToTop.kt`. */

data class ScrollingInfo(
    val isScrollingDown: Boolean = false,
    val isFar: Boolean = false,
)

@Composable
fun LazyListState.scrollingInfo(): ScrollingInfo {
    var previousIndex by remember(this) { mutableStateOf(firstVisibleItemIndex) }
    var previousScrollOffset by remember(this) { mutableStateOf(firstVisibleItemScrollOffset) }

    return remember(this) {
        derivedStateOf {
            val isScrollingDown = if (previousIndex == firstVisibleItemIndex) {
                firstVisibleItemScrollOffset > previousScrollOffset
            } else {
                firstVisibleItemIndex > previousIndex
            }

            val isFar = firstVisibleItemIndex > layoutInfo.visibleItemsInfo.size

            previousIndex = firstVisibleItemIndex
            previousScrollOffset = firstVisibleItemScrollOffset

            ScrollingInfo(isScrollingDown, isFar)
        }
    }.value
}

@Composable
fun LazyGridState.scrollingInfo(): ScrollingInfo {
    var previousIndex by remember(this) { mutableStateOf(firstVisibleItemIndex) }
    var previousScrollOffset by remember(this) { mutableStateOf(firstVisibleItemScrollOffset) }

    return remember(this) {
        derivedStateOf {
            val isScrollingDown = if (previousIndex == firstVisibleItemIndex) {
                firstVisibleItemScrollOffset > previousScrollOffset
            } else {
                firstVisibleItemIndex > previousIndex
            }

            val isFar = firstVisibleItemIndex > layoutInfo.visibleItemsInfo.size

            previousIndex = firstVisibleItemIndex
            previousScrollOffset = firstVisibleItemScrollOffset

            ScrollingInfo(isScrollingDown, isFar)
        }
    }.value
}

suspend fun LazyGridState.smoothScrollToTop() {
    if (firstVisibleItemIndex > layoutInfo.visibleItemsInfo.size) {
        scrollToItem(layoutInfo.visibleItemsInfo.size)
    }
    animateScrollToItem(0)
}

suspend fun LazyListState.smoothScrollToTop() {
    if (firstVisibleItemIndex > layoutInfo.visibleItemsInfo.size) {
        scrollToItem(layoutInfo.visibleItemsInfo.size)
    }
    animateScrollToItem(0)
}
