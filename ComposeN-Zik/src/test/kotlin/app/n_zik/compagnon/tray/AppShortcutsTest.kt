package app.n_zik.compagnon.tray

import app.n_zik.compagnon.components.ui.screens.home.LibraryTab
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The phone's app-shortcut rules (its `ShortcutIconSync.kt` / `AppShortcutsSettingsDialog.kt`), on the PC tray. */
class AppShortcutsTest {

    @Test
    fun `nothing stored gives the phone's defaults`() {
        assertEquals(
            listOf(AppShortcut.Albums, AppShortcut.Artists, AppShortcut.Library, AppShortcut.Rescue),
            resolveActiveShortcuts(null, null),
        )
    }

    @Test
    fun `the stored order wins and missing or unknown ids are repaired`() {
        val config = parseShortcutConfig("rescue,bogus,search,search", "search")
        assertEquals(listOf("rescue", "search", "home", "quick_picks", "songs", "albums", "artists", "library", "profiles"), config.order)
        // Rescue is always enabled
        assertEquals(setOf("search", "rescue"), config.enabled)
    }

    @Test
    fun `the PC has no cap — every enabled shortcut shows`() {
        val all = ALL_SHORTCUT_IDS.joinToString(",")
        val active = resolveActiveShortcuts(all, all)
        assertEquals(AppShortcut.entries.toList(), active)
    }

    @Test
    fun `Rescue cannot be unchecked`() {
        val rows = ShortcutRows.defaults()
        assertEquals(ShortcutToggle.Locked, rows.toggle(rows.order.indexOf("rescue"), false))
    }

    @Test
    fun `a fifth and sixth check are accepted`() {
        val defaults = ShortcutRows.defaults() // 4 checked
        val five = (defaults.toggle(defaults.order.indexOf("search"), true) as ShortcutToggle.Applied).rows
        val six = (five.toggle(five.order.indexOf("profiles"), true) as ShortcutToggle.Applied).rows
        assertEquals(6, six.checked.count { it })
    }

    @Test
    fun `a toggle and a move update the csv values`() {
        val defaults = ShortcutRows.defaults()
        val unchecked = (defaults.toggle(defaults.order.indexOf("albums"), false) as ShortcutToggle.Applied).rows
        val moved = unchecked.move(unchecked.order.indexOf("rescue"), 0) // rescue first
        assertEquals("rescue,home,quick_picks,songs,search,albums,artists,library,profiles", moved.orderCsv())
        assertEquals("rescue,artists,library", moved.enabledCsv())
        // Out-of-range moves are no-ops
        assertEquals(moved, moved.move(0, -1))
    }

    @Test
    fun `the dialog opens on the tray's resolution`() {
        val rows = ShortcutRows.load(null, null)
        assertEquals(ALL_SHORTCUT_IDS, rows.order)
        assertEquals(listOf(false, false, false, false, true, true, true, false, true), rows.checked)
    }

    @Test
    fun `every shortcut icon is a parseable vector with no Android resource reference`() {
        // A non-UTF-8 byte or an `@android:color/...` reference crashes the tray menu at runtime
        // (both observed live 2026-10-12): Compose Desktop parses the XML when the icon first draws
        val dir = java.io.File("src/main/composeResources/drawable")
        AppShortcut.entries.forEach { shortcut ->
            val file = dir.resolve("shortcut_${shortcut.id}.xml")
            assertTrue(file.isFile, "missing ${file.name}")
            val doc = javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
            assertEquals("vector", doc.documentElement.tagName, file.name)
            assertTrue("@android:" !in file.readText() && "@color/" !in file.readText(), "${file.name} references an Android resource")
        }
    }

    @Test
    fun `the home and its tabs are the PC destinations`() {
        assertEquals(ShortcutDestination.Home, AppShortcut.Home.destination)
        assertEquals(ShortcutDestination.Tab(LibraryTab.Songs), AppShortcut.Songs.destination)
        assertEquals(ShortcutDestination.Tab(LibraryTab.Albums), AppShortcut.Albums.destination)
        assertEquals(ShortcutDestination.Tab(LibraryTab.Artists), AppShortcut.Artists.destination)
        assertEquals(ShortcutDestination.Tab(LibraryTab.Playlists), AppShortcut.Library.destination)
        // Inert: no PC destination
        assertNull(AppShortcut.QuickPicks.destination)
        assertNull(AppShortcut.Search.destination)
        assertNull(AppShortcut.Profiles.destination)
        assertNull(AppShortcut.Rescue.destination)
        assertTrue(AppShortcut.Rescue.locked)
    }
}
