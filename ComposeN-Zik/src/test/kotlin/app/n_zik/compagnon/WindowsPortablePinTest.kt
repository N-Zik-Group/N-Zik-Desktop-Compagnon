package app.n_zik.compagnon

import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The Windows portable zip contract (spec `spec-windows-portable-zip`) lives in build config that
 * no other test path touches: a renamed `linuxPackageName` (the zip reuses the channel's package
 * base — the dev channel keeps its own `-dev` base, AD-8) would desynchronize the zip name from
 * the other six app assets of the channel, and a helper drift in the channel rename would
 * corrupt the name — all with a green build. The build exposes the effective archive name
 * (`windows.portable.zipName` in `ComposeN-Zik/build.gradle.kts`, same pattern as `linux.portable.zipName`)
 * so this test pins it. Channel-aware: stable/debug keep the catalog base byte-identical, beta /
 * dev / git carry the channel suffix right after the base version — `build.sh package beta|dev`
 * runs `:ComposeN-Zik:test -Pchannel=…`, so a channel-blind pin reds those builds. (The zip ITSELF is
 * Windows-host only; the NAME is channel-derived and pinned on every host.)
 */
class WindowsPortablePinTest {

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

    // The test-side copy of the build's single rename helper (the [ChannelArtifactNameTest]
    // idiom — the copy pins the documented semantics, the build's own values pin its implementation)

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

    private fun channelArtifactName(fileName: String, base: String, suffix: String, suffixPrefix: String): String {
        if (suffix.isEmpty()) return fileName
        val index = versionBoundaryIndex(fileName, base)
        require(index >= 0) { "the bounded base version '$base' is missing from '$fileName'" }
        return fileName.substring(0, index + base.length) + suffix + fileName.substring(index + base.length)
    }

    /** The channel-aware package base the running build generates (dev: its own `-dev` base, AD-8). */
    private fun pkgName(): String =
        System.getProperty("linux.packageName") ?: error("linux.packageName is not exposed to the tests")

    private val zipName: String
        get() = System.getProperty("windows.portable.zipName")
            ?: error("the windows portable zip archiveFileName is not exposed to the tests")

    @Test
    fun `the zip name is the frozen package base with the windows portable suffix`() {
        // The dev channel keeps its OWN package base (AD-8 — a parallel product): the frozen
        // install identity is the base name, dev adds its -dev suffix
        assertEquals(
            "n-zik-desktop-compagnon",
            pkgName().removeSuffix("-dev"),
            "the catalog-derived linux.packageName base is the frozen install identity (dev adds its -dev suffix)",
        )
        // The zip reuses the channel's package base — the same base the other app assets of the
        // channel share (the Linux portable zip, the installer and the packages all derive from it)
        assertTrue(zipName.startsWith("${pkgName()}-"), "the windows portable zip base name must be the channel-aware package name: $zipName")
        assertTrue(zipName.endsWith("-windows-portable.zip"), "the windows portable zip keeps its -windows-portable.zip suffix: $zipName")
    }

    @Test
    fun `the zip name is byte-identical per channel through the single rename helper`() {
        // The full shape: <package base>-<catalog base version>[channel suffix]-windows-portable.zip
        // (the channel suffix rides right after the base version, exactly like the other assets)
        assertEquals(
            channelArtifactName("${pkgName()}-$baseVersion-windows-portable.zip", baseVersion, channelSuffix, channelSuffixPrefix),
            zipName,
            "the windows portable zip name is <package base>-<version>[channel suffix]-windows-portable.zip",
        )
        // Stable / debug: no channel suffix — the name is the package base + catalog base version
        if (channelSuffix.isEmpty()) {
            assertEquals("${pkgName()}-$baseVersion-windows-portable.zip", zipName, "stable/debug: the zip name is byte-identical (no channel suffix)")
        }
    }
}
