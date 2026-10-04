package app.n_zik.compagnon.bridge.library

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

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

/**
 * Paginated answer of contract §1: `{ items, total, offset, limit }`. Since 1.7.2 [totalDurationMs]
 * carries the total duration in ms of the FULL list, before pagination and before `text` — set by
 * `GET /library/playlists/{id}/songs` (the phone's header duration), `0` on every other route.
 */
@Serializable
data class Page<T>(
    val items: List<T> = emptyList(),
    val total: Int = 0,
    val offset: Int = 0,
    val limit: Int = 0,
    val totalDurationMs: Long = 0L,
)

/**
 * `Playlist.origin` (contract §1.1, since 1.7); an unknown value reads as [Local] (a phone older
 * than 1.7.1 sends the plain [Rewind] for every generated rewind playlist — the legacy wire value).
 */
@Serializable(with = PlaylistOriginSerializer::class)
enum class PlaylistOrigin(val wire: String) {
    Local("local"),
    Ytmusic("ytmusic"),
    Spotify("spotify"),
    Ripley("riplay"),
    /** Since 1.7.1: the generated monthly rewind playlist (the phone's `stat_month` icon). */
    RewindMonthly("rewind-monthly"),
    /** Since 1.7.1: the generated yearly rewind playlist (the phone's `stat_year` icon). */
    RewindYearly("rewind-yearly"),
    /** Since 1.7.1: the generated all-time rewind snapshot (the phone's `musical_notes` icon). */
    RewindAlltime("rewind-alltime"),
    Rewind("rewind");

    companion object {
        fun fromWire(value: String?): PlaylistOrigin = entries.firstOrNull { it.wire == value } ?: Local
    }
}

/** The wire is lowercase (`local`, `ytmusic`…): the default enum-by-name codec would not match it. */
object PlaylistOriginSerializer : KSerializer<PlaylistOrigin> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("PlaylistOrigin", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: PlaylistOrigin) = encoder.encodeString(value.wire)
    override fun deserialize(decoder: Decoder): PlaylistOrigin = PlaylistOrigin.fromWire(decoder.decodeString())
}

/** `Playlist` (contract §1.1). */
@Serializable
data class Playlist(
    val id: String,
    val name: String = "",
    val trackCount: Int = 0,
    val artworkTrackId: String? = null,
    /** Since 1.7: origin of the playlist, for its origin icon; a ≤ 1.6 phone does not send it (→ [PlaylistOrigin.Local]). */
    val origin: PlaylistOrigin = PlaylistOrigin.Local,
    /** Since 1.7: the phone's `pinned:` name prefix; a ≤ 1.6 phone does not send it (→ `false`). */
    val isPinned: Boolean = false,
    /** Since 1.7: saved in the YouTube Music library (the phone's bookmark badge); a ≤ 1.6 phone does not send it (→ `false`). */
    val isBookmarked: Boolean = false,
    /** Since 1.7.1: the phone's `isEditable` — the phone's lock badge on a non-editable YouTube playlist. */
    val isEditable: Boolean = true,
    /** Since 1.7.1: the playlist's play count, from the phone's playback events (the phone's grid overlay). */
    val playCount: Int = 0,
    /** Since 1.7.1: the playlist's total play time in ms, from the phone's playback events (the phone's grid overlay). */
    val totalPlayTimeMs: Long = 0L,
    /**
     * Since 1.7.2: the phone's raw `browseId` — the playlist-menu guards (contract §1.1): the header
     * bookmark is refused for the "special playlists" (its `browseId` minus the `VL` prefix is `LM`
     * or `SE`), the "change id" entry is shown, under [isEditable], on a bookmarked YouTube playlist
     * or one whose `browseId` starts with `modified:` or `VL`. A local playlist without import is
     * `null`; a ≤ 1.7.1 phone does not send it.
     */
    val browseId: String? = null,
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
    /** Since 1.7.1: the album is disliked on the phone (the phone's `bookmark_slash` badge); a ≤ 1.7 phone does not send it (→ `false`). */
    val isDisliked: Boolean = false,
    /** Since 1.7.1: origin of the album, for its origin icon; a ≤ 1.7 phone does not send it (→ [PlaylistOrigin.Local]). */
    val origin: PlaylistOrigin = PlaylistOrigin.Local,
    /** Since 1.7.1: the album's play count, from the phone's playback events (the phone's grid overlay). */
    val playCount: Int = 0,
    /** Since 1.7.1: the album's total play time in ms, from the phone's playback events (the phone's grid overlay). */
    val totalPlayTimeMs: Long = 0L,
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
    /** Since 1.7: the artist is disliked on the phone (the `filter=disliked` list); a ≤ 1.6 phone does not send it (→ `false`). */
    val isDisliked: Boolean = false,
    /** Since 1.7.1: origin of the artist, for its origin icon; a ≤ 1.7 phone does not send it (→ [PlaylistOrigin.Local]). */
    val origin: PlaylistOrigin = PlaylistOrigin.Local,
    /** Since 1.7.1: the artist's play count, from the phone's playback events (the phone's grid overlay). */
    val playCount: Int = 0,
    /** Since 1.7.1: the artist's total play time in ms, from the phone's playback events (the phone's grid overlay). */
    val totalPlayTimeMs: Long = 0L,
)

