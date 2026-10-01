package app.n_zik.compagnon.ui.pairing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.n_zik.compagnon.generated.resources.Res
import app.n_zik.compagnon.generated.resources.globe
import app.n_zik.compagnon.generated.resources.link
import app.n_zik.compagnon.generated.resources.manual_code
import app.n_zik.compagnon.generated.resources.manual_code_invalid
import app.n_zik.compagnon.generated.resources.manual_description
import app.n_zik.compagnon.generated.resources.manual_ip
import app.n_zik.compagnon.generated.resources.manual_ip_invalid
import app.n_zik.compagnon.generated.resources.manual_port
import app.n_zik.compagnon.generated.resources.manual_port_invalid
import app.n_zik.compagnon.generated.resources.manual_submit
import app.n_zik.compagnon.generated.resources.manual_title
import app.n_zik.compagnon.generated.resources.pair_device_name
import app.n_zik.compagnon.generated.resources.pair_device_name_invalid
import app.n_zik.compagnon.generated.resources.pair_listener_unreachable
import app.n_zik.compagnon.generated.resources.pair_no_address
import app.n_zik.compagnon.generated.resources.pair_show_qr
import app.n_zik.compagnon.generated.resources.pair_title
import app.n_zik.compagnon.pairing.ManualForm
import app.n_zik.compagnon.pairing.PairingState
import app.n_zik.compagnon.ui.components.NZikButton
import app.n_zik.compagnon.ui.components.SectionCard
import app.n_zik.compagnon.ui.components.SettingsEntry
import app.n_zik.compagnon.ui.theme.colorPalette
import org.jetbrains.compose.resources.stringResource

/**
 * Not paired, manual mode (contract §4.6): IP, port, code and device name. Errors of a non-empty
 * field show as soon as it is typed; "Pair" stays disabled until the form is valid.
 */
@Composable
fun ManualPairingScreen(
    state: PairingState.Unpaired,
    onFormChange: (ManualForm) -> Unit,
    onDeviceNameChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onShowQr: () -> Unit,
    onDismissError: () -> Unit,
) {
    val palette = colorPalette()
    val form = state.manual
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (state.noCandidates) {
            MessageBanner(stringResource(Res.string.pair_no_address), palette.accent)
        } else if (state.listenerUnreachable) {
            MessageBanner(stringResource(Res.string.pair_listener_unreachable), palette.accent)
        }
        ErrorBanner(state.error, onDismiss = onDismissError)
        SectionCard(
            title = stringResource(Res.string.manual_title),
            icon = Res.drawable.globe,
            description = stringResource(Res.string.manual_description),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    NZikTextField(
                        value = form.ip,
                        onValueChange = { onFormChange(form.copy(ip = it.trim())) },
                        label = stringResource(Res.string.manual_ip),
                        errorText = if (form.ip.isNotEmpty() && !form.isIpValid) stringResource(Res.string.manual_ip_invalid) else null,
                        modifier = Modifier.weight(2f),
                    )
                    NZikTextField(
                        value = form.port,
                        onValueChange = { value -> onFormChange(form.copy(port = value.filter(Char::isDigit).take(5))) },
                        label = stringResource(Res.string.manual_port),
                        errorText = if (form.port.isNotEmpty() && form.portNumber == null) stringResource(Res.string.manual_port_invalid) else null,
                        numeric = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                NZikTextField(
                    value = form.code,
                    onValueChange = { onFormChange(form.copy(code = it.take(16))) },
                    label = stringResource(Res.string.manual_code),
                    errorText = if (form.code.isNotBlank() && !form.isCodeValid) stringResource(Res.string.manual_code_invalid) else null,
                )
                NZikTextField(
                    value = state.deviceName,
                    onValueChange = onDeviceNameChange,
                    label = stringResource(Res.string.pair_device_name),
                    errorText = if (state.validDeviceName == null) stringResource(Res.string.pair_device_name_invalid) else null,
                )
                NZikButton(
                    text = stringResource(Res.string.manual_submit),
                    onClick = onSubmit,
                    enabled = form.isIpValid && form.portNumber != null && form.isCodeValid && state.validDeviceName != null,
                )
            }
        }
        if (!state.noCandidates) {
            SectionCard(title = stringResource(Res.string.pair_title), icon = Res.drawable.link) {
                SettingsEntry(
                    title = stringResource(Res.string.pair_show_qr),
                    text = "",
                    icon = Res.drawable.link,
                    onClick = onShowQr,
                )
            }
        }
    }
}
