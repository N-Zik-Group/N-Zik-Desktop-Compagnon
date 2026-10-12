package app.n_zik.compagnon.tray

import org.freedesktop.dbus.Struct
import org.freedesktop.dbus.Tuple
import org.freedesktop.dbus.annotations.DBusBoundProperty
import org.freedesktop.dbus.annotations.DBusInterfaceName
import org.freedesktop.dbus.annotations.DBusMemberName
import org.freedesktop.dbus.annotations.Position
import org.freedesktop.dbus.interfaces.DBusInterface
import org.freedesktop.dbus.types.UInt32
import org.freedesktop.dbus.types.Variant
import org.jetbrains.compose.resources.DrawableResource
import javax.swing.SwingUtilities
import java.util.concurrent.atomic.AtomicLong

/** The `com.canonical.dbusmenu` interface name — the tray menu the host renders itself. */
const val DBUS_MENU_IFACE = "com.canonical.dbusmenu"

/**
 * The DBusMenu's object path, on the SNI item's OWN bus name (same service, different object). This
 * is the value of the SNI `Menu` property. The GNOME AppIndicator extension gates the TRAY ICON on
 * it — its `_checkIfReady` only fires (`isReady = hasNameOwner && id && menuPath`) when `Menu` is a
 * non-empty object path, and builds its menu client as `(busName = the SNI bus name, path = Menu)`.
 * A host that sees an empty `Menu` shows only the fallback icon and no menu.
 */
const val DBUS_MENU_PATH = "/com/canonical/menu"

/**
 * The `GetLayout` reply — the two return values of the wire tuple `(u, (ia{sv}av))`: the [revision]
 * counter and the tree's root [layout] node. It is a PARAMETERIZED [Tuple] on purpose: dbus-java
 * flattens a [Tuple] return into the method's return tuple, while a plain [Struct] return would be
 * wrapped as `((u(ia{sv}av)))` — the GNOME extension's GDBus proxy type-checks the reply and drops
 * the call (journal: "a renvoyé le type « ((u(ia{sv}av))) », mais « (u(ia{sv}av)) » était attendu"),
 * leaving the menu client empty (live-verified 2026-10-12). [revision] is a wire `u` — dbus-java's
 * [UInt32] (the incoming `Event` `timestamp` is matched the same way: the host's call dispatches on
 * the exact wire signature `isvu`).
 */
class DBusMenuLayout<out A, out B>(
    @field:Position(0) val revision: A,
    @field:Position(1) val layout: B,
) : Tuple()

/** One `GetGroupProperties` entry `(ia{sv})`: an [id] and its [properties]. */
class DBusMenuIdProperties(
    @field:Position(0) val id: Int,
    @field:Position(1) val properties: Map<String, Variant<out Any>>,
) : Struct()

/**
 * One node of the `GetLayout` tree (`(ia{sv}av)`): [properties] is the item's `a{sv}`, [children] is
 * an array of variants, each wrapping a nested node (the menu's recursion). The tray menu is flat:
 * the root's children are the rows, and every row has no children of its own.
 */
class DBusMenuLayoutNode(
    @field:Position(0) val id: Int,
    @field:Position(1) val properties: Map<String, Variant<out Any>>,
    @field:Position(2) val children: List<Variant<DBusMenuLayoutNode>>,
) : Struct()

/**
 * The `com.canonical.dbusmenu` interface — served to the host that reads the SNI `Menu` path (GNOME's
 * AppIndicator extension; KDE Plasma uses the same `Menu` property). Only the members the client
 * actually calls are served, matched to the extension's own `interfaces-xml/DBusMenu.xml`:
 * `GetLayout`, `GetGroupProperties`, `Event`, `AboutToShow` — plus the four read-only properties
 * (the client does not load them; they ride for spec conformance).
 *
 * Wire types the host expects: `GetLayout(ii,as) → (u,(ia{sv}av))`, `GetGroupProperties(ai,as) →
 * a(ia{sv})`, `Event(isvu)`, `AboutToShow(i) → b`. The method SIGNATURES must match exactly —
 * dbus-java dispatches on `(name, signature)` — so the `u` members use [UInt32].
 */
@DBusInterfaceName(DBUS_MENU_IFACE)
interface DBusMenuIface : DBusInterface {
    @get:DBusBoundProperty
    val version: UInt32

    @get:DBusBoundProperty
    val textDirection: String

    @get:DBusBoundProperty
    val status: String

    @get:DBusBoundProperty
    val iconThemePath: List<String>

    @DBusMemberName("GetLayout")
    fun getLayout(parentId: Int, recursionDepth: Int, propertyNames: List<String>): DBusMenuLayout<UInt32, DBusMenuLayoutNode>

