package app.n_zik.compagnon.components.ui.screens.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import app.n_zik.compagnon.colorPalette
import kotlin.math.round
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/*
 * Port of the phone's `app/n_zik/android/components/ui/screens/home/CollapsibleHeader.kt`. A desktop window
 * has no status bar: `titleStatusBarAlpha` is always 1 and is not ported.
 */

private const val SNAP_DURATION_MS = 200

internal fun collapsibleHeaderSnapTarget(offset: Float, headerHeight: Int): Float {
    if (headerHeight <= 0) return 0f
    val threshold = -headerHeight / 2f
    return if (offset < threshold) -headerHeight.toFloat() else 0f
}

private const val TITLE_FADE_FRACTION = 0.3f

internal fun collapsibleTitleAlpha(offset: Float, headerHeight: Int): Float {
    if (headerHeight <= 0) return 1f
    return (1f + offset / (headerHeight * TITLE_FADE_FRACTION)).coerceIn(0f, 1f)
}

/** The title row (section title + count) fades out over the first 30 % of the header's collapse. */
@Composable
fun CollapsibleTitleRow(
    titleOffset: MutableFloatState,
    titleHeight: MutableIntState,
    content: @Composable () -> Unit,
) {
    Box(
        Modifier
            .graphicsLayer {
                alpha = collapsibleTitleAlpha(titleOffset.floatValue, titleHeight.intValue)
            },
    ) {
        content()
    }
}

internal fun collapsibleHeaderConnection(
    headerHeight: Int,
    headerOffset: MutableFloatState,
    scope: CoroutineScope,
    snapJob: MutableState<Job?>,
    enabled: Boolean,
): NestedScrollConnection = object : NestedScrollConnection {
    private var pendingDelta = 0f

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (!enabled) return Offset.Zero
        val delta = available.y
        if (delta != 0f) {
            snapJob.value?.cancel()
            snapJob.value = null
        }
        val before = headerOffset.floatValue
        headerOffset.floatValue = (before + delta).coerceIn(-headerHeight.toFloat(), 0f)
        pendingDelta = headerOffset.floatValue - before
        return Offset.Zero
    }

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        if (!enabled || pendingDelta == 0f) return Offset.Zero
        // Only a collapse is reverted when the content consumed nothing
        if (consumed.y == 0f && pendingDelta < 0f) {
            headerOffset.floatValue = (headerOffset.floatValue - pendingDelta).coerceIn(-headerHeight.toFloat(), 0f)
        }
        pendingDelta = 0f
        return Offset.Zero
    }

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        if (!enabled || headerHeight <= 0) return Velocity.Zero
        val target = collapsibleHeaderSnapTarget(headerOffset.floatValue, headerHeight)
        if (target == headerOffset.floatValue) return Velocity.Zero
        snapJob.value?.cancel()
        snapJob.value = scope.launch {
            Animatable(headerOffset.floatValue)
                .animateTo(target, tween(SNAP_DURATION_MS, easing = FastOutSlowInEasing)) {
                    headerOffset.floatValue = value
                }
        }
        return Velocity.Zero
    }
}

@Composable
fun rememberCollapsibleHeaderConnection(
    headerHeight: Int,
    headerOffset: MutableFloatState,
    enabled: Boolean,
): NestedScrollConnection {
    val scope = rememberCoroutineScope()
    val snapJob = remember { mutableStateOf<Job?>(null) }
    LaunchedEffect(headerHeight) {
        snapJob.value?.cancel()
    }
    LaunchedEffect(enabled) {
        if (!enabled) {
            snapJob.value?.cancel()
            headerOffset.floatValue = 0f
        }
    }
    return remember(headerHeight, scope, snapJob, enabled) {
        collapsibleHeaderConnection(headerHeight, headerOffset, scope, snapJob, enabled)
    }
}

/**
 * The layout every home tab repeats on the phone (`HomeSongsScreen.kt` 789-827, `HomeAlbum.kt` 605-773,
 * `HomeArtist.kt` 587-…, `HomeLibrary.kt` 764-952): the list fills the screen behind a header that slides
 * away with the scroll (offset, then `background0`, then the alpha of the header content).
 * [content] gets the measured header height as its top padding. [scrollOverHeader] puts the scroll
 * connection on the outer box, header included, as the phone's Songs tab (`HomeSongsScreen.kt` 804-807);
 * the other tabs keep it on the list's box.
 */
@Composable
fun CollapsibleHeaderScreen(
    enabled: Boolean,
    scrollOverHeader: Boolean = false,
    header: @Composable (MutableFloatState, MutableIntState) -> Unit,
    content: @Composable BoxScope.(headerPadding: Dp) -> Unit,
) {
    val headerHeightState = remember { mutableIntStateOf(0) }
    var headerHeight by headerHeightState
    val headerOffsetState = remember { mutableFloatStateOf(0f) }
    val headerOffset by headerOffsetState
    val headerAlpha by remember(headerHeight) {
        derivedStateOf {
            if (headerHeight == 0) 1f
            else (1f + headerOffset / headerHeight).coerceIn(0f, 1f)
        }
    }

    val nestedScrollConnection = rememberCollapsibleHeaderConnection(headerHeight, headerOffsetState, enabled)

    Box(modifier = Modifier.fillMaxSize().then(if (scrollOverHeader) Modifier.nestedScroll(nestedScrollConnection) else Modifier)) {
        Box(modifier = Modifier.fillMaxSize().then(if (scrollOverHeader) Modifier else Modifier.nestedScroll(nestedScrollConnection))) {
            val headerPadding = with(LocalDensity.current) { headerHeight.toDp() }
            content(headerPadding)
        }

        Box(
            modifier = Modifier
                .onGloballyPositioned { headerHeight = it.size.height }
                .offset { IntOffset(0, round(headerOffset).toInt()) }
                .background(colorPalette().background0),
        ) {
            Box(Modifier.graphicsLayer { alpha = headerAlpha }) {
                header(headerOffsetState, headerHeightState)
            }
        }
    }
}
