package app.n_zik.compagnon.tray

import java.awt.image.BufferedImage
import java.util.logging.Logger
import org.freedesktop.dbus.DBusPath
import org.freedesktop.dbus.Struct
import org.freedesktop.dbus.types.Variant
import org.freedesktop.dbus.annotations.DBusBoundProperty
import org.freedesktop.dbus.annotations.DBusInterfaceName
import org.freedesktop.dbus.annotations.DBusMemberName
import org.freedesktop.dbus.annotations.Position
import org.freedesktop.dbus.connections.impl.DBusConnection
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder
import org.freedesktop.dbus.interfaces.DBus
import org.freedesktop.dbus.interfaces.DBusInterface
import org.freedesktop.dbus.types.UInt32

/** One `IconPixmap` entry (`(iiay)`): ARGB32 pixels in network byte order, row by row. */
class IconPixmap(
    @field:Position(0) val width: Int,
    @field:Position(1) val height: Int,
    @field:Position(2) val data: ByteArray,
) : Struct()

/**
 * The `org.kde.StatusNotifierItem` interface (the freedesktop tray protocol KDE Plasma — X11 AND
 * Wayland — and the GNOME AppIndicator extension host). [menu] is the [DBusMenu] object path on this
 * item's own bus name: the GNOME extension gates the TRAY ICON on it (`isReady = hasNameOwner && id &&
 * menuPath`) and renders the right-click menu from it; hosts without DBusMenu support (X11) fall back
 * to [contextMenu], where the app opens its own styled menu ([TrayMenuWindow]).
 * dbus-java's reflection rules as in `MPRISRoot` (property names strip the `get`: `Category`, `Id` …).
 */
@DBusInterfaceName(SNI_IFACE)
interface StatusNotifierItemIface : DBusInterface {
    @get:DBusBoundProperty
    val category: String

    @get:DBusBoundProperty
    val id: String

    @get:DBusBoundProperty
    val title: String

    @get:DBusBoundProperty
    val status: String

    @get:DBusBoundProperty
    val windowId: Int

    @get:DBusBoundProperty
    val iconName: String

    @get:DBusBoundProperty
    val iconPixmap: List<IconPixmap>

    @get:DBusBoundProperty
    val itemIsMenu: Boolean

    /** The [DBusMenu] object path on this item's own bus name (wire type `o`). */
    @get:DBusBoundProperty
    val menu: DBusPath

    @DBusMemberName("Activate")
    fun activate(x: Int, y: Int)

    @DBusMemberName("SecondaryActivate")
    fun secondaryActivate(x: Int, y: Int)

    @DBusMemberName("ContextMenu")
    fun contextMenu(x: Int, y: Int)

    @DBusMemberName("Scroll")
    fun scroll(delta: Int, orientation: String)
}

/** The tray host's registry (`org.kde.StatusNotifierWatcher`). */
@DBusInterfaceName(SNI_WATCHER_IFACE)
interface StatusNotifierWatcherIface : DBusInterface {
    @DBusMemberName("RegisterStatusNotifierItem")
    fun registerStatusNotifierItem(service: String)
}

/**
 * The Linux tray (StatusNotifierItem over the D-Bus session bus, dbus-java — the same stack as MPRIS):
 * unlike AWT's XEmbed `SystemTray` it also works on Wayland sessions (KDE Plasma natively, GNOME
 * with the AppIndicator extension). A left click ([StatusNotifierItemIface.activate]) re-shows the
 * window. A right click: the hosts that render the DBusMenu themselves (GNOME's AppIndicator, KDE)
 * open the menu from the [DBusMenu] exported next to the item; a host without that support calls
 * [StatusNotifierItemIface.contextMenu] instead, and the app opens its own styled menu at the
 * coordinates the host sends (a Wayland host may send 0,0 — the menu then clamps into the screen).
 *
 * Every D-Bus call runs off the EDT and every failure only logs: the tray is a convenience, it must
 * never break the app (the house no-crash constraint). The callbacks fire on dbus-java's receiving
 * threads — the caller hops them to the EDT.
 */
