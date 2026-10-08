package app.n_zik.compagnon.updater

import app.n_zik.compagnon.updater.models.ArtifactNames
import app.n_zik.compagnon.updater.models.InstallMode
import app.n_zik.compagnon.updater.models.PackageManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * The per-channel artifact names and product names (spec `spec-updater`, AD-8, loop 2): the
 * channel inferred from the version suffix, the per-channel product name ("… (Beta)" / "… (Dev)"
 * — the jpackage app name the build sets, mirrored here so the updater finds the renamed asset),
 * and the full set of six asset names per channel (the beta keeps the stable Linux base — the
 * beta REPLACES the stable in place; the dev carries its own package base, the parallel product).
 */
class ArtifactNamesChannelTest {

    @Test
    fun `the channel is inferred from the version suffix`() {
        assertEquals("stable", ArtifactNames.channelOf("0.0.2"))
        assertEquals("stable", ArtifactNames.channelOf("0.0.2.1"))
        assertEquals("beta", ArtifactNames.channelOf("0.0.2-beta"))
        assertEquals("dev", ArtifactNames.channelOf("0.0.2-dev-20261007"))
    }

    @Test
    fun `the product name is per channel`() {
        assertEquals("N-Zik Desktop Compagnon", ArtifactNames.productName("stable"))
        assertEquals("N-Zik Desktop Compagnon", ArtifactNames.productName("debug"))
        assertEquals("N-Zik Desktop Compagnon", ArtifactNames.productName("git"))
        assertEquals("N-Zik Desktop Compagnon (Beta)", ArtifactNames.productName("beta"))
        assertEquals("N-Zik Desktop Compagnon (Dev)", ArtifactNames.productName("dev"))
    }

    @Test
    fun `the stable names keep the plain byte-identical bases`() {
        val v = "0.0.2"
        assertEquals("N-Zik.Desktop.Compagnon-$v.exe", ArtifactNames.exe(v))
        assertEquals("n-zik-desktop-compagnon_$v-1_amd64.deb", ArtifactNames.deb(v))
        assertEquals("n-zik-desktop-compagnon-$v-1.x86_64.rpm", ArtifactNames.rpm(v))
        assertEquals("n-zik-desktop-compagnon-$v-linux-portable.zip", ArtifactNames.portableZip(v))
        assertEquals("n-zik-desktop-compagnon-$v-x86_64.AppImage", ArtifactNames.appImage(v))
        assertEquals("n-zik-desktop-compagnon-$v-x86_64.flatpak", ArtifactNames.flatpak(v))
    }

    @Test
    fun `the beta names carry the product name in the exe only`() {
        // AD-8: the beta replaces the stable in place — the same Linux identity (package name /
        // app-id / path), only the Windows installer carries the channel product name
        val v = "0.0.3-beta"
        assertEquals("N-Zik.Desktop.Compagnon.Beta.-$v.exe", ArtifactNames.exe(v))
        assertEquals("n-zik-desktop-compagnon_$v-1_amd64.deb", ArtifactNames.deb(v))
        assertEquals("n-zik-desktop-compagnon-$v-1.x86_64.rpm", ArtifactNames.rpm(v))
        assertEquals("n-zik-desktop-compagnon-$v-linux-portable.zip", ArtifactNames.portableZip(v))
        assertEquals("n-zik-desktop-compagnon-$v-x86_64.AppImage", ArtifactNames.appImage(v))
        assertEquals("n-zik-desktop-compagnon-$v-x86_64.flatpak", ArtifactNames.flatpak(v))
    }

    @Test
    fun `the dev names carry the dev product name and the dev linux package base`() {
        // AD-8: the dev is a parallel product — its own product name on Windows AND its own Linux
        // package base (n-zik-desktop-compagnon-dev → /opt/n-zik-desktop-compagnon-dev), for ALL
        // the linux artifacts
        val v = "0.0.3-dev-20261007"
        assertEquals("N-Zik.Desktop.Compagnon.Dev.-$v.exe", ArtifactNames.exe(v))
        assertEquals("n-zik-desktop-compagnon-dev_$v-1_amd64.deb", ArtifactNames.deb(v))
        assertEquals("n-zik-desktop-compagnon-dev-$v-1.x86_64.rpm", ArtifactNames.rpm(v))
        assertEquals("n-zik-desktop-compagnon-dev-$v-linux-portable.zip", ArtifactNames.portableZip(v))
        assertEquals("n-zik-desktop-compagnon-dev-$v-x86_64.AppImage", ArtifactNames.appImage(v))
        assertEquals("n-zik-desktop-compagnon-dev-$v-x86_64.flatpak", ArtifactNames.flatpak(v))
    }

    @Test
    fun `the per mode names follow the channel inference`() {
        val v = "0.0.3-dev-20261007"
        assertEquals("N-Zik.Desktop.Compagnon.Dev.-$v.exe", ArtifactNames.forMode(InstallMode.WINDOWS, v, PackageManager.NONE))
        assertEquals("n-zik-desktop-compagnon-dev_$v-1_amd64.deb", ArtifactNames.forMode(InstallMode.PACKAGE_MANAGED, v, PackageManager.DEB))
        assertEquals("n-zik-desktop-compagnon-dev-$v-x86_64.flatpak", ArtifactNames.forMode(InstallMode.FLATPAK, v, PackageManager.NONE))
        // pacman builds from source: no binary asset (the dialog shows the AUR entry hint)
        assertNull(ArtifactNames.forMode(InstallMode.PACKAGE_MANAGED, v, PackageManager.AUR))
    }
}
