package app.n_zik.compagnon.components.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.n_zik.compagnon.colorPalette
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
import app.n_zik.compagnon.utils.medium
import app.n_zik.compagnon.utils.semiBold
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * The phone's `AppShortcutsSettingsDialog` (order + toggle of the app shortcuts), on the PC the
 * shortcuts of the tray menu: the same rules (Rescue locked, the phone's toast — but no 4-shortcut
 * cap, see [AppShortcut]), the same Reset / Cancel / Confirm. PC adaptation: the rows move with up / down
 * arrows instead of the phone's drag handle (no reorderable-list library on the PC). The choice is
 * stored in `settings.json` (the phone's two CSV keys); the tray re-reads it live.
 */
@Composable
fun AppShortcutsDialog(preferences: Preferences, onDismiss: () -> Unit) {
    val colorPalette = colorPalette()
    val initial = remember {
        val s = preferences.settings.value
        ShortcutRows.load(s.appShortcutsOrder, s.appShortcutsEnabled)
    }
    var rows by remember { mutableStateOf(initial) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Card(
                modifier = Modifier.fillMaxWidth(0.9f).widthIn(max = 560.dp).padding(16.dp),
                shape = uiRoundnessShape(),
                colors = CardDefaults.cardColors(containerColor = colorPalette.background1),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    BasicText(
                        text = stringResource(Res.string.app_shortcuts_settings),
                        style = typography().l.semiBold.copy(color = colorPalette.text),
                    )
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
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DialogAction(stringResource(Res.string.reset), primary = false, Modifier.weight(1f)) {
                            rows = ShortcutRows.defaults()
                        }
                        DialogAction(stringResource(Res.string.cancel), primary = false, Modifier.weight(1f), onClick = onDismiss)
                        DialogAction(stringResource(Res.string.confirm), primary = true, Modifier.weight(1f)) {
                            val confirmed = rows
                            preferences.update {
                                it.copy(appShortcutsOrder = confirmed.orderCsv(), appShortcutsEnabled = confirmed.enabledCsv())
                            }
                            Toaster.s(Res.string.toast_preference_saved)
                            onDismiss()
                        }
                    }
                }
            }
        }
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

@Composable
private fun DialogAction(text: String, primary: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colorPalette = colorPalette()
    Box(
        modifier = modifier
            .background(if (primary) colorPalette.accent else colorPalette.background2, uiRoundnessShape())
            .clip(uiRoundnessShape())
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = text,
            style = typography().xs.medium.copy(
                color = if (primary) colorPalette.onAccent else colorPalette.text,
                textAlign = TextAlign.Center,
            ),
        )
    }
}
