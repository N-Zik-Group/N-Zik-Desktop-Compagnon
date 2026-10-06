package app.n_zik.compagnon.playback.vlc

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/** Unit tests of [VlcRuntime]'s platform split and the distro install hint; the native load stays in the opt-in trial. */
class VlcRuntimeTest {

    @Test
    fun `the embedded runtime is only targeted on windows`() {
        assertTrue(VlcRuntime.isEmbeddedPlatform("Windows 11"))
        assertTrue(VlcRuntime.isEmbeddedPlatform("windows"))
        assertFalse(VlcRuntime.isEmbeddedPlatform("Linux"))
        assertFalse(VlcRuntime.isEmbeddedPlatform("Mac OS X"))
    }

    @Test
    fun `linux loads the system libvlc without any embedded runtime`() {
        val availability = VlcRuntime.load("Linux", resourcesDir = null) { true }
            as VlcRuntime.Availability.Available
        assertEquals("system", availability.directory)
    }

    @Test
    fun `linux without any libvlc is unavailable with the system reason and an install hint`() {
        val unavailable = VlcRuntime.load("Linux", resourcesDir = null) { false }
            as VlcRuntime.Availability.Unavailable
        assertEquals("no libvlc on the system", unavailable.reason)
        assertTrue(unavailable.installHint != null)
    }

    @Test
    fun `a throwing discovery degrades to unavailable instead of propagating`() {
        val unavailable = VlcRuntime.load("Linux", resourcesDir = null) { throw UnsatisfiedLinkError("no native libvlc") }
            as VlcRuntime.Availability.Unavailable
        assertEquals("no libvlc on the system", unavailable.reason)
    }

    @Test
    fun `windows without the resources property is unavailable`() {
        val availability = VlcRuntime.load("Windows 11", resourcesDir = null) { true }
        assertTrue((availability as VlcRuntime.Availability.Unavailable).reason.contains("is not set"))
    }

    @Test
    fun `windows with a runtime dir missing the dll is unavailable`(@TempDir resources: File) {
        val availability = VlcRuntime.load("Windows 11", resourcesDir = resources.absolutePath) { true }
        assertTrue((availability as VlcRuntime.Availability.Unavailable).reason.contains("no libvlc.dll"))
    }

    @Test
    fun `windows with the embedded dll loads it`(@TempDir resources: File) {
        val runtime = File(resources, "vlc").apply { mkdirs() }
        File(runtime, "libvlc.dll").writeText("")
        val availability = withRestoredJnaLibraryPath {
            VlcRuntime.load("Windows 11", resourcesDir = resources.absolutePath) { true }
        }
        assertTrue(availability is VlcRuntime.Availability.Available)
    }

    @Test
    fun `windows with the embedded dll but a failed discovery is unavailable`(@TempDir resources: File) {
        val runtime = File(resources, "vlc").apply { mkdirs() }
        File(runtime, "libvlc.dll").writeText("")
        val availability = withRestoredJnaLibraryPath {
            VlcRuntime.load("Windows 11", resourcesDir = resources.absolutePath) { false }
        }
        assertEquals("NativeDiscovery found no libvlc", (availability as VlcRuntime.Availability.Unavailable).reason)
    }

    @Test
    fun `the install hint matches the distro family`() {
        assertEquals("sudo apt install vlc", VlcRuntime.vlcInstallHint(osRelease("ID=ubuntu", "ID_LIKE=debian")))
        assertEquals("sudo apt install vlc", VlcRuntime.vlcInstallHint(osRelease("ID=debian", "VERSION_ID=\"12\"")))
        assertEquals("sudo apt install vlc", VlcRuntime.vlcInstallHint(osRelease("ID=pop", "ID_LIKE=\"ubuntu debian\"")))
        assertEquals("sudo apt install vlc", VlcRuntime.vlcInstallHint(osRelease("ID=somespin", "ID_LIKE=debian")))
        assertEquals("sudo dnf install vlc", VlcRuntime.vlcInstallHint(osRelease("ID=fedora")))
        assertEquals("sudo dnf install vlc", VlcRuntime.vlcInstallHint(osRelease("ID=rocky", "ID_LIKE=\"rhel centos\"")))
        // Quoted ID_LIKE as the sole driver (the ID itself is not in any family).
        assertEquals("sudo dnf install vlc", VlcRuntime.vlcInstallHint(osRelease("ID=centos-stream", "ID_LIKE=\"rhel fedora\"")))
        assertEquals("sudo zypper install vlc", VlcRuntime.vlcInstallHint(osRelease("ID=opensuse-tumbleweed", "ID_LIKE=\"suse opensuse\"")))
        assertEquals("sudo pacman -S vlc", VlcRuntime.vlcInstallHint(osRelease("ID=arch")))
        assertEquals("sudo pacman -S vlc", VlcRuntime.vlcInstallHint(osRelease("ID=manjaro", "ID_LIKE=arch")))
        assertEquals("sudo zypper install vlc", VlcRuntime.vlcInstallHint(osRelease("ID=opensuse-leap", "ID_LIKE=\"suse opensuse\"")))
        assertEquals("sudo apk add vlc", VlcRuntime.vlcInstallHint(osRelease("ID=alpine")))
    }

    @Test
    fun `the install hint falls back to the generic one`() {
        assertEquals(VlcRuntime.GENERIC_VLC_INSTALL_HINT, VlcRuntime.vlcInstallHint(osRelease("ID=gentoo")))
        assertEquals(VlcRuntime.GENERIC_VLC_INSTALL_HINT, VlcRuntime.vlcInstallHint(null))
        assertEquals(VlcRuntime.GENERIC_VLC_INSTALL_HINT, VlcRuntime.vlcInstallHint(""))
    }

    private fun osRelease(vararg lines: String): String =
        ("PRETTY_NAME=\"Test Distro\"") + lines.joinToString("") { "\n$it" }

    /**
     * Runs [block] with the JVM-wide `jna.library.path` restored afterwards: the embedded-runtime branch of
     * `load()` prefixes it with the temp dir, and the fake `libvlc.dll` must not leak into later
     * JNA-based tests in the same test JVM.
     */
    private fun withRestoredJnaLibraryPath(block: () -> VlcRuntime.Availability): VlcRuntime.Availability {
        val previousPath = System.getProperty("jna.library.path")
        return try {
            block()
        } finally {
            if (previousPath == null) System.clearProperty("jna.library.path") else System.setProperty("jna.library.path", previousPath)
        }
    }
}
