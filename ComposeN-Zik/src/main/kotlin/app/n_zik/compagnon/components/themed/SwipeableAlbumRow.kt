package app.n_zik.compagnon.components.themed

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.unit.dp
import app.n_zik.compagnon.colorPalette
import app.n_zik.compagnon.components.player.SwipeActionLatch
import app.n_zik.compagnon.enums.AlbumSwipeAction
import app.n_zik.compagnon.uiRoundnessShape
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import org.jetbrains.compose.resources.painterResource

/**
 * The phone's direction → action decision of the Albums cards (its `SwipeableContent.kt`): a swipe
 * to the right fires [rightAction], one to the left [leftAction], nothing while the box is at rest
 * ([SwipeToDismissBoxValue.Settled] → null).
 */
internal fun albumSwipeAction(
    direction: SwipeToDismissBoxValue,
    leftAction: AlbumSwipeAction,
    rightAction: AlbumSwipeAction,
): AlbumSwipeAction? = when (direction) {
    SwipeToDismissBoxValue.StartToEnd -> rightAction
    SwipeToDismissBoxValue.EndToStart -> leftAction
    SwipeToDismissBoxValue.Settled -> null
}

/**
 * The phone's `getActionCallback` (its `SwipeableContent.kt`): the callback of the chosen [action].
 * A `null` (box at rest) or the phone's [AlbumSwipeAction.NoAction] is a no-op.
 */
internal fun albumSwipeCallback(
    action: AlbumSwipeAction?,
    onPlayNext: () -> Unit,
    onEnqueue: () -> Unit,
    onBookmark: () -> Unit,
): () -> Unit = when (action) {
    AlbumSwipeAction.PlayNext -> onPlayNext
    AlbumSwipeAction.Bookmark -> onBookmark
    AlbumSwipeAction.Enqueue -> onEnqueue
    else -> ({})
}

/**
 * Port of `SwipeableContent` + `SwipeableAlbumItem` (phone's
 * `app/it/fast4x/rimusic/ui/components/SwipeableContent.kt` 82-155, 383-416; spec
 * `spec-fill-pc-ui-gaps-home` #61): an Albums card behind a `SwipeToDismissBox` with the phone's
 * 25 % positional threshold, always snap-back (the phone's `confirmValueChange = false`), the
 * action icon of the chosen [AlbumSwipeAction] revealed on the card's background while dragging in
 * the accent (the [AlbumSwipeAction.Bookmark] bookmark fills with the album's bookmarked state —
 * the phone's `getStateIcon(bookmarkedAt)`, the PC's contract carrying the boolean
 * [bookmarkedState] instead of the phone's nullable timestamp). The phone's doubled touch slop
 * (its `LocalViewConfiguration` override, so the grid's vertical scroll wins a slow diagonal drag)
 * is kept; its haptics are not (no haptic device). The action runs once per gesture through the
 * ported [SwipeActionLatch] (issue #818 on the phone — see [SwipeableRow]). (The phone defines
 * `SwipeableAlbumItem` but never wraps its home Albums grid with it — the spec ports the component
 * as the real PC Albums cards' swipe, the phone's `albumSwipeLeft/RightActionKey` keys having no
 * other home surface.)
 */
@Composable
fun SwipeableAlbumRow(
    bookmarkedState: Boolean,
    swipeLeftAction: AlbumSwipeAction,
    swipeRightAction: AlbumSwipeAction,
    swipeEnabled: Boolean,
    onPlayNext: () -> Unit,
    onEnqueue: () -> Unit,
    onBookmark: () -> Unit,
    backgroundColor: Color = colorPalette().background0,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val swipeLeftAction by rememberUpdatedState(swipeLeftAction)
    val swipeRightAction by rememberUpdatedState(swipeRightAction)
    val bookmarkedState by rememberUpdatedState(bookmarkedState)
    val onPlayNext by rememberUpdatedState(onPlayNext)
    val onEnqueue by rememberUpdatedState(onEnqueue)
    val onBookmark by rememberUpdatedState(onBookmark)

    // The callback is called again on every drag delta once the finger reaches the end anchor (it
    // never settles, as it answers false): run the action once per gesture (issue #818 on the phone)
    val swipeActionLatch = remember { SwipeActionLatch() }

    val dismissState = rememberSwipeToDismissBoxState(
        positionalThreshold = { distance: Float -> distance * 0.25f },
        confirmValueChange = { value ->
            if (swipeActionLatch.tryFire()) {
                albumSwipeCallback(
                    albumSwipeAction(value, swipeLeftAction, swipeRightAction),
                    onPlayNext = onPlayNext,
                    onEnqueue = onEnqueue,
                    onBookmark = onBookmark,
                )()
            }

            // Always snap back to the center
            false
        },
    )

    // Back at rest means the gesture is over: rearm the latch. The offset is not readable before
    // the first layout, which is also a state at rest.
    LaunchedEffect(dismissState) {
        snapshotFlow {
            try { dismissState.requireOffset() == 0f } catch (e: IllegalStateException) { true }
        }
            .distinctUntilChanged()
            .filter { atRest -> atRest }
            .collect { swipeActionLatch.release() }
    }

    // Once the action ran the swipe is off (see gesturesEnabled): send the box back to its position.
    // It comes back on when the box is at rest, which releases the latch above.
    LaunchedEffect(swipeActionLatch.isFired) {
        if (swipeActionLatch.isFired) dismissState.reset()
    }

    // The action changes the card (bookmark), which recomputes the swipe anchors: the state can
    // then settle in a dismissed direction and stay displaced, its gestures being off until it is

    // back at rest. Bring the card back to its position.
    LaunchedEffect(dismissState) {
        snapshotFlow { dismissState.settledValue }
            .filter { settled -> settled != SwipeToDismissBoxValue.Settled }
            .collect { dismissState.reset() }
    }

    val current = LocalViewConfiguration.current
    CompositionLocalProvider(LocalViewConfiguration provides object : ViewConfiguration by current {
        override val touchSlop: Float
            get() = current.touchSlop * 2f
    }) {
        SwipeToDismissBox(
            // The box's gestures are off while the action has run (issue #818), so the drag ends
            // and the box can go back to its position instead of firing the action again.
            gesturesEnabled = swipeEnabled && !swipeActionLatch.isFired,
            modifier = modifier
                .clip(uiRoundnessShape()),
            state = dismissState,
            backgroundContent = {
                val offset = try { dismissState.requireOffset() } catch (e: IllegalStateException) { 0f }
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(backgroundColor)
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = when {
                        offset > 0 -> Arrangement.Start
                        offset < 0 -> Arrangement.End
                        else -> Arrangement.Center
                    },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val iconId = when {
                        offset > 0 -> swipeRightAction.getStateIcon(bookmarkedState)
                        offset < 0 -> swipeLeftAction.getStateIcon(bookmarkedState)
                        else -> null
                    }
                    if (iconId != null) {
                        Icon(
                            painter = painterResource(iconId),
                            contentDescription = null,
                            tint = colorPalette().accent,
                        )
                    }
                }
            },
        ) {
            content()
        }
    }
}
