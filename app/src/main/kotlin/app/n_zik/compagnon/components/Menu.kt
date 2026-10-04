package app.n_zik.compagnon.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.height
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import app.n_zik.compagnon.topUiRoundnessShape
import kotlinx.coroutines.launch

/**
 * Port of `MenuState` (phone's `app/it/fast4x/rimusic/ui/components/Menu.kt` 32): the content of the menu
 * sheet. The phone's back stack of menus (`pop`) is not ported: no library menu opens a sub-menu.
 */
@Stable
class MenuState {
    var content: (@Composable () -> Unit)? by mutableStateOf(null)
        private set

    var isDisplayed: Boolean by mutableStateOf(false)
        private set

    fun display(content: @Composable () -> Unit) {
        this.content = content
        isDisplayed = true
    }

    fun hide() {
        isDisplayed = false
    }
}

val LocalMenuState = staticCompositionLocalOf { MenuState() }

/** Fraction of the window height the menu sheet opens at: the phone's partially-expanded sheet (half). */
internal const val MENU_SHEET_PARTIAL_FRACTION = 0.5f

/** A drag released above this fraction snaps the sheet to the full height (the queue's threshold). */
internal const val MENU_SHEET_FULL_THRESHOLD = 0.85f

/** A drag released below this fraction closes the sheet (the queue's threshold). */
internal const val MENU_SHEET_CLOSE_THRESHOLD = 0.4f

/**
 * The fraction of the window height the sheet animates to when the drag ends at [fraction]: full
 * (1.0) above [MENU_SHEET_FULL_THRESHOLD], the partially-expanded [MENU_SHEET_PARTIAL_FRACTION] in
 * between, `null` when the sheet closes (below [MENU_SHEET_CLOSE_THRESHOLD]).
 */
internal fun menuSheetSnapFraction(fraction: Float): Float? = when {
    fraction > MENU_SHEET_FULL_THRESHOLD -> 1f
    fraction < MENU_SHEET_CLOSE_THRESHOLD -> null
    else -> MENU_SHEET_PARTIAL_FRACTION
}

/**
 * The menu sheet, after the phone's `CustomModalBottomSheet` of `MainActivity.kt` (2268-2300): a Material 3
 * modal bottom sheet with its defaults (scrim black at 32 %, at most 640 dp wide), transparent container,
 * top corners at the UI roundness, no drag handle of its own (each menu draws its own). A click on the
 * scrim or Escape closes it. The phone's sheet opens partially expanded (`skipPartiallyExpanded = false`):
 * half of the window's height, the menu's content filling it and scrolling (`ListMenu`'s `weight(1f)`).
 * The phone's sheet drag is ported (the M3 sheet of `CustomModalBottomSheet`, `MainActivity.kt`
 * 2268-2300): a 48 dp zone along the top edge (the same zone as the queue's handle) moves the sheet
 * between the partial half and the full height; on release it snaps to the full above
 * [MENU_SHEET_FULL_THRESHOLD], closes below [MENU_SHEET_CLOSE_THRESHOLD] and back to the half in
 * between — the drag gesture only consumes moved pointers, so the menus' clicks (handle bar, header
 * icons) pass through the zone unchanged. A new content slides in horizontally (`AnimatedContent`,
 * 300 ms, `MainActivity.kt` 2423-2438).
 */
@Composable
fun BottomSheetMenu(state: MenuState, modifier: Modifier = Modifier) {
    // Fraction of the window height the sheet occupies: the partial half and the full, dragged between
    val sheetFraction = remember { Animatable(MENU_SHEET_PARTIAL_FRACTION) }
    val dragScope = rememberCoroutineScope()
    LaunchedEffect(state.isDisplayed) {
        if (state.isDisplayed) sheetFraction.snapTo(MENU_SHEET_PARTIAL_FRACTION)
    }
    AnimatedVisibility(visible = state.isDisplayed, enter = fadeIn(), exit = fadeOut()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.32f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = state::hide),
        )
    }
    BoxWithConstraints(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        val density = LocalDensity.current
        val sheetMaxHeightPx = with(density) { maxHeight.toPx() }
        val sheetTopGapPx = with(density) { 24.dp.toPx() }
        AnimatedVisibility(
            visible = state.isDisplayed,
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
        ) {
            Box(
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .padding(top = 24.dp)
                    .layout { measurable, constraints ->
                        // Read fraction in LAYOUT phase (not composition) → no recomposition. The 24 dp
                        // top gap is inside the sheet box, so it is subtracted to keep the sheet at
                        // exactly [fraction] of the window height
                        val f = sheetFraction.value.coerceIn(0.05f, 1f)
                        val sheetHeight = (sheetMaxHeightPx * f - sheetTopGapPx).toInt().coerceAtLeast(1)
                        val placeable = measurable.measure(
                            constraints.copy(minHeight = sheetHeight, maxHeight = sheetHeight),
                        )
                        layout(placeable.width, sheetHeight) { placeable.placeRelative(0, 0) }
                    }
                    .clip(topUiRoundnessShape())
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                            state.hide()
                            true
                        } else {
                            false
                        }
                    }
                    // Clicks inside the sheet never reach the scrim
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
            ) {
                AnimatedContent(
                    targetState = state.content,
                    transitionSpec = {
                        slideInHorizontally(animationSpec = tween(300)) { width -> width / 2 } + fadeIn(animationSpec = tween(300)) togetherWith
                            slideOutHorizontally(animationSpec = tween(300)) { width -> -width / 2 } + fadeOut(animationSpec = tween(300))
                    },
                    label = "MenuContentTransition",
                ) { target ->
                    Box(modifier = Modifier.fillMaxWidth()) {
                        target?.invoke()
                    }
                }

                // Drag zone — the ported sheet drag (see the KDoc): 48 dp along the top edge, over the
                // menus' own drag handle. A click is not a drag, so it passes through to the content
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .align(Alignment.TopCenter)
                        .pointerInput(Unit) {
                            detectVerticalDragGestures(
                                onDragEnd = {
                                    dragScope.launch {
                                        val target = menuSheetSnapFraction(sheetFraction.value)
                                        if (target == null) {
                                            state.hide()
                                        } else {
                                            sheetFraction.animateTo(
                                                target,
                                                spring(dampingRatio = 0.8f, stiffness = 300f),
                                            )
                                        }
                                    }
                                },
                                onVerticalDrag = { _, dragAmount ->
                                    dragScope.launch {
                                        sheetFraction.snapTo(
                                            (sheetFraction.value - dragAmount / sheetMaxHeightPx).coerceIn(0.15f, 1f),
                                        )
                                    }
                                },
                            )
                        },
                )
            }
        }
    }
}
