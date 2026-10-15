package app.n_zik.compagnon.components.dialog.common

import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.n_zik.compagnon.colorPalette
import app.n_zik.compagnon.components.tab.toolbar.MenuIcon
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import app.n_zik.compagnon.generated.resources.*
import app.n_zik.compagnon.typography
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Port of the phone's `CheckboxDialog` (`app/n_zik/android/components/dialog/common/CheckboxDialog.kt`)
 * with its bases `Dialog` / `InteractiveDialog` / `ConfirmDialog` (the phone's `dialog/common/
 * Dialog.kt`, `InteractiveDialog.kt`, `ConfirmDialog.kt`): a wrap-content card on the shared
 * [DialogFrame] (the PC window bounds — a fixed 560 dp max width, 80 % of the window height — in
 * place of the phone's screen ratios, `background1`, 8 dp elevation, UI roundness) with the
 * centred `l` semi-bold title, 20 dp, the body (a column of checkable [items], 80 % wide, the
 * phone's [Item.SELECT_ALL] semantics), 20 dp, then the two pills of the shared [ButtonRow] —
 * Cancel (the red `danger`) and Confirm (the accent `primary`, the phone's `ConfirmDialog` roles).
 * Confirm runs [onConfirm] only (the implementation closes the dialog, as on the phone). The base
 * of the phone's `UpdateSongDialog`.
 *
 * Ported ahead for `spec-home-songs-toolbar-actions` (its update dialog): no PC screen opens one
 * yet.
 */
abstract class CheckboxDialog(activeState: MutableState<Boolean>) {

    val items: MutableList<Item> = ArrayList()

    var isActive: Boolean by activeState

    @get:Composable
    abstract val dialogTitle: String

    abstract fun onConfirm()

    fun showDialog() {
        isActive = true
    }

    fun hideDialog() {
        isActive = false
    }

    @Composable
    fun DialogBody() {
        Column(Modifier.fillMaxWidth(.8f)) {
            items.forEach { it.ToolBarButton() }
        }
    }

    @Composable
    fun Render() {
        if (!isActive) return
        // The frame carries the card chrome + the PC window bounds (the phone's `Dialog.Render()`)
        DialogFrame(
            onDismiss = ::hideDialog,
            title = dialogTitle,
        ) {
            Spacer(Modifier.height(SPACE_BETWEEN_SECTIONS.dp))
            Box(modifier = Modifier.weight(1f, fill = false)) { DialogBody() }
            Spacer(Modifier.height(SPACE_BETWEEN_SECTIONS.dp))
            Buttons()
        }
    }

    /** The phone's `ConfirmDialog.Buttons` (the shared [ButtonRow]: the red cancel + the accent confirm). */
    @Composable
    private fun Buttons() = ButtonRow(
        buttons = listOf(
            DialogButton(
                text = stringResource(Res.string.cancel),
                onClick = ::hideDialog,
                danger = true,
            ),
            DialogButton(
                text = stringResource(Res.string.confirm),
                onClick = ::onConfirm,
                primary = true,
            ),
        ),
    )

    private companion object {
        /** The phone's `Dialog` constants (`Dialog.kt` 16-55). */
        const val SPACE_BETWEEN_SECTIONS = 20
    }

    abstract class Item : MenuIcon {

        companion object {
            /** The phone's "All" entry: checks every item, never unchecked by a tap. */
            val SELECT_ALL: Item by lazy {
                object : Item() {
                    override val id: String = "select_all"
                    override val menuIconTitle: String
                        @Composable
                        get() = stringResource(Res.string.all)

                    override fun onShortClick() {
                        // Disable uncheck
                        if (selected) return
                        super.onShortClick()
                    }
                }
            }
        }

        abstract val id: String

        /** The phone's items have no icon (`iconId = -1`); the PC needs a resource, never drawn. */
        override val iconId: DrawableResource = Res.drawable.unchecked_outline

        var selected: Boolean by mutableStateOf(false)

        override fun onShortClick() {
            if (selected) SELECT_ALL.selected = false
            selected = !selected
        }

        @Composable
        override fun ToolBarButton() {
            LaunchedEffect(SELECT_ALL.selected) {
                if (SELECT_ALL.selected) selected = true
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = ::onShortClick,
                    ),
            ) {
                Icon(
                    painter = painterResource(if (selected) Res.drawable.checked_filled else Res.drawable.unchecked_outline),
                    contentDescription = null,
                    tint = if (selected) colorPalette().accent else colorPalette().textDisabled,
                    modifier = Modifier.size(20.dp),
                )

                Spacer(Modifier.width(5.dp))

                BasicText(
                    text = menuIconTitle,
                    maxLines = 1,
                    modifier = Modifier
                        .weight(1f)
                        .basicMarquee(iterations = Int.MAX_VALUE),
                    style = typography().xs.copy(color = colorPalette().text),
                )
            }
        }
    }
}
