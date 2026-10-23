package app.n_zik.compagnon.components.themed

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularWavyProgressIndicator
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.unit.dp
import app.n_zik.compagnon.bridge.state.Track
import app.n_zik.compagnon.colorPalette
import app.n_zik.compagnon.components.player.SwipeActionLatch
import app.n_zik.compagnon.enums.QueueSwipeAction
import app.n_zik.compagnon.generated.resources.*
import app.n_zik.compagnon.uiRoundnessShape
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import org.jetbrains.compose.resources.painterResource

/**
 * The phone's direction → action decision of the queue rows (its `SwipeableContent.kt`): a swipe to
 * the right fires [rightAction], one to the left [leftAction], nothing while the box is at rest
 * ([SwipeToDismissBoxValue.Settled] → null).
 */
internal fun queueSwipeAction(
    direction: SwipeToDismissBoxValue,
    leftAction: QueueSwipeAction,
    rightAction: QueueSwipeAction,
): QueueSwipeAction? = when (direction) {
    SwipeToDismissBoxValue.StartToEnd -> rightAction
    SwipeToDismissBoxValue.EndToStart -> leftAction
    SwipeToDismissBoxValue.Settled -> null
}

/**
 * The phone's `getActionCallback` (its `SwipeableContent.kt` 235-244): the callback of the chosen
 * [action]. A `null` (box at rest), the phone's [QueueSwipeAction.NoAction], or
 * [QueueSwipeAction.Download] (no download on the PC) is a no-op.
 */
internal fun queueSwipeCallback(
    action: QueueSwipeAction?,
    onPlayNext: () -> Unit,
    onEnqueue: () -> Unit,
    onRemoveFromQueue: () -> Unit,
    onFavourite: () -> Unit,
): () -> Unit = when (action) {
    QueueSwipeAction.PlayNext -> onPlayNext
    QueueSwipeAction.Download -> ({}) // no download on the PC
    QueueSwipeAction.Favourite -> onFavourite
    QueueSwipeAction.RemoveFromQueue -> onRemoveFromQueue
    QueueSwipeAction.Enqueue -> onEnqueue
    else -> ({})
}

/**
 * Port of `SwipeableContent` + `SwipeableQueueItem` (phone's
 * `app/it/fast4x/rimusic/ui/components/SwipeableContent.kt` 82-155, 158-269): a queue row behind a
 * `SwipeToDismissBox` with the phone's 25 % positional threshold, always snap-back (the phone's
 * `confirmValueChange = false`), the action icon of the chosen [QueueSwipeAction] revealed on the
 * row's background while dragging — `download_progress` drawn as the phone's 20 dp wavy ring with
 * its 2 dp strokes (its `DOWNLOAD_INDICATOR_SIZE_SWIPE` / `DOWNLOAD_INDICATOR_STROKE_WIDTH`), the
 * other icons in the accent. The phone's doubled touch slop (its `LocalViewConfiguration`
 * override, so the list's vertical scroll wins a slow diagonal drag) is kept; its haptics are not
 * (no haptic device). `rememberSwipeToDismissBoxState` keeps the `confirmValueChange` it got at the
 * first composition: the actions and the row's callbacks are read through `rememberUpdatedState`
 * (the phone's "Compose gotcha" — its index-by-id resolution at the call site plays the same role).
 * The action runs once per gesture through the ported [SwipeActionLatch] (issue #818 on the phone:
 * `SwipeToDismissBox` re-invokes `confirmValueChange` on every drag delta once the positional
 * threshold is crossed and the box never settles because the callback answers false, so the action
 * would otherwise repeat — `Enqueue` would duplicate the track, `Favourite` cycle the like, `PlayNext`
 * re-issue the `move`). Dropped: the download action (no download on the PC — the
 * [QueueSwipeAction.Download] swipe is a no-op), the like-state / download-state database queries
 * (the PC's [Track] already carries the wire's tri-state like and download state, mapped by
 * [QueueSwipeAction.getStateIcon]).
 */
@Composable
fun SwipeableRow(
    song: Track,
    swipeLeftAction: QueueSwipeAction,
    swipeRightAction: QueueSwipeAction,
    swipeEnabled: Boolean,
    onPlayNext: () -> Unit,
    onEnqueue: () -> Unit,
    onRemoveFromQueue: () -> Unit,
    onFavourite: () -> Unit,
    backgroundColor: Color = colorPalette().background0,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val swipeLeftAction by rememberUpdatedState(swipeLeftAction)
    val swipeRightAction by rememberUpdatedState(swipeRightAction)
    val onPlayNext by rememberUpdatedState(onPlayNext)
    val onEnqueue by rememberUpdatedState(onEnqueue)
    val onRemoveFromQueue by rememberUpdatedState(onRemoveFromQueue)
    val onFavourite by rememberUpdatedState(onFavourite)

    // The callback is called again on every drag delta once the finger reaches the end anchor (it
    // never settles, as it answers false): run the action once per gesture (issue #818 on the phone)
    val swipeActionLatch = remember { SwipeActionLatch() }

    val dismissState = rememberSwipeToDismissBoxState(
        positionalThreshold = { distance: Float -> distance * 0.25f },
        confirmValueChange = { value ->
            if (swipeActionLatch.tryFire()) {
                queueSwipeCallback(
                    queueSwipeAction(value, swipeLeftAction, swipeRightAction),
                    onPlayNext = onPlayNext,
                    onEnqueue = onEnqueue,
                    onRemoveFromQueue = onRemoveFromQueue,
                    onFavourite = onFavourite,
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

    // The action changes the row (removal, move), which recomputes the swipe anchors: the state can
    // then settle in a dismissed direction and stay displaced, its gestures being off until it is

    // back at rest. Bring the row back to its position.
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
                        offset > 0 -> swipeRightAction.getStateIcon(song)
                        offset < 0 -> swipeLeftAction.getStateIcon(song)
                        else -> null
                    }
                    if (iconId == Res.drawable.download_progress) {
                        CircularWavyProgressIndicator(
                            color = colorPalette().accent,
                            trackColor = colorPalette().textDisabled,
                            modifier = Modifier.size(20.dp),
                            stroke = Stroke(width = with(LocalDensity.current) { 2.dp.toPx() }),
                            trackStroke = Stroke(width = with(LocalDensity.current) { 2.dp.toPx() }),
                        )
                    } else if (iconId != null) {
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
