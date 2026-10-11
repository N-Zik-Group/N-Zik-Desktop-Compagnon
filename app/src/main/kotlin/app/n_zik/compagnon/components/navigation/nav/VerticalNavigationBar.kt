package app.n_zik.compagnon.components.navigation.nav

import androidx.compose.animation.animateColor
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import app.n_zik.compagnon.components.styling.Dimensions
import app.n_zik.compagnon.colorPalette
import app.n_zik.compagnon.enums.NavigationBarType
import app.n_zik.compagnon.typography
import app.n_zik.compagnon.uiRoundnessShape
import app.n_zik.compagnon.utils.semiBold

/**
 * Port of the phone's `VerticalNavigationBar` (`app/it/fast4x/rimusic/ui/components/navigation/nav/
 * VerticalNavigationBar.kt`, with `AbstractNavigationBar.kt`), in its `UiType.RiMusic` flavour (no
 * back, search, settings or statistics button) and the phone's portrait layout (its `isLandscape`
 * branch is not ported — a desktop window is never in the phone's mobile landscape). A 50 dp wide
 * rail, vertically scrollable, stacking the tab entries from the top (the phone's plain `Column`,
 * no arrangement); the selected icon is `text`, the others `textDisabled`, the colour animated:
 * - [NavigationBarType.IconOnly]: the 24 dp icon, 12 dp of vertical padding.
 * - [NavigationBarType.IconAndText]: an 18 dp dot that slides in and turns -90° with the selected
 *   tab (the phone's `dotAlpha`), then the label rotated -90° (`xs` semiBold, 16 dp of horizontal
 *   padding), run down the rail by [vertical].
 * The phone's 12 dp vertical padding of the [NavigationBarType.IconOnly] icon comes from its
 * `buttonModifier` (its `Button` sizing quirk is not ported: the icon is drawn at its own 24 dp).
 * Like the phone, it draws nothing with fewer than two tabs.
 */
@Composable
fun VerticalNavigationBar(
    tabs: List<NavigationTab>,
    tabIndex: Int,
    onTabChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
    type: NavigationBarType = NavigationBarType.IconOnly,
) {
    if (tabs.size < 2) return

    val transition = updateTransition(targetState = tabIndex, label = null)
    val isIconOnly = type == NavigationBarType.IconOnly

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .width(Dimensions.navigationRailWidth)
            .fillMaxHeight()
            .verticalScroll(rememberScrollState()),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            tabs.forEachIndexed { index, tab ->
                val color by transition.animateColor(label = "") {
                    if (it == index) colorPalette().text else colorPalette().textDisabled
                }
                val dotAlpha by transition.animateFloat(label = "") {
                    if (it == index) 1f else 0f
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(uiRoundnessShape())
                        .clickable(onClick = { onTabChanged(index) })
                        .padding(vertical = 8.dp),
                ) {
                    if (isIconOnly) {
                        Box(
                            modifier = Modifier
                                .padding(top = 12.dp, bottom = 12.dp)
                                .size(24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Image(
                                painter = painterResource(tab.icon),
                                contentDescription = null,
                                colorFilter = ColorFilter.tint(color),
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    } else {
                        Image(
                            painter = painterResource(tab.icon),
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(color),
                            modifier = Modifier
                                .size(Dimensions.navigationRailIconOffset * 3)
                                .graphicsLayer {
                                    alpha = dotAlpha
                                    translationX = (1f - dotAlpha) * -48.dp.toPx()
                                    rotationZ = -90f
                                },
                        )
                        BasicText(
                            text = tab.text,
                            style = TextStyle(
                                fontSize = typography().xs.semiBold.fontSize,
                                fontWeight = typography().xs.semiBold.fontWeight,
                                color = colorPalette().text,
                            ),
                            modifier = Modifier
                                .vertical()
                                .rotate(-90f)
                                .padding(horizontal = 16.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * The phone's `Modifier.vertical` (its `utils/Modifier.kt` 58-70): measure the label with an
 * unbounded width (one long line) and report its height as the layout's width, so the following
 * -90° rotation makes the line run down the rail, centered on the icon.
 */
private fun Modifier.vertical(enabled: Boolean = true): Modifier =
    if (enabled)
        layout { measurable, constraints ->
            val c: Constraints = constraints.copy(maxWidth = Int.MAX_VALUE)
            val placeable = measurable.measure(c)

            layout(placeable.height, placeable.width) {
                placeable.place(
                    x = -(placeable.width / 2 - placeable.height / 2),
                    y = -(placeable.height / 2 - placeable.width / 2),
                )
            }
        }
    else this
