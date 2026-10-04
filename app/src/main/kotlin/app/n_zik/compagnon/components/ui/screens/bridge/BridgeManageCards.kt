package app.n_zik.compagnon.components.ui.screens.bridge

import app.n_zik.compagnon.uiRoundnessShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.ColumnScope
import app.n_zik.compagnon.components.themed.ConfirmationDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.n_zik.compagnon.bridge.ConnectionState
import app.n_zik.compagnon.bridge.pairing.PairedStatus
import app.n_zik.compagnon.bridge.pairing.PairingRecord
import app.n_zik.compagnon.bridge.pairing.PairingRules
import app.n_zik.compagnon.bridge.pairing.PairingState
import app.n_zik.compagnon.components.settings.OtherSettingsEntry
import app.n_zik.compagnon.components.settings.SettingsSectionCard
import app.n_zik.compagnon.colorPalette
import app.n_zik.compagnon.utils.semiBold
import app.n_zik.compagnon.typography
import app.n_zik.compagnon.generated.resources.Res
import app.n_zik.compagnon.generated.resources.alert_circle
import app.n_zik.compagnon.generated.resources.cancel
import app.n_zik.compagnon.generated.resources.close
import app.n_zik.compagnon.generated.resources.connection_kicked
import app.n_zik.compagnon.generated.resources.connection_other_active
import app.n_zik.compagnon.generated.resources.connection_reconnecting_in
import app.n_zik.compagnon.generated.resources.connection_replaced
import app.n_zik.compagnon.generated.resources.connection_server_stopped
import app.n_zik.compagnon.generated.resources.devices
import app.n_zik.compagnon.generated.resources.error_unknown_code
import app.n_zik.compagnon.generated.resources.forget_phone
import app.n_zik.compagnon.generated.resources.forget_phone_text
import app.n_zik.compagnon.generated.resources.manual_ip
import app.n_zik.compagnon.generated.resources.manual_ip_invalid
import app.n_zik.compagnon.generated.resources.paired_as
import app.n_zik.compagnon.generated.resources.paired_checking
import app.n_zik.compagnon.generated.resources.paired_error
import app.n_zik.compagnon.generated.resources.paired_incompatible
import app.n_zik.compagnon.generated.resources.paired_ok
import app.n_zik.compagnon.generated.resources.paired_other_active
import app.n_zik.compagnon.generated.resources.paired_other_active_unknown
import app.n_zik.compagnon.generated.resources.paired_title
import app.n_zik.compagnon.generated.resources.paired_with
import app.n_zik.compagnon.generated.resources.pencil
import app.n_zik.compagnon.generated.resources.phone_panel_title
import app.n_zik.compagnon.generated.resources.reconnect
import app.n_zik.compagnon.generated.resources.retry
import app.n_zik.compagnon.generated.resources.revoked_description
import app.n_zik.compagnon.generated.resources.revoked_pair_again
import app.n_zik.compagnon.generated.resources.revoked_title
import app.n_zik.compagnon.generated.resources.trash
import app.n_zik.compagnon.generated.resources.unreachable_description
import app.n_zik.compagnon.generated.resources.unreachable_edit_ip
import app.n_zik.compagnon.generated.resources.unreachable_edit_ip_text
import app.n_zik.compagnon.generated.resources.unreachable_port_kept
import app.n_zik.compagnon.generated.resources.unreachable_save_ip
import app.n_zik.compagnon.generated.resources.unreachable_title
import org.jetbrains.compose.resources.stringResource

