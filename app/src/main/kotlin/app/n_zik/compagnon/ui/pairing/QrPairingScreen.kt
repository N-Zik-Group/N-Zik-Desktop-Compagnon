package app.n_zik.compagnon.ui.pairing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.n_zik.compagnon.generated.resources.Res
import app.n_zik.compagnon.generated.resources.link
import app.n_zik.compagnon.generated.resources.manual_title
import app.n_zik.compagnon.generated.resources.pair_device_name
import app.n_zik.compagnon.generated.resources.pair_device_name_invalid
import app.n_zik.compagnon.generated.resources.pair_manual_entry
import app.n_zik.compagnon.generated.resources.pair_manual_entry_text
import app.n_zik.compagnon.generated.resources.pair_qr_addresses
import app.n_zik.compagnon.generated.resources.pair_qr_description
import app.n_zik.compagnon.generated.resources.pair_qr_preparing
import app.n_zik.compagnon.generated.resources.pair_qr_renew_hint
import app.n_zik.compagnon.generated.resources.pair_qr_waiting
import app.n_zik.compagnon.generated.resources.pair_title
import app.n_zik.compagnon.generated.resources.pencil
import app.n_zik.compagnon.pairing.PairingState
import app.n_zik.compagnon.ui.components.SectionCard
import app.n_zik.compagnon.ui.components.SettingsEntry
import app.n_zik.compagnon.ui.theme.colorPalette
import app.n_zik.compagnon.ui.theme.typography
import org.jetbrains.compose.resources.stringResource

/** Not paired, QR mode (contract §4.2–§4.4): QR, editable PC name, status, "Manual entry". */
@Composable
fun QrPairingScreen(
    state: PairingState.Unpaired,
    onDeviceNameChange: (String) -> Unit,
    onManualEntry: () -> Unit,
    onDismissError: () -> Unit,
) {
    val palette = colorPalette()
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ErrorBanner(state.error, onDismiss = onDismissError)
        SectionCard(
            title = stringResource(Res.string.pair_title),
            icon = Res.drawable.link,
            description = stringResource(Res.string.pair_qr_description),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val payload = state.qrPayload
                if (payload != null) {
                    QrCodeImage(payload.toJson(), size = 240.dp)
                } else {
                    Box(modifier = Modifier.size(288.dp), contentAlignment = Alignment.Center) {
                        if (state.validDeviceName != null) CircularProgressIndicator(color = palette.accent)
                    }
                }
                Text(
                    text = stringResource(if (payload != null) Res.string.pair_qr_waiting else Res.string.pair_qr_preparing),
                    style = typography().s,
                    color = palette.text,
                )
                if (payload != null) {
                    Text(
                        text = stringResource(Res.string.pair_qr_addresses, payload.ips.joinToString(", "), payload.port),
                        style = typography().xxs,
                        color = palette.textSecondary,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = stringResource(Res.string.pair_qr_renew_hint),
                        style = typography().xxs,
                        color = palette.textSecondary,
                    )
                }
                NZikTextField(
                    value = state.deviceName,
                    onValueChange = onDeviceNameChange,
                    label = stringResource(Res.string.pair_device_name),
                    errorText = if (state.validDeviceName == null) stringResource(Res.string.pair_device_name_invalid) else null,
                )
            }
        }
        SectionCard(title = stringResource(Res.string.manual_title), icon = Res.drawable.pencil) {
            SettingsEntry(
                title = stringResource(Res.string.pair_manual_entry),
                text = stringResource(Res.string.pair_manual_entry_text),
                icon = Res.drawable.pencil,
                onClick = onManualEntry,
            )
        }
    }
}
