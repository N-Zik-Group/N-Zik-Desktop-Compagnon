package app.n_zik.compagnon.components.ui.screens.home

import app.n_zik.compagnon.bridge.library.SongFilter
import app.n_zik.compagnon.bridge.state.SongRecommendations
import app.n_zik.compagnon.bridge.state.Track

/** A row of the Songs tab: a track of the loaded list at [loadedIndex], or a smart-shuffle recommendation of the phone (contract §10.1, since 1.12.0) — `loadedIndex == null`. */
data class SongRow(val track: Track, val loadedIndex: Int?) {
    val isRecommended: Boolean get() = loadedIndex == null
}

/** The phone's recommendations matching the PC's chip: they apply only when the phone's Songs tab is on the same chip ([chipFilter], `null` for a PC-only chip) — an empty list otherwise. */
fun matchingRecommendations(recommendations: SongRecommendations?, chipFilter: SongFilter?): List<Track> =
    if (recommendations != null && chipFilter != null && recommendations.filter == chipFilter.wire) {
        recommendations.items.map { it.track }
    } else {
        emptyList()
    }

/**
 * The rows of the Songs tab, as the phone's `HomeSongs.kt` 446-469 builds its display list: each
 * recommendation is inserted at its `position` bounded to the list built so far (`coerceIn(0, size)`),
 * the ids are kept unique (the first occurrence wins, `distinctBy`), then the search [searchText] keeps
 * the rows whose title or artists contain it — the loaded rows already come filtered by the phone's
 * `query`, so only the recommendations are tested here. A [chipFilter] other than the phone's tab
 * inserts nothing.
 */
fun songRows(
    loaded: List<Track>,
    recommendations: SongRecommendations?,
    chipFilter: SongFilter?,
    searchText: String?,
): List<SongRow> {
    val rows = loaded.mapIndexedTo(ArrayList(loaded.size)) { index, track -> SongRow(track, index) }
    if (recommendations == null || matchingRecommendations(recommendations, chipFilter).isEmpty()) {
        return rows.distinctBy { it.track.id }
    }
    recommendations.items.forEach { item ->
        rows.add(item.position.coerceIn(0, rows.size), SongRow(item.track, null))
    }
    return rows
        .distinctBy { it.track.id }
        .filter { row -> !row.isRecommended || searchText.isNullOrEmpty() || row.track.matches(searchText) }
}

/** The phone's search match (`HomeSongs.kt` 460-464): the title or the artists contain [text], case-insensitive. */
private fun Track.matches(text: String): Boolean =
    title.contains(text, ignoreCase = true) || artists.orEmpty().contains(text, ignoreCase = true)
