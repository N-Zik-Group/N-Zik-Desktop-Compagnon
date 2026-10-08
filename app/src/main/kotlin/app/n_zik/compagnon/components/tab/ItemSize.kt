package app.n_zik.compagnon.components.tab

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.n_zik.compagnon.components.LocalMenuState
import app.n_zik.compagnon.components.MenuState
import app.n_zik.compagnon.components.menu.ListMenu
import app.n_zik.compagnon.components.styling.HomeItemSize
import app.n_zik.compagnon.components.tab.toolbar.MenuIcon
import app.n_zik.compagnon.colorPalette
import app.n_zik.compagnon.generated.resources.*
import app.n_zik.compagnon.uiRoundnessShape
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Port of the phone's `ItemSize` (`app/it/fast4x/rimusic/ui/components/tab/ItemSize.kt`): the toolbar
 * button cycling the home grid's item size (the phone's `HomeItemSize`: 100 / 130 / 160 dp). Its menu
 * holds the phone's three entries (the accent `arrow_forward` badge, no selected highlight, as on the
 * phone). The size is a Compagnon-local setting, persisted per page (the phone keeps one size per home
 * tab; its own settings are not in the contract).
 */
class ItemSize private constructor(
    private val menuState: MenuState,
    private val size: MutableState<HomeItemSize>,
    private val onSizeSelected: (HomeItemSize) -> Unit,
) : MenuIcon {

    companion object {
        @Composable
        fun init(size: MutableState<HomeItemSize>, onSizeSelected: (HomeItemSize) -> Unit): ItemSize =
            ItemSize(LocalMenuState.current, size, onSizeSelected)
    }

    override val iconId: DrawableResource = Res.drawable.resize

    override val menuIconTitle: String
        @Composable
        get() = stringResource(Res.string.size)

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
                painter = painterResource(Res.drawable.arrow_forward),
                tint = iconColor,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
        }
    }

    override fun onShortClick() {
        menuState.display {
            ListMenu.Menu(title = stringResource(Res.string.size)) {
                HomeItemSize.entries.forEach { entry ->
                    ListMenu.Entry(
                        text = stringResource(entry.labelId),
                        icon = { SettingIcon() },
                        onClick = {
                            size.value = entry
                            onSizeSelected(entry)
                            menuState.hide()
                        },
                    )
                }
            }
        }
    }
}
