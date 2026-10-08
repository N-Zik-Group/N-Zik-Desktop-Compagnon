package app.n_zik.compagnon.components.player

import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import app.n_zik.compagnon.colorPalette
import app.n_zik.compagnon.components.ui.screens.player.Queue
import app.n_zik.compagnon.topUiRoundnessShape
import kotlinx.coroutines.launch

/**
 * Port of `MiniPlayerQueueOverlay` (phone's `app/n_zik/android/components/player/MiniPlayerQueueOverlay.kt`),
 * the copy of the player's inline queue panel opened from the mini-player (`QueueType.Modern`): it rises to
 * 65 % of the window with a spring (damping 0.8, stiffness 300) over a black scrim (50 % at full height), its
 * 48 dp drag handle (`background0` at 50 %, 40 × 4 dp bar) resizes it (above 85 %: full height, below 40 %:
 * closed), the [QueueToolBar] slides in at its bottom. A click on the scrim closes it.
 * Pulled up, it stops under the [APP_HEADER_HEIGHT] header: the PC's equivalent of the phone's status bar
 * exclusion (`MiniPlayerQueueOverlay.kt` 107-115, [queuePanelMaxFraction]).
 * Dropped: the system back (handled by the window's back key, `MainActivity`) and the navigation-bar strip (a
 * desktop window has no navigation bar).
 */
@Composable
fun MiniPlayerQueueOverlay(
    showSheet: Boolean,
    onDismiss: () -> Unit,
) {
    // Inline resizable queue panel — exact copy of the player's
    val queuePanelHeightFraction = remember { Animatable(0f) }
    var isQueuePanelVisible by remember { mutableStateOf(false) }
    val queuePanelCoroutineScope = rememberCoroutineScope()

    LaunchedEffect(showSheet) {
        if (showSheet) {
            isQueuePanelVisible = true
            queuePanelHeightFraction.snapTo(0f)
            queuePanelHeightFraction.animateTo(
                targetValue = 0.65f,
                animationSpec = spring(dampingRatio = 0.8f, stiffness = 300f),
            )
        } else if (isQueuePanelVisible) {
            queuePanelHeightFraction.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 200, easing = LinearOutSlowInEasing),
            )
            isQueuePanelVisible = false
        }
    }

    if (isQueuePanelVisible) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val density = LocalDensity.current
            val screenHeightPx = with(density) { maxHeight.roundToPx() }
            val maxFraction = queuePanelMaxFraction(screenHeightPx, with(density) { APP_HEADER_HEIGHT.roundToPx() })

            QueuePanel(
                queuePanelHeightFraction = queuePanelHeightFraction,
                screenHeightPx = screenHeightPx,
                maxFraction = maxFraction,
                onDismiss = onDismiss,
                onDrag = { block -> queuePanelCoroutineScope.launch { block() } },
                handleColor = Color.White,
            )
        }
    }
}

/** Height of the [app.n_zik.compagnon.components.ui.header.AppHeader] (phone's `AppHeader.kt`: 64 dp). */
internal val APP_HEADER_HEIGHT = 64.dp

/**
 * The highest fraction of the window the queue panel may take: the window minus the header, as the phone's
 * window minus its status bar (`MiniPlayerQueueOverlay.kt` 115, `Player.kt` 2378-2390).
 */
internal fun queuePanelMaxFraction(windowHeightPx: Int, headerHeightPx: Int): Float =
    if (windowHeightPx <= 0) 1f
    else ((windowHeightPx - headerHeightPx).toFloat() / windowHeightPx).coerceIn(0.1f, 1f)

/**
 * The [QueueToolBar]'s slide / fade progress for a panel fraction: the phone's `(f − 0.55) / 0.1`
 * (`MiniPlayerQueueOverlay.kt` 228). In float, 0.65 (the panel's rest height) gives 0.9999996 and not 1:
 * the value is snapped to 1 within 1e-4, so the toolbar is fully shown at rest.
 */
