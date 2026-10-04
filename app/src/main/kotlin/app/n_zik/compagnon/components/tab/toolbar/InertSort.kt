package app.n_zik.compagnon.components.tab.toolbar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.n_zik.compagnon.components.LocalMenuState
import app.n_zik.compagnon.components.MenuState
import app.n_zik.compagnon.components.SortOption
import app.n_zik.compagnon.components.menu.ListMenu
import app.n_zik.compagnon.colorPalette
import app.n_zik.compagnon.generated.resources.Res
import app.n_zik.compagnon.generated.resources.arrow_up
import app.n_zik.compagnon.generated.resources.sorting_order
import app.n_zik.compagnon.uiRoundnessShape
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * The phone's sort arrow for a tab whose sort the connected phone does not implement (contract < 1.6,
 * no `library.sort` feature): the same arrow and the same menu, listing all the options of
 * the tab's enum in the phone's order — but a short click does nothing and an option click closes the
 * menu without selecting anything (the list keeps the order the phone sends).
 */
class InertSort<T>(
    private val menuState: MenuState,
    private val options: List<SortOption<T>>,
) : MenuIcon {

    companion object {
        @Composable
        fun <T> init(options: List<SortOption<T>>) = InertSort(LocalMenuState.current, options)
    }

    override val iconId: DrawableResource = Res.drawable.arrow_up

    override val menuIconTitle: String
        @Composable
        get() = stringResource(Res.string.sorting_order)

    override fun onShortClick() {}

    @Composable
    private fun ListMenu() {
        ListMenu.Menu(title = menuIconTitle) {
            options.forEach {
                ListMenu.Entry(
                    text = stringResource(it.labelId),
                    icon = {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(
                                    color = colorPalette().accent.copy(alpha = 0.1f),
                                    shape = uiRoundnessShape(),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painter = painterResource(it.iconId),
                                contentDescription = null,
                                tint = colorPalette().accent,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    },
                    onClick = { menuState.hide() },
                )
            }
        }
    }
}