/** The used vs configured cap of one of the phone's disk caches (contract §10, since 1.7.1). */
@Serializable
data class CacheSpace(
    val usedBytes: Long = 0L,
    /** The configured cap; `null` when it is unlimited (the phone hides its bar in that case). */
    val maxBytes: Long? = null,
    /**
     * Since 1.7.2: the phone's label of its configured cap in its own language (its
     * `ExoPlayerDiskCacheMaxSize.text`: "2GB", "4GB", "Custom", "Turn off", …), `null` when unlimited
     * or on a ≤ 1.7.1 phone (the client then formats [maxBytes] itself).
     */
    val maxText: String? = null,
)

/** The answer of `GET /library/cache` (contract §10, since 1.7.1): the phone's media (streaming) cache and its download cache. */
@Serializable
data class LibraryCache(
    val cached: CacheSpace = CacheSpace(),
    val downloaded: CacheSpace = CacheSpace(),
)

/**
 * Target state of `POST /library/artists/{id}/follow` (contract §10.2, since 1.7); an unknown value
 * reads as [Neutral].
 */
@Serializable(with = ArtistFollowSerializer::class)
enum class ArtistFollow(val wire: String) {
    Followed("followed"),
    Neutral("neutral"),
    Disliked("disliked");

    companion object {
        fun fromWire(value: String?): ArtistFollow = entries.firstOrNull { it.wire == value } ?: Neutral
    }
}

/** The wire is lowercase (`followed`, `neutral`, `disliked`): the default enum-by-name codec would not match it. */
object ArtistFollowSerializer : KSerializer<ArtistFollow> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("ArtistFollow", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: ArtistFollow) = encoder.encodeString(value.wire)
    override fun deserialize(decoder: Decoder): ArtistFollow = ArtistFollow.fromWire(decoder.decodeString())
}

/**
 * The phone's follow rotation (its `rotateLikeState`, default `excludeDislikedArtists`):
 * followed → disliked → neutral → followed.
 */
fun ArtistFollow.nextRotation(): ArtistFollow = when (this) {
    ArtistFollow.Followed -> ArtistFollow.Disliked
    ArtistFollow.Disliked -> ArtistFollow.Neutral
    ArtistFollow.Neutral -> ArtistFollow.Followed
}

/**
 * The phone's binary follow toggle (its `toggleBookmark`, used when its "disliked" mode is off —
 * contract 1.7.2 `library.dislikeMode`): followed → neutral, disliked → followed, neutral → followed.
 */
fun ArtistFollow.nextToggle(): ArtistFollow = when (this) {
    ArtistFollow.Followed -> ArtistFollow.Neutral
    ArtistFollow.Disliked -> ArtistFollow.Followed
    ArtistFollow.Neutral -> ArtistFollow.Followed
}

/**
 * Target state of `POST /library/albums/{id}/like` (contract §10.2, since 1.7.2): the phone's album
 * tri-state (its `AlbumDetails.AlbumBookmark`); an unknown value reads as [Neutral].
 */
@Serializable(with = AlbumLikeSerializer::class)
enum class AlbumLike(val wire: String) {
    Neutral("neutral"),
    Bookmarked("bookmarked"),
    Disliked("disliked");

    companion object {
        fun fromWire(value: String?): AlbumLike = entries.firstOrNull { it.wire == value } ?: Neutral
    }
}

