package app.n_zik.compagnon.updater

import app.n_zik.compagnon.updater.models.ArtifactNames
import app.n_zik.compagnon.updater.models.InstallMode
import app.n_zik.compagnon.updater.models.PackageManager
import app.n_zik.compagnon.updater.models.RELEASE_MARKER_CONTENT
import app.n_zik.compagnon.updater.models.appRootOfResolvedPath
import app.n_zik.compagnon.updater.models.currentDistributionMarker
import app.n_zik.compagnon.updater.models.currentInstallMode
import app.n_zik.compagnon.updater.models.currentIsAurBlocked
import app.n_zik.compagnon.updater.models.detectInstallMode
import app.n_zik.compagnon.updater.models.distributionMarkerAt
import app.n_zik.compagnon.updater.models.isAurBlocked
import app.n_zik.compagnon.updater.models.liveExecutablePath
import app.n_zik.compagnon.updater.models.probePackageManager
import app.n_zik.compagnon.updater.models.updaterEffectivelyEnabled
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * Pins the install-mode detection and the per-mode asset names (spec `spec-updater`, AD-4): the pure
 * [detectInstallMode] (Windows wins, then the Flatpak environment, then the package-managed `/opt`
 * path, else portable) and [ArtifactNames.forMode] (the channel-suffixed asset names of the
 * release, `null` for the pacman / no-probe installs that carry no binary asset).
 */
class InstallModeDetectionTest {

    private val env = emptyMap<String, String>()
    private val LINUX = "Linux"

    @Test
    fun `a windows host is always windows`() {
        // The env / path inputs are irrelevant on Windows (the silent in-place install)
        assertEquals(
            InstallMode.WINDOWS,
            detectInstallMode("Windows 11", mapOf("FLATPAK_ID" to "app.com"), "/opt/n-zik-desktop-compagnon/bin/app"),
        )
    }

    @Test
    fun `the flatpak environment wins over every path`() {
        assertEquals(
            InstallMode.FLATPAK,
            detectInstallMode(LINUX, mapOf("FLATPAK_ID" to "com.nzik.desktop.compagnon"), "/usr/bin/something"),
        )
        assertEquals(
            InstallMode.FLATPAK,
            detectInstallMode(LINUX, mapOf("container" to "flatpak"), "/tmp/.mount_xyz/app"),
        )
        // Blank env values do not count
        assertEquals(
            InstallMode.PORTABLE,
            detectInstallMode(LINUX, mapOf("FLATPAK_ID" to "", "container" to " "), "/home/me/app/app"),
        )
    }

    @Test
    fun `a non-flatpak container is not flatpak`() {
        // Loop 2: the `container` marker must be EXACTLY "flatpak" — a docker / podman host is a
        // plain portable install, not a Flatpak
        assertEquals(
            InstallMode.PORTABLE,
            detectInstallMode(LINUX, mapOf("container" to "docker"), "/home/me/app/app"),
        )
        assertEquals(
            InstallMode.PORTABLE,
            detectInstallMode(LINUX, mapOf("container" to "Flatpak"), "/home/me/app/app"),
        )
    }

    @Test
    fun `the package managed path is package managed`() {
        // The .deb / .rpm / AUR entries all share the single /opt location
        assertEquals(
            InstallMode.PACKAGE_MANAGED,
            detectInstallMode(LINUX, env, "/opt/n-zik-desktop-compagnon/bin/N-Zik Desktop Compagnon"),
        )
        // The dev channel installs under its OWN /opt base (spec `spec-updater`, AD-8)
        assertEquals(
            InstallMode.PACKAGE_MANAGED,
            detectInstallMode(LINUX, env, "/opt/n-zik-desktop-compagnon-dev/bin/N-Zik Desktop Compagnon DEV"),
        )
    }

    @Test
    fun `any other linux path is portable`() {
        assertEquals(
            InstallMode.PORTABLE,
            detectInstallMode(LINUX, env, "/home/me/downloads/n-zik/N-Zik Desktop Compagnon"),
        )
        assertEquals(
            InstallMode.PORTABLE,
            detectInstallMode(LINUX, env, ""),
        )
    }

