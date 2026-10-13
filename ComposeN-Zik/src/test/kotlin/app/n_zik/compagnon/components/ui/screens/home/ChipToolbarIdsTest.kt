package app.n_zik.compagnon.components.ui.screens.home

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

/**
 * The chip's static toolbar (spec `spec-remove-ui-sync`): the phone's default buttons of the chip
 * (its `HomeSongsToolbarSettingsDialog.tabAvailableIds`) — the wire's `toolbar` is no longer
 * consumed, the phone's own user order stays on the phone. "Refresh" is added by the screen after
 * every id, always last.
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
    fun `each chip keeps its own phone default set`() {
        // All and Liked: the phone's default minus the FFmpeg "export cache"
        assertEquals(allStatic, songsToolbarButtonIds(SongsChip.All))
        assertEquals(allStatic, songsToolbarButtonIds(SongsChip.Liked))
    }

    @Test
    fun `the static toolbar keeps the disliked, download and cached chips' own phone sets`() {
        // The Disliked chip has no export_cache, no sync_ytm_likes and no import_menu
        val dislikedStatic = listOf(
            "sort", "position_lock", "match", "search", "locator",
            "download_all", "delete_downloads", "shuffle", "smart_shuffle", "item_selector",
            "play_next", "enqueue", "add_to_favorite", "add_to_playlist",
            "export_dialog", "update", "smart_trash",
        )
        assertEquals(dislikedStatic, songsToolbarButtonIds(SongsChip.Disliked))

        // The download / cached chips (phone and PC) take their phone twin's set
        // (no import_menu, no sync_ytm_likes)
        val downloadStatic = listOf(
            "sort", "position_lock", "match", "search", "locator",
            "download_all", "delete_downloads", "shuffle", "smart_shuffle", "item_selector",
            "play_next", "enqueue", "add_to_favorite", "add_to_playlist",
            "export_dialog", "export_cache", "update", "smart_trash",
        )
        assertEquals(downloadStatic, songsToolbarButtonIds(SongsChip.DownloadTel))
        assertEquals(downloadStatic, songsToolbarButtonIds(SongsChip.DownloadPc))
        assertEquals(downloadStatic, songsToolbarButtonIds(SongsChip.CachedTel))
        assertEquals(downloadStatic, songsToolbarButtonIds(SongsChip.CachedPc))
    }

    @Test
    fun `the top and on-device chips take their own phone sets`() {
        // The Top chip has no position_lock, no import_menu, no sync_ytm_likes
        val top = songsToolbarButtonIds(SongsChip.Top)
        assertEquals(16, top.size)
        assertFalse("position_lock" in top)

        // The OnDevice chip: the phone's 11 buttons, no match, no download, no update
        val onDevice = songsToolbarButtonIds(SongsChip.OnDevice)
        assertEquals(
            listOf("sort", "position_lock", "search", "locator", "shuffle", "smart_shuffle",
                "item_selector", "play_next", "enqueue", "add_to_favorite", "add_to_playlist"),
            onDevice,
        )
        assertFalse("match" in onDevice)
    }
}