/** The wire is lowercase (`neutral`, `bookmarked`, `disliked`): the default enum-by-name codec would not match it. */
object AlbumLikeSerializer : KSerializer<AlbumLike> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("AlbumLike", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: AlbumLike) = encoder.encodeString(value.wire)
    override fun deserialize(decoder: Decoder): AlbumLike = AlbumLike.fromWire(decoder.decodeString())
}

/**
 * The phone's album tri-state rotation (its `rotateLikeState`, used when its "disliked" mode is on —
 * contract 1.7.2 `library.dislikeMode`): neutral → bookmarked → disliked → neutral.
 */
fun AlbumLike.nextRotation(): AlbumLike = when (this) {
    AlbumLike.Neutral -> AlbumLike.Bookmarked
    AlbumLike.Bookmarked -> AlbumLike.Disliked
    AlbumLike.Disliked -> AlbumLike.Neutral
}

/**
 * The phone's album binary bookmark toggle (used when its "disliked" mode is off — contract 1.7.2
 * `library.dislikeMode`): bookmarked → neutral, otherwise → bookmarked.
 */
fun AlbumLike.nextToggle(): AlbumLike = when (this) {
    AlbumLike.Bookmarked -> AlbumLike.Neutral
    else -> AlbumLike.Bookmarked
}

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

/**
 * `rewind` of `GET /library/playlists` (contract §10, since 1.7.2): the phone's Month / Year / All
 * row of its Rewind chip; an unknown value reads as [Month] (the phone's default).
 */
@Serializable(with = RewindFilterSerializer::class)
enum class RewindFilter(val wire: String) {
    Month("month"),
    Year("year"),
    All("all");

    companion object {
        fun fromWire(value: String?): RewindFilter = entries.firstOrNull { it.wire == value } ?: Month
    }
}

/** The wire is lowercase (`month`, `year`, `all`): the default enum-by-name codec would not match it. */
object RewindFilterSerializer : KSerializer<RewindFilter> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("RewindFilter", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: RewindFilter) = encoder.encodeString(value.wire)
    override fun deserialize(decoder: Decoder): RewindFilter = RewindFilter.fromWire(decoder.decodeString())
}

/**
 * The answer of `GET /library/rewind` (contract §10, since 1.7.2): the phone's Month / Year / All row
 * state — its two rewind-creation toggles and its current filter. The phone's row exists only while
 * both toggles are on (its `rowVisible`).
 */
@Serializable
data class RewindState(
    /** The phone's monthly rewind playlist creation toggle (default `true`). */
    val monthlyEnabled: Boolean = true,
    /** The phone's yearly rewind playlist creation toggle (default `true`). */
    val yearlyEnabled: Boolean = true,
    /** The phone's current Month / Year / All filter (default [RewindFilter.Month]). */
    val filter: RewindFilter = RewindFilter.Month,
) {
    /** The phone's `RewindPlaylists.rowVisible`: the row exists only while both toggles are on. */
    val rowVisible: Boolean get() = monthlyEnabled && yearlyEnabled
}

/**
 * The answer of `GET /library/dislikeMode` (contract §10, since 1.7.2): the phone's "disliked" mode
 * (its `DislikeMode.Enabled`) per collection — `true` shows its Disliked chip and the like rotation,
 * `false` hides the chip and makes the like a binary toggle.
 */
@Serializable
data class DislikeMode(
    val songs: Boolean = true,
    val albums: Boolean = true,
    val artists: Boolean = true,
)

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

/**
 * The query of the Playlists list (contract §10, since 1.6; [filter] the phone's home-tab chips).
 * Since 1.7.2: [rewind] (the phone's Month / Year / All row, applied and persisted by the phone, only
 * with [PlaylistsFilter.Rewind]) and [text] (the phone's search, `total` kept pre-`text`).
 */
data class PlaylistsQuery(
    val filter: PlaylistsFilter = PlaylistsFilter.All,
    val sort: PlaylistSort = PlaylistSort.Name,
    val reverse: Boolean = false,
    val rewind: RewindFilter? = null,
    val text: String? = null,
)

/**
 * The query of a local playlist's tracks (contract §10.1, since 1.6). Absent `sort` keeps the phone's
 * position order (the phone's default for local playlists). Since 1.7.2: [text] (the phone's search,
 * `total` kept pre-`text`).
 */
data class PlaylistSongsQuery(
    val sort: PlaylistSongSort = PlaylistSongSort.Custom,
    val reverse: Boolean = false,
    val text: String? = null,
)
