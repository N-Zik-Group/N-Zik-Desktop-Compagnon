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
)

/** `Artist` (contract §1.1, since 1.1). */
@Serializable
data class Artist(
    val id: String,
    val name: String = "",
    val trackCount: Int = 0,
    val hasArtwork: Boolean = false,
)

/** `filter` of `GET /library/songs` (contract §10.1). */
enum class SongFilter(val wire: String) {
    All("all"),
    Liked("liked"),
    Local("local"),
    Downloaded("downloaded"),
}

/** `sort` of `GET /library/songs` (contract §10.1). */
enum class SongSort(val wire: String) {
    Title("title"),
    Artist("artist"),
    PlayTime("playTime"),
}

/** `filter` of `GET /library/albums` and `GET /library/artists` (contract §10.1). */
enum class CollectionFilter(val wire: String) {
    Library("library"),
    Bookmarked("bookmarked"),
}

/** The three kinds of collection whose tracks can be listed, with their path segment under `/library`. */
enum class CollectionKind(val segment: String) {
    Playlist("playlists"),
    Album("albums"),
    Artist("artists"),
}

/** A playlist, album or artist, designated by its opaque id (contract §1: never parsed). */
data class CollectionRef(val kind: CollectionKind, val id: String)

/** The query of the Songs list: text, filter and sort. */
data class SongsQuery(
    val text: String? = null,
    val filter: SongFilter = SongFilter.All,
    val sort: SongSort = SongSort.Title,
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
