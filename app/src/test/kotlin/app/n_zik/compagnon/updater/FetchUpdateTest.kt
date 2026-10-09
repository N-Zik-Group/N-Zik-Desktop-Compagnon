package app.n_zik.compagnon.updater

import app.n_zik.compagnon.updater.models.ArtifactNames
import app.n_zik.compagnon.updater.models.InstallMode
import app.n_zik.compagnon.updater.models.PackageManager
import app.n_zik.compagnon.updater.models.currentInstallMode
import app.n_zik.compagnon.updater.models.probePackageManager
import app.n_zik.compagnon.updater.services.Updater
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import java.nio.file.NoSuchFileException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Drives [Updater.fetchUpdate] against a Ktor `MockEngine` with canned GitHub releases (spec
 * `spec-updater`, AD-1 / AD-3): the channel filter over the whole releases list, the dev date
 * tiebreak, the `NoSuchFileException` "no update available" mapping, the changelog fetch (the body
 * link / `dev.txt`) and the per-mode asset selection. Every release carries ALL the channel's
 * asset names (the uniform 6-asset matrix), so the assertion holds on any host (Windows exe,
 * Linux deb / rpm / zip / flatpak / Arch pkg, or the pacman / none case with no asset).
 */
class FetchUpdateTest {

    @BeforeEach
    fun resetStaticState() {
        // All the results `fetchUpdate` writes are static on the `Updater` object — reset every
        // one so the tests do not leak state across each other (JUnit does not guarantee order)
        Updater.latestChangelog = null
        Updater.latestVersionCode = null
        Updater.githubRelease = null
        Updater.build = null
    }

    private fun releasesJson(vararg tags: String, changelogCode: String? = null): String =
        tags.joinToString(", ", prefix = "[", postfix = "]") { tag ->
            val body = if (tag.startsWith("v0.0.2") && changelogCode != null) {
                "Release notes — full changelog: [the changes](Updater/changelogs/$changelogCode.txt)"
            } else {
                "Release notes with no changelog link"
            }
            """
            {
              "id": ${tags.indexOf(tag) + 1},
              "tag_name": "$tag",
              "name": "$tag",
              "body": ${Json.quote(body)},
              "prerelease": ${tag.contains("-dev")},
              "assets": [
                ${assetJson(tag, ArtifactNames.exe(tag.removePrefix("v")))},
                ${assetJson(tag, ArtifactNames.deb(tag.removePrefix("v")))},
                ${assetJson(tag, ArtifactNames.rpm(tag.removePrefix("v")))},
                ${assetJson(tag, ArtifactNames.portableZip(tag.removePrefix("v")))},
                ${assetJson(tag, ArtifactNames.flatpak(tag.removePrefix("v")))},
                ${assetJson(tag, ArtifactNames.archPkg(tag.removePrefix("v")))}
              ]
            }
            """.trimIndent()
        }

    /**
     * The staging carrier of the pinned Linux VLC tarball (spec `spec-vlc-tarball-ci`): the
     * prerelease release `vlc-<v>-tarball` with its REAL asset name `vlc-<v>-linux-x64.tar.gz`.
     * It is not an app release — the channel filter must never select it.
     */
    private fun tarballReleaseJson(version: String = "3.0.24"): String = """
        {
          "id": 99,
          "tag_name": "vlc-$version-tarball",
          "name": "VLC Linux tarball $version (staging)",
          "body": "Staging release - consumed only by the version catalog, never by the in-app updater.",
          "prerelease": true,
          "assets": [
            ${assetJson("vlc-$version-tarball", "vlc-$version-linux-x64.tar.gz")}
          ]
        }
    """.trimIndent()

    /** The app tags' releases list with the tarball prerelease appended (the carrier alongside the app releases). */
    private fun releasesWithTarball(vararg tags: String): String =
        releasesJson(*tags).dropLast(1) + ", " + tarballReleaseJson() + "]"

    private fun assetJson(tag: String, name: String): String = """
        {
          "id": 10,
          "url": "https://github.com/N-Zik-Group/N-Zik-Desktop-Compagnon/releases/assets/10",
          "name": ${Json.quote(name)},
          "size": 12345678,
          "created_at": "2026-10-01T00:00:00Z",
          "browser_download_url": "https://github.com/N-Zik-Group/N-Zik-Desktop-Compagnon/releases/download/$tag/$name"
        }
    """.trimIndent()

