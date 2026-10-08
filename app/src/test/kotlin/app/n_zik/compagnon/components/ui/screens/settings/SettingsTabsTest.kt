package app.n_zik.compagnon.components.ui.screens.settings

import app.n_zik.compagnon.generated.resources.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Spec `spec-settings-navigation` NAV-1 / NAV-3: the 5 sub-tabs of the settings page — the phone's
 * label + icon of each, in the phone's order, About last (the 5th tab).
 */
class SettingsTabsTest {

    @Test
    fun `the five sub-tabs are defined with the phone's labels and icons in the phone's order`() {
        assertEquals(5, SETTINGS_TABS.size)

        assertEquals(Res.string.tab_general, SETTINGS_TABS[0].first)
        assertEquals(Res.drawable.ic_launcher_monochrome, SETTINGS_TABS[0].second)

        assertEquals(Res.string.tab_data, SETTINGS_TABS[1].first)
        assertEquals(Res.drawable.server, SETTINGS_TABS[1].second)

        assertEquals(Res.string.tab_network, SETTINGS_TABS[2].first)
        assertEquals(Res.drawable.network, SETTINGS_TABS[2].second)

        assertEquals(Res.string.tab_miscellaneous, SETTINGS_TABS[3].first)
        assertEquals(Res.drawable.equalizer, SETTINGS_TABS[3].second)

        // About is the last tab (NAV-3)
        assertEquals(Res.string.about, SETTINGS_TABS[4].first)
        assertEquals(Res.drawable.information, SETTINGS_TABS[4].second)
    }
}