    @DBusMemberName("GetGroupProperties")
    fun getGroupProperties(ids: List<Int>, propertyNames: List<String>): List<DBusMenuIdProperties>

    @DBusMemberName("Event")
    fun event(id: Int, eventId: String, data: Variant<*>?, timestamp: UInt32)

    @DBusMemberName("AboutToShow")
    fun aboutToShow(id: Int): Boolean

    override fun getObjectPath(): String
}

/**
 * One row the DBusMenu serves: [id] its STABLE DBusMenu id (the host reuses its items by id — see
 * [TrayMenuEntry]), [label] the item's text, [onClick] its action, [separator] a divider (no
 * label, empty action), [icon] the item's icon as `icon-data` (a PNG in memory — the host draws it
 * without any icon theme; `null` for a text-only row). The rows sit under the root (id 0) in
 * display order, keeping their own ids.
 */
data class DBusMenuRow(val id: Int, val label: String, val onClick: () -> Unit, val separator: Boolean = false, val icon: ByteArray? = null)

/** The stable id of the « Open » row (the first top action, always present). */
internal const val TRAY_MENU_ID_OPEN = 1

/** The stable ids of the [TrayActions] rows (the top actions after « Open » — none today). */
internal const val TRAY_MENU_ID_TOP_ACTION_BASE = 100

/** The stable id of the separator before the shortcuts (present only while a shortcut is active). */
internal const val TRAY_MENU_ID_SEP_SHORTCUTS = 200

/** The stable id of the separator before the bottom rows (present only while a bottom row exists). */
internal const val TRAY_MENU_ID_SEP_BOTTOM = 201

/** The stable id base of the shortcut rows ([AppShortcut.trayMenuId] = base + the enum ordinal). */
internal const val TRAY_MENU_ID_SHORTCUT_BASE = 300

/** The stable id of the « Quit » row (the first bottom row, always present). */
internal const val TRAY_MENU_ID_QUIT = 400

/**
 * The [TrayMenuModel] as the DBusMenu's flat rows: the top actions, then the app shortcuts, then the
 * bottom actions — with the same separators as the app's styled menu ([TrayMenuWindow]). Every row
 * carries its STABLE id ([TrayMenuEntry.id] — the host's item recycling depends on it), its label
 * the host-facing one ([nativeRow]) and its icon the `icon-data` PNG. [iconData] resolves a row's
 * icon resource to its `icon-data` PNG (the native menu's icons) — the default `null` keeps the rows
 * text-only (the pure callers and the tests).
 */
internal fun TrayMenuModel.toMenuRows(iconData: (DrawableResource?) -> ByteArray? = { null }): List<DBusMenuRow> = buildList {
    top.forEach { add(nativeRow(it, iconData)) }
    if (shortcuts.isNotEmpty()) {
        add(DBusMenuRow(TRAY_MENU_ID_SEP_SHORTCUTS, SEPARATOR_LABEL, {}, separator = true))
        shortcuts.forEach { add(nativeRow(it, iconData)) }
    }
    if (bottom.isNotEmpty()) {
        add(DBusMenuRow(TRAY_MENU_ID_SEP_BOTTOM, SEPARATOR_LABEL, {}, separator = true))
        bottom.forEach { add(nativeRow(it, iconData)) }
    }
}

/**
 * One native row for [entry]: the [TrayMenuEntry.label] with the host's icon-gap trailing space
 * ([NATIVE_MENU_LABEL_TRAILING_SPACE]) and the row's `icon-data`. The styled menu ([TrayMenuWindow])
 * keeps the plain label — it owns its own layout.
 */
private fun nativeRow(entry: TrayMenuEntry, iconData: (DrawableResource?) -> ByteArray?): DBusMenuRow =
    DBusMenuRow(entry.id, entry.label + NATIVE_MENU_LABEL_TRAILING_SPACE, entry.onClick, icon = iconData(entry.icon))

/**
 * The trailing space served on every native row's label: the host pins the row's icon to the item's
 * right edge and lets the label expand, so the longest label fills the row and butts against its icon
 * (live feedback 2026-10-13: « Centre de Secours »). One space keeps a uniform minimum gap on every
 * row. The host's own padding applies on top, so the gap grows with the label's length.
 */
private const val NATIVE_MENU_LABEL_TRAILING_SPACE = " "

/** The separator row's (unused) label — a separator carries no text. */
private const val SEPARATOR_LABEL = ""

/**
 * The exported `com.canonical.dbusmenu` object: it serves the FLAT tray menu to the host that reads
 * the SNI `Menu` path. `GetLayout` always returns the whole tree (the client requests `parentId 0`,
 * `recursionDepth -1`); `Event` `clicked` is forwarded — through [onEdt] — to the row's
 * [DBusMenuRow.onClick]. The rows are LIVE: [updateRows] replaces them when the settings change and
 * returns the new [revision] — the caller announces it with the `LayoutUpdated` signal, so the host
 * re-fetches the layout (right away when its menu is open, on the next open otherwise).
 */
