package app.n_zik.compagnon.enums

import app.n_zik.compagnon.bridge.library.SongSort
import app.n_zik.compagnon.components.SortOption
import app.n_zik.compagnon.generated.resources.*
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource

/**
 * Port of `OnDeviceSongSortBy` (phone's `app/it/fast4x/rimusic/enums/OnDeviceSongSortBy.kt` 10-26):
 * the five sort options of the phone's "On device" songs tab, in the phone's order — Title
 * (`sort_title`, `text`), Date Added (`sort_date_added`, `calendar`), Artist (`sort_artist`,
 * `artist`), Duration (`sort_duration`, `time`), Album (`sort_album`, `album`). Each maps to the
 * existing [SongSort] wire value it sorts by ([songSort]) — spec `spec-fill-pc-ui-gaps-menus`
 * écart 1 (the "On device" chip offers these five, not the twelve `SongSortBy`).
 */
enum class OnDeviceSongSortBy(
    val labelId: StringResource,
    val iconId: DrawableResource,
    val songSort: SongSort,
) {
    Title(Res.string.sort_title, Res.drawable.text, SongSort.Title),
    DateAdded(Res.string.sort_date_added, Res.drawable.calendar, SongSort.DateAdded),
    Artist(Res.string.sort_artist, Res.drawable.artist, SongSort.Artist),
    Duration(Res.string.sort_duration, Res.drawable.time, SongSort.Duration),
    Album(Res.string.sort_album, Res.drawable.album, SongSort.Album),
}

/** The five [SortOption]s of the phone's "On device" tab, in the phone's order (écart 1). */
val onDeviceSongSortOptions: List<SortOption<SongSort>> = OnDeviceSongSortBy.entries.map {
    SortOption(it.labelId, it.iconId, it.songSort)
}
