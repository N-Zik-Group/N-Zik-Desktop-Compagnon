package app.n_zik.compagnon.components.themed

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.material3.Icon
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.n_zik.compagnon.colorPalette
import app.n_zik.compagnon.components.styling.Dimensions
import app.n_zik.compagnon.components.theme.favoritesIcon
import app.n_zik.compagnon.generated.resources.*
import app.n_zik.compagnon.uiRoundnessShape
import app.n_zik.compagnon.utils.LocalPreferences
import app.n_zik.compagnon.utils.ScrollingInfo
import app.n_zik.compagnon.utils.Toaster
import app.n_zik.compagnon.utils.UserSettings
import app.n_zik.compagnon.utils.scrollingInfo
import app.n_zik.compagnon.utils.smoothScrollToTop
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/*
 * Port of the "scroll to top" button and, since spec `spec-fill-pc-ui-gaps-home` #64, the phone's
 * floating multi-action icon of the phone's
 * `app/it/fast4x/rimusic/ui/components/themed/FloatingActionsContainer.kt` (128-300, 196-246).
 * Dropped (scroll to top): the landscape bars toggle. Dropped (multi-FAB): the phone's
 * `useAsActionsMenu` shortcut, its `BackHandler`, its per-frame offset persistence — see
 * [MultiFloatingActionsContainer].
 */

/**
 * `floatingActionsBottomPadding()`: no system bar on PC, plus the collapsed mini player (72 dp with the
 * floating bar), plus the room of a bottom navigation bar (40 dp): the button stays above the
 * floating bar as on the phone.
 */
private val FLOATING_ACTIONS_BOTTOM_PADDING: Dp = 72.dp + 40.dp

@Composable
fun BoxScope.FloatingActionsContainerWithScrollToTop(lazyGridState: LazyGridState, modifier: Modifier = Modifier) {
    val transitionState = remember {
        MutableTransitionState<ScrollingInfo?>(ScrollingInfo())
    }.apply { targetState = lazyGridState.scrollingInfo() }

    FloatingActions(transitionState = transitionState, onScrollToTop = lazyGridState::smoothScrollToTop, modifier = modifier)
}

@Composable
fun BoxScope.FloatingActionsContainerWithScrollToTop(lazyListState: LazyListState, modifier: Modifier = Modifier) {
    val transitionState = remember {
        MutableTransitionState<ScrollingInfo?>(ScrollingInfo())
    }.apply { targetState = lazyListState.scrollingInfo() }

    FloatingActions(transitionState = transitionState, onScrollToTop = lazyListState::smoothScrollToTop, modifier = modifier)
}

@Composable
fun BoxScope.FloatingActions(
    transitionState: MutableTransitionState<ScrollingInfo?>,
    onScrollToTop: suspend () -> Unit,
    modifier: Modifier = Modifier,
) {
    val transition = rememberTransition(transitionState, "")

    val modifierActions = Modifier
        .padding(bottom = 16.dp)
        .padding(bottom = FLOATING_ACTIONS_BOTTOM_PADDING)

    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Bottom,
        modifier = modifier
            .align(Alignment.BottomEnd)
            .padding(end = 16.dp),
    ) {
        transition.AnimatedVisibility(
            visible = { it?.isScrollingDown == false && it.isFar },
            enter = slideInVertically(tween(500, 0)) { it },
            exit = slideOutVertically(tween(500, 0)) { it },
        ) {
            val coroutineScope = rememberCoroutineScope()
            PrimaryButton(
                iconId = Res.drawable.chevron_up,
                onClick = {
                    coroutineScope.launch {
                        onScrollToTop()
                    }
                },
                enabled = transition.targetState?.isScrollingDown == false && transition.targetState?.isFar == true,
                modifier = modifierActions,
            )
        }
    }
}

/**
 * The state of the phone's multi floating actions button (its `MultiFloatingActionsButton.kt` 64-66).
 */
enum class MultiFabState {
    Collapsed,
    Expanded,
}

/**
 * An item of the phone's expanded floating actions menu (its `FabItem`, its
 * `MultiFloatingActionsButton.kt` 67-71), with the PC's [DrawableResource] icon and the
 * [onFabItemLongClicked] the phone never needed (its items always navigate).
 */
class FabItem(
    val icon: DrawableResource,
    val label: String,
    val onFabItemClicked: () -> Unit,
    val onFabItemLongClicked: (() -> Unit)? = null,
)

/**
 * The phone's `floatingActionsBottomPadding()` (its `FloatingActionsContainer.kt` 55-70),
 * PC-adapted: no system-bar inset, the collapsed mini player (72 dp), and the phone's 40 dp of
 * extra space over a bottom navigation bar — the PC's bar position (the phone's
 * `navigationBarPosition`) decides (`Bottom` / `BottomFloating` only, like the phone).
 */
