package app.n_zik.compagnon.components.dialog.updater

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.n_zik.compagnon.colorPalette
import app.n_zik.compagnon.components.dialog.common.ButtonRow
import app.n_zik.compagnon.components.dialog.common.DialogButton
import app.n_zik.compagnon.components.dialog.common.DialogFrame
import app.n_zik.compagnon.generated.AppVersion
import app.n_zik.compagnon.generated.resources.*
import app.n_zik.compagnon.typography
import app.n_zik.compagnon.updater.services.Updater
import app.n_zik.compagnon.updater.ui.channelLabel
import app.n_zik.compagnon.utils.bold
import app.n_zik.compagnon.utils.formatText
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * The new-update dialog (port of the phone's `NewUpdateAvailableDialog` (phone's
 * `app/n_zik/android/components/dialog/updater/NewUpdateAvailableDialog.kt`), spec `spec-updater`
 * AD-6 — 1:1 since the phone-parity rework): the active / cancelled states as on the phone (the
 * cancel persists in `settings.json` through [Updater.persistUpdateCancelled]), the same header
 * (the phone's `update` icon + the flavor label + "Update available" + the version line + the
 * size line) and the same static button row — Cancel + **Go to download**. The button does NOT
 * download in the dialog: it dismisses the dialog and opens the update PAGE (the phone's
 * `onNavigateToUpdater`), where the download (progression) and the per-mode install gesture
 * happen (the UpdateScreen port, its changelog card included) — the phone's flow, unmodified.
 *
 * The "go to download" navigation is a one-shot request ([GoToDownloadRequest]): the dialog is
 * composed at the app root, above the navigation (the phone's `Skeleton` call site), so it cannot
 * reach the activity's page state directly — `MainActivity` consumes the flag and opens the
 * update page.
 *
 * The chrome renders through the shared [DialogFrame] (420 dp cap — the updater width) and the
 * shared [ButtonRow] (the phone's roles: Cancel = the red `danger` pill, the download action =
 * the accent `primary` pill), spec `spec-dialog-framework-port`.
 */
object NewUpdateAvailableDialog {

    /** The one-shot "go to download" request (the phone's `onNavigateToUpdater` callback): the dialog's button sets it, `MainActivity` consumes it (resets it and opens the update page). */
    object GoToDownloadRequest {
        var requested: Boolean by mutableStateOf(false)
    }

    /**
     * `true` when the dialog is cancelled — it keeps the dialog from showing even when
     * [isActive] is `true`, until the next check finds no update (which resets it). The initial
     * value is `false` on the updater-enabled channels, `true` on debug / -git (the phone's
     * `!IS_AUTOUPDATE`), so a source build never pops a dialog.
     */
    var isCancelled: Boolean by mutableStateOf(!AppVersion.updaterEnabled)

    var isActive: Boolean by mutableStateOf(false)

    fun onDismiss() {
        isCancelled = true
        isActive = false
        // Mark update as cancelled when the user cancels (persisted — the phone's
        // SharedPreferences write, here through settings.json)
        Updater.persistUpdateCancelled(true)
    }

    /** The dialog root (composed at the app root, the phone's `Skeleton` call site). */
    @Composable
    fun Render() {
        if (isCancelled || !isActive) return
        DialogFrame(onDismiss = ::onDismiss, maxWidth = 420.dp) {
            val colorPalette = colorPalette()
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth(),
            ) {
                // Header with title (the phone's `update` icon + title + version + size lines)
                Icon(
                    painter = painterResource(Res.drawable.update),
                    contentDescription = null,
                    tint = colorPalette.accent,
                    modifier = Modifier.size(32.dp),
                )
                Spacer(modifier = Modifier.height(8.dp))
                BasicText(
                    text = channelLabel() + stringResource(Res.string.update_available),
                    style = typography().l.bold.copy(color = colorPalette.text),
                )
                Spacer(modifier = Modifier.height(4.dp))
                // The phone's display version (its `getDisplayVersion()` — keeps
                // the tag's "v", appends the channel suffix when absent)
                BasicText(
                    text = formatText(
                        stringResource(Res.string.app_update_dialog_version),
                        Updater.getDisplayVersion(),
                    ),
                    style = typography().xs.copy(color = colorPalette.textSecondary),
                )
                // The phone always shows the size line (its L98-101) — the "?" fallback
                // when no asset is selected (the desktop's no-asset phase)
                BasicText(
                    text = formatText(
                        stringResource(Res.string.app_update_dialog_size),
                        Updater.build?.readableSize?.ifEmpty { "?" } ?: "?",
                    ),
                    style = typography().xs.copy(color = colorPalette.textSecondary),
                )

                // The phone's 16 dp header → actions gap (its L103)
                Spacer(modifier = Modifier.height(16.dp))

                // The phone's static button row: Cancel + Go to download — the button dismisses
                // the dialog and opens the update page (the phone's `onNavigateToUpdater`); the

                // download and the per-mode install gesture happen on the page, not in the dialog
                ButtonRow(
                    buttons = listOf(
                        DialogButton(
                            text = stringResource(Res.string.cancel),
                            icon = Res.drawable.close,
                            onClick = ::onDismiss,
                            danger = true,
                        ),
                        DialogButton(
                            text = stringResource(Res.string.go_to_download),
                            icon = Res.drawable.chevron_forward,
                            onClick = {
                                onDismiss()
                                GoToDownloadRequest.requested = true
                            },
                            primary = true,
                        ),
                    ),
                )
            }
        }
    }
}
