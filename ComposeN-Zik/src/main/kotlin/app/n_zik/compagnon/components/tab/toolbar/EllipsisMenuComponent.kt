package app.n_zik.compagnon.components.tab.toolbar

import androidx.compose.runtime.Composable
import app.n_zik.compagnon.components.LocalMenuState
import app.n_zik.compagnon.components.MenuState
import app.n_zik.compagnon.components.menu.ListMenu
import app.n_zik.compagnon.generated.resources.*
import org.jetbrains.compose.resources.DrawableResource

/**
 * Port of the phone's `app/it/fast4x/rimusic/ui/components/tab/toolbar/EllipsisMenuComponent.kt`: the
 * "…" button that groups the toolbar buttons that do not fit the row (`TabToolBar.Buttons` shows the
 * first `canDisplay - 1` of them and this button holds the rest). List menu only (the phone's default
 * `MenuStyle` is `List`; its grid menu, served by `ui.settings` since 1.10.0, is deferred), like the other menus of the Compagnon.
 *
 * The menu lists every overflowed button — the phone's buttons as well as the placeholders without a
 * contract route ([InertButton]: shown labelled with the phone's own title, a click does nothing).
 */
class EllipsisMenuComponent private constructor(
    private val buttons: () -> List<Button>,
    private val menuState: MenuState,
) : Icon {

    companion object {
        @Composable
        fun init(items: () -> List<Button>) = EllipsisMenuComponent(items, LocalMenuState.current)
    }

    override val iconId: DrawableResource = Res.drawable.ellipsis_horizontal

    override fun onShortClick() = openMenu()

    private fun openMenu() = menuState.display { ListMenu() }

    @Composable
    private fun ListMenu() {
        ListMenu.Menu(title = "") {
            buttons().forEach {
                if (it is MenuIcon) it.ListMenuItem()
            }
        }
    }
}
