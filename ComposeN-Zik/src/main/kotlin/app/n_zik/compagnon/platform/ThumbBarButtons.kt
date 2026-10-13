package app.n_zik.compagnon.platform

import app.n_zik.compagnon.bridge.ConnectionState
import app.n_zik.compagnon.bridge.state.PlayerState
import app.n_zik.compagnon.bridge.state.SessionContract

/**
 * The three ⏮⏯⏭ ThumbBar buttons (spec `spec-tray-taskbar-media-controls`). [id] is the value the taskbar
 * reports back as `LOWORD(wParam)` of the `WM_COMMAND` it delivers on a click (see `WindowsThumbBar`) —
 * 1 / 2 / 3, the button's order in the toolbar.
 */
enum class ThumbBarButton(val id: Int) {
    Previous(1),

    PlayPause(2),

    Next(3),
}

/** The glyph of the ⏯ button: it follows the phone's `isPlaying` (the PC invents no state — AD-1). */
enum class PlayPauseGlyph { Play, Pause }

/** One ThumbBar button's spec: enabled by the wire-contract gate, and the ⏯'s current glyph. */
data class ThumbBarSpec(
    val button: ThumbBarButton,
    val enabled: Boolean,
    val glyph: PlayPauseGlyph? = null,
)

/**
 * The three buttons' specs from the phone's state (spec `spec-tray-taskbar-media-controls`, spine AD-2):
 * the buttons are enabled **only** with a live session AND the contract's `playback` feature — otherwise
 * they render disabled (`dwFlags = THBF_DISABLED`), the taskbar greys them out. The ⏯'s glyph follows
 * `isPlaying` (`null` state, no snapshot yet, reads as not playing).
 */
fun thumbBarSpecs(state: PlayerState?, connection: ConnectionState, features: Set<String>): List<ThumbBarSpec> {
    val enabled = connection is ConnectionState.Live && SessionContract.FEATURE_PLAYBACK in features
    return listOf(
        ThumbBarSpec(ThumbBarButton.Previous, enabled),
        ThumbBarSpec(ThumbBarButton.PlayPause, enabled, if (state?.isPlaying == true) PlayPauseGlyph.Pause else PlayPauseGlyph.Play),
        ThumbBarSpec(ThumbBarButton.Next, enabled),
    )
}
