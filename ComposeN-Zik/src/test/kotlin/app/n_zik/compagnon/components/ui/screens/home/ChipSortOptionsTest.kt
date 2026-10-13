package app.n_zik.compagnon.components.ui.screens.home

import app.n_zik.compagnon.bridge.library.SongSort
import app.n_zik.compagnon.components.legacySongSortOptions
import app.n_zik.compagnon.components.songSortOptions
import app.n_zik.compagnon.enums.OnDeviceSongSortBy
import app.n_zik.compagnon.enums.onDeviceSongSortOptions
import app.n_zik.compagnon.generated.resources.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * The chip's sort options (spec `spec-remove-ui-sync`): the phone's static options — the wire's
 * `sortMenu` is no longer consumed. "Downloaded" is hidden on the downloaded / cached chips, as on
 * the phone; on a phone without `library.sort`, the legacy options stand in. The "On device" chip
 * offers the phone's five `OnDeviceSongSortBy` (spec `spec-fill-pc-ui-gaps-menus` écart 1).
 */
class ChipSortOptionsTest {

    @Test
    fun `a phone with library sort shows the phone's static options`() {
        assertEquals(songSortOptions, chipSortOptions(SongsChip.All, sortsOnPhone = true))
    }

    @Test
    fun `a phone without library sort keeps the static legacy options`() {
        assertEquals(legacySongSortOptions, chipSortOptions(SongsChip.All, sortsOnPhone = false))
    }

    @Test
    fun `downloaded is hidden on the downloaded and cached chips, phone or not`() {
        val tel = chipSortOptions(SongsChip.DownloadTel, sortsOnPhone = true)
        assertEquals(songSortOptions.filter { it.value != SongSort.Downloaded }, tel)
        val pc = chipSortOptions(SongsChip.CachedPc, sortsOnPhone = true)
        assertEquals(songSortOptions.filter { it.value != SongSort.Downloaded }, pc)
        val downloadPc = chipSortOptions(SongsChip.DownloadPc, sortsOnPhone = true)
        assertEquals(songSortOptions.filter { it.value != SongSort.Downloaded }, downloadPc)
        val cachedTel = chipSortOptions(SongsChip.CachedTel, sortsOnPhone = true)
        assertEquals(songSortOptions.filter { it.value != SongSort.Downloaded }, cachedTel)
    }

    @Test
    fun `the other chips keep the full static option list`() {
        assertEquals(songSortOptions, chipSortOptions(SongsChip.Liked, sortsOnPhone = true))
        assertEquals(songSortOptions, chipSortOptions(SongsChip.Top, sortsOnPhone = true))
    }

    @Test
    fun `the on device chip offers the phone's five options, phone or not`() {
        // Écart 1: the phone's `OnDeviceSongSortBy`, not the twelve `SongSortBy`
        assertEquals(onDeviceSongSortOptions, chipSortOptions(SongsChip.OnDevice, sortsOnPhone = true))
        assertEquals(onDeviceSongSortOptions, chipSortOptions(SongsChip.OnDevice, sortsOnPhone = false))
    }

    @Test
    fun `the on device options are the phone's five, in the phone's order with the phone's icons`() {
        assertEquals(
            listOf(
                OnDeviceSongSortBy.Title,
                OnDeviceSongSortBy.DateAdded,
                OnDeviceSongSortBy.Artist,
                OnDeviceSongSortBy.Duration,
                OnDeviceSongSortBy.Album,
            ),
            OnDeviceSongSortBy.entries,
        )
        assertEquals(5, onDeviceSongSortOptions.size)
        // The phone's icons (its `OnDeviceSongSortBy.kt` 17-25)
        assertEquals(
            listOf(
                Res.drawable.text,
                Res.drawable.calendar,
                Res.drawable.artist,
                Res.drawable.time,
                Res.drawable.album,
            ),
            onDeviceSongSortOptions.map { it.iconId },
        )
        // The phone's labels (its `OnDeviceSongSortBy.kt` 17-25)
        assertEquals(
            listOf(
                Res.string.sort_title,
                Res.string.sort_date_added,
                Res.string.sort_artist,
                Res.string.sort_duration,
                Res.string.sort_album,
            ),
            onDeviceSongSortOptions.map { it.labelId },
        )
        // Each option sorts by its own wire value
        assertEquals(
            listOf(
                SongSort.Title,
                SongSort.DateAdded,
                SongSort.Artist,
                SongSort.Duration,
                SongSort.Album,
            ),
            onDeviceSongSortOptions.map { it.value },
        )
    }
}
