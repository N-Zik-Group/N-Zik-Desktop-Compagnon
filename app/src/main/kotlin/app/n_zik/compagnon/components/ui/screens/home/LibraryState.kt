package app.n_zik.compagnon.components.ui.screens.home

import app.n_zik.compagnon.LocalLibraryActions
import app.n_zik.compagnon.utils.Toaster
import app.n_zik.compagnon.utils.formatMessage
import app.n_zik.compagnon.utils.formatText
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import app.n_zik.compagnon.bridge.command.PlayWindow
import app.n_zik.compagnon.bridge.library.Album
import app.n_zik.compagnon.bridge.library.AlbumLike
import app.n_zik.compagnon.bridge.library.AlbumsQuery
import app.n_zik.compagnon.bridge.library.Artist
import app.n_zik.compagnon.bridge.library.ArtistFollow
import app.n_zik.compagnon.bridge.library.ArtistsQuery
import app.n_zik.compagnon.bridge.library.CollectionFilter
import app.n_zik.compagnon.bridge.library.CollectionKind
import app.n_zik.compagnon.bridge.library.CollectionRef
import app.n_zik.compagnon.bridge.library.DislikeMode
import app.n_zik.compagnon.bridge.library.LibraryCache
import app.n_zik.compagnon.bridge.library.LibraryContract
import app.n_zik.compagnon.bridge.library.LibraryError
import app.n_zik.compagnon.bridge.library.LibraryRepository
import app.n_zik.compagnon.bridge.library.Page
import app.n_zik.compagnon.bridge.library.PagedList
import app.n_zik.compagnon.bridge.library.PagedState
import app.n_zik.compagnon.bridge.library.Playlist
import app.n_zik.compagnon.bridge.library.PlaylistSongsQuery
import app.n_zik.compagnon.bridge.library.PlaylistsFilter
import app.n_zik.compagnon.bridge.library.PlaylistsQuery
import app.n_zik.compagnon.bridge.library.RewindState
import app.n_zik.compagnon.bridge.library.SongFilter
import app.n_zik.compagnon.bridge.library.SongsQuery
import app.n_zik.compagnon.playback.cache.AudioCache
import app.n_zik.compagnon.bridge.state.PlayerRepository
import app.n_zik.compagnon.bridge.state.QueuePosition
import app.n_zik.compagnon.bridge.state.SessionContract
import app.n_zik.compagnon.bridge.state.Track
import app.n_zik.compagnon.bridge.state.TrackLike
import app.n_zik.compagnon.colorPalette
import app.n_zik.compagnon.core.network.LibraryResult
import app.n_zik.compagnon.core.network.WriteResult
import app.n_zik.compagnon.generated.resources.Res
import app.n_zik.compagnon.generated.resources.album
import app.n_zik.compagnon.generated.resources.albums
import app.n_zik.compagnon.generated.resources.artists
import app.n_zik.compagnon.generated.resources.error_unknown_code
import app.n_zik.compagnon.generated.resources.library
import app.n_zik.compagnon.generated.resources.library_error_unreachable
import app.n_zik.compagnon.generated.resources.library_not_found
import app.n_zik.compagnon.generated.resources.library_tracks_failed
import app.n_zik.compagnon.generated.resources.musical_notes
import app.n_zik.compagnon.generated.resources.no_song_found
import app.n_zik.compagnon.generated.resources.no_song_to_shuffle
import app.n_zik.compagnon.generated.resources.paired_error
import app.n_zik.compagnon.generated.resources.paired_other_active
import app.n_zik.compagnon.generated.resources.paired_other_active_unknown
import app.n_zik.compagnon.generated.resources.people
import app.n_zik.compagnon.generated.resources.playlists
import app.n_zik.compagnon.generated.resources.retry
import app.n_zik.compagnon.generated.resources.songs
import app.n_zik.compagnon.typography
import app.n_zik.compagnon.utils.semiBold

/**
 * The four library tabs, in the phone's order (`HomeScreen.kt` `navBarContent`; no Quick picks: not in the
 * contract), with the phone's tab labels and icons.
 */
enum class LibraryTab(val feature: String, val textId: StringResource, val iconId: DrawableResource) {
    Songs(SessionContract.FEATURE_LIBRARY_SONGS, Res.string.songs, Res.drawable.musical_notes),
    Artists(SessionContract.FEATURE_LIBRARY_ARTISTS, Res.string.artists, Res.drawable.people),
    Albums(SessionContract.FEATURE_LIBRARY_ALBUMS, Res.string.albums, Res.drawable.album),
    Playlists(SessionContract.FEATURE_LIBRARY_PLAYLISTS, Res.string.playlists, Res.drawable.library),
    ;

    companion object {
        /** The tabs the phone serves (contract §5 `features`), in the phone's order. */
        fun visible(features: Set<String>): List<LibraryTab> = entries.filter { it.feature in features }
    }
}

/**
 * The chips of the Songs tab (contract §10.1), in the phone's order around the PC-only ones: the phone's
 * chips read the phone through the contract's `filter`, "Cached PC" keeps the tracks of the phone's whole
 * list that sit in the Compagnon's local audio cache (no phone round trip per page), "Download PC" is a
 * placeholder (no PC download store yet) and "On device phone" is empty (the PC reads nothing from the
 * phone's storage, faithful to the phone whose "On device" tab lists its local files).
 */
enum class SongsChip(val key: String, val wireFilter: SongFilter?) {
    All("songs:all", SongFilter.All),
    Liked("songs:liked", SongFilter.Liked),
    Disliked("songs:disliked", SongFilter.Disliked),
    CachedTel("songs:cached_tel", SongFilter.Offline),
    CachedPc("songs:cached_pc", null),
    DownloadTel("songs:download_tel", SongFilter.Downloaded),
    DownloadPc("songs:download_pc", null),
    Top("songs:top", SongFilter.Top),
    OnDevice("songs:on_device", null),
}

