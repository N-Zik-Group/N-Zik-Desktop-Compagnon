package app.n_zik.compagnon.components.ui.screens.home

import app.n_zik.compagnon.bridge.library.SongSort
import app.n_zik.compagnon.components.legacySongSortOptions
import app.n_zik.compagnon.components.songSortOptions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * The chip's sort options (spec `spec-remove-ui-sync`): the phone's static options — the wire's
 * `sortMenu` is no longer consumed. "Downloaded" is hidden on the downloaded / cached chips, as on
 * the phone; on a phone without `library.sort`, the legacy options stand in.
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
        assertEquals(songSortOptions, chipSortOptions(SongsChip.OnDevice, sortsOnPhone = true))
    }
}
