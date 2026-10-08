package app.n_zik.compagnon.updater

import app.n_zik.compagnon.generated.resources.*
import app.n_zik.compagnon.updater.services.UpdateDownloadManager
import app.n_zik.compagnon.updater.services.UpdateDownloadManager.DownloadState
import app.n_zik.compagnon.updater.services.updaterHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.jetbrains.compose.resources.getString
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

/**
 * The loop-2 download hardening (spec `spec-updater`, Review Triage): a body shorter than its
 * `Content-Length` is a [DownloadState.Failed] truncation with the localized message (the Ktor
 * engine rejects the short body while receiving it — no "completed" half-file, no raw engine
 * message); a cancel resets to [DownloadState.Idle] and deletes the cancelled download's OWN
 * file; and the asset name is validated before any path is built (no path escape — an unsafe
 * name fails without touching the disk).
 *
 * The download runs on the REAL `NzikDispatchers` (the fire-and-forget pool, not the test
 * scheduler), so the states are awaited on the [DownloadState] flow with a timeout; the engine
 * is the test seam [UpdateDownloadManager.httpClientFactory] (a Ktor `MockEngine`).
 */
class UpdateDownloadManagerTest {

    @TempDir
    lateinit var dir: Path

    @BeforeEach
    fun setUp() {
        UpdateDownloadManager.setTestDirectory(dir)
    }

    @AfterEach
    fun tearDown() {
        // Static state on the object — reset so no test leaks a download into the next one
        UpdateDownloadManager.cancelDownload()
        UpdateDownloadManager.setTestDirectory(null)
        UpdateDownloadManager.setTestMarkerDirectory(null)
        UpdateDownloadManager.httpClientFactory = ::updaterHttpClient
    }

    private fun mockClient(body: ByteArray, contentLength: String? = body.size.toString()): HttpClient = HttpClient(
        MockEngine {
            respond(
                content = body,
                status = HttpStatusCode.OK,
                headers = if (contentLength != null) headersOf(HttpHeaders.ContentLength, contentLength) else headersOf(),
            )
        },
    )

    @Test
    fun `a truncated download fails with the localized truncation message`() = runBlocking {
        // `runBlocking` (real time, the codebase test convention — see MessageFormatTest), not
        // runTest: the download runs on the REAL NzikDispatchers pool, and runTest's virtual
        // clock would let withTimeout expire before the real download makes progress.
        //
        // A 50-byte body against a Content-Length of 100. With the Ktor CIO client the engine
        // validates the body against its Content-Length while RECEIVING the response and rejects
        // the short body at the `get()` call — before any byte reaches the disk. So the download
        // fails with the localized truncation string (never the raw engine message, never a
        // "completed" half-file), and there is no partial file on disk (deviation from the spec
        // matrix "the file is kept": the bytes simply never get there with this client).
        val client = mockClient(ByteArray(50) { it.toByte() }, contentLength = "100")
        UpdateDownloadManager.httpClientFactory = { client }
        val assetName = "app-truncated-0.0.2.exe"

        UpdateDownloadManager.startDownload("https://github.com/N-Zik-Group/N-Zik-Desktop-Compagnon/releases/download/v0.0.2/$assetName", "0.0.2", assetName)

        val failed = withTimeout(10_000) { UpdateDownloadManager.downloadState.first { it is DownloadState.Failed } }
        assertInstanceOf(DownloadState.Failed::class.java, failed)
        // The raw engine message is mapped to the localized truncation string
        assertEquals(getString(Res.string.update_download_truncated), (failed as DownloadState.Failed).error)
        // No partial file was created (the short body is rejected before the file is opened)
        assertFalse(File(dir.toFile(), assetName).exists(), "no file may exist for a body rejected by the engine")
        client.close()
    }

