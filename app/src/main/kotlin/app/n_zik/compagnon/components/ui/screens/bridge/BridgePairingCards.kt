package app.n_zik.compagnon.components.ui.screens.bridge

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.n_zik.compagnon.bridge.pairing.ManualForm
import app.n_zik.compagnon.bridge.pairing.PairingState
import app.n_zik.compagnon.components.settings.OtherSettingsEntry
import app.n_zik.compagnon.components.settings.SettingsSectionCard
import app.n_zik.compagnon.colorPalette
import app.n_zik.compagnon.utils.formatText
import app.n_zik.compagnon.utils.semiBold
import app.n_zik.compagnon.typography
import app.n_zik.compagnon.uiRoundnessShape
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
import app.n_zik.compagnon.generated.resources.pair_manual_entry
import app.n_zik.compagnon.generated.resources.pair_manual_entry_text
import app.n_zik.compagnon.generated.resources.pair_no_address
import app.n_zik.compagnon.generated.resources.pair_qr_addresses
import app.n_zik.compagnon.generated.resources.pair_qr_description
import app.n_zik.compagnon.generated.resources.pair_qr_preparing
import app.n_zik.compagnon.generated.resources.pair_qr_renew_hint
import app.n_zik.compagnon.generated.resources.pair_qr_waiting
import app.n_zik.compagnon.generated.resources.pair_show_qr
import app.n_zik.compagnon.generated.resources.pair_title
import app.n_zik.compagnon.generated.resources.pencil
import org.jetbrains.compose.resources.stringResource

/** Not paired, QR mode (contract §4.2–§4.4): QR, editable PC name, status, "Manual entry". */
@Composable
fun PairPcCard(
    state: PairingState.Unpaired,
    onDeviceNameChange: (String) -> Unit,
    onManualEntry: () -> Unit,
    onDismissError: () -> Unit,
) {
    val palette = colorPalette()
    Column {
        ErrorBanner(state.error, onDismiss = onDismissError, modifier = BANNER_MODIFIER)
        SettingsSectionCard(
            title = stringResource(Res.string.pair_title),
            icon = Res.drawable.link,
            description = stringResource(Res.string.pair_qr_description),
            content = {
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
                        text = formatText(stringResource(Res.string.pair_qr_addresses), payload.ips.joinToString(", "), payload.port),
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
        })
        SettingsSectionCard(title = stringResource(Res.string.manual_title), icon = Res.drawable.pencil, content = {
            OtherSettingsEntry(
                title = stringResource(Res.string.pair_manual_entry),
                text = stringResource(Res.string.pair_manual_entry_text),
                icon = Res.drawable.pencil,
                onClick = onManualEntry,
            )
        })
    }
}

/**
 * Not paired, manual mode (contract §4.6): IP, port, code and device name. Errors of a non-empty
 * field show as soon as it is typed; "Pair" stays disabled until the form is valid.
 */
@Composable
fun ManualPairingCard(
    state: PairingState.Unpaired,
    onFormChange: (ManualForm) -> Unit,
    onDeviceNameChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onShowQr: () -> Unit,
    onDismissError: () -> Unit,
) {
    val palette = colorPalette()
    val form = state.manual
    Column {
        if (state.noCandidates) {
            MessageBanner(stringResource(Res.string.pair_no_address), isError = false, modifier = BANNER_MODIFIER)
        } else if (state.listenerUnreachable) {
            MessageBanner(stringResource(Res.string.pair_listener_unreachable), isError = false, modifier = BANNER_MODIFIER)
        }
        ErrorBanner(state.error, onDismiss = onDismissError, modifier = BANNER_MODIFIER)
        SettingsSectionCard(
            title = stringResource(Res.string.manual_title),
            icon = Res.drawable.globe,
            description = stringResource(Res.string.manual_description),
            content = {
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
                PairingButton(
                    text = stringResource(Res.string.manual_submit),
                    onClick = onSubmit,
                    enabled = form.isIpValid && form.portNumber != null && form.isCodeValid && state.validDeviceName != null,
                )
            }
        })
        if (!state.noCandidates) {
            SettingsSectionCard(title = stringResource(Res.string.pair_title), icon = Res.drawable.link, content = {
                OtherSettingsEntry(
                    title = stringResource(Res.string.pair_show_qr),
                    text = "",
                    icon = Res.drawable.link,
                    onClick = onShowQr,
                )
            })
        }
    }
}

/**
 * Port of `PairingButton` (phone's `app/n_zik/android/components/ui/screens/bridge/BridgePairingCards.kt` 359):
 * a Material button in the UI roundness, at least 48 dp high, full width unless [fillWidth] is `false`,
 * text in s.semiBold. The phone's primary buttons are `accent` / `textSecondary`, its secondary ones
 * `background2` / `text`.
 */
@Composable
fun PairingButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = colorPalette().accent,
    contentColor: Color = colorPalette().textSecondary,
    enabled: Boolean = true,
    fillWidth: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(containerColor = containerColor, contentColor = contentColor),
        shape = uiRoundnessShape(),
        modifier = (if (fillWidth) modifier.fillMaxWidth() else modifier).heightIn(min = 48.dp),
    ) {
        Text(text, style = typography().s.semiBold)
    }
}

/** PC only: the pairing banners line up with the phone's cards (16 dp from the sides, 16 dp below). */
internal val BANNER_MODIFIER = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
