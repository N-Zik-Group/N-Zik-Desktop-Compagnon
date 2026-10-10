package app.n_zik.compagnon.components.ui.screens.localplaylist

import app.n_zik.compagnon.components.themed.PinPlaylist
import androidx.compose.foundation.lazy.items
import app.n_zik.compagnon.bridge.library.toListRef
import app.n_zik.compagnon.components.themed.FloatingActionsContainerWithScrollToTop
import app.n_zik.compagnon.uiRoundnessShape
import app.n_zik.compagnon.generated.resources.*
import app.n_zik.compagnon.components.themed.HeaderIconButton
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import app.n_zik.compagnon.bridge.library.LibraryRepository
import app.n_zik.compagnon.bridge.library.PlaylistOrigin
import app.n_zik.compagnon.bridge.library.PlaylistSongsQuery
import app.n_zik.compagnon.bridge.library.SongsQuery
import app.n_zik.compagnon.bridge.state.SessionContract
import app.n_zik.compagnon.bridge.state.Track
import app.n_zik.compagnon.bridge.state.formattedTotalPlayTime
import app.n_zik.compagnon.bridge.state.unmatched
import app.n_zik.compagnon.components.LocalMenuState
import app.n_zik.compagnon.components.SongItem
import app.n_zik.compagnon.components.songSortOverlay
import app.n_zik.compagnon.components.Sort
import app.n_zik.compagnon.components.SortOption
import app.n_zik.compagnon.components.playlistSongSortOptions
import app.n_zik.compagnon.components.items.playlistThumbnails
import app.n_zik.compagnon.components.menu.song.SongItemMenu
import app.n_zik.compagnon.components.navigation.header.TabToolBar
import app.n_zik.compagnon.components.styling.Dimensions
import app.n_zik.compagnon.components.tab.Locator
import app.n_zik.compagnon.components.tab.Search
import app.n_zik.compagnon.components.tab.SongShuffler
import app.n_zik.compagnon.components.tab.toolbar.Button
import app.n_zik.compagnon.components.tab.toolbar.InertButton
import app.n_zik.compagnon.components.tab.toolbar.NoRouteConfirmButton
import app.n_zik.compagnon.components.themed.Bookmark
import app.n_zik.compagnon.colorPalette
import app.n_zik.compagnon.core.network.ArtworkKey
import app.n_zik.compagnon.libraryWrites
import app.n_zik.compagnon.utils.Toaster
import app.n_zik.compagnon.utils.semiBold
import app.n_zik.compagnon.typography
import app.n_zik.compagnon.components.themed.Enqueue
import app.n_zik.compagnon.components.themed.HeaderWithIcon
import app.n_zik.compagnon.components.themed.IconInfo
import app.n_zik.compagnon.components.themed.PlayNext
import app.n_zik.compagnon.components.themed.Playlist
import app.n_zik.compagnon.components.ui.screens.home.CollectionHeader
import app.n_zik.compagnon.components.ui.screens.home.LibraryActions
import app.n_zik.compagnon.components.ui.screens.home.LoadMoreEffect
import app.n_zik.compagnon.components.ui.screens.home.LibraryLists
import app.n_zik.compagnon.components.ui.screens.home.PagedStatus
import app.n_zik.compagnon.components.ui.screens.home.rememberPlaylistSongs
import app.n_zik.compagnon.thumbnailShape
import app.n_zik.compagnon.utils.ChipSort
import app.n_zik.compagnon.utils.LocalPreferences
import app.n_zik.compagnon.utils.formatAsTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.StringResource

/** The playlist card's cover is `Dimensions.thumbnails.playlist` = 128 dp: `size` ≈ 2× in px. */
private const val PLAYLIST_CARD_SIZE_PX = 256