    @Test
    fun `a cancelled download resets to idle and deletes its own file`() = runBlocking {
        val client = mockClient(ByteArray(1 shl 20) { it.toByte() })
        UpdateDownloadManager.httpClientFactory = { client }
        val assetName = "app-cancelled-0.0.2.exe"

        UpdateDownloadManager.startDownload("https://github.com/N-Zik-Group/N-Zik-Desktop-Compagnon/releases/download/v0.0.2/$assetName", "0.0.2", assetName)

        // Wait for the download to LEAVE Starting, then cancel. The 1 MB in-memory mock body
        // can be fully consumed BEFORE this wait even reads the state (fast filesystem +
        // in-memory engine): the StateFlow then conflates to Completed and a
        // `first { Downloading }` wait would never match. So wait for ANY non-Starting state —
        // whatever it is, the cancel below is authoritative (the manager's cancel fence turns
        // a late Completed/Failed into a cancel, and cancelDownload's synchronous backstop
        // deletes the file when the job already finished), so the terminal state is Idle with
        // the file gone on every platform.
        withTimeout(10_000) {
            UpdateDownloadManager.downloadState.first { it !is DownloadState.Starting }
        }
        UpdateDownloadManager.cancelDownload()

        assertInstanceOf(DownloadState.Idle::class.java, withTimeout(10_000) { UpdateDownloadManager.downloadState.first { it is DownloadState.Idle } })
        // The file is deleted either way — by the job's cleanup after it closes its stream
        // (cancel mid-download; a file cannot be deleted while its handle is open — Windows)
        // or by cancelDownload's synchronous backstop (the download completed first) — so wait
        // for its absence instead of asserting it right after cancelDownload() returns
        val file = File(dir.toFile(), assetName)
        withTimeout(10_000) {
            while (file.exists()) {
                delay(20)
            }
        }
        assertFalse(file.exists(), "the cancelled download's own file must be deleted")
        client.close()
    }

    @Test
    fun `an unsafe asset name fails without touching the disk`() = runBlocking {
        val client = mockClient(ByteArray(16))
        UpdateDownloadManager.httpClientFactory = { client }

        UpdateDownloadManager.startDownload("https://example.com/x", "0.0.2", "../evil.exe")

        assertInstanceOf(DownloadState.Failed::class.java, withTimeout(10_000) { UpdateDownloadManager.downloadState.first { it is DownloadState.Failed } })
        // Nothing was written anywhere under the download dir
        assertFalse(dir.toFile().listFiles().orEmpty().any { it.name.contains("evil") }, "no file may be created for an unsafe asset name")
        client.close()
    }

    @Test
    fun `the safe asset name validation is pure`() {
        assertTrue(UpdateDownloadManager.isSafeAssetName("app-0.0.2-beta.exe"))
        assertTrue(UpdateDownloadManager.isSafeAssetName("n-zik-desktop-compagnon_0.0.2-1_amd64.deb"))
        assertFalse(UpdateDownloadManager.isSafeAssetName(""))
        assertFalse(UpdateDownloadManager.isSafeAssetName("a/b.exe"))
        assertFalse(UpdateDownloadManager.isSafeAssetName("a\\b.exe"))
        assertFalse(UpdateDownloadManager.isSafeAssetName("..\\up.exe"))
        assertFalse(UpdateDownloadManager.isSafeAssetName("a..b.exe"))
    }

    // ---- The Windows install helper script (interactive NSIS — the user decisions replaced the
    // silent design, the per-user design and the machine-level-only jpackage exe) ----

    private fun helperScript(): String = UpdateDownloadManager.windowsInstallScript(
        appExe = "C:\\Program Files\\N-Zik Desktop Compagnon (Dev)\\N-Zik Desktop Compagnon (Dev).exe",
        installerPath = "C:\\Users\\danie\\AppData\\Local\\N-Zik Desktop Compagnon\\updates\\N-Zik Desktop Compagnon (Dev)-0.0.2-dev.exe",
        markerPath = "C:\\Users\\danie\\AppData\\Roaming\\N-Zik Desktop Compagnon\\update-install-failed.txt",
    )

    @Test
    fun `the helper waits for the app to exit with a capped poll before launching the installer`() {
        val script = helperScript()
        // The capped poll: 1-s tick, the cap constant, the app matched by its exact path —
        // the deterministic path that closes the running instance and releases the locked files
        assertTrue(script.contains("Start-Sleep -Seconds 1"))
        assertTrue(script.contains("while (\$w -lt ${UpdateDownloadManager.APP_EXIT_WAIT_S})"))
        assertTrue(script.contains("Where-Object Path -eq \$appExe"))
        // A still-alive app at the cap aborts with the failure marker (not an install over locked files)
        assertTrue(script.contains("the app did not exit within ${UpdateDownloadManager.APP_EXIT_WAIT_S} s; the install was aborted"))
        assertTrue(script.contains("exit 1"))
    }

