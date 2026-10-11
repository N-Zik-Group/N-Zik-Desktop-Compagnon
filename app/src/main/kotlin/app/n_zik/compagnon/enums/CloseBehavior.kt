package app.n_zik.compagnon.enums

import app.n_zik.compagnon.generated.resources.*
import org.jetbrains.compose.resources.StringResource

/**
 * What the window's close button (X) does (spec `spec-tray-taskbar-media-controls`): [MinimizeToTray]
 * (the default) hides the window to the system tray and keeps the app alive in the background; [Quit]
 * closes the app. Stored in `settings.json` as the wire [wire] string; an unknown or missing value reads
 * as the default [MinimizeToTray] (forward tolerance).
 */
enum class CloseBehavior(
    val textId: StringResource,
    /** The wire value stored in `settings.json`. */
    val wire: String,
) {
    MinimizeToTray(Res.string.close_behavior_minimize, "minimize_to_tray"),
    Quit(Res.string.close_behavior_quit, "quit");

    companion object {
        /** An unknown or missing wire value reads as the default [MinimizeToTray]. */
        fun fromWire(wire: String): CloseBehavior = entries.firstOrNull { it.wire == wire } ?: MinimizeToTray
    }
}