/**
 * The chips of the Albums tab, in the phone's order (`HomeAlbum.kt` `albumsDefaultOrder`): the phone's
 * chips read the phone through the contract's `filter` (`disliked` since 1.6, inert on an older phone,
 * which filters that tab on its own database).
 */
enum class AlbumsChip(val key: String, val wireFilter: CollectionFilter) {
    All("albums:all", CollectionFilter.Library),
    Liked("albums:favorites", CollectionFilter.Bookmarked),
    Disliked("albums:disliked", CollectionFilter.Disliked),
}

/**
 * The chips of the Artists tab, in the phone's order (`HomeArtist.kt` `artistsDefaultOrder`): the
 * phone's chips read the phone through the contract's `filter` (`disliked` since 1.6, inert on an
 * older phone, which filters that tab on its own database).
 */
enum class ArtistsChip(val key: String, val wireFilter: CollectionFilter) {
    All("artists:all", CollectionFilter.Library),
    Liked("artists:favorites", CollectionFilter.Bookmarked),
    Disliked("artists:disliked", CollectionFilter.Disliked),
}

/**
 * The chips of the Playlists tab, in the phone's order (`HomeLibrary.kt` `playlistsDefaultOrder`):
 * the phone's chips read the phone through the contract's `filter` (contract §10, since 1.6). The
 * phone's own visibility preferences (show pinned / rewind / YT playlists) are dropped, as on the
 * other tabs.
 */
enum class PlaylistsChip(val key: String, val wireFilter: PlaylistsFilter) {
    All("playlists:all", PlaylistsFilter.All),
    Pinned("playlists:pinned", PlaylistsFilter.Pinned),
    Rewind("playlists:rewind", PlaylistsFilter.Rewind),
    Youtube("playlists:youtube", PlaylistsFilter.Youtube),
}

/** A playlist, album or artist opened from its tab: what its screen knows before reading its tracks. */
sealed interface CollectionHeader {
    val ref: CollectionRef
    val title: String
    val trackCount: Int

    data class OfPlaylist(val playlist: Playlist, val firstTracks: List<Track>?) : CollectionHeader {
        override val ref get() = CollectionRef(CollectionKind.Playlist, playlist.id)
        override val title get() = playlist.name
        override val trackCount get() = playlist.trackCount
    }

    data class OfAlbum(val album: Album) : CollectionHeader {
        override val ref get() = CollectionRef(CollectionKind.Album, album.id)
        override val title get() = album.title
        override val trackCount get() = album.trackCount
    }

    data class OfArtist(val artist: Artist) : CollectionHeader {
        override val ref get() = CollectionRef(CollectionKind.Artist, artist.id)
        override val title get() = artist.name
        override val trackCount get() = artist.trackCount
    }
}

/**
 * Playback actions of a track or a collection. [enabled] is `false` outside a `Live` session.
 * [onShuffle] is `null` for a track. Since 1.7.1 [currentTrackList] is the live list the track's menu
 * came from: the song menu collects it, so its heart follows a confirmed like write while the menu is
 * open (as on the phone, where the menu reads the track's own row live); `null` for a collection or a
 * track outside a list (the queue, the player's own menu).
 */
data class ItemActions(
    val onPlay: () -> Unit,
    val onPlayNext: () -> Unit,
    val onEnqueue: () -> Unit,
    val enabled: Boolean,
    val onShuffle: (() -> Unit)? = null,
    val currentTrackList: StateFlow<PagedState<Track>>? = null,
)

/**
 * The lists of the four tabs, kept in memory for the main window's lifetime (contract §12: never
 * persisted). The phone's home tabs read its own database, so they are always fresh; here the lists
 * are REST snapshots, so [reload] is called on every tab switch — the newly visible tab comes back
 * from the phone, as if "Refresh" had been tapped.
 *
 * The Songs search text lives here (it survives a change of tab); it reaches the phone as `query` after a
 * short pause of typing ([SEARCH_DEBOUNCE_MS]).
 *
 * Real-time gap (vs the phone): the phone's lists observe its own database and update while the tab is
 * open; the contract exposes no library push, so a PC list only moves on its reload, or in place when a
 * confirmed write of this window patches it (a like / bookmark performed here). The song menu's heart
 * sidesteps the gap with [ItemActions.currentTrack].
 */
