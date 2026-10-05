package app.n_zik.compagnon.components.ui.screens.home

import app.n_zik.compagnon.bridge.library.SongSort
import app.n_zik.compagnon.bridge.library.TopPeriod
import app.n_zik.compagnon.components.legacySongSortOptions
import app.n_zik.compagnon.components.songSortOptions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * The chip's sort menu (contract 1.7.3 `library.sortMenu`): the phone serves its effective menu
 * (content and order); the static options stand in without it.
 */
class ChipSortOptionsTest {

    @Test
    fun `the phone's menu is shown as-is, content and order`() {
        val options = chipSortOptions(SongsChip.All, sortsOnPhone = true, sortMenu = listOf("artist", "playCount"))
        assertEquals(listOf(SongSort.Artist, SongSort.PlayCount), options.map { it.value })
    }

    @Test
    fun `the unknown ids of the phone's menu are dropped, the rest kept in its order`() {
        // The phone's Top menu mixes its periods (its `StatisticsType` names) in its sort ids
        val options = chipSortOptions(SongsChip.All, sortsOnPhone = true, sortMenu = listOf("OneWeek", "custom", "bogus", "title"))
        assertEquals(listOf(SongSort.Custom, SongSort.Title), options.map { it.value })
    }

    @Test
    fun `an empty or absent served menu falls back to the static options`() {
        assertEquals(songSortOptions, chipSortOptions(SongsChip.All, sortsOnPhone = true, sortMenu = emptyList()))
        assertEquals(songSortOptions, chipSortOptions(SongsChip.All, sortsOnPhone = true, sortMenu = null))
        // A served menu of nothing but unknown ids also falls back
        assertEquals(songSortOptions, chipSortOptions(SongsChip.All, sortsOnPhone = true, sortMenu = listOf("OneWeek", "bogus")))
    }

    @Test
    fun `a phone without library sort keeps the static legacy options`() {
        assertEquals(legacySongSortOptions, chipSortOptions(SongsChip.All, sortsOnPhone = false, sortMenu = listOf("artist")))
        assertEquals(legacySongSortOptions, chipSortOptions(SongsChip.All, sortsOnPhone = false, sortMenu = null))
    }

    @Test
    fun `the top menu shows the phone periods as-is, the unknown ids dropped`() {
        assertEquals(
            listOf(TopPeriod.Week, TopPeriod.Month, TopPeriod.AllTime),
            topPeriodOptions(listOf("week", "month", "bogus", "all")),
        )
    }

    @Test
    fun `the top menu falls back to the static periods`() {
        assertEquals(TopPeriod.entries, topPeriodOptions(null))
        assertEquals(TopPeriod.entries, topPeriodOptions(emptyList()))
        assertEquals(TopPeriod.entries, topPeriodOptions(listOf("bogus")))
    }

    @Test
    fun `downloaded is hidden on the downloaded and cached chips, the phone menu included`() {
        val menu = listOf("title", "downloaded", "artist")
        val tel = chipSortOptions(SongsChip.DownloadTel, sortsOnPhone = true, sortMenu = menu)
        assertEquals(listOf(SongSort.Title, SongSort.Artist), tel.map { it.value })
        val pc = chipSortOptions(SongsChip.CachedPc, sortsOnPhone = true, sortMenu = menu)
        assertEquals(listOf(SongSort.Title, SongSort.Artist), pc.map { it.value })
        val all = chipSortOptions(SongsChip.All, sortsOnPhone = true, sortMenu = menu)
        assertEquals(listOf(SongSort.Title, SongSort.Downloaded, SongSort.Artist), all.map { it.value })
    }
}
