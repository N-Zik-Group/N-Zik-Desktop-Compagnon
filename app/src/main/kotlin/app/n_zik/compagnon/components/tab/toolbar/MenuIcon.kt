package app.n_zik.compagnon.components.tab.toolbar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.n_zik.compagnon.components.menu.ListMenu
import app.n_zik.compagnon.colorPalette
import app.n_zik.compagnon.uiRoundnessShape

/**
 * Port of the phone's `app/it/fast4x/rimusic/ui/components/tab/toolbar/MenuIcon.kt` (list menus only: the
 * phone's default `MenuStyle` is `List`; the grid menu, whose style `ui.settings` serves since 1.10.0, is deferred).
 */
interface MenuIcon : Icon {

    @get:Composable
    val menuIconTitle: String

    @Composable
    private fun SettingIcon() {
        // The phone's MenuIcon: a DynamicColor icon is accent or textDisabled; an icon whose color is
        // Color.Unspecified keeps its own colors, without the 10 % background
        val useOriginalColors = color == Color.Unspecified
        val iconColor = if (this is DynamicColor) {
            if (isFirstColor) colorPalette().accent else colorPalette().textDisabled
        } else {
            if (useOriginalColors) Color.Unspecified else colorPalette().accent
        }
        Box(
            modifier = Modifier
                .size(32.dp)
                .then(
                    if (!useOriginalColors) {
                        Modifier.background(color = iconColor.copy(alpha = 0.1f), shape = uiRoundnessShape())
                    } else Modifier
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = icon,
                tint = iconColor,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
        }
    }

    @Composable
    fun ListMenuItem() = ListMenu.Entry(
        text = menuIconTitle,
        icon = { SettingIcon() },
        modifier = modifier,
        enabled = isEnabled,
        onClick = ::onShortClick,
        onLongClick = ::onLongClick,
    )
}
