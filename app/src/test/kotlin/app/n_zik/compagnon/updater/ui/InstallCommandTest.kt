package app.n_zik.compagnon.updater.ui

import app.n_zik.compagnon.updater.models.InstallMode
import app.n_zik.compagnon.updater.models.PackageManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Pins [installCommand] — the exact install command per install mode (spec `spec-updater` AD-4,
 * spec `spec-arch-binary-package-release` review loop 3 — L3-VG1): the command is shown and
 * copied, NEVER auto-run, so its exact text is a user-facing contract — a pacman `-U` regressed
 * to `-S`, or a broken quoting, would ship green without this pin.
 */
class InstallCommandTest {

    private val filePath = "/home/me/Downloads/n-zik-desktop-compagnon-0.0.2-1-x86_64.pkg.tar.zst"

    @Test
    fun `the flatpak command is the sandbox install command`() {
        // The probe result is irrelevant to the flatpak mode (its runtime is the sandbox, not
        // the system package manager)
        assertEquals("flatpak install \"$filePath\"", installCommand(InstallMode.FLATPAK, null, filePath))
        assertEquals("flatpak install \"$filePath\"", installCommand(InstallMode.FLATPAK, PackageManager.DEB, filePath))
    }

    @Test
    fun `the package managed command matches the probed package manager`() {
        assertEquals("sudo apt install \"$filePath\"", installCommand(InstallMode.PACKAGE_MANAGED, PackageManager.DEB, filePath))
        assertEquals("sudo dnf install \"$filePath\"", installCommand(InstallMode.PACKAGE_MANAGED, PackageManager.RPM, filePath))
        assertEquals("sudo pacman -U \"$filePath\"", installCommand(InstallMode.PACKAGE_MANAGED, PackageManager.AUR, filePath))
    }

    @Test
    fun `the modes without a command show the manual hint instead`() {
        // The interactive Windows install, the manual-replacement modes, and the probe-failed
        // package-managed fallback (no command to show or copy — the card shows the manual hint)
        assertNull(installCommand(InstallMode.WINDOWS, null, filePath))
        assertNull(installCommand(InstallMode.APPIMAGE, null, filePath))
        assertNull(installCommand(InstallMode.PORTABLE, null, filePath))
        assertNull(installCommand(InstallMode.PACKAGE_MANAGED, PackageManager.NONE, filePath))
        assertNull(installCommand(InstallMode.PACKAGE_MANAGED, null, filePath))
    }
}
