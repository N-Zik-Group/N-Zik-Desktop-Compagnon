package app.n_zik.compagnon.tray

import app.n_zik.compagnon.enums.CloseBehavior
import app.n_zik.compagnon.generated.resources.*
import app.n_zik.compagnon.platform.WindowController
import app.n_zik.compagnon.utils.Preferences
import app.n_zik.compagnon.utils.coroutines.NzikDispatchers
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import java.awt.Color
import java.awt.Image
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.logging.Logger
import kotlin.math.roundToInt
import javax.imageio.ImageIO
import javax.swing.SwingUtilities
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** Which tray the platform offers: AWT's `SystemTray` (Windows, Linux X11 XEmbed), the D-Bus StatusNotifierItem (Linux, also Wayland), or none. */
enum class TrayBackend { Awt, StatusNotifier, None }

/**
 * The platform's tray, decided once per process: on Linux the StatusNotifierItem host comes first
 * (it works on Wayland too, and draws a proper item on KDE / GNOME-AppIndicator), AWT's XEmbed tray
 * is the fallback (X11 without a host); elsewhere AWT's `SystemTray`. The decision is logged — the
 * tray's absence in the field is otherwise invisible (a sandboxed probe, a missing host, a
 * headless session all look the same from the user side).
 */
val trayBackend: TrayBackend by lazy {
    val linux = System.getProperty("os.name", "").lowercase().startsWith("linux")
    val backend = when {
        linux && StatusNotifierTray.hostAvailable() -> TrayBackend.StatusNotifier
        runCatching { SystemTray.isSupported() }.getOrDefault(false) -> TrayBackend.Awt
        else -> TrayBackend.None
    }
    val sandbox = StatusNotifierTray.isFlatpakSandbox(System.getenv("DBUS_SESSION_BUS_ADDRESS"), System.getenv("FLATPACK_ID"))
    val awt = runCatching { SystemTray.isSupported() }.getOrDefault(false)
    Logger.getLogger("TraySetup").info("Tray: backend=$backend (linux=$linux, flatpakSandbox=$sandbox, awtSupported=$awt)")
    backend
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
    // The tray shows the app icon the player screen uses (`ic_launcher_monochrome` — the "material"
    // look): the tray icon AND the tray menu's « Open » row (a full-color logo would stand out

    // against the monochrome menu icons). The full-color in-app tile (`app_icon`) is the WINDOWS
    // taskbar's icon instead: the dconf-based tint is Linux-only (there is no dconf on Windows —

    // the white monochrome glyph would be invisible on a light taskbar), so Windows keeps the
    // full-color untinted glyph (no regression to the AWT path). `ic_launcher` (the full-color

    // launcher tile) stays with the desktop launchers.
    val iconPainter = painterResource(Res.drawable.ic_launcher_monochrome)
    val windowsTrayPainter = painterResource(Res.drawable.app_icon)
    var anchor by remember { mutableStateOf<TrayAnchor?>(null) }
    // The tray callbacks land on AWT / D-Bus threads: the menu state is only touched on the EDT
    val openMenu: (TrayAnchor) -> Unit = { at -> SwingUtilities.invokeLater { anchor = at } }

    val settings by preferences.settings.collectAsState()
    val shortcuts = resolveActiveShortcuts(settings.appShortcutsOrder, settings.appShortcutsEnabled)
    val model = TrayMenuModel(
        title = title,
        // Stable ids (the native DBusMenu's host recycles its items by id — TrayMenuEntry)
        top = listOf(TrayMenuEntry(TRAY_MENU_ID_OPEN, stringResource(Res.string.tray_open), Res.drawable.ic_launcher_monochrome, onClick = windowController::showAndFocus)) +
            TrayActions.mapIndexed { index, action -> TrayMenuEntry(TRAY_MENU_ID_TOP_ACTION_BASE + index, stringResource(action.titleId), null, onClick = action::onClick) },
        shortcuts = shortcuts.map { shortcut ->
            TrayMenuEntry(shortcut.trayMenuId, stringResource(shortcut.labelId), shortcut.iconId) {
                // Every shortcut opens the window; the ones with a PC destination then switch to it
                windowController.showAndFocus()
                AppShortcutRequests.pending.value = shortcut
            }
        },
        bottom = listOf(TrayMenuEntry(TRAY_MENU_ID_QUIT, stringResource(Res.string.tray_quit), Res.drawable.close, onClick = onQuit)),
    )

    // The session's monochrome tint ([menuIconTint]): resolved once before the UI exists
    // ([primeMenuTint] from `main()`) — the composition itself never touches the disk. Linux-only

    // by construction (see [primeMenuTint]).
    val menuTint = menuIconTint()
    val linux = System.getProperty("os.name", "").lowercase().startsWith("linux")

    when (backend) {
        TrayBackend.Awt -> AwtTray(
            iconPainter = if (linux) iconPainter else windowsTrayPainter,
            tint = if (linux) menuTint else null,
            title = title,
            onActivate = windowController::showAndFocus,
            onMenu = openMenu,
        )
        TrayBackend.StatusNotifier -> {
            // The native DBusMenu's icons: each distinct row icon rendered once as a tinted
            // `icon-data` (a PNG in memory — the host draws it without any icon theme). Linux-only

            // work: the AWT backend never consumes the PNGs, so they are not rendered there
            val iconData = rememberIconData((model.top + model.shortcuts + model.bottom).mapNotNull { it.icon }.distinct(), menuTint)
            val menuRows = model.toMenuRows { icon -> icon?.let { iconData[it] } }
            // The SNI registration can still fail at runtime (no watcher on the host side, or the
            // static bus name taken by another instance): the AWT tray then takes over where the

            // platform supports it (X11 — never on a Wayland-only session).
            var sniUnavailable by remember { mutableStateOf(false) }
            StatusNotifierItem(
                iconPainter,
                menuTint,
                title,
                onActivate = windowController::showAndFocus,
                onMenu = openMenu,
                menuRows = menuRows,
                onUnavailable = {
                    Logger.getLogger("TraySetup").info("Tray: the SNI registration failed — the AWT tray takes over (if supported)")
                    SwingUtilities.invokeLater { sniUnavailable = true }
                },
            )
            if (sniUnavailable && runCatching { SystemTray.isSupported() }.getOrDefault(false)) {
                AwtTray(iconPainter, menuTint, title, onActivate = windowController::showAndFocus, onMenu = openMenu)
            }
        }
        TrayBackend.None -> Unit
    }

    anchor?.let { at -> TrayMenuWindow(at, model, onDismiss = { anchor = null }) }
}

