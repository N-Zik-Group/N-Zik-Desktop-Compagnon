package app.n_zik.compagnon.updater.services

import app.n_zik.compagnon.bridge.pairing.CredentialStore
import app.n_zik.compagnon.generated.resources.*
import app.n_zik.compagnon.utils.coroutines.NzikDispatchers
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.utils.io.readAvailable
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.util.logging.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString

/**
 * Manages the update-artifact download (port of the phone's `UpdateDownloadManager`
 * (phone's `app/n_zik/android/updater/services/UpdateDownloadManager.kt`)): the streaming download
 * with the same `DownloadState` [StateFlow] progression, Ktor CIO instead of the OkHttp call.
 *
 * Deliberate deviations (desktop): no Android notification (a desktop window has no system
 * notification — the update dialog shows the progress); the file is saved under the app's data
 * directory (`updates/`, next to `settings.json`) instead of the external Downloads dir; and the
 * file is KEPT on failure (spec matrix INSTALL_WINDOWS: "l'install échoue → dialog d'erreur, le
 * fichier est gardé").
 *
 * Loop-2 hardening (spec `spec-updater`, Review Triage): every terminal state write is guarded
 * by a per-download token (a stale job can never overwrite the state of a newer download); a
 * body shorter than its Content-Length is a `Failed` truncation, never a `Completed` half-file
 * (the Ktor CIO client rejects a short body while RECEIVING the response — the raw engine
 * message is mapped to the localized truncation string; note the body never reaches the disk on
 * that path, so there is no partial file to keep);
 * no Content-Length → the indeterminate byte counter; the asset name is validated before any
 * path is built; the cancel cleanup touches only the cancelled download's own file; and the
 * Windows install helper (the interactive NSIS installer — a scope-choice page, and a UAC prompt
 * only when the user picks the global scope; the user decisions replaced the silent design, the
 * per-user design and the machine-level-only jpackage exe) inspects the exit code (0 → relaunch
 * when the app is not already running, 1 / 1223 = a normal cancel or UAC decline with nothing
 * written, failure marker for the other codes).
 */
object UpdateDownloadManager {

    sealed class DownloadState {
        data object Idle : DownloadState()
        data object Starting : DownloadState()
        data class Downloading(val progress: Float) : DownloadState()

        /** No `Content-Length` header: indeterminate progress — the byte counter instead of a bar. */
        data class DownloadingIndeterminate(val bytesRead: Long) : DownloadState()

        data class Completed(val filePath: String) : DownloadState()
        data class Failed(val error: String) : DownloadState()
    }

    private val log = Logger.getLogger("UpdateDownloadManager")

    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()

    var downloadingVersion: String? = null
        private set

    /** The asset name of the running download (its own file — the cancel cleanup touches only this). */
    private var downloadingAsset: String? = null

    private var downloadJob: Job? = null

    /**
     * One token per [startDownload]: every terminal state write (Completed / Failed / the cancel
     * reset) happens only while the token still matches — a superseded or cancelled job never
     * overwrites the state of the download that replaced it.
     */
    private var downloadToken: Any? = null

    /** Injectable download directory for the JVM tests (`@TempDir`); `null` in production. */
    private var testDirectory: Path? = null

    /** Injectable failure-marker directory for the JVM tests (`@TempDir`); `null` in production. */
    private var markerTestDirectory: Path? = null

    /**
     * The HTTP client factory (the house test seam — [Updater.fetchUpdate] takes its [HttpClient]
     * the same way): the tests replace it with a Ktor `MockEngine`, the production default is the
     * updater's CIO client ([updaterHttpClient]).
     */
    internal var httpClientFactory: () -> HttpClient = ::updaterHttpClient

    /** Sets the test download directory (JVM tests only — `null` restores the production default). */
    fun setTestDirectory(dir: Path?) {
        testDirectory = dir
    }

    /** Sets the test marker directory (JVM tests only — `null` restores the production default). */
    fun setTestMarkerDirectory(dir: Path?) {
        markerTestDirectory = dir
    }

    /** The download directory: `updates/` in the app's data directory (next to `settings.json`). */
    fun downloadDirectory(): Path = testDirectory ?: CredentialStore.appDirectory().resolve("updates")

