package app.n_zik.compagnon

import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The Linux packaging contract lives in build config that no other test path touches: a plugin
 * upgrade that stops forwarding the jpackage `freeArgs` would ship a `.deb`/`.rpm` WITHOUT the
 * `vlc` dependency (the core contract — the `.deb`/`.rpm`/AUR/portable paths never bundle VLC; only
 * the AppImage and the Flatpak embed the runtime, each into its own staging), and a renamed `nzikPackageName` would desynchronize the `/opt`
 * dir from the portable zip name, the AUR `pkgname`s and the download URL — all with a green build.
 * The build exposes the effective values (`systemProperty` in `app/build.gradle.kts`, same pattern
 * as `windows.packageName`) so this test pins them.
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

    private fun appImageProp(name: String): String =
        System.getProperty(name) ?: error("$name is not exposed to the tests")

    private val sha256Regex = Regex("[0-9a-f]{64}")

    private fun assertRealSha256(prop: String, value: String) {
        assertTrue(sha256Regex.matches(value), "$prop: not a SHA-256 hex string: $value")
        assertTrue(value.toSet().size > 1, "$prop: still the placeholder (all-zeros) SHA-256 — pin the real hash")
    }

    @Test
    fun `the AppImage tarball is pinned by url name and real sha256`() {
        val url = appImageProp("linux.appImage.tarballUrl")
        val name = appImageProp("linux.appImage.tarballName")
        val sha = appImageProp("linux.appImage.tarballSha256")
        assertTrue(url.startsWith("https://github.com/N-Zik-Group/N-Zik-Desktop-Compagnon/releases/download/"),
            "the Linux tarball must be downloaded from the project's GitHub releases (the release asset it is published as): $url")
        assertTrue(url.endsWith("/$name"), "the tarball URL must point at the pinned file: $url vs $name")
        assertTrue(Regex("vlc-\\d+\\.\\d+\\.\\d+-linux-x64\\.tar\\.gz").matches(name),
            "the tarball name keeps the vlc-<version>-linux-x64.tar.gz shape: $name")
        assertRealSha256("linux.appImage.tarballSha256", sha)
    }

    @Test
    fun `the AppImage linuxdeploy is pinned by url version and real sha256`() {
        val url = appImageProp("linux.appImage.linuxdeployUrl")
        val sha = appImageProp("linux.appImage.linuxdeploySha256")
        // The version is the catalog's linuxdeploy-version (the dead-pin guard: the URL must carry
        // it, not just any linuxdeploy release) — read it from the toml like the frozen-name test.
        val toml = File("../gradle/libs.versions.toml")
        val version = Regex("""linuxdeploy-version\s*=\s*"?([^"\n]+)"?""")
            .find(toml.readText())?.groupValues?.get(1)
            ?: error("the catalog's linuxdeploy-version line is missing")
        assertTrue(url.startsWith("https://github.com/linuxdeploy/linuxdeploy/releases/download/"),
            "linuxdeploy must come from its official GitHub releases: $url")
        assertTrue(url.contains("/$version/"), "the linuxdeploy URL must pin the catalog's linuxdeploy version ($version): $url")
        assertTrue(url.endsWith("/linuxdeploy-x86_64.AppImage"), "the pinned linuxdeploy asset is the x86_64 AppImage: $url")
        assertRealSha256("linux.appImage.linuxdeploySha256", sha)
    }

    @Test
    fun `the AppImage file name is frozen to the package name and version`() {
        // The version is the catalog's nzikVersionName (shared with Windows) — read it from the toml
        // so the test pins the derivation, not a copy of the string.
        val toml = File("../gradle/libs.versions.toml")
        val version = Regex("""nzikVersionName\s*=\s*"?([^"\n]+)"?""")
            .find(toml.readText())?.groupValues?.get(1)
            ?: error("the catalog's nzikVersionName line is missing")
        val fileName = appImageProp("linux.appImage.fileName")
        assertEquals("${pkgName()}-$version-x86_64.AppImage", fileName,
            "the AppImage name is <package>-<version>-x86_64.AppImage (pinned through LDAI_OUTPUT in packageAppImage)")
    }

    @Test
    fun `the AppImage and the Flatpak are the only Linux artifacts that inject resources vlc`() {
        // The runtime goes into the AppDir's app resources dir (the cfg's $APPDIR/resources), and NO
        // other Linux path gets it: the .deb/.rpm/AUR/portable app-image keeps its system-vlc contract.
        val injection = appImageProp("linux.appImage.resourcesInjection")
        assertTrue(injection.endsWith("resources/vlc"), "the runtime lands in the app's resources/vlc dir: $injection")
        assertEquals("usr/lib/app/resources/vlc", injection,
            "the AppDir mapping keeps the app-image layout 1:1 (usr/lib/app = the app-image's lib/app)")
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
