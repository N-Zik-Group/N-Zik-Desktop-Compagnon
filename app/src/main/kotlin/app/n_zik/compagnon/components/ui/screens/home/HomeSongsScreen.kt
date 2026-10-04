package app.n_zik.compagnon.components.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.n_zik.compagnon.bridge.library.SongFilter
import app.n_zik.compagnon.bridge.library.SongSort
import app.n_zik.compagnon.bridge.library.SongsQuery
import app.n_zik.compagnon.bridge.state.QueuePosition
import app.n_zik.compagnon.bridge.state.SessionContract
import app.n_zik.compagnon.bridge.state.TrackSource
import app.n_zik.compagnon.components.ButtonsRow
import app.n_zik.compagnon.components.LocalMenuState
import app.n_zik.compagnon.components.PeriodSelector
import app.n_zik.compagnon.components.Sort
import app.n_zik.compagnon.components.SortOption
import app.n_zik.compagnon.components.baseRotation
import app.n_zik.compagnon.components.legacySongSortOptions
import app.n_zik.compagnon.components.songSortOptions
import app.n_zik.compagnon.components.navigation.header.TabToolBar
import app.n_zik.compagnon.components.tab.Locator
import app.n_zik.compagnon.components.tab.Refresh
import app.n_zik.compagnon.components.tab.Search
import app.n_zik.compagnon.components.tab.SongShuffler
import app.n_zik.compagnon.components.tab.TabHeader
import app.n_zik.compagnon.components.tab.toolbar.Button
import app.n_zik.compagnon.components.tab.toolbar.InertButton
import app.n_zik.compagnon.colorPalette
import app.n_zik.compagnon.components.themed.Enqueue
import app.n_zik.compagnon.components.themed.FloatingActionsContainerWithScrollToTop
import app.n_zik.compagnon.components.themed.HeaderInfo
import app.n_zik.compagnon.components.themed.PlayNext
import app.n_zik.compagnon.generated.resources.Res
import app.n_zik.compagnon.generated.resources.add_in_playlist
import app.n_zik.compagnon.generated.resources.add_to_favorites
import app.n_zik.compagnon.generated.resources.add_to_playlist
import app.n_zik.compagnon.generated.resources.alert
import app.n_zik.compagnon.generated.resources.all
import app.n_zik.compagnon.generated.resources.cached
import app.n_zik.compagnon.generated.resources.cached_pc
import app.n_zik.compagnon.generated.resources.disliked
import app.n_zik.compagnon.generated.resources.download
import app.n_zik.compagnon.generated.resources.download_pc
import app.n_zik.compagnon.generated.resources.downloaded
import app.n_zik.compagnon.generated.resources.enqueue
import app.n_zik.compagnon.generated.resources.export_cached
import app.n_zik.compagnon.generated.resources.export_outline
import app.n_zik.compagnon.generated.resources.export_playlist
import app.n_zik.compagnon.generated.resources.favorites
import app.n_zik.compagnon.generated.resources.heart
import app.n_zik.compagnon.generated.resources.import_outline
import app.n_zik.compagnon.generated.resources.import_playlist
import app.n_zik.compagnon.generated.resources.info_download_all_songs
import app.n_zik.compagnon.generated.resources.info_lock_unlock_reorder_songs
import app.n_zik.compagnon.generated.resources.info_open_update_dialog
import app.n_zik.compagnon.generated.resources.info_remove_all_downloaded_songs
import app.n_zik.compagnon.generated.resources.info_shuffle
import app.n_zik.compagnon.generated.resources.info_smart_recommendation
import app.n_zik.compagnon.generated.resources.item_select
import app.n_zik.compagnon.generated.resources.locked
import app.n_zik.compagnon.generated.resources.match_album_audio_version
import app.n_zik.compagnon.generated.resources.musical_notes
import app.n_zik.compagnon.generated.resources.on_device
import app.n_zik.compagnon.generated.resources.play_next
import app.n_zik.compagnon.generated.resources.play_skip_forward
import app.n_zik.compagnon.generated.resources.playlist_top
import app.n_zik.compagnon.generated.resources.refresh
import app.n_zik.compagnon.generated.resources.shuffle
import app.n_zik.compagnon.generated.resources.smart_shuffle
import app.n_zik.compagnon.generated.resources.smart_trash
import app.n_zik.compagnon.generated.resources.songs
import app.n_zik.compagnon.generated.resources.trash
import app.n_zik.compagnon.generated.resources.unchecked_outline
import app.n_zik.compagnon.utils.ChipSort
import app.n_zik.compagnon.utils.LocalPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

