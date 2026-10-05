package app.n_zik.compagnon.components.ui.screens.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.n_zik.compagnon.bridge.library.LibraryCache
import app.n_zik.compagnon.bridge.library.SongFilter
import app.n_zik.compagnon.bridge.library.SongSort
import app.n_zik.compagnon.bridge.library.SongsQuery
import app.n_zik.compagnon.bridge.library.TopPeriod
import app.n_zik.compagnon.bridge.state.QueuePosition
import app.n_zik.compagnon.bridge.state.SessionContract
import app.n_zik.compagnon.bridge.state.unmatched
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
import app.n_zik.compagnon.components.ui.screens.settings.formatShortFileSize
import app.n_zik.compagnon.colorPalette
import app.n_zik.compagnon.components.themed.CacheSpaceIndicator
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
import app.n_zik.compagnon.generated.resources.info_lock_unlock_reorder_songs
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
import app.n_zik.compagnon.generated.resources.update
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
 * Chips: the phone's `BuiltInPlaylist` chips in the phone's order, around the PC-only ones (the phone's
 * all, favorites, disliked, cached, downloaded, top, on_device) — All, Liked, Disliked, Cached, Cached
 * PC, Download, Download PC, Top, "On device phone" ([SongsChip]). The phone's chips read the phone
 * through the contract's `filter` (since 1.6); "Download PC" is a placeholder (no PC download store yet)
 * and "On device phone" is empty (the PC reads nothing from the phone's storage); "Cached PC" keeps the
 * tracks of the phone's list that sit in the Compagnon's local audio cache ([LibraryLists.songsPcCached]).
 *
 * Toolbar: the active chip's toolbar as-served by the phone since 1.8.0 (feature `library.toolbar`:
 * its `toolbar` of `GET /library/songs` — the user's order kept to the tab's visible buttons, the
 * hidden ones dropped, the locked ones always kept; the PC-only chips read it from their mobile
 * counterpart's probe), with the phone's show conditions (position lock only while the chip's sort
 * is `Custom`, match only while unmatched tracks are loaded, no YouTube sync on the PC), plus the
 * Compagnon's "Refresh". Until the first answered page — or on a phone before 1.8.0 — the phone's
 * default buttons of the active chip stand in ([HomeSongsToolbarSettingsDialog] `allButtonIds` /
 * `tabAvailableIds`). Wired: sort (or the period
 * selector on Top), search, locator, shuffle, play next and enqueue (on the loaded tracks); the rest
 * (position lock, match, download all / delete downloads, smart shuffle, item selector, add to
 * favorites / to a playlist, import / export, update, smart trash) are placeholders without a contract
 * route, a click does nothing; the buttons that do not fit the row go behind the "…" menu. The phone
 * reads its order synchronously from local preferences, so its tabs are always the chip's own toolbar;
 * the PC's served list is null while the chip's page loads — the chip's last served toolbar stands in
 * (the previously displayed one on a first visit in the session, the static default only before any
 * page at all), and the row crossfades on a change (the phone's 300 ms — its home tabs disable this
 * animation).
 *
 * Sort: the phone keeps one sort per tab — the chip's sort and direction live in the user settings
 * (`chipSorts`, key `songs:<chip>`), applied to the chip's list on every change and on the first
 * composition (a cold start opens in the chip's own sort, not the list's default); the Liked / Disliked
 * chips default to the phone's `DateLiked` (its `HOME_SONGS_FAVORITES_SORT_BY` /
 * `HOME_SONGS_DISLIKED_SORT_BY`); the Top chip replaces the sort with the phone's period selector
 * (`period`, the phone's own period when unset); the "Downloaded" option is hidden on the downloaded /
 * cached chips, as on the phone. On a phone without `library.sort` (contract < 1.6) the options without
 * a route are shown without effect, the direction is applied client-side, and the Disliked / Cached / Top
 * chips are inert (they are filtered on the phone's own database, which the contract cannot reach before
 * 1.6). The header's count is live: the wire's `total` is the post-search count while a search is
 * active, as on the phone (its `itemsOnDisplayState.size`).
 *
 * Since 1.7.1: the phone's storage bar of the cached / downloaded chips (contract §10 `library.cache`,
 * the phone's `HomeSongsScreen.kt` 763-782; hidden while the phone's limit is `Unlimited`), the
 * `playCount` / `playTime` row overlays, and the sentinel alert of a track not matched to the phone's
 * library (a click is blocked with the phone's toast).
 *
 * Dropped (contract v1 or PC): YouTube likes sync, the chip visibility / order preferences, the
 * YouTube filter row, the smart-recommendation counter, the floating search / settings icon.
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
    val chipSort = settings?.chipSorts?.get(chip.key) ?: chip.defaultSort()
    val activeList = lists.activeSongsList()
    val state by activeList.state.collectAsState()
    val searchText by lists.songsSearch.collectAsState()

    // Since 1.7.3 (feature `library.sortMenu`): the phone's effective sort menu of the active chip —
    // on the phone's chips it rides on the list's pages; on the PC-only chips it is read from their
    // mobile counterpart ("Cached PC" ← Offline, "Download PC" ← Downloaded) with a one-track probe
    val pcChipSortMenus by lists.pcChipSortMenus.collectAsState()
    val sortMenu: List<String>? = when (chip) {
        SongsChip.CachedPc, SongsChip.DownloadPc, SongsChip.OnDevice -> pcChipSortMenus[chip]
        else -> state.sortMenu
    }

    // Since 1.8.0 (feature `library.toolbar`): the phone's effective Home Songs toolbar of the active
    // chip — on the phone's chips it rides on the list's pages; on the PC-only chips it is read from
    // their mobile counterpart with the same one-track probe as their sort menu
    val pcChipToolbars by lists.pcChipToolbars.collectAsState()
    val servedToolbar: List<String>? = when (chip) {
        SongsChip.CachedPc, SongsChip.DownloadPc, SongsChip.OnDevice -> pcChipToolbars[chip]
        else -> state.toolbar
    }

    // The phone reads its toolbar order synchronously from local preferences — its tabs never stand
    // a default in while loading. The PC's served list is null while the chip's page loads (the
    // list's reload resets it): the chip's last served toolbar stands in (a revisit shows the chip's
    // own toolbar immediately, and the page's arrival is a no-op when it confirms it); on a first
    // visit in the session, the previously displayed toolbar stands in — the static default is the
    // last resort, only the very first chip of a session, before any page
    var lastServedToolbars by remember { mutableStateOf<Map<SongsChip, List<String>?>>(emptyMap()) }
    var previousToolbar by remember { mutableStateOf<List<String>?>(null) }
    val toolbar = servedToolbar ?: lastServedToolbars[chip] ?: previousToolbar
    LaunchedEffect(chip, servedToolbar) {
        if (servedToolbar != null) {
            lastServedToolbars = lastServedToolbars + (chip to servedToolbar)
            previousToolbar = servedToolbar
        }
    }
    val toolbarFeature = SessionContract.FEATURE_LIBRARY_TOOLBAR in lists.features

    // Since 1.7.1: the phone's disk caches (contract §10 `library.cache`), read on a tab switch;
    // `null` hides the cache bar
    var libraryCache by remember { mutableStateOf<LibraryCache?>(null) }
    LaunchedEffect(lists) {
        libraryCache = if (SessionContract.FEATURE_LIBRARY_CACHE in lists.features) lists.cacheSpace() else null
    }

    // Since 1.7.2 (feature `library.dislikeMode`): the phone's "disliked" mode (its `DislikeMode.Enabled`
    // per collection, read once per session by the lists) — `false` hides the Disliked chip, like on
    // the phone; `null` keeps the pre-1.7.2 display (the phone's own default: the mode enabled, the
    // chip shown)
    val dislikeMode by lists.dislikeMode.collectAsState()

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
        applyChipQuery(target, settings?.chipSorts?.get(target.key) ?: target.defaultSort())
    }

    /** The chip's sort changed: persisted (the phone keeps one sort per tab), then applied. */
    fun applyChipSort(sort: ChipSort) {
        preferences?.update { it.copy(chipSorts = it.chipSorts + (chip.key to sort)) }
        applyChipQuery(chip, sort)
    }

    // The persisted sort of the visible chip, applied on the first composition: a cold start opens in
    // the chip's own sort, not the list's default query (a no-op while the persisted sort is the default)
    LaunchedEffect(lists) { applyChipQuery(chip, settings?.chipSorts?.get(chip.key) ?: chip.defaultSort()) }

    // Since 1.7.3: the PC-only chips read their sort menu from their mobile counterpart; a no-op on
    // the phone's chips
    LaunchedEffect(chip, lists) {
        if (SessionContract.FEATURE_LIBRARY_SORT_MENU in lists.features) lists.loadPcChipSortMenu(chip)
    }

    // The phone's `hasUnmatchedSongs` (HomeSongsScreen.kt 463, the phone's full check — `Track.unmatched`,
    // contract 1.7.1): a non-YT id or the zero-duration sentinel, the phone's `local:` files excluded —
    // the match button is shown when one is loaded
    val hasUnmatched = state.items.any { it.unmatched() }

    // The chip's sort button (the phone's per-tab sort). Top replaces it with the period selector;
    // Disliked keeps the arrow like on the phone (the phone's provider ignores the sort there)
    val sortButton: Button = if (chip == SongsChip.Top) {
        // Since 1.7.3: the phone's Top tab menu — its periods in its order, its hidden ones dropped
        PeriodSelector(menuState, chipSort.topPeriod, topPeriodOptions(sortMenu)) { period ->
            applyChipSort(chipSort.copy(period = period.wire))
        }
    } else if (sortsOnPhone) {
        Sort(
            menuState,
            chipSortOptions(chip, sortsOnPhone, sortMenu),
            chipSort.songSort,
            chipSort.reverse,
            onSortBy = { sort -> applyChipSort(chipSort.copy(sort = sort.wire)) },
            onSortDirection = { reverse -> applyChipSort(chipSort.copy(reverse = reverse)) },
        )
    } else {
        Sort(
            menuState,
            chipSortOptions(chip, sortsOnPhone, sortMenu),
            chipSort.songSort,
            chipSort.reverse,
            onSortBy = { sort -> applyChipSort(chipSort.copy(sort = sort.wire)) },
            onSortDirection = { reverse -> applyChipSort(chipSort.copy(reverse = reverse)) },
            baseRotation = chipSort.songSort.baseRotation,
        )
    }

    // The active chip's toolbar, as-served by the phone since 1.8.0 (its user order and enabled
    // toggles; the static default buttons stand in while the feature is absent or the served list
    // is null/empty), with the phone's show conditions; the Compagnon's "Refresh" is added at the
    // end, always last. Wired: sort / period, search, locator, shuffle, play next, enqueue, refresh.
    // Placeholders (no contract route, a click does nothing): the rest. Built from the ids — the
    // button instances are fresh on every composition (reference equality), so the ids (value
    // equality) are the toolbar change's animation target (its `AnimatedContent`).
    val toolbarIds = chipToolbarIds(chip, toolbarFeature, toolbar)

    @Composable
    fun toolbarButtons(ids: List<String>): List<Button> = buildList {
        ids.forEach { id ->
            when (id) {
                "sort" -> add(sortButton)
                "position_lock" -> if (chipSort.songSort == SongSort.Custom) {
                    add(InertButton(Res.drawable.locked, Res.string.info_lock_unlock_reorder_songs))
                }
                "match" -> if (hasUnmatched) add(InertButton(Res.drawable.alert, Res.string.match_album_audio_version))
                "search" -> add(search)
                "locator" -> add(locator)
                "download_all" -> add(InertButton(Res.drawable.downloaded, Res.string.download))
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
                // Since 1.7.2 (feature `library.ffmpeg`): shown only when the phone's build ships FFmpeg,
                // as on the phone (its `HomeSongsScreen.kt` 555)
                "export_cache" -> if (SessionContract.FEATURE_LIBRARY_FFMPEG in lists.features) {
                    add(InertButton(Res.drawable.export_outline, Res.string.export_cached))
                }
                "update" -> add(InertButton(Res.drawable.refresh, Res.string.update))
                "smart_trash" -> add(InertButton(Res.drawable.trash, Res.string.smart_trash))
            }
        }
        add(Refresh(lists::reloadActiveSongs))
    }

    // The user's chip order (the phone's `BuiltInPlaylist` labels); since 1.7.2 the phone's "disliked"
    // mode hides its Disliked chip when off (its `HomeSongsScreen.kt` 690)
    val chips = SongsChip.entries
        .filter { it != SongsChip.Disliked || dislikeMode?.songs != false }
        .map { it to stringResource(chipLabel(it)) }

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
                            // The phone's live count (its `itemsOnDisplayState.size`): the wire's `total` is
                            // the post-search count while a search is active, as on the phone
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    HeaderInfo((state.total ?: state.items.size).toString(), Res.drawable.musical_notes)
                                }
                            }
                        }
                    }

                    // The chip change animates the toolbar row (the phone's `AnimatedContent`, its
                    // `TabToolBar.kt` 91-98): the target is the ids (value equality — the button
                    // instances are reference-fresh on every composition, so a plain `List<Button>`
                    // would animate on every unrelated recomposition); the incoming row fades in
                    // quickly and the outgoing one is removed immediately (no lingering of the
                    // previous chip's toolbar while the new chip's page loads)
                    AnimatedContent(
                        targetState = toolbarIds,
                        label = "SongsToolbarAnimation",
                        transitionSpec = {
                            // A real crossfade on a change (both rows fade over the 300 ms, the
                            // phone's default): only the page's arrival animates — while a chip's
                            // page loads the row keeps the previous toolbar, so nothing lingers
                            ContentTransform(fadeIn(tween(300)), fadeOut(tween(300)))
                        },
                    ) { ids ->
                        TabToolBar.Buttons(toolbarButtons(ids), disableAnimation = true)
                    }

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

                    // The cache space bar (contract 1.7.1, phone's `HomeSongsScreen.kt` 763-782): on the
                    // cached / downloaded chips, the phone's cache used over its configured cap; on the
                    // "Cached PC" chip, the Compagnon's own audio cache (contract §8.3) used over its
                    // ceiling — an unlimited cap hides the bar, like on the phone
                    val phoneCache = when (chip) {
                        SongsChip.CachedTel -> libraryCache?.cached
                        SongsChip.DownloadTel -> libraryCache?.downloaded
                        else -> null
                    }
                    val pcCache = if (chip == SongsChip.CachedPc) lists.audioCache else null
                    AnimatedVisibility(visible = phoneCache?.maxBytes != null || pcCache?.ceiling != null) {
                        when (val cache = pcCache) {
                            null -> CacheSpaceIndicator(
                                usedBytes = phoneCache?.usedBytes ?: 0L,
                                maxBytes = phoneCache?.maxBytes,
                                // Since 1.7.2: the phone's own label of its cap ("2GB", "Custom", "Turn off",
                                // its `ExoPlayerDiskCacheMaxSize.text`); a ≤ 1.7.1 phone does not send it,
                                // then the client formats its cap itself, as before
                                maxText = phoneCache?.maxText ?: formatShortFileSize(phoneCache?.maxBytes ?: 0L),
                            )
                            else -> CacheSpaceIndicator(
                                usedBytes = cache.totalBytes(),
                                maxBytes = cache.ceiling,
                                maxText = formatShortFileSize(cache.ceiling ?: 0L),
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
                    // Since 1.7.1: the phone's listening-sort overlays and the Top chip's rank
                    sort = chipSort.songSort,
                    isTop = chip == SongsChip.Top,
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

/**
 * The [chip]'s toolbar ids, in the order to build its buttons. Since 1.8.0 (feature
 * `library.toolbar`), the phone serves the chip's effective toolbar — its user order kept to its
 * tab's visible buttons (its hidden ones dropped, its locked ones always kept) — and it is shown
 * as-served; the static default buttons stand in while the feature is absent or the served list
 * is null/empty (a phone before 1.8.0, or before the first answered page). Ids the screen has no
 * button branch for are dropped by its `when`; "Refresh" is added by the caller, always last.
 */
internal fun chipToolbarIds(chip: SongsChip, toolbarFeature: Boolean, served: List<String>?): List<String> {
    if (!toolbarFeature) return songsToolbarButtonIds(chip)
    return served
        ?.filter { id -> id in SONGS_TOOLBAR_BUTTON_IDS }
        ?.takeIf { it.isNotEmpty() }
        ?: songsToolbarButtonIds(chip)
}

/** The chips that are filtered on the phone's own database and need `library.sort` (contract 1.6). */
private val INERT_CHIPS_WITHOUT_LIBRARY_SORT = setOf(SongsChip.Disliked, SongsChip.CachedTel, SongsChip.Top)

/**
 * The [chip]'s own default sort (used while it has no persisted sort yet): the phone's favorites and
 * disliked tabs are sorted by `DateLiked` (its `HOME_SONGS_FAVORITES_SORT_BY` /
 * `HOME_SONGS_DISLIKED_SORT_BY`), the others by the list's default (`Title`).
 */
private fun SongsChip.defaultSort(): ChipSort = when (this) {
    SongsChip.Liked, SongsChip.Disliked -> ChipSort(sort = SongSort.DateLiked.wire)
    else -> ChipSort()
}

/**
 * The Top chip's period options. Since 1.7.3 (feature `library.sortMenu`), the phone serves its
 * Top tab's periods — its content and order — and they are shown as-is; the static periods stand
 * in until the first page (or on a ≤ 1.7.2 phone).
 */
internal fun topPeriodOptions(sortMenu: List<String>?): List<TopPeriod> {
    val options = sortMenu?.mapNotNull { id -> TopPeriod.entries.firstOrNull { period -> period.wire == id } }
    return options?.takeIf { it.isNotEmpty() } ?: TopPeriod.entries
}

/**
 * The [chip]'s sort options. Since 1.7.3 (feature `library.sortMenu`), the phone serves the chip's
 * effective menu — its content and order (its saved order kept to its visible options) — and it is
 * shown as-is; the static options stand in until the first page (or on a ≤ 1.7.2 phone), and the
 * unknown ids (the phone's Top periods) are dropped, the PC's Top selector keeping them. "Downloaded"
 * is hidden on the downloaded / cached chips, as on the phone; on a phone without `library.sort`,
 * the options without a contract route are shown without effect.
 */
internal fun chipSortOptions(chip: SongsChip, sortsOnPhone: Boolean, sortMenu: List<String>?): List<SortOption<SongSort>> {
    val menu = if (sortsOnPhone) sortMenu else null
    val options = when {
        // The phone's menu: its ids in its order, the unknown ones dropped
        !menu.isNullOrEmpty() ->
            menu.mapNotNull { id -> songSortOptions.firstOrNull { option -> option.value == SongSort.fromWire(id) } }
                .takeIf { it.isNotEmpty() } ?: songSortOptions
        sortsOnPhone -> songSortOptions
        else -> legacySongSortOptions
    }
    if (chip !in setOf(SongsChip.DownloadTel, SongsChip.DownloadPc, SongsChip.CachedTel, SongsChip.CachedPc)) return options
    return options.filter { it.value != SongSort.Downloaded }
}
