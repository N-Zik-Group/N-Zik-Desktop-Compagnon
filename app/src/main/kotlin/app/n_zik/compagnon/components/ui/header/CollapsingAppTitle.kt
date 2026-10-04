package app.n_zik.compagnon.components.ui.header

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import app.n_zik.compagnon.colorPalette
import app.n_zik.compagnon.generated.resources.Res
import app.n_zik.compagnon.generated.resources.cd_app_s_icon
import app.n_zik.compagnon.generated.resources.ic_launcher
import app.n_zik.compagnon.typography
import app.n_zik.compagnon.uiRoundnessShape
import app.n_zik.compagnon.utils.semiBold
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** Collapse animation duration (ms) for the header title's sliding extras, in both directions. */
private const val APP_TITLE_COLLAPSE_ANIMATION_MS = 250

/** Boundary jitter tolerated before the header title collapses. */
private val APP_TITLE_COLLAPSE_TOLERANCE = 2.dp

/**
 * Collapse decision for the header title row: the sliding extras must slide behind the logo when the width
 * actually available to the row is below its ideal width by more than [tolerancePx].
 */
internal fun shouldCollapseTitle(availablePx: Int, idealPx: Int, tolerancePx: Int): Boolean {
    if (idealPx <= 0) return false
    return availablePx < idealPx - tolerancePx
}

/**
 * Port of `CollapsingAppTitle` (phone's `app/n_zik/android/components/ui/header/CollapsingAppTitle.kt`): the
 * 36 dp app logo then "N-ZIK" in xl.semiBold, which slides behind the logo when the header runs out of width.
 * The title goes home ([onHome], phone's `AppLogoText` 245-266); the logo stays clickable without action
 * (its easter eggs are phone games). Dropped: the version badge (none for a release build), the
 * parental-control shield and the debug badge (phone settings).
 */
@Composable
fun CollapsingAppTitle(
    onHome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val idealExtrasPx = remember { mutableIntStateOf(0) }
    val logoPx = remember { mutableIntStateOf(0) }
    val availablePx = remember { mutableIntStateOf(0) }

    val collapsed = shouldCollapseTitle(
        availablePx = availablePx.intValue,
        idealPx = logoPx.intValue + idealExtrasPx.intValue,
        tolerancePx = with(density) { APP_TITLE_COLLAPSE_TOLERANCE.toPx().toInt() },
    )

    val extrasWidth by animateDpAsState(
        targetValue = if (collapsed) 0.dp else with(density) { idealExtrasPx.intValue.toDp() },
        animationSpec = tween(APP_TITLE_COLLAPSE_ANIMATION_MS, easing = FastOutSlowInEasing),
        label = "appTitleExtrasWidth",
    )
    val slide by animateFloatAsState(
        targetValue = if (collapsed) 1f else 0f,
        animationSpec = tween(APP_TITLE_COLLAPSE_ANIMATION_MS, easing = FastOutSlowInEasing),
        label = "appTitleSlide",
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.layout { measurable, constraints ->
            availablePx.intValue = constraints.maxWidth
            val placeable = measurable.measure(constraints)
            layout(placeable.width, placeable.height) {
                placeable.place(0, 0)
            }
        },
    ) {
        AppLogo(
            modifier = Modifier
                .zIndex(1f) // draw the logo above the sliding extras while they pass behind
                .onSizeChanged { logoPx.intValue = it.width },
        )

        Box(
            modifier = Modifier
                .width(extrasWidth)
                .graphicsLayer {
                    translationX = -slide * logoPx.intValue
                    alpha = (1f - slide) * (1f - slide)
                }
                .clip(RectangleShape),
        ) {
            AppTitleExtras(
                onHome = onHome,
                modifier = Modifier
                    .layout { measurable, constraints ->
                        // Always measure at natural width: the animated Box width above only clips and slides it
                        val placeable = measurable.measure(
                            constraints.copy(maxWidth = Int.MAX_VALUE),
                        )
                        layout(placeable.width, placeable.height) {
                            placeable.place(0, 0)
                        }
                    }
                    .onSizeChanged { idealExtrasPx.intValue = it.width },
            )
        }
    }
}

@Composable
private fun AppTitleExtras(
    onHome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        AppLogoText(onHome)
    }
}

@Composable
private fun AppLogo(
    modifier: Modifier,
) {
    Image(
        painter = painterResource(Res.drawable.ic_launcher),
        contentDescription = stringResource(Res.string.cd_app_s_icon),
        modifier = modifier
            .clip(uiRoundnessShape())
            .combinedClickable(onClick = {}, onLongClick = {})
            .size(36.dp),
    )
}

@Composable
private fun AppLogoText(onHome: () -> Unit) {
    BasicText(
        text = "N-ZIK",
        style = TextStyle(
            fontSize = typography().xl.semiBold.fontSize,
            fontWeight = typography().xl.semiBold.fontWeight,
            fontFamily = typography().xl.semiBold.fontFamily,
            color = colorPalette().text,
        ),
        modifier = Modifier
            .clip(uiRoundnessShape())
            .clickable { onHome() }
            .padding(horizontal = 8.dp),
    )
}