/**
 * Port of `HomeSongsScreen` (phone's `app/n_zik/android/components/ui/screens/home/HomeSongsScreen.kt`,
 * header 448-571 + 573-844).
 *
 * Chips: the phone's `BuiltInPlaylist` chips in the user's order — All, Favorites, Disliked, Top
 * Playlist, Downloaded, Download PC, Cached, Cached PC, "On device phone" ([SongsChip]). The phone's
 * chips read the phone through the contract's `filter` (since 1.6); "Download PC" is a placeholder
 * (no PC download store yet) and "On device phone" is empty (the PC reads nothing from the phone's
 * storage); "Cached PC" keeps the tracks of the phone's list that sit in the Compagnon's local audio
 * cache ([LibraryLists.songsPcCached]).
 *
 * Toolbar: the phone's default buttons of the active chip in the phone's order
 * ([HomeSongsToolbarSettingsDialog] `allButtonIds` / `tabAvailableIds`), with the phone's show
 * conditions (position lock only while the chip's sort is `Custom`, match only while unmatched tracks
 * are loaded, no YouTube sync on the PC), plus the Compagnon's "Refresh". Wired: sort (or the period
 * selector on Top), search, locator, shuffle, play next and enqueue (on the loaded tracks); the rest
 * (download all / delete downloads, smart shuffle, item selector, add to favorites / to a playlist,
 * import / export, update, smart trash) are placeholders without a contract route, a click does
 * nothing; the buttons that do not fit the row go behind the "…" menu.
 *
 * Sort: the phone keeps one sort per tab — the chip's sort and direction live in the user settings
 * (`chipSorts`, key `songs:<chip>`), applied to the chip's list on every change; the Top chip replaces
 * the sort with the phone's period selector (`period`, the phone's own period when unset); the
 * "Downloaded" option is hidden on the downloaded / cached chips, as on the phone. On a phone without
 * `library.sort` (contract < 1.6) the options without a route are shown without effect, the direction
 * is applied client-side, and the Disliked / Cached / Top chips are inert (they are filtered on the
 * phone's own database, which the contract cannot reach before 1.6).
 *
 * Dropped (contract v1): position lock, match, YouTube likes sync, the chip visibility / order and
 * toolbar order / visibility preferences, the YouTube filter row, the cache space indicator, the
 * smart-recommendation counter, the floating search / settings icon.
 */