    @Test
    fun `the helper launches the installer interactively with the UPDATE flag (no silent flag, no timeout, no auto-kill)`() {
        val script = helperScript()
        // The interactive launch with /UPDATE: the "existing installation" upgrade/repair/
        // remove page is skipped (the scope defaults to the current install's scope); the
        // scope-choice page still shows (the UAC prompt only when the user picks the global
        // scope — then the progress), so the MSI-era /quiet flag AND the NSIS /S silent flag
        // are both gone
        assertFalse(script.contains("/quiet"), "the MSI-era /quiet flag must be gone")
        assertFalse(script.contains("/S"), "the NSIS /S silent flag must be gone")
        assertTrue(script.contains("Start-Process -FilePath 'C:\\Users\\danie\\AppData\\Local\\N-Zik Desktop Compagnon\\updates\\N-Zik Desktop Compagnon (Dev)-0.0.2-dev.exe' -ArgumentList '/UPDATE' -Wait -PassThru"))
        // No timeout and no auto-kill: a hang is now VISIBLE to the user, and killing a dialog
        // the user is looking at would be wrong
        assertFalse(script.contains("WaitForExit("), "no install timeout anymore")
        assertFalse(script.contains("Kill-Tree"), "no process-tree kill anymore")
        assertFalse(script.contains("taskkill"), "no blanket process kill anymore")
    }

    @Test
    fun `the helper relaunches on 0 when not already running, treats 1, 2 and 1223 as normal declines, marks the other codes`() {
        val script = helperScript()
        // The exit codes the NSIS installer returns (the NSIS script header pins the contract):
        // 0 → relaunch, but only when the app is NOT already running (the installer's finish
        // page may have launched it) and its path still exists (the cross-scope migration edge:
        // the old path is gone → no relaunch, no marker)
        assertTrue(script.contains("if (\$code -eq 0) {"))
        assertTrue(script.contains("\$running=Get-Process -ErrorAction SilentlyContinue | Where-Object Path -eq \$appExe"))
        assertTrue(script.contains(
            "if (-not \$running -and (Test-Path 'C:\\Program Files\\N-Zik Desktop Compagnon (Dev)\\N-Zik Desktop Compagnon (Dev).exe')) { Start-Process -FilePath 'C:\\Program Files\\N-Zik Desktop Compagnon (Dev)\\N-Zik Desktop Compagnon (Dev).exe' }",
        ))
        // 1 (user cancelled any page) / 2 (remove choice — the app is gone by design) /
        // 1223 (user declined the UAC) → NOTHING: a normal decline writes no marker
        assertTrue(script.contains("} elseif (\$code -ne 1 -and \$code -ne 2 -and \$code -ne 1223) {"))
        assertTrue(script.contains("install failed with exit code \$code"))
        // Two marker writes only: the app never exited / a bad exit code (a cancel is not one)
        assertEquals(2, script.split("Set-Content -Path '").size - 1, "exactly the two failure paths write the marker")
        // The marker path is the helper's single marker, single-quoted for PowerShell
        assertTrue(script.contains("C:\\Users\\danie\\AppData\\Roaming\\N-Zik Desktop Compagnon\\update-install-failed.txt"))
    }

    @Test
    fun `the helper script escapes single quotes in the paths`() {
        val script = UpdateDownloadManager.windowsInstallScript(
            appExe = "C:\\it's\\App.exe",
            installerPath = "C:\\it's\\Installer.exe",
            markerPath = "C:\\it's\\marker.txt",
        )
        // The PowerShell single-quote escape: doubled quotes, never a bare one in the literals
        assertTrue(script.contains("\$appExe='C:\\it''s\\App.exe'"))
        assertTrue(script.contains("Start-Process -FilePath 'C:\\it''s\\Installer.exe'"))
        assertTrue(script.contains("Set-Content -Path 'C:\\it''s\\marker.txt'"))
    }

    // ---- The install failure marker (the helper writes it on a failed install, the app consumes
    // it at the next startup) ----

    @Test
    fun `consumeInstallFailureMarker returns the content, deletes the marker, null when absent`() {
        // The seam: the marker lives under the temp dir, not the real data dir
        UpdateDownloadManager.setTestMarkerDirectory(dir)
        val marker = UpdateDownloadManager.installFailureMarkerPath()

        assertNull(UpdateDownloadManager.consumeInstallFailureMarker(), "no marker before the helper wrote one")

        Files.writeString(marker, "install failed with exit code 3")
        assertEquals("install failed with exit code 3", UpdateDownloadManager.consumeInstallFailureMarker())
        assertFalse(Files.exists(marker), "the marker must be deleted when consumed")
        assertNull(UpdateDownloadManager.consumeInstallFailureMarker(), "a consumed marker is gone")
    }
}
