package app.n_zik.compagnon.components.ui.screens.bridge

import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.n_zik.compagnon.bridge.pairing.PairingError
import app.n_zik.compagnon.bridge.state.CommandKind
import app.n_zik.compagnon.bridge.state.PlayerNotice
import app.n_zik.compagnon.colorPalette
import app.n_zik.compagnon.typography
import app.n_zik.compagnon.generated.resources.*
import app.n_zik.compagnon.utils.formatMessage
import app.n_zik.compagnon.utils.formatText
import app.n_zik.compagnon.utils.Toaster
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

/** UI text of a [PairingError]; decided from the error kind (itself from the contract `code`). */
@Composable
fun pairingErrorText(error: PairingError): String = when (error) {
    PairingError.CodeRejected -> stringResource(Res.string.error_code_rejected)
    is PairingError.RateLimited -> formatText(stringResource(Res.string.error_rate_limited), ((error.retryAfterMs + 999) / 1_000).toInt())
    is PairingError.IncompatibleVersion -> formatText(stringResource(Res.string.error_incompatible_version), error.contractVersion)
    PairingError.PhoneUnreachable -> stringResource(Res.string.error_phone_unreachable)
    PairingError.StorageFailed -> stringResource(Res.string.error_storage_failed)
    PairingError.ListenerFailed -> stringResource(Res.string.error_listener_failed)
    is PairingError.Unexpected -> formatText(
        stringResource(Res.string.error_unexpected),
        error.status,
        error.code ?: stringResource(Res.string.error_unknown_code),
    )
}

/**
 * An inline message, as the phone's pairing card shows its result line (`BridgePairingCards.kt` 203-212): `xs`
 * text, centred, in the palette's red for an error ([isError]) or in the text colour otherwise. [onDismiss]
 * adds a "Dismiss" [PairingButton] under it (`background2` / text, the phone's secondary buttons).
 */
@Composable
fun MessageBanner(text: String, isError: Boolean, modifier: Modifier = Modifier, onDismiss: (() -> Unit)? = null) {
    val palette = colorPalette()
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = text,
            style = typography().xs,
            color = if (isError) palette.red else palette.text,
            textAlign = TextAlign.Center,
        )
        if (onDismiss != null) {
            Spacer(modifier = Modifier.height(8.dp))
            PairingButton(
                text = stringResource(Res.string.dismiss),
                onClick = onDismiss,
                containerColor = palette.background2,
                contentColor = palette.text,
            )
        }
    }
}

@Composable
fun ErrorBanner(error: PairingError?, onDismiss: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    if (error != null) MessageBanner(pairingErrorText(error), isError = true, modifier = modifier, onDismiss = onDismiss)
}

/**
 * Outlined text field aligned on the phone's (`AccountsSettings.kt` 1989-2002): single line, full width, the
 * hint as a placeholder in `s` `textSecondary`, text in `text`, cursor and focused border in `accent`,
 * unfocused border in `textSecondary`. PC only: the error line under it (`xxs` red) and the red error border.
 */
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
            placeholder = { Text(label, style = typography().s.copy(color = palette.textSecondary)) },
            singleLine = true,
            enabled = enabled,
            isError = errorText != null,
            keyboardOptions = KeyboardOptions(keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Text),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = palette.text,
                unfocusedTextColor = palette.text,
                cursorColor = palette.accent,
                focusedBorderColor = palette.accent,
                unfocusedBorderColor = palette.textSecondary,
                errorBorderColor = palette.red,
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

/**
 * Name of a command in a notice (Compagnon only: the phone runs its commands itself and has no such text).
 * Next to [pairingErrorText], the other bridge texts decided from a contract `code`.
 */
