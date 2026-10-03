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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import app.n_zik.compagnon.components.styling.Dimensions
import app.n_zik.compagnon.colorPalette
import app.n_zik.compagnon.uiRoundnessShape
import app.n_zik.compagnon.components.themed.Button
import org.jetbrains.compose.resources.DrawableResource

/** One tab of the bar: the `Item(index, text, iconId)` of the phone's `HomeScreen.kt` `navBarContent`. */
data class NavigationTab(val text: String, val icon: DrawableResource)

/**
 * Port of the phone's `HorizontalNavigationBar` (`app/it/fast4x/rimusic/ui/components/navigation/nav/
 * HorizontalNavigationBar.kt`, with `AbstractNavigationBar.kt`) in its default setting: position
 * `BottomFloating`, type `IconOnly`, `UiType.RiMusic` (no back, search, settings or statistics button).
 * A floating rounded bar, 64 dp high, 85 % of the width (≤ 5 buttons), 25 dp above the bottom, with the
 * tab icons evenly spread; the selected icon is `text`, the others `textDisabled`, the colour animated.
 * Like the phone, it draws nothing with fewer than two tabs.
 */
@Composable
fun HorizontalNavigationBar(
    tabs: List<NavigationTab>,
    tabIndex: Int,
    onTabChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (tabs.size < 2) return

    val transition = updateTransition(targetState = tabIndex, label = null)
    val widthFraction = if (tabs.size > 5) 0.95f else 0.85f

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom,
        modifier = modifier.padding(top = 0.dp, bottom = Dimensions.navBarBottomPadding),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceAround,
            modifier = Modifier
                .fillMaxWidth(widthFraction)
                .height(Dimensions.floatingNavBarIconOnlyHeight),
        ) {
            val roundedCornerShape = uiRoundnessShape()

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 2.dp)
                    .shadow(elevation = 8.dp, shape = roundedCornerShape)
                    .clip(roundedCornerShape)
                    .background(colorPalette().background1.copy(alpha = 0.95f)),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxSize()
                        .padding(horizontal = if (tabs.size > 5) 8.dp else 16.dp)
                        .padding(vertical = 6.dp)
                        .horizontalScroll(rememberScrollState()),
                ) {
                    tabs.forEachIndexed { index, tab ->
                        val textColor = colorPalette().text
                        val disabledColor = colorPalette().textDisabled
                        val color by transition.animateColor(label = "") {
                            if (it == index) textColor else disabledColor
                        }
                        Box(
                            Modifier
                                .clip(uiRoundnessShape()).clickable(onClick = { onTabChanged(index) }),
                        ) {
                            Button(tab.icon, color, 12.dp, 24.dp)
                        }
                    }
                }
            }
        }
    }
}
