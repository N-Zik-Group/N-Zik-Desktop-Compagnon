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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.Dialog
import app.n_zik.compagnon.uiRoundnessShape
import app.n_zik.compagnon.utils.medium
import app.n_zik.compagnon.utils.semiBold
import app.n_zik.compagnon.generated.resources.*
import app.n_zik.compagnon.typography
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Port of the phone's `CheckboxDialog` (`app/n_zik/android/components/dialog/common/CheckboxDialog.kt`) with its
 * bases `Dialog` / `InteractiveDialog` / `ConfirmDialog` (`dialog/common/Dialog.kt` 96-150,
 * `InteractiveDialog.kt`, `ConfirmDialog.kt`), 1:1: a wrap-content card (at most 95 % × 80 % of the window,
 * 16 dp margin, `background1`, 8 dp elevation, UI roundness) with the centred `l` semi-bold title, 20 dp, the
 * body (a column of checkable [items], 80 % wide, the phone's [Item.SELECT_ALL] semantics), 20 dp, then the
 * two Material buttons 12 dp apart — Cancel (`background2`, its label in red at 30 %) and Confirm (`accent`,
 * its label in `onAccent`). Confirm runs [onConfirm] only (the implementation closes the dialog, as on the
 * phone). The base of the phone's `UpdateSongDialog`.
 *
 * Ported ahead for `spec-home-songs-toolbar-actions` (its update dialog): no PC screen opens one yet.
 * PC adaptation: the window size stands in for the phone's screen (portrait ratios; the PC has no
 * orientation), the dialog drawn without the platform's default width so those ratios apply.
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
        // The phone's sizes are fractions of the SCREEN (`LocalConfiguration`): the window's size here
        val window = LocalWindowInfo.current.containerSize
        val density = LocalDensity.current
        val screenWidth = with(density) { window.width.toDp() }
        val screenHeight = with(density) { window.height.toDp() }
        Dialog(onDismissRequest = ::hideDialog, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            run {
                Card(
                    modifier = Modifier
                        .wrapContentSize()
                        .sizeIn(maxWidth = screenWidth * MAX_WIDTH_PORTRAIT, maxHeight = screenHeight * MAX_HEIGHT_PORTRAIT)
                        .padding(16.dp),
                    shape = uiRoundnessShape(),
                    colors = CardDefaults.cardColors(containerColor = colorPalette().background1),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            Modifier.padding(bottom = 4.dp).fillMaxWidth(.9f),
                            contentAlignment = Alignment.Center,
                        ) {
                            BasicText(
                                text = dialogTitle,
                                style = typography().l.semiBold.copy(color = colorPalette().text),
                            )
                        }
                        Spacer(Modifier.height(SPACE_BETWEEN_SECTIONS.dp))
                        Box(modifier = Modifier.weight(1f, fill = false)) { DialogBody() }
                        Spacer(Modifier.height(SPACE_BETWEEN_SECTIONS.dp))
                        Buttons()
                    }
                }
            }
        }
    }

    /** The phone's `ConfirmDialog.Buttons`. */
    @Composable
    private fun Buttons() = Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Button(
            onClick = ::hideDialog,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(containerColor = colorPalette().background2, contentColor = colorPalette().text),
            shape = uiRoundnessShape(),
        ) {
            BasicText(
                text = stringResource(Res.string.cancel),
                style = typography().xs.medium.copy(color = colorPalette().red.copy(alpha = .3f), textAlign = TextAlign.Center),
                // The phone's `InteractiveDialog.ButtonModifier`
                modifier = Modifier.wrapContentWidth(Alignment.CenterHorizontally).clip(uiRoundnessShape()).clickable(onClick = ::hideDialog),
            )
        }
        Button(
            onClick = ::onConfirm,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(containerColor = colorPalette().accent, contentColor = colorPalette().textSecondary),
            shape = uiRoundnessShape(),
        ) {
            BasicText(
                text = stringResource(Res.string.confirm),
                style = typography().xs.medium.copy(color = colorPalette().onAccent, textAlign = TextAlign.Center),
                modifier = Modifier.wrapContentWidth(Alignment.CenterHorizontally).clip(uiRoundnessShape()).clickable(onClick = ::onConfirm),
            )
        }
    }

    private companion object {
        /** The phone's `Dialog` constants (`Dialog.kt` 16-55). */
        const val SPACE_BETWEEN_SECTIONS = 20
        const val MAX_WIDTH_PORTRAIT = .95f
        const val MAX_HEIGHT_PORTRAIT = .8f
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