    @Test
    fun `an appimage mount path falls through to portable`() {
        // The 2026-10-09 rework removed the APPIMAGE mode from detection (spec
        // `spec-channel-version-naming`): a legacy AppImage mount (`/tmp/.mount_*`) is no longer
        // recognized — it falls through to the portable/generic path, so a legacy AppImage install
        // gets the portable-zip gesture (accepted consequence, the "pas besoin" family: the
        // releases no longer carry an AppImage asset)
        assertEquals(
            InstallMode.PORTABLE,
            detectInstallMode(LINUX, env, "/tmp/.mount_NZikDE/x86_64/N-Zik Desktop Compagnon"),
        )
    }

    @Test
    fun `the asset name per install mode follows the channel-suffixed convention`() {
        val version = "0.0.2-beta"
        // The 2026-10-09 naming rework / AD-8: the published .exe is the channel's Linux base
        // (the CI renames the jpackage output — the spaced product name never appears in the
        // asset name); the other assets carry the channel suffix after the base version
        assertEquals("n-zik-desktop-compagnon-$version.exe", ArtifactNames.forMode(InstallMode.WINDOWS, version, PackageManager.NONE))
        assertEquals("n-zik-desktop-compagnon-$version-x86_64.flatpak", ArtifactNames.forMode(InstallMode.FLATPAK, version, PackageManager.NONE))
        assertEquals("n-zik-desktop-compagnon_$version-1_amd64.deb", ArtifactNames.forMode(InstallMode.PACKAGE_MANAGED, version, PackageManager.DEB))
        assertEquals("n-zik-desktop-compagnon-$version-1.x86_64.rpm", ArtifactNames.forMode(InstallMode.PACKAGE_MANAGED, version, PackageManager.RPM))
        assertEquals("n-zik-desktop-compagnon-$version-linux-portable.zip", ArtifactNames.forMode(InstallMode.PORTABLE, version, PackageManager.NONE))
    }

    @Test
    fun `pacman and a failed probe carry no binary asset`() {
        // pacman builds from source: the dialog shows the AUR entry hint instead of a download
        assertNull(ArtifactNames.forMode(InstallMode.PACKAGE_MANAGED, "0.0.2-beta", PackageManager.AUR))
        assertNull(ArtifactNames.forMode(InstallMode.PACKAGE_MANAGED, "0.0.2-beta", PackageManager.NONE))
    }

    @Test
    fun `the package manager probe maps the answering binary`() {
        // The probe is injected (no real binaries in the test): the first answer wins, in the apt / dnf / pacman order
        assertEquals(PackageManager.DEB, probePackageManager(probe = { it == "apt" }))
        assertEquals(PackageManager.RPM, probePackageManager(probe = { it == "dnf" }))
        assertEquals(PackageManager.AUR, probePackageManager(probe = { it == "pacman" }))
        assertEquals(PackageManager.NONE, probePackageManager(probe = { false }))
        // dnf answers even when apt does not (the probe order is fixed)
        assertEquals(PackageManager.RPM, probePackageManager(probe = { it == "dnf" || it == "pacman" }))
    }

    // ---- spec `spec-arch-binary-package-release`: the release marker + the AUR block ----

