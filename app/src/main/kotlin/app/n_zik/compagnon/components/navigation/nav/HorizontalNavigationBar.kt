package app.n_zik.compagnon.components.navigation.nav

import androidx.compose.animation.animateColor
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.n_zik.compagnon.components.styling.Dimensions
import app.n_zik.compagnon.components.themed.Button
import app.n_zik.compagnon.colorPalette
import app.n_zik.compagnon.enums.NavigationBarPosition
import app.n_zik.compagnon.enums.NavigationBarType
import app.n_zik.compagnon.topUiRoundnessShape
import app.n_zik.compagnon.typography
import app.n_zik.compagnon.uiRoundnessShape
import app.n_zik.compagnon.utils.semiBold
import org.jetbrains.compose.resources.DrawableResource

/** One tab of the bar: the `Item(index, text, iconId)` of the phone's `HomeScreen.kt` `navBarContent`. */
data class NavigationTab(val text: String, val icon: DrawableResource)

/**
 * Port of the phone's `HorizontalNavigationBar` (`app/it/fast4x/rimusic/ui/components/navigation/nav/
 * HorizontalNavigationBar.kt`, with `AbstractNavigationBar.kt`), in its `UiType.RiMusic` flavour (no
 * back, search, settings or statistics button), all three horizontal positions and both types:
 * - [NavigationBarPosition.BottomFloating]: the floating rounded bar, 64/84 dp high (icon-only / with
 *   labels), 85 % of the width (≤ 5 tabs, 95 % otherwise; always 95 % with labels), 25 dp above the
 *   bottom, a shadow, the `background1` at 95 % alpha.
 * - [NavigationBarPosition.Bottom]: the full-width fixed bar, `background1` solid, no shadow, the top
 *   corners rounded (`topUiRoundnessShape`), height `navigationBarHeight - 10.dp`.
 * - [NavigationBarPosition.Top]: the full-width fixed bar flush at the top: no rounded corners
 *   (`RectangleShape`), a 5 dp bottom padding inside the bar.
 * The selected tab's colour is `text`, the others `textDisabled`, the colour animated.
 * Like the phone, it draws nothing with fewer than two tabs.
 */
@Composable
fun HorizontalNavigationBar(
    tabs: List<NavigationTab>,
    tabIndex: Int,
    onTabChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
    position: NavigationBarPosition = NavigationBarPosition.BottomFloating,
    type: NavigationBarType = NavigationBarType.IconOnly,
) {
    if (tabs.size < 2) return

    val transition = updateTransition(targetState = tabIndex, label = null)
    val isFloating = position.isFloating
    val isIconOnly = type == NavigationBarType.IconOnly

    val floatingHeight = if (isIconOnly) Dimensions.floatingNavBarIconOnlyHeight else Dimensions.floatingNavBarHeight
    val widthFraction = if (isIconOnly) {
        if (tabs.size > 5) 0.95f else 0.85f
    } else {
        0.95f
    }

    // The phone's `bottomPadding()`: the floating bar's 25 dp, 0 for the fixed bottom bar, 5 dp at the top
    val barBottomPadding = if (position == NavigationBarPosition.BottomFloating) Dimensions.navBarBottomPadding
    else if (position == NavigationBarPosition.Bottom) 0.dp
    else 5.dp

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom,
        modifier = modifier.padding(top = 0.dp, bottom = if (isFloating) barBottomPadding else 0.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceAround,
            modifier = Modifier
                .then(if (isFloating) Modifier.fillMaxWidth(widthFraction) else Modifier.fillMaxWidth())
                .height(if (isFloating) floatingHeight else Dimensions.navigationBarHeight - 10.dp + barBottomPadding),
        ) {
            // A bar at the top of the screen sits flush under the header: no rounded corners
            val roundedCornerShape = when {
                isFloating -> uiRoundnessShape()
                position == NavigationBarPosition.Top -> RectangleShape
                else -> topUiRoundnessShape()
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = if (isFloating) 2.dp else 0.dp)
                    .shadow(elevation = if (isFloating) 8.dp else 0.dp, shape = roundedCornerShape)
                    .clip(roundedCornerShape)
                    .background(if (isFloating) colorPalette().background1.copy(alpha = 0.95f) else colorPalette().background1),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxSize()
                        .padding(horizontal = if (isFloating && tabs.size > 5) 8.dp else 16.dp)
                        .padding(vertical = if (isFloating) 6.dp else 0.dp)
                        .padding(bottom = if (!isFloating) barBottomPadding else 0.dp)
                        .horizontalScroll(rememberScrollState()),
                ) {
                    tabs.forEachIndexed { index, tab ->
                        val color by transition.animateColor(label = "") {
                            if (it == index) colorPalette().text else colorPalette().textDisabled
                        }
                        Box(
                            modifier = Modifier
                                .clip(uiRoundnessShape())
                                .clickable(onClick = { onTabChanged(index) })
                                .then(if (isIconOnly) Modifier else Modifier.fillMaxHeight()),
                        ) {
                            if (isIconOnly) {
                                Button(tab.icon, color, 12.dp, if (isFloating) 24.dp else 20.dp)
                            } else {
                                TextIconButton(
                                    text = tab.text,
                                    icon = tab.icon,
                                    color = color,
                                    textSpacing = if (isFloating) 12.dp else 5.dp,
                                    isCompact = isFloating,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Port of the phone's `TextIconButton` (`app/it/fast4x/rimusic/ui/components/themed/TextIconButton.kt`):
 * the icon (20 dp) + a spacer + the label below it, a 6 dp padding when compact (the floating bar),
 * 5 dp otherwise, the label `xxs` semiBold on one line when compact, `xs` on two otherwise, ellipsized.
 * The phone's `LocalIsManyButtons` ×0.75 scale and its `xxxs` label only apply above 5 buttons: the
 * Compagnon has 4, so they never apply (as in [Button]).
 */
@Composable
private fun TextIconButton(
    text: String,
    icon: DrawableResource,
    color: Color,
    textSpacing: Dp,
    isCompact: Boolean,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = if (isCompact) Arrangement.Center else Arrangement.SpaceAround,
        modifier = Modifier
            .padding(all = if (isCompact) 6.dp else 5.dp)
            .fillMaxHeight(),
    ) {
        Button(icon, color, 0.dp, 20.dp)
        Spacer(modifier = Modifier.height(textSpacing))

        BasicText(
            text = text,
            style = TextStyle(
                fontSize = if (isCompact) typography().xxs.semiBold.fontSize else typography().xs.semiBold.fontSize,
                fontWeight = typography().xs.semiBold.fontWeight,
                fontFamily = typography().xs.semiBold.fontFamily,
                color = color,
            ),
            maxLines = if (isCompact) 1 else 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
