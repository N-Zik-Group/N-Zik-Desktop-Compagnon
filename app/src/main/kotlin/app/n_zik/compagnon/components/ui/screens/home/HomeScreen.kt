package app.n_zik.compagnon.components.ui.screens.home

import app.n_zik.compagnon.components.navigation.pageTransition
import app.n_zik.compagnon.components.navigation.tabTransition
import app.n_zik.compagnon.LocalBottomBarOffset
import app.n_zik.compagnon.enums.HomeScreenTabs
import app.n_zik.compagnon.enums.NavigationBarPosition
import app.n_zik.compagnon.enums.NavigationBarType
import app.n_zik.compagnon.utils.LocalPreferences
import app.n_zik.compagnon.utils.UserSettings
import kotlin.math.roundToInt
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.layout.offset
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.MutableStateFlow
import org.jetbrains.compose.resources.stringResource
import app.n_zik.compagnon.bridge.library.LibraryRepository
import app.n_zik.compagnon.colorPalette
import app.n_zik.compagnon.components.navigation.nav.HorizontalNavigationBar
import app.n_zik.compagnon.components.navigation.nav.NavigationTab
import app.n_zik.compagnon.components.navigation.nav.VerticalNavigationBar
import app.n_zik.compagnon.components.ui.screens.album.AlbumScreen
import app.n_zik.compagnon.components.ui.screens.artist.ArtistScreen
import app.n_zik.compagnon.components.ui.screens.localplaylist.LocalPlaylistSongs
import app.n_zik.compagnon.generated.resources.*
/**
 * Port of `HomeScreen` (phone's `app/n_zik/android/components/ui/screens/home/HomeScreen.kt` 182-342) with its
 * `Skeleton` (`app/it/fast4x/rimusic/ui/components/Skeleton.kt` 106-338): the tabs Songs, Artists, Albums,
 * Playlists (each hidden without its `library.*` feature), their content switched with the phone's tab
 * transition (its `transitionEffect`, `ui.settings` since 1.10.0; `Fade` 350 ms by default), each tab's
 * state kept (`rememberSaveableStateHolder`), and the list of the tab that just became visible re-read
 * from the phone (its own tabs always are: they read its database; the PC's are REST snapshots —
 * `LibraryLists.reload`).
 * The navigation bar is drawn in the position and type of the settings (spec
 * `spec-fill-pc-ui-gaps-home` #45, the phone's `Skeleton.Draw`): `BottomFloating` = the floating bar
 * over the bottom (sliding out with the scroll, `LocalBottomBarOffset`), `Bottom` = the full-width
 * fixed bar under the content (the same slide), `Top` = the full-width fixed bar over the content,
 * `Left`/`Right` = the vertical rail next to the content. The start tab (#46) is the phone's
 * `indexNavigationTab` while [UserSettings.homeScreenTabIndex] is unset, then the last tab used
 * (persisted on tab change); both are applied once, at the first composition (the phone's
 * `rememberPreference` semantics — a live change of the start tab applies on the next launch).
 * An opened playlist, album or artist ([detail], held by the window for the header's back button)
 * replaces the home with the phone's page transition (its `transitionEffect`, the pop transitions on
 * the way back, `AppNavigation.kt` 183-229), as the phone's navigation to its own screen (which has
 * no bar).
 * Dropped: Quick picks (not in the contract), the tab shortcuts, the update dialogs, the
 * exit-on-double-back, and the phone's landscape bar toggle (no mobile landscape on the desktop).
 * [onNavBarVisible] tells the window whether the bar is shown (the mini-player sits above it).
 */
