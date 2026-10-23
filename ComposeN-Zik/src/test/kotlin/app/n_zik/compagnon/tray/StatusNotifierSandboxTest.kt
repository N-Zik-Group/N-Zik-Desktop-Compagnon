package app.n_zik.compagnon.tray

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The sandbox-aware SNI host probe (the 2026-10-12 Flatpak tray fix): inside the Flatpak sandbox
 * the watcher name is not probeable (xdg-dbusproxy filters the bus names the manifest does not
 * grant), so the manifest's `--talk-name` is trusted; outside, the live `NameHasOwner` probe runs.
 */
class StatusNotifierSandboxTest {

    @Test
    fun `the flatpak proxy bus address marks the sandbox`() {
        assertTrue(StatusNotifierTray.isFlatpakSandbox("unix:path=/run/flatpak/bus", null))
    }

    @Test
    fun `the flatpak id marker marks the sandbox`() {
        assertTrue(StatusNotifierTray.isFlatpakSandbox(null, "app.com.nzik.desktop.compagnon.dev"))
    }

    @Test
    fun `a bare session bus address is not the sandbox`() {
        assertFalse(StatusNotifierTray.isFlatpakSandbox("unix:path=/run/user/1000/bus", null))
        assertFalse(StatusNotifierTray.isFlatpakSandbox(null, null))
        assertFalse(StatusNotifierTray.isFlatpakSandbox("tcp:host=busdev.network,port=11311", "  "))
    }

    @Test
    fun `inside the sandbox the manifest is trusted and the probe never runs`() {
        var probeRan = false
        val decision = StatusNotifierTray.hostAvailable("unix:path=/run/flatpak/bus", null) {
            probeRan = true
            error("the probe must not run inside the sandbox (xdg-dbusproxy filters the watcher name)")
        }
        assertTrue(decision)
        assertFalse(probeRan)
    }

    @Test
    fun `outside the sandbox the live probe decides`() {
        assertEquals(true, StatusNotifierTray.hostAvailable("unix:path=/run/user/1000/bus", null) { true })
        assertEquals(false, StatusNotifierTray.hostAvailable(null, null) { false })
    }
}
