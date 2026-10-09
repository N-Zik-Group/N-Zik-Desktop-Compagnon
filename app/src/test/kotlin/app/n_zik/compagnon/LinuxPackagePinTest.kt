package app.n_zik.compagnon

import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The Linux packaging contract lives in build config that no other test path touches: a plugin
 * upgrade that stops forwarding the jpackage `freeArgs` would ship a `.deb`/`.rpm` WITHOUT the
 * `vlc` dependency (the core contract — the `.deb`/`.rpm`/AUR/portable paths never bundle VLC;
 * only the Flatpak embeds the runtime, into its own staging), and a renamed `nzikPackageName`
 * would desynchronize the `/opt` dir from the portable zip name, the AUR `pkgname`s and the
 * download URL — all with a green build.
 * The build exposes the effective values (`systemProperty` in `app/build.gradle.kts`, same pattern
 * as `windows.packageName`) so this test pins them. Channel-aware: the dev channel keeps its OWN
 * package base (`n-zik-desktop-compagnon-dev`, AD-8) and every artifact name carries the channel
 * suffix after the base version — `build.sh package beta|dev` runs `:app:test -Pchannel=…`, so a
 * channel-blind pin reds those builds.
 */
class LinuxPackagePinTest {

    private val releasePkgbuild = File("../packaging/aur/PKGBUILD")
    private val gitPkgbuild = File("../packaging/aur-git/PKGBUILD")

    /** The channel-aware package name the running build generates (dev: its own `-dev` base, AD-8). */
    private fun pkgName(): String =
        System.getProperty("linux.packageName") ?: error("linux.packageName is not exposed to the tests")

    @Test
    fun `both jpackage tasks carry the vlc dependency and the Audio menu group`() {
        // The plugin exposes no `depends` in the linux { } DSL and never passes jpackage's
        // --linux-package-deps itself: the args are injected through freeArgs, so pin the effective
        // args (joined with the unit separator, exposed that way by the build).
        for ((prop, label) in listOf("linux.freeArgs.deb" to ".deb", "linux.freeArgs.rpm" to ".rpm")) {
            val args = System.getProperty(prop) ?: error("$label: freeArgs are not exposed to the tests")
            assertTrue(
                args.contains("--linux-package-deps\u001fvlc"),
                "$label: jpackage must be passed --linux-package-deps vlc (the package declares the system vlc)"
            )
            assertTrue(
                args.contains("--linux-menu-group\u001fAudio;"),
                "$label: jpackage must be passed --linux-menu-group Audio; (the .desktop menu category)"
            )
        }
    }

    @Test
    fun `the package name is frozen and matches the portable zip base`() {
        // The dev channel keeps its OWN package base (AD-8 — a parallel product): the frozen
        // install identity is the base name, dev adds its -dev suffix
        assertEquals(
            "n-zik-desktop-compagnon",
            pkgName().removeSuffix("-dev"),
            "the catalog-derived linux.packageName base is the frozen install identity (dev adds its -dev suffix)",
        )
        val zip = System.getProperty("linux.portable.zipName") ?: error("the portable zip archiveFileName is not exposed to the tests")
        assertTrue(zip.startsWith("${pkgName()}-"), "the portable zip base name must be the channel-aware package name: $zip")
        assertTrue(zip.endsWith("-linux-portable.zip"), "the portable zip keeps its -linux-portable.zip suffix: $zip")
    }

    private fun pinProp(name: String): String =
        System.getProperty(name) ?: error("$name is not exposed to the tests")

    private val sha256Regex = Regex("[0-9a-f]{64}")

    private fun assertRealSha256(prop: String, value: String) {
        assertTrue(sha256Regex.matches(value), "$prop: not a SHA-256 hex string: $value")
        assertTrue(value.toSet().size > 1, "$prop: still the placeholder (all-zeros) SHA-256 — pin the real hash")
    }

    @Test
    fun `the Linux VLC tarball is pinned by url name and real sha256`() {
        // The Flatpak is the only Linux artifact that embeds the runtime, so the tarball pin is
        // exposed under the shared `linux.tarball.*` names.
        val url = pinProp("linux.tarballUrl")
        val name = pinProp("linux.tarballName")
        val sha = pinProp("linux.tarballSha256")
        assertTrue(url.startsWith("https://github.com/N-Zik-Group/N-Zik-Desktop-Compagnon/releases/download/"),
            "the Linux tarball must be downloaded from the project's GitHub releases (the release asset it is published as): $url")
        assertTrue(url.endsWith("/$name"), "the tarball URL must point at the pinned file: $url vs $name")
        assertTrue(Regex("vlc-\\d+\\.\\d+\\.\\d+-linux-x64\\.tar\\.gz").matches(name),
            "the tarball name keeps the vlc-<version>-linux-x64.tar.gz shape: $name")
        assertRealSha256("linux.tarballSha256", sha)
    }

    @Test
    fun `the package name matches the AUR pkgnames and the download URL`() {
        // The committed AUR entries are STABLE-ONLY (the dev channel is a parallel product — its
        // own -dev package base): compare against the base name, whatever the channel is.
        val baseName = pkgName().removeSuffix("-dev")
        // Release entry: pkgname is the package identity.
        val releaseName = releasePkgbuild.readText().lineSequence()
            .firstOrNull { it.startsWith("pkgname=") }?.removePrefix("pkgname=")
            ?: error("release entry: the pkgname= line is missing")
        assertEquals(baseName, releaseName, "the release AUR pkgname must be the jpackage package name")
        // Git entry: pkgname carries the -git suffix; the install identity is _pkgname.
        val gitName = gitPkgbuild.readText().lineSequence()
            .firstOrNull { it.startsWith("_pkgname=") }?.removePrefix("_pkgname=")
            ?: error("git entry: the _pkgname= line is missing")
        assertEquals(baseName, gitName, "the git AUR install identity (_pkgname) must be the jpackage package name")
        // The release entry downloads the zip from the release; the URL's package base must be the same name.
        val urlBase = Regex("releases/download/v\\$\\{pkgver\\}/([a-z0-9-]+)-\\$\\{pkgver\\}-linux-portable\\.zip")
            .find(releasePkgbuild.readText())?.groupValues?.get(1)
            ?: error("release entry: the portable-zip download URL is missing")
        assertEquals(baseName, urlBase, "the release download URL must name the portable zip after the package name")
    }
}
