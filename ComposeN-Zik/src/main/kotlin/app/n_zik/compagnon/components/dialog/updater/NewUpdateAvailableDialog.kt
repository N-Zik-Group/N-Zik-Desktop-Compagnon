package app.n_zik.compagnon.components.dialog.updater

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
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
import app.n_zik.compagnon.updater.models.InstallMode
import app.n_zik.compagnon.updater.models.PackageManager
import app.n_zik.compagnon.updater.models.currentInstallMode
import app.n_zik.compagnon.updater.models.livePackageManager
import app.n_zik.compagnon.updater.services.UpdateDownloadManager
import app.n_zik.compagnon.updater.services.Updater
import app.n_zik.compagnon.updater.ui.InstallStep
import app.n_zik.compagnon.updater.ui.channelLabel
import app.n_zik.compagnon.updater.ui.copyToClipboard
import app.n_zik.compagnon.updater.ui.installCommand
import app.n_zik.compagnon.updater.ui.openFileFolder
import app.n_zik.compagnon.utils.Toaster
import app.n_zik.compagnon.utils.bold
import app.n_zik.compagnon.utils.coroutines.NzikDispatchers
import app.n_zik.compagnon.utils.formatShortFileSize
import app.n_zik.compagnon.utils.formatText
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * The new-update dialog (port of the phone's `NewUpdateAvailableDialog` (phone's
 * `app/n_zik/android/components/dialog/updater/NewUpdateAvailableDialog.kt`), spec `spec-updater`
 * AD-6): the active / cancelled states as on the phone (the cancel persists in `settings.json`
 * through [Updater.persistUpdateCancelled]), minus the phone's separate `UpdateScreen` — on the
 * desktop the download (progression) and the per-mode install gesture happen IN the dialog.
 * The install gesture is native per detected install mode (AD-4): Windows = interactive in-place
 * (quit → the standard WiX UI shows: repair for the same version, an upgrade for a newer one —
 * the user sees progress and can cancel — the self-extracting exe then relaunches the app on a
 * 0 / 3010 exit), Flatpak = `flatpak install <file>`, package-managed = the exact command shown,
 * never auto-run (privilege), portable = manual replacement.
 *
 * The chrome renders through the shared [DialogFrame] (420 dp cap — the updater width) and the
 * shared [ButtonRow] (the phone's roles: Cancel = the red `danger` pill, the download / install /
 * copy / open-folder action = the accent `primary` pill), spec `spec-dialog-framework-port`.
 *
 * Frame-migration deltas (spec `spec-dialog-framework-port`): the phone's per-state nested cards
 * are removed (the content renders directly in the shared frame — [InstallStep] keeps its own
 * content card, a shared component, pre-existing) and the metrics moved to the shared pill
 * language (pill spacing 12→8 dp, pill padding 16/10 dp, the updater icons 24→16 dp, the 420 dp
 * cap now includes the frame's 16 dp card padding).
 */
object NewUpdateAvailableDialog {

    /**
     * `true` when the dialog is cancelled — it keeps the dialog from showing even when
     * [isActive] is `true`, until the next check finds no update (which resets it). The initial
     * value is `false` on the updater-enabled channels, `true` on debug / -git (the phone's
     * `!IS_AUTOUPDATE`), so a source build never pops a dialog.
     */
    var isCancelled: Boolean by mutableStateOf(!AppVersion.updaterEnabled)

    var isActive: Boolean by mutableStateOf(false)

    fun onDismiss() {
        // A download in flight dies with the dialog (the file is cleaned up by the manager).
        when (UpdateDownloadManager.downloadState.value) {
            is UpdateDownloadManager.DownloadState.Starting,
            is UpdateDownloadManager.DownloadState.Downloading ->
                UpdateDownloadManager.cancelDownload()
            // Idle / Completed / Failed: nothing in flight (a completed file stays for the retry)
            else -> {}
        }
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
        RenderDialog(onDismiss = ::onDismiss)
    }

    @Composable
    private fun RenderDialog(onDismiss: () -> Unit) {
        val download by UpdateDownloadManager.downloadState.collectAsState()
        val release = Updater.githubRelease
        val build = Updater.build
        // Snapshot read: the changelog is set by the check before the download starts, and each
        // phase change (the [download] flow) re-composes this dialog
        val changelog = Updater.latestChangelog
        // The install gesture of this instance (detected once — the mode does not change at runtime)
        val installMode = remember { currentInstallMode() }
        // Loop 2: the package-manager probe is a blocking `--version` process scan — it runs off
        // the composition thread, and [PackageManager] stays `null` until it resolves (the
        // shared [InstallStep] waits on it before showing the command hint).
        var packageManager by remember { mutableStateOf<PackageManager?>(null) }
        LaunchedEffect(installMode) {
            if (installMode == InstallMode.PACKAGE_MANAGED) {
                packageManager = withContext(NzikDispatchers.DATA) { livePackageManager() }
            }
        }

        DialogFrame(onDismiss = onDismiss, maxWidth = 420.dp) {
            val colorPalette = colorPalette()

            // Header with title (the phone's `update` icon + title + version + size lines)
            AnimatedVisibility(
                visible = true,
                enter = fadeIn(animationSpec = tween(300)) + scaleIn(animationSpec = tween(300), initialScale = 0.9f),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
                    // The phone always shows the size line (L185-188) — the "?" fallback
                    // when no asset is selected (the desktop's no-asset phase)
                    BasicText(
                        text = formatText(
                            stringResource(Res.string.app_update_dialog_size),
                            build?.readableSize?.ifEmpty { "?" } ?: "?",
                        ),
                        style = typography().xs.copy(color = colorPalette.textSecondary),
                    )
                }
            }

            // The phone's 16 dp header → actions gap (its L194)
            Spacer(modifier = Modifier.height(16.dp))

            // The release's changelog (when fetched): the frame body is not scrollable, so a tall
            // changelog scrolls in its own column bounded to the frame's remaining space (the
            // [DefaultDialog] idiom) — the action pills below always stay visible and tappable
            if (!changelog.isNullOrBlank()) {
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                ) {
                    BasicText(
                        text = changelog,
                        style = typography().xs.copy(color = colorPalette.textSecondary),
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Action phase: download / progression / install / error
            when (val state = download) {
                is UpdateDownloadManager.DownloadState.Idle,
                is UpdateDownloadManager.DownloadState.Starting -> {
                    if (build == null) {
                        // No binary asset for this install mode (the probe failed, or the
                        // release simply has no asset for this mode): the manual-commands
                        // hint, no download button (the v1 code silently offered a dead
                        // button). An AUR install (pacman, no release marker) never reaches
                        // this dialog — its updater is blocked by provenance before any
                        // check (spec `spec-arch-binary-package-release`); a marked
                        // release-pkg install gets its binary package asset instead.
                        BasicText(
                            text = stringResource(Res.string.install_pkg_manual),
                            style = typography().xs.copy(color = colorPalette.textSecondary),
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        ButtonRow(
                            buttons = listOf(
                                DialogButton(
                                    text = stringResource(Res.string.cancel),
                                    icon = Res.drawable.close,
                                    onClick = onDismiss,
                                    danger = true,
                                ),
                            ),
                        )
                    } else {
                        ButtonRow(
                            buttons = listOf(
                                DialogButton(
                                    text = stringResource(Res.string.cancel),
                                    icon = Res.drawable.close,
                                    onClick = onDismiss,
                                    danger = true,
                                ),
                                DialogButton(
                                    text = stringResource(Res.string.download),
                                    icon = Res.drawable.chevron_forward,
                                    onClick = {
                                        UpdateDownloadManager.startDownload(
                                            downloadUrl = build.downloadUrl,
                                            version = release?.tagName?.removePrefix("v") ?: AppVersion.versionName,
                                            assetName = build.name,
                                        )
                                    },
                                    primary = true,
                                ),
                            ),
                        )
                    }
                }

                is UpdateDownloadManager.DownloadState.Downloading -> {
                    BasicText(
                        text = formatText(
                            stringResource(Res.string.update_download_progress),
                            (state.progress * 100).toInt(),
                        ),
                        style = typography().xs.copy(color = colorPalette.textSecondary),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { state.progress },
                        modifier = Modifier.fillMaxWidth().height(6.dp),
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    ButtonRow(
                        buttons = listOf(
                            DialogButton(
                                text = stringResource(Res.string.cancel),
                                icon = Res.drawable.close,
                                onClick = onDismiss,
                                danger = true,
                            ),
                            DialogButton(
                                text = stringResource(Res.string.cancel),
                                icon = Res.drawable.chevron_forward,
                                onClick = {
                                    UpdateDownloadManager.cancelDownload()
                                    onDismiss()
                                },
                                primary = true,
                            ),
                        ),
                    )
                }

                is UpdateDownloadManager.DownloadState.DownloadingIndeterminate -> {
                    // Loop 2: no Content-Length header — the byte counter instead of the bar
                    BasicText(
                        text = formatText(
                            stringResource(Res.string.update_download_indeterminate),
                            formatShortFileSize(state.bytesRead),
                        ),
                        style = typography().xs.copy(color = colorPalette.textSecondary),
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    ButtonRow(
                        buttons = listOf(
                            DialogButton(
                                text = stringResource(Res.string.cancel),
                                icon = Res.drawable.close,
                                onClick = onDismiss,
                                danger = true,
                            ),
                            DialogButton(
                                text = stringResource(Res.string.cancel),
                                icon = Res.drawable.chevron_forward,
                                onClick = {
                                    UpdateDownloadManager.cancelDownload()
                                    onDismiss()
                                },
                                primary = true,
                            ),
                        ),
                    )
                }

                is UpdateDownloadManager.DownloadState.Completed -> {
                    // Hoisted into composable scope: the click lambda below is neither suspend nor
                    // composable, so the non-composable `getString` cannot be called inside it
                    val windowsHelperError = stringResource(Res.string.error_windows_install_helper)
                    // The exact command of this mode (`null` = the manual modes — the primary
                    // action keeps the "Open folder" fallback instead of a misleading
                    // "Install")
                    val command = installCommand(installMode, packageManager, state.filePath)
                    InstallStep(
                        filePath = state.filePath,
                        installMode = installMode,
                        packageManager = packageManager,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    ButtonRow(
                        buttons = listOf(
                            DialogButton(
                                text = stringResource(Res.string.cancel),
                                icon = Res.drawable.close,
                                onClick = {
                                    UpdateDownloadManager.resetState()
                                    onDismiss()
                                },
                                danger = true,
                            ),
                            DialogButton(
                                text = when {
                                    installMode == InstallMode.WINDOWS -> stringResource(Res.string.install_now)
                                    command != null -> stringResource(Res.string.install_copy_command)
                                    else -> stringResource(Res.string.install_open_folder)
                                },
                                icon = Res.drawable.chevron_forward,
                                onClick = {
                                    when {
                                        installMode == InstallMode.WINDOWS -> {
                                            if (UpdateDownloadManager.startWindowsInstall(state.filePath)) {
                                                // Quit so the installer can replace the running files. `exitApplication()`
                                                // is only reachable from the `application {}` scope in Main.kt, so a JVM
                                                // exit is the way to quit from here (safe: dispatchers are daemon threads,
                                                // no shutdown hooks, and the install helper is a detached process).
                                                System.exit(0)
                                            } else {
                                                Toaster.e(windowsHelperError)
                                            }
                                        }
                                        // The command-managed modes (flatpak / apt / dnf / pacman): copy
                                        // the exact command — the user runs it in their terminal
                                        // (never auto-run, privilege)
                                        command != null -> copyToClipboard(command, Res.string.command_copied)
                                        else -> openFileFolder(state.filePath)
                                    }
                                },
                                primary = true,
                            ),
                        ),
                    )
                }

                is UpdateDownloadManager.DownloadState.Failed -> {
                    BasicText(
                        text = state.error,
                        style = typography().xs.copy(color = colorPalette.red),
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    ButtonRow(
                        buttons = listOf(
                            DialogButton(
                                text = stringResource(Res.string.cancel),
                                icon = Res.drawable.close,
                                onClick = onDismiss,
                                danger = true,
                            ),
                            DialogButton(
                                text = stringResource(Res.string.download),
                                icon = Res.drawable.chevron_forward,
                                onClick = {
                                    build?.let {
                                        UpdateDownloadManager.startDownload(
                                            downloadUrl = it.downloadUrl,
                                            version = release?.tagName?.removePrefix("v") ?: AppVersion.versionName,
                                            assetName = it.name,
                                        )
                                    }
                                },
                                primary = true,
                            ),
                        ),
                    )
                }
            }
        }
    }
}