class LibraryLists(
    private val library: LibraryRepository,
    private val scope: CoroutineScope,
    /** The Compagnon's own audio cache (contract §8.3): the "Cached PC" chip's list and cache bar. */
    val audioCache: AudioCache? = null,
) {
    /**
     * The phone sorts its songs in its own database (`library.sort`, contract 1.6): `reverse` is sent
     * to the phone, which re-sorts. On an older phone the PC keeps the display's reverse direction,
     * reading the phone's pages from their end ([songsReversed]).
     */
    private val sortsOnPhone get() = SessionContract.FEATURE_LIBRARY_SORT in library.features

    val songs = PagedList(SongsQuery(), scope) { query, offset, limit ->
        if (query.reverse && !sortsOnPhone) songsReversed(query, offset, limit) else library.songs(offset, limit, query)
    }

    /**
     * The "Cached PC" chip: the phone's whole list (the chip's sort, `filter=all`), kept to the tracks in
     * the Compagnon's local [AudioCache] — a client-side filter, so the phone's pages are read one after
     * the other and the kept tracks are served as a single page.
     */
    val songsPcCached = PagedList(SongsQuery(), scope) { query, offset, _ -> pcCachedSongs(query, offset) }

    /** The "Download PC" placeholder and the "On device phone" chip (the PC reads nothing): an empty list. */
    val songsEmpty = PagedList(SongsQuery(), scope) { _, offset, _ ->
        LibraryResult.Ok(Page<Track>(emptyList(), 0, offset, 0))
    }
    val playlists = PagedList(PlaylistsQuery(), scope) { query, offset, limit -> library.playlists(offset, limit, query) }
    val albums = PagedList(AlbumsQuery(), scope) { query, offset, limit -> library.albums(offset, limit, query) }
    val artists = PagedList(ArtistsQuery(), scope) { query, offset, limit -> library.artists(offset, limit, query) }

    /**
     * The open detail screens' track lists (album / artist / local playlist), registered by their
     * `rememberCollectionSongs` / `rememberPlaylistSongs`: the §10.2 writes patch them with the tab
     * lists, so an open screen updates in place as the phone's own screens do.
     */
    private val _detailTrackLists = mutableSetOf<PagedList<*, Track>>()

    fun registerTrackList(list: PagedList<*, Track>) {
        synchronized(_detailTrackLists) { _detailTrackLists.add(list) }
    }

    fun unregisterTrackList(list: PagedList<*, Track>) {
        synchronized(_detailTrackLists) { _detailTrackLists.remove(list) }
    }

    /** The registered detail lists, snapshotted (a screen may unregister mid-iteration). */
    private fun detailTrackLists(): List<PagedList<*, Track>> =
        synchronized(_detailTrackLists) { _detailTrackLists.toList() }

    /**
     * Re-reads the lists that show [trackId] after a failed §10.2 write left them holding a state
     * the phone never wrote (the optimistic update is undone by the phone's own pages).
     */
    fun reloadTrackLists(trackId: String) {
        songs.reload()
        songsPcCached.reload()
        detailTrackLists().forEach { list ->
            if (list.state.value.items.any { it.id == trackId }) list.reload()
        }
    }

    // ---- Live (contract §7.2, since 1.7.3 `library.live`) ----------------------------------------

    private val liveKinds = mutableSetOf<String>()
    private var liveJob: Job? = null

    /**
     * The phone's `libraryChanged` delta: [kind]'s family is re-read from its first page, coalesced —
     * a burst of deltas (one write can touch several tables, and the PC's own §10.2 writes come back
     * over the WS) waits until the phone is quiet, then each invalidated family reloads once. Only
     * the loaded lists move: a family never read comes fresh from the phone on its first read. The
     * PC's own writes are re-read too: the re-consult replaces the optimistic patch, no conflict.
     */
    fun onLibraryChanged(kind: String) {
        synchronized(liveKinds) { liveKinds.add(kind) }
        liveJob?.cancel()
        liveJob = scope.launch {
            delay(LIVE_RELOAD_DEBOUNCE_MS)
            val kinds = synchronized(liveKinds) { liveKinds.toList().also { liveKinds.clear() } }
            kinds.forEach { reloadLibraryFamily(it) }
        }
    }

    /** The loaded lists of the [kind]'s family, re-read from their first page. */
    private fun reloadLibraryFamily(kind: String) {
        when (kind) {
            "songs" -> {
                if (isLoaded(songs)) songs.reload()
                if (isLoaded(songsPcCached)) songsPcCached.reload()
                // A no-op on the phone's chips (their menu rides on their pages)
                loadPcChipSortMenu(activeSongsChip)
                detailTrackLists().forEach { list -> if (isLoaded(list)) list.reload() }
            }
            "albums" -> if (isLoaded(albums)) albums.reload()
            "artists" -> if (isLoaded(artists)) artists.reload()
            "playlists" -> if (isLoaded(playlists)) playlists.reload()
            else -> Unit // a family the contract does not name: no loaded list answers it
        }
    }

    /** A list is loaded while its first page has answered ([PagedState.total] is set). */
    private fun isLoaded(list: PagedList<*, *>): Boolean = list.state.value.total != null

    /**
     * Since 1.7.3 (feature `library.sortMenu`): the sort menu of a PC-only chip, read from its
     * mobile counterpart (its "Cached PC" the phone's Offline tab, its "Download PC" the phone's
     * Downloaded tab, its "On Device" the phone's OnDevice tab) with a one-track page — only the
     * menu counts. One entry per chip: a stale or out-of-order probe (the user switched chips
     * while one was in flight) refreshes only its own chip's entry. A failed read keeps the
     * last served menu (none, or the earlier one); a ≤ 1.7.2 phone serves `null`.
     */
    private val _pcChipSortMenus = MutableStateFlow<Map<SongsChip, List<String>?>>(emptyMap())
    val pcChipSortMenus: StateFlow<Map<SongsChip, List<String>?>> = _pcChipSortMenus.asStateFlow()

    fun loadPcChipSortMenu(chip: SongsChip) {
        val filter = when (chip) {
            SongsChip.CachedPc -> SongFilter.Offline
            SongsChip.DownloadPc -> SongFilter.Downloaded
            SongsChip.OnDevice -> SongFilter.Local
            else -> return
        }
        scope.launch {
            when (val page = library.songs(0, 1, SongsQuery(null, filter))) {
                is LibraryResult.Ok -> _pcChipSortMenus.value = _pcChipSortMenus.value + (chip to page.page.sortMenu)
                else -> Unit
            }
        }
    }

    /**
     * The phone's disk caches (contract §10, since 1.7.1), read on a tab switch: the Songs tab's cache
     * bar. `null` hides the bar (unreachable or a phone without the `library.cache` feature).
     */
    suspend fun cacheSpace(): LibraryCache? = library.cacheSpace()

    /**
     * The phone's Month / Year / All row (contract §10, since 1.7.2), read on the Rewind chip:
     * `null` hides the row (feature absent or a failed read).
     */
    suspend fun rewindState(): RewindState? = library.rewindState()

    /**
     * The phone's "disliked" mode per collection (contract §10, since 1.7.2), read once per session
     * (its `GET /library/dislikeMode`): the Disliked chips' visibility and the menus' like rotation /
     * binary toggle (the phone's `DislikeMode.Enabled`). `null` keeps the pre-1.7.2 display (the
     * phone's own default: the mode enabled, the chips shown).
     */
    private val _dislikeMode = MutableStateFlow<DislikeMode?>(null)
    val dislikeMode: StateFlow<DislikeMode?> = _dislikeMode.asStateFlow()

    init {
        if (SessionContract.FEATURE_LIBRARY_DISLIKE_MODE in library.features) {
            scope.launch { _dislikeMode.value = library.dislikeMode() }
        }
    }

    /** The chip shown on the Songs tab (the screen keeps it in sync); its list is re-read on a tab switch. */
    var activeSongsChip: SongsChip = SongsChip.All
        private set

    fun setActiveSongsChip(chip: SongsChip) {
        activeSongsChip = chip
    }

    /** The list behind the active chip of the Songs tab. */
    fun activeSongsList(): PagedList<SongsQuery, Track> = when (activeSongsChip) {
        SongsChip.CachedPc -> songsPcCached
        SongsChip.DownloadPc, SongsChip.OnDevice -> songsEmpty
        else -> songs
    }

    /** The list behind the active chip of the Songs tab, re-read from its first page. */
    fun reloadActiveSongs() {
        activeSongsList().reload()
    }

    /** The chip shown on the Albums tab (the screen keeps it in sync); its list is re-read on a tab switch. */
    var activeAlbumsChip: AlbumsChip = AlbumsChip.All
        private set

    fun setActiveAlbumsChip(chip: AlbumsChip) {
        activeAlbumsChip = chip
    }

    /** The list behind the active chip of the Albums tab (one list: the chip is the query's `filter`). */
    fun activeAlbumsList(): PagedList<AlbumsQuery, Album> = albums

    /** The list behind the active chip of the Albums tab, re-read from its first page. */
    fun reloadActiveAlbums() {
        albums.reload()
    }

    /** The chip shown on the Artists tab (the screen keeps it in sync); its list is re-read on a tab switch. */
    var activeArtistsChip: ArtistsChip = ArtistsChip.All
        private set

    fun setActiveArtistsChip(chip: ArtistsChip) {
        activeArtistsChip = chip
    }

    /** The list behind the active chip of the Artists tab (one list: the chip is the query's `filter`). */
    fun activeArtistsList(): PagedList<ArtistsQuery, Artist> = artists

    /** The list behind the active chip of the Artists tab, re-read from its first page. */
    fun reloadActiveArtists() {
        artists.reload()
    }

    /** The chip shown on the Playlists tab (the screen keeps it in sync); its list is re-read on a tab switch. */
    var activePlaylistsChip: PlaylistsChip = PlaylistsChip.All
        private set

    fun setActivePlaylistsChip(chip: PlaylistsChip) {
        activePlaylistsChip = chip
    }

    /** The list behind the active chip of the Playlists tab (one list: the chip is the query's `filter`). */
    fun activePlaylistsList(): PagedList<PlaylistsQuery, Playlist> = playlists

    /** The list behind the active chip of the Playlists tab, re-read from its first page. */
    fun reloadActivePlaylists() {
        playlists.reload()
    }

    /** The phone's `features` (contract §5): the tabs gate their sort on `library.sort` (1.6). */
    val features: Set<String> get() = library.features

    /**
     * The §10.2 writes (since 1.7): the confirmed `200` (the phone's resulting state) applied to the
     * lists already loaded, as the only local mutation — a later reload re-syncs with the phone. The
     * rows follow the phone's per-chip list semantics (its Room queries): the Liked / Bookmarked,
     * Disliked and Pinned chips are membership lists — a row stays only while its state matches the
     * chip, so those writes drop it (and shrink `total`). The phone's home tabs also hide their
     * disliked rows in every chip but the Disliked one (its `HomeSongs.kt` 342-344: the `likedAt`
     * filter is on every tab except Disliked), so a dislike drops the row from the chips except
     * Disliked, where the row stays and only flips its flag (in red). A row that *enters* a chip (a
     * like while on the Liked chip) is not inserted: the phone's page order is unknown to the PC, so
     * it appears on the next reload.
     */
    fun patchSongLike(trackId: String, state: TrackLike) {
        val filter = songs.query.value.filter
        songs.patchItems { track ->
            if (track.id != trackId) track
            else when {
                // The phone's per-chip membership: the row leaves a chip its state no longer matches
                filter == SongFilter.Liked && state != TrackLike.Liked -> null
                filter == SongFilter.Disliked && state != TrackLike.Disliked -> null
                // The phone's home tabs hide their disliked rows in every chip but the Disliked one
                // (its `HomeSongs.kt` 342-344): disliking a row leaves its chip
                state == TrackLike.Disliked && filter != SongFilter.Disliked -> null
                else -> track.copy(like = state, isLiked = state == TrackLike.Liked)
            }
        }
        // The "Cached PC" chip hides its disliked rows like the phone's cached tab; the open detail
        // screens' lists keep every row (the phone's detail screens show their disliked rows)
        songsPcCached.patchItems { track ->
            when {
                track.id != trackId -> track
                state == TrackLike.Disliked -> null
                else -> track.copy(like = state, isLiked = state == TrackLike.Liked)
            }
        }
        detailTrackLists().forEach { list ->
            list.patchItems { track ->
                if (track.id != trackId) track else track.copy(like = state, isLiked = state == TrackLike.Liked)
            }
        }
    }

    fun patchAlbumBookmark(albumId: String, bookmarked: Boolean) {
        val filter = albums.query.value.filter
        albums.patchItems { album ->
            if (album.id != albumId) album
            else when {
                // The phone's per-chip membership: unbooking leaves the Bookmarked chip; the phone's
                // `bookmarkState` clears the album's dislike, so a bookmark leaves the Disliked chip
                filter == CollectionFilter.Bookmarked && !bookmarked -> null
                filter == CollectionFilter.Disliked && bookmarked -> null
                else -> album.copy(isBookmarked = bookmarked)
            }
        }
    }

    fun patchArtistFollow(artistId: String, state: ArtistFollow) {
        val filter = artists.query.value.filter
        artists.patchItems { artist ->
            if (artist.id != artistId) artist
            else when {
                // The phone's per-chip membership: the row leaves a chip its state no longer matches
                // (the Library list keeps its disliked artists, shown in red)
                filter == CollectionFilter.Bookmarked && state != ArtistFollow.Followed -> null
                filter == CollectionFilter.Disliked && state != ArtistFollow.Disliked -> null
                else -> artist.copy(
                    isBookmarked = state == ArtistFollow.Followed,
                    isDisliked = state == ArtistFollow.Disliked,
                )
            }
        }
    }

    fun patchPlaylistPin(playlistId: String, pinned: Boolean) {
        val filter = playlists.query.value.filter
        playlists.patchItems { playlist ->
            if (playlist.id != playlistId) playlist
            else when {
                // The phone's per-chip membership: unpinning leaves the Pinned chip
                filter == PlaylistsFilter.Pinned && !pinned -> null
                else -> playlist.copy(isPinned = pinned)
            }
        }
    }

    fun patchAlbumLike(albumId: String, state: AlbumLike) {
        val filter = albums.query.value.filter
        albums.patchItems { album ->
            if (album.id != albumId) album
            else when {
                // The phone's per-chip membership: the row leaves a chip its state no longer matches
                filter == CollectionFilter.Bookmarked && state != AlbumLike.Bookmarked -> null
                filter == CollectionFilter.Disliked && state != AlbumLike.Disliked -> null
                else -> album.copy(isBookmarked = state == AlbumLike.Bookmarked, isDisliked = state == AlbumLike.Disliked)
            }
        }
    }

    fun patchPlaylistBookmark(playlistId: String, bookmarked: Boolean) {
        val filter = playlists.query.value.filter
        playlists.patchItems { playlist ->
            if (playlist.id != playlistId) playlist
            else when {
                // The phone's per-chip membership: unbookmarking leaves the YouTube chip
                filter == PlaylistsFilter.Youtube && !bookmarked -> null
                else -> playlist.copy(isBookmarked = bookmarked)
            }
        }
    }

    /** The phone's total of the current reverse read: re-probed for every list generation (offset 0). */
    private var reverseTotal: Int? = null

    /**
     * The display's reverse direction, on top of the phone's fixed sort order (contract §10.1): the phone's
     * pages are read from its end and shown reversed. For the displayed items [offset]–[offset]+[limit],
     * the phone's page is `total - offset - limit` → `total - offset` (clamped at the list's start), read
     * at most [limit] tracks so the phone's page bounds are never crossed mid-page. One read at a time,
     * as `PagedList` guarantees.
     */
    private suspend fun songsReversed(query: SongsQuery, offset: Int, limit: Int): LibraryResult<Track> {
        val phoneQuery = SongsQuery(query.text, query.filter, query.sort)
        if (offset == 0) reverseTotal = null
        val total = reverseTotal ?: run {
            val probe = library.songs(0, 1, phoneQuery)
            if (probe !is LibraryResult.Ok) return probe
            probe.page.total.also { reverseTotal = it }
        }
        val start = (total - offset - limit).coerceAtLeast(0)
        val count = (total - offset) - start
        val result = library.songs(start, count, phoneQuery)
        return when (result) {
            is LibraryResult.Ok ->
                result.copy(page = result.page.copy(items = result.page.items.reversed(), total = total, offset = offset, limit = limit))
            else -> result
        }
    }

    /**
     * The read of [songsPcCached]: the phone's whole list (the chip's own sort, the current search)
     * kept to the tracks of the Compagnon's local [audioCache]. The filter is client-side, so the
     * phone's pages are read one after the other and the kept tracks are served as a single page; a
     * later [offset] answers empty (the whole list is already served). On an older phone, the display's
     * reverse direction is applied client-side, as on [songsReversed].
     */
    private suspend fun pcCachedSongs(query: SongsQuery, offset: Int): LibraryResult<Track> {
        if (offset > 0) return LibraryResult.Ok(Page(emptyList(), 0, offset, 0))
        val cache = audioCache ?: return LibraryResult.Ok(Page(emptyList(), 0, 0, 0))
        val all = mutableListOf<Track>()
        var pageOffset = 0
        while (true) {
            val page = library.songs(pageOffset, LibraryContract.PAGE_SIZE, query)
            if (page !is LibraryResult.Ok) return page
            all += page.page.items
            if (page.page.items.isEmpty() || pageOffset + page.page.items.size >= page.page.total) break
            pageOffset += page.page.items.size
        }
        val kept = all.filter { cache.contains(it.id) }
            .let { if (query.reverse && !sortsOnPhone) it.reversed() else it }
        return LibraryResult.Ok(Page(kept, kept.size, 0, kept.size))
    }

    private val _songsSearch = MutableStateFlow("")
    val songsSearch: StateFlow<String> = _songsSearch.asStateFlow()
    private var searchJob: Job? = null

    fun onSongsSearch(text: String) {
        _songsSearch.value = text
        searchJob?.cancel()
        searchJob = scope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            activeSongsList().setQuery(activeSongsList().query.value.copy(text = SongsQuery.normalizeText(text)))
        }
    }

    /**
     * The Playlists tab's search text (contract 1.7.2, the phone's `text`), surviving a change of tab;
     * it reaches the phone after a short pause of typing ([SEARCH_DEBOUNCE_MS]).
     */
    private val _playlistsSearch = MutableStateFlow("")
    val playlistsSearch: StateFlow<String> = _playlistsSearch.asStateFlow()
    private var playlistsSearchJob: Job? = null

    fun onPlaylistsSearch(text: String) {
        _playlistsSearch.value = text
        playlistsSearchJob?.cancel()
        playlistsSearchJob = scope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            playlists.setQuery(playlists.query.value.copy(text = SongsQuery.normalizeText(text)))
        }
    }

    /** First tracks of each playlist (`/songs?limit=4`, for the grid mosaic), memory only. */
    private val _playlistFirstTracks = MutableStateFlow<Map<String, List<Track>>>(emptyMap())
    val playlistFirstTracks: StateFlow<Map<String, List<Track>>> = _playlistFirstTracks.asStateFlow()
    private val requestedFirstTracks = HashSet<String>()

    fun loadPlaylistFirstTracks(playlistId: String) {
        synchronized(requestedFirstTracks) { if (!requestedFirstTracks.add(playlistId)) return }
        scope.launch {
            val result = library.collectionSongs(CollectionRef(CollectionKind.Playlist, playlistId), 0, MOSAIC_TRACKS)
            if (result is LibraryResult.Ok) {
                _playlistFirstTracks.value = _playlistFirstTracks.value + (playlistId to result.page.items)
            } else {
                // A failure is never cached: the cell asks again next time it is shown
                synchronized(requestedFirstTracks) { requestedFirstTracks.remove(playlistId) }
            }
        }
    }

    /** The list of [tab] re-read from its first page: the tab just became visible. */
    fun reload(tab: LibraryTab) {
        when (tab) {
            LibraryTab.Songs -> reloadActiveSongs()
            LibraryTab.Artists -> reloadActiveArtists()
            LibraryTab.Albums -> reloadActiveAlbums()
            LibraryTab.Playlists -> reloadActivePlaylists()
        }
    }

    /** "Refresh" on the Playlists tab also forgets the mosaics. */
    fun reloadPlaylists() {
        synchronized(requestedFirstTracks) { requestedFirstTracks.clear() }
        _playlistFirstTracks.value = emptyMap()
        playlists.reload()
    }

    companion object {
        const val SEARCH_DEBOUNCE_MS = 300L
        const val MOSAIC_TRACKS = 4

        /** The live reload's coalescing window (contract §7.2, since 1.7.3 `library.live`). */
        const val LIVE_RELOAD_DEBOUNCE_MS = 300L
    }
}