@Composable
fun HomeScreen(
    lists: LibraryLists,
    library: LibraryRepository,
    actions: LibraryActions,
    live: Boolean,
    onMessage: (String) -> Unit,
    detail: CollectionHeader?,
    /** The last navigation was a back (the phone's pop transitions); a title click home is forward. */
    isBackNavigation: Boolean = detail == null,
    onDetail: (CollectionHeader?) -> Unit,
    /** A page closing itself (its back, "not found"): a back navigation. */
    onDetailBack: () -> Unit = { onDetail(null) },
    onNavBarVisible: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val saveableStateHolder = rememberSaveableStateHolder()
    val scope = rememberCoroutineScope()
    val tabs = LibraryTab.visible(library.features)
    val preferences = LocalPreferences.current
    val settings by (preferences?.settings ?: remember { MutableStateFlow(UserSettings()) }).collectAsState()

    // #46: the start tab (the phone's `indexNavigationTab`) applies while the last tab used
    // (`homeScreenTabIndex`) has never been written; a persisted value (including 0) then takes
    // precedence. Computed once, at the first composition — a live change applies on the next launch.
    val initialTabIndex = remember {
        initialHomeTabIndex(settings.homeScreenTabIndex, settings.indexNavigationTabValue, tabs.map { it.name.lowercase() })
    }
    var tabIndex by remember { mutableIntStateOf(initialTabIndex) }
    if (tabIndex >= tabs.size) tabIndex = 0

    val changeTab: (Int) -> Unit = { newTab ->
        if (newTab != tabIndex) {
            tabIndex = newTab
            preferences?.update { it.copy(homeScreenTabIndex = newTab) }
        }
    }

    // The phone's tabs are always fresh (its own database): the tab that just became visible is re-read
    LaunchedEffect(tabIndex) { tabs.getOrNull(tabIndex)?.let { lists.reload(it) } }
    val onBack = onDetailBack
    val bottomBarOffset = LocalBottomBarOffset.current

    val navBarVisible = detail == null && tabs.size >= 2
    LaunchedEffect(navBarVisible) { onNavBarVisible(navBarVisible) }

    val position = settings.navigationBarPositionValue
    val navBarType = settings.navigationBarTypeValue
    val navTabs = tabs.map { NavigationTab(stringResource(it.textId), it.iconId) }
    val transition = app.n_zik.compagnon.bridge.state.LocalUiSettings.current.transition

    val tabsContent: @Composable () -> Unit = {
        AnimatedContent(
            targetState = tabIndex,
            // The phone's tab switch (`EffectHandler.kt` `transition()`): its `transitionEffect`
            transitionSpec = { tabTransition(transition) },
            label = "",
            modifier = Modifier.fillMaxSize(),
        ) { currentTabIndex ->
            saveableStateHolder.SaveableStateProvider(key = currentTabIndex) {
                when (tabs.getOrNull(currentTabIndex)) {
                    LibraryTab.Songs -> HomeSongsScreen(lists, actions, live, scope, onMessage)
                    LibraryTab.Artists -> HomeArtists(lists, actions, live) { onDetail(CollectionHeader.OfArtist(it)) }
                    LibraryTab.Albums -> HomeAlbums(lists, actions, live) { onDetail(CollectionHeader.OfAlbum(it)) }
                    LibraryTab.Playlists -> HomeLibrary(lists, actions, live) { playlist, firstTracks ->
                        onDetail(CollectionHeader.OfPlaylist(playlist, firstTracks))
                    }
                    null -> Unit
                }
            }
        }
    }

    AnimatedContent(
        targetState = detail,
        // The phone's page transition (its `transitionEffect`, `ui.settings` since 1.10.0)
        transitionSpec = { pageTransition(transition, isBack = isBackNavigation) },
        label = "page",
        modifier = modifier.fillMaxWidth().background(colorPalette().background0),
    ) { opened ->
      Box(modifier = Modifier.fillMaxSize()) {
        when {
            tabs.isEmpty() -> NoItems(stringResource(Res.string.library_none))
            opened is CollectionHeader.OfAlbum -> AlbumScreen(opened, library, actions, live, onMessage, onBack)
            opened is CollectionHeader.OfArtist -> ArtistScreen(opened, library, actions, live, onMessage, onBack)
            opened is CollectionHeader.OfPlaylist -> LocalPlaylistSongs(opened, library, actions, live, onMessage, onBack)
            else -> saveableStateHolder.SaveableStateProvider(key = "home") {
              Box(modifier = Modifier.fillMaxSize()) {
                // The phone's `Skeleton.Draw` layout of the five positions
                when (position) {
                    NavigationBarPosition.Left -> Row(modifier = Modifier.fillMaxSize()) {
                        VerticalNavigationBar(
                            tabs = navTabs,
                            tabIndex = tabIndex,
                            onTabChanged = changeTab,
                            type = navBarType,
                            modifier = Modifier.fillMaxHeight(),
                        )
                        Box(modifier = Modifier.weight(1f).fillMaxHeight()) { tabsContent() }
                    }
                    NavigationBarPosition.Right -> Row(modifier = Modifier.fillMaxSize()) {
                        Box(modifier = Modifier.weight(1f).fillMaxHeight()) { tabsContent() }
                        VerticalNavigationBar(
                            tabs = navTabs,
                            tabIndex = tabIndex,
                            onTabChanged = changeTab,
                            type = navBarType,
                            modifier = Modifier.fillMaxHeight(),
                        )
                    }
                    NavigationBarPosition.Top -> Column(modifier = Modifier.fillMaxSize()) {
                        HorizontalNavigationBar(
                            tabs = navTabs,
                            tabIndex = tabIndex,
                            onTabChanged = changeTab,
                            position = position,
                            type = navBarType,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Box(modifier = Modifier.weight(1f).fillMaxWidth()) { tabsContent() }
                    }
                    NavigationBarPosition.Bottom -> Column(modifier = Modifier.fillMaxSize()) {
                        Box(modifier = Modifier.weight(1f).fillMaxWidth()) { tabsContent() }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .offset { IntOffset(0, bottomBarOffset.value.roundToInt()) },
                        ) {
                            HorizontalNavigationBar(
                                tabs = navTabs,
                                tabIndex = tabIndex,
                                onTabChanged = changeTab,
                                position = position,
                                type = navBarType,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                    NavigationBarPosition.BottomFloating -> Box(modifier = Modifier.fillMaxSize()) {
                        Box(modifier = Modifier.fillMaxSize()) { tabsContent() }
                        HorizontalNavigationBar(
                            tabs = navTabs,
                            tabIndex = tabIndex,
                            onTabChanged = changeTab,
                            position = position,
                            type = navBarType,
                            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                                .offset { IntOffset(0, bottomBarOffset.value.roundToInt()) },
                        )
                    }
                }
              }
            }
        }
      }
    }
}

/**
 * #46: the home's first tab, with the phone's `rememberPreference` precedence — a persisted
 * last-used tab ([lastUsed], written on every tab change) wins, including 0; while it is `null`
 * (never written) the start tab ([startTab], the phone's `indexNavigationTab`) resolves among the
 * visible [tabIds].
 */
internal fun initialHomeTabIndex(lastUsed: Int?, startTab: HomeScreenTabs, tabIds: List<String>): Int =
    lastUsed ?: resolveInitialTabIndex(startTab, tabIds)

/** The home tab id for a [HomeScreenTabs] index, or null when [index] is not a home tab (the phone's `tabIdForIndex`). */
private fun tabIdForIndex(index: Int): String? = when (index) {
    HomeScreenTabs.QuickPics.index -> "quickpicks"
    HomeScreenTabs.Songs.index -> "songs"
    HomeScreenTabs.Artists.index -> "artists"
    HomeScreenTabs.Albums.index -> "albums"
    HomeScreenTabs.Playlists.index -> "playlists"
    else -> null
}

/**
 * The phone's `resolveInitialTabIndex` (its `HomeScreen.kt` 82-102), without the launcher shortcut
 * input (the PC has none): the preferred tab's index is mapped to a home tab id —
 * [HomeScreenTabs.Default] resolving to the phone's Quick Picks id, which the PC has no tab for, so
 * the first visible tab — and looked up among the visible tabs; an absent tab falls back to the
 * first one.
 */
internal fun resolveInitialTabIndex(preferredTab: HomeScreenTabs, activeTabIds: List<String>): Int {
    val targetIndex = when (preferredTab) {
        HomeScreenTabs.Default -> HomeScreenTabs.QuickPics.index
        else -> preferredTab.index
    }
    val targetTabId = tabIdForIndex(targetIndex) ?: "quickpicks"
    val foundIndex = activeTabIds.indexOf(targetTabId)
    return if (foundIndex != -1) foundIndex else 0
}
