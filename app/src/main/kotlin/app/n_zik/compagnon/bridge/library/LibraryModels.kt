package app.n_zik.compagnon.bridge.library

import kotlinx.serialization.Serializable

/** Library constants of contract v1 §1, §9, §10 (read from the BMAD workspace, never copied here). */
object LibraryContract {
    /** Story 11b: the lists load by pages of 100 (contract §1 bounds: `limit` 1–200). */
    const val PAGE_SIZE = 100

    /** §10.1: `query` length bound. */
    const val QUERY_MAX_LENGTH = 100

    /** §9 / §14: `trackIds` holds 1 to 500 ids. */
    const val TRACK_IDS_MAX = 500

    /** §10.1: default artwork side (64–1200 px, the server may ignore it). */
    const val ARTWORK_SIZE_PX = 544
    const val ARTWORK_SIZE_MIN_PX = 64
    const val ARTWORK_SIZE_MAX_PX = 1200
}

/** Paginated answer of contract §1: `{ items, total, offset, limit }`. */
@Serializable
data class Page<T>(
    val items: List<T> = emptyList(),
    val total: Int = 0,
    val offset: Int = 0,
    val limit: Int = 0,
)

/** `Playlist` (contract §1.1). */
@Serializable
data class Playlist(
    val id: String,
    val name: String = "",
    val trackCount: Int = 0,
    val artworkTrackId: String? = null,
)

/** `Album` (contract §1.1, since 1.1). */
@Serializable
data class Album(
    val id: String,
    val title: String = "",
    val artists: String? = null,
    val year: String? = null,
    val trackCount: Int = 0,
    val hasArtwork: Boolean = false,
    /** Since 1.3: the album is bookmarked on the phone; a 1.2 phone does not send it (→ `false`). */
    val isBookmarked: Boolean = false,
)

/** `Artist` (contract §1.1, since 1.1). */
@Serializable
data class Artist(
    val id: String,
    val name: String = "",
    val trackCount: Int = 0,
    val hasArtwork: Boolean = false,
    /** Since 1.3: the artist is followed (bookmarked) on the phone; a 1.2 phone does not send it (→ `false`). */
    val isBookmarked: Boolean = false,
)

/** `filter` of `GET /library/songs` (contract §10.1; the phone's `BuiltInPlaylist`, since 1.6). */
enum class SongFilter(val wire: String) {
    All("all"),
    Liked("liked"),
    Local("local"),
    Downloaded("downloaded"),
    Disliked("disliked"),
    Offline("offline"),
    Top("top"),
}

/** `sort` of `GET /library/songs` (contract §10.1; the phone's `SongSortBy`, all values since 1.6). */
enum class SongSort(val wire: String) {
    Title("title"),
    Artist("artist"),
    Album("album"),
    Duration("duration"),
    PlayCount("playCount"),
    PlayTime("playTime"),
    RelativePlayTime("relativePlayTime"),
    DateAdded("dateAdded"),
    DatePlayed("datePlayed"),
    DateLiked("dateLiked"),
    Downloaded("downloaded"),
    Custom("custom"),
}

/** `sort` of `GET /library/playlists/{id}/songs` (contract §10.1; the phone's `PlaylistSongSortBy`, since 1.6). */
enum class PlaylistSongSort(val wire: String) {
    Title("title"),
    Artist("artist"),
    Album("album"),
    ArtistAndAlbum("artistAndAlbum"),
    Duration("duration"),
    PlayCount("playCount"),
    PlayTime("playTime"),
    RelativePlayTime("relativePlayTime"),
    DateAdded("dateAdded"),
    DatePlayed("datePlayed"),
    DateLiked("dateLiked"),
    AlbumYear("albumYear"),
    Downloaded("downloaded"),
    Custom("custom"),
}

/** `filter` of `GET /library/albums` and `GET /library/artists` (contract §10.1; `disliked` since 1.6). */
enum class CollectionFilter(val wire: String) {
    Library("library"),
    Bookmarked("bookmarked"),
    Disliked("disliked"),
}

/** `filter` of `GET /library/playlists` (contract §10, since 1.6; the phone's `PlaylistsType` chips). */
enum class PlaylistsFilter(val wire: String) {
    All("all"),
    Pinned("pinned"),
    Rewind("rewind"),
    Youtube("youtube"),
}