class StatusNotifierTray(
    private val title: String,
    private val icon: BufferedImage,
    private val onActivate: () -> Unit,
    private val onContextMenu: (TrayAnchor) -> Unit,
    private val menuRows: List<DBusMenuRow>,
) {
    private val log = Logger.getLogger("StatusNotifierTray")
    /** The exported menu object — kept for the live [updateMenuRows] after the registration. */
    private val dbMenu = DBusMenu(menuRows)
    @Volatile
    private var connection: DBusConnection? = null

    private inner class Item : StatusNotifierItemIface {
        override val category: String get() = "ApplicationStatus"
        override val id: String get() = SNI_ID
        override val title: String get() = this@StatusNotifierTray.title
        override val status: String get() = "Active"
        override val windowId: Int get() = 0
        override val iconName: String get() = ""
        override val iconPixmap: List<IconPixmap> = listOf(icon.toIconPixmap())
        override val itemIsMenu: Boolean get() = false
        override val menu: DBusPath get() = DBusPath(DBUS_MENU_PATH)

        override fun activate(x: Int, y: Int) = onActivate()
        override fun secondaryActivate(x: Int, y: Int) = onActivate()
        override fun contextMenu(x: Int, y: Int) = onContextMenu(TrayAnchor(x, y))
        override fun scroll(delta: Int, orientation: String) = Unit

        override fun getObjectPath(): String = SNI_OBJECT_PATH
    }

    /**
     * Exports the item and registers it with the host's watcher (blocking — call off the EDT).
     * `false` when the bus is missing, the static bus name is taken (another instance) or no
     * watcher answers — the caller then falls back to the AWT tray ([TraySetup]).
     */
    fun start(): Boolean {
        if (connection != null) return true
        val conn = runCatching { DBusConnectionBuilder.forSessionBus().build() }.getOrElse {
            log.warning("SNI: no session bus — no tray (${it::class.simpleName})")
            return false
        }
        return try {
            conn.exportObject(dbMenu)
            conn.exportObject(Item())
            conn.requestBusName(SNI_BUS_NAME)
            conn.getRemoteObject(SNI_WATCHER_BUS, SNI_WATCHER_PATH, StatusNotifierWatcherIface::class.java)
                .registerStatusNotifierItem(SNI_BUS_NAME)
            connection = conn
            log.info("SNI: tray item registered as $SNI_BUS_NAME")
            true
        } catch (t: Throwable) {
            runCatching { conn.close() }
            log.warning("SNI: could not register the tray item (${t::class.simpleName}: ${t.message})")
            false
        }
    }

    /**
     * Re-announces the [Item.menu] property with one `PropertiesChanged`. The GNOME AppIndicator
     * extension races name-owner discovery with the first menu layout: when the owner is only found
     * AFTERWARD, it re-fetches the layout but never re-runs the menu attach (its `ready-changed`
     * signal is dropped), so the right-click menu stays empty. A delayed `Menu` announcement makes
     * the extension rebuild the menu client — by then the name owner is known for good, the attach
     * runs, and the menu opens (live-verified 2026-10-12).
     */
    fun announceMenu() {
        val conn = connection ?: return
        val signal = try {
            conn.getMessageFactory().createSignal(
                SNI_BUS_NAME,
                SNI_OBJECT_PATH,
                PROPERTIES_IFACE,
                PROPERTIES_CHANGED,
                "sa{sv}as",
                SNI_IFACE,
                menuAnnouncementPayload(),
                emptyList<String>(),
            )
        } catch (t: Throwable) {
            log.warning("SNI: could not announce the Menu property (${t::class.simpleName})")
            return
        }
        runCatching { conn.sendMessage(signal) }
            .onFailure { log.warning("SNI: could not send the Menu announcement (${it::class.simpleName})") }
    }

    /**
     * The live menu update (the settings changed — the shortcut list or its order): replaces the
     * exported [dbMenu]'s rows and announces the new layout with the `LayoutUpdated` signal — the
     * host re-fetches `GetLayout` right away when its menu is open, and on the next open otherwise.
     * A no-op when the visible menu is unchanged or the registration never happened (the first
     * `GetLayout` then already serves the new rows).
     */
    fun updateMenuRows(rows: List<DBusMenuRow>) {
        val newRevision = dbMenu.updateRows(rows) ?: return
        val conn = connection ?: return
        val signal = runCatching {
            conn.getMessageFactory().createSignal(
                SNI_BUS_NAME,
                DBUS_MENU_PATH,
                DBUS_MENU_IFACE,
                LAYOUT_UPDATED,
                "u",
                UInt32(newRevision),
            )
        }.getOrNull() ?: return
        runCatching { conn.sendMessage(signal) }
            .onFailure { log.warning("SNI: could not announce the menu update (${it::class.simpleName})") }
    }

    /** Removes the item (the bus name goes with the connection). */
    fun stop() {
        val conn = connection ?: return
        connection = null
        runCatching { conn.close() }
    }

    companion object {
        /** The `PropertiesChanged` `a{sv}` of the [announceMenu] signal: `Menu` carries its object path (wire `o`). */
        internal fun menuAnnouncementPayload(): Map<String, Variant<out Any>> =
            mapOf("Menu" to Variant(DBusPath(DBUS_MENU_PATH)))

        /**
         * Whether a StatusNotifierItem host is running on the session bus (blocking, one short D-Bus
         * call). Inside a Flatpak sandbox the watcher name is NOT probeable — xdg-dbusproxy filters
         * every bus name the manifest does not grant — so the manifest's
         * `--talk-name=org.kde.StatusNotifierWatcher` is trusted instead: a host without a watcher
         * then fails the [start] registration, and the caller falls back to the AWT tray.
         */
        fun hostAvailable(): Boolean =
            hostAvailable(System.getenv("DBUS_SESSION_BUS_ADDRESS"), System.getenv("FLATPACK_ID")) { probeWatcher() }

        /** The [hostAvailable] decision from its two inputs (pure — the tests pin it). */
        internal fun hostAvailable(dbusAddress: String?, flatpakId: String?, probe: () -> Boolean): Boolean =
            if (isFlatpakSandbox(dbusAddress, flatpakId)) true else probe()

        /**
         * Whether the process runs inside a Flatpak sandbox: the sandbox's D-Bus proxy bus
         * (`unix:path=/run/flatpak/bus`) or the runtime's `FLATPACK_ID` marker (newer runtimes).
         */
        internal fun isFlatpakSandbox(dbusAddress: String?, flatpakId: String?): Boolean =
            flatpakId?.isNotBlank() == true || dbusAddress?.contains("/run/flatpak/bus") == true

        private fun probeWatcher(): Boolean = runCatching {
            DBusConnectionBuilder.forSessionBus().build().use { conn ->
                conn.getRemoteObject(DBUS_BUS, DBUS_PATH, DBus::class.java).NameHasOwner(SNI_WATCHER_BUS)
            }
        }.getOrDefault(false)
    }
}

