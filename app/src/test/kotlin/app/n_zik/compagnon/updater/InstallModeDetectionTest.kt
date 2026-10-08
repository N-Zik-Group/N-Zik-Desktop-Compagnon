package app.n_zik.compagnon.updater

import app.n_zik.compagnon.updater.models.ArtifactNames
import app.n_zik.compagnon.updater.models.InstallMode
import app.n_zik.compagnon.updater.models.PackageManager
import app.n_zik.compagnon.updater.models.detectInstallMode
import app.n_zik.compagnon.updater.models.probePackageManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Pins the install-mode detection and the per-mode asset names (spec `spec-updater`, AD-4): the pure
 * [detectInstallMode] (Windows wins, then the Flatpak environment, then the AppImage mount, then the
 * package-managed `/opt` path, else portable) and [ArtifactNames.forMode] (the channel-suffixed asset
 * names of the release, `null` for the pacman / no-probe installs that carry no binary asset).
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
    fun `the appimage mount path is appimage`() {
        assertEquals(
            InstallMode.APPIMAGE,
            detectInstallMode(LINUX, env, "/tmp/.mount_NZikDE/x86_64/N-Zik Desktop Compagnon"),
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
            detectInstallMode(LINUX, env, "/opt/n-zik-desktop-compagnon-dev/bin/N-Zik Desktop Compagnon (Dev)"),
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
    fun `the asset name per install mode follows the channel-suffixed convention`() {
        val version = "0.0.2-beta"
        // The observed jpackage convention (no architecture suffix): base name + version + extension
        // Loop 2 / AD-8 (documented deviation from the v1 pin): the beta installer carries the
        // per-channel product name ("… (Beta)" — the jpackage app name the build sets)
        assertEquals("N-Zik.Desktop.Compagnon.Beta.-$version.exe", ArtifactNames.forMode(InstallMode.WINDOWS, version, PackageManager.NONE))
        assertEquals("n-zik-desktop-compagnon-$version-x86_64.flatpak", ArtifactNames.forMode(InstallMode.FLATPAK, version, PackageManager.NONE))
        assertEquals("n-zik-desktop-compagnon_$version-1_amd64.deb", ArtifactNames.forMode(InstallMode.PACKAGE_MANAGED, version, PackageManager.DEB))
        assertEquals("n-zik-desktop-compagnon-$version-1.x86_64.rpm", ArtifactNames.forMode(InstallMode.PACKAGE_MANAGED, version, PackageManager.RPM))
        assertEquals("n-zik-desktop-compagnon-$version-x86_64.AppImage", ArtifactNames.forMode(InstallMode.APPIMAGE, version, PackageManager.NONE))
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
}