/** The tray icon image: the app icon rendered at [px] * [TRAY_ICON_PADDING_SCALE], centered (the trays
 * scale it to their own size; the icon is full-bleed, so the margin keeps it from looking oversized
 * next to the other panel icons — live feedback 2026-10-12), tinted [tint] when non-null (the
 * panel's text color — the tray draws the pixmap as-is, see [menuIconTint]; null = the untinted
 * full-color icon, the Windows taskbar). */
private fun Painter.trayImage(px: Int, tint: Color?): BufferedImage {
    val size = (px * TRAY_ICON_PADDING_SCALE).roundToInt()
    val icon = toAwtImage(Density(1f), LayoutDirection.Ltr, Size(size.toFloat(), size.toFloat()))
    val padded = padTrayImage(icon, px)
    tint?.let { tintImage(padded, it) }
    return padded
}

/** Centers [icon] on a [px]×[px] transparent ARGB canvas (the tray icon's padding). */
internal fun padTrayImage(icon: Image, px: Int): BufferedImage =
    BufferedImage(px, px, BufferedImage.TYPE_INT_ARGB).also { canvas ->
        canvas.createGraphics().apply {
            drawImage(icon, (px - icon.getWidth(null)) / 2, (px - icon.getHeight(null)) / 2, null)
            dispose()
        }
    }

/**
 * AWT's tray icon WITHOUT a native popup menu: the right click opens the app's own menu. The popup
 * trigger is the release on Windows and the press on X11 — both are listened to, the first one wins.
 */
