package app.n_zik.compagnon.components.ui.screens.home

import app.n_zik.compagnon.LocalBottomBarOffset
import kotlin.math.roundToInt
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.layout.offset
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import app.n_zik.compagnon.bridge.library.LibraryRepository
import app.n_zik.compagnon.colorPalette
import app.n_zik.compagnon.components.navigation.nav.HorizontalNavigationBar
import app.n_zik.compagnon.components.navigation.nav.NavigationTab
import app.n_zik.compagnon.components.ui.screens.album.AlbumScreen
import app.n_zik.compagnon.components.ui.screens.artist.ArtistScreen
import app.n_zik.compagnon.components.ui.screens.localplaylist.LocalPlaylistSongs
import app.n_zik.compagnon.generated.resources.Res
import app.n_zik.compagnon.generated.resources.library_none

/**
 * Port of `HomeScreen` (phone's `app/n_zik/android/components/ui/screens/home/HomeScreen.kt` 182-342) with its
 * `Skeleton` (`app/it/fast4x/rimusic/ui/components/Skeleton.kt` 106-338) in the default `BottomFloating`
 * position: the tabs Songs, Artists, Albums, Playlists (each hidden without its `library.*` feature),
 * their content cross-faded in 350 ms (the default `TransitionEffect.Fade`), each tab's state kept
 * (`rememberSaveableStateHolder`), and the floating bar over the bottom (sliding out with the scroll,
 * `LocalBottomBarOffset`). An opened playlist, album or artist ([detail], held by the window for the header's
 * back button) replaces the home with the phone's default page transition (`TransitionEffect.Fade`: 350 ms
 * fades, `AppNavigation.kt` 183-229), as the phone's navigation to its own screen (which has no bar).
 * Dropped: Quick picks (not in the contract), the tab shortcuts and the tab order / visibility
 * preferences (defaults kept), the update dialogs, the exit-on-double-back.
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
    onDetail: (CollectionHeader?) -> Unit,
    onNavBarVisible: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val saveableStateHolder = rememberSaveableStateHolder()
    val scope = rememberCoroutineScope()
    val tabs = LibraryTab.visible(library.features)
    var tabIndex by remember { mutableStateOf(0) }
    if (tabIndex >= tabs.size) tabIndex = 0
    val onBack = { onDetail(null) }
    val bottomBarOffset = LocalBottomBarOffset.current

    val navBarVisible = detail == null && tabs.size >= 2
    LaunchedEffect(navBarVisible) { onNavBarVisible(navBarVisible) }

    AnimatedContent(
        targetState = detail,
        transitionSpec = { fadeIn(tween(350)) togetherWith fadeOut(tween(350)) },
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
                AnimatedContent(
                    targetState = tabIndex,
                    transitionSpec = { fadeIn(tween(350)) togetherWith fadeOut(tween(350)) },
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

                HorizontalNavigationBar(
                    tabs = tabs.map { NavigationTab(stringResource(it.textId), it.iconId) },
                    tabIndex = tabIndex,
                    onTabChanged = { tabIndex = it },
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                        .offset { IntOffset(0, bottomBarOffset.value.roundToInt()) },
                )
              }
            }
        }
      }
    }
}
