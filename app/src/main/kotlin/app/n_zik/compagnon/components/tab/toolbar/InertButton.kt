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
 * labelled — when it overflows the row. Its right click shows that title as the phone's long-click
 * description (`Descriptive`).
 */
class InertButton(
    override val iconId: DrawableResource,
    private val titleId: StringResource,
    override val modifier: Modifier = Modifier,
    private val tint: Color? = null,
    /** The phone button's `Descriptive.messageId` when it differs from its title (e.g. `SongShuffler`). */
    /** `null` where the phone's button has no help on a long press. */
    private val descriptionId: StringResource? = titleId,
) : MenuIcon, Descriptive {
    /** The phone's own button is `Descriptive`: a right click shows the same description. */
    override val messageId: StringResource get() = descriptionId ?: titleId

    override fun onLongClick() {
        descriptionId?.let { app.n_zik.compagnon.utils.Toaster.i(it) }
    }

    override val color: Color
        @Composable
        get() = tint ?: colorPalette().text

    override val menuIconTitle: String
        @Composable
        get() = stringResource(titleId)

    override fun onShortClick() {}
}
