package app.n_zik.compagnon

import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The Linux packaging contract lives in build config that no other test path touches: a plugin
 * upgrade that stops forwarding the jpackage `freeArgs` would ship a `.deb`/`.rpm` WITHOUT the
 * `vlc` dependency (the core contract — no Linux package bundles VLC), and a renamed
 * `nzikPackageName` would desynchronize the `/opt` dir from the portable zip name, the AUR
 * `pkgname`s and the download URL — all with a green build. The build exposes the effective values
 * (`systemProperty` in `app/build.gradle.kts`, same pattern as `install.upgradeUuid`) so this test
 * pins them.
 */
class LinuxPackagePinTest {

    private val releasePkgbuild = File("../packaging/aur/PKGBUILD")
    private val gitPkgbuild = File("../packaging/aur-git/PKGBUILD")

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
        assertEquals("n-zik-desktop-compagnon", pkgName(), "the catalog-derived linux.packageName is the frozen install identity")
        val zip = System.getProperty("linux.portable.zipName") ?: error("the portable zip archiveFileName is not exposed to the tests")
        assertTrue(zip.startsWith("${pkgName()}-"), "the portable zip base name must be the package name: $zip")
        assertTrue(zip.endsWith("-linux-portable.zip"), "the portable zip keeps its -linux-portable.zip suffix: $zip")
    }

    @Test
    fun `the package name matches the AUR pkgnames and the download URL`() {
        // Release entry: pkgname is the package identity.
        val releaseName = releasePkgbuild.readText().lineSequence()
            .firstOrNull { it.startsWith("pkgname=") }?.removePrefix("pkgname=")
            ?: error("release entry: the pkgname= line is missing")
        assertEquals(pkgName(), releaseName, "the release AUR pkgname must be the jpackage package name")
        // Git entry: pkgname carries the -git suffix; the install identity is _pkgname.
        val gitName = gitPkgbuild.readText().lineSequence()
            .firstOrNull { it.startsWith("_pkgname=") }?.removePrefix("_pkgname=")
            ?: error("git entry: the _pkgname= line is missing")
        assertEquals(pkgName(), gitName, "the git AUR install identity (_pkgname) must be the jpackage package name")
        // The release entry downloads the zip from the release; the URL's package base must be the same name.
        val urlBase = Regex("releases/download/v\\$\\{pkgver\\}/([a-z0-9-]+)-\\$\\{pkgver\\}-linux-portable\\.zip")
            .find(releasePkgbuild.readText())?.groupValues?.get(1)
            ?: error("release entry: the portable-zip download URL is missing")
        assertEquals(pkgName(), urlBase, "the release download URL must name the portable zip after the package name")
    }
}
