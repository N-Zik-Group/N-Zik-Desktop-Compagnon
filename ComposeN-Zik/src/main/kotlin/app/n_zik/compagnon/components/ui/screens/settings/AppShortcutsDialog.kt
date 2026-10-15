package app.n_zik.compagnon.components.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import app.n_zik.compagnon.colorPalette
import app.n_zik.compagnon.components.dialog.common.ButtonRow
import app.n_zik.compagnon.components.dialog.common.DialogButton
import app.n_zik.compagnon.components.dialog.common.DialogFrame
import app.n_zik.compagnon.generated.resources.*
import app.n_zik.compagnon.tray.AppShortcut
import app.n_zik.compagnon.tray.ShortcutRows
import app.n_zik.compagnon.tray.ShortcutToggle
import app.n_zik.compagnon.tray.move
import app.n_zik.compagnon.tray.toggle
import app.n_zik.compagnon.typography
import app.n_zik.compagnon.uiRoundnessShape
import app.n_zik.compagnon.utils.Preferences
import app.n_zik.compagnon.utils.Toaster
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * The phone's `AppShortcutsSettingsDialog` (order + toggle of the app shortcuts), on the PC the
 * shortcuts of the tray menu: the same rules (Rescue locked, the phone's toast — but no 4-shortcut
 * cap, see [AppShortcut]), the same Reset / Cancel / Confirm. PC adaptation: the rows move with up / down
 * arrows instead of the phone's drag handle (no reorderable-list library on the PC). The choice is
 * stored in `settings.json` (the phone's two CSV keys); the tray re-reads it live.
 *
 * The chrome renders through the shared [DialogFrame] and the Reset / Cancel / Confirm row through
 * the shared [ButtonRow] (the phone's `ToggleListDialog` roles — Reset secondary, Cancel the red
 * `danger`, Confirm the accent `primary`), spec `spec-dialog-framework-port`.
 */
@Composable
fun AppShortcutsDialog(preferences: Preferences, onDismiss: () -> Unit) {
    val initial = remember {
        val s = preferences.settings.value
        ShortcutRows.load(s.appShortcutsOrder, s.appShortcutsEnabled)
    }
    var rows by remember { mutableStateOf(initial) }

    DialogFrame(
        onDismiss = onDismiss,
        title = stringResource(Res.string.app_shortcuts_settings),
    ) {
        Spacer(Modifier.height(16.dp))
        Column(modifier = Modifier.verticalScroll(rememberScrollState()).weight(1f, false)) {
            rows.order.forEachIndexed { index, id ->
                val shortcut = AppShortcut.of(id) ?: return@forEachIndexed
                ShortcutRow(
                    shortcut = shortcut,
                    checked = rows.checked[index],
                    canMoveUp = index > 0,
                    canMoveDown = index < rows.order.lastIndex,
                    onChecked = { value ->
                        when (val result = rows.toggle(index, value)) {
                            is ShortcutToggle.Applied -> rows = result.rows
                            ShortcutToggle.Locked -> Toaster.s(Res.string.app_shortcuts_rescue_locked)
                        }
                    },
                    onMoveUp = { rows = rows.move(index, index - 1) },
                    onMoveDown = { rows = rows.move(index, index + 1) },
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        ButtonRow(
            buttons = listOf(
                DialogButton(
                    text = stringResource(Res.string.reset),
                    onClick = {
                        rows = ShortcutRows.defaults()
                    },
                ),
                DialogButton(
                    text = stringResource(Res.string.cancel),
                    onClick = onDismiss,
                    danger = true,
                ),
                DialogButton(
                    text = stringResource(Res.string.confirm),
                    onClick = {
                        val confirmed = rows
                        preferences.update {
                            it.copy(appShortcutsOrder = confirmed.orderCsv(), appShortcutsEnabled = confirmed.enabledCsv())
                        }
                        Toaster.s(Res.string.toast_preference_saved)
                        onDismiss()
                    },
                    primary = true,
                ),
            ),
        )
    }
}

@Composable
private fun ShortcutRow(
    shortcut: AppShortcut,
    checked: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onChecked: (Boolean) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    val colorPalette = colorPalette()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clip(uiRoundnessShape()).clickable { onChecked(!checked) }.padding(horizontal = 8.dp),
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onChecked,
            colors = CheckboxDefaults.colors(
                checkedColor = colorPalette.accent,
                uncheckedColor = colorPalette.textSecondary,
                checkmarkColor = colorPalette.onAccent,
            ),
        )
        Icon(
            painter = painterResource(shortcut.iconId),
            contentDescription = null,
            tint = colorPalette.text,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        BasicText(
            text = stringResource(shortcut.labelId),
            style = typography().s.copy(color = colorPalette.text),
            modifier = Modifier.weight(1f),
        )
        MoveArrow(Res.drawable.chevron_up, enabled = canMoveUp, onClick = onMoveUp)
        MoveArrow(Res.drawable.chevron_down, enabled = canMoveDown, onClick = onMoveDown)
    }
}

@Composable
private fun MoveArrow(icon: org.jetbrains.compose.resources.DrawableResource, enabled: Boolean, onClick: () -> Unit) {
    val colorPalette = colorPalette()
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(36.dp).clip(uiRoundnessShape()).clickable(enabled = enabled, onClick = onClick),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = if (enabled) colorPalette.text else colorPalette.textDisabled,
            modifier = Modifier.size(18.dp),
        )
    }
}