/**
 * The library's playback actions, all through the phone (`queue/play`, `queue/add`); nothing changes
 * locally, the queue and the bar follow the WS. [message] shows a short text (snackbar, the phone's toast).
 */
class LibraryActions(
    val player: PlayerRepository,
    val library: LibraryRepository,
    private val scope: CoroutineScope,
    /** The phone's information messages (`Toaster.i`, e.g. "No song to shuffle"); [message] when `null`. */
    private val info: ((String) -> Unit)? = null,
    /** The loaded lists, patched by the confirmed §10.2 writes (the optimistic update, since 1.7). */
    val lists: LibraryLists? = null,
    /** The phone's error messages (`Toaster.e`: no song found, failures). */
    private val message: (String) -> Unit,
) {
    /** Without the `queue` feature, playback actions are hidden. */
    val available: Boolean get() = SessionContract.FEATURE_QUEUE in player.features

    /** The §10.2 writes (since 1.7) need the phone's `library.write` feature: without it the actions stay inert. */
    val canWrite: Boolean get() = SessionContract.FEATURE_LIBRARY_WRITE in library.features

    // ---- §10.2 writes (since 1.7): the explicit state, local Room only ----
    //
    // Optimistic (the phone's own write is local and instant): the loaded lists are patched at the
    // click, so the menus and rows react at once as on the phone; the confirmed `200` re-patches with
    // the phone's resulting state (a no-op when it agrees) and a failure re-reads the lists the
    // optimistic update touched (they hold a state the phone never wrote).

    /** `POST /library/songs/{id}/like`; the optimistic state patches every loaded list of the track. */
    fun likeSong(trackId: String, state: TrackLike) {
        lists?.patchSongLike(trackId, state)
        scope.launch {
            write(library.songLike(trackId, state), { if (it is WriteResult.SongLike) lists?.patchSongLike(trackId, it.state) }) {
                lists?.reloadTrackLists(trackId)
            }
        }
    }

    /** `POST /library/albums/{id}/bookmark`; the optimistic state patches the Albums list. */
    fun bookmarkAlbum(albumId: String, bookmarked: Boolean) {
        lists?.patchAlbumBookmark(albumId, bookmarked)
        scope.launch {
            write(library.albumBookmark(albumId, bookmarked), {
                if (it is WriteResult.AlbumBookmark) lists?.patchAlbumBookmark(albumId, it.bookmarked)
            }, { lists?.albums?.reload() })
        }
    }

    /** `POST /library/artists/{id}/follow`; the optimistic state patches the Artists list. */
    fun followArtist(artistId: String, state: ArtistFollow) {
        lists?.patchArtistFollow(artistId, state)
        scope.launch {
            write(library.artistFollow(artistId, state), {
                if (it is WriteResult.ArtistFollow) lists?.patchArtistFollow(artistId, it.state)
            }, { lists?.artists?.reload() })
        }
    }

    /** `POST /library/playlists/{id}/pin`; the optimistic state patches the Playlists list. */
    fun pinPlaylist(playlistId: String, pinned: Boolean) {
        lists?.patchPlaylistPin(playlistId, pinned)
        scope.launch {
            write(library.playlistPin(playlistId, pinned), {
                if (it is WriteResult.PlaylistPin) lists?.patchPlaylistPin(playlistId, it.pinned)
            }, { lists?.playlists?.reload() })
        }
    }

    /** `POST /library/albums/{id}/like` (since 1.7.2); the optimistic state patches the Albums list. */
    fun likeAlbum(albumId: String, state: AlbumLike) {
        lists?.patchAlbumLike(albumId, state)
        scope.launch {
            write(library.albumLike(albumId, state), {
                if (it is WriteResult.AlbumLike) lists?.patchAlbumLike(albumId, it.state)
            }, { lists?.albums?.reload() })
        }
    }

    /** `POST /library/playlists/{id}/bookmark` (since 1.7.2); the optimistic state patches the Playlists list. */
    fun bookmarkPlaylist(playlistId: String, bookmarked: Boolean) {
        lists?.patchPlaylistBookmark(playlistId, bookmarked)
        scope.launch {
            write(library.playlistBookmark(playlistId, bookmarked), {
                if (it is WriteResult.PlaylistBookmark) lists?.patchPlaylistBookmark(playlistId, it.bookmarked)
            }, { lists?.playlists?.reload() })
        }
    }

    /**
     * The common §10.2 write: on a confirmed `200` [apply] runs with the resulting state; a failure
     * answers with the contract `code`'s message and [onFail] re-reads the touched lists. A confirmed
     * revocation erases the pairing on its own: nothing to say, nothing to re-read.
     */
    private suspend fun write(
        result: WriteResult,
        apply: suspend (WriteResult) -> Unit,
        onFail: () -> Unit = {},
    ) {
        when (result) {
            is WriteResult.NotFound -> {
                message(getString(Res.string.library_not_found))
                onFail()
            }
            is WriteResult.OtherActive -> {
                message(formatMessage(Res.string.paired_other_active, result.deviceName ?: getString(Res.string.paired_other_active_unknown)))
                onFail()
            }
            is WriteResult.Failed -> {
                message(formatMessage(Res.string.paired_error, result.status, result.code ?: getString(Res.string.error_unknown_code)))
                onFail()
            }
            WriteResult.Revoked -> Unit
            WriteResult.Unreachable -> {
                message(getString(Res.string.library_error_unreachable))
                onFail()
            }
            else -> apply(result)
        }
    }

    /**
     * A click on a track, as on the phone: the loaded list becomes the queue, from that track. All the
     * loaded ids are handed over; `playTracks` keeps a window of 500 around the track and tells when it
     * cut. [expectedId] is the id displayed on the clicked row: a list that changed meanwhile never plays
     * another track.
     */
    fun playFrom(tracks: List<Track>, index: Int, expectedId: String = tracks.getOrNull(index)?.id.orEmpty()) {
        val at = if (tracks.getOrNull(index)?.id == expectedId) index else tracks.indexOfFirst { it.id == expectedId }
        if (at < 0) return
        scope.launch { player.playTracks(tracks.map { it.id }, at) }
    }

    fun add(track: Track, position: QueuePosition) {
        scope.launch { player.addTracks(listOf(track.id), position) }
    }

    /** The toolbar's "Play next" / "Enqueue" on a whole list: its loaded tracks (500 at most, with a notice). */
    fun addAll(tracks: List<Track>, position: QueuePosition, total: Int = tracks.size) {
        if (tracks.isEmpty()) return
        scope.launch { player.addTracks(tracks.map { it.id }, position, maxOf(total, tracks.size)) }
    }

    /**
     * The toolbar's "Shuffle" on a whole list (`SongShuffler`): its loaded tracks shuffled on the PC, from
     * the first one. An empty list gives the phone's "No song to shuffle".
     */
    fun playShuffled(tracks: List<Track>, total: Int = tracks.size) {
        scope.launch {
            if (tracks.isEmpty()) {
                (info ?: message)(getString(Res.string.no_song_to_shuffle))
                return@launch
            }
            val selection = PlayWindow.shuffled(tracks.map { it.id })
            player.playTracks(selection.trackIds, selection.startIndex, maxOf(total, tracks.size))
        }
    }

    /**
     * The menu of the track at [index] of [tracks], shown with the id [expectedId]; `null` (no menu) without
     * the `queue` feature. A list that changed meanwhile (reload) never adds another track: the entries act
     * only while the item at [index] still has [expectedId].
     */
    fun trackActions(tracks: StateFlow<PagedState<Track>>, index: Int, expectedId: String, live: Boolean): ItemActions? {
        if (!available) return null
        fun shown(): Track? = tracks.value.items.getOrNull(index)?.takeIf { it.id == expectedId }
        return ItemActions(
            onPlay = { if (shown() != null) playFrom(tracks.value.items, index, expectedId) },
            onPlayNext = { shown()?.let { add(it, QueuePosition.Next) } },
            onEnqueue = { shown()?.let { add(it, QueuePosition.End) } },
            enabled = live,
            // Since 1.7.1: the menu's heart follows the track's live state — the menu collects this
            // list (a confirmed like write patches it, recomposing the menu, as on the phone); the menu
            // also keeps the state of the tap itself, so a row the write drops (a chip its new state no
            // longer matches) still shows the written state on its heart
            currentTrackList = tracks,
        )
    }

    /** The actions of a playlist, album or artist; `null` (no actions) without the `queue` feature. */
    fun collectionActions(ref: CollectionRef, live: Boolean): ItemActions? = if (!available) null else ItemActions(
        enabled = live,
        onPlay = { withTracks(ref) { ids, total -> player.playTracks(ids, 0, total) } },
        onPlayNext = { withTracks(ref) { ids, total -> player.addTracks(ids, QueuePosition.Next, total) } },
        onEnqueue = { withTracks(ref) { ids, total -> player.addTracks(ids, QueuePosition.End, total) } },
        onShuffle = {
            withTracks(ref) { ids, total ->
                val selection = PlayWindow.shuffled(ids)
                player.playTracks(selection.trackIds, selection.startIndex, total)
            }
        },
    )

    /**
     * Reads the collection's tracks (one more than 500, so a longer one is truncated with a notice) and
     * hands their ids with the collection's real size, for the notice. An empty collection only shows a
     * message.
     */
    private fun withTracks(ref: CollectionRef, block: suspend (ids: List<String>, total: Int) -> Unit) {
        scope.launch {
            when (val result = library.collectionTracks(ref)) {
                is LibraryResult.Ok ->
                    if (result.page.items.isNotEmpty()) {
                        block(result.page.items.map { it.id }, result.page.total)
                    } else {
                        message(getString(Res.string.no_song_found))
                    }
                LibraryResult.NotFound -> message(getString(Res.string.library_not_found))
                LibraryResult.Revoked -> Unit
                is LibraryResult.OtherActive -> fail(LibraryError.OtherActive(result.deviceName))
                is LibraryResult.Failed -> fail(LibraryError.Failed(result.status, result.code))
                LibraryResult.Unreachable -> fail(LibraryError.Unreachable)
            }
        }
    }

    private suspend fun fail(error: LibraryError) = message(formatMessage(Res.string.library_tracks_failed, libraryErrorMessage(error)))
}