/** Paired: phone name, connection status, "Retry" when it makes sense, "Forget this phone". */
@Composable
fun PairedScreen(state: PairingState.Paired, onRetry: () -> Unit, onForget: () -> Unit) {
    val palette = colorPalette()
    val record = state.record
    Column {
        SettingsSectionCard(title = stringResource(Res.string.paired_title), icon = Res.drawable.devices, content = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(Res.string.paired_with, record.serverName),
                    style = typography().s.semiBold,
                    color = palette.text,
                )
                Text(stringResource(Res.string.paired_as, record.deviceName), style = typography().xs, color = palette.textSecondary)
                when (val status = state.status) {
                    PairedStatus.Checking -> Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CircularProgressIndicator(color = palette.accent, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                        Text(stringResource(Res.string.paired_checking), style = typography().xs, color = palette.textSecondary)
                    }
                    PairedStatus.Ok -> Text(stringResource(Res.string.paired_ok), style = typography().xs, color = palette.accent)
                    is PairedStatus.OtherActive -> StatusWithRetry(
                        stringResource(
                            Res.string.paired_other_active,
                            status.deviceName ?: stringResource(Res.string.paired_other_active_unknown),
                        ),
                        onRetry,
                    )
                    is PairedStatus.Incompatible -> StatusWithRetry(
                        stringResource(Res.string.paired_incompatible, status.contractVersion),
                        onRetry,
                    )
                    is PairedStatus.Error -> StatusWithRetry(
                        stringResource(Res.string.paired_error, status.status, status.code ?: stringResource(Res.string.error_unknown_code)),
                        onRetry,
                    )
                    PairedStatus.Unreachable -> Unit // own screen
                }
            }
        })
        ForgetCard(onForget)
    }
}

@Composable
private fun StatusWithRetry(text: String, onRetry: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        MessageBanner(text, isError = true)
        PairingButton(text = stringResource(Res.string.retry), onClick = onRetry, containerColor = colorPalette().background2, contentColor = colorPalette().text)
    }
}

@Composable
private fun ForgetCard(onForget: () -> Unit) {
    SettingsSectionCard(title = stringResource(Res.string.forget_phone), icon = Res.drawable.trash, content = {
        ForgetEntry(onForget)
    })
}

/** "Forget this phone", confirmed first by the phone's `ConfirmationDialog` (a destructive action). */
@Composable
private fun ForgetEntry(onForget: () -> Unit) {
    var confirmForget by remember { mutableStateOf(false) }
    if (confirmForget) {
        ConfirmationDialog(
            text = stringResource(Res.string.forget_phone_text),
            onDismiss = { confirmForget = false },
            onConfirm = {
                confirmForget = false
                onForget()
            },
        )
    }
    OtherSettingsEntry(
        title = stringResource(Res.string.forget_phone),
        text = stringResource(Res.string.forget_phone_text),
        icon = Res.drawable.trash,
        onClick = { confirmForget = true },
    )
}

/**
 * Paired but `meta` unreachable (contract §4.7): Retry, or edit the IP only — token and port kept
 * and prefilled. Never a loss of pairing.
 */
