package app.n_zik.compagnon.components.ui.screens.home

import app.n_zik.compagnon.enums.HomeScreenTabs
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * The home's start tab ([resolveInitialTabIndex], the phone's `HomeScreen.kt` 82-102 without its
 * launcher shortcut): the preferred tab's index maps to a home tab id and is looked up among the
 * visible tabs; [HomeScreenTabs.Default] and [HomeScreenTabs.QuickPics] resolve to the phone's
 * Quick Picks id, which the PC has no tab for, so the first visible tab; an absent tab falls back
 * to the first one.
 */
class ResolveInitialTabIndexTest {

    // The PC's visible home tabs, in the phone's ids
    private val tabs = listOf("songs", "artists", "albums", "playlists")

    @Test
    fun `the default start tab is the first visible tab`() {
        assertEquals(0, resolveInitialTabIndex(HomeScreenTabs.Default, tabs))
    }

    @Test
    fun `quick picks resolves to the first visible tab (absent on the PC)`() {
        assertEquals(0, resolveInitialTabIndex(HomeScreenTabs.QuickPics, tabs))
    }

    @Test
    fun `each start tab opens its own tab`() {
        assertEquals(0, resolveInitialTabIndex(HomeScreenTabs.Songs, tabs))
        assertEquals(1, resolveInitialTabIndex(HomeScreenTabs.Artists, tabs))
        assertEquals(2, resolveInitialTabIndex(HomeScreenTabs.Albums, tabs))
        assertEquals(3, resolveInitialTabIndex(HomeScreenTabs.Playlists, tabs))
    }

    @Test
    fun `an absent tab falls back to the first one`() {
        // "search" is not a home tab on the PC
        assertEquals(0, resolveInitialTabIndex(HomeScreenTabs.Search, tabs))
        // A tab the screen does not offer
        assertEquals(0, resolveInitialTabIndex(HomeScreenTabs.Playlists, listOf("songs", "artists")))
    }

    // The [initialHomeTabIndex] precedence (the phone's `rememberPreference` semantics): a
    // persisted last-used tab wins, while a `null` (never written) applies the start tab.

    @Test
    fun `a persisted last-used tab takes precedence over the start tab, including 0`() {
        // 0 persisted + start tab Playlists → the persisted 0
        assertEquals(0, initialHomeTabIndex(0, HomeScreenTabs.Playlists, tabs))
        // Another persisted value is returned as is (the screen's clamp bounds it later)
        assertEquals(2, initialHomeTabIndex(2, HomeScreenTabs.Songs, tabs))
    }

    @Test
    fun `a never-written last-used tab applies the start tab`() {
        assertEquals(3, initialHomeTabIndex(null, HomeScreenTabs.Playlists, tabs))
        assertEquals(0, initialHomeTabIndex(null, HomeScreenTabs.Default, tabs))
    }
}
