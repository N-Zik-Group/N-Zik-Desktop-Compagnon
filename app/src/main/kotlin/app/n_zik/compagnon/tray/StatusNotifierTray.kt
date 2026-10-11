package app.n_zik.compagnon.tray

import java.awt.image.BufferedImage
import java.util.logging.Logger
import org.freedesktop.dbus.Struct
import org.freedesktop.dbus.annotations.DBusBoundProperty
import org.freedesktop.dbus.annotations.DBusInterfaceName
import org.freedesktop.dbus.annotations.DBusMemberName
import org.freedesktop.dbus.annotations.Position
import org.freedesktop.dbus.connections.impl.DBusConnection
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder
import org.freedesktop.dbus.interfaces.DBus
import org.freedesktop.dbus.interfaces.DBusInterface

/** One `IconPixmap` entry (`(iiay)`): ARGB32 pixels in network byte order, row by row. */
class IconPixmap(
    @field:Position(0) val width: Int,
    @field:Position(1) val height: Int,
    @field:Position(2) val data: ByteArray,
) : Struct()

/**
 * The `org.kde.StatusNotifierItem` interface (the freedesktop tray protocol KDE Plasma — X11 AND
 * Wayland — and the GNOME AppIndicator extension host). No `Menu` property: the host then calls
 * [contextMenu] on a right click, and the app opens its own styled menu ([TrayMenuWindow]).
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
 * window, a right click ([StatusNotifierItemIface.contextMenu]) opens the app's styled menu at the
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
) {
    private val log = Logger.getLogger("StatusNotifierTray")
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

        override fun activate(x: Int, y: Int) = onActivate()
        override fun secondaryActivate(x: Int, y: Int) = onActivate()
        override fun contextMenu(x: Int, y: Int) = onContextMenu(TrayAnchor(x, y))
        override fun scroll(delta: Int, orientation: String) = Unit

        override fun getObjectPath(): String = SNI_OBJECT_PATH
    }

    /** Exports the item and registers it with the host's watcher (blocking — call off the EDT). */
    fun start(): Boolean {
        if (connection != null) return true
        val conn = runCatching { DBusConnectionBuilder.forSessionBus().build() }.getOrElse {
            log.warning("SNI: no session bus — no tray (${it::class.simpleName})")
            return false
        }
        return try {
            val busName = "org.kde.StatusNotifierItem-${ProcessHandle.current().pid()}-1"
            conn.exportObject(Item())
            conn.requestBusName(busName)
            conn.getRemoteObject(SNI_WATCHER_BUS, SNI_WATCHER_PATH, StatusNotifierWatcherIface::class.java)
                .registerStatusNotifierItem(busName)
            connection = conn
            log.info("SNI: tray item registered as $busName")
            true
        } catch (t: Throwable) {
            runCatching { conn.close() }
            log.warning("SNI: could not register the tray item (${t::class.simpleName}: ${t.message})")
            false
        }
    }

    /** Removes the item (the bus name goes with the connection). */
    fun stop() {
        val conn = connection ?: return
        connection = null
        runCatching { conn.close() }
    }

    companion object {
        /** Whether a StatusNotifierItem host is running on the session bus (blocking, one short D-Bus call). */
        fun hostAvailable(): Boolean = runCatching {
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
private const val SNI_WATCHER_IFACE = "org.kde.StatusNotifierWatcher"
private const val SNI_WATCHER_BUS = "org.kde.StatusNotifierWatcher"
private const val SNI_WATCHER_PATH = "/StatusNotifierWatcher"
private const val SNI_OBJECT_PATH = "/StatusNotifierItem"
private const val SNI_ID = "nzik-desktop-compagnon"
private const val DBUS_BUS = "org.freedesktop.DBus"
private const val DBUS_PATH = "/org/freedesktop/DBus"