@Composable
fun UnreachableScreen(state: PairingState.Paired, onRetry: () -> Unit, onEditIp: (String) -> Unit, onForget: () -> Unit) {
    val palette = colorPalette()
    val record = state.record
    var editing by remember { mutableStateOf(false) }
    var ip by remember(record.serverIps) { mutableStateOf(record.serverIps.firstOrNull().orEmpty()) }
    Column {
        SettingsSectionCard(
            title = stringResource(Res.string.unreachable_title),
            icon = Res.drawable.alert_circle,
            description = stringResource(
                Res.string.unreachable_description,
                record.serverName,
                record.serverIps.joinToString(", ") { "$it:${record.serverPort}" },
            ),
            content = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PairingButton(text = stringResource(Res.string.retry), onClick = onRetry)
                if (!editing) {
                    OtherSettingsEntry(
                        title = stringResource(Res.string.unreachable_edit_ip),
                        text = stringResource(Res.string.unreachable_edit_ip_text),
                        icon = Res.drawable.pencil,
                        onClick = { editing = true },
                    )
                } else {
                    val valid = PairingRules.isIpv4(ip)
                    NZikTextField(
                        value = ip,
                        onValueChange = { ip = it.trim() },
                        label = stringResource(Res.string.manual_ip),
                        errorText = if (ip.isNotEmpty() && !valid) stringResource(Res.string.manual_ip_invalid) else null,
                    )
                    Text(
                        stringResource(Res.string.unreachable_port_kept, record.serverPort),
                        style = typography().xs,
                        color = palette.textSecondary,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        PairingButton(
                            text = stringResource(Res.string.cancel),
                            onClick = { editing = false },
                            containerColor = colorPalette().background2,
                            contentColor = colorPalette().text,
                            modifier = Modifier.weight(1f),
                        )
                        PairingButton(
                            text = stringResource(Res.string.unreachable_save_ip),
                            onClick = {
                                editing = false
                                onEditIp(ip)
                            },
                            enabled = valid,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        })
        ForgetCard(onForget)
    }
}

/** The phone revoked this PC: the credential is already erased; offer to pair again. */
@Composable
fun RevokedScreen(onPairAgain: () -> Unit) {
    SettingsSectionCard(
        title = stringResource(Res.string.revoked_title),
        icon = Res.drawable.alert_circle,
        description = stringResource(Res.string.revoked_description),
            content = {
        PairingButton(text = stringResource(Res.string.revoked_pair_again), onClick = onPairAgain)
    })
}

/** Contract §6.4 states that need words, with "Reconnect" / "Retry" where the user must act. */
@Composable
fun ConnectionBanner(connection: ConnectionState, onReconnect: () -> Unit) {
    val palette = colorPalette()
    val (text, button) = when (connection) {
        ConnectionState.Live, ConnectionState.Connecting, ConnectionState.Revoked -> return
        is ConnectionState.Reconnecting ->
            stringResource(Res.string.connection_reconnecting_in, ((connection.delayMs + 999) / 1_000).toInt()) to null
        is ConnectionState.ServerStopped -> stringResource(Res.string.connection_server_stopped) to Res.string.reconnect
        ConnectionState.Kicked -> stringResource(Res.string.connection_kicked) to Res.string.reconnect
        ConnectionState.Replaced -> stringResource(Res.string.connection_replaced) to Res.string.reconnect
        is ConnectionState.OtherActive -> stringResource(
            Res.string.connection_other_active,
            connection.deviceName ?: stringResource(Res.string.paired_other_active_unknown),
        ) to Res.string.retry
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MessageBanner(text, isError = button != null, modifier = Modifier.weight(1f))
        if (button != null) {
            TextButton(onClick = onReconnect) { Text(stringResource(button), style = typography().xs.semiBold, color = palette.accent) }
        }
    }
}

/**
 * The "Paired" content of story 10, now a panel of the main window ("PC server" in the header menu): phone
 * name, connection, "Forget this phone" (confirmed). Shown in the same [OverlayPanel] as the settings.
 */
@Composable
fun PhonePanel(record: PairingRecord, connection: ConnectionState, onClose: () -> Unit, onForget: () -> Unit) {
    val palette = colorPalette()
    OverlayPanel(onClose = onClose) {
        SettingsSectionCard(title = stringResource(Res.string.phone_panel_title), icon = Res.drawable.devices, content = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(Res.string.paired_with, record.serverName), style = typography().s.semiBold, color = palette.text)
                Text(stringResource(Res.string.paired_as, record.deviceName), style = typography().xs, color = palette.textSecondary)
                ConnectionIndicator(connection)
                ForgetEntry(onForget)
            }
        })
        PairingButton(
            text = stringResource(Res.string.close),
            onClick = onClose,
            containerColor = colorPalette().background2,
            contentColor = colorPalette().text,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}

/**
 * Compagnon only (the phone navigates to its pages): the overlay that holds the "Phone" panel and the
 * settings, one container for both: `background0` at 70 % over the window (a click closes it), a column at
 * most 560 dp wide, 24 dp from the edges, on `background0` in the UI roundness, scrolling, 16 dp above and
 * below its content.
 */
@Composable
fun OverlayPanel(onClose: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val palette = colorPalette()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background0.copy(alpha = 0.7f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .padding(24.dp)
                .background(palette.background0, shape = uiRoundnessShape())
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .verticalScroll(rememberScrollState())
                .padding(vertical = 16.dp),
            content = content,
        )
    }
}