/** Readable text of a library read failure, decided from the contract `code`. */
suspend fun libraryErrorMessage(error: LibraryError): String = when (error) {
    LibraryError.Unreachable -> getString(Res.string.library_error_unreachable)
    is LibraryError.OtherActive ->
        formatMessage(Res.string.paired_other_active, error.deviceName ?: getString(Res.string.paired_other_active_unknown))
    is LibraryError.Failed -> formatMessage(Res.string.paired_error, error.status, error.code ?: getString(Res.string.error_unknown_code))
}

@Composable
private fun libraryErrorText(error: LibraryError): String = when (error) {
    LibraryError.Unreachable -> stringResource(Res.string.library_error_unreachable)
    is LibraryError.OtherActive ->
        formatText(stringResource(Res.string.paired_other_active), error.deviceName ?: stringResource(Res.string.paired_other_active_unknown))
    is LibraryError.Failed -> formatText(stringResource(Res.string.paired_error), error.status, error.code ?: stringResource(Res.string.error_unknown_code))
}

/**
 * Pagination by scrolling (no phone equivalent: the phone reads its whole database): the first page is
 * read when the list has none, the next one when the last visible item comes within [prefetch] items of
 * the end (checked again whenever the list grows or stops loading).
 */
@Composable
fun <T> LoadMoreEffect(list: PagedList<*, T>, state: PagedState<T>, lastVisibleIndex: () -> Int, prefetch: Int = 10) {
    LaunchedEffect(list, state.total == null && !state.loading && state.error == null) {
        if (state.total == null && !state.loading && state.error == null) list.loadMore()
    }
    // The item count and the loading state are observed too: a page that arrives while the user is already
    // parked near the end (the earlier loadMore was refused while loading) loads the next one
    LaunchedEffect(list) {
        combine(snapshotFlow { lastVisibleIndex() }, list.state) { last, current ->
            Triple(last, current.items.size, current.loading)
        }
            .distinctUntilChanged()
            .collect { (last, size, loading) -> if (!loading && last >= size - prefetch) list.loadMore() }
    }
}

