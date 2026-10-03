package app.n_zik.compagnon.components.tab

import androidx.compose.runtime.Composable
import app.n_zik.compagnon.components.tab.toolbar.MenuIcon
import app.n_zik.compagnon.generated.resources.Res
import app.n_zik.compagnon.generated.resources.library_refresh
import app.n_zik.compagnon.generated.resources.refresh
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.stringResource

/**
 * Compagnon only (no phone equivalent: the phone reads its own database): the toolbar's "Refresh" button,
 * which reloads the list from the phone (contract §12, the library is never kept).
 */
class Refresh(private val onRefresh: () -> Unit) : MenuIcon {
    override val iconId: DrawableResource = Res.drawable.refresh
    override val menuIconTitle: String
        @Composable
        get() = stringResource(Res.string.library_refresh)

    override fun onShortClick() = onRefresh()
}