@Composable
fun HomeSongsScreen(
    lists: LibraryLists,
    actions: LibraryActions,
    live: Boolean,
    scope: CoroutineScope,
    onMessage: (String) -> Unit,
) {
    val lazyListState = rememberLazyListState()
    val menuState = LocalMenuState.current
    val preferences = LocalPreferences.current
    val settings = preferences?.settings?.collectAsState()?.value
    var chip by remember { mutableStateOf(SongsChip.All) }
    val chipSort = settings?.chipSorts?.get(chip.key) ?: ChipSort()
    val activeList = lists.activeSongsList()
    val state by activeList.state.collectAsState()
    val searchText by lists.songsSearch.collectAsState()

    val showText: (StringResource) -> Unit = { id -> scope.launch { onMessage(getString(id)) } }
    val search = Search(searchText, lists::onSongsSearch, lazyListState)
    val locator = Locator(lazyListState, { activeList.state.value.items }, onMessage = showText)
    val sortsOnPhone = SessionContract.FEATURE_LIBRARY_SORT in lists.features
    val playbackEnabled = live && actions.available

    /** The [target]'s list query: its own sort (the persisted [sort]) and the current search text. */
    fun applyChipQuery(target: SongsChip, sort: ChipSort) {
        val text = SongsQuery.normalizeText(searchText)
        when (target) {
            SongsChip.CachedPc -> lists.songsPcCached.setQuery(SongsQuery(text, SongFilter.All, sort.songSort, sort.reverse, null))
            SongsChip.DownloadPc, SongsChip.OnDevice -> Unit
            else -> target.wireFilter?.let { filter ->
                lists.songs.setQuery(
                    SongsQuery(
                        text,
                        filter,
                        sort.songSort,
                        sort.reverse,
                        if (target == SongsChip.Top) sort.topPeriod else null,
                    ),
                )
            }
        }
    }

    /** The phone's chip change; Disliked / Cached / Top are inert on a phone without `library.sort`. */
    fun selectChip(target: SongsChip) {
        if (target == chip) return
        if (!sortsOnPhone && target in INERT_CHIPS_WITHOUT_LIBRARY_SORT) return
        chip = target
        lists.setActiveSongsChip(target)
        applyChipQuery(target, settings?.chipSorts?.get(target.key) ?: ChipSort())
    }

    /** The chip's sort changed: persisted (the phone keeps one sort per tab), then applied. */
    fun applyChipSort(sort: ChipSort) {
        preferences?.update { it.copy(chipSorts = it.chipSorts + (chip.key to sort)) }
        applyChipQuery(chip, sort)
    }

    // The phone's `hasUnmatchedSongs` (HomeSongsScreen.kt 463) with the PC's `Track` in hand: a
    // non-YT id or a zero duration, and not a local song — the match button is shown when one is loaded
    val hasUnmatched = state.items.any {
        (it.id.length != 11 || it.durationMs == null || it.durationMs == 0L) && it.source != TrackSource.Local
    }

    // The chip's sort button (the phone's per-tab sort). Top replaces it with the period selector;
    // Disliked keeps the arrow like on the phone (the phone's provider ignores the sort there)
    val sortButton: Button = if (chip == SongsChip.Top) {
        PeriodSelector(menuState, chipSort.topPeriod) { period ->
            applyChipSort(chipSort.copy(period = period.wire))
        }
    } else if (sortsOnPhone) {
        Sort(
            menuState,
            chipSortOptions(chip, sortsOnPhone),
            chipSort.songSort,
            chipSort.reverse,
            onSortBy = { sort -> applyChipSort(chipSort.copy(sort = sort.wire)) },
            onSortDirection = { reverse -> applyChipSort(chipSort.copy(reverse = reverse)) },
        )
    } else {
        Sort(
            menuState,
            chipSortOptions(chip, sortsOnPhone),
            chipSort.songSort,
            chipSort.reverse,
            onSortBy = { sort -> applyChipSort(chipSort.copy(sort = sort.wire)) },
            onSortDirection = { reverse -> applyChipSort(chipSort.copy(reverse = reverse)) },
            baseRotation = chipSort.songSort.baseRotation,
        )
    }

    // The phone's toolbar of the active chip, in the phone's order, with the phone's show conditions;
    // the Compagnon's "Refresh" is added at the end. Wired: sort / period, search, locator, shuffle,
    // play next, enqueue, refresh. Placeholders (no contract route, a click does nothing): the rest.
    val buttons = buildList<Button> {
        songsToolbarButtonIds(chip).forEach { id ->
            when (id) {
                "sort" -> add(sortButton)
                "position_lock" -> if (chipSort.songSort == SongSort.Custom) {
                    add(InertButton(Res.drawable.locked, Res.string.info_lock_unlock_reorder_songs))
                }
                "match" -> if (hasUnmatched) add(InertButton(Res.drawable.alert, Res.string.match_album_audio_version))
                "search" -> add(search)
                "locator" -> add(locator)
                "download_all" -> add(InertButton(Res.drawable.downloaded, Res.string.info_download_all_songs))
                "delete_downloads" -> add(InertButton(Res.drawable.download, Res.string.info_remove_all_downloaded_songs))
                "shuffle" -> if (actions.available) {
                    add(SongShuffler(enabled = playbackEnabled) { activeList.state.value.let { items -> actions.playShuffled(items.items, items.total ?: items.items.size) } })
                } else {
                    add(InertButton(Res.drawable.shuffle, Res.string.info_shuffle))
                }
                "smart_shuffle" -> add(InertButton(Res.drawable.smart_shuffle, Res.string.info_smart_recommendation))
                "item_selector" -> add(InertButton(Res.drawable.unchecked_outline, Res.string.item_select))
                "play_next" -> if (actions.available) {
                    add(PlayNext(enabled = playbackEnabled) { activeList.state.value.let { items -> actions.addAll(items.items, QueuePosition.Next, items.total ?: items.items.size) } })
                } else {
                    add(InertButton(Res.drawable.play_skip_forward, Res.string.play_next))
                }
                "enqueue" -> if (actions.available) {
                    add(Enqueue(enabled = playbackEnabled) { activeList.state.value.let { items -> actions.addAll(items.items, QueuePosition.End, items.total ?: items.items.size) } })
                } else {
                    add(InertButton(Res.drawable.enqueue, Res.string.enqueue))
                }
                "add_to_favorite" -> add(InertButton(Res.drawable.heart, Res.string.add_to_favorites))
                "add_to_playlist" -> add(InertButton(Res.drawable.add_in_playlist, Res.string.add_to_playlist))
                "import_menu" -> add(InertButton(Res.drawable.import_outline, Res.string.import_playlist))
                "export_dialog" -> add(InertButton(Res.drawable.export_outline, Res.string.export_playlist))
                "export_cache" -> add(InertButton(Res.drawable.export_outline, Res.string.export_cached))
                "update" -> add(InertButton(Res.drawable.refresh, Res.string.info_open_update_dialog))
                "smart_trash" -> add(InertButton(Res.drawable.trash, Res.string.smart_trash))
            }
        }
        add(Refresh(lists::reloadActiveSongs))
    }

    // The user's chip order (the phone's `BuiltInPlaylist` labels)
    val chips = SongsChip.entries.map { it to stringResource(chipLabel(it)) }

    Box(
        modifier = Modifier.background(colorPalette().background0)
            .fillMaxHeight()
            .fillMaxWidth(),
    ) {
        CollapsibleHeaderScreen(
            enabled = state.items.isNotEmpty(),
            scrollOverHeader = true,
            header = { titleOffsetState, titleHeightState ->
                Column {
                    CollapsibleTitleRow(titleOffsetState, titleHeightState) {
                        TabHeader(stringResource(Res.string.songs)) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    HeaderInfo((state.total ?: state.items.size).toString(), Res.drawable.musical_notes)
                                }
                            }
                        }
                    }

                    TabToolBar.Buttons(buttons, disableAnimation = true)

                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 16.dp)
                            .padding(bottom = 8.dp)
                            .fillMaxWidth(),
                    ) {
                        Column {
                            ButtonsRow(
                                chips = chips,
                                currentValue = chip,
                                onValueUpdate = { selectChip(it) },
                                modifier = Modifier.padding(end = 12.dp),
                            )
                        }
                    }
                    search.SearchBar()
                }
            },
        ) { headerPadding ->
            Column(Modifier.fillMaxSize()) {
                HomeSongs(
                    list = activeList,
                    state = state,
                    lazyListState = lazyListState,
                    search = search,
                    actions = actions,
                    live = live,
                    headerPadding = headerPadding,
                )
            }
        }
        FloatingActionsContainerWithScrollToTop(lazyListState = lazyListState)
    }
}

