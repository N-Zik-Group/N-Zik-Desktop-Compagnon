package app.n_zik.compagnon.components.ui.screens.home

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

/**
 * The chip's toolbar (contract 1.8.0 `library.toolbar`): the phone serves its effective toolbar
 * (content and order); the static default buttons stand in without it. "Refresh" is added by the
 * screen after every id, always last.
 */
class ChipToolbarIdsTest {

    /** The static default toolbar of the All chip (the phone's `tabAvailableIds`, 19 of the 20 buttons). */
    private val allStatic = listOf(
        "sort", "position_lock", "match", "search", "sync_ytm_likes", "locator",
        "download_all", "delete_downloads", "shuffle", "smart_shuffle", "item_selector",
        "play_next", "enqueue", "add_to_favorite", "add_to_playlist",
        "import_menu", "export_dialog", "update", "smart_trash",
    )

    @Test
    fun `the phone's toolbar is shown as-served, order included`() {
        assertEquals(listOf("locator", "search"), chipToolbarIds(SongsChip.All, toolbarFeature = true, served = listOf("locator", "search")))
    }

    @Test
    fun `the unknown ids of the phone's toolbar are dropped, the rest kept in its order`() {
        assertEquals(
            listOf("locator", "search"),
            chipToolbarIds(SongsChip.All, toolbarFeature = true, served = listOf("locator", "bogus", "search")),
        )
    }

    @Test
    fun `an absent or empty served toolbar falls back to the static buttons`() {
        assertEquals(allStatic, chipToolbarIds(SongsChip.All, toolbarFeature = true, served = null))
        assertEquals(allStatic, chipToolbarIds(SongsChip.All, toolbarFeature = true, served = emptyList()))
        // A served list of nothing but unknown ids also falls back
        assertEquals(allStatic, chipToolbarIds(SongsChip.All, toolbarFeature = true, served = listOf("bogus")))
    }

    @Test
    fun `a phone without the feature keeps the static buttons, its served list ignored`() {
        assertEquals(allStatic, chipToolbarIds(SongsChip.All, toolbarFeature = false, served = listOf("locator")))
        assertEquals(allStatic, chipToolbarIds(SongsChip.All, toolbarFeature = false, served = null))
    }

    @Test
    fun `the static fallback keeps each chip's own phone set`() {
        // The Top chip has no position_lock, the OnDevice chip no match (the phone's tabAvailableIds)
        val top = chipToolbarIds(SongsChip.Top, toolbarFeature = false, served = null)
        assertEquals(16, top.size)
        assertFalse("position_lock" in top)
        val onDevice = chipToolbarIds(SongsChip.OnDevice, toolbarFeature = false, served = null)
        assertEquals(11, onDevice.size)
        assertEquals(
            listOf("sort", "position_lock", "search", "locator", "shuffle", "smart_shuffle",
                "item_selector", "play_next", "enqueue", "add_to_favorite", "add_to_playlist"),
            onDevice,
        )
        assertFalse("match" in onDevice)
    }
}
