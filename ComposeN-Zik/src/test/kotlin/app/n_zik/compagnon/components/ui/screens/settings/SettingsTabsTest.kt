package app.n_zik.compagnon.components.ui.screens.settings

import app.n_zik.compagnon.generated.resources.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Spec `spec-settings-navigation` NAV-1 / NAV-3 + `spec-fill-pc-ui-gaps-settings`: the 7 sub-tabs of
 * the settings page — the phone's label + icon of each, in the phone's order, About last (the 7th
 * tab). The 2 new phone tabs (User Interface / Player appearance) are inserted after Général.
 */
class SettingsTabsTest {

    @Test
    fun `the seven sub-tabs are defined with the phone's labels and icons in the phone's order`() {
        assertEquals(7, SETTINGS_TABS.size)

        assertEquals(Res.string.settings_general_title, SETTINGS_TABS[0].first)
        assertEquals(Res.drawable.ic_launcher_monochrome, SETTINGS_TABS[0].second)

        // The 2 new phone tabs (spec `spec-fill-pc-ui-gaps-settings`), after Général, the phone's order
        assertEquals(Res.string.settings_interface_page_title, SETTINGS_TABS[1].first)
        assertEquals(Res.drawable.ui, SETTINGS_TABS[1].second)

        assertEquals(Res.string.player_appearance, SETTINGS_TABS[2].first)
        assertEquals(Res.drawable.color_palette, SETTINGS_TABS[2].second)

        assertEquals(Res.string.settings_data_title, SETTINGS_TABS[3].first)
        assertEquals(Res.drawable.server, SETTINGS_TABS[3].second)

        assertEquals(Res.string.settings_network_title, SETTINGS_TABS[4].first)
        assertEquals(Res.drawable.network, SETTINGS_TABS[4].second)

        assertEquals(Res.string.settings_miscellaneous_title, SETTINGS_TABS[5].first)
        assertEquals(Res.drawable.equalizer, SETTINGS_TABS[5].second)

        // About is the last tab (NAV-3)
        assertEquals(Res.string.about_title, SETTINGS_TABS[6].first)
        assertEquals(Res.drawable.information, SETTINGS_TABS[6].second)
    }
}