/** The phone's `BuiltInPlaylist` label of the [chip]. */
private fun chipLabel(chip: SongsChip): StringResource = when (chip) {
    SongsChip.All -> Res.string.all
    SongsChip.Liked -> Res.string.favorites
    SongsChip.Disliked -> Res.string.disliked
    SongsChip.Top -> Res.string.playlist_top
    SongsChip.DownloadTel -> Res.string.downloaded
    SongsChip.DownloadPc -> Res.string.download_pc
    SongsChip.CachedTel -> Res.string.cached
    SongsChip.CachedPc -> Res.string.cached_pc
    SongsChip.OnDevice -> Res.string.on_device
}

/** The phone's default order of its Songs toolbar (`HomeSongsToolbarSettingsDialog.allButtonIds`). */
private val SONGS_TOOLBAR_BUTTON_IDS = listOf(
    "sort", "position_lock", "match", "search", "sync_ytm_likes", "locator",
    "download_all", "delete_downloads",
    "shuffle", "smart_shuffle", "item_selector",
    "play_next", "enqueue", "add_to_favorite", "add_to_playlist",
    "import_menu", "export_dialog", "export_cache", "update", "smart_trash",
)

/**
 * The buttons of the [chip]'s toolbar, in the phone's order (`HomeSongsToolbarSettingsDialog
 * .tabAvailableIds`). The PC chips take the set of their phone twin ("Download PC" ← "Downloaded",
 * "Cached PC" ← "Offline"); "sync_ytm_likes" never shows (no YouTube sync on the PC).
 */
