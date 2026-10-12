package app.n_zik.compagnon.components.ui.screens.settings

import app.n_zik.compagnon.components.navigation.tabTransition
import app.n_zik.compagnon.LocalBottomBarOffset
import app.n_zik.compagnon.components.navigation.nav.HorizontalNavigationBar
import app.n_zik.compagnon.components.navigation.nav.NavigationTab
import app.n_zik.compagnon.generated.resources.*
import app.n_zik.compagnon.playback.cache.AudioCache
import app.n_zik.compagnon.utils.Preferences
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The Compagnon's settings page (spec `spec-settings-navigation` NAV-1/NAV-3/NAV-9/NAV-13): a navigation
 * destination of the main window (the home content and the connection banner replaced, the window
 * header's back arrow closes it) with the phone's settings screens as **7 sub-tabs** — Général /
 * User Interface / Player appearance / Données / Réseau / Autres / About, a mirror of the phone's
 * tabs (the 2 new phone tabs ported by spec `spec-fill-pc-ui-gaps-settings`). The floating bar is the
 * home's [HorizontalNavigationBar] (the phone's `BottomFloating` / `IconOnly` default), sliding out
 * with the scroll like it does on the home ([LocalBottomBarOffset]); the tab change is an
 * [AnimatedContent] (the phone's `Skeleton` pattern) and each tab keeps its own scroll state. The tab
 * state is owned by the window ([settingsTab] / [onTabChanged]) and reset to 0 when the page is closed.
 *
 * This file is the **container only** (NAV-13): the 7 sub-tabs are 7 page files mirroring the phone's
 * file structure — [GeneralSettingsScreen] (the phone's `GeneralSettings.kt`), [UiSettingsScreen]
 * (`UiSettings.kt`), [AppearanceSettingsScreen] (`AppearanceSettings.kt`), [DataSettingsScreen]
 * (`DataSettings.kt`), [NetworkSettingsScreen] (`NetworkSettings.kt`), [OtherSettingsScreen]
 * (`OtherSettings.kt`) and [AboutScreen] (`About.kt`) — each keeping its phone screen's structure (its
 * `HeaderWithIcon(enabled = false)` + `SettingsDescription` + the section cards). The About tab is the
 * [AboutScreen] itself (NAV-3); the update page is a sub-destination of this navigation, opened from it
 * (NAV-4).
 *
 * The search (NAV-12): each content tab has the phone's `Search()` of its own toolbar row — the
 * standalone toolbar button right after the description (the phone's `search.ToolBarButton()` +
 * `search.SearchBar`, its `GeneralSettings.kt` 307-308) — its query preserved per tab across tab
 * switches (the phone keeps one `Search` per tab, its destination's state surviving the change), and
 * its [settingsSearchCtx] filter hiding the cards whose titles no longer match (the phone's
 * `searchCtx` pattern, its `AnimatedVisibility` fadeIn + scaleIn 600 ms). The About tab and the update
 * page have none (the phone has none there).
 *
 * The settings logic (state, dialogs, the `CacheSpaceIndicator`) is unchanged from the v1 overlay —
 * only its container moved into the tabs. Dropped (NAV-1, PC scope): 2 of the phone's 9 tabs — AI
 * recommendations, Accounts (`SettingsScreen.kt` 129-137; User Interface + Player appearance are now
 * ported, spec `spec-fill-pc-ui-gaps-settings`) — and the deep links to a setting; the phone's
 * settings search over its 9 tabs (here it is functional over the 6 ported ones). The tab switch
 * follows the phone's `transitionEffect` (`EffectHandler.kt` `transition()`, `ui.settings` since
 * 1.10.0).
 *
 * The Réseau tab's "PC server" entry (spec `spec-fill-pc-ui-gaps-settings` §A3, the phone's
 * `NetworkSettings.kt` 175 navigation to its `bridgeServer` route) opens the window's own Serveur PC
 * page through [onOpenServer] — the same transition as the header's "Serveur PC" button.
 */
