package app.n_zik.compagnon.tray

import java.util.concurrent.atomic.AtomicBoolean
import kotlin.OptIn
import org.freedesktop.dbus.DBusPath
import org.freedesktop.dbus.types.UInt32
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.InternalResourceApi
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The `com.canonical.dbusmenu` served on the SNI's own bus name: the `GetLayout` tree (flat — root 0
 * with the rows at ids 1..N), `GetGroupProperties` (the known ids' labels), `AboutToShow` (a static
 * menu — nothing changed), the live `updateRows` (the revision + the `LayoutUpdated` trigger), and
 * the `Event` `clicked` routing to the row's action. The icon tests build fake [DrawableResource]s
 * (the test source set does not see the generated `Res` accessors).
 */
@OptIn(InternalResourceApi::class) // the DrawableResource constructor (fake test resources)
class DbusMenuTest {

    private fun testMenu(vararg rows: DBusMenuRow): DBusMenu =
        // a direct dispatch — no AWT event thread in the unit test
        DBusMenu(rows.toList()) { it() }

    @Test
    fun `the layout is the flat tree under root 0`() {
        val menu = testMenu(
            DBusMenuRow(1, "Open", {}),
            DBusMenuRow(200, "", {}, separator = true),
            DBusMenuRow(400, "Quit", {}),
        )
        val result = menu.getLayout(0, -1, listOf("type", "children-display"))
        assertEquals(UInt32(0L), result.revision)
        assertEquals(0, result.layout.id)
        assertEquals("submenu", result.layout.properties.getValue("type").value)
        assertEquals(3, result.layout.children.size)
        // a flat menu: the rows have no children of their own
        result.layout.children.forEach { assertTrue(it.value.children.isEmpty()) }
    }

    @Test
    fun `the rows are standard items with their labels in order`() {
        val menu = testMenu(DBusMenuRow(1, "Open", {}), DBusMenuRow(400, "Quit", {}))
        val root = menu.getLayout(0, -1, emptyList()).layout
        val open = root.children[0].value
        val quit = root.children[1].value
        assertEquals(1, open.id)
        assertEquals("Open", open.properties.getValue("label").value)
        assertEquals("standard", open.properties.getValue("type").value)
        assertEquals(true, open.properties.getValue("enabled").value)
        assertEquals(400, quit.id)
        assertEquals("Quit", quit.properties.getValue("label").value)
    }

    @Test
    fun `a separator is a bare separator node`() {
        val menu = testMenu(DBusMenuRow(1, "Open", {}), DBusMenuRow(200, "", {}, separator = true))
        val separator = menu.getLayout(0, -1, emptyList()).layout.children[1].value
        assertEquals(200, separator.id)
        assertEquals("separator", separator.properties.getValue("type").value)
        assertFalse(separator.properties.containsKey("label"))
    }

    @Test
    fun `the group properties answer only the known ids`() {
        val menu = testMenu(DBusMenuRow(1, "Open", {}), DBusMenuRow(400, "Quit", {}))
        val properties = menu.getGroupProperties(listOf(1, 400, 99), emptyList())
        assertEquals(listOf(1, 400), properties.map { it.id })
        assertEquals("Quit", properties[1].properties.getValue("label").value)
    }

    @Test
    fun `about to show reports nothing changed`() {
        val menu = testMenu(DBusMenuRow(1, "Open", {}))
        assertFalse(menu.aboutToShow(0))
    }

    @Test
    fun `a clicked event runs the row action`() {
        val clicked = AtomicBoolean(false)
        val menu = DBusMenu(listOf(DBusMenuRow(400, "Quit", { clicked.set(true) }))) { it() }
        menu.event(400, "clicked", null, UInt32(0L))
        assertTrue(clicked.get())
    }

    @Test
    fun `an event for an unknown id or another event is dropped`() {
        val clicked = AtomicBoolean(false)
        val menu = DBusMenu(listOf(DBusMenuRow(400, "Quit", { clicked.set(true) }))) { it() }
        menu.event(7, "clicked", null, UInt32(0L))
        menu.event(400, "hover", null, UInt32(0L))
        assertFalse(clicked.get())
    }