@Composable
private fun AwtTray(iconPainter: Painter, tint: Color?, title: String, onActivate: () -> Unit, onMenu: (TrayAnchor) -> Unit) {
    val log = remember { Logger.getLogger("TraySetup") }
    DisposableEffect(iconPainter, tint) {
        val trayIcon = TrayIcon(iconPainter.trayImage(TRAY_ICON_PX, tint), title).apply {
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

/**
 * The Linux StatusNotifierItem: registered off the EDT, removed with the composition. [onUnavailable]
 * fires once, on the registration's DATA thread, when [StatusNotifierTray.start] could not register
 * (the caller hops it to the UI thread).
 */
@Composable
private fun StatusNotifierItem(iconPainter: Painter, tint: Color, title: String, onActivate: () -> Unit, onMenu: (TrayAnchor) -> Unit, menuRows: List<DBusMenuRow>, onUnavailable: () -> Unit) {
    var item by remember { mutableStateOf<StatusNotifierTray?>(null) }
    DisposableEffect(iconPainter, tint) {
        val newItem = StatusNotifierTray(title, iconPainter.trayImage(TRAY_ICON_PX, tint), onActivate, onMenu, menuRows)
        item = newItem
        val scope = NzikDispatchers.fireAndForget(NzikDispatchers.DATA)
        scope.launch {
            if (!newItem.start()) {
                onUnavailable()
                return@launch
            }
            // The GNOME AppIndicator extension races the menu proxy's name-owner discovery with the
            // first GetLayout: when the owner is found afterward, it re-fetches the layout but drops

            // the `ready-changed` that would run the attach — the right-click menu stays empty. The
            // delayed Menu announcement makes it rebuild the menu client on solid name-owner state

            // (live-verified 2026-10-12; StatusNotifierTray.announceMenu documents the rest).
            delay(MENU_ANNOUNCE_DELAY_MS)
            newItem.announceMenu()
        }
        onDispose {
            item = null
            scope.launch { newItem.stop() }
        }
    }
    // The settings changed (the shortcut list or its order) — the native menu follows live: the
    // LayoutUpdated signal makes the host re-fetch the layout (right away when it is open, on the

    // next open otherwise). The first run is a no-op: the rows equal the constructor's, and
    // DBusMenu.updateRows guards the signal on the visible content.

    // The `LayoutUpdated` signal is a D-Bus call — off the EDT, as every other call of the tray
    // (the LaunchedEffect body runs on the composition thread)
    LaunchedEffect(menuRows) {
        val tray = item ?: return@LaunchedEffect
        withContext(NzikDispatchers.DATA) { tray.updateMenuRows(menuRows) }
    }
}

@Volatile
private var menuTintValue: Color = Color.WHITE

/**
 * Resolves the tray/menu icon tint before the UI exists (called from `Main.main()` on the startup
 * thread — the run's one small synchronous read: the composition itself never touches the disk).
 * Linux-only: there is no dconf on Windows, and its AWT taskbar keeps the full-color untinted app
 * icon (the `TraySetup` composable decides the icon per backend).
 */
internal fun primeMenuTint() {
    if (!System.getProperty("os.name", "").lowercase().startsWith("linux")) return
    menuTintValue = menuIconTintFor(HostDconf.readString("org/gnome/desktop/interface", "color-scheme"))
}

/**
 * The tray icon's (the SNI `IconPixmap`) and the native menu rows' icons' (the DBusMenu
 * `icon-data`) monochrome tint, as resolved by [primeMenuTint] (the white default until it runs).
 * The shell draws both AS-IS (no icon theme, no host tint — a themed symbolic `IconName` would
 * adapt on its own, but the host-side shell cannot see a flatpak app's `/app`, so a custom icon
 * file never resolves from the sandbox), so the icons must match the menu/panel's text color. The
 * session's `org.gnome.desktop.interface` `color-scheme` setting decides — read from the HOST's
 * dconf user DB ([HostDconf]) because every probe from inside the sandbox (gsettings, the dconf
 * daemon's D-Bus API, the DConf/Settings portals) answers the schema defaults, not the host's
 * value — `prefer-light` → dark icons on the light menu/panel, anything else (dark, the default,
 * no value, a failed read) → white, the color of the dark menus/panels this targets.
 */
internal fun menuIconTint(): Color = menuTintValue

/** The tint decision (pure, testable): only an explicit light session gets the dark tint. */
internal fun menuIconTintFor(colorScheme: String?): Color =
    if (colorScheme == "prefer-light") Color(MENU_ICON_LIGHT_TINT) else Color.WHITE

/**
 * The menu rows' icon data for the native DBusMenu: each distinct [icons] resource rendered once as
 * a [tint]-tinted PNG and cached for the run ([iconPng] is the `icon-data` property's bytes — the
 * host draws it without any icon theme). A failed render leaves the row text-only (the host then
 * shows no icon).
 */
@Composable
private fun rememberIconData(icons: List<DrawableResource>, tint: Color): Map<DrawableResource, ByteArray> {
    val cache = remember { mutableMapOf<DrawableResource, ByteArray>() }
    for (icon in icons) {
        if (icon !in cache) {
            // The painter resolves composable-side (the conversion below is plain AWT)
            val painter = painterResource(icon)
            runCatching { iconPng(painter, tint) }
                .onSuccess { cache[icon] = it }
                .onFailure { Logger.getLogger("TraySetup").warning("Tray: could not render the menu icon (${it::class.simpleName}: ${it.message})") }
        }
    }
    return cache
}

/** [painter] rendered as a [tint]-tinted PNG — the tray menu's `icon-data` ([MENU_ICON_PX]: the
 * shell's menus show ~16–24 px icons, so 48 px stays crisp at 200 % scaling). The row icons get
 * the SAME margin as the tray icon ([TRAY_ICON_PADDING_SCALE] — full-bleed glyphs look oversized
 * next to the menu text, the way Discord insets its tray icons). */
private fun iconPng(painter: Painter, tint: Color): ByteArray {
    // `toAwtImage` returns a Compose `PainterImage` — an AWT `Image` that is NOT a `RenderedImage`
    // (Compose 1.13.0-alpha01): `ImageIO` needs a `RenderedImage`, so encode through a

    // `BufferedImage` canvas (the tray icon's own path — [padTrayImage])
    val size = (MENU_ICON_PX * TRAY_ICON_PADDING_SCALE).roundToInt()
    val icon = painter.toAwtImage(Density(1f), LayoutDirection.Ltr, Size(size.toFloat(), size.toFloat()))
    val canvas = padTrayImage(icon, MENU_ICON_PX)
    tintImage(canvas, tint)
    return ByteArrayOutputStream().also { out -> ImageIO.write(canvas, "png", out) }.toByteArray()
}

/**
 * Tints [image] with [tint] pixel by pixel, preserving each pixel's alpha (a pixel-by-pixel replace
 * — an AWT composite like `SrcAtop` would blend the edge pixels instead of replacing them) — the
 * tray icon and the native menu's row icons are monochrome glyphs (the host draws them as-is, so
 * the tint is baked into the pixmap/PNG — see [menuIconTint]).
 */
internal fun tintImage(image: BufferedImage, tint: Color) {
    // the tint's own alpha byte (Color.getRGB carries it as 0xFF) must not ride into the pixels'
    // alpha — mask it, keep each pixel's own
    val rgb = tint.rgb and 0x00FFFFFF
    for (y in 0 until image.height) {
        for (x in 0 until image.width) {
            val alpha = image.getRGB(x, y).ushr(24)
            if (alpha != 0) image.setRGB(x, y, alpha shl 24 or rgb)
        }
    }
}

/** The tray icon's render size (px): crisp at 200 % scaling, scaled down by the tray otherwise. */
private const val TRAY_ICON_PX = 64

/** The icon's scale within its canvas: the margin around a full-bleed icon (the panels are not full-bleed). */
private const val TRAY_ICON_PADDING_SCALE = 0.8f

/** How long after the SNI registration the `Menu` property is re-announced (the GNOME extension's menu-client rebuild — the app's own registration + the host's indicator setup fit well inside). */
private const val MENU_ANNOUNCE_DELAY_MS = 1500L

/** The menu rows' icon render size (px) for the DBusMenu's `icon-data` (see [iconPng]). */
private const val MENU_ICON_PX = 48

/** The row icons' tint on a LIGHT menu (the dark menu's is `Color.WHITE` — [menuIconTint]). */
private const val MENU_ICON_LIGHT_TINT = 0xFF241F1F.toInt()
