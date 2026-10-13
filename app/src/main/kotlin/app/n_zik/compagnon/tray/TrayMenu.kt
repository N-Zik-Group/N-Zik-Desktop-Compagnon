package app.n_zik.compagnon.tray

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberDialogState
import app.n_zik.compagnon.components.theme.Appearance
import app.n_zik.compagnon.components.theme.LocalAppearance
import app.n_zik.compagnon.utils.medium
import app.n_zik.compagnon.utils.semiBold
import java.awt.GraphicsEnvironment
import java.awt.Rectangle
import java.awt.Toolkit
import java.awt.event.WindowEvent
import java.awt.event.WindowFocusListener
import kotlinx.coroutines.flow.MutableStateFlow
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/** The live app appearance, published by the window's root — the tray menu is its own window. */
object TrayMenuAppearance {
    val current = MutableStateFlow<Appearance?>(null)
}

/** Where the tray menu was asked for, in screen coordinates (AWT logical pixels). */
data class TrayAnchor(val x: Int, val y: Int)

/** One row of the tray menu; [icon] `null` = no icon; [tinted] `false` keeps a full-color icon (the app logo). */
/**
 * One row of the tray menu. [id] is the row's STABLE identity (not its position): the native
 * DBusMenu host reuses its items BY ID (a row that moves keeps its id; a row that disappears is
 * removed from the layout, a new one is added) — reusing an id for a different row would change the
 * host item's type in place and corrupt its menu (live bug 2026-10-13: a duplicated « Quitter »
 * after a settings change). See [DBusMenuRow] and the id constants in `DbusMenu.kt`.
 */
data class TrayMenuEntry(val id: Int, val label: String, val icon: DrawableResource?, val tinted: Boolean = true, val onClick: () -> Unit)

/** The tray menu's rows: the header-less list « Open » / the shortcuts / « Quit ». */
data class TrayMenuModel(val title: String, val top: List<TrayMenuEntry>, val shortcuts: List<TrayMenuEntry>, val bottom: List<TrayMenuEntry>)

internal const val TRAY_MENU_WIDTH_DP = 248
internal const val TRAY_MENU_ROW_DP = 40
internal const val TRAY_MENU_TITLE_DP = 44
internal const val TRAY_MENU_SEPARATOR_DP = 9
internal const val TRAY_MENU_PADDING_DP = 6

/** The menu's height for [model] (fixed row heights — the window is sized before it is shown). */
internal fun trayMenuHeightDp(model: TrayMenuModel): Int {
    val separators = (if (model.shortcuts.isNotEmpty()) 1 else 0) + 1
    val rows = model.top.size + model.shortcuts.size + model.bottom.size
    return TRAY_MENU_PADDING_DP * 2 + TRAY_MENU_TITLE_DP + rows * TRAY_MENU_ROW_DP + separators * TRAY_MENU_SEPARATOR_DP
}

/**
 * The menu's top-left corner for a click at [anchor] (pure): it opens toward the screen's middle —
 * above the click on a bottom taskbar (Windows), below it on a top panel (GNOME / KDE top bar),
 * left of it on the right half — and always stays inside [usable] (the screen minus its panels).
 */
fun trayMenuPosition(anchor: TrayAnchor, width: Int, height: Int, usable: Rectangle): TrayAnchor {
    val x = if (anchor.x > usable.centerX) anchor.x - width else anchor.x
    val y = if (anchor.y > usable.centerY) anchor.y - height else anchor.y
    val maxX = usable.x + usable.width - width
    val maxY = usable.y + usable.height - height
    return TrayAnchor(
        x.coerceIn(usable.x, maxOf(usable.x, maxX)),
        y.coerceIn(usable.y, maxOf(usable.y, maxY)),
    )
}

/** The usable area (bounds minus the taskbar / panels) of the screen holding [anchor]; the default screen otherwise. */
internal fun usableScreenArea(anchor: TrayAnchor): Rectangle {
    val env = GraphicsEnvironment.getLocalGraphicsEnvironment()
    val config = env.screenDevices.map { it.defaultConfiguration }.firstOrNull { it.bounds.contains(anchor.x, anchor.y) }
        ?: env.defaultScreenDevice.defaultConfiguration
    val bounds = config.bounds
    val insets = runCatching { Toolkit.getDefaultToolkit().getScreenInsets(config) }.getOrNull()
        ?: return bounds
    return Rectangle(
        bounds.x + insets.left,
        bounds.y + insets.top,
        bounds.width - insets.left - insets.right,
        bounds.height - insets.top - insets.bottom,
    )
}