    @Test
    fun `the model rows mirror the styled menu separators`() {
        val entry = TrayMenuEntry(1, "x", null) {}
        val model = TrayMenuModel("t", listOf(entry), listOf(entry, entry), listOf(entry))
        val rows = model.toMenuRows()
        // top + separator + the two shortcuts + separator + bottom
        assertEquals(6, rows.size)
        assertTrue(rows[1].separator)
        assertTrue(rows[4].separator)
        assertEquals(listOf(false, true, false, false, true, false), rows.map { it.separator })
    }

    @Test
    fun `the menu announcement carries the object path with the wire o type`() {
        // the PropertiesChanged payload the SNI item re-sends to make the GNOME extension rebuild
        // its menu client (the name-owner race — StatusNotifierTray.announceMenu)
        val payload = StatusNotifierTray.menuAnnouncementPayload()
        val variant = payload.getValue("Menu")
        assertEquals("o", variant.sig)
        assertEquals(DBUS_MENU_PATH, (variant.value as DBusPath).path)
    }

    @Test
    fun `without shortcuts the menu keeps a single separator before the bottom`() {
        val entry = TrayMenuEntry(1, "x", null) {}
        val model = TrayMenuModel("t", listOf(entry), emptyList(), listOf(entry))
        val rows = model.toMenuRows()
        assertEquals(3, rows.size)
        assertEquals(listOf(false, true, false), rows.map { it.separator })
    }

    @Test
    fun `the model rows keep their stable ids through the conversion`() {
        val model = TrayMenuModel(
            "t",
            listOf(TrayMenuEntry(TRAY_MENU_ID_OPEN, "Open", null) {}),
            listOf(TrayMenuEntry(TRAY_MENU_ID_SHORTCUT_BASE + 1, "A", null) {}, TrayMenuEntry(TRAY_MENU_ID_SHORTCUT_BASE + 3, "B", null) {}),
            listOf(TrayMenuEntry(TRAY_MENU_ID_QUIT, "Quit", null) {}),
        )
        val rows = model.toMenuRows()
        // display order with the stable ids: open, separator, the two shortcuts, separator, quit
        assertEquals(listOf(TRAY_MENU_ID_OPEN, TRAY_MENU_ID_SEP_SHORTCUTS, TRAY_MENU_ID_SHORTCUT_BASE + 1, TRAY_MENU_ID_SHORTCUT_BASE + 3, TRAY_MENU_ID_SEP_BOTTOM, TRAY_MENU_ID_QUIT), rows.map { it.id })
    }

    @Test
    fun `the native row labels carry the trailing space the host's icon gap needs`() {
        val model = TrayMenuModel(
            "t",
            listOf(TrayMenuEntry(TRAY_MENU_ID_OPEN, "Open", null) {}),
            emptyList(),
            listOf(TrayMenuEntry(TRAY_MENU_ID_QUIT, "Quit", null) {}),
        )
        val rows = model.toMenuRows()
        // the model labels get the host's icon-gap space on the wire (the separator stays empty —
        // the styled menu keeps the plain labels, it owns its own layout)
        assertEquals(listOf("Open ", "", "Quit "), rows.map { it.label })
    }

    @Test
    fun `stable ids travel with their rows on a live reorder`() {
        val menu = testMenu(
            DBusMenuRow(TRAY_MENU_ID_OPEN, "Open", {}),
            DBusMenuRow(TRAY_MENU_ID_SEP_SHORTCUTS, "", {}, separator = true),
            DBusMenuRow(TRAY_MENU_ID_SHORTCUT_BASE + 1, "A", {}),
            DBusMenuRow(TRAY_MENU_ID_QUIT, "Quit", {}),
        )
        val before = menu.getLayout(0, -1, emptyList()).layout
        assertEquals(listOf(TRAY_MENU_ID_OPEN, TRAY_MENU_ID_SEP_SHORTCUTS, TRAY_MENU_ID_SHORTCUT_BASE + 1, TRAY_MENU_ID_QUIT), before.children.map { it.value.id })
        // the settings changed: a new shortcut appears before A, A moves — the ids travel with
        // their rows (the host recycles its items by id instead of corrupting them)
        val revision = menu.updateRows(
            listOf(
                DBusMenuRow(TRAY_MENU_ID_OPEN, "Open", {}),
                DBusMenuRow(TRAY_MENU_ID_SEP_SHORTCUTS, "", {}, separator = true),
                DBusMenuRow(TRAY_MENU_ID_SHORTCUT_BASE + 2, "B", {}),
                DBusMenuRow(TRAY_MENU_ID_SHORTCUT_BASE + 1, "A", {}),
                DBusMenuRow(TRAY_MENU_ID_QUIT, "Quit", {}),
            ),
        )
        assertEquals(1L, revision)
        val after = menu.getLayout(0, -1, emptyList()).layout
        assertEquals(listOf(TRAY_MENU_ID_OPEN, TRAY_MENU_ID_SEP_SHORTCUTS, TRAY_MENU_ID_SHORTCUT_BASE + 2, TRAY_MENU_ID_SHORTCUT_BASE + 1, TRAY_MENU_ID_QUIT), after.children.map { it.value.id })
        assertEquals("A", after.children[3].value.properties.getValue("label").value)
    }