@Composable
private fun multiFabBottomPadding(): Dp {
    val preferences = LocalPreferences.current
    val settings = (preferences?.settings ?: remember { MutableStateFlow(UserSettings()) }).collectAsState().value
    val additionalBottomPadding =
        if (settings.navigationBarPositionValue.isBottomType) {
            Dimensions.additionalVerticalSpaceForFloatingAction
        } else {
            0.dp
        }
    return Dimensions.collapsedPlayer + additionalBottomPadding
}

/**
 * Port of `MultiFloatingActionsContainer` (phone's
 * `app/it/fast4x/rimusic/ui/components/themed/FloatingActionsContainer.kt` 196-246) +
 * `MultiFloatingActionsButton` (its `app/it/fast4x/rimusic/ui/components/MultiFloatingActionsButton.kt`):
 * the phone's floating "search" actions menu of the home tabs (spec
 * `spec-fill-pc-ui-gaps-home` #64), BottomEnd with the phone's 16 dp end padding and its
 * [multiFabBottomPadding].
 *
 * PC adaptations (the phone's home variant is a tap shortcut to its search screen, the expansion
 * being reserved for its `useAsActionsMenu` variant):
 * - the phone's `useAsActionsMenu` flag is dropped: the main tap always toggles the expansion
 *   (the spec's "tap → expansion"), the main icon is always the phone's `search`;
 * - the phone's `BackHandler` (collapsing on the back key) is dropped (no back key on the PC) —
 *   tapping the scrim collapses it, as on the phone;
 * - the phone's per-drag-delta offset persistence (its `multiFloatActionIconOffsetX/Y` written on
 *   every drag delta) becomes local state during the long-press drag, persisted once on the drag
 *   end — the PC's settings update rewrites the whole file, the phone's preference manager wrote
 *   one key;
 * - the "Réglages" item is inert (no contract route for the phone's home dialogs — its
 *   `UiSettings` is a phone build): a long press toasts the phone's `settings`
 *   title, like the PC's other inert placeholders; the "Recherche" item runs the tab's own search
 *   ([onClickSearch], a no-op on a tab without a wired search).
 *
 * The rest is faithful: the 64 × 64 dp main FAB (`background2`, the phone's 45° spring rotation,
 * 24 dp icon), the 20 dp item spacing, the label + `SmallFloatingActionButton` items (`background2`
 * containers, `favoritesIcon` icons, 24 dp) and the phone's 400 dp expansion zone with its
 * `favoritesIcon` circle scrim (scale 2.2 / 2.1, translate 150 / 300, scale 5, radius 200 dp).
 */
