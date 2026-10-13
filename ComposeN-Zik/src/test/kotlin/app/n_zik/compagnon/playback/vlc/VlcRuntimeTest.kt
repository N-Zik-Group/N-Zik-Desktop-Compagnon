package app.n_zik.compagnon.playback.vlc

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * Unit tests of [VlcRuntime]'s unified embedded-vs-system decision, the platform libvlc name and
 * the distro install hint; the native load stays in the opt-in trial.
 */
class VlcRuntimeTest {

    @Test
    fun `the libvlc name matches the platform`() {
        assertEquals("libvlc.dll", VlcRuntime.libVlcName("Windows 11"))
        assertEquals("libvlc.dll", VlcRuntime.libVlcName("windows"))
        assertEquals("libvlc.so", VlcRuntime.libVlcName("Linux"))
        // Any non-Windows name maps to the Linux libvlc (the project's targets are Windows and Linux only).
        assertEquals("libvlc.so", VlcRuntime.libVlcName("Mac OS X"))
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
    fun `windows without the resources property falls back to the system libvlc`() {
        val availability = VlcRuntime.load("Windows 11", resourcesDir = null) { true }
            as VlcRuntime.Availability.Available
        assertEquals("system", availability.directory)
    }

    @Test
    fun `windows without any libvlc is unavailable with the system reason`() {
        val unavailable = VlcRuntime.load("Windows 11", resourcesDir = null) { false }
            as VlcRuntime.Availability.Unavailable
        assertEquals("no libvlc on the system", unavailable.reason)
    }

    @Test
    fun `a runtime dir without the platform libvlc falls back to the system`(@TempDir resources: File) {
        for (osName in listOf("Windows 11", "Linux")) {
            val availability = VlcRuntime.load(osName, resourcesDir = resources.absolutePath) { true }
                as VlcRuntime.Availability.Available
            assertEquals("system", availability.directory, "$osName: no embedded runtime → the system libvlc is discovered")
        }
    }

    @Test
    fun `windows with the embedded dll loads it`(@TempDir resources: File) {
        val runtime = File(resources, "vlc").apply { mkdirs() }
        File(runtime, "libvlc.dll").writeText("")
        val availability = withRestoredJnaLibraryPath {
            VlcRuntime.load("Windows 11", resourcesDir = resources.absolutePath) { true }
        }
        assertEquals(runtime.absolutePath, (availability as VlcRuntime.Availability.Available).directory)
    }

    @Test
    fun `linux with the embedded so loads it`(@TempDir resources: File) {
        val runtime = File(resources, "vlc").apply { mkdirs() }
        File(runtime, "libvlc.so").writeText("")
        val availability = withRestoredJnaLibraryPath {
            VlcRuntime.load("Linux", resourcesDir = resources.absolutePath) { true }
        }
        assertEquals(runtime.absolutePath, (availability as VlcRuntime.Availability.Available).directory)
    }

    @Test
    fun `windows with the embedded dll but a failed discovery is the system-unavailable result`(@TempDir resources: File) {
        val runtime = File(resources, "vlc").apply { mkdirs() }
        File(runtime, "libvlc.dll").writeText("")
        val unavailable = withRestoredJnaLibraryPath {
            VlcRuntime.load("Windows 11", resourcesDir = resources.absolutePath) { false }
        } as VlcRuntime.Availability.Unavailable
        assertEquals("no libvlc on the system", unavailable.reason)
        assertTrue(unavailable.installHint != null)
    }

    @Test
    fun `linux with the embedded so but a failed discovery is the system-unavailable result`(@TempDir resources: File) {
        val runtime = File(resources, "vlc").apply { mkdirs() }
        File(runtime, "libvlc.so").writeText("")
        val unavailable = withRestoredJnaLibraryPath {
            VlcRuntime.load("Linux", resourcesDir = resources.absolutePath) { false }
        } as VlcRuntime.Availability.Unavailable
        assertEquals("no libvlc on the system", unavailable.reason)
        assertTrue(unavailable.installHint != null)
    }

    @Test
    fun `a failed embedded load falls back to the system libvlc when the retry loads it`(@TempDir resources: File) {
        val runtime = File(resources, "vlc").apply { mkdirs() }
        File(runtime, "libvlc.so").writeText("")
        var calls = 0
        val availability = withRestoredJnaLibraryPath {
            // The first call is the embedded attempt (fails), the second the system retry (loads).
            VlcRuntime.load("Linux", resourcesDir = resources.absolutePath) { calls++; calls > 1 }
        }
        assertEquals(2, calls)
        assertEquals("system", (availability as VlcRuntime.Availability.Available).directory)
    }

    @Test
    fun `a resources dir carrying only the foreign platform libvlc falls back to the system`(@TempDir resources: File) {
        val dllOnly = File(resources, "dll-only").apply { mkdirs() }
        File(dllOnly, "libvlc.dll").writeText("")
        val soOnly = File(resources, "so-only").apply { mkdirs() }
        File(soOnly, "libvlc.so").writeText("")
        val onLinux = VlcRuntime.load("Linux", resourcesDir = dllOnly.absolutePath) { true }
        assertEquals("system", (onLinux as VlcRuntime.Availability.Available).directory,
            "Linux with only a libvlc.dll: no embedded runtime for the platform, the system libvlc is discovered")
        val onWindows = VlcRuntime.load("Windows 11", resourcesDir = soOnly.absolutePath) { true }
        assertEquals("system", (onWindows as VlcRuntime.Availability.Available).directory,
            "Windows with only a libvlc.so: no embedded runtime for the platform, the system libvlc is discovered")
    }

    @Test
    fun `the unavailable message selection is per platform`() {
        assertTrue(VlcRuntime.unavailableMessageIsEmbedded("Windows 11"))
        assertTrue(VlcRuntime.unavailableMessageIsEmbedded("windows"))
        assertFalse(VlcRuntime.unavailableMessageIsEmbedded("Linux"))
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
     * Runs [block] with the JVM-wide `jna.library.path` restored afterwards: the embedded-runtime
     * branch of `load()` prefixes it with the temp dir, and the fake `libvlc.dll`/`libvlc.so` must
     * not leak into later JNA-based tests in the same test JVM.
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
