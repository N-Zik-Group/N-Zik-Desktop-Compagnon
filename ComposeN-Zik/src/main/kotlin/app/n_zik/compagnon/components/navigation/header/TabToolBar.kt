package app.n_zik.compagnon.components.navigation.header

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.n_zik.compagnon.components.tab.toolbar.Button
import app.n_zik.compagnon.components.tab.toolbar.EllipsisMenuComponent
import app.n_zik.compagnon.colorPalette
import app.n_zik.compagnon.uiRoundnessShape
import app.n_zik.compagnon.utils.onSecondaryClick

/**
 * Port of the phone's `app/it/fast4x/rimusic/ui/components/navigation/header/TabToolBar.kt`.
 *
 * The buttons change through an `AnimatedContent` (its default fade and size transform, phone's 86-98),
 * unless [disableAnimation] (the phone's home tabs). As on the phone, the row is measured first and, when
 * more buttons than [canDisplay] are passed, all but the first `canDisplay - 1` go behind an
 * `EllipsisMenuComponent` ("…") that lists them (the phone's 65-81).
 */
object TabToolBar {

    val TOOLBAR_ICON_SIZE = 32.dp
    val HORIZONTAL_PADDING = 12.dp
    val VERTICAL_PADDING = 4.dp

    @Composable
    fun Buttons(
        buttons: List<Button>,
        horizontalArrangement: Arrangement.Horizontal = Arrangement.SpaceEvenly,
        verticalAlignment: Alignment.Vertical = Alignment.CenterVertically,
        modifier: Modifier = Modifier,
        disableAnimation: Boolean = false,
    ) {
        val density = LocalDensity.current.density
        var availableWidth by remember { mutableStateOf(0.dp) }
        val sizeWithSpacing = TOOLBAR_ICON_SIZE + 15.dp
        val canDisplay = (availableWidth / sizeWithSpacing).toInt()

        val baseModifier = modifier.fillMaxWidth()
            .padding(HORIZONTAL_PADDING, VERTICAL_PADDING)
            .onGloballyPositioned {
                val widthDp = it.size.width / density
                availableWidth = widthDp.dp - (HORIZONTAL_PADDING * 2)
            }

        val content = @Composable { targetButtons: List<Button> ->
            if (canDisplay == 0) {
                Spacer(modifier = Modifier.fillMaxWidth())
            } else {
                val isClustered = targetButtons.size > canDisplay
                val ellipsisMenu = EllipsisMenuComponent.init {
                    targetButtons.takeLast((targetButtons.size - canDisplay + 1).coerceAtLeast(0))
                }

                Row(
                    horizontalArrangement = horizontalArrangement,
                    verticalAlignment = verticalAlignment,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    targetButtons.take(if (isClustered) canDisplay - 1 else targetButtons.size)
                        .forEach { it.ToolBarButton() }

                    if (isClustered) ellipsisMenu.ToolBarButton()
                }
            }
        }

        if (disableAnimation) {
            Box(modifier = baseModifier) {
                content(buttons)
            }
        } else {
            AnimatedContent(
                targetState = buttons,
                label = "ToolbarButtonsAnimation",
                modifier = baseModifier,
            ) { targetButtons ->
                content(targetButtons)
            }
        }
    }

    @Composable
    fun Buttons(
        vararg buttons: Button,
        horizontalArrangement: Arrangement.Horizontal = Arrangement.SpaceEvenly,
        verticalAlignment: Alignment.Vertical = Alignment.CenterVertically,
        modifier: Modifier = Modifier,
        disableAnimation: Boolean = false,
    ) = Buttons(listOf(*buttons), horizontalArrangement, verticalAlignment, modifier, disableAnimation)

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    fun Icon(
        icon: Painter,
        tint: Color = colorPalette().text,
        size: Dp = TOOLBAR_ICON_SIZE,
        enabled: Boolean = true,
        modifier: Modifier = Modifier,
        onClick: () -> Unit = {},
        onLongClick: () -> Unit = {},
    ) {
        val interactionSource = remember { MutableInteractionSource() }
        Box(
            modifier = modifier
                .minimumInteractiveComponentSize()
                .clip(uiRoundnessShape())
                .onSecondaryClick(onLongClick.takeIf { enabled })
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = ripple(
                        bounded = false,
                        radius = 20.dp,
                    ),
                    enabled = enabled,
                    onClick = onClick,
                    onLongClick = onLongClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = icon,
                contentDescription = null,
                modifier = Modifier
                    .size(size)
                    .padding(horizontal = 4.dp),
                tint = if (enabled) tint else tint.copy(alpha = 0.5f),
            )
        }
    }
}
