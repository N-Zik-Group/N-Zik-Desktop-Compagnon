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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.n_zik.compagnon.bridge.library.SongFilter
import app.n_zik.compagnon.bridge.state.QueuePosition
import app.n_zik.compagnon.components.ButtonsRow
import app.n_zik.compagnon.components.LocalMenuState
import app.n_zik.compagnon.components.Sort
import app.n_zik.compagnon.components.navigation.header.TabToolBar
import app.n_zik.compagnon.components.tab.Locator
import app.n_zik.compagnon.components.tab.Refresh
import app.n_zik.compagnon.components.tab.Search
import app.n_zik.compagnon.components.tab.SongShuffler
import app.n_zik.compagnon.components.tab.TabHeader
import app.n_zik.compagnon.components.tab.toolbar.Button
import app.n_zik.compagnon.colorPalette
import app.n_zik.compagnon.components.themed.Enqueue
import app.n_zik.compagnon.components.themed.FloatingActionsContainerWithScrollToTop
import app.n_zik.compagnon.components.themed.HeaderInfo
import app.n_zik.compagnon.components.themed.PlayNext
import app.n_zik.compagnon.generated.resources.Res
import app.n_zik.compagnon.generated.resources.all
import app.n_zik.compagnon.generated.resources.downloaded
import app.n_zik.compagnon.generated.resources.favorites
import app.n_zik.compagnon.generated.resources.musical_notes
import app.n_zik.compagnon.generated.resources.on_device
import app.n_zik.compagnon.generated.resources.songs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

/**
 * Port of `HomeSongsScreen` (phone's `app/n_zik/android/components/ui/screens/home/HomeSongsScreen.kt`,
 * header 573-787, layout 789-844).
 *
 * Kept: the collapsible header (`TabHeader` "Songs" + `HeaderInfo` count, the `TabToolBar`, the chips
 * row, the search bar), the list behind it, the scroll-to-top button.
 * Toolbar, in the phone's default order, reduced to contract v1: sort, search, locator, shuffle, play
 * next, enqueue (on the loaded tracks), plus the Compagnon's "Refresh".
 * Chips: the phone's `BuiltInPlaylist` chips reduced to the contract's filters, in the phone's order:
 * All (`all`), Favorites (`liked`), Downloaded (`downloaded`), On device (`local`).
 * Dropped (contract v1): position lock, match, YouTube likes sync, download all / delete downloads,
 * smart shuffle, multi-selection, add to favorites / to a playlist, import / export / export cache,
 * update, smart trash, the Cached / My top / Disliked chips, the YouTube filter chip, the cache
 * space indicator, the smart-recommendation counter, the floating search / settings icon.
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
    val state by lists.songs.state.collectAsState()
    val query by lists.songs.query.collectAsState()
    val searchText by lists.songsSearch.collectAsState()

    val showText: (StringResource) -> Unit = { id -> scope.launch { onMessage(getString(id)) } }
    val search = Search(searchText, lists::onSongsSearch, lazyListState)
    val locator = Locator(lazyListState, { lists.songs.state.value.items }, onMessage = showText)
    val songSort = Sort(menuState, query.sort) { lists.songs.setQuery(query.copy(sort = it)) }
    val playbackEnabled = live && actions.available

    val buttons = mutableListOf<Button>().apply {
        add(songSort)
        add(search)
        add(locator)
        if (actions.available) {
            add(SongShuffler(enabled = playbackEnabled) { lists.songs.state.value.let { actions.playShuffled(it.items, it.total ?: it.items.size) } })
            add(PlayNext(enabled = playbackEnabled) { lists.songs.state.value.let { actions.addAll(it.items, QueuePosition.Next, it.total ?: it.items.size) } })
            add(Enqueue(enabled = playbackEnabled) { lists.songs.state.value.let { actions.addAll(it.items, QueuePosition.End, it.total ?: it.items.size) } })
        }
        add(Refresh(lists.songs::reload))
    }

    val chips = listOf(
        SongFilter.All to stringResource(Res.string.all),
        SongFilter.Liked to stringResource(Res.string.favorites),
        SongFilter.Downloaded to stringResource(Res.string.downloaded),
        SongFilter.Local to stringResource(Res.string.on_device),
    )

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
                                currentValue = query.filter,
                                onValueUpdate = { lists.songs.setQuery(query.copy(filter = it)) },
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
                    list = lists.songs,
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