/**
 * The tray's menu in the app's own look (the palette, the roundness, the fonts) instead of the OS's
 * native menu: a borderless, always-on-top dialog window (an owner-less dialog never shows a taskbar
 * button) opened next to the click, dismissed on a pick, on Escape or as soon as it loses the focus
 * (a click anywhere else — the native menu's behavior).
 */
@Composable
fun TrayMenuWindow(anchor: TrayAnchor, model: TrayMenuModel, onDismiss: () -> Unit) {
    val appearance by TrayMenuAppearance.current.collectAsState()
    val style = appearance ?: return
    val height = trayMenuHeightDp(model)
    val position = remember(anchor, height) { trayMenuPosition(anchor, TRAY_MENU_WIDTH_DP, height, usableScreenArea(anchor)) }
    val state = rememberDialogState(
        position = WindowPosition(position.x.dp, position.y.dp),
        size = DpSize(TRAY_MENU_WIDTH_DP.dp, height.dp),
    )
    DialogWindow(
        onCloseRequest = onDismiss,
        state = state,
        title = model.title,
        undecorated = true,
        transparent = true,
        resizable = false,
        onPreviewKeyEvent = { event ->
            if (event.key == Key.Escape) {
                onDismiss()
                true
            } else {
                false
            }
        },
    ) {
        DisposableEffect(window) {
            // The native menu's behavior: a click anywhere else closes it
            val focus = object : WindowFocusListener {
                override fun windowGainedFocus(e: WindowEvent?) = Unit
                override fun windowLostFocus(e: WindowEvent?) = onDismiss()
            }
            window.isAlwaysOnTop = true
            window.addWindowFocusListener(focus)
            onDispose { window.removeWindowFocusListener(focus) }
        }
        LaunchedEffect(window) {
            window.toFront()
            window.requestFocus()
        }
        CompositionLocalProvider(LocalAppearance provides style) {
            TrayMenuContent(style, model, onDismiss)
        }
    }
}

@Composable
private fun TrayMenuContent(style: Appearance, model: TrayMenuModel, onDismiss: () -> Unit) {
    val palette = style.colorPalette
    val shape = style.uiRoundnessShape
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(shape)
            .background(palette.background1, shape)
            .border(1.dp, palette.background2, shape)
            .padding(TRAY_MENU_PADDING_DP.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            BasicText(
                text = model.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = style.typography.xs.semiBold.copy(color = palette.textSecondary),
                modifier = Modifier.height(TRAY_MENU_TITLE_DP.dp).padding(horizontal = 12.dp, vertical = 14.dp),
            )
            model.top.forEach { TrayMenuRow(style, it, onDismiss) }
            if (model.shortcuts.isNotEmpty()) {
                TrayMenuSeparator(style)
                model.shortcuts.forEach { TrayMenuRow(style, it, onDismiss) }
            }
            TrayMenuSeparator(style)
            model.bottom.forEach { TrayMenuRow(style, it, onDismiss) }
        }
    }
}

@Composable
private fun TrayMenuRow(style: Appearance, entry: TrayMenuEntry, onDismiss: () -> Unit) {
    val palette = style.colorPalette
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(TRAY_MENU_ROW_DP.dp)
            .clip(style.uiRoundnessShape)
            .clickable {
                onDismiss()
                entry.onClick()
            }
            .padding(horizontal = 12.dp),
    ) {
        if (entry.icon != null && !entry.tinted) {
            // A full-color icon (the app logo): a tint would flatten it into a solid square
            Image(
                painter = painterResource(entry.icon),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
        } else if (entry.icon != null) {
            Icon(
                painter = painterResource(entry.icon),
                contentDescription = null,
                tint = palette.text,
                modifier = Modifier.size(18.dp),
            )
        } else {
            Spacer(Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        BasicText(
            text = entry.label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = style.typography.xs.medium.copy(color = palette.text),
        )
    }
}

@Composable
private fun TrayMenuSeparator(style: Appearance) {
    Box(modifier = Modifier.fillMaxWidth().height(TRAY_MENU_SEPARATOR_DP.dp), contentAlignment = Alignment.Center) {
        Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp).height(1.dp).background(style.colorPalette.background2))
    }
}