class DBusMenu(
    rows: List<DBusMenuRow>,
    private val onEdt: (() -> Unit) -> Unit = { block -> SwingUtilities.invokeLater(block) },
) : DBusMenuIface {
    /** The live rows (read by the getters on dbus-java's receiving threads). */
    @Volatile
    private var _rows: List<DBusMenuRow> = rows

    private val rows: List<DBusMenuRow> get() = _rows

    /** stable row id → the row's action (a separator carries an empty action). */
    @Volatile
    private var actions: Map<Int, () -> Unit> = rows.toActions()

    /** The `GetLayout` [revision] counter — bumped by [updateRows] and echoed in the reply. */
    private val revision = AtomicLong(0)

    /**
     * Replaces the rows live (the settings changed — the shortcut list or its order). Returns the
     * new [revision] for the `LayoutUpdated` signal, or `null` when the visible menu is unchanged —
     * the host view only depends on the labels, the separators and the icon data, not on the row
     * actions (which the host never sees).
     */
    fun updateRows(newRows: List<DBusMenuRow>): Long? {
        if (newRows.fingerprint() == _rows.fingerprint()) return null
        _rows = newRows
        actions = newRows.toActions()
        return revision.incrementAndGet()
    }

    override val version: UInt32 get() = UInt32(DBUS_MENU_VERSION.toLong())
    override val textDirection: String get() = "ltr"
    override val status: String get() = "active"
    override val iconThemePath: List<String> get() = emptyList()
    override fun getObjectPath(): String = DBUS_MENU_PATH

    override fun getLayout(parentId: Int, recursionDepth: Int, propertyNames: List<String>): DBusMenuLayout<UInt32, DBusMenuLayoutNode> =
        DBusMenuLayout(revision = UInt32(revision.get()), layout = rootLayout())

    override fun getGroupProperties(ids: List<Int>, propertyNames: List<String>): List<DBusMenuIdProperties> =
        ids.mapNotNull { id ->
            rows.firstOrNull { it.id == id }?.let { DBusMenuIdProperties(id, it.properties()) }
        }

    override fun event(id: Int, eventId: String, data: Variant<*>?, timestamp: UInt32) {
        if (eventId != EVENT_CLICKED) return
        val action = actions[id] ?: return
        // `event` lands on dbus-java's receiving thread; the row's actions touch the Compose window
        // (show/focus, quit) — hop them to the AWT event thread, as the tray's own callbacks do
        onEdt { action() }
    }

    override fun aboutToShow(id: Int): Boolean = false

    private fun rootLayout(): DBusMenuLayoutNode = DBusMenuLayoutNode(
        ROOT_ID,
        mapOf(
            "type" to Variant("submenu"),
            "children-display" to Variant("submenu"),
        ),
        // display order, each row keeping its STABLE id (the host recycles its items by id)
        rows.map { row -> Variant(row.layout(row.id), LAYOUT_NODE_SIG) },
    )

    private fun DBusMenuRow.layout(id: Int): DBusMenuLayoutNode =
        DBusMenuLayoutNode(id, properties(), emptyList())

    private fun DBusMenuRow.properties(): Map<String, Variant<out Any>> =
        if (separator) {
            mapOf("type" to Variant("separator"))
        } else {
            buildMap {
                put("type", Variant("standard"))
                put("label", Variant(label))
                put("enabled", Variant(true))
                put("visible", Variant(true))
                // The item's icon (the `icon-data` property — a PNG in memory: the GNOME extension
                // decodes it straight to a pixbuf, no icon theme or file needed)
                icon?.let { put("icon-data", Variant(it, ICON_DATA_SIG)) }
            }
        }

    /** The host view's fingerprint: the ids, the labels, the separators and the icon data (the row actions never ride on the wire). */
    private fun List<DBusMenuRow>.fingerprint() =
        map { listOf(it.id, it.label, it.separator, it.icon?.contentHashCode()) }

    private fun List<DBusMenuRow>.toActions() =
        associate { row -> row.id to row.onClick }

    private companion object {
        const val ROOT_ID = 0
        const val EVENT_CLICKED = "clicked"
        const val DBUS_MENU_VERSION = 3
        /** The `icon-data` wire type — a PNG byte array (`ay`). */
        const val ICON_DATA_SIG = "ay"
        /** The `GetLayout` node type `(ia{sv}av)` — the recursion forces the explicit sig. */
        const val LAYOUT_NODE_SIG = "(ia{sv}av)"
    }
}
