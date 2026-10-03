package app.n_zik.compagnon.components.tab.toolbar

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.Dp
import app.n_zik.compagnon.components.navigation.header.TabToolBar
import app.n_zik.compagnon.colorPalette
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/**
 * Port of the phone's `app/it/fast4x/rimusic/ui/components/tab/toolbar/Icon.kt`, with `Clickable` folded in:
 * [onLongClick] does nothing unless overridden (a right click calls it too, see [TabToolBar.Icon]).
 */
interface Icon : Button {

    val iconId: DrawableResource
    val color: Color
        @Composable
        get() = colorPalette().text
    val sizeDp: Dp
        get() = TabToolBar.TOOLBAR_ICON_SIZE
    val icon: Painter
        @Composable
        get() = painterResource(this.iconId)
    val modifier: Modifier
        get() = Modifier
    val isEnabled: Boolean
        get() = true

    fun onShortClick()

    fun onLongClick() {}

    @Composable
    override fun ToolBarButton() {
        TabToolBar.Icon(
            icon,
            color,
            sizeDp,
            isEnabled,
            modifier,
            this::onShortClick,
            this::onLongClick,
        )
    }
}
