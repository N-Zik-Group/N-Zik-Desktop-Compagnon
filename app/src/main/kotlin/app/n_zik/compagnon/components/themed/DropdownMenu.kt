package app.n_zik.compagnon.components.themed

import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MenuItemColors
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.n_zik.compagnon.colorPalette
import app.n_zik.compagnon.components.theme.favoritesIcon
import app.n_zik.compagnon.uiRoundnessShape
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Port of the phone's `app/it/fast4x/rimusic/ui/components/themed/DropdownMenu.kt`: a Material 3
 * `DropdownMenu` in the UI roundness, whose items are a 24 dp leading icon (`favoritesIcon`) and a text
 * (`textSecondary`).
 */
class DropdownMenu(
    val expanded: Boolean,
    val containerColor: Color = Color.Transparent,
    val modifier: Modifier = Modifier,
    val onDismissRequest: () -> Unit,
) {

    private val _components: MutableList<@Composable () -> Unit> = mutableListOf()

    @Composable
    fun components() = remember { _components }

    fun add(item: Item) = _components.add { item.Draw() }

    fun add(component: @Composable () -> Unit) = _components.add(component)

    @Composable
    fun Draw() {
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = onDismissRequest,
            containerColor = containerColor,
            modifier = modifier,
            shape = uiRoundnessShape(),
            content = { components().forEach { it() } },
        )
    }

    class Item(
        val icon: DrawableResource,
        val text: StringResource? = null,
        val size: Dp = 24.dp,
        val padding: Dp = Dp.Hairline,
        val colors: MenuItemColors? = null,
        val modifier: Modifier = Modifier,
        val customText: String? = null,
        val enabled: Boolean = true,
        val onClick: () -> Unit,
    ) {

        companion object {

            @Composable
            fun colors(): MenuItemColors {
                return MenuItemColors(
                    leadingIconColor = colorPalette().favoritesIcon,
                    trailingIconColor = colorPalette().favoritesIcon,
                    textColor = colorPalette().textSecondary,
                    disabledTextColor = colorPalette().text,
                    disabledLeadingIconColor = colorPalette().text,
                    disabledTrailingIconColor = colorPalette().text,
                )
            }
        }

        @Composable
        fun Draw() {
            val icon: @Composable () -> Unit = {
                Icon(
                    painter = painterResource(icon),
                    contentDescription = null,
                    modifier = modifier.size(24.dp),
                )
            }

            DropdownMenuItem(
                enabled = enabled,
                colors = colors ?: colors(),
                text = { Text(customText ?: text?.let { stringResource(it) } ?: "") },
                leadingIcon = icon,
                onClick = onClick,
            )
        }
    }
}
