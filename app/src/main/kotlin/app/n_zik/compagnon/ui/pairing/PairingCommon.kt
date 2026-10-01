package app.n_zik.compagnon.ui.pairing

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.n_zik.compagnon.generated.resources.Res
import app.n_zik.compagnon.generated.resources.alert_circle
import app.n_zik.compagnon.generated.resources.dismiss
import app.n_zik.compagnon.generated.resources.error_code_rejected
import app.n_zik.compagnon.generated.resources.error_incompatible_version
import app.n_zik.compagnon.generated.resources.error_listener_failed
import app.n_zik.compagnon.generated.resources.error_phone_unreachable
import app.n_zik.compagnon.generated.resources.error_rate_limited
import app.n_zik.compagnon.generated.resources.error_storage_failed
import app.n_zik.compagnon.generated.resources.error_unexpected
import app.n_zik.compagnon.generated.resources.error_unknown_code
import app.n_zik.compagnon.pairing.PairingError
import app.n_zik.compagnon.ui.theme.colorPalette
import app.n_zik.compagnon.ui.theme.typography
import app.n_zik.compagnon.ui.theme.uiRoundnessShape
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** UI text of a [PairingError]; decided from the error kind (itself from the contract `code`). */
@Composable
fun pairingErrorText(error: PairingError): String = when (error) {
    PairingError.CodeRejected -> stringResource(Res.string.error_code_rejected)
    is PairingError.RateLimited -> stringResource(Res.string.error_rate_limited, ((error.retryAfterMs + 999) / 1_000).toInt())
    is PairingError.IncompatibleVersion -> stringResource(Res.string.error_incompatible_version, error.contractVersion)
    PairingError.PhoneUnreachable -> stringResource(Res.string.error_phone_unreachable)
    PairingError.StorageFailed -> stringResource(Res.string.error_storage_failed)
    PairingError.ListenerFailed -> stringResource(Res.string.error_listener_failed)
    is PairingError.Unexpected -> stringResource(
        Res.string.error_unexpected,
        error.status,
        error.code ?: stringResource(Res.string.error_unknown_code),
    )
}

/** Tinted message box with an alert icon; [color] is the palette's red for errors, the accent for notices. */
@Composable
fun MessageBanner(text: String, color: Color, modifier: Modifier = Modifier, onDismiss: (() -> Unit)? = null) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(color.copy(alpha = 0.12f), uiRoundnessShape())
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(painterResource(Res.drawable.alert_circle), contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        Text(text, style = typography().xs, color = colorPalette().text, modifier = Modifier.weight(1f))
        if (onDismiss != null) {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.dismiss), style = typography().xs, color = color)
            }
        }
    }
}

@Composable
fun ErrorBanner(error: PairingError?, onDismiss: (() -> Unit)? = null) {
    if (error != null) MessageBanner(pairingErrorText(error), colorPalette().red, onDismiss = onDismiss)
}

/** Outlined text field in the N-Zik palette, with an optional error line. */
@Composable
fun NZikTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    errorText: String? = null,
    enabled: Boolean = true,
    numeric: Boolean = false,
) {
    val palette = colorPalette()
    Column(modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            singleLine = true,
            enabled = enabled,
            isError = errorText != null,
            shape = uiRoundnessShape(),
            textStyle = typography().s,
            keyboardOptions = KeyboardOptions(keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Text),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = palette.accent,
                unfocusedBorderColor = palette.background3,
                focusedLabelColor = palette.accent,
                unfocusedLabelColor = palette.textSecondary,
                cursorColor = palette.accent,
                focusedTextColor = palette.text,
                unfocusedTextColor = palette.text,
                disabledTextColor = palette.textDisabled,
                errorBorderColor = palette.red,
                errorLabelColor = palette.red,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        if (errorText != null) {
            Text(
                errorText,
                style = typography().xxs,
                color = palette.red,
                modifier = Modifier.padding(start = 12.dp, top = 4.dp),
            )
        }
    }
}
