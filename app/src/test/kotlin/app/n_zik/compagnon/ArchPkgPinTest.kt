package app.n_zik.compagnon

import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The Arch binary package contract (spec `spec-arch-binary-package-release`) lives in build config
 * that no other test path touches: a drifted package name would desynchronize the `/opt` install
 * dir, the `/usr/bin` symlink, the `.desktop` / icon / LICENSE paths and the artifact name; a
 * PKGINFO that lost its `depend = vlc` would ship a package that cannot play (the system vlc is
 * never bundled — the same contract as the .deb/.rpm/AUR paths); and a PKGINFO with a keyword
 * pacman 7 does not know (the v1 `pkgrel` / `depends` / `pkgbuild`) would fail at install with a
 * green build. The layout is byte-identical to the release entry's `package()` in
 * `packaging/aur/PKGBUILD` (the layout's source of truth — the two can never drift, cross-checked
 * here against that file); the only addition is the provenance marker `distribution.txt`, the
 * single signal that distinguishes a release-pkg install from an AUR install for the in-app
 * updater. Channel-aware: `build.sh package beta|dev` runs `:app:test -Pchannel=…`, so every pin
 * must hold per channel (a channel-blind pin reds those builds). The build exposes the effective
 * values (`systemProperty` in `app/build.gradle.kts`, same pattern as `linux.packageName`).
 */
class ArchPkgPinTest {

    private val releasePkgbuild = File("../packaging/aur/PKGBUILD")

    private fun archProp(name: String): String =
        System.getProperty(name) ?: error("$name is not exposed to the tests")

    private val pkgName: String get() = archProp("arch.pkgname")

    /** The channel-aware in-app version the running build generates (stable/debug: the catalog base). */
    private val appVersionName: String
        get() = System.getProperty("appVersion.versionName")
            ?: error("appVersion.versionName is not exposed to the tests")

    /** The per-channel product name (the jpackage app name — the launcher the /usr/bin symlink targets). */
    private val channelDisplayName: String
        get() = System.getProperty("windows.packageName")
            ?: error("windows.packageName is not exposed to the tests")

    /** A frozen field of the release PKGBUILD (the layout's source of truth; arrays kept as their single element). */
    private fun pkgbuildField(name: String): String =
        releasePkgbuild.readText().lineSequence()
            .firstOrNull { it.startsWith("$name=") }?.removePrefix("$name=")?.trim()
            ?.trim('"')?.removePrefix("(")?.removeSuffix(")")
            ?: error("release PKGBUILD: the $name= line is missing")

    @Test
    fun `the artifact name is the channel-aware package name full version and release number`() {
        // The 7th asset: <package>-<full version WITH the channel suffix>-1-x86_64.pkg.tar.zst
        // (the channel rides in the package name — dev: the -dev base — and in the version)
        assertEquals(
            "$pkgName-$appVersionName-1-x86_64.pkg.tar.zst",
            archProp("arch.fileName"),
            "the Arch artifact name is <package>-<full version>-1-x86_64.pkg.tar.zst (channel-aware)",
        )
    }

    @Test
    fun `the pkgver is the full version with the mandatory dash release number`() {
        // PKGINFO v2: `pkgver` carries the FULL version `<base>[-channel suffix]-<pkgrel>` — the
        // release number is INSIDE the pkgver (there is no `pkgrel` keyword), and libalpm rejects
        // a version without the dash
        assertEquals("$appVersionName-1", archProp("arch.pkgver"))
        assertTrue(
            archProp("arch.pkgver").endsWith("-1"),
            "the pkgver ends with the dash release number (the dash is mandatory — libalpm)",
        )
    }

