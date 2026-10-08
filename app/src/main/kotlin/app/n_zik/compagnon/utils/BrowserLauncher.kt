package app.n_zik.compagnon.utils

import java.awt.Desktop
import java.net.URI

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
        Toaster.e(it.message ?: "Could not open the browser")
    }
}
