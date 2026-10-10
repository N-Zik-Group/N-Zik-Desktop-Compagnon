package app.n_zik.compagnon.components.tab.toolbar

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.n_zik.compagnon.colorPalette
import androidx.compose.ui.graphics.Color
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * A phone toolbar button that opens its confirmation dialog (the phone's `DownloadAllSongsDialog` /
 * `DeleteDownloadsDialog`, `ConfirmDialog`s) but has no contract route: a click shows the phone's
 * confirmation, its Confirm only closes it — NOT LINKED, the PC never fakes the action. `Descriptive` as the
 * phone's button ([descriptionId]; `null` where the phone's long press is empty, e.g. `delete_playlists`).
 * [dialogTextArg] formats the phone's counted text (e.g. `delete_playlists_confirm_all`, `%d`).
 * [Render] must be composed by the screen.
 */
class NoRouteConfirmButton(
    override val iconId: DrawableResource,
    private val titleId: StringResource,
    private val descriptionId: StringResource?,
    private val dialogTextId: StringResource,
    activeState: MutableState<Boolean>,
    private val dialogTextArg: Int? = null,
) : MenuIcon, Descriptive, ConfirmDialog {

    companion object {
        @Composable
        operator fun invoke(
            iconId: DrawableResource,
            titleId: StringResource,
            descriptionId: StringResource?,
            dialogTextId: StringResource,
            dialogTextArg: Int? = null,
        ) = NoRouteConfirmButton(iconId, titleId, descriptionId, dialogTextId, remember { mutableStateOf(false) }, dialogTextArg)
    }

    override var isActive: Boolean by activeState
    override val messageId: StringResource get() = descriptionId ?: titleId

    override fun onLongClick() {
        descriptionId?.let { app.n_zik.compagnon.utils.Toaster.i(it) }
    }
    override val color: Color
        @Composable
        get() = colorPalette().text
    override val menuIconTitle: String
        @Composable
        get() = stringResource(titleId)
    override val dialogTitle: String
        @Composable
        get() = dialogTextArg?.let { stringResource(dialogTextId, it) } ?: stringResource(dialogTextId)

    override fun onShortClick() {
        isActive = true
    }

    override fun onConfirm() {
        isActive = false
    }
}