@Composable
fun SettingsScreen(
    preferences: Preferences,
    cache: AudioCache?,
    settingsTab: Int,
    onTabChanged: (Int) -> Unit,
    onOpenUpdate: () -> Unit,
    onOpenServer: () -> Unit,
) {
    val bottomBarOffset = LocalBottomBarOffset.current
    // Fresh on every recomposition: the bar animates only on the tab index (its `updateTransition`),
    // and the `stringResource` labels need the composition scope
    val tabs = SETTINGS_TABS.map { (label, icon) -> NavigationTab(stringResource(label), icon) }

    // The per-tab search queries (NAV-12): the phone keeps one Search per tab, its query surviving a
    // tab change (its destinations stay alive in the back stack); here they live at the page level,
    // the tabs only reading and writing their own
    var searchQueries by remember { mutableStateOf<Map<Int, String>>(emptyMap()) }

    val tabEffect = app.n_zik.compagnon.bridge.state.LocalUiSettings.current.transition
    val tabStates = androidx.compose.runtime.saveable.rememberSaveableStateHolder()
    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = settingsTab,
            // The phone's tab switch (`EffectHandler.kt` `transition()`): its `transitionEffect` (`ui.settings`)
            transitionSpec = { tabTransition(tabEffect) },
            label = "settings tab",
            modifier = Modifier.fillMaxSize(),
        ) { tab ->
            // Each tab keeps its state (scroll) across switches, as the phone's back-stack destinations
            tabStates.SaveableStateProvider(key = tab) {
            when (tab) {
                0 -> GeneralSettingsScreen(preferences, searchQueries[0].orEmpty()) { searchQueries = searchQueries + (0 to it) }
                1 -> UiSettingsScreen(preferences, searchQueries[1].orEmpty()) { searchQueries = searchQueries + (1 to it) }
                2 -> AppearanceSettingsScreen(preferences, searchQueries[2].orEmpty()) { searchQueries = searchQueries + (2 to it) }
                3 -> DataSettingsScreen(preferences, cache, searchQueries[3].orEmpty()) { searchQueries = searchQueries + (3 to it) }
                4 -> NetworkSettingsScreen(preferences, searchQueries[4].orEmpty(), onOpenServer) { searchQueries = searchQueries + (4 to it) }
                5 -> OtherSettingsScreen(preferences, searchQueries[5].orEmpty()) { searchQueries = searchQueries + (5 to it) }
                6 -> AboutScreen(preferences, onOpenUpdate)
                else -> Unit
            }
            }
        }

        HorizontalNavigationBar(
            tabs = tabs,
            tabIndex = settingsTab,
            onTabChanged = onTabChanged,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .offset { IntOffset(0, bottomBarOffset.value.roundToInt()) },
        )
    }
}

/**
 * The 7 settings sub-tabs (NAV-1 + spec `spec-fill-pc-ui-gaps-settings`, the phone's label + icon
 * of each, in the phone's order): the 2 new phone tabs (User Interface / Player appearance) are
 * inserted after Général, as on the phone.
 */
internal val SETTINGS_TABS: List<Pair<StringResource, DrawableResource>> = listOf(
    Res.string.tab_general to Res.drawable.ic_launcher_monochrome,
    Res.string.ui_tab to Res.drawable.ui,
    Res.string.player_appearance to Res.drawable.color_palette,
    Res.string.tab_data to Res.drawable.server,
    Res.string.tab_network to Res.drawable.network,
    Res.string.tab_miscellaneous to Res.drawable.equalizer,
    Res.string.about to Res.drawable.information,
)

/**
 * The phone's `searchCtx` filter (NAV-12, its `GeneralSettings.kt` 311): a section is shown when the
 * query is blank or one of its titles (the section card's, its entries') contains it, case-insensitively.
 */
internal fun settingsSearchCtx(query: String, vararg titles: String): Boolean =
    query.isBlank() || titles.any { title -> title.contains(query, ignoreCase = true) }