    @Test
    fun `a marked pacman install gets the arch package asset`() {
        // The 6th asset: the pacman binary package, offered only when the install carries the
        // release marker (a release-pkg install)
        assertEquals(
            "n-zik-desktop-compagnon-0.0.2-beta-1-x86_64.pkg.tar.zst",
            ArtifactNames.forMode(InstallMode.PACKAGE_MANAGED, "0.0.2-beta", PackageManager.AUR, isReleasePackage = true),
        )
        // The dev channel carries its own package base (AD-8) in the asset name
        assertEquals(
            "n-zik-desktop-compagnon-dev-0.0.2-dev-20261008-1-x86_64.pkg.tar.zst",
            ArtifactNames.forMode(InstallMode.PACKAGE_MANAGED, "0.0.2-dev-20261008", PackageManager.AUR, isReleasePackage = true),
        )
        // The archPkg helper itself (the default channel inference from the version suffix)
        assertEquals(
            "n-zik-desktop-compagnon-0.0.2-beta-1-x86_64.pkg.tar.zst",
            ArtifactNames.archPkg("0.0.2-beta"),
        )
        // An AUR install (no marker) has no binary asset — its updater is blocked before any
        // check, so no asset is ever selected for it
        assertNull(ArtifactNames.forMode(InstallMode.PACKAGE_MANAGED, "0.0.2-beta", PackageManager.AUR, isReleasePackage = false))
        // The other modes are untouched by the marker
        assertEquals(
            "n-zik-desktop-compagnon_0.0.2-beta-1_amd64.deb",
            ArtifactNames.forMode(InstallMode.PACKAGE_MANAGED, "0.0.2-beta", PackageManager.DEB, isReleasePackage = true),
        )
        assertEquals(
            "n-zik-desktop-compagnon-0.0.2-beta-linux-portable.zip",
            ArtifactNames.forMode(InstallMode.PORTABLE, "0.0.2-beta", PackageManager.NONE, isReleasePackage = true),
        )
    }

    @Test
    fun `the aur block requires pacman and a package managed install without marker`() {
        // The decision matrix (spec `spec-arch-binary-package-release`): a package-managed install
        // on a pacman host WITHOUT the release marker is an AUR install — the updater is blocked.
        assertTrue(isAurBlocked(InstallMode.PACKAGE_MANAGED, null, { PackageManager.AUR }))
        assertFalse(isAurBlocked(InstallMode.PACKAGE_MANAGED, null, { PackageManager.DEB }))
        assertFalse(isAurBlocked(InstallMode.PACKAGE_MANAGED, null, { PackageManager.RPM }))
        assertFalse(isAurBlocked(InstallMode.PACKAGE_MANAGED, null, { PackageManager.NONE }))
        // A release-pkg install (the marker present) is NOT blocked — even on pacman
        assertFalse(isAurBlocked(InstallMode.PACKAGE_MANAGED, "github-release", { PackageManager.AUR }))
        // A non package-managed mode is never blocked, even on pacman
        assertFalse(isAurBlocked(InstallMode.PORTABLE, null, { PackageManager.AUR }))
        assertFalse(isAurBlocked(InstallMode.WINDOWS, null, { PackageManager.AUR }))
        assertFalse(isAurBlocked(InstallMode.FLATPAK, null, { PackageManager.AUR }))
    }

    @Test
    fun `the marker short-circuits the probe`() {
        // Loop 2 G13: the marker is checked BEFORE the probe — a marked install (release pkg) and
        // every non package-managed mode must never spawn the apt / dnf / pacman probes
        var probed = false
        val probe = { probed = true; PackageManager.AUR }
        isAurBlocked(InstallMode.PACKAGE_MANAGED, "github-release", probe)
        isAurBlocked(InstallMode.PORTABLE, null, probe)
        isAurBlocked(InstallMode.WINDOWS, null, probe)
        assertFalse(probed, "the probe must not spawn when the marker or the mode short-circuits")
    }

    @Test
    fun `the live composition is testable through the injectable defaults`() {
        // currentIsAurBlocked / updaterEffectivelyEnabled with injected parameters: the production
        // composition (currentInstallMode / currentDistributionMarker / probePackageManager) is the
        // same wiring the live gates use — an inversion of the marker negation would fail here
        assertTrue(currentIsAurBlocked(installMode = InstallMode.PACKAGE_MANAGED, marker = null, probe = { PackageManager.AUR }))
        assertFalse(currentIsAurBlocked(installMode = InstallMode.PACKAGE_MANAGED, marker = "github-release", probe = { PackageManager.AUR }))
        assertFalse(currentIsAurBlocked(installMode = InstallMode.PORTABLE, marker = null, probe = { PackageManager.AUR }))

        assertTrue(updaterEffectivelyEnabled(buildEnabled = true, aurBlocked = false))
        assertFalse(updaterEffectivelyEnabled(buildEnabled = true, aurBlocked = true))
        assertFalse(updaterEffectivelyEnabled(buildEnabled = false, aurBlocked = false))
        assertFalse(updaterEffectivelyEnabled(buildEnabled = false, aurBlocked = true))
    }

