package app.n_zik.compagnon

import app.n_zik.compagnon.updater.models.ArtifactNames
import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The build's channel artifact rename (spec `spec-updater`, AD-2 / AD-8, loop 2 — the
 * `versionBoundaryIndex` / `canBeChannelRenamed` / `channelArtifactName` helpers in
 * `ComposeN-Zik/build.gradle.kts`): build scripts cannot call test sources, so this file carries a
 * TEST-SIDE COPY of the logic (the [UnescapedStringsTest] idiom — the copy pins the documented
 * semantics, the build's own values pin its implementation). Pinned: the version BOUNDARY check
 * (a base version that is a prefix of a longer version is never hit mid-number), the anti-RE-
 * SUFFIX guard (a stale suffixed artifact is rejected, never corrupted) and the effective
 * per-task artifact names the build exposes (`artifacts.*` / `windows.installer.name` /
 * `windows.portable.zipName` system properties — the installer output name is cross-checked
 * against `ArtifactNames.exe`, the name the in-app updater downloads by).
 */
class ChannelArtifactNameTest {

    // ---- The test-side copy of the build's helpers (the build globals parameterized) ----

    private fun versionBoundaryIndex(fileName: String, base: String): Int {
        var from = 0
        while (true) {
            val index = fileName.indexOf(base, from)
            if (index < 0) return -1
            val next = fileName.getOrNull(index + base.length)
            if (next == null || next == '.' || next == '-' || next == '_') return index
            from = index + 1
        }
    }

    private fun canBeChannelRenamed(fileName: String, base: String, suffix: String, suffixPrefix: String): Boolean {
        if (suffix.isEmpty()) return true
        val index = versionBoundaryIndex(fileName, base)
        if (index < 0) return false
        return suffixPrefix.isEmpty() || !fileName.startsWith(suffixPrefix, index + base.length)
    }

    private fun channelArtifactName(fileName: String, base: String, suffix: String, suffixPrefix: String): String {
        if (suffix.isEmpty()) return fileName
        check(canBeChannelRenamed(fileName, base, suffix, suffixPrefix)) {
            "channel rename: '$fileName' is not a plain-base artifact to rename"
        }
        val index = versionBoundaryIndex(fileName, base)
        return fileName.substring(0, index + base.length) + suffix + fileName.substring(index + base.length)
    }

    // ---- The build's globals of this test run (the system properties the build exposes) ----

    private val channel = System.getProperty("channel") ?: error("the build must expose the effective channel")

    private val baseVersion = Regex("""nzikVersionName\s*=\s*"?([^"\n]+)"?""")
        .find(File("../gradle/libs.versions.toml").readText())?.groupValues?.get(1)
        ?: error("the catalog's nzikVersionName line is missing")

    private val channelSuffix =
        (System.getProperty("appVersion.versionName") ?: error("appVersion.versionName is not exposed to the tests"))
            .substring(baseVersion.length)

    private val channelSuffixPrefix = when (channel) {
        "dev" -> "-dev-"
        "git" -> "-git-"
        else -> channelSuffix
    }

    // ---- (a) the pure semantics of the copy (every channel shape, not only this test run's) ----

    @Test
    fun `the suffix is inserted right after the base version`() {
        assertEquals(
            "n-zik-desktop-compagnon-0.0.2-beta-linux-portable.tar.gz",
            channelArtifactName("n-zik-desktop-compagnon-0.0.2-linux-portable.tar.gz", "0.0.2", "-beta", "-beta"),
        )
        assertEquals(
            "N-Zik Desktop Compagnon DEV-0.0.2-dev-20261007.exe",
            channelArtifactName("N-Zik Desktop Compagnon DEV-0.0.2.exe", "0.0.2", "-dev-20261007", "-dev-"),
        )
    }

    @Test
    fun `an empty suffix is a no-op (stable and debug stay byte-identical)`() {
        val name = "n-zik-desktop-compagnon-0.0.2-linux-portable.tar.gz"
        assertEquals(name, channelArtifactName(name, "0.0.2", "", ""))
        assertTrue(canBeChannelRenamed("any-stale-name", "0.0.2", "", ""))
    }

    @Test
    fun `the base version must sit on a version boundary`() {
        // The boundary characters are dot, dash, underscore — and the end of the name
        assertEquals(6, versionBoundaryIndex("n-zik-0.0.1-linux-portable.tar.gz", "0.0.1"))
        assertEquals(2, versionBoundaryIndex("v-0.0.1", "0.0.1"))
        assertEquals(4, versionBoundaryIndex("pkg_0.0.2_amd64", "0.0.2"))
        assertEquals(4, versionBoundaryIndex("app.0.0.2.exe", "0.0.2"))
        // "0.0.1" inside "0.0.11-…" is a PREFIX of a longer version — never hit mid-number
        assertEquals(-1, versionBoundaryIndex("n-zik-desktop-compagnon-0.0.11-linux-portable.tar.gz", "0.0.1"))
        assertFalse(canBeChannelRenamed("n-zik-desktop-compagnon-0.0.11-linux-portable.tar.gz", "0.0.1", "-beta", "-beta"))
        assertThrows(IllegalStateException::class.java) {
            channelArtifactName("n-zik-desktop-compagnon-0.0.11-linux-portable.tar.gz", "0.0.1", "-beta", "-beta")
        }
    }

    @Test
    fun `an artifact that already carries the suffix is rejected`() {
        // A stale dated dev file must never be re-suffixed into a corrupted name
        val staleDev = "n-zik-desktop-compagnon-0.0.2-dev-20261006-linux-portable.tar.gz"
        assertFalse(canBeChannelRenamed(staleDev, "0.0.2", "-dev-20261007", "-dev-"))
        assertThrows(IllegalStateException::class.java) { channelArtifactName(staleDev, "0.0.2", "-dev-20261007", "-dev-") }
        // No -beta-beta either
        assertFalse(canBeChannelRenamed("n-zik-desktop-compagnon-0.0.2-beta-linux-portable.tar.gz", "0.0.2", "-beta", "-beta"))
        // A missing base version is rejected too
        assertFalse(canBeChannelRenamed("n-zik-other-9.9.9-linux-portable.tar.gz", "0.0.2", "-beta", "-beta"))
        // … but the same base version is fine (the guard is on the SUFFIX, not the version)
        assertTrue(canBeChannelRenamed("n-zik-desktop-compagnon-0.0.2-linux-portable.tar.gz", "0.0.2", "-dev-20261007", "-dev-"))
    }

    // ---- (b) the effective per-task artifact names the build exposes ----

    @Test
    fun `the effective per-task artifact names are the channel-renamed jpackage names`() {
        val windowsPackageName = System.getProperty("windows.packageName") ?: error("windows.packageName is not exposed to the tests")
        val linuxPackageName = System.getProperty("linux.packageName") ?: error("linux.packageName is not exposed to the tests")
        assertEquals(
            channelArtifactName("$windowsPackageName-$baseVersion.exe", baseVersion, channelSuffix, channelSuffixPrefix),
            System.getProperty("artifacts.exe"),
            "the exe artifact name is the channel-renamed jpackage name",
        )
        assertEquals(
            channelArtifactName("${linuxPackageName}_$baseVersion-1_amd64.deb", baseVersion, channelSuffix, channelSuffixPrefix),
            System.getProperty("artifacts.deb"),
            "the deb artifact name is the channel-renamed jpackage name",
        )
        assertEquals(
            channelArtifactName("$linuxPackageName-$baseVersion-1.x86_64.rpm", baseVersion, channelSuffix, channelSuffixPrefix),
            System.getProperty("artifacts.rpm"),
            "the rpm artifact name is the channel-renamed jpackage name",
        )
        // The Windows portable zip reuses the channel's package base (spec `spec-windows-portable-zip`)
        assertEquals(
            channelArtifactName("$linuxPackageName-$baseVersion-windows-portable.zip", baseVersion, channelSuffix, channelSuffixPrefix),
            System.getProperty("windows.portable.zipName"),
            "the windows portable zip artifact name is the channel-renamed package-base name",
        )
        // The NSIS installer output name rides on the channel's package base (the build writes it
        // directly — the CI uploads it as-is, no rename step): it must be the channel-renamed
        // package-base name AND the exact name the in-app updater (ArtifactNames.exe) downloads by
        assertEquals(
            channelArtifactName("$linuxPackageName-$baseVersion.exe", baseVersion, channelSuffix, channelSuffixPrefix),
            System.getProperty("windows.installer.name"),
            "the installer output name is the channel-renamed package-base name",
        )
        assertEquals(
            ArtifactNames.exe("$baseVersion$channelSuffix", channel),
            System.getProperty("windows.installer.name"),
            "the installer output name is what the in-app updater (ArtifactNames.exe) downloads",
        )
    }
}
