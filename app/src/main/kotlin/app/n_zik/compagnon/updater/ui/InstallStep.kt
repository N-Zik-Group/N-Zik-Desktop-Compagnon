package app.n_zik.compagnon.updater.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.n_zik.compagnon.colorPalette
import app.n_zik.compagnon.generated.AppVersion
import app.n_zik.compagnon.generated.resources.*
import app.n_zik.compagnon.typography
import app.n_zik.compagnon.uiRoundnessShape
import app.n_zik.compagnon.updater.models.InstallMode
import app.n_zik.compagnon.updater.models.PackageManager
import app.n_zik.compagnon.utils.Toaster
import app.n_zik.compagnon.utils.semiBold
import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import org.jetbrains.compose.resources.stringResource

/**
 * The per-install-mode install gesture card (spec `spec-updater` AD-4, loop 2 — extracted from
 * the update dialog and shared with the update page, AD-9/AD-10): the saved file path + the
 * EXACT command of this install mode (never auto-run on the privileged package-managed modes —
 * the command is shown, the user runs it) + the copy / open-folder actions.
 *
 * @param packageManager the package manager of the package-managed install — `null` while the
 *   probe is still running (the command hint waits for it, the rest of the card shows).
 */
@Composable
fun InstallStep(filePath: String, installMode: InstallMode, packageManager: PackageManager?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = colorPalette().background1),
        shape = uiRoundnessShape(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Column {
            BasicText(
                text = stringResource(Res.string.install_file_saved_at),
                style = typography().xs.copy(color = colorPalette().textSecondary),
            )
            Spacer(modifier = Modifier.height(4.dp))
            BasicText(
                text = filePath,
                style = typography().xs.semiBold.copy(color = colorPalette().text),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(12.dp))
            when (installMode) {
                InstallMode.WINDOWS -> {
                    // The silent in-place install runs on the "Install now" click (the caller
                    // quits the app — nothing else to show here)
                }

                InstallMode.FLATPAK -> CommandHint(
                    stringResource(Res.string.install_flatpak_command),
                    "flatpak install \"$filePath\"",
                )

                InstallMode.PACKAGE_MANAGED -> when (packageManager) {
                    PackageManager.DEB -> CommandHint(
                        stringResource(Res.string.install_pkg_command),
                        "sudo apt install \"$filePath\"",
                    )

                    PackageManager.RPM -> CommandHint(
                        stringResource(Res.string.install_pkg_command),
                        "sudo dnf install \"$filePath\"",
                    )

                    // pacman builds from source: no binary asset — the AUR entry hint
                    PackageManager.AUR -> BasicText(
                        text = stringResource(Res.string.install_pkg_aur_hint),
                        style = typography().xs.copy(color = colorPalette().textSecondary),
                    )

                    // Probe failed (or not run yet): the manual command, no auto-run
                    PackageManager.NONE, null -> BasicText(
                        text = stringResource(Res.string.install_pkg_manual),
                        style = typography().xs.copy(color = colorPalette().textSecondary),
                    )
                }

                InstallMode.APPIMAGE -> {
                    BasicText(
                        text = stringResource(Res.string.install_replacement_appimage),
                        style = typography().xs.copy(color = colorPalette().textSecondary),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    BasicText(
                        text = stringResource(Res.string.old_install_path) + " ${oldInstallPath()}",
                        style = typography().xs.semiBold.copy(color = colorPalette().text),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                InstallMode.PORTABLE -> {
                    BasicText(
                        text = stringResource(Res.string.install_replacement_portable),
                        style = typography().xs.copy(color = colorPalette().textSecondary),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    BasicText(
                        text = stringResource(Res.string.old_install_path) + " ${oldInstallPath()}",
                        style = typography().xs.semiBold.copy(color = colorPalette().text),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                BasicText(
                    text = stringResource(Res.string.install_copy_path),
                    style = typography().xs.semiBold.copy(color = colorPalette().accent),
                    modifier = Modifier.clickable { copyToClipboard(filePath) },
                )
                Spacer(modifier = Modifier.width(12.dp))
                BasicText(
                    text = stringResource(Res.string.install_open_folder),
                    style = typography().xs.semiBold.copy(color = colorPalette().accent),
                    modifier = Modifier.clickable { openFileFolder(filePath) },
                )
            }
        }
    }
}

/** The "label + exact command" pair of the install gesture (the command is shown, never run). */
@Composable
fun CommandHint(label: String, command: String) {
    BasicText(
        text = label,
        style = typography().xs.copy(color = colorPalette().textSecondary),
    )
    Spacer(modifier = Modifier.height(4.dp))
    BasicText(
        text = command,
        style = typography().xs.semiBold.copy(color = colorPalette().text),
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

/** The current executable's path (the "old install" shown for the manual-replacement modes). */
fun oldInstallPath(): String =
    // `ProcessHandle.info().command()` is an Optional<String> — flattened through runCatching
    runCatching { ProcessHandle.current().info().command().orElse("") }.getOrDefault("")
        .ifEmpty { AppVersion.versionName }

/** Copies [text] to the clipboard (the desktop's "Copy" — a toast confirms it). */
fun copyToClipboard(text: String) {
    runCatching {
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
        Toaster.s(Res.string.path_copied)
    }.onFailure { Toaster.e(it.message ?: "Copy failed") }
}

/** Opens the folder containing [filePath] in the file manager. */
fun openFileFolder(filePath: String) {
    runCatching {
        Desktop.getDesktop().open(File(filePath).parentFile ?: File("."))
    }.onFailure { Toaster.e(it.message ?: "Could not open the folder") }
}
