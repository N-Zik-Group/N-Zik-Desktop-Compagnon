package app.n_zik.compagnon.components.ui.screens.player

import app.n_zik.compagnon.components.tab.ShuffleOkFlash
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
import androidx.compose.runtime.MutableState
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import app.n_zik.compagnon.CommandLauncher
import app.n_zik.compagnon.LocalCommandLauncher
import app.n_zik.compagnon.LocalLibraryActions
import app.n_zik.compagnon.LocalPlayerRepository
import app.n_zik.compagnon.bridge.ConnectionState
import app.n_zik.compagnon.bridge.library.DislikeMode
import app.n_zik.compagnon.bridge.state.PlayerState
import app.n_zik.compagnon.bridge.state.QueuePosition
import app.n_zik.compagnon.bridge.state.SessionContract
import app.n_zik.compagnon.colorPalette
import app.n_zik.compagnon.components.LocalMenuState
import app.n_zik.compagnon.components.SongItem
import app.n_zik.compagnon.components.SongItemPlaceholder
import app.n_zik.compagnon.components.menu.playlist.PlaylistsMenu
import app.n_zik.compagnon.components.menu.song.SongItemMenu
import app.n_zik.compagnon.components.player.QueueToolBarState
import app.n_zik.compagnon.components.player.rotateTrackLike
import app.n_zik.compagnon.components.styling.Dimensions
import app.n_zik.compagnon.components.tab.Locator
import app.n_zik.compagnon.components.tab.PositionLock
import app.n_zik.compagnon.components.tab.Search
import app.n_zik.compagnon.components.tab.rememberItemSelector
import app.n_zik.compagnon.components.tab.toolbar.ConfirmDialog
import app.n_zik.compagnon.components.tab.toolbar.Descriptive
import app.n_zik.compagnon.components.tab.toolbar.Icon
import app.n_zik.compagnon.components.tab.toolbar.InertButton
import app.n_zik.compagnon.components.tab.toolbar.MenuIcon
import app.n_zik.compagnon.components.themed.FloatingActionsContainerWithScrollToTop
import app.n_zik.compagnon.components.themed.IconButton
import app.n_zik.compagnon.components.themed.SwipeableRow
import app.n_zik.compagnon.components.ui.screens.home.ItemActions
import app.n_zik.compagnon.enums.QueueLoopType
import app.n_zik.compagnon.enums.QueueSwipeAction
import app.n_zik.compagnon.generated.resources.*
import app.n_zik.compagnon.utils.LocalPreferences
import app.n_zik.compagnon.utils.Toaster
import app.n_zik.compagnon.utils.UserSettings
import app.n_zik.compagnon.utils.cleanPrefix
import app.n_zik.compagnon.utils.shimmer
import app.n_zik.compagnon.utils.smoothScrollToTop
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
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
 * The toolbar holds the phone's full order, Discover included (hidden by the phone's default
 * preferences, shown here — spec #46): the download buttons, the item selector and the CSV export are
 * placeholders without a contract route, a click does nothing; "Add to playlist" opens the
 * [PlaylistsMenu] layer (spec #47, the phone's `Queue.kt` 43-72): the playlists already read, everything
 * inert there.
 * Kept: the swipe actions (phone's `SwipeableContent.kt` 82-155, 158-269, ported as
 * [SwipeableRow]: the 25 % positional threshold, always snap-back, the revealed icon by the
 * phone's `queueSwipeLeftAction` / `queueSwipeRightAction` keys, off with the phone's
 * `isSwipeToActionEnabled` key — left `RemoveFromQueue` resolves the row's actual index by id at
 * the swipe then removes it and toasts the phone's "deleted" of the cleaned title, right
 * `PlayNext` moves the row after the current track (the phone's `moveMediaItem(index, current + 1)`
 * with its after / before cases), `Enqueue` appends it, `Favourite` likes it (the rotation, a no-op
 * without `library.write`), `Download` is a no-op (no download on the PC)). "Remove from queue"
 * also moves to the item's menu (PC only, no swipe there).
 * Dropped: the multi-selection, the radio's loading placeholders (kept rendered, the condition
 * `false` — the wire carries no radio loading state, the radio plays like any track), the
 * remembered scroll position across openings (the list opens on the current track). Clearing shows
 * the phone's "Deleted" toast (its `Queue.kt` 301-303).
 * Without the `queue` feature the item menu still opens (as on the phone) with inert entries; queue actions do
 * nothing outside a `Live` session.
 * The clear's "Deleted" toast (the phone's 301-303, after its local clear) is shown when the `clear`
 * command is sent, before the phone answers (optimistic; a late failure toasts its own error).
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

    // The phone's `queueSwipeLeftActionKey` / `queueSwipeRightActionKey` / `isSwipeToActionEnabledKey`
    val preferences = LocalPreferences.current
    val settings by (preferences?.settings ?: remember { MutableStateFlow(UserSettings()) }).collectAsState()
    val queueSwipeLeftAction = settings.queueSwipeLeftActionValue
    val queueSwipeRightAction = settings.queueSwipeRightActionValue
    val isSwipeToActionEnabled = settings.isSwipeToActionEnabled

    // The like of the `Favourite` swipe (the phone's `YouTubeSync.rotateSongLikeState`, its
    // `SwipeableContent.kt` 224-228): the rotation, the phone's "disliked" mode deciding it (its
    // `Player.kt` heart pattern), a no-op without `library.write`
    val likeWrites = LocalLibraryActions.current?.takeIf { it.canWrite }
    // Collected unconditionally (no conditional composable call): a remembered empty flow without the lists
    val dislikeModeFlow = LocalLibraryActions.current?.lists?.dislikeMode
        ?: remember { MutableStateFlow<DislikeMode?>(null) }
    val likeRotation = dislikeModeFlow.collectAsState().value?.songs != false

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
            // The phone's toast after clearing its queue (its `Queue.kt` 301-303)
            Toaster.s(Res.string.deleted)
            onDismiss(repeat.type)
        }
        val queueArrow = QueueArrow { onDismiss(repeat.type) }
        val locator = Locator(lazyListState, { windowsOnDisplay.map { it.value } }, dedupedRows = false, onMessage = { Toaster.i(it) })

        // Dialog renders
        deleteDialog.Render()

        // The phone's item selector (a local toggle, never persisted): the rows' selection boxes
        val itemSelector = rememberItemSelector()

        // The phone's toolbar order; Locator, Search, position lock, Repeat, Shuffle and "Remove from
        // queue" are wired to the contract, the rest are placeholders without a contract route
        val buttonsList = buildList {
            add(locator)
            add(search)
            // Spec #46: the phone's "Discover" (its `Queue.kt` 345-350, 3rd position — the phone's default
            // preferences hide it): inert — no contract route, no discovery on the PC
            add(InertButton(Res.drawable.discover, Res.string.discover, descriptionId = Res.string.discoverinfo))
            add(InertButton(Res.drawable.downloaded, Res.string.info_download_all_songs))
            add(InertButton(Res.drawable.download, Res.string.info_remove_all_downloaded_songs))
            if (queueFeature) {
                add(positionLock)
                add(repeat)
                add(shuffle)
            }
            add(itemSelector)
            if (queueFeature) {
                add(deleteDialog)
            }
            // Spec #47: the phone's "Add to playlist" (its `Queue.kt` 43-72): opens the [PlaylistsMenu]
            // layer — the playlists already read, everything inert there (no add route in the contract)
            add(
                InertButton(
                    Res.drawable.add_in_playlist,
                    Res.string.add_to_playlist,
                    onOpen = { menuState.display { PlaylistsMenu() } },
                ),
            )
            add(InertButton(Res.drawable.export_outline, Res.string.export_playlist))
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
                            // Phone's `Queue.kt` 435-437
                            .animateItem()
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
                        // The phone's `SwipeableQueueItem` (its `Queue.kt` 469-575): the row behind its
                        // swipe, the drag handle staying in front of it
                        SwipeableRow(
                            song = song,
                            swipeLeftAction = queueSwipeLeftAction,
                            swipeRightAction = queueSwipeRightAction,
                            swipeEnabled = isSwipeToActionEnabled,
                            onPlayNext = {
                                // The phone's "Play next" swipe (its `Queue.kt` 477-494): a move after
                                // the current track — if the song is already after the current, only
                                // when it is not already in the next position
                                if (actionsEnabled) {
                                    val currentIndex = state.currentIndex
                                    val targetIndex = currentIndex + 1
                                    if (index > currentIndex) {
                                        if (index != targetIndex) {
                                            onCommand { move(index, targetIndex, song.id) }
                                        }
                                    } else {
                                        // If it's before or in the current position, move it to the next
                                        onCommand { move(index, targetIndex, song.id) }
                                    }
                                }
                            },
                            onEnqueue = {
                                if (actionsEnabled) {
                                    onCommand { addTracks(listOf(song.id), QueuePosition.End) }
                                }
                            },
                            onRemoveFromQueue = {
                                // The phone's removal (its `Queue.kt` 504-530): the actual index is
                                // resolved by id at the swipe (the row's captured index can be stale),
                                // then the phone's "deleted" toast of the cleaned title
                                if (actionsEnabled) {
                                    val actualIndex = state.queue.indexOfFirst { it.id == song.id }
                                    if (actualIndex in state.queue.indices) {
                                        onCommand { remove(actualIndex, song.id) }
                                        Toaster.s(Res.string.deleted_item, cleanPrefix(song.title))
                                    }
                                }
                            },
                            onFavourite = {
                                if (live) {
                                    rotateTrackLike(song, likeWrites, likeRotation)
                                }
                            },
                        ) {
                        SongItem(
                            song = song,
                            backgroundColor = itemBackground,
                            itemSelector = itemSelector,
                            // The phone always opens `SongItemMenu` (its 549); without the `queue` feature its
                            // entries stay inert (no "Remove from queue", the queue cannot change)
                            onLongClick = {
                                menuState.display {
                                    SongItemMenu(
                                        song = song,
                                        actions = if (queueFeature) actions else actions.copy(enabled = false),
                                        onRemoveFromQueue = if (queueFeature) ({ onCommand { remove(index, song.id) } }) else null,
                                    ).MenuComponent()
                                }
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
                                    // The phone's `shouldBePlaying` (`playWhenReady`): buffering counts as playing
                                    if (state.shouldBePlaying) {
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

                // The phone's radio loading placeholders (its `Queue.kt` 580-588): the wire carries no
                // radio loading state (the radio plays like any track), so the condition is always
                // `false` on the PC
                val isLoadingRadio = false
                if (isLoadingRadio) {
                    item {
                        Column(Modifier.shimmer()) {
                            repeat(3) { index ->
                                SongItemPlaceholder(Modifier.alpha(1f - index * 0.125f))
                            }
                        }
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
    return visible.minByOrNull { abs(it.offset + it.size / 2f - center) }
        ?.key?.toString()?.substringBefore(':')?.toIntOrNull()
}

/*
 * Ports of the queue's toolbar buttons (phone's `app/n_zik/android/components/ui/screens/player/Queue.kt`).
 * Their commands go to the phone; [enabled] is `false` while the queue cannot be changed (the phone's Listen
 * Together lock plays the same role there).
 */

/**
 * Port of `Repeat`: the repeat mode, `off` → `one` → `all` (`player/repeat`). As the phone's (its `Queue.kt`
 * 109-119) it is not `Descriptive`: no help on a long press / right click.
 */
class Repeat(
    state: PlayerState,
    private val onCommand: CommandLauncher,
    private val enabled: Boolean,
) : MenuIcon {

    val type: QueueLoopType = QueueLoopType.from(state.repeatMode)

    override val iconId: DrawableResource = Res.drawable.repeat
    override val menuIconTitle: String
        @Composable
        get() = stringResource(Res.string.repeat)
    override val icon: Painter
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
 * As on the phone (issue #866) a press lights the app-wide [ShuffleOkFlash]. Dropped: the selection (no
 * multi-selection).
 */
class ShuffleQueue(
    private val state: PlayerState,
    private val lazyListState: LazyListState,
    private val coroutineScope: CoroutineScope,
    private val onCommand: CommandLauncher,
    private val enabled: Boolean,
) : MenuIcon, Descriptive {

    override val iconId: DrawableResource
        get() = if (ShuffleOkFlash.active) Res.drawable.shuffle_ok else Res.drawable.shuffle
    override val messageId: StringResource = Res.string.shuffle
    override val menuIconTitle: String
        @Composable
        get() = stringResource(messageId)

    override val isEnabled: Boolean
        get() = enabled

    override fun onShortClick() {
        if (!enabled) return
        ShuffleOkFlash.trigger()
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
    private val activeState: MutableState<Boolean>,
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
