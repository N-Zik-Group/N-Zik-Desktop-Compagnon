package app.n_zik.compagnon.components.ui.screens.settings

import app.n_zik.compagnon.generated.AppVersion
import app.n_zik.compagnon.updater.models.ArtifactNames
import app.n_zik.compagnon.updater.models.InstallMode
import app.n_zik.compagnon.updater.models.PackageManager
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
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

/**
 * The update page's "Download again last update" gesture (spec `spec-arch-binary-package-release`,
 * loop 2 G14): the release marker is adopted on the re-download too — a pacman install picks the
 * 7th asset only when it is a release-pkg install (`isReleasePackage` = the `distribution.txt`
 * marker present), an AUR install (no marker) is a no-op (its updater is blocked before any
 * download, so the second `forMode` call site must not silently skip the marker either).
 *
 * The release read goes through the injected [HttpClient] (the test seam — the production default
 * is the updater's CIO client); the download rides the shared [UpdateDownloadManager] (its
 * `httpClientFactory` seam — the house pattern from `UpdateDownloadManagerTest`: the download runs
 * on the REAL `NzikDispatchers` pool, so the states are awaited on the flow with a timeout).
 */
class RedownloadCurrentVersionTest {

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

    /** The running version's release, carrying its channel's Arch package asset. */
    private fun archPkgReleaseJson(): String {
        val tag = "v${AppVersion.versionName}"
        val assetName = ArtifactNames.archPkg(AppVersion.versionName)
        return """
            {
              "id": 1,
              "tag_name": "$tag",
              "name": "$tag",
              "body": "release notes",
              "prerelease": false,
              "assets": [
                {
                  "id": 10,
                  "url": "https://github.com/N-Zik-Group/N-Zik-Desktop-Compagnon/releases/assets/10",
                  "name": "$assetName",
                  "size": 12345678,
                  "created_at": "2026-10-01T00:00:00Z",
                  "browser_download_url": "https://github.com/N-Zik-Group/N-Zik-Desktop-Compagnon/releases/download/$tag/$assetName"
                }
              ]
            }
        """.trimIndent()
    }

    /** The seam of the release read: the running version's tag answered with the arch asset. */
    private fun releaseClient(): HttpClient = HttpClient(
        MockEngine {
            respond(
                content = archPkgReleaseJson(),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        },
    )

    /** The download seam ([UpdateDownloadManager.httpClientFactory]): a small complete body. */
    private fun downloadClient(): HttpClient = HttpClient(
        MockEngine {
            respond(
                content = byteArrayOf(1, 2, 3),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentLength, "3"),
            )
        },
    )

    @Test
    fun `a release package install re-downloads the arch package asset`() = runBlocking {
        // `runBlocking` (real time), not runTest: the download runs on the REAL NzikDispatchers
        // pool, and runTest's virtual clock would let withTimeout expire before the real
        // download makes progress (the UpdateDownloadManagerTest convention). The gesture's
        // fire-and-forget changelog fetch is seam'd out (a no-op in the test — no live network
        // call in a unit test, review loop 3 — L3-BH10).
        val client = releaseClient()
        UpdateDownloadManager.httpClientFactory = { downloadClient() }
        try {
            redownloadCurrentVersion(
                installMode = InstallMode.PACKAGE_MANAGED,
                packageManager = PackageManager.AUR,
                isReleasePackage = true,
                client = client,
                changelogFetcher = {},
            )
        } finally {
            client.close()
        }
        val state = withTimeout(10_000) {
            UpdateDownloadManager.downloadState.first { it is DownloadState.Completed }
        }
        assertInstanceOf(DownloadState.Completed::class.java, state)
        val completed = state as DownloadState.Completed
        assertTrue(
            completed.filePath.endsWith(ArtifactNames.archPkg(AppVersion.versionName)),
            "the re-downloaded file is the running version's Arch package (the 7th asset): ${completed.filePath}",
        )
    }

    @Test
    fun `an aur install without marker does not download`() = runBlocking {
        // No marker → no asset selected for the pacman install → the gesture is a no-op (no
        // download started — the state stays Idle; a started download would have completed in
        // the in-memory engine, so the Idle check is authoritative after a settle)
        val client = releaseClient()
        UpdateDownloadManager.httpClientFactory = { downloadClient() }
        try {
            redownloadCurrentVersion(
                installMode = InstallMode.PACKAGE_MANAGED,
                packageManager = PackageManager.AUR,
                isReleasePackage = false,
                client = client,
                changelogFetcher = {},
            )
        } finally {
            client.close()
        }
        delay(250)
        assertEquals(
            DownloadState.Idle,
            UpdateDownloadManager.downloadState.value,
            "an AUR install (no marker) selects no asset — no download may be started",
        )
    }
}
