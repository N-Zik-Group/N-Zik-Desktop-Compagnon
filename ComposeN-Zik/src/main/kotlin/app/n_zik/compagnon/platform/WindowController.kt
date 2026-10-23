package app.n_zik.compagnon.platform

import java.awt.Frame
import java.awt.Window
import javax.swing.SwingUtilities

/**
 * App-lifetime holder of the AWT window behind the Compose content (spec `spec-tray-taskbar-media-controls`):
 * the tray's « Ouvrir » entry and the MPRIS `Raise()` must be able to re-show and focus the window from
 * outside the composition — the `onCloseRequest` lambda and the system surfaces cannot see the `window`
 * parameter of [androidx.compose.ui.window.Window]. The window is attached once during the root's
 * composition (`remember(window)`), before any session repository exists.
 */
class WindowController {
    @Volatile
    private var window: Window? = null

    /** Keeps the AWT window the [showAndFocus]/[hideToTray] actions act on; called once from composition. */
    fun attach(window: Window) {
        this.window = window
    }

    /** The attached AWT window, `null` before the root composes it — the [WindowsThumbBar] reads its HWND from it. */
    fun currentWindow(): Window? = window

    /**
     * An optional callback invoked on the Swing EDT right before [showAndFocus] makes the window
     * visible: the taskbar media surface uses it to (re)add its toolbar BEFORE the show — the
     * taskbar's thumbnail button reads the stored toolbar when it (re)creates itself on the show,
     * and a push landing after the button exists reports success into the void (live-verified
     * 2026-10-11). `null` until the app wires its instance.
     */
    var onShowHook: (() -> Unit)? = null

    /** Re-shows and focuses the window on the Swing EDT. No-op when nothing is attached yet. */
    fun showAndFocus() {
        val window = window ?: return
        onEdt {
            // A manually minimized window (taskbar minimize button) stays "shown" but minimized:
            // restore it first, or setVisible alone would leave it on the taskbar instead of in

            // front (the `extendedState` API — this JDK's `Frame` has no `isIconified`/`setIconified`)
            (window as? Frame)?.let { frame ->
                if ((frame.extendedState and Frame.ICONIFIED) != 0) frame.extendedState = Frame.NORMAL
            }
            onShowHook?.invoke()
            window.isVisible = true
            window.toFront()
            window.requestFocus()
        }
    }

    /**
     * Closes the window to the tray (the « X = minimize to tray » close behavior, user decision 2026-10-11):
     * the window is hidden entirely — gone from the taskbar, only the tray icon remains — while the app
     * keeps running; [showAndFocus] brings it back. On the Swing EDT. No-op when detached.
     */
    fun hideToTray() {
        val window = window ?: return
        onEdt { window.isVisible = false }
    }

    private fun onEdt(block: () -> Unit) {
        if (SwingUtilities.isEventDispatchThread()) block() else SwingUtilities.invokeLater(block)
    }
}
