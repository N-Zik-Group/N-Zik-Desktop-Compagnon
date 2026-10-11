package app.n_zik.compagnon.tray

import app.n_zik.compagnon.enums.CloseBehavior
import app.n_zik.compagnon.generated.resources.*
import app.n_zik.compagnon.platform.WindowController
import app.n_zik.compagnon.utils.Preferences
import app.n_zik.compagnon.utils.coroutines.NzikDispatchers
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.ApplicationScope
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.image.BufferedImage
import java.util.logging.Logger
import javax.swing.SwingUtilities
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** Which tray the platform offers: AWT's `SystemTray` (Windows, Linux X11 XEmbed), the D-Bus StatusNotifierItem (Linux, also Wayland), or none. */
enum class TrayBackend { Awt, StatusNotifier, None }

/**
 * The platform's tray, decided once per process: on Linux the StatusNotifierItem host comes first
 * (it works on Wayland too, and draws a proper item on KDE / GNOME-AppIndicator), AWT's XEmbed tray
 * is the fallback (X11 without a host); elsewhere AWT's `SystemTray`.
 */
val trayBackend: TrayBackend by lazy {
    val linux = System.getProperty("os.name", "").lowercase().startsWith("linux")
    when {
        linux && StatusNotifierTray.hostAvailable() -> TrayBackend.StatusNotifier
        runCatching { SystemTray.isSupported() }.getOrDefault(false) -> TrayBackend.Awt
        else -> TrayBackend.None
    }
}

/**
 * Whether the platform has a tray at all. The close-button decision ([shouldMinimizeOnClose],
 * [Main.kt]) and the general settings cards gate on it.
 */
internal fun isSystemTraySupported(): Boolean = trayBackend != TrayBackend.None

/**
 * One extensible tray-menu action (frozen spec `spec-tray-taskbar-media-controls`: the menu is
 * « Ouvrir » / extensible actions / « Quitter »). The app shortcuts ([AppShortcut]) now fill the
 * middle section; [TrayActions] stays the extension point for contract actions.
 */
interface TrayAction {
    /** The menu entry's title (a `values/strings.xml` id — the house strings rule). */
    val titleId: org.jetbrains.compose.resources.StringResource

    fun onClick()
}

/** The tray menu's extensible contract actions — none today. */
val TrayActions: List<TrayAction> = emptyList()

/**
 * The window's close-button (X) decision (frozen spec): « minimize to tray » only exists where a tray
 * exists — without one closing the window quits the app instead (the setting itself is hidden there,
 * see the general settings screen).
 */
fun shouldMinimizeOnClose(behavior: CloseBehavior, traySupported: Boolean): Boolean =
    behavior == CloseBehavior.MinimizeToTray && traySupported

/**
 * The app's system tray: a left click re-shows and focuses the window, a right click opens the
 * app-styled menu ([TrayMenuWindow]) — « Open » / the active app shortcuts (the phone's launcher
 * shortcuts, configured in the general settings) / « Quit ». Lives at the APPLICATION level (not
 * window content). Where the platform has no tray nothing is created: the close decision then falls
 * back to quitting through [shouldMinimizeOnClose].
 */