    // ---- the live executable-path resolution (loop 2 G9) ----

    @Test
    fun `the app root of a resolved launcher path is the opt app dir`() {
        // A package-managed launcher lives at <appRoot>/bin/<launcher> — the parent dir named
        // `bin` gives the app root, where the distribution.txt marker lives
        assertEquals("/opt/n-zik-desktop-compagnon", appRootOfResolvedPath("/opt/n-zik-desktop-compagnon/bin/N-Zik Desktop Compagnon"))
        assertEquals("/opt/n-zik-desktop-compagnon-dev", appRootOfResolvedPath("/opt/n-zik-desktop-compagnon-dev/bin/N-Zik Desktop Compagnon DEV"))
        // The UNRESOLVED launch path (/usr/bin/<pkg> — the symlink the .desktop Exec carries): the
        // parent is /usr, so no marker can be read from it — this is the loop-2 bug: the
        // production launch carries the SYMLINK path in its own cmdline
        assertEquals("/usr", appRootOfResolvedPath("/usr/bin/n-zik-desktop-compagnon"))
        // Paths whose parent is not a bin/ dir have no app root
        assertNull(appRootOfResolvedPath("/home/me/app/N-Zik Desktop Compagnon"))
        assertNull(appRootOfResolvedPath(""))
    }

    @Test
    fun `the marker is read from the resolved app root only`(@TempDir root: File) {
        // A real symlinked layout (the packageArch staging shape): usr/bin/<pkg> ->
        // opt/<pkg>/bin/<launcher>, the marker at opt/<pkg>/distribution.txt. Symlink creation is
        // refused on a host without the privilege (Windows developer mode) — the test fails
        // LOUD with the creation error, it never silently skips.
        val pkg = "n-zik-desktop-compagnon"
        val optRoot = File(root, "opt/$pkg")
        val binDir = File(optRoot, "bin")
        binDir.mkdirs()
        val launcher = File(binDir, "N-Zik Desktop Compagnon")
        launcher.writeText("launcher")
        File(optRoot, "distribution.txt").writeText("github-release")
        val usrBin = File(root, "usr/bin")
        usrBin.mkdirs()
        val symlink = File(usrBin, pkg)
        Files.createSymbolicLink(symlink.toPath(), launcher.toPath())

        // The kernel-resolved path (what liveExecutablePath returns on Linux): the root is the
        // opt app dir and the marker is read
        val resolved = Paths.get(symlink.absolutePath).toRealPath()
        assertEquals(launcher.toPath(), resolved)
        // The contract of [appRootOfResolvedPath] is POSIX-normalized (it replaces the
        // backslashes) — the Windows absolute path of the temp dir must be normalized to the
        // same separators before the comparison (on Linux the two are identical as-is)
        assertEquals(optRoot.absolutePath.replace('\\', '/'), appRootOfResolvedPath(resolved.toString()))
        assertEquals("github-release", distributionMarkerAt(resolved.toString()))

        // The UNRESOLVED launch path (the symlink itself, what ProcessHandle.command() returns):
        // the root resolves to usr/ — no marker there, so an unresolved read would see no marker
        assertNull(distributionMarkerAt(symlink.absolutePath))

        // Any launcher inside the same app root sees the marker (it belongs to the root, not the
        // launcher file)
        val secondLauncher = File(optRoot, "bin/aur")
        secondLauncher.writeText("launcher")
        assertEquals("github-release", distributionMarkerAt(secondLauncher.absolutePath))
        // A blank marker is equivalent to absent (an AUR install carries the file not)
        File(optRoot, "distribution.txt").writeText("   ")
        assertNull(distributionMarkerAt(launcher.absolutePath))
    }

