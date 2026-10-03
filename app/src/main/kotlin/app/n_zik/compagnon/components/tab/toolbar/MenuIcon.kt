package app.n_zik.compagnon.components.tab.toolbar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.n_zik.compagnon.components.menu.ListMenu
import app.n_zik.compagnon.colorPalette
import app.n_zik.compagnon.uiRoundnessShape
import org.jetbrains.compose.resources.painterResource

/**
 * Port of the phone's `app/it/fast4x/rimusic/ui/components/tab/toolbar/MenuIcon.kt` (list menus only: the
 * phone's default `MenuStyle` is `List`, the grid menu is not ported).
 */
interface MenuIcon : Icon {

    @get:Composable
    val menuIconTitle: String

    @Composable
    private fun SettingIcon() {
        val iconColor = colorPalette().accent
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(
                    color = iconColor.copy(alpha = 0.1f),
                    shape = uiRoundnessShape(),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(iconId),
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
