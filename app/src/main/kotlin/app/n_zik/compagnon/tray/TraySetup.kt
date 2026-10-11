package app.n_zik.compagnon.tray

import app.n_zik.compagnon.enums.CloseBehavior
import app.n_zik.compagnon.generated.resources.*
import app.n_zik.compagnon.platform.WindowController
import androidx.compose.runtime.Composable
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.rememberTrayState
import java.awt.SystemTray
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Whether the platform has a system tray at all (the same check Compose's own [Tray] performs
 * internally before creating the icon): `false` on Wayland. The close-button decision
 * ([shouldMinimizeOnClose], [Main.kt]) and the general settings card both gate on it, so it is
 * a shared top-level helper.
 */
internal fun isSystemTraySupported(): Boolean = SystemTray.isSupported()

/**
 * One extensible tray-menu action (frozen spec `spec-tray-taskbar-media-controls`: the menu is
 * « Ouvrir » / extensible actions / « Quitter »). [TrayActions] is the extension point — the frozen
 * spec fixes the list at NONE, so today it is empty, and the interface only shapes the day a
 * contract action earns a slot.
 */
interface TrayAction {
    /** The menu entry's title (a `values/strings.xml` id — the house strings rule). */
    val titleId: StringResource

    fun onClick()
}

/** The tray menu's extensible actions — none today (the frozen spec's « actions extensibles (aucune) »). */
val TrayActions: List<TrayAction> = emptyList()

/**
 * The window's close-button (X) decision (frozen spec): « minimize to tray » only exists where a tray
 * exists — on Wayland the tray is unsupported and closing the window quits the app instead (the
 * setting itself is hidden there, see the general settings screen).
 */
fun shouldMinimizeOnClose(behavior: CloseBehavior, traySupported: Boolean): Boolean =
    behavior == CloseBehavior.MinimizeToTray && traySupported

/**
 * The app's system tray (frozen spec `spec-tray-taskbar-media-controls`): the menu is « Open » /
 * the (empty) extensible actions / « Quit », and clicking the icon re-shows and focuses the window.
 * It lives at the APPLICATION level — [Tray] is an [ApplicationScope] composable, not part of the
 * window's content. Where the platform has no tray (Wayland) nothing is created: the close decision
 * then falls back to quitting through [shouldMinimizeOnClose].
 */
@Composable
fun ApplicationScope.TraySetup(
    windowController: WindowController,
    onQuit: () -> Unit,
) {
    if (!isSystemTraySupported()) return
    Tray(
        icon = painterResource(Res.drawable.app_icon),
        state = rememberTrayState(),
        tooltip = stringResource(Res.string.tray_tooltip),
        onAction = windowController::showAndFocus,
    ) {
        Item(text = stringResource(Res.string.tray_open), onClick = windowController::showAndFocus)
        TrayActions.forEach { action ->
            Item(text = stringResource(action.titleId), onClick = action::onClick)
        }
        Separator()
        Item(text = stringResource(Res.string.tray_quit), onClick = onQuit)
    }
}