@Composable
fun ApplicationScope.TraySetup(
    windowController: WindowController,
    preferences: Preferences,
    onQuit: () -> Unit,
) {
    val backend = trayBackend
    if (backend == TrayBackend.None) return

    val title = stringResource(Res.string.tray_tooltip)
    val iconPainter = painterResource(Res.drawable.app_icon)
    var anchor by remember { mutableStateOf<TrayAnchor?>(null) }
    // The tray callbacks land on AWT / D-Bus threads: the menu state is only touched on the EDT
    val openMenu: (TrayAnchor) -> Unit = { at -> SwingUtilities.invokeLater { anchor = at } }

    when (backend) {
        TrayBackend.Awt -> AwtTray(iconPainter, title, onActivate = windowController::showAndFocus, onMenu = openMenu)
        TrayBackend.StatusNotifier -> StatusNotifierItem(iconPainter, title, onActivate = windowController::showAndFocus, onMenu = openMenu)
        TrayBackend.None -> Unit
    }

    val settings by preferences.settings.collectAsState()
    val shortcuts = resolveActiveShortcuts(settings.appShortcutsOrder, settings.appShortcutsEnabled)
    val model = TrayMenuModel(
        title = title,
        top = listOf(TrayMenuEntry(stringResource(Res.string.tray_open), Res.drawable.app_icon, tinted = false, onClick = windowController::showAndFocus)) +
            TrayActions.map { action -> TrayMenuEntry(stringResource(action.titleId), null, onClick = action::onClick) },
        shortcuts = shortcuts.map { shortcut ->
            TrayMenuEntry(stringResource(shortcut.labelId), shortcut.iconId) {
                // Every shortcut opens the window; the ones with a PC destination then switch to it
                windowController.showAndFocus()
                AppShortcutRequests.pending.value = shortcut
            }
        },
        bottom = listOf(TrayMenuEntry(stringResource(Res.string.tray_quit), Res.drawable.close, onClick = onQuit)),
    )
    anchor?.let { at -> TrayMenuWindow(at, model, onDismiss = { anchor = null }) }
}

/** The tray icon image: the app icon rendered at [px] (the trays scale it to their own size). */
private fun Painter.trayImage(px: Int): BufferedImage {
    val image = toAwtImage(Density(1f), LayoutDirection.Ltr, Size(px.toFloat(), px.toFloat()))
    if (image is BufferedImage && image.type == BufferedImage.TYPE_INT_ARGB) return image
    return BufferedImage(px, px, BufferedImage.TYPE_INT_ARGB).also { it.createGraphics().apply { drawImage(image, 0, 0, null); dispose() } }
}

/**
 * AWT's tray icon WITHOUT a native popup menu: the right click opens the app's own menu. The popup
 * trigger is the release on Windows and the press on X11 — both are listened to, the first one wins.
 */
@Composable
private fun AwtTray(iconPainter: Painter, title: String, onActivate: () -> Unit, onMenu: (TrayAnchor) -> Unit) {
    val log = remember { Logger.getLogger("TraySetup") }
    DisposableEffect(iconPainter) {
        val trayIcon = TrayIcon(iconPainter.trayImage(TRAY_ICON_PX), title).apply {
            isImageAutoSize = true
            addMouseListener(object : MouseAdapter() {
                private var menuOpenedByPress = false

                override fun mousePressed(e: MouseEvent) {
                    menuOpenedByPress = e.isPopupTrigger
                    if (e.isPopupTrigger) onMenu(TrayAnchor(e.xOnScreen, e.yOnScreen))
                }

                override fun mouseReleased(e: MouseEvent) {
                    if (e.isPopupTrigger && !menuOpenedByPress) onMenu(TrayAnchor(e.xOnScreen, e.yOnScreen))
                }

                override fun mouseClicked(e: MouseEvent) {
                    if (e.button == MouseEvent.BUTTON1) onActivate()
                }
            })
        }
        val tray = runCatching { SystemTray.getSystemTray().also { it.add(trayIcon) } }
            .onFailure { log.warning("Tray: could not add the tray icon (${it::class.simpleName})") }
            .getOrNull()
        onDispose { tray?.remove(trayIcon) }
    }
}

/** The Linux StatusNotifierItem: registered off the EDT, removed with the composition. */
@Composable
private fun StatusNotifierItem(iconPainter: Painter, title: String, onActivate: () -> Unit, onMenu: (TrayAnchor) -> Unit) {
    DisposableEffect(iconPainter) {
        val item = StatusNotifierTray(title, iconPainter.trayImage(TRAY_ICON_PX), onActivate, onMenu)
        val scope = NzikDispatchers.fireAndForget(NzikDispatchers.DATA)
        scope.launch { item.start() }
        onDispose { scope.launch { item.stop() } }
    }
}

/** The tray icon's render size (px): crisp at 200 % scaling, scaled down by the tray otherwise. */
private const val TRAY_ICON_PX = 64