/** `period` of `GET /library/songs?filter=top` (contract §10, since 1.6; the phone's `StatisticsType`). */
enum class TopPeriod(val wire: String) {
    Today("today"),
    Week("week"),
    Month("month"),
    ThreeMonths("3months"),
    SixMonths("6months"),
    Year("year"),
    AllTime("all"),
}

/** The three kinds of collection whose tracks can be listed, with their path segment under `/library`. */
enum class CollectionKind(val segment: String) {
    Playlist("playlists"),
    Album("albums"),
    Artist("artists"),
}

/** A playlist, album or artist, designated by its opaque id (contract §1: never parsed). */
data class CollectionRef(val kind: CollectionKind, val id: String)

/**
 * The query of the Songs list: text, filter and sort. With a phone that has `library.sort` (contract 1.6)
 * [reverse] is sent to the phone, which re-sorts; with an older phone it is a display choice of the PC
 * only — the phone's pages are read from their end and shown reversed
 * ([app.n_zik.compagnon.components.ui.screens.home.LibraryLists]).
 */
data class SongsQuery(
    val text: String? = null,
    val filter: SongFilter = SongFilter.All,
    val sort: SongSort = SongSort.Title,
    val reverse: Boolean = false,
    /** The period of the phone's Top tab (contract §10, since 1.6); `null` keeps the phone's own period. */
    val period: TopPeriod? = null,
) {
    companion object {
        /**
         * Trimmed, at most [LibraryContract.QUERY_MAX_LENGTH] characters, `null` when blank. A cut that would
         * split a surrogate pair drops its lone high surrogate.
         */
        fun normalizeText(text: String): String? = text.trim().take(LibraryContract.QUERY_MAX_LENGTH)
            .let { if (it.isNotEmpty() && it.last().isHighSurrogate()) it.dropLast(1) else it }
            .takeIf { it.isNotEmpty() }
    }
}

/** `sort` of `GET /library/albums` (contract §10, since 1.6; the phone's `AlbumSortBy`). */
enum class AlbumSort(val wire: String) {
    Title("title"),
    Artist("artist"),
    Songs("songs"),
    Duration("duration"),
    PlayCount("playCount"),
    ListeningTime("listeningTime"),
    DateAdded("dateAdded"),
    Year("year"),
    Custom("custom"),
}

/** `sort` of `GET /library/artists` (contract §10, since 1.6; the phone's `ArtistSortBy`). */
enum class ArtistSort(val wire: String) {
    Name("name"),
    PlayCount("playCount"),
    ListeningTime("listeningTime"),
    DateAdded("dateAdded"),
    Custom("custom"),
}

/** `sort` of `GET /library/playlists` (contract §10, since 1.6; the phone's `PlaylistSortBy`). */
enum class PlaylistSort(val wire: String) {
    Name("name"),
    SongCount("songCount"),
    ListeningTime("listeningTime"),
    PlayCount("playCount"),
    DateAdded("dateAdded"),
    Custom("custom"),
}

/**
 * The query of the Albums list: [CollectionFilter] plus `sort` and [reverse], sent to the phone
 * (contract §10, since 1.6: unlike songs, the direction is asked to the phone, not applied client-side).
 */
data class AlbumsQuery(
    val filter: CollectionFilter = CollectionFilter.Library,
    val sort: AlbumSort = AlbumSort.Title,
    val reverse: Boolean = false,
)

/** The query of the Artists list (contract §10, since 1.6). */
data class ArtistsQuery(
    val filter: CollectionFilter = CollectionFilter.Library,
    val sort: ArtistSort = ArtistSort.Name,
    val reverse: Boolean = false,
)

/** The query of the Playlists list (contract §10, since 1.6; [filter] the phone's home-tab chips). */
data class PlaylistsQuery(
    val filter: PlaylistsFilter = PlaylistsFilter.All,
    val sort: PlaylistSort = PlaylistSort.Name,
    val reverse: Boolean = false,
)

/**
 * The query of a local playlist's tracks (contract §10.1, since 1.6). Absent `sort` keeps the phone's
 * position order (the phone's default for local playlists).
 */
data class PlaylistSongsQuery(
    val sort: PlaylistSongSort = PlaylistSongSort.Custom,
    val reverse: Boolean = false,
)