/**
 * Port of `LocalPlaylistSongs` (phone's `app/it/fast4x/rimusic/ui/screens/localplaylist/LocalPlaylistSongs.kt`
 * 1164-1560).
 *
 * Kept: the `HeaderWithIcon` title (no icon); the `background1` card in thumbnail shape (16 dp sides) with
 * the `Playlist` cover of 128 dp (14 dp above, mosaic of the first four tracks or the artwork of
 * `artworkTrackId`, count pill), the info column (track count with the note icon, duration with the clock
 * icon, 10 / 5 / 30 dp spacers, 80 % or 90 % in landscape) and the column of buttons: the smart-shuffle
 * button in its 48 dp box (recommendations off: `textDisabled`, no contract route so no action), 10 dp,
 * Shuffle (`LocalPlaylistSongs.kt` 1290-1320); the `TabToolBar` in the phone's default order
 * (`LocalPlaylistToolbarSettingsDialog.allButtonIds`); the row with the phone's sort arrow at its start
 * (contract §10.1, since 1.6 `library.sort`: `sort` and `reverse` are sent to the phone, which re-sorts;
 * a phone before 1.6 has no route for the sort, so the arrow is hidden) and the locator at its end;
 * the `SongItem` list, its `Dimensions.bottomSpacer` footer and the scroll-to-top button
 * (`FloatingActionsContainerWithScrollToTop`, 1575-1583).
 * Sort: the phone's per-playlist sort (`rememberPreference("PlaylistSongsSortBy_<id>")`, default `Title`),
 * persisted in the user settings (key `playlistsongs:<id>`), applied on the first composition (a cold
 * open shows the arrow's sort, not the phone's position order). As on the phone (its `PlaylistSongsSort`), the label opens the sort menu on a click (the PC keeps the arrow’s right-click too);
 * the arrow carries the current sort's name, and the generated `rewind-*` playlists (since 1.7.1 the
 * contract's `origin`, the phone's name-based `RewindPlaylists.isRewind`) default to their top order —
 * the phone's rewind-only "Rewind Top" option, sent as the phone's position order (a live sort never
 * rewrites it).
 * Toolbar: the phone's twenty-one buttons in the phone's order
 * (`LocalPlaylistToolbarSettingsDialog.allButtonIds`), with the phone's show conditions — pin off on the
 * rewind and legacy `monthly:` playlists (since 1.10.0 the `monthly` origin), position lock and renumber only while the sort is `Custom` and off on the
 * rewind playlists, "match" only while some track is not matched to the phone's library (since 1.7.1
 * the phone's full check: an id that is not a YouTube one, or the zero-duration sentinel, the phone's
 * `local:` on-device files excluded), rename off on the rewind playlists,
 * and sync / listen on YouTube only with a YouTube browse id (the phone's 1372-1373: a non-blank
 * `browseId`, the wire's since 1.7.2). Play next and enqueue are wired to the contract, and the pin
 * (`PinPlaylist`, `library.write`); "listen on YouTube" pauses the phone and opens the playlist's
 * `youtube.com` page in the desktop browser (the phone's 917-923, `ExternalUris.youtubePlaylist`); download
 * all / remove downloads open the phone's confirmations (NOT LINKED: Confirm only closes them); the rest
 * (sync included: YouTube Music sync is phone-only) are placeholders without a contract route, a click
 * does nothing — "update" keeps the phone's `info_open_update_dialog` help. Contract adaptation: the phone's own toolbar order
 * (`localPlaylistToolbarOrderKey`) and per-playlist hidden buttons (`pl_ts_<id>`) are phone preferences the
 * contract does not serve: the default order and visibility are kept.
 * Since 1.7.1: the phone's play-count / play-time overlays of the `playCount` / `playTime` /
 * `relativePlayTime` sorts (phone's `LocalPlaylistSongs.kt` 1524-1551, the play time in the phone's `formattedTotalPlayTime` format), and the lock badge of a
 * non-editable YouTube playlist on the card.
 * Since 1.7.2: the phone's search (its toolbar button, its header search bar and its `text` — the phone
 * re-filters its tracks, `total` kept pre-`text`), its header bookmark of a bookmarkeable playlist
 * (the phone's `canBeBookmarked`, its `POST /library/playlists/{id}/bookmark`, with the phone's "special
 * playlists" refusal and toasts, inert without `library.write`), the phone's custom cover over the card's
 * mosaic (its `GET /library/playlists/{id}/artwork`) and the header's total duration sent whole (the
 * wire's `totalDurationMs`, the full list before pagination and before `text`).
 * Adaptations: a click outside a `Live` session does nothing (no command can reach the phone); the top
 * fade has no system-bar inset (none on the desktop).
 * Dropped (contract v1 or PC): smart recommendations (counter, related songs), swipe actions
 * (drag to reorder), the phone's auto-sync on open, the floating multi-action icon (`showFloatingIcon`,
 * off by default; deferred).
 * Added by the Compagnon: the paging row, "Nothing here." for an empty playlist. The duration comes whole
 * from the phone (`totalDurationMs`). Since 1.10.0 a click, the toolbar's shuffle / play next / enqueue and
 * the locator act on the phone's whole list in its current sort and search (`queue.fullList`, `library.locate`). As on the phone, a track not matched to the phone's library carries its orange
 * 18 dp alert icon, and a click on it is blocked with the phone's `playback_blocked_match_first` toast.
 * The card's right column (smart-shuffle + shuffle) renders always, as on the phone.
 */
