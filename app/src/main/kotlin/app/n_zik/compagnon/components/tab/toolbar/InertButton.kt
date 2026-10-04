package app.n_zik.compagnon.components.tab.toolbar

import app.n_zik.compagnon.colorPalette
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * A toolbar button of the phone that has no contract route (story 11c): drawn exactly like the phone's
 * (same icon, size, colour, ripple), and its click has no effect. Its title is the phone's own (the
 * button's `Descriptive.messageId`), so the [EllipsisMenuComponent] lists it like the phone's buttons —
 * labelled — when it overflows the row.
 */
class InertButton(
    override val iconId: DrawableResource,
    private val titleId: StringResource,
    override val modifier: Modifier = Modifier,
    private val tint: Color? = null,
) : MenuIcon {
    override val color: Color
        @Composable
        get() = tint ?: colorPalette().text

    override val menuIconTitle: String
        @Composable
        get() = stringResource(titleId)

    override fun onShortClick() {}
}