/**
 * What a list shows besides its items (no phone equivalent): a spinner while a page loads, or the error
 * with "Retry" (`5xx`, unreachable, another PC active; the pages already loaded stay above).
 */
@Composable
fun PagedStatus(state: PagedState<*>, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val error = state.error
    when {
        error != null -> Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = modifier.fillMaxWidth().padding(16.dp),
        ) {
            BasicText(
                text = libraryErrorText(error),
                style = typography().xs.semiBold.copy(color = colorPalette().textSecondary, textAlign = TextAlign.Center),
            )
            TextButton(onClick = onRetry) {
                Text(stringResource(Res.string.retry), style = typography().xs.semiBold, color = colorPalette().accent)
            }
        }
        state.loading -> Box(modifier = modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = colorPalette().accent)
        }
    }
}

/**
 * The phone's empty list text (`HomeSongs.kt` 676, `HomeAlbum.kt` 775…): "No items", `m` semi-bold
 * `textSecondary`, 47 dp above the bottom. Shown only once the list is known to be empty.
 */
@Composable
fun NoItems(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize().padding(bottom = 47.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = text,
            style = typography().m.semiBold.copy(
                color = colorPalette().textSecondary,
            ),
        )
    }
}

/**
 * The tracks of an opened playlist, album or artist, read by pages while scrolling (memory only). A `404`
 * (the collection disappeared) shows "Not found" and goes back to the list. Albums and artists keep the
 * phone's fixed order: no query is sent.
 */
