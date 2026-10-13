package app.n_zik.compagnon.components.dialog.updater

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import app.n_zik.compagnon.colorPalette
import app.n_zik.compagnon.components.theme.ModernBlackColorPalette
import app.n_zik.compagnon.components.theme.PureBlackColorPalette
import app.n_zik.compagnon.generated.AppVersion
import app.n_zik.compagnon.generated.resources.*
import app.n_zik.compagnon.typography
import app.n_zik.compagnon.uiRoundnessShape
import app.n_zik.compagnon.updater.models.InstallMode
import app.n_zik.compagnon.updater.models.PackageManager
import app.n_zik.compagnon.updater.models.currentInstallMode
import app.n_zik.compagnon.updater.models.livePackageManager
import app.n_zik.compagnon.updater.services.UpdateDownloadManager
import app.n_zik.compagnon.updater.services.Updater
import app.n_zik.compagnon.updater.ui.InstallStep
import app.n_zik.compagnon.updater.ui.copyToClipboard
import app.n_zik.compagnon.updater.ui.installCommand
import app.n_zik.compagnon.updater.ui.openFileFolder
import app.n_zik.compagnon.utils.Toaster
import app.n_zik.compagnon.utils.bold
import app.n_zik.compagnon.utils.coroutines.NzikDispatchers
import app.n_zik.compagnon.utils.formatShortFileSize
import app.n_zik.compagnon.utils.formatText
import app.n_zik.compagnon.utils.semiBold
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

        Dialog(onDismissRequest = onDismiss) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .widthIn(max = 420.dp)
                    .fillMaxWidth()
                    .padding(8.dp),
            ) {
                // Header with title
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(animationSpec = tween(300)) + scaleIn(animationSpec = tween(300), initialScale = 0.9f),
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (colorPalette() === PureBlackColorPalette || colorPalette() === ModernBlackColorPalette || app.n_zik.compagnon.bridge.state.LocalUiSettings.current.isPitchBlack) {
                                Color(0xFF1A1A1A) // Gray dark for pitch black themes
                            } else {
                                colorPalette().background1
                            },
                        ),
                        shape = uiRoundnessShape(),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                // The phone's `update` icon (its `arrow_up` is a desktop-only leftover)
                                Icon(
                                    painter = painterResource(Res.drawable.update),
                                    contentDescription = null,
                                    tint = colorPalette().accent,
                                    modifier = Modifier.size(32.dp),
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                BasicText(
                                    text = channelLabel() + stringResource(Res.string.update_available),
                                    style = typography().l.bold.copy(color = colorPalette().text),
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                // The phone's display version (its `getDisplayVersion()` — keeps
                                // the tag's "v", appends the channel suffix when absent)
                                BasicText(
                                    text = formatText(
                                        stringResource(Res.string.app_update_dialog_version),
                                        Updater.getDisplayVersion(),
                                    ),
                                    style = typography().xs.copy(color = colorPalette().textSecondary),
                                )
                                // The phone always shows the size line (L185-188) — the "?" fallback
                                // when no asset is selected (the desktop's no-asset phase)
                                BasicText(
                                    text = formatText(
                                        stringResource(Res.string.app_update_dialog_size),
                                        build?.readableSize?.ifEmpty { "?" } ?: "?",
                                    ),
                                    style = typography().xs.copy(color = colorPalette().textSecondary),
                                )
                            }
                        }
                    }
                }

                // The phone's 16 dp header → actions gap (its L194)
                Spacer(modifier = Modifier.height(16.dp))

                // The release's changelog (when fetched)
                if (!changelog.isNullOrBlank()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (colorPalette() === PureBlackColorPalette || colorPalette() === ModernBlackColorPalette || app.n_zik.compagnon.bridge.state.LocalUiSettings.current.isPitchBlack) {
                                Color(0xFF1A1A1A) // Gray dark for pitch black themes
                            } else {
                                colorPalette().background1
                            },
                        ),
                        shape = uiRoundnessShape(),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                    ) {
                        BasicText(
                            text = changelog,
                            style = typography().xs.copy(color = colorPalette().textSecondary),
                            modifier = Modifier
                                .verticalScroll(rememberScrollState())
                                .padding(16.dp),
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
                            // card, no download button (the v1 code silently offered a dead
                            // button). An AUR install (pacman, no release marker) never reaches
                            // this dialog — its updater is blocked by provenance before any
                            // check (spec `spec-arch-binary-package-release`); a marked
                            // release-pkg install gets its binary package asset instead.
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (colorPalette() === PureBlackColorPalette || colorPalette() === ModernBlackColorPalette || app.n_zik.compagnon.bridge.state.LocalUiSettings.current.isPitchBlack) {
                                        Color(0xFF1A1A1A) // Gray dark for pitch black themes
                                    } else {
                                        colorPalette().background1
                                    },
                                ),
                                shape = uiRoundnessShape(),
                                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                            ) {
                                BasicText(
                                    text = stringResource(Res.string.install_pkg_manual),
                                    style = typography().xs.copy(color = colorPalette().textSecondary),
                                    modifier = Modifier.padding(16.dp),
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            CancelButton(onClick = onDismiss)
                        } else {
                            ActionRow(
                                primaryLabel = stringResource(Res.string.download),
                                onPrimary = {
                                    UpdateDownloadManager.startDownload(
                                        downloadUrl = build.downloadUrl,
                                        version = release?.tagName?.removePrefix("v") ?: AppVersion.versionName,
                                        assetName = build.name,
                                    )
                                },
                                onCancel = onDismiss,
                            )
                        }
                    }

                    is UpdateDownloadManager.DownloadState.Downloading -> {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = if (colorPalette() === PureBlackColorPalette || colorPalette() === ModernBlackColorPalette || app.n_zik.compagnon.bridge.state.LocalUiSettings.current.isPitchBlack) {
                                    Color(0xFF1A1A1A) // Gray dark for pitch black themes
                                } else {
                                    colorPalette().background1
                                },
                            ),
                            shape = uiRoundnessShape(),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                BasicText(
                                    text = formatText(
                                        stringResource(Res.string.update_download_progress),
                                        (state.progress * 100).toInt(),
                                    ),
                                    style = typography().xs.copy(color = colorPalette().textSecondary),
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                LinearProgressIndicator(
                                    progress = { state.progress },
                                    modifier = Modifier.fillMaxWidth().height(6.dp),
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        ActionRow(
                            primaryLabel = stringResource(Res.string.cancel),
                            onPrimary = {
                                UpdateDownloadManager.cancelDownload()
                                onDismiss()
                            },
                            onCancel = onDismiss,
                        )
                    }

                    is UpdateDownloadManager.DownloadState.DownloadingIndeterminate -> {
                        // Loop 2: no Content-Length header — the byte counter instead of the bar
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = if (colorPalette() === PureBlackColorPalette || colorPalette() === ModernBlackColorPalette || app.n_zik.compagnon.bridge.state.LocalUiSettings.current.isPitchBlack) {
                                    Color(0xFF1A1A1A) // Gray dark for pitch black themes
                                } else {
                                    colorPalette().background1
                                },
                            ),
                            shape = uiRoundnessShape(),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                        ) {
                            BasicText(
                                text = formatText(
                                    stringResource(Res.string.update_download_indeterminate),
                                    formatShortFileSize(state.bytesRead),
                                ),
                                style = typography().xs.copy(color = colorPalette().textSecondary),
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        ActionRow(
                            primaryLabel = stringResource(Res.string.cancel),
                            onPrimary = {
                                UpdateDownloadManager.cancelDownload()
                                onDismiss()
                            },
                            onCancel = onDismiss,
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
                        ActionRow(
                            primaryLabel = when {
                                installMode == InstallMode.WINDOWS -> stringResource(Res.string.install_now)
                                command != null -> stringResource(Res.string.install_copy_command)
                                else -> stringResource(Res.string.install_open_folder)
                            },
                            onPrimary = {
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
                            onCancel = {
                                UpdateDownloadManager.resetState()
                                onDismiss()
                            },
                        )
                    }

                    is UpdateDownloadManager.DownloadState.Failed -> {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = if (colorPalette() === PureBlackColorPalette || colorPalette() === ModernBlackColorPalette || app.n_zik.compagnon.bridge.state.LocalUiSettings.current.isPitchBlack) {
                                    Color(0xFF1A1A1A) // Gray dark for pitch black themes
                                } else {
                                    colorPalette().background1
                                },
                            ),
                            shape = uiRoundnessShape(),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                        ) {
                            BasicText(
                                text = state.error,
                                style = typography().xs.copy(color = colorPalette().red),
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        ActionRow(
                            primaryLabel = stringResource(Res.string.download),
                            onPrimary = {
                                build?.let {
                                    UpdateDownloadManager.startDownload(
                                        downloadUrl = it.downloadUrl,
                                        version = release?.tagName?.removePrefix("v") ?: AppVersion.versionName,
                                        assetName = it.name,
                                    )
                                }
                            },
                            onCancel = onDismiss,
                        )
                    }
                }
            }
        }
    }

    /** The "BETA Update available" title prefix: the uppercase channel label + a space —
     *  stable is labeled too (the phone's strings are uppercase in the update flow — its
     *  "BETA Update available"). The desktop's `*_title` strings are uppercase in
     *  `values/strings.xml`, but the `values-*` locale copies stay mixed-case until the next
     *  Crowdin resync, so the case is applied at render (plain rendering/normalization, not a
     *  safeguard — round-4 audit MINOR-1). */
    @Composable
    private fun channelLabel(): String = when (AppVersion.channel) {
        "beta" -> stringResource(Res.string.beta_title).uppercase() + " "
        "stable" -> stringResource(Res.string.stable_title).uppercase() + " "
        "dev" -> stringResource(Res.string.dev_title).uppercase() + " "
        "git" -> stringResource(Res.string.git_title).uppercase() + " "
        else -> ""
    }

    /** A single full-width cancel button (the no-download / no-asset phases). */
    @Composable
    private fun CancelButton(onClick: () -> Unit) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(uiRoundnessShape())
                .clickable { onClick() },
            colors = CardDefaults.cardColors(
                containerColor = if (colorPalette() === PureBlackColorPalette || colorPalette() === ModernBlackColorPalette || app.n_zik.compagnon.bridge.state.LocalUiSettings.current.isPitchBlack) {
                    Color(0xFF1A1A1A) // Gray dark for pitch black themes
                } else {
                    colorPalette().background1
                },
            ),
            shape = uiRoundnessShape(),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(
                    text = stringResource(Res.string.cancel),
                    style = typography().xs.semiBold.copy(color = colorPalette().textSecondary),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }

    /** The Cancel / primary action pair of the phone's dialog (the accent card + the flat card). */
    @Composable
    private fun ActionRow(primaryLabel: String, onPrimary: () -> Unit, onCancel: () -> Unit) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Cancel button
            Card(
                modifier = Modifier
                    .weight(1f)
                    .clip(uiRoundnessShape())
                    .clickable { onCancel() },
                colors = CardDefaults.cardColors(
                    containerColor = if (colorPalette() === PureBlackColorPalette || colorPalette() === ModernBlackColorPalette || app.n_zik.compagnon.bridge.state.LocalUiSettings.current.isPitchBlack) {
                        Color(0xFF1A1A1A) // Gray dark for pitch black themes
                    } else {
                        colorPalette().background1
                    },
                ),
                shape = uiRoundnessShape(),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            ) {
                // The phone's cancel card (its `NewUpdateAvailableDialog.kt` 223-244): the close
                // icon + the label, 16 dp vertical padding
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(Res.drawable.close),
                            contentDescription = null,
                            tint = colorPalette().textSecondary,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        BasicText(
                            text = stringResource(Res.string.cancel),
                            style = typography().xs.semiBold.copy(color = colorPalette().textSecondary),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            // Primary button (the phone's card, its 261-282): the label + the chevron icon
            Card(
                modifier = Modifier
                    .weight(1f)
                    .clip(uiRoundnessShape())
                    .clickable { onPrimary() },
                colors = CardDefaults.cardColors(containerColor = colorPalette().accent),
                shape = uiRoundnessShape(),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BasicText(
                            text = primaryLabel,
                            style = typography().xs.semiBold.copy(color = Color.White),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            painter = painterResource(Res.drawable.chevron_forward),
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }
    }

}
