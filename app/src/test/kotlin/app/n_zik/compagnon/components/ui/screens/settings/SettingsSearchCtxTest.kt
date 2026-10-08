package app.n_zik.compagnon.components.ui.screens.settings

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Spec `spec-settings-navigation` NAV-12: the phone's `searchCtx` filter — a section shows when the
 * query is blank or one of its titles (the section card's, its entries') contains it, case-insensitively.
 */
class SettingsSearchCtxTest {

    @Test
    fun `an empty query shows the section`() {
        assertTrue(settingsSearchCtx("", "Cache", "Song cache max size"))
        assertTrue(settingsSearchCtx("   ", "Cache", "Song cache max size"))
    }

    @Test
    fun `a title match shows the section, case-insensitively`() {
        assertTrue(settingsSearchCtx("cache", "Cache", "Song cache max size"))
        assertTrue(settingsSearchCtx("CACHE", "Cache", "Song cache max size"))
        assertTrue(settingsSearchCtx("song cache", "Cache", "Song cache max size"))
        // The card's title counts, as its entries' do
        assertTrue(settingsSearchCtx("languages", "Languages", "App language"))
        assertTrue(settingsSearchCtx("language", "Languages", "App language"))
    }

    @Test
    fun `no title match hides the section`() {
        assertFalse(settingsSearchCtx("zzz", "Cache", "Song cache max size"))
        assertFalse(settingsSearchCtx("quali", "Cache", "Song cache max size"))
    }
}