    @Test
    fun `the marker reader is strict on content`(@TempDir root: File) {
        // The reader accepts EXACTLY the release marker content (spec
        // `spec-arch-binary-package-release`, review loop 3 — L3-EC2): a stray or forged
        // distribution.txt with any other content must read as NO marker (the write side is
        // equally strict — packageArch fails the build on any other content). No symlink needed
        // (the reader is pure over the path) — this runs on any host, developer mode or not.
        val optRoot = File(root, "opt/n-zik-desktop-compagnon")
        File(optRoot, "bin").mkdirs()
        val launcher = File(optRoot, "bin/N-Zik Desktop Compagnon")
        launcher.writeText("launcher")
        val marker = File(optRoot, "distribution.txt")

        marker.writeText(RELEASE_MARKER_CONTENT)
        assertEquals(RELEASE_MARKER_CONTENT, distributionMarkerAt(launcher.absolutePath))
        // The build side writes it with a trailing newline — the read trims
        marker.writeText("$RELEASE_MARKER_CONTENT\n")
        assertEquals(RELEASE_MARKER_CONTENT, distributionMarkerAt(launcher.absolutePath))
        // Any OTHER content is not the marker (a stray file must not unblock the updater)
        marker.writeText("aur")
        assertNull(distributionMarkerAt(launcher.absolutePath))
        // Blank is not the marker either (an AUR install carries the file not)
        marker.writeText("   ")
        assertNull(distributionMarkerAt(launcher.absolutePath))
        // Absent is not the marker
        marker.delete()
        assertNull(distributionMarkerAt(launcher.absolutePath))
    }

    @Test
    fun `the resolved path is what the detection runs on`() {
        // The composition the production gate uses: detectInstallMode over the RESOLVED path
        // (the loop-2 fix — over the unresolved /usr/bin path, an /opt install would be
        // misdetected PORTABLE)
        assertEquals(
            InstallMode.PACKAGE_MANAGED,
            detectInstallMode("Linux", emptyMap(), "/opt/n-zik-desktop-compagnon/bin/N-Zik Desktop Compagnon"),
        )
        // The unresolved launch path of that same install (before the loop-2 fix)
        assertEquals(
            InstallMode.PORTABLE,
            detectInstallMode("Linux", emptyMap(), "/usr/bin/n-zik-desktop-compagnon"),
        )
    }

    @Test
    fun `the live entry points are the pure helpers over the live data`() {
        // The live composition (spec `spec-arch-binary-package-release` G9, review loop 3 —
        // L3-VG2): the pure helpers are pinned above; this pins the wiring — that the live
        // kernel-resolved path actually feeds [detectInstallMode] / [distributionMarkerAt] (a
        // resolution regression would mis-detect a release-pkg install as PORTABLE with a null
        // marker, and nothing else would catch it).
        val resolved = liveExecutablePath()
        assertTrue(resolved.isNotBlank(), "liveExecutablePath must not be empty")
        assertEquals(
            detectInstallMode(System.getProperty("os.name", ""), System.getenv(), resolved),
            currentInstallMode(),
        )
        assertEquals(distributionMarkerAt(resolved), currentDistributionMarker())

        if (Files.isSymbolicLink(Paths.get("/proc/self/exe"))) {
            // Linux: the kernel-resolved path is NOT the launch symlink — toRealPath walked the
            // full chain (the production launch goes through the /usr/bin/<pkg> symlink)
            assertFalse(Files.isSymbolicLink(Paths.get(resolved)))
        } else if (!Files.exists(Paths.get("/proc/self/exe"))) {
            // Non-Linux hosts: the fallback is the command-line path itself
            // (ProcessHandle.command())
            assertEquals(ProcessHandle.current().info().command().orElse(""), resolved)
        }
    }
}