    /**
     * A safe asset file name (loop 2): the name comes from the release asset and is joined into a
     * local path — it must not escape the download directory (no separators, no `..`). Pure, so
     * it is pinned by the download tests.
     */
    internal fun isSafeAssetName(name: String): Boolean =
        name.isNotEmpty() && !name.contains('/') && !name.contains('\\') && !name.contains("..")

    /**
     * Starts downloading the update artifact (port of the phone's `startDownload` — the OkHttp
     * call replaced by Ktor streaming; the progress is the same [DownloadState] flow). The
     * [assetName] is the release asset's file name (validated by [isSafeAssetName] before any
     * path is built — an unsafe name fails the download without touching the disk).
     */
    fun startDownload(downloadUrl: String, version: String, assetName: String) {
        if (!isSafeAssetName(assetName)) {
            log.severe("Refusing an unsafe asset name: $assetName")
            // Cancel the in-flight download FIRST: the token set below would orphan its job —
            // it would keep writing bytes to disk under a token that can no longer write the
            // state (the happy path below already cancels, after the validation)
            cancelDownload()
            // `getString` is suspend — the failure state is emitted from a coroutine (the token
            // guard keeps a superseding startDownload / cancelDownload from being overridden by
            // this late write)
            val token = Any()
            downloadToken = token
            downloadJob = NzikDispatchers.fireAndForget(NzikDispatchers.DATA).launch {
                if (downloadToken === token) {
                    _downloadState.value = DownloadState.Failed(
                        getString(Res.string.error_update_download_failed, "invalid file name"),
                    )
                }
            }
            return
        }

        // Cancel any existing download (its job dies with its token — the token guard below
        // stops it from touching the state of this one).
        cancelDownload()

        val token = Any()
        downloadToken = token
        downloadingVersion = version
        downloadingAsset = assetName
        _downloadState.value = DownloadState.Starting

        downloadJob = NzikDispatchers.fireAndForget(NzikDispatchers.DATA).launch {
            var outputStream: OutputStream? = null
            var client: HttpClient? = null
            var cancelled = false
            try {
                client = httpClientFactory()
                val response = client.get(downloadUrl)

                if (response.status.value !in 200..299) {
                    if (downloadToken === token) {
                        _downloadState.value = DownloadState.Failed(
                            getString(Res.string.error_update_download_failed, "HTTP ${response.status.value}"),
                        )
                    }
                    return@launch
                }

                val channel = response.bodyAsChannel()
                val contentLength = response.headers[HttpHeaders.ContentLength]?.toLongOrNull() ?: -1L

                // Create the download directory
                val dir = downloadDirectory()
                Files.createDirectories(dir)
                val outputFile = File(dir.toFile(), assetName)
                outputStream = FileOutputStream(outputFile)

                // A heap `ByteBuffer` so `array()` gives a direct view of the read bytes
                val buffer = ByteBuffer.allocate(8192)
                var totalBytesRead = 0L

                if (downloadToken === token) {
                    _downloadState.value = DownloadState.Downloading(0f)
                }

                while (true) {
                    buffer.clear()
                    // Suspends until bytes are available; returns -1 when the channel is closed
                    val read = channel.readAvailable(buffer)
                    if (read == -1) break
                    // Non-null here: assigned just above and only reset to null after the loop
                    outputStream.write(buffer.array(), 0, read)
                    totalBytesRead += read
                    if (downloadToken === token) {
                        if (contentLength > 0) {
                            val progress = (totalBytesRead.toDouble() / contentLength).toFloat().coerceIn(0f, 1f)
                            _downloadState.value = DownloadState.Downloading(progress)
                        } else {
                            // Loop 2: no Content-Length — an indeterminate byte counter
                            // (GitHub's `application/gzip` redirect can drop the header).
                            _downloadState.value = DownloadState.DownloadingIndeterminate(totalBytesRead)
                        }
                    }
                    // Check if the coroutine was cancelled between reads (the phone's `ensureActive`)
                    ensureActive()
                }

                outputStream.flush()
                outputStream.close()
                outputStream = null

                // Loop 2: a body that ended before its Content-Length is a truncated download —
                // the file is kept (the download can be re-run) but the state is a failure, never
                // a "completed" half-file. (Safety net: the Ktor CIO client validates the body
                // against its Content-Length while RECEIVING the response and throws on a short
                // body before any byte reaches the disk — that path is mapped in the Exception
                // handler below; a streaming engine without that validation would reach this check.)
                if (contentLength > 0 && totalBytesRead != contentLength) {
                    log.warning("Update download truncated: $totalBytesRead of $contentLength bytes ($version)")
                    if (downloadToken === token) {
                        _downloadState.value = DownloadState.Failed(
                            getString(Res.string.update_download_truncated),
                        )
                    }
                    return@launch
                }

                if (downloadToken === token) {
                    _downloadState.value = DownloadState.Completed(outputFile.absolutePath)
                }
            } catch (e: CancellationException) {
                // Download was cancelled — the state resets here (the phone's cancellation
                // branch); the token guard keeps a stale cancel from touching the state of a
                // download that already replaced this one. The FILE cleanup is in the finally,
                // after the output stream is closed (a file cannot be deleted while its handle
                // is open — Windows).
                cancelled = true
                if (downloadToken === token) {
                    log.info("Update download cancelled ($version)")
                    downloadingVersion = null
                    _downloadState.value = DownloadState.Idle
                }
            } catch (e: Exception) {
                // The file is KEPT on failure (the matrix: the error dialog shows, the file stays —
                // the download can be re-run without re-fetching from scratch). With the Ktor CIO
                // client a truncated body never reaches this branch as a stream end: the engine
                // validates the body against its Content-Length while receiving the response and
                // throws — mapped to the localized truncation string instead of the raw engine
                // message (the bytes never reached the disk, so there is no partial file to keep).
                log.warning("Update download failed: ${e::class.simpleName} (${e.message})")
                val error = if (e.message?.contains("Content-Length mismatch") == true) {
                    getString(Res.string.update_download_truncated)
                } else {
                    e.message ?: getString(Res.string.error_update_download_failed, "unknown")
                }
                if (downloadToken === token) {
                    _downloadState.value = DownloadState.Failed(error)
                }
            } finally {
                // Close this job's stream FIRST: on Windows a file cannot be deleted while its
                // handle is open, so the cancel cleanup below (and the synchronous backstop in
                // cancelDownload) needs the handle released.
                runCatching { outputStream?.close() }
                if (cancelled && downloadToken === token) {
                    // Cancel cleanup, job-side: delete ONLY this download's own file (validated
                    // at start — no path escape). The token guard keeps a superseded job from
                    // deleting the file of the download that replaced it (a new startDownload
                    // set a new token).
                    runCatching { File(downloadDirectory().toFile(), assetName).delete() }
                        .onFailure { log.warning("Could not delete the cancelled download $assetName: ${it.message}") }
                }
                client?.close()
            }
        }
    }