    @Test
    fun `the layout paths all derive from the package name`() {
        // The layout is the PKGBUILD's package(): the app-image in /opt/<pkg>, the space-free
        // launcher symlink in /usr/bin/<pkg>, the .desktop, the icon and the LICENSE — all named
        // after the package name
        assertEquals("opt/$pkgName", archProp("arch.optRoot"))
        assertEquals("usr/bin/$pkgName", archProp("arch.symlinkPath"))
        assertEquals(
            "/opt/$pkgName/bin/$channelDisplayName",
            archProp("arch.symlinkTarget"),
            "the /usr/bin symlink targets the app's bin/ launcher (named after the channel display name)",
        )
        assertEquals("usr/share/applications/$pkgName.desktop", archProp("arch.desktopPath"))
        assertEquals("usr/share/icons/hicolor/256x256/apps/$pkgName.png", archProp("arch.iconPath"))
        assertEquals("usr/share/licenses/$pkgName/LICENSE", archProp("arch.licensePath"))
    }

    @Test
    fun `the pkginfo is the v2 schema in the makepkg keyword order with only the two runtime placeholders`() {
        val lines = String(java.util.Base64.getDecoder().decode(archProp("arch.pkginfoContent")))
            .trimEnd('\n').split("\n")
        assertEquals(
            listOf(
                "pkgname = $pkgName",
                "pkgbase = $pkgName",
                "xdata = pkgtype=pkg",
                "pkgver = $appVersionName-1",
                "pkgdesc = ${pkgbuildField("pkgdesc")}",
                "arch = x86_64",
                "url = ${pkgbuildField("url")}",
                "license = ${pkgbuildField("license")}",
                "depend = vlc",
                "builddate = __BUILDDATE__",
                "packager = N-Zik Group CI",
                "size = __SIZE__",
            ),
            lines,
            "the .PKGINFO is the PKGINFO v2 schema in the exact makepkg keyword order (only builddate/size are runtime-substituted)",
        )
        // The v1 keywords are not accepted by pacman 7 — a regression would fail the install with
        // a green build
        assertFalse(
            lines.any { it.startsWith("pkgrel") || it.startsWith("depends") || it.startsWith("pkgbuild") },
            "the v1 PKGINFO keywords (pkgrel / depends / pkgbuild) must be absent (PKGINFO v2)",
        )
    }

    @Test
    fun `the frozen package metadata matches the release pkgsbuild`() {
        // The release entry is the layout's source of truth: the frozen package metadata must
        // never drift from it. The entry is stable-only (the dev channel is a parallel product —
        // its pkgname rides the -dev suffix), so compare against the base name.
        assertEquals(
            pkgName.removeSuffix("-dev"),
            pkgbuildField("pkgname"),
            "the package name is the release AUR pkgname (the dev channel adds its own -dev suffix)",
        )
        assertEquals("GPL-3.0-only", pkgbuildField("license"), "the license is the frozen GPL-3.0-only")
        assertEquals(
            "vlc",
            pkgbuildField("depends"),
            "the AUR entry declares the system vlc (the binary package must too — no bundled runtime)",
        )
    }

    @Test
    fun `the provenance marker is pinned to the app root with the frozen content`() {
        // The marker is the single signal that distinguishes a release-pkg install from an AUR
        // install for the in-app updater: it lives in the app root (opt/<pkg>/distribution.txt)
        // and its content is the frozen literal
        assertEquals("opt/$pkgName/distribution.txt", archProp("arch.markerPath"))
        assertEquals("github-release", archProp("arch.markerContent"))
    }

    @Test
    fun `the desktop entry is the pkgsbuild one with the channel name`() {
        val desktop = String(java.util.Base64.getDecoder().decode(archProp("arch.desktopContent")))
        val fields = desktop.trimEnd('\n').lineSequence()
            .filter { it.contains('=') }
            .associate { val i = it.indexOf('='); it.substring(0, i) to it.substring(i + 1) }
        // The Name carries the channel display name (the jpackage app name — the launcher the
        // /usr/bin symlink resolves to); everything else is the PKGBUILD's verbatim
        assertEquals(channelDisplayName, fields["Name"], "the .desktop Name is the channel display name (the jpackage app name)")
        assertEquals("/usr/bin/$pkgName", fields["Exec"])
        assertEquals(pkgName, fields["Icon"])
        assertEquals("Audio;", fields["Categories"])
        assertEquals("Application", fields["Type"])
        assertEquals("false", fields["Terminal"])
        assertEquals(pkgbuildField("pkgdesc"), fields["Comment"])
    }
}