internal fun queueToolBarProgress(fraction: Float): Float {
    val progress = ((fraction - 0.55f) / 0.1f).coerceIn(0f, 1f)
    return if (progress > 1f - 1e-4f) 1f else progress
}

/**
 * The queue panel shared by the player and [MiniPlayerQueueOverlay] (the phone copies the same code in both,
 * `Player.kt` 2378-2523): scrim, panel resized from its handle, [Queue], [QueueToolBar].
 */
@Composable
internal fun BoxScope.QueuePanel(
    queuePanelHeightFraction: Animatable<Float, *>,
    screenHeightPx: Int,
    maxFraction: Float,
    onDismiss: () -> Unit,
    onDrag: (suspend () -> Unit) -> Unit,
    handleColor: Color,
) {
    val density = LocalDensity.current

    // Scrim — read fraction inside graphicsLayer to avoid recomposition
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                alpha = (queuePanelHeightFraction.value / 0.65f).coerceIn(0f, 1f) * 0.5f
            }
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onDismiss() },
    )

    // Queue panel — height controlled via Modifier.layout to avoid recomposition
    // QueueType.Modern: transparent panel
    val queuePanelBackground = Color.Transparent
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .align(Alignment.BottomCenter)
            .layout { measurable, constraints ->
                // Read fraction in LAYOUT phase (not composition) → no recomposition
                val f = queuePanelHeightFraction.value.coerceIn(0.01f, 1f)
                val panelHeight = (constraints.maxHeight * f).toInt().coerceAtLeast(1)
                val placeable = measurable.measure(
                    constraints.copy(minHeight = panelHeight, maxHeight = panelHeight),
                )
                layout(placeable.width, panelHeight) {
                    placeable.placeRelative(0, 0)
                }
            }
            .clip(topUiRoundnessShape())
            .background(queuePanelBackground)
            // Clicks inside the panel never reach the scrim
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
    ) {
        // Queue content - padding top for drag handle
        Box(
            modifier = Modifier
                .padding(top = 48.dp),
        ) {
            Queue(
                onDismiss = { onDismiss() },
            )
        }

        // Drag handle - overlays at top
        val handleAlpha = 0.5f
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .align(Alignment.TopCenter)
                .clip(topUiRoundnessShape())
                .background(colorPalette().background0.copy(alpha = handleAlpha))
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragEnd = {
                            onDrag {
                                val currentFraction = queuePanelHeightFraction.value
                                when {
                                    currentFraction > 0.85f -> queuePanelHeightFraction.animateTo(
                                        maxFraction,
                                        spring(dampingRatio = 0.8f, stiffness = 300f),
                                    )
                                    currentFraction < 0.4f -> onDismiss()
                                    else -> queuePanelHeightFraction.animateTo(
                                        0.65f,
                                        spring(dampingRatio = 0.8f, stiffness = 300f),
                                    )
                                }
                            }
                        },
                        onVerticalDrag = { _, dragAmount ->
                            onDrag {
                                val newFraction = (queuePanelHeightFraction.value - dragAmount / screenHeightPx)
                                    .coerceIn(0.1f, maxFraction)
                                queuePanelHeightFraction.snapTo(newFraction)
                            }
                        },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(handleColor),
            )
        }

        // QueueToolBar at bottom of panel — slides down and fades out when closing.
        // PC (story 11c, the mini-player hidden at 65 %): on the desktop an alpha below 1 renders the layer
        // offscreen, clipped to the toolbar's 60 dp, and the mini-player it draws 72 dp above was cut away
        // until the panel was pulled higher (alpha 0.9999996 at rest). The alpha is applied per draw instead
        // (`ModulateAlpha`, no offscreen clip), and the rest progress is snapped to 1 ([queueToolBarProgress]).
        QueueToolBar(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .graphicsLayer {
                    val toolbarProgress = queueToolBarProgress(queuePanelHeightFraction.value)
                    translationY = with(density) { (1f - toolbarProgress) * 100.dp.toPx() }
                    alpha = toolbarProgress
                    compositingStrategy = CompositingStrategy.ModulateAlpha
                },
        )
    }
}
