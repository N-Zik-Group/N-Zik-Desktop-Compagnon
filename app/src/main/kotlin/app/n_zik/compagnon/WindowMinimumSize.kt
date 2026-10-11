package app.n_zik.compagnon

import java.awt.Dimension
import java.awt.Rectangle
import java.awt.Toolkit
import java.awt.Window
import java.util.logging.Logger

private val log = Logger.getLogger("WindowMinimumSize")

/**
 * The smallest window the desktop layout is designed for, in logical (DPI-scaled) pixels — the
 * same floor as the Spotify desktop app (about 1000 x 750 at any display scale).
 */
internal val PREFERRED_MINIMUM_WINDOW_SIZE = Dimension(1000, 750)

/**
 * The minimum window size for a screen whose usable area is [usableArea]: [preferred], capped to
 * that area on each axis, so a small or heavily scaled screen (e.g. 1080p at 175 % = 1097 x 617
 * logical pixels) never gets a minimum window that overflows it.
 *
 * Pure, so it is unit-testable headless.
 */
internal fun windowMinimumSize(
    usableArea: Dimension,
    preferred: Dimension = PREFERRED_MINIMUM_WINDOW_SIZE,
): Dimension = Dimension(minOf(preferred.width, usableArea.width), minOf(preferred.height, usableArea.height))

/**
 * Applies [windowMinimumSize] to [window], measured on the screen the window opens on (its bounds
 * minus the taskbar insets). If the screen cannot be read, the window keeps no minimum instead of
 * failing.
 */
internal fun applyWindowMinimumSize(window: Window) {
    val config = window.graphicsConfiguration ?: return
    val screen: Rectangle = config.bounds
    val insets = runCatching { Toolkit.getDefaultToolkit().getScreenInsets(config) }.getOrNull()
    val usable = Dimension(
        screen.width - (insets?.let { it.left + it.right } ?: 0),
        screen.height - (insets?.let { it.top + it.bottom } ?: 0),
    )
    val minimum = windowMinimumSize(usable)
    window.minimumSize = minimum
    log.fine { "Window minimum size ${minimum.width}x${minimum.height} (usable screen ${usable.width}x${usable.height})" }
}