    @Test
    fun `an unchanged row set updates nothing`() {
        val rows = listOf(DBusMenuRow(1, "Open", {}), DBusMenuRow(200, "", {}, separator = true), DBusMenuRow(400, "Quit", {}))
        val menu = testMenu(*rows.toTypedArray())
        assertNull(menu.updateRows(rows))
        // a structurally different list with the same visible content is a no-op too (the
        // actions differ, but the host never sees them)
        assertNull(menu.updateRows(rows.map { DBusMenuRow(it.id, it.label, {}, it.separator, it.icon) }))
        assertEquals(UInt32(0L), menu.getLayout(0, -1, emptyList()).revision)
    }

    @Test
    fun `a changed row set bumps the revision and the layout follows`() {
        val menu = testMenu(DBusMenuRow(1, "Open", {}), DBusMenuRow(400, "Quit", {}))
        assertEquals(UInt32(0L), menu.getLayout(0, -1, emptyList()).revision)
        val revision = menu.updateRows(listOf(DBusMenuRow(1, "Open", {}), DBusMenuRow(301, "Search", {}), DBusMenuRow(400, "Quit", {})))
        assertEquals(1L, revision)
        val result = menu.getLayout(0, -1, emptyList())
        assertEquals(UInt32(1L), result.revision)
        val root = result.layout
        assertEquals(3, root.children.size)
        assertEquals("Search", root.children[1].value.properties.getValue("label").value)
        // the group properties serve the replacement rows too
        assertEquals("Search", menu.getGroupProperties(listOf(301), emptyList()).first().properties.getValue("label").value)
    }

    @Test
    fun `the clicked event serves the replacement rows actions`() {
        val searched = AtomicBoolean(false)
        val menu = testMenu(DBusMenuRow(1, "Open", {}))
        menu.updateRows(listOf(DBusMenuRow(1, "Open", {}), DBusMenuRow(301, "Search", { searched.set(true) })))
        menu.event(301, "clicked", null, UInt32(0L))
        assertTrue(searched.get())
    }

    @Test
    fun `a changed row icon bumps the revision`() {
        val menu = testMenu(DBusMenuRow(1, "Open", {}, icon = byteArrayOf(1, 2, 3)))
        assertEquals(1L, menu.updateRows(listOf(DBusMenuRow(1, "Open", {}, icon = byteArrayOf(4, 5, 6)))))
    }

    @Test
    fun `a row icon rides as the ay icon data property`() {
        val icon = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)
        val menu = testMenu(DBusMenuRow(1, "Open", {}, icon = icon), DBusMenuRow(400, "Quit", {}))
        val root = menu.getLayout(0, -1, emptyList()).layout
        val data = root.children[0].value.properties.getValue("icon-data")
        assertEquals("ay", data.sig)
        assertArrayEquals(icon, data.value as ByteArray)
        assertFalse(root.children[1].value.properties.containsKey("icon-data"))
    }

    @Test
    fun `the model rows carry their resolved icon data`() {
        val iconA = DrawableResource("a", emptySet())
        val iconB = DrawableResource("b", emptySet())
        val model = TrayMenuModel(
            "t",
            listOf(TrayMenuEntry(1, "A", iconA) {}),
            listOf(TrayMenuEntry(301, "B", iconB) {}),
            emptyList(),
        )
        val bytes = mapOf<DrawableResource, ByteArray>(iconA to byteArrayOf(1), iconB to byteArrayOf(2))
        val rows = model.toMenuRows { icon -> bytes[icon] }
        assertArrayEquals(byteArrayOf(1), rows[0].icon)
        assertNull(rows[1].icon, "the separator carries no icon")
        assertArrayEquals(byteArrayOf(2), rows[2].icon)
    }
}
