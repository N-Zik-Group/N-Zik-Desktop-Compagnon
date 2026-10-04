package app.n_zik.compagnon.components.ui.screens.home

import app.n_zik.compagnon.utils.Toaster
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
import app.n_zik.compagnon.bridge.library.Artist
import app.n_zik.compagnon.bridge.library.CollectionFilter
import app.n_zik.compagnon.bridge.library.CollectionKind
import app.n_zik.compagnon.bridge.library.CollectionRef
import app.n_zik.compagnon.bridge.library.LibraryError
import app.n_zik.compagnon.bridge.library.LibraryRepository
import app.n_zik.compagnon.bridge.library.PagedList
import app.n_zik.compagnon.bridge.library.PagedState
import app.n_zik.compagnon.bridge.library.Playlist
import app.n_zik.compagnon.bridge.library.SongsQuery
import app.n_zik.compagnon.bridge.state.PlayerRepository
import app.n_zik.compagnon.bridge.state.QueuePosition
import app.n_zik.compagnon.bridge.state.SessionContract
import app.n_zik.compagnon.bridge.state.Track
import app.n_zik.compagnon.colorPalette
import app.n_zik.compagnon.core.network.LibraryResult
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
 * [onShuffle] is `null` for a track.
 */
data class ItemActions(
    val onPlay: () -> Unit,
    val onPlayNext: () -> Unit,
    val onEnqueue: () -> Unit,
    val enabled: Boolean,
    val onShuffle: (() -> Unit)? = null,
)

/**
 * The lists of the four tabs, kept in memory for the main window's lifetime (contract §12: never
 * persisted), so that switching tabs does not reload them. "Refresh" reloads one.
 *
 * The Songs search text lives here (it survives a change of tab); it reaches the phone as `query` after a
 * short pause of typing ([SEARCH_DEBOUNCE_MS]).
 */
class LibraryLists(private val library: LibraryRepository, private val scope: CoroutineScope) {
    val songs = PagedList(SongsQuery(), scope) { query, offset, limit -> library.songs(offset, limit, query) }
    val playlists = PagedList(Unit, scope) { _, offset, limit -> library.playlists(offset, limit) }
    val albums = PagedList(CollectionFilter.Library, scope) { filter, offset, limit -> library.albums(offset, limit, filter) }
    val artists = PagedList(CollectionFilter.Library, scope) { filter, offset, limit -> library.artists(offset, limit, filter) }

    private val _songsSearch = MutableStateFlow("")
    val songsSearch: StateFlow<String> = _songsSearch.asStateFlow()
    private var searchJob: Job? = null

    fun onSongsSearch(text: String) {
        _songsSearch.value = text
        searchJob?.cancel()
        searchJob = scope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            songs.setQuery(songs.query.value.copy(text = SongsQuery.normalizeText(text)))
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

    /** "Refresh" on the Playlists tab also forgets the mosaics. */
    fun reloadPlaylists() {
        synchronized(requestedFirstTracks) { requestedFirstTracks.clear() }
        _playlistFirstTracks.value = emptyMap()
        playlists.reload()
    }

    companion object {
        const val SEARCH_DEBOUNCE_MS = 300L
        const val MOSAIC_TRACKS = 4
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
    /** The phone's error messages (`Toaster.e`: no song found, failures). */
    private val message: (String) -> Unit,
) {
    /** Without the `queue` feature, playback actions are hidden. */
    val available: Boolean get() = SessionContract.FEATURE_QUEUE in player.features

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
    fun trackActions(tracks: () -> List<Track>, index: Int, expectedId: String, live: Boolean): ItemActions? {
        if (!available) return null
        fun shown(): Track? = tracks().getOrNull(index)?.takeIf { it.id == expectedId }
        return ItemActions(
            onPlay = { if (shown() != null) playFrom(tracks(), index, expectedId) },
            onPlayNext = { shown()?.let { add(it, QueuePosition.Next) } },
            onEnqueue = { shown()?.let { add(it, QueuePosition.End) } },
            enabled = live,
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

    private suspend fun fail(error: LibraryError) = message(getString(Res.string.library_tracks_failed, libraryErrorMessage(error)))
}

/** Readable text of a library read failure, decided from the contract `code`. */
suspend fun libraryErrorMessage(error: LibraryError): String = when (error) {
    LibraryError.Unreachable -> getString(Res.string.library_error_unreachable)
    is LibraryError.OtherActive ->
        getString(Res.string.paired_other_active, error.deviceName ?: getString(Res.string.paired_other_active_unknown))
    is LibraryError.Failed -> getString(Res.string.paired_error, error.status, error.code ?: getString(Res.string.error_unknown_code))
}

@Composable
private fun libraryErrorText(error: LibraryError): String = when (error) {
    LibraryError.Unreachable -> stringResource(Res.string.library_error_unreachable)
    is LibraryError.OtherActive ->
        stringResource(Res.string.paired_other_active, error.deviceName ?: stringResource(Res.string.paired_other_active_unknown))
    is LibraryError.Failed -> stringResource(Res.string.paired_error, error.status, error.code ?: stringResource(Res.string.error_unknown_code))
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
 * (the collection disappeared) shows "Not found" and goes back to the list.
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