@Composable
fun LocalPlaylistSongs(
    header: CollectionHeader.OfPlaylist,
    library: LibraryRepository,
    actions: LibraryActions,
    live: Boolean,
    onMessage: (String) -> Unit,
    onBack: () -> Unit,
) {
    val playlist = header.playlist
    // The pin state shown by the toolbar, following its own taps (the header is a snapshot)
    var pinned by remember(playlist.id, playlist.isPinned) { mutableStateOf(playlist.isPinned) }
    // The header bookmark's state, following its own writes (the header is a snapshot, never refreshed)
    var bookmarked by remember(playlist.id, playlist.isBookmarked) { mutableStateOf(playlist.isBookmarked) }
    val lazyListState = rememberLazyListState()
    val menuState = LocalMenuState.current
    val scope = rememberCoroutineScope()
    val preferences = LocalPreferences.current
    val settings = preferences?.settings?.collectAsState()?.value
    val list = rememberPlaylistSongs(library, header.ref, onBack)
    val state by list.state.collectAsState()
    val items = state.items
    // The phone's rewind playlists keep their top order in the database: since 1.7.1 the contract's
    // [PlaylistOrigin] carries it (the phone's name-based `RewindPlaylists.isRewind`), which gates
    // their toolbar buttons and their sort default
    val isRewind = playlist.origin in REWIND_ORIGINS
    // The phone's per-playlist sort (`rememberPreference("PlaylistSongsSortBy_<id>")`, default
    // `Title`; a rewind playlist with no saved sort defaults to its top order) — persisted in the
    // user settings, key `playlistsongs:<id>`
    val sortKey = "playlistsongs:${playlist.id}"
    val chipSort = settings?.chipSorts?.get(sortKey) ?: if (isRewind) ChipSort(sort = "rewindTop") else ChipSort()

    // Since 1.7.2: the phone's search (its `cleanTitle().contains(text) || cleanArtistsText().contains(text)`,
    // its `LocalPlaylistSongs.kt` 1059-1066; the wire `text`, `total` kept pre-`text`)
    var searchText by remember { mutableStateOf("") }
    val search = Search(searchText, { text -> searchText = text }, lazyListState)

    /** The list query: the sort (absent `sort` keeps the phone's position order, "Rewind Top" asks the
     *  phone's position order too — the rewind playlists' top order, never rewritten) and the search text. */
    fun applyQuery(sort: ChipSort) {
        list.setQuery(PlaylistSongsQuery(sort.playlistSongSort, sort.reverse, SongsQuery.normalizeText(searchText)))
    }

    /** The sort changed: persisted per playlist (the phone keeps one sort per playlist), then sent. */
    fun applySort(sort: ChipSort) {
        preferences?.update { it.copy(chipSorts = it.chipSorts + (sortKey to sort)) }
        applyQuery(sort)
    }

    // The persisted sort, applied on the first composition: a cold open shows the arrow's sort, not the
    // phone's position order (a no-op while the persisted sort is the list's default)
    LaunchedEffect(list) { applySort(chipSort) }

    // Since 1.7.2: the search text, debounced before being sent to the phone
    LaunchedEffect(searchText) {
        delay(LibraryLists.SEARCH_DEBOUNCE_MS)
        applyQuery(chipSort)
    }

    LoadMoreEffect(list, state, { lazyListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 })

    /** The phone's sort menu: the 14 wire values, plus the rewind-only "Rewind Top" (the phone's
     *  `PlaylistSongsSort.getEnumConstants`); the names are the phone's local sort names. */
    val sortOptions: List<SortOption<String>> = playlistSongSortOptions.map {
        SortOption(it.labelId, it.iconId, it.value?.wire)
    } + if (isRewind) listOf(SortOption(Res.string.rewind_top_sort, Res.drawable.position, "rewindTop")) else emptyList()

    // The phone's own sort (contract §10.1, since 1.6 `library.sort`): `sort` and `reverse` are sent
    // to the phone, which re-sorts. As on the phone, the arrow carries the current sort's name
    // (`PlaylistSongsSort`'s `ToolBarButton` override). A phone before 1.6 lists its tracks in
    // position order, and the contract then had no route for the sort — the arrow is hidden
    val songSort: Sort<String>? = if (SessionContract.FEATURE_LIBRARY_SORT in library.features) {
        Sort(
            menuState,
            sortOptions,
            chipSort.sort,
            chipSort.reverse,
            onSortBy = { name -> applySort(chipSort.copy(sort = name)) },
            onSortDirection = { reverse -> applySort(chipSort.copy(reverse = reverse)) },
            currentLabel = {
                BasicText(
                    text = stringResource(sortLabel(chipSort.sort)),
                    style = typography().s.semiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
        )
    } else {
        null
    }

    val collection = actions.collectionActions(header.ref, live) { header.ref.toListRef(list.query.value) }
    val playbackEnabled = live && collection != null
    val shuffle = SongShuffler(enabled = playbackEnabled) { collection?.onShuffle?.invoke() }
    val playNext = PlayNext(enabled = playbackEnabled) { collection?.onPlayNext?.invoke() }
    val enqueue = Enqueue(enabled = playbackEnabled) { collection?.onEnqueue?.invoke() }
    // Since 1.10.0 (`library.locate`): the phone searches the playlist's whole list (its sort and search)
    val locator = Locator(
        lazyListState,
        { list.state.value.items },
        indexOffset = 1,
        listRef = { header.ref.toListRef(list.query.value) },
        pagedList = list,
    ) { id ->
        scope.launch { onMessage(getString(id)) }
    }
    // The phone's "match" button appears while some track is not matched to the phone's library
    // (contract 1.7.1: the phone's full check — `Track.unmatched`)
    val hasUnmatchedSongs = items.any { it.unmatched() }

    // The phone's download / remove buttons open their confirmations (as the album's); NOT LINKED: no route
    val downloadAll = NoRouteConfirmButton(
        Res.drawable.downloaded, Res.string.download, Res.string.info_download_all_songs, Res.string.do_you_really_want_to_download_all,
    )
    val deleteDownloads = NoRouteConfirmButton(
        Res.drawable.download, Res.string.info_remove_all_downloaded_songs, Res.string.info_remove_all_downloaded_songs,
        Res.string.do_you_really_want_to_delete_download,
    )
    downloadAll.Render()
    deleteDownloads.Render()
    // The phone's `ListenOnYouTube` (its 917-923): the phone paused, the playlist's page in the browser
    val player = app.n_zik.compagnon.LocalPlayerRepository.current
    val listenOnYouTube = object : app.n_zik.compagnon.components.tab.toolbar.MenuIcon,
        app.n_zik.compagnon.components.tab.toolbar.Descriptive {
        override val iconId = Res.drawable.play
        override val messageId = Res.string.listen_on_youtube
        override val menuIconTitle: String
            @Composable
            get() = stringResource(Res.string.listen_on_youtube)

        override fun onShortClick() {
            val browseId = playlist.browseId?.removePrefix("modified:")?.removePrefix("VL").orEmpty()
            if (live) player?.let { p -> scope.launch { p.pause() } }
            app.n_zik.compagnon.utils.openInBrowser("https://youtube.com/playlist?list=$browseId")
        }
    }

    // The phone's toolbar in the phone's order (`LocalPlaylistToolbarSettingsDialog.allButtonIds`),
    // with the phone's show conditions (pin / position lock / renumber / rename off on the rewind
    // playlists; position lock and renumber only while the sort is `Custom`; sync and listen on
    // YouTube need a YouTube browse id). Play next, enqueue and listen on YouTube are wired; the
    // rest are placeholders without a contract route (no action)
    val toolbarButtons = buildList<Button> {
        LOCAL_PLAYLIST_TOOLBAR_BUTTON_IDS.forEach { id ->
            when (id) {
                // The phone's `playlistNotMonthlyType` (its 1127-1130): no pin on a rewind or legacy monthly playlist
                "pin" -> if (!isRewind && playlist.origin != PlaylistOrigin.Monthly) {
                    // The phone's `PinPlaylist` (contract §10.2 `pin`, `library.write`); inert without it
                    add(
                        libraryWrites()?.let { w ->
                            PinPlaylist(isPinned = pinned, enabled = live) {
                                val previous = pinned
                                pinned = !previous
                                // The confirmed state wins; a failure rolls the toggle back
                                w.pinPlaylist(playlist.id, !previous) { confirmed -> pinned = confirmed ?: previous }
                            }
                        } ?: InertButton(Res.drawable.pin_filled, Res.string.info_pin_unpin_playlist),
                    )
                }
                // Since 1.7.2: the phone's search (its toolbar button, its header search bar and its `text`)
                "search" -> add(search)
                "position_lock" -> if (chipSort.sort == "custom" && !isRewind) {
                    add(InertButton(Res.drawable.locked, Res.string.info_lock_unlock_reorder_songs))
                }
                "match" -> if (hasUnmatchedSongs) {
                    add(InertButton(Res.drawable.alert, Res.string.match_album_audio_version))
                }
                "renumber" -> if (chipSort.sort == "custom" && !isRewind) {
                    add(InertButton(Res.drawable.position, Res.string.renumber_songs_positions))
                }
                "download_all" -> add(downloadAll)
                "delete_downloads" -> add(deleteDownloads)
                "item_selector" -> add(InertButton(Res.drawable.unchecked_outline, Res.string.item_select))
                "play_next" -> if (collection != null) add(playNext) else add(InertButton(Res.drawable.play_skip_forward, Res.string.play_next))
                "enqueue" -> if (collection != null) add(enqueue) else add(InertButton(Res.drawable.enqueue, Res.string.enqueue))
                "add_to_favorite" -> add(InertButton(Res.drawable.heart, Res.string.add_to_favorites))
                "add_to_playlist" -> add(InertButton(Res.drawable.add_in_playlist, Res.string.add_to_playlist))
                // The phone's 1372-1373: only with a YouTube browse id
                "sync" -> if (!playlist.browseId.isNullOrBlank()) add(InertButton(Res.drawable.sync, Res.string.sync))
                "listen_on_yt" -> if (!playlist.browseId.isNullOrBlank()) add(listenOnYouTube)
                "import_menu" -> add(InertButton(Res.drawable.import_outline, Res.string.import_playlist))
                "rename" -> if (!isRewind) add(InertButton(Res.drawable.title_edit, Res.string.rename_playlist))
                "delete" -> add(InertButton(Res.drawable.trash, Res.string.delete))
                "export" -> add(InertButton(Res.drawable.export_outline, Res.string.export_playlist))
                "thumbnail_picker" -> add(InertButton(Res.drawable.image, Res.string.edit_thumbnail))
                "reset_thumbnail" -> add(InertButton(Res.drawable.image, Res.string.reset_thumbnail))
                "update" -> add(InertButton(Res.drawable.refresh, Res.string.update, descriptionId = Res.string.info_open_update_dialog))
            }
        }
    }
    // The phone's mosaic (`PlaylistItem.kt` 179-185: the last four by play time, with a thumbnail): the
    // home's tracks when it opened the page, else the same read here (never the list's current sort)
    val loadedFirstTracks = actions.lists?.playlistFirstTracks?.collectAsState()?.value?.get(playlist.id)
    LaunchedEffect(playlist.id) {
        if (header.firstTracks == null) actions.lists?.loadPlaylistFirstTracks(playlist.id)
    }
    val thumbnails = playlistThumbnails(header.firstTracks ?: loadedFirstTracks, playlist.artworkTrackId, PLAYLIST_CARD_SIZE_PX)

    // The phone's `canBeBookmarked` (its `Playlist.kt` 28): `browseId?.startsWith("modified:") == false`,
    // so a playlist without a browse id (`null`) cannot be bookmarked
    val canBookmark = playlist.browseId?.startsWith("modified:") == false

    // The phone's header bookmark (its `LocalPlaylistSongs.kt` 343, 1326-1329, the 1.7.2 write): the
    // "special playlists" (their `browseId` minus the `VL` prefix is `LM` or `SE`) refuse it with the
    // phone's toast, otherwise the toggle, with the phone's toasts — inert without `library.write`
    val writes = libraryWrites()
    val bookmark = Bookmark(isBookmarked = bookmarked) {
        if (playlist.browseId?.removePrefix("VL") in listOf("LM", "SE")) {
            Toaster.e(Res.string.cannot_bookmark_special_playlist)
        } else {
            writes?.let {
                // The phone toasts once its write is done (its 345-371): after the confirmed `200`
                val previous = bookmarked
                bookmarked = !previous
                it.bookmarkPlaylist(playlist.id, !previous, onFailed = { bookmarked = previous }) { confirmed ->
                    bookmarked = confirmed
                    Toaster.s(if (confirmed) Res.string.added_to_favorites else Res.string.removed_from_favorites)
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .background(colorPalette().background0)
            .fillMaxHeight()
            .fillMaxWidth(),
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val isLandscape = maxWidth > maxHeight
            LazyColumn(
                state = lazyListState,
                modifier = Modifier
                    .background(colorPalette().background0)
                    .fillMaxSize(),
            ) {
                item(
                    key = "header",
                    contentType = 0,
                ) {
                    Column {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth(),
                        ) {
                            HeaderWithIcon(
                                title = playlist.name,
                                iconId = Res.drawable.playlist,
                                enabled = true,
                                showIcon = false,
                                modifier = Modifier
                                    .padding(bottom = 8.dp),
                                onClick = {},
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.Start,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .background(
                                    color = colorPalette().background1,
                                    shape = thumbnailShape(),
                                ),
                        ) {
                            Playlist(
                                name = playlist.name,
                                songCount = state.total ?: playlist.trackCount,
                                origin = playlist.origin,
                                // The local states (the header snapshot is never refreshed)
                                isPinned = pinned,
                                isBookmarked = bookmarked,
                                isEditable = playlist.isEditable,
                                thumbnails = thumbnails,
                                // Since 1.7.2: the phone's custom cover (its `thumbnail/playlist_<id>`),
                                // else the mosaic, as on the phone
                                customCover = ArtworkKey.playlist(playlist.id, PLAYLIST_CARD_SIZE_PX),
                                thumbnailSizeDp = Dimensions.thumbnails.playlist,
                                alternative = true,
                                showName = false,
                                modifier = Modifier
                                    .padding(top = 14.dp),
                            )

                            Column(
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.Start,
                                modifier = Modifier
                                    .padding(end = 10.dp)
                                    .fillMaxWidth(if (isLandscape) 0.90f else 0.80f),
                            ) {
                                Spacer(modifier = Modifier.height(10.dp))
                                IconInfo(
                                    title = (state.total ?: playlist.trackCount).toString(),
                                    icon = painterResource(Res.drawable.musical_notes),
                                )
                                Spacer(modifier = Modifier.height(5.dp))

                                // Since 1.7.2: the phone's header duration is sent whole (the wire's
                                // `totalDurationMs`, the full list before pagination and before the search
                                // text); a phone before 1.7.2 does not send it, then the duration shows once
                                // all tracks are loaded, as before
                                val totalDuration = state.totalDurationMs.takeIf { it > 0L }
                                    ?: (if (state.endReached) items.sumOf { it.durationMs ?: 0L } else null)
                                IconInfo(
                                    title = totalDuration?.let { formatAsTime(it) } ?: "…",
                                    icon = painterResource(Res.drawable.time),
                                )
                                Spacer(modifier = Modifier.height(30.dp))
                            }

                            // The phone renders the column always (the smart-shuffle box greyed without
                            // recommendations; here the shuffle button greys without a live session)
                            Column(
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Box(
                                    modifier = Modifier.size(48.dp), // Standard IconButton size
                                    contentAlignment = Alignment.Center,
                                ) {
                                    // isRecommendationEnabled = false (the default)
                                    HeaderIconButton(
                                        icon = Res.drawable.smart_shuffle,
                                        enabled = true,
                                        color = colorPalette().textDisabled,
                                        modifier = Modifier.clip(uiRoundnessShape()),
                                        onClick = {},
                                        // The phone's long-press help (its 1314-1319)
                                        onLongClick = { Toaster.i(Res.string.info_smart_recommendation) },
                                    )
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                shuffle.ToolBarButton()
                                // The phone's header bookmark (its `LocalPlaylistSongs.kt` 1326): a playlist
                                // whose browse id is not the phone's "modified:" prefix (its
                                // `canBeBookmarked`); the 1.7.2 write, with the phone's "special playlists"
                                // refusal and toasts
                                if (canBookmark) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    bookmark.ToolBarButton()
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        TabToolBar.Buttons(toolbarButtons)

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .padding(horizontal = 10.dp)
                                .fillMaxWidth(),
                        ) {
                            // The phone's sort arrow, at the start of the locator row (contract 1.6 `library.sort`)
                            songSort?.ToolBarButton()
                            Row(
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth(),
                            ) { locator.ToolBarButton() }
                        }

                        // Since 1.7.2: the phone's search bar (its `LocalPlaylistSongs.kt` 1415), at the
                        // end of the header
                        search.SearchBar()
                    }
                }

                items(
                    items = items.withIndex().distinctBy { it.value.id },
                    // The phone's keys: the track id, duplicates dropped (`distinctBy`)
                    key = { it.value.id },
                ) { (index, song) ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .zIndex(2f),
                    ) {
                        val menu = actions.trackActions(list.state, index, song.id, live, header.ref.toListRef(list.query.value))
                        SongItem(
                            song = song,
                            modifier = Modifier,
                            // The phone's listening-sort overlays (contract 1.7.1, phone's
                            // `LocalPlaylistSongs.kt` 1524-1551): the play count of the `playCount`
                            // sort, the total play time of the `playTime` / `relativePlayTime` sorts in
                            // the phone's `formattedTotalPlayTime` format (`45m` / `2h` / `3d`)
                            thumbnailOverlay = {
                                songSortOverlay(
                                    text = when (chipSort.sort) {
                                        "playCount" -> song.playCount.toString()
                                        "playTime",
                                        "relativePlayTime",
                                        -> song.formattedTotalPlayTime
                                        else -> null
                                    },
                                    rank = null,
                                )
                            },
                            onLongClick = menu?.let { { menuState.display { SongItemMenu(song, it).MenuComponent() } } },
                            // The phone's orange alert icon of a track not matched to the phone's library
                            // (LocalPlaylistSongs.kt 1512-1519)
                            trailingContent = {
                                if (song.unmatched()) {
                                    Icon(
                                        painter = painterResource(Res.drawable.alert),
                                        contentDescription = stringResource(Res.string.unmatched_song),
                                        tint = Color(0xFFFF9800),
                                        modifier = Modifier
                                            .padding(start = 8.dp)
                                            .size(18.dp),
                                    )
                                }
                            },
                            onClick = {
                                // Since 1.7.2: as on the phone (its 1554, 1566), the search hides on a pick
                                search.hideIfEmpty()
                                // As on the phone (LocalPlaylistSongs.kt 1557-1558): a track not matched to
                                // the phone's library cannot be played (its toast)
                                if (song.unmatched()) {
                                    Toaster.w(Res.string.playback_blocked_match_first)
                                } else if (live && actions.available) {
                                    actions.playFrom(list.state.value.items, index, song.id, header.ref.toListRef(list.query.value))
                                }
                            },
                        )
                    }
                }

                item(key = "status") {
                    if (state.endReached && items.isEmpty()) {
                        BasicText(
                            text = stringResource(Res.string.library_empty),
                            style = typography().xs.semiBold.copy(color = colorPalette().textSecondary, textAlign = TextAlign.Center),
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                        )
                    }
                    PagedStatus(state, onRetry = list::retry)
                }

                item(
                    key = "footer",
                    contentType = 0,
                ) {
                    Spacer(modifier = Modifier.height(Dimensions.bottomSpacer))
                }
            }
        }

        FloatingActionsContainerWithScrollToTop(lazyListState = lazyListState)
    }
}

/**
 * The contract's rewind origins (since 1.7.1): the phone maps its name-based `RewindPlaylists.isRewind`
 * into them (`LibraryMapping.kt` 145-152) — a monthly, a yearly or the all-time playlist, plus the
 * legacy `Rewind` fallback of a phone before 1.7.1.
 */
private val REWIND_ORIGINS = setOf(
    PlaylistOrigin.Rewind,
    PlaylistOrigin.RewindMonthly,
    PlaylistOrigin.RewindYearly,
    PlaylistOrigin.RewindAlltime,
)

/** The phone's `PlaylistSongSortBy.text` of the stored sort name (its menu label). */
private fun sortLabel(name: String): StringResource = when (name) {
    "title" -> Res.string.sort_title
    "artist" -> Res.string.sort_artist
    "album" -> Res.string.sort_album
    "artistAndAlbum" -> Res.string.sort_artist_and_album
    "duration" -> Res.string.sort_duration
    "playCount" -> Res.string.sort_play_count
    "playTime" -> Res.string.sort_listening_time
    "relativePlayTime" -> Res.string.relative_listening_time
    "dateAdded" -> Res.string.sort_date_added
    "datePlayed" -> Res.string.sort_date_played
    "dateLiked" -> Res.string.sort_date_liked
    "albumYear" -> Res.string.sort_album_year
    "downloaded" -> Res.string.sort_downloaded
    "custom" -> Res.string.sort_custom_order
    else -> Res.string.rewind_top_sort
}

/** The phone's default order (`LocalPlaylistToolbarSettingsDialog.allButtonIds`). */
private val LOCAL_PLAYLIST_TOOLBAR_BUTTON_IDS = listOf(
    "pin", "search", "position_lock", "match", "renumber",
    "download_all", "delete_downloads",
    "item_selector",
    "play_next", "enqueue", "add_to_favorite", "add_to_playlist",
    "sync", "listen_on_yt",
    "import_menu", "rename", "delete", "export",
    "thumbnail_picker", "reset_thumbnail", "update",
)
