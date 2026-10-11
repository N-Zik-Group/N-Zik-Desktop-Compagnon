package app.n_zik.compagnon.platform

import app.n_zik.compagnon.bridge.ConnectionState
import app.n_zik.compagnon.bridge.state.PlayerState
import app.n_zik.compagnon.bridge.state.SessionContract
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * A state push that lands before the taskbar setup (no window attached yet — the session
 * auto-reconnects at startup, before the window's first show) is kept: the first addButtons then
 * carries the phone's live state instead of the disabled defaults. No window → no native call.
 */
class WindowsThumbBarTest {

    @Test
    fun `a state push before the setup is kept for the first add`() {
        val thumbBar = WindowsThumbBar(WindowController())
        val state = PlayerState(isPlaying = true)
        val features = setOf(SessionContract.FEATURE_PLAYBACK)

        thumbBar.onStateChanged(state, ConnectionState.Live, features)

        assertEquals(thumbBarSpecs(state, ConnectionState.Live, features), thumbBar.specs)
    }
}
