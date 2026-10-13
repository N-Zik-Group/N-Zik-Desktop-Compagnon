package app.n_zik.compagnon.platform

import app.n_zik.compagnon.bridge.ConnectionState
import app.n_zik.compagnon.bridge.state.PlayerState

/**
 * The system media surface of any OS that is not Windows or Linux (spec `spec-tray-taskbar-media-controls`):
 * none. Every hook is a no-op, so the base lifecycle (attach / state stream / detach) stays uniform
 * and the frozen spec's « Never » list (no macOS media controls, none on Wayland) is met by construction.
 */
class NoopMediaControls : TaskbarMediaControls() {
    override fun onPlatformSetup() = Unit

    override fun onStateChanged(state: PlayerState?, connection: ConnectionState, features: Set<String>) = Unit

    override fun onSessionEnded() = Unit
}
