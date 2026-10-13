package app.n_zik.compagnon

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Spec `spec-settings-navigation` NAV-2 / NAV-4 / NAV-7 / NAV-8: the navigation pages replace the
 * home one at a time (the settings tab reset to General when the other page opens them); back closes
 * the update sub-page first, then the settings or the Serveur PC page; a press on the active header
 * button does nothing; the logo closes every page.
 */
class NavPageStateTest {

    @Test
    fun `back closes the update sub-page first, then the settings page with its tab reset`() {
        val pages = NavPageState.closed.onSettingsPress().openUpdate()

        val updateClosed = pages.back()
        assertTrue(updateClosed!!.settingsOpen)
        assertFalse(updateClosed.updateOpen)
        assertEquals(NavPageState.ABOUT_TAB, updateClosed.settingsTab)

        val settingsClosed = updateClosed.back()
        assertFalse(settingsClosed!!.anyPageOpen)
        assertEquals(0, settingsClosed.settingsTab)
        assertNull(settingsClosed.back())
    }

    @Test
    fun `back closes the Serveur PC page when the settings are closed`() {
        val pages = NavPageState.closed.onPhonePress()

        val closed = pages.back()
        assertFalse(closed!!.anyPageOpen)
        assertNull(closed.back())
    }

    @Test
    fun `with no page open the back is not used`() {
        assertNull(NavPageState.closed.back())
    }

    @Test
    fun `the settings and the Serveur PC page replace each other, the tab reset to General`() {
        val fromPhone = NavPageState.closed.onPhonePress().onSettingsPress()
        assertTrue(fromPhone.settingsOpen)
        assertFalse(fromPhone.phoneOpen)
        assertEquals(0, fromPhone.settingsTab)

        // A tab switch moves the tab only
        val tabbed = fromPhone.onTabChanged(3)
        assertEquals(3, tabbed.settingsTab)

        // The Serveur PC page then replaces the settings (with its update sub-page if open)
        val backToPhone = tabbed.openUpdate().onPhonePress()
        assertTrue(backToPhone.phoneOpen)
        assertFalse(backToPhone.settingsOpen)
        assertFalse(backToPhone.updateOpen)
    }

    @Test
    fun `a press on the active header button does nothing`() {
        val settings = NavPageState.closed.onSettingsPress().onTabChanged(2)
        assertEquals(settings, settings.onSettingsPress())

        val phone = NavPageState.closed.onPhonePress()
        assertEquals(phone, phone.onPhonePress())
    }

    @Test
    fun `the update opens over the settings on its About tab`() {
        val pages = NavPageState.closed.onSettingsPress().onTabChanged(NavPageState.ABOUT_TAB)

        val updated = pages.openUpdate()
        assertTrue(updated.updateOpen)
        assertTrue(updated.settingsOpen)
        assertEquals(NavPageState.ABOUT_TAB, updated.settingsTab)
    }

    @Test
    fun `the logo closes every page and resets the tab`() {
        val pages = NavPageState.closed.onSettingsPress().onTabChanged(NavPageState.ABOUT_TAB).openUpdate()

        assertEquals(NavPageState.closed, pages.closeAll())
    }
}
