package app.n_zik.compagnon.ui.pairing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.n_zik.compagnon.generated.resources.Res
import app.n_zik.compagnon.generated.resources.alert_circle
import app.n_zik.compagnon.generated.resources.cancel
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
import app.n_zik.compagnon.pairing.PairedStatus
import app.n_zik.compagnon.pairing.PairingRules
import app.n_zik.compagnon.pairing.PairingState
import app.n_zik.compagnon.ui.components.NZikButton
import app.n_zik.compagnon.ui.components.SectionCard
import app.n_zik.compagnon.ui.components.SettingsEntry
import app.n_zik.compagnon.ui.theme.colorPalette
import app.n_zik.compagnon.ui.theme.semiBold
import app.n_zik.compagnon.ui.theme.typography
import org.jetbrains.compose.resources.stringResource

/** Paired: phone name, connection status, "Retry" when it makes sense, "Forget this phone". */
@Composable
fun PairedScreen(state: PairingState.Paired, onRetry: () -> Unit, onForget: () -> Unit) {
    val palette = colorPalette()
    val record = state.record
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionCard(title = stringResource(Res.string.paired_title), icon = Res.drawable.devices) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(Res.string.paired_with, record.serverName),
                    style = typography().l.semiBold,
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
        }
        ForgetCard(onForget)
    }
}

@Composable
private fun StatusWithRetry(text: String, onRetry: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        MessageBanner(text, colorPalette().red)
        NZikButton(text = stringResource(Res.string.retry), onClick = onRetry, primary = false)
    }
}

@Composable
private fun ForgetCard(onForget: () -> Unit) {
    SectionCard(title = stringResource(Res.string.forget_phone), icon = Res.drawable.trash) {
        SettingsEntry(
            title = stringResource(Res.string.forget_phone),
            text = stringResource(Res.string.forget_phone_text),
            icon = Res.drawable.trash,
            onClick = onForget,
        )
    }
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
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionCard(
            title = stringResource(Res.string.unreachable_title),
            icon = Res.drawable.alert_circle,
            description = stringResource(
                Res.string.unreachable_description,
                record.serverName,
                record.serverIps.joinToString(", ") { "$it:${record.serverPort}" },
            ),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                NZikButton(text = stringResource(Res.string.retry), onClick = onRetry)
                if (!editing) {
                    SettingsEntry(
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
                        NZikButton(
                            text = stringResource(Res.string.cancel),
                            onClick = { editing = false },
                            primary = false,
                            modifier = Modifier.weight(1f),
                        )
                        NZikButton(
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
        }
        ForgetCard(onForget)
    }
}

/** The phone revoked this PC: the credential is already erased; offer to pair again. */
@Composable
fun RevokedScreen(onPairAgain: () -> Unit) {
    SectionCard(
        title = stringResource(Res.string.revoked_title),
        icon = Res.drawable.alert_circle,
        description = stringResource(Res.string.revoked_description),
    ) {
        NZikButton(text = stringResource(Res.string.revoked_pair_again), onClick = onPairAgain)
    }
}
