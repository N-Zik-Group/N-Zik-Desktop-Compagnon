package app.n_zik.compagnon.components.ui.screens.player

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import app.n_zik.compagnon.CommandLauncher
import app.n_zik.compagnon.LocalCommandLauncher
import app.n_zik.compagnon.LocalPlayerRepository
import app.n_zik.compagnon.bridge.ConnectionState
import app.n_zik.compagnon.bridge.state.PlayerState
import app.n_zik.compagnon.bridge.state.QueuePosition
import app.n_zik.compagnon.bridge.state.SessionContract
import app.n_zik.compagnon.bridge.state.Track
import app.n_zik.compagnon.colorPalette
import app.n_zik.compagnon.components.LocalMenuState
import app.n_zik.compagnon.components.SongItem
import app.n_zik.compagnon.components.menu.song.SongItemMenu
import app.n_zik.compagnon.components.player.QueueToolBarState
import app.n_zik.compagnon.components.styling.Dimensions
import app.n_zik.compagnon.components.tab.Locator
import app.n_zik.compagnon.components.tab.PositionLock
import app.n_zik.compagnon.components.tab.Search
import app.n_zik.compagnon.components.tab.toolbar.ConfirmDialog
import app.n_zik.compagnon.components.tab.toolbar.Descriptive
import app.n_zik.compagnon.components.tab.toolbar.Icon
import app.n_zik.compagnon.components.tab.toolbar.MenuIcon
import app.n_zik.compagnon.components.themed.FloatingActionsContainerWithScrollToTop
import app.n_zik.compagnon.components.themed.IconButton
import app.n_zik.compagnon.components.ui.screens.home.ItemActions
import app.n_zik.compagnon.enums.QueueLoopType
import app.n_zik.compagnon.generated.resources.Res
import app.n_zik.compagnon.generated.resources.chevron_down
import app.n_zik.compagnon.generated.resources.clean_queue_confirm
import app.n_zik.compagnon.generated.resources.remove_from_queue
import app.n_zik.compagnon.generated.resources.reorder
import app.n_zik.compagnon.generated.resources.repeat
import app.n_zik.compagnon.generated.resources.shuffle
import app.n_zik.compagnon.generated.resources.trash
import app.n_zik.compagnon.utils.Toaster
import app.n_zik.compagnon.utils.cleanPrefix
import app.n_zik.compagnon.utils.smoothScrollToTop
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Port of `Queue` (phone's `app/it/fast4x/rimusic/ui/screens/player/Queue.kt` 142-606) in its default
 * `QueueType.Modern` style: the phone's queue in its effective order (contract §7), on `background0` at
 * 50 %, its `SongItem`s on a transparent background, the current one highlighted; the search bar at the bottom
 * (`background1`); the scroll-to-top button; the toolbar buttons handed to [QueueToolBarState] (drawn by
 * `QueueToolBar`).
 *
 * Kept: a click on an item plays it (`queue/jump`), or plays / pauses it when it is the current one; the
 * long press (a right click on the PC) opens `SongItemMenu`; the reorder lock and, once unlocked, the drag
 * handles (`queue/move` on release: the dragged row follows the pointer, nothing else moves until the phone
 * confirms, no optimistic UI); the toolbar's Locator, Search (local filter on the cleaned title / artists,
 * like the phone), reorder lock,
 * Repeat, Shuffle and "Remove from queue" (empties the queue after the phone's confirmation, `queue/clear`).
 * Dropped: the swipe actions (no swipe on the PC: "Remove from queue" moves to the item's menu, PC only), the
 * multi-selection, Discover, the download buttons, "Add to playlist" and the CSV export (not in contract v1),
 * the radio's loading placeholders, the remembered scroll position across openings (the list opens on the
 * current track), the "Deleted" toast after clearing (the phone confirms it through the WS).
 * Queue actions are hidden without the `queue` feature and do nothing outside a `Live` session.
 */
@Composable
fun Queue(
    onDismiss: (QueueLoopType) -> Unit,
) {
    val repository = LocalPlayerRepository.current ?: return
    val onCommand = LocalCommandLauncher.current
    val menuState = LocalMenuState.current
    val coroutineScope = rememberCoroutineScope()
    val playerState by repository.state.collectAsState()
    val connection by repository.connection.collectAsState()
    val state = playerState ?: PlayerState()
    val queueFeature = SessionContract.FEATURE_QUEUE in repository.features
    val live = connection == ConnectionState.Live
    // Without a live session no delta can confirm a change: queue actions wait for the reconnection
    val actionsEnabled by rememberUpdatedState(queueFeature && live)

    val rippleIndication = ripple(bounded = false)

    Box(Modifier.fillMaxSize()) {
        val items = state.queue

        val lazyListState = rememberLazyListState(initialFirstVisibleItemIndex = state.currentIndex.coerceAtLeast(0))

        val positionLock = remember { PositionLock { actionsEnabled } }

        var searchText by rememberSaveable { mutableStateOf("") }
        val search = Search(searchText, { searchText = it }, lazyListState)
        val windowsOnDisplay = remember(items, searchText) {
            val indexed = items.withIndex().toList()
            if (searchText.isEmpty()) {
                indexed
            } else {
                indexed.filter { (_, song) ->
                    // Filter on the cleaned metadata like the phone (`cleanTitle()` / `cleanArtistsText()`):
                    // without cleaning, a user could search explicit songs with "e:"
                    val containsTitle = cleanPrefix(song.title).contains(searchText, true)
                    val containsArtist = cleanPrefix(song.artists.orEmpty()).contains(searchText, true)

                    containsTitle || containsArtist
                }
            }
        }

        val shuffle = ShuffleQueue(state, lazyListState, coroutineScope, onCommand, actionsEnabled)
        val repeat = Repeat(state, onCommand, actionsEnabled)
        val deleteDialog = DeleteFromQueue(actionsEnabled) {
            onCommand { clearQueue() }
            onDismiss(repeat.type)
        }
        val queueArrow = QueueArrow { onDismiss(repeat.type) }
        val locator = Locator(lazyListState, { windowsOnDisplay.map { it.value } }, onMessage = { Toaster.i(it) })

        // Dialog renders
        deleteDialog.Render()

        val buttonsList = buildList {
            add(locator)
            add(search)
            if (queueFeature) {
                add(positionLock)
                add(repeat)
                add(shuffle)
                add(deleteDialog)
            }
        }

        QueueToolBarState.mediaItemCount = items.size
        QueueToolBarState.buttons = buttonsList
        QueueToolBarState.queueArrow = queueArrow
        QueueToolBarState.onBarClick = { onDismiss(repeat.type) }
        QueueToolBarState.isVisible = if (search.isVisible) 1 else 0

        DisposableEffect(Unit) {
            onDispose { QueueToolBarState.reset() }
        }

        // Drag of a handle: the row index being dragged and how far it moved (local while the pointer is
        // down, as the seek bar's scrubber; the queue itself only changes when the phone confirms)
        var draggedIndex by remember { mutableStateOf<Int?>(null) }
        var dragOffset by remember { mutableFloatStateOf(0f) }

        Column {
            // QueueType.Modern
            val backgroundAlpha = .5f
            val itemBackground = Color.Transparent

            LazyColumn(
                state = lazyListState,
                horizontalAlignment = Alignment.CenterHorizontally,
                contentPadding = PaddingValues(bottom = Dimensions.bottomSpacer),
                modifier = Modifier.weight(1f)
                    .background(
                        colorPalette().background0.copy(alpha = backgroundAlpha),
                    ),
            ) {
                itemsIndexed(
                    items = windowsOnDisplay,
                    key = { _, window -> "${window.index}:${window.value.id}" },
                ) { _, window ->
                    val song = window.value
                    val index = window.index
                    val isDraggingItem = draggedIndex == index

                    Box(
                        modifier = Modifier.fillMaxWidth()
                            .zIndex(if (isDraggingItem) 1f else 0f)
                            .graphicsLayer { translationY = if (isDraggingItem) dragOffset else 0f },
                    ) {
                        // Drag anchor
                        if (!positionLock.isLocked() && searchText.isEmpty()) {
                            Box(
                                modifier = Modifier.padding(end = 16.dp) // Accommodate horizontal padding of SongItem
                                    .size(24.dp)
                                    .zIndex(2f)
                                    .align(Alignment.CenterEnd)
                                    .pointerInput(index, song.id) {
                                        detectDragGestures(
                                            onDragStart = {
                                                draggedIndex = index
                                                dragOffset = 0f
                                            },
                                            onDragEnd = {
                                                val to = targetIndex(lazyListState, index, dragOffset)
                                                if (to != null && to != index) {
                                                    onCommand { move(index, to, song.id) }
                                                }
                                                draggedIndex = null
                                                dragOffset = 0f
                                            },
                                            onDragCancel = {
                                                draggedIndex = null
                                                dragOffset = 0f
                                            },
                                        ) { change, amount ->
                                            change.consume()
                                            dragOffset += amount.y
                                        }
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                IconButton(
                                    icon = Res.drawable.reorder,
                                    color = if (isDraggingItem) colorPalette().accent else colorPalette().textDisabled,
                                    indication = rippleIndication,
                                    onClick = {},
                                )
                            }
                        }

                        val actions = ItemActions(
                            onPlay = { onCommand { jump(index, song.id) } },
                            onPlayNext = { onCommand { addTracks(listOf(song.id), QueuePosition.Next) } },
                            onEnqueue = { onCommand { addTracks(listOf(song.id), QueuePosition.End) } },
                            enabled = actionsEnabled,
                        )
                        SongItem(
                            song = song,
                            backgroundColor = itemBackground,
                            onLongClick = if (queueFeature) {
                                {
                                    menuState.display {
                                        SongItemMenu(
                                            song = song,
                                            actions = actions,
                                            onRemoveFromQueue = { onCommand { remove(index, song.id) } },
                                        ).MenuComponent()
                                    }
                                }
                            } else {
                                null
                            },
                            trailingContent = {
                                if (!positionLock.isLocked()) {
                                    // Create a fake box to store drag anchor and checkbox
                                    Box(Modifier.width(24.dp))
                                }
                            },
                            onClick = {
                                if (!actionsEnabled) return@SongItem
                                if (index == state.currentIndex && song.id == state.currentTrackId) {
                                    if (state.isPlaying) {
                                        onCommand { pause() }
                                    } else {
                                        onCommand { play() }
                                    }
                                } else {
                                    onCommand { jump(index, song.id) }
                                }

                                search.hideIfEmpty()
                            },
                        )
                    }
                }
            }

            // Search box
            val searchBottomPadding by animateDpAsState(if (search.isVisible) 64.dp else 0.dp)
            Box(
                modifier = Modifier.fillMaxWidth()
                    .background(colorPalette().background1)
                    .padding(bottom = searchBottomPadding),
            ) { search.SearchBar() }
        }

        FloatingActionsContainerWithScrollToTop(
            lazyListState = lazyListState,
            modifier = Modifier.padding(bottom = Dimensions.miniPlayerHeight),
        )
    }
}

/**
 * Where a dragged row lands: the index of the visible row whose centre is the nearest to the dragged row's
 * centre, moved by [offset] px; `null` when the row is not laid out.
 */
private fun targetIndex(state: LazyListState, from: Int, offset: Float): Int? {
    val visible = state.layoutInfo.visibleItemsInfo
    val dragged = visible.firstOrNull { it.key.toString().substringBefore(':').toIntOrNull() == from } ?: return null
    val center = dragged.offset + dragged.size / 2f + offset
    return visible.minByOrNull { kotlin.math.abs(it.offset + it.size / 2f - center) }
        ?.key?.toString()?.substringBefore(':')?.toIntOrNull()
}

/*
 * Ports of the queue's toolbar buttons (phone's `app/n_zik/android/components/ui/screens/player/Queue.kt`).
 * Their commands go to the phone; [enabled] is `false` while the queue cannot be changed (the phone's Listen
 * Together lock plays the same role there).
 */

/** Port of `Repeat`: the repeat mode, `off` → `one` → `all` (`player/repeat`). */
class Repeat(
    state: PlayerState,
    private val onCommand: CommandLauncher,
    private val enabled: Boolean,
) : MenuIcon, Descriptive {

    val type: QueueLoopType = QueueLoopType.from(state.repeatMode)

    override val iconId: DrawableResource = Res.drawable.repeat
    override val messageId: StringResource = Res.string.repeat
    override val menuIconTitle: String
        @Composable
        get() = stringResource(messageId)
    override val icon: androidx.compose.ui.graphics.painter.Painter
        @Composable
        get() = painterResource(type.iconId)

    override val isEnabled: Boolean
        get() = enabled

    override fun onShortClick() {
        if (!enabled) return
        val next = type.next().type
        onCommand { setRepeat(next) }
    }
}

/**
 * Port of `ShuffleQueue`: scrolls to the top then shuffles. The phone shuffles its queue; the contract turns
 * the shuffle mode on or off (`player/shuffle`, which reorders the queue), so the button toggles it.
 * Dropped: the selection (no multi-selection) and the confirmation flash of the icon.
 */
class ShuffleQueue(
    private val state: PlayerState,
    private val lazyListState: LazyListState,
    private val coroutineScope: CoroutineScope,
    private val onCommand: CommandLauncher,
    private val enabled: Boolean,
) : MenuIcon, Descriptive {

    override val iconId: DrawableResource = Res.drawable.shuffle
    override val messageId: StringResource = Res.string.shuffle
    override val menuIconTitle: String
        @Composable
        get() = stringResource(messageId)

    override val isEnabled: Boolean
        get() = enabled

    override fun onShortClick() {
        if (!enabled) return
        val shuffled = !state.shuffle
        coroutineScope.launch {
            lazyListState.smoothScrollToTop()
        }.invokeOnCompletion {
            onCommand { setShuffle(shuffled) }
        }
    }
}

/**
 * Port of `DeleteFromQueue`: "Remove from queue" with the phone's confirmation ("Do you really want to clean
 * queue?"); without a selection (none on the PC) it empties the queue (`queue/clear`).
 */
@Composable
fun DeleteFromQueue(
    enabled: Boolean,
    onDeleteConfirm: ConfirmDialog.() -> Unit,
): DeleteFromQueueButton {
    val activeState = rememberSaveable { mutableStateOf(false) }
    return DeleteFromQueueButton(activeState, enabled, onDeleteConfirm)
}

class DeleteFromQueueButton(
    private val activeState: androidx.compose.runtime.MutableState<Boolean>,
    private val enabled: Boolean,
    private val onDeleteConfirm: ConfirmDialog.() -> Unit,
) : MenuIcon, Descriptive, ConfirmDialog {
    override val iconId: DrawableResource = Res.drawable.trash
    override val messageId: StringResource = Res.string.remove_from_queue
    override val menuIconTitle: String
        @Composable
        get() = stringResource(messageId)
    override val dialogTitle: String
        @Composable
        get() = stringResource(Res.string.clean_queue_confirm)

    override val isEnabled: Boolean
        get() = enabled

    override var isActive: Boolean by activeState

    override fun onShortClick() {
        if (enabled) isActive = !isActive
    }

    override fun onConfirm() = onDeleteConfirm()
}

/** Port of `QueueArrow`: the arrow that closes the queue (shown by default). */
fun QueueArrow(
    onShortClick: () -> Unit,
): Icon = object : Icon {
    override val isEnabled: Boolean = true
    override val iconId: DrawableResource = Res.drawable.chevron_down

    override fun onShortClick() = onShortClick()
}