/** [this] as the SNI pixmap: ARGB32, network (big-endian) byte order. */
internal fun BufferedImage.toIconPixmap(): IconPixmap {
    val bytes = ByteArray(width * height * 4)
    var i = 0
    for (y in 0 until height) {
        for (x in 0 until width) {
            val argb = getRGB(x, y)
            bytes[i++] = (argb ushr 24).toByte()
            bytes[i++] = (argb ushr 16).toByte()
            bytes[i++] = (argb ushr 8).toByte()
            bytes[i++] = argb.toByte()
        }
    }
    return IconPixmap(width, height, bytes)
}

private const val SNI_IFACE = "org.kde.StatusNotifierItem"
private const val PROPERTIES_IFACE = "org.freedesktop.DBus.Properties"
private const val PROPERTIES_CHANGED = "PropertiesChanged"
/** The `com.canonical.dbusmenu` layout-change signal (the live menu update — [updateMenuRows]). */
private const val LAYOUT_UPDATED = "LayoutUpdated"
private const val SNI_WATCHER_IFACE = "org.kde.StatusNotifierWatcher"
private const val SNI_WATCHER_BUS = "org.kde.StatusNotifierWatcher"
private const val SNI_WATCHER_PATH = "/StatusNotifierWatcher"
private const val SNI_OBJECT_PATH = "/StatusNotifierItem"
private const val SNI_ID = "nzik-desktop-compagnon"
private const val DBUS_BUS = "org.freedesktop.DBus"
private const val DBUS_PATH = "/org/freedesktop/DBus"

/**
 * The SNI item's bus name: STATIC, not the spec's `org.kde.StatusNotifierItem-<pid>-<n>` — the
 * Flatpak sandbox's D-Bus proxy can only be granted static names (`--own-name`), and a dynamic
 * pid-based name would keep the tray dead inside the sandbox. A second app instance then finds the
 * name taken: its registration fails and it falls back to the AWT tray. Pinned by FlatpakPinTest.
 */
internal const val SNI_BUS_NAME = "org.kde.StatusNotifierItem.nzik_desktop_compagnon"
