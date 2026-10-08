package app.n_zik.compagnon.components.themed

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.n_zik.compagnon.generated.resources.*
import app.n_zik.compagnon.utils.ScrollingInfo
import app.n_zik.compagnon.utils.scrollingInfo
import app.n_zik.compagnon.utils.smoothScrollToTop
import kotlinx.coroutines.launch

/*
 * Port of the "scroll to top" button of the phone's
 * `app/it/fast4x/rimusic/ui/components/themed/FloatingActionsContainer.kt` (128-300).
 * Dropped: the second floating icon (the landscape bars toggle and the optional floating search /
 * settings menu, off by default), and moving the button by a long-press drag (offset preference).
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