    /**
     * Cancels an ongoing download and resets the state (port of the phone's `cancelDownload`).
     * The token is deliberately NOT invalidated here: the cancelled job's cleanup (guarded by
     * the token) must still be able to close its stream and delete its own file — only a newer
     * [startDownload] replaces the token, and that is what invalidates the stale job.
     */
    fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
        val asset = downloadingAsset
        downloadingAsset = null
        downloadingVersion = null
        if (asset != null) {
            // Synchronous backstop: it works when the job has already finished (its handle is
            // closed). While the job is still running, its own handle is open (Windows: the
            // delete fails silently) and the job-side cleanup deletes the file after closing
            // the stream.
            runCatching { File(downloadDirectory().toFile(), asset).delete() }
                .onFailure { log.warning("Could not delete the cancelled download $asset: ${it.message}") }
        }
        _downloadState.value = DownloadState.Idle
    }

    /** Resets the state back to Idle without cancelling (port of the phone's `resetState`). */
    fun resetState() {
        downloadingVersion = null
        // Nothing is downloading anymore — a later cancel must not clean up this file
        downloadingAsset = null
        _downloadState.value = DownloadState.Idle
    }

    /**
     * Cancels any active download and purges the whole download cache (`updates/`) — the update
     * page's cache gesture: the next download re-fetches from scratch.
     */
    fun clearUpdateCache() {
        cancelDownload()
        val dir = downloadDirectory()
        if (Files.exists(dir)) {
            dir.toFile().walkBottomUp().forEach { runCatching { it.delete() } }
            log.info("Update cache cleared (${dir})")
        }
    }

    // ---- Windows install (spec `spec-updater`, AD-4 — interactive + machine-level with UAC — the user decisions) ----

    /** The failure marker written by the install helper when the install failed (loop 2). */
    const val INSTALL_FAILURE_MARKER = "update-install-failed.txt"

    /** The marker's path: the app's data directory (next to `settings.json`) — the JVM tests stand
     * [markerTestDirectory] in for the data directory (the [setTestDirectory] idiom). */
    fun installFailureMarkerPath(): Path =
        (markerTestDirectory ?: CredentialStore.appDirectory()).resolve(INSTALL_FAILURE_MARKER)

    /**
     * Reads and DELETES the install-failure marker (loop 2) — called once at startup: if the
     * previous session's install failed, the app toasts the error instead of relaunching
     * a half-installed build forever. `null` when there is no marker.
     */
    fun consumeInstallFailureMarker(): String? {
        val marker = installFailureMarkerPath()
        if (!Files.exists(marker)) return null
        val content = runCatching { Files.readString(marker) }.getOrDefault("")
        runCatching { Files.delete(marker) }
            .onFailure { log.warning("Could not delete the install failure marker: ${it.message}") }
        return content
    }

    /**
     * The helper's wait for the app to exit: a 1-s poll, capped at this many seconds — past the
     * cap the app's files are still locked, so the install is aborted with the failure marker
     * (launching the installer over locked files would leave the user stuck on a repair/upgrade
     * that cannot run; a previous install step still holding the app's file locks is exactly
     * what the wait protects against).
     */
    const val APP_EXIT_WAIT_S = 30

    /**
     * The PowerShell one-liner of [startWindowsInstall] — PURE, so it is pinned by
     * `UpdateDownloadManagerTest`. The install is **INTERACTIVE** (the user decisions — the
     * silent `/quiet /wait` design, the per-user design AND the machine-level-only jpackage exe
     * are superseded by the NSIS installer; the Change Log of `spec-updater.md` is the record):
     * the NSIS pages show (a scope-choice page — per-user by default, or global with a UAC
     * prompt that the installer itself triggers by re-launching elevated), so a hang is VISIBLE
     * to the user (an upgrade for a newer version, "already installed — reinstall anyway?" for a
     * same/older one, progress shown, cancellable) — there is therefore NO timeout and NO
     * auto-kill in the helper (killing a dialog the user is looking at would be wrong; the
     * phone's UX is the same — its system install dialog is always visible).
     * The sequence:
     * 1. **WAIT FOR THE APP TO EXIT** (the caller quits right after starting the helper): the
     *    app process is polled by its EXACT path, 1-s tick, capped at [APP_EXIT_WAIT_S] — if it
     *    is still alive at the cap, the marker is written and the helper exits. This loop is
     *    the DETERMINISTIC path that closes the running instance and releases the locked files
     *    before the installer launches — the NSIS installer has no auto-close behavior for a
     *    running app, and it is deliberately NOT relied on (it replaces the whole install
     *    directory — locked files would break the upgrade);
     * 2. **RUN THE INSTALLER INTERACTIVELY** (`Start-Process -Wait -ArgumentList '/UPDATE'`,
     *    no `/S` silent flag — `/UPDATE` skips the "existing installation" upgrade/repair/
     *    remove page and defaults the scope to the current install's scope; the scope-choice
     *    page still shows; the UAC prompt appears only when the user picks the global scope;
     *    with an existing install of the SAME OR OLDER version an "already installed —
     *    reinstall anyway?" confirmation shows first — declining exits 1);
     * 3. **THE EXIT CODE** (the NSIS script header pins the contract — InstallerContractTest keeps the two sides in sync): **0** → the app is
     *    relaunched from its current path — but only when it is NOT already running (the
     *    installer's finish page launched it) and its path still EXISTS (cross-scope edge: if
     *    the user migrated to the other scope in the installer UI, the old path is gone — no
     *    relaunch, no marker, the user just launched the app from the finish page); **1** (user
     *    cancelled any page — or the installer's running-app guard fired: the app was still
     *    running when the install started) / **2** (the remove choice — the existing install was removed, the
     *    app is gone by design) / **1223** (ERROR_CANCELLED — user DECLINED THE UAC) → NOTHING —
     *    a normal decline, no marker is written; **3** (install failure)
     *    / **4** (the elevated child vanished — death detected through its PID file +
     *    GetExitCodeProcess, or the PID file never appeared within the grace) or anything else → the
     *    marker.
     */
    internal fun windowsInstallScript(appExe: String, installerPath: String, markerPath: String): String {
        // Single quotes inside the PowerShell literals are doubled (the PowerShell escape)
        fun esc(value: String): String = value.replace("'", "''")
        val app = esc(appExe)
        val installer = esc(installerPath)
        val marker = esc(markerPath)
        return listOf(
            "\$appExe='$app'",
            "\$w=0",
            "while (\$w -lt ${APP_EXIT_WAIT_S}) {",
            "  \$alive=Get-Process -ErrorAction SilentlyContinue | Where-Object Path -eq \$appExe",
            "  if (-not \$alive) { break }",
            "  Start-Sleep -Seconds 1",
            "  \$w=\$w+1",
            "}",
            "if (\$alive) {",
            "  Set-Content -Path '$marker' -Value 'the app did not exit within ${APP_EXIT_WAIT_S} s; the install was aborted (its files were still locked)'",
            "  exit 1",
            "}",
            "\$p=Start-Process -FilePath '$installer' -ArgumentList '/UPDATE' -Wait -PassThru",
            "\$code=\$p.ExitCode",
            "if (\$code -eq 0) {",
            "  \$running=Get-Process -ErrorAction SilentlyContinue | Where-Object Path -eq \$appExe",
            "  if (-not \$running -and (Test-Path '$app')) { Start-Process -FilePath '$app' }",
            "} elseif (\$code -ne 1 -and \$code -ne 2 -and \$code -ne 1223) {",
            "  Set-Content -Path '$marker' -Value \"install failed with exit code \$code\"",
            "}",
        ).joinToString(" ")
    }

    /**
     * The Windows install gesture (spec AD-4 — **INTERACTIVE, NSIS** since the user decisions;
     * the silent `/quiet /wait` design, its timeout/tree-kill, the per-user install AND the
     * machine-level-only jpackage exe are superseded — `spec-updater.md`
     * Change Log): quit → the DETACHED PowerShell helper (its script is the pure
     * [windowsInstallScript]): it waits for the running instance to actually exit (the capped
     * [APP_EXIT_WAIT_S] poll — the deterministic path that releases the locked files, the NSIS
     * installer has no auto-close and it is not relied on), then launches the NSIS installer
     * **interactively with /UPDATE** (the "existing installation" upgrade/repair/remove page
     * is skipped; the scope defaults to the current install's scope — per-user unless it is
     * global; the global scope makes the installer re-launch itself elevated — a VISIBLE UAC
     * prompt at that moment; a same/older-version re-install is confirmed by an "already
     * installed — reinstall anyway?" prompt first — declining exits 1) — then INSPECTS THE
     * EXIT CODE (the NSIS script header pins the
     * contract): **0** → relaunches the app from its current path (only when it is not already
     * running — the installer's finish page may have launched it — and the path still exists);
     * **1** (user cancelled — or the installer's running-app guard fired) / **2** (remove choice — the existing install was removed, the
     * app is gone by design) / **1223** (user declined the UAC) → nothing (a normal decline —
     * the app simply does not relaunch); **3** (install failure) / **4** (the elevated child
     * vanished) or anything else → the
     * failure marker
     * ([installFailureMarkerPath] — consumed at
     * the next startup with an error toast). A hang is now VISIBLE to the user — there is no
     * helper timeout and no auto-kill of the installer.
     *
     * @return `true` when the helper started (the caller quits the app); `false` when it could
     *   not (the caller keeps the app alive and shows an error).
     */
    fun startWindowsInstall(installerPath: String): Boolean {
        // `ProcessHandle.info().command()` is an Optional<String> — flattened through runCatching
        val appExe = runCatching { ProcessHandle.current().info().command().orElse(null) }.getOrNull().orEmpty()
        // With an empty path the helper's wait loop (matched by EXACT path) would see "no app"
        // immediately and launch the installer over the still-running, file-locking instance —
        // refuse instead (the caller keeps the app alive and shows an error)
        if (appExe.isEmpty()) {
            log.severe("Windows install refused: the app executable path is empty (ProcessHandle.command() unavailable)")
            return false
        }
        val script = windowsInstallScript(appExe, installerPath, installFailureMarkerPath().toString())
        return try {
            ProcessBuilder("powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", script)
                .redirectErrorStream(true)
                .start()
            true
        } catch (e: Exception) {
            log.severe("Could not start the Windows install helper: ${e::class.simpleName} (${e.message})")
            false
        }
    }
}