suspend fun commandName(command: CommandKind?): String = getString(
    when (command) {
        CommandKind.Play -> Res.string.command_play
        CommandKind.Pause -> Res.string.command_pause
        CommandKind.Seek -> Res.string.command_seek
        CommandKind.Next -> Res.string.command_next
        CommandKind.Previous -> Res.string.command_previous
        CommandKind.Speed -> Res.string.command_speed
        CommandKind.Repeat -> Res.string.command_repeat
        CommandKind.Shuffle -> Res.string.command_shuffle
        CommandKind.Jump -> Res.string.command_jump
        CommandKind.Remove -> Res.string.command_remove
        CommandKind.Move -> Res.string.command_move
        CommandKind.Clear -> Res.string.command_clear
        CommandKind.QueuePlay -> Res.string.command_queue_play
        CommandKind.QueueAdd -> Res.string.command_queue_add
        CommandKind.Output -> Res.string.command_output
        CommandKind.QueueList -> Res.string.command_queue_play
        null -> Res.string.command_unknown
    },
)

/** Readable text of a [PlayerNotice], decided from its kind (itself from the contract `code`); shown as a toast. */
suspend fun noticeText(notice: PlayerNotice): String {
    val name = commandName(notice.command)
    return when (notice) {
        is PlayerNotice.NoDelta -> formatMessage(Res.string.notice_no_delta, name)
        is PlayerNotice.QueueMismatch -> formatMessage(Res.string.notice_queue_mismatch, name)
        is PlayerNotice.Rejected -> formatMessage(Res.string.notice_rejected, name)
        is PlayerNotice.Unavailable -> formatMessage(Res.string.notice_unavailable, name)
        is PlayerNotice.ServerStopping -> formatMessage(Res.string.notice_server_stopping, name)
        is PlayerNotice.OtherActive ->
            formatMessage(Res.string.notice_other_active, name, notice.deviceName ?: getString(Res.string.paired_other_active_unknown))
        is PlayerNotice.NotFound -> formatMessage(Res.string.notice_not_found, name)
        is PlayerNotice.Truncated -> formatMessage(Res.string.notice_truncated, name, notice.sent, notice.total)
        is PlayerNotice.Unreachable -> formatMessage(Res.string.notice_unreachable, name)
        is PlayerNotice.Failed ->
            formatMessage(Res.string.notice_failed, name, notice.status, notice.code ?: getString(Res.string.error_unknown_code))
        is PlayerNotice.LateError ->
            formatMessage(Res.string.notice_late_error, name, notice.code, notice.commandId?.take(8) ?: getString(Res.string.error_unknown_code))
        is PlayerNotice.PhoneToast -> phoneToastText(notice.key, notice.args, notice.message)
    }
}

/**
 * Contract limit (NOT LINKED): §7.9 carries no duration nor custom icon, so a relayed toast is always shown
 * short with its type's icon (the phone's `LENGTH_LONG` / custom-icon toasts lose those).
 *
 * Text of a relayed phone toast (contract §7.9, since 1.10.0): the PC's own string of the phone's
 * [key] (same resource names), formatted with [args] — numeric ones as numbers, the phone's `%d` —
 * else the [message] the phone showed (its language). A format that does not apply falls back to
 * [message] too.
 */
suspend fun phoneToastText(key: String, args: List<String>, message: String): String {
    val resource = Res.allStringResources[key] ?: return message
    val raw = getString(resource)
    val formatted = formatText(raw, *args.map<String, Any> { it.toLongOrNull() ?: it }.toTypedArray())
    return if (args.isNotEmpty() && formatted == raw) message.ifEmpty { raw } else formatted
}

/** The PC [Toaster.Type] of a relayed phone toast's `toastType`; an unknown one is `normal` (contract §7.9). */
fun phoneToastType(toastType: String): Toaster.Type = when (toastType) {
    "success" -> Toaster.Type.SUCCESS
    "info" -> Toaster.Type.INFO
    "warning" -> Toaster.Type.WARNING
    "error" -> Toaster.Type.ERROR
    else -> Toaster.Type.NORMAL
}