    private fun clientFor(releases: String): HttpClient = HttpClient(
        MockEngine { request ->
            when {
                request.url.encodedPath.endsWith("/releases") -> respond(
                    content = releases,
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )

                request.url.encodedPath.contains("Updater/changelogs") -> respond(
                    content = "New:\n- the mocked changelog",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "text/plain"),
                )

                else -> respondError(HttpStatusCode.NotFound)
            }
        },
    )

    @Test
    fun `the stable channel picks the newest plain tag, ignoring beta and dev`() = runTest {
        val client = clientFor(
            releasesJson("v0.0.1", "v0.0.1-beta", "v0.0.2", "v0.0.2-beta", "v0.0.2-dev-20261001", changelogCode = "2"),
        )
        try {
            Updater.fetchUpdate(client, "stable")
        } finally {
            client.close()
        }
        assertEquals("v0.0.2", Updater.githubRelease?.tagName)
    }

    @Test
    fun `the beta channel picks the newest beta tag`() = runTest {
        val client = clientFor(
            releasesJson("v0.0.1-beta", "v0.0.2-beta", "v0.0.2", changelogCode = "3"),
        )
        try {
            Updater.fetchUpdate(client, "beta")
        } finally {
            client.close()
        }
        assertEquals("v0.0.2-beta", Updater.githubRelease?.tagName)
    }

    @Test
    fun `the dev channel breaks the base tie on the date suffix`() = runTest {
        val client = clientFor(
            releasesJson("v0.0.2-dev-20261001", "v0.0.2-dev-20261002", "v0.0.2"),
        )
        try {
            Updater.fetchUpdate(client, "dev")
        } finally {
            client.close()
        }
        assertEquals("v0.0.2-dev-20261002", Updater.githubRelease?.tagName)
    }

    @Test
    fun `no same-channel release throws NoSuchFileException`() = runTest {
        // Only a beta release is published, the stable check must report "no update available"
        val client = clientFor(releasesJson("v0.0.1-beta"))
        // `fetchUpdate` is suspend, so it is called directly in the coroutine body (a plain
        // `assertThrows` lambda is not suspend); the catch records the mapping to assert on
        var thrown: NoSuchFileException? = null
        try {
            Updater.fetchUpdate(client, "stable")
        } catch (e: NoSuchFileException) {
            thrown = e
        } finally {
            client.close()
        }
        assertNotNull(thrown)
        assertNull(Updater.githubRelease)
    }

    @Test
    fun `the vlc tarball staging release does not change the channel pick`() = runTest {
        // The tarball prerelease (its real asset name vlc-3.0.24-linux-x64.tar.gz) sits in the
        // list alongside the app tags — the pick of every checking channel is unchanged (the
        // filter is tag-based; the prerelease flag is never read by fetchUpdate)
        val expectedPicks = linkedMapOf(
            "stable" to "v0.0.2",
            "beta" to "v0.0.2-beta",
            "dev" to "v0.0.2-dev-20261002",
        )
        for ((channel, pick) in expectedPicks) {
            val client = clientFor(
                releasesWithTarball("v0.0.1", "v0.0.2", "v0.0.2-beta", "v0.0.2-dev-20261001", "v0.0.2-dev-20261002"),
            )
            try {
                Updater.fetchUpdate(client, channel)
            } finally {
                client.close()
            }
            assertEquals(pick, Updater.githubRelease?.tagName, "channel $channel")
        }
    }

    @Test
    fun `a repo with only the vlc tarball staging release reports no update`() = runTest {
        // The tarball prerelease is the ONLY release: `findBestRelease` returns null for every
        // channel (the carrier is not same-channel), so `fetchUpdate` maps to the same
        // "no update available" path as an empty list — it throws NoSuchFileException and the
        // static state stays untouched (the actual behavior at Updater.fetchUpdate)
        val client = clientFor("[" + tarballReleaseJson() + "]")
        var thrown: NoSuchFileException? = null
        try {
            Updater.fetchUpdate(client, "stable")
        } catch (e: NoSuchFileException) {
            thrown = e
        } finally {
            client.close()
        }
        assertNotNull(thrown)
        assertNull(Updater.githubRelease)
        assertNull(Updater.build)
    }

    @Test
    fun `the stable release changelog is fetched from the body link`() = runTest {
        val client = clientFor(
            releasesJson("v0.0.1", "v0.0.2", changelogCode = "2"),
        )
        try {
            Updater.fetchUpdate(client, "stable")
        } finally {
            client.close()
        }
        assertEquals("New:\n- the mocked changelog", Updater.latestChangelog)
        assertEquals(2, Updater.latestVersionCode)
    }

    @Test
    fun `the dev release changelog is always dev txt, regardless of the body`() = runTest {
        // The dev tag links a (wrong) code file in its body — the dev branch still fetches dev.txt
        val client = clientFor(
            releasesJson("v0.0.2-dev-20261001", changelogCode = "99"),
        )
        try {
            Updater.fetchUpdate(client, "dev")
        } finally {
            client.close()
        }
        assertEquals("New:\n- the mocked changelog", Updater.latestChangelog)
    }

    @Test
    fun `the selected asset is this host's install-mode asset for the release`() = runTest {
        val client = clientFor(
            releasesJson("v0.0.2", changelogCode = "2"),
        )
        try {
            Updater.fetchUpdate(client, "stable")
        } finally {
            client.close()
        }
        val expected = ArtifactNames.forMode(currentInstallMode(), "0.0.2", probePackageManager())
        if (expected == null) {
            // pacman / no-probe host: no binary asset, the dialog shows the hint instead
            assertNull(Updater.build)
        } else {
            assertEquals(expected, Updater.build?.name)
        }
    }

    @Test
    fun `a marked pacman install picks the arch package asset, an aur install picks none`() = runTest {
        // The 7th asset (spec `spec-arch-binary-package-release`): the release marker decides —
        // a package-managed install on pacman gets the binary package only when it is a
        // release-pkg install; without the marker (an AUR install) no asset is selected (its
        // updater is blocked before it reaches a check on that path)
        val client = clientFor(
            releasesJson("v0.0.2"),
        )
        try {
            Updater.fetchUpdate(
                client,
                "stable",
                installMode = InstallMode.PACKAGE_MANAGED,
                packageManager = PackageManager.AUR,
                isReleasePackage = true,
            )
            assertEquals(ArtifactNames.archPkg("0.0.2"), Updater.build?.name)

            // Reset between the two fetches (the object's static state, JUnit does not guarantee order)
            Updater.build = null
            Updater.fetchUpdate(
                client,
                "stable",
                installMode = InstallMode.PACKAGE_MANAGED,
                packageManager = PackageManager.AUR,
                isReleasePackage = false,
            )
            assertNull(Updater.build, "an AUR install (no marker) has no binary asset")
        } finally {
            client.close()
        }
    }
}

/** Minimal JSON string escaper (the canned bodies carry no special characters — kept explicit). */
private object Json {
    fun quote(s: String): String = "\"${s.replace("\"", "\\\"")}\""
}