private fun songsToolbarButtonIds(chip: SongsChip): List<String> = when (chip) {
    SongsChip.All, SongsChip.Liked -> SONGS_TOOLBAR_BUTTON_IDS.filter { it != "export_cache" }
    SongsChip.Disliked -> SONGS_TOOLBAR_BUTTON_IDS.filter { it != "export_cache" && it != "sync_ytm_likes" && it != "import_menu" }
    SongsChip.Top -> SONGS_TOOLBAR_BUTTON_IDS.filter { it !in setOf("import_menu", "position_lock", "export_cache", "sync_ytm_likes") }
    SongsChip.DownloadTel, SongsChip.DownloadPc, SongsChip.CachedTel, SongsChip.CachedPc ->
        SONGS_TOOLBAR_BUTTON_IDS.filter { it != "import_menu" && it != "sync_ytm_likes" }
    SongsChip.OnDevice -> SONGS_TOOLBAR_BUTTON_IDS.filter {
        it !in setOf("import_menu", "export_dialog", "export_cache", "smart_trash", "match", "download_all", "delete_downloads", "sync_ytm_likes", "update")
    }
}

/** The chips that are filtered on the phone's own database and need `library.sort` (contract 1.6). */
private val INERT_CHIPS_WITHOUT_LIBRARY_SORT = setOf(SongsChip.Disliked, SongsChip.CachedTel, SongsChip.Top)

/**
 * The [chip]'s sort options, as on the phone: "Downloaded" is hidden on the downloaded / cached chips
 * (the tab is already the downloaded / cached songs); on a phone without `library.sort`, the options
 * without a contract route are shown without effect.
 */
private fun chipSortOptions(chip: SongsChip, sortsOnPhone: Boolean): List<SortOption<SongSort>> {
    val options = if (sortsOnPhone) songSortOptions else legacySongSortOptions
    if (chip !in setOf(SongsChip.DownloadTel, SongsChip.DownloadPc, SongsChip.CachedTel, SongsChip.CachedPc)) return options
    return options.filter { it.value != SongSort.Downloaded }
}
