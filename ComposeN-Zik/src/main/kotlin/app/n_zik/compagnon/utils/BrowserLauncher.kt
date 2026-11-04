package app.n_zik.compagnon.utils

import app.n_zik.compagnon.generated.resources.*
import java.awt.Desktop
import java.net.URI
import java.util.logging.Logger

/**
 * Opens [url] in the system's default browser (the desktop port of the phone's
 * `LocalUriHandler.openUri` — spec `spec-updater` AD-9/AD-10: the About page's repo / issue links,
 * the update page's GitHub release link, the contributors' profile links). A failure (no browser
 * association, headless session) is a warning toast, never a crash.
 */
fun openInBrowser(url: String) {
    runCatching {
        val desktop = Desktop.getDesktop()
        if (desktop.isSupported(Desktop.Action.BROWSE)) {
            desktop.browse(URI(url))
        }
    }.onFailure {
        Logger.getLogger("BrowserLauncher").warning("Could not open the browser: ${it.message}")
        Toaster.e(Res.string.error_open_browser)
    }
}