@Composable
fun rememberCollectionSongs(
    library: LibraryRepository,
    ref: CollectionRef,
    onBack: () -> Unit,
): PagedList<Unit, Track> {
    val scope = rememberCoroutineScope()
    val list = remember(ref) {
        PagedList(Unit, scope) { _, offset, limit -> library.collectionSongs(ref, offset, limit) }
    }
    // The list joins the library's patch scope: the §10.2 writes update it in place (the phone's own
    // detail screens update through its database)
    val lists = LocalLibraryActions.current?.lists
    DisposableEffect(list) {
        lists?.registerTrackList(list)
        onDispose { lists?.unregisterTrackList(list) }
    }
    val state by list.state.collectAsState()
    LaunchedEffect(state.notFound) {
        if (state.notFound) {
            // An error toast, like the phone's "not found" ones
            Toaster.e(getString(Res.string.library_not_found))
            onBack()
        }
    }
    return list
}

/**
 * The tracks of an opened local playlist, read by pages while scrolling (memory only), with the
 * phone's own sort (contract §10.1, since 1.6 `library.sort`): `sort` and `reverse` are sent to the
 * phone, which re-sorts (the default `custom` keeps the phone's position order, the 1.5 behavior).
 * A `404` (the playlist disappeared) shows "Not found" and goes back to the list.
 */
@Composable
fun rememberPlaylistSongs(
    library: LibraryRepository,
    ref: CollectionRef,
    onBack: () -> Unit,
): PagedList<PlaylistSongsQuery, Track> {
    val scope = rememberCoroutineScope()
    val list = remember(ref) {
        PagedList(PlaylistSongsQuery(), scope) { query, offset, limit -> library.collectionSongs(ref, offset, limit, query) }
    }
    // The list joins the library's patch scope, as [rememberCollectionSongs']
    val lists = LocalLibraryActions.current?.lists
    DisposableEffect(list) {
        lists?.registerTrackList(list)
        onDispose { lists?.unregisterTrackList(list) }
    }
    val state by list.state.collectAsState()
    LaunchedEffect(state.notFound) {
        if (state.notFound) {
            // An error toast, like the phone's "not found" ones
            Toaster.e(getString(Res.string.library_not_found))
            onBack()
        }
    }
    return list
}