@Composable
fun BoxScope.MultiFloatingActionsContainer(
    onClickSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Bottom,
        modifier = modifier
            .align(Alignment.BottomEnd)
            .padding(end = 16.dp)
            .padding(bottom = multiFabBottomPadding()),
    ) {
        MultiFloatingActionsButton(
            fabIcon = painterResource(Res.drawable.search),
            items = listOf(
                FabItem(
                    icon = Res.drawable.settings,
                    label = stringResource(Res.string.txt_settings),
                    // Inert — no contract route for the phone's home dialogs
                    onFabItemClicked = {},
                    onFabItemLongClicked = { Toaster.i(Res.string.txt_settings) },
                ),
                FabItem(
                    icon = Res.drawable.search,
                    label = stringResource(Res.string.search),
                    onFabItemClicked = { onClickSearch() },
                ),
            ),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MultiFloatingActionsButton(
    fabIcon: Painter,
    items: List<FabItem>,
    showLabels: Boolean = true,
) {
    val preferences = LocalPreferences.current
    var currentState by remember { mutableStateOf(MultiFabState.Collapsed) }
    val stateTransition: Transition<MultiFabState> =
        updateTransition(targetState = currentState, label = "")
    // PC adaptation (the phone's `useAsActionsMenu` variant is dropped): the main tap always
    // toggles the expansion
    val stateChange: () -> Unit = {
        currentState = if (stateTransition.currentState == MultiFabState.Expanded) {
            MultiFabState.Collapsed
        } else {
            MultiFabState.Expanded
        }
    }

    val rotation: Float by stateTransition.animateFloat(
        transitionSpec = {
            if (targetState == MultiFabState.Expanded) {
                spring(stiffness = Spring.StiffnessLow)
            } else {
                spring(stiffness = Spring.StiffnessMedium)
            }
        },
        label = "",
    ) { state ->
        if (state == MultiFabState.Expanded) 45f else 0f
    }

    // The phone's long-press drag offsets (its `multiFloatActionIconOffsetX/Y`): read once from the
    // settings, moved locally during the drag, persisted on the drag end (the phone persists on
    // every drag delta — see the KDoc above)
    val settings by (preferences?.settings ?: remember { MutableStateFlow(UserSettings()) }).collectAsState()
    var offsetX by remember { mutableFloatStateOf(settings.multiFloatActionIconOffsetX) }
    var offsetY by remember { mutableFloatStateOf(settings.multiFloatActionIconOffsetY) }

    // The phone's scrim of the expanded state: tapping anywhere on the screen collapses it (its
    // back handler is dropped — no back key on the PC)
    Box(
        modifier = if (currentState == MultiFabState.Expanded) {
            Modifier
                .fillMaxSize()
                .clip(uiRoundnessShape())
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                ) {
                    currentState = MultiFabState.Collapsed
                }
        } else {
            Modifier.fillMaxSize()
        },
        contentAlignment = Alignment.BottomEnd,
    ) {
        Box(Modifier.fillMaxWidth().height(400.dp), contentAlignment = Alignment.BottomEnd) {
            if (currentState == MultiFabState.Expanded) {
                val color = colorPalette().favoritesIcon.copy(0.85f)
                Canvas(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = 2.2f
                            scaleY = 2.1f
                        },
                ) {
                    translate(150f, top = 300f) {
                        scale(5f) {}
                        drawCircle(color, radius = 200.dp.toPx())
                    }
                }
            }

            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.Bottom) {
                items.forEach { item ->
                    SmallFloatingActionButtonRow(item = item, showLabel = showLabels, stateTransition = stateTransition)
                    Spacer(Modifier.height(20.dp))
                }
                Box(
                    modifier = Modifier
                        .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                        .pointerInput(Unit) {
                            detectDragGesturesAfterLongPress(
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    offsetX += dragAmount.x
                                    offsetY += dragAmount.y
                                },
                                // The phone's per-frame persistence becomes one settings write on
                                // the drag end (see the KDoc above)
                                onDragEnd = {
                                    preferences?.update {
                                        it.copy(
                                            multiFloatActionIconOffsetX = offsetX,
                                            multiFloatActionIconOffsetY = offsetY,
                                        )
                                    }
                                },
                            )
                        }
                        .clip(uiRoundnessShape())
                        .background(colorPalette().background2)
                        .height(64.dp)
                        .width(64.dp)
                        .clip(uiRoundnessShape())
                        .combinedClickable(onClick = stateChange),
                ) {
                    Image(
                        painter = fabIcon,
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(colorPalette().text),
                        modifier = Modifier
                            .rotate(rotation)
                            .align(Alignment.Center)
                            .size(24.dp),
                    )
                }
            }
        }
    }
}

/**
 * The phone's `SmallFloatingActionButtonRow` (its `MultiFloatingActionsButton.kt` 240-293): the
 * item's label (the PC's inert long press for "Réglages") and its `SmallFloatingActionButton` —
 * both fading / scaling in on the expansion with the phone's 50 ms.
 */
@Composable
private fun SmallFloatingActionButtonRow(
    item: FabItem,
    showLabel: Boolean,
    stateTransition: Transition<MultiFabState>,
) {
    val alpha: Float by stateTransition.animateFloat(
        transitionSpec = {
            tween(durationMillis = 50)
        },
        label = "",
    ) { state ->
        if (state == MultiFabState.Expanded) 1f else 0f
    }
    val scale: Float by stateTransition.animateFloat(
        label = "",
    ) { state ->
        if (state == MultiFabState.Expanded) 1.0f else 0f
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .alpha(animateFloatAsState(alpha, label = "").value)
            .scale(animateFloatAsState(targetValue = scale, label = "").value),
    ) {
        if (showLabel) {
            Text(
                text = item.label,
                modifier = Modifier
                    .padding(start = 6.dp, end = 6.dp, top = 4.dp, bottom = 4.dp)
                    .clip(uiRoundnessShape())
                    .combinedClickable(
                        onClick = { item.onFabItemClicked() },
                        onLongClick = item.onFabItemLongClicked,
                    ),
            )
        }
        // The PC's Material3 `SmallFloatingActionButton` has no `onLongClick` (the phone's own
        // label/button do not need one either): the inert "Réglages" long press lives on the label
        SmallFloatingActionButton(
            shape = uiRoundnessShape(),
            modifier = Modifier.padding(4.dp),
            onClick = { item.onFabItemClicked() },
            containerColor = colorPalette().background2,
            contentColor = colorPalette().favoritesIcon,
        ) {
            Icon(
                painter = painterResource(item.icon),
                contentDescription = item.label,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}
