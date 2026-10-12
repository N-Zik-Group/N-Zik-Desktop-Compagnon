package app.n_zik.compagnon.components.ui.screens.settings

import app.n_zik.compagnon.colorPalette
import app.n_zik.compagnon.components.settings.OtherSettingsEntry
import app.n_zik.compagnon.components.settings.SettingsDescription
import app.n_zik.compagnon.components.settings.SettingsSectionCard
import app.n_zik.compagnon.components.settings.ToggleSettingsEntry
import app.n_zik.compagnon.components.tab.Search
import app.n_zik.compagnon.components.themed.HeaderWithIcon
import app.n_zik.compagnon.components.themed.ValueSelectorDialog
import app.n_zik.compagnon.enums.AlbumSwipeAction
import app.n_zik.compagnon.enums.HomeScreenTabs
import app.n_zik.compagnon.enums.NavigationBarPosition
import app.n_zik.compagnon.enums.NavigationBarType
import app.n_zik.compagnon.enums.PlaylistSwipeAction
import app.n_zik.compagnon.enums.QueueSwipeAction
import app.n_zik.compagnon.generated.resources.*
import app.n_zik.compagnon.utils.Preferences
import app.n_zik.compagnon.utils.Toaster
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource

/**
 * The PC's User-Interface tab visibility inputs — everything a live condition evaluates, so
 * [uiVisibleEntries] stays pure and testable (spec `spec-fill-pc-ui-gaps-settings` §B).
 *
 * @param isSwipeToActionEnabled the PC key `isSwipeToActionEnabled` — live
 */
data class UiVisibilityInput(
    val isSwipeToActionEnabled: Boolean,
)

/**
 * The visibility of the User-Interface tab's conditional entries: the 6 swipe-action sub-entries
 * (the phone's `UiSettings.kt` 1435-1556), shown only while "Swipe for action" is on (spec
 * `spec-fill-pc-ui-gaps-settings` §B — the condition is dynamic, never static).
 */
data class UiVisibility(
    val swipeSubEntries: Boolean,
)

/**
 * The pure visibility of the User-Interface tab's conditional entries, evaluated from
 * [UiVisibilityInput] only, so the screen re-evaluates it live on every setting change without any
 * side effect (tested in `UiVisibleEntriesTest.kt`).
 */
fun uiVisibleEntries(input: UiVisibilityInput): UiVisibility = UiVisibility(
    swipeSubEntries = input.isSwipeToActionEnabled,
)

/**
 * The User Interface tab (the phone's `UiSettings.kt` 680-1795 header / description / search, spec
 * `spec-fill-pc-ui-gaps-settings` §B): the tab-style header (the phone's `user_interface` label +
 * `ui_settings_description` + the tab's own `ui` icon), the description, the search, then the phone's
 * 8 section cards in the phone's order — **Theme** (UIS:748), **User interface** (UIS:1112), **Font
 * type** (UIS:1357), **Swipe for action** (UIS:1422), **Home page** (UIS:1576), **Sort Menu**
 * (UIS:1636), **Toolbar** (UIS:1703) and **Reset** (UIS:1758) — each with the phone's animation
 * duration.
 *
 * The phone entries without a PC key are **inert** (the PC's inert-entry convention): their normal
 * appearance, the phone's default value / state shown, no effect on a click — a long click toasts the
 * phone's description string (the entries whose phone row shows a value instead of a description have
 * no toast, as the phone has none for them). The 11 wired entries act on the PC's own settings:
 *
 * - "Navigation bar position" (UIS:1118, the PC key `navigationBarPosition`, 5-value
 *   `NavigationBarPosition` dialog) — MOVED from the PC's former "Others" card (the old Autres tab)
 * - "Navigation bar style" (UIS:1138, the PC key `navigationBarType`, 2-value `NavigationBarType`
 *   dialog) — MOVED, idem
 * - "Default page" (UIS:1218, the PC key `indexNavigationTab`) — MOVED, idem — PC constraint on the
 *   value list: `HomeScreenTabs.Default` + Songs / Artists / Albums / Playlists (the phone's Quick
 *   Picks and Search tabs are absent on the PC, so not offered)
 * - "Show floating search icon" (UIS:1323, the PC key `showFloatingIcon`, off by default, aligned to
 *   the phone)
 * - "Swipe for action" (UIS:1427, the PC key `isSwipeToActionEnabled`) — MOVED from the old Autres
 *   tab (G2 #62)
 * - its 6 sub-entries (UIS:1440 / 1460 / 1480 / 1500 / 1520 / 1540, the PC keys
 *   `queueSwipeLeftAction` / `playlistSwipeLeftAction` / `playlistSwipeRightAction` /
 *   `albumSwipeLeftAction` / `albumSwipeRightAction` / `queueSwipeRightAction`), each with the
 *   phone's value-selector dialog of its own enum — shown only while `isSwipeToActionEnabled` is on,
 *   evaluated **live** from the PC key (the condition is dynamic, never static)
 *
 * The phone entries whose condition is false on the phone's defaults, or Android-only (⛔), are not
 * rendered — a KDoc at the site names the phone entry (file:line) and the reason.
 *
 * This screen is the 2nd tab of [SettingsScreen]'s 7-tab bar (the 5 → 7 tab bar of the spec,
 * inserted after Général, the phone's order).
 */
@Composable
fun UiSettingsScreen(preferences: Preferences, query: String, onQuery: (String) -> Unit) {
    val settings by preferences.settings.collectAsState()
    var showNavPosDialog by remember { mutableStateOf(false) }
    var showNavTypeDialog by remember { mutableStateOf(false) }
    var showStartTabDialog by remember { mutableStateOf(false) }
    var showQSLeftDialog by remember { mutableStateOf(false) }
    var showPLLeftDialog by remember { mutableStateOf(false) }
    var showPLRightDialog by remember { mutableStateOf(false) }
    var showALeftDialog by remember { mutableStateOf(false) }
    var showARightDialog by remember { mutableStateOf(false) }
    var showQSRightDialog by remember { mutableStateOf(false) }
    val navigationBarPosition = settings.navigationBarPositionValue
    val navigationBarType = settings.navigationBarTypeValue
    val indexNavigationTab = settings.indexNavigationTabValue
    // The live condition, pure (spec §B, tested in UiVisibleEntriesTest)
    val uiVis = uiVisibleEntries(UiVisibilityInput(isSwipeToActionEnabled = settings.isSwipeToActionEnabled))
    val search = Search(query, onQuery, lazyListState = null)
    // The cards' search ctx (spec §B): the card's title + its rendered entries' titles
    val themeCtx = settingsSearchCtx(
        search.inputValue,
        stringResource(Res.string.theme),
        stringResource(Res.string.interface_in_use),
        stringResource(Res.string.theme_mode),
    )
    val userInterfaceCtx = settingsSearchCtx(
        search.inputValue,
        stringResource(Res.string.user_interface),
        stringResource(Res.string.navigation_bar_position),
        stringResource(Res.string.navigation_bar_type),
        stringResource(Res.string.player_position),
        stringResource(Res.string.menu_style),
        stringResource(Res.string.message_type),
        stringResource(Res.string.default_page),
        stringResource(Res.string.home_tabs_settings),
        stringResource(Res.string.transition_effect),
        stringResource(Res.string.disable_navigation_back_stack),
        stringResource(Res.string.search_display_order),
        stringResource(Res.string.show_floating_icon),
        stringResource(Res.string.remember_queue_scroll_position),
    )
    val fontCtx = settingsSearchCtx(
        search.inputValue,
        stringResource(Res.string.settings_use_font_type),
        stringResource(Res.string.use_system_font),
        stringResource(Res.string.apply_font_padding),
    )
    val swipeCtx = settingsSearchCtx(
        search.inputValue,
        stringResource(Res.string.swipe_to_action),
        stringResource(Res.string.queue_and_local_playlists_left_swipe),
        stringResource(Res.string.playlist_left_swipe),
        stringResource(Res.string.playlist_right_swipe),
        stringResource(Res.string.album_left_swipe),
        stringResource(Res.string.album_right_swipe),
        stringResource(Res.string.queue_and_local_playlists_right_swipe),
    )
    val homePageCtx = settingsSearchCtx(
        search.inputValue,
        stringResource(Res.string.home_tabs_settings),
        stringResource(Res.string.quick_picks),
        stringResource(Res.string.songs),
        stringResource(Res.string.artists),
        stringResource(Res.string.albums),
        stringResource(Res.string.playlists),
    )
    val sortMenuCtx = settingsSearchCtx(
        search.inputValue,
        stringResource(Res.string.sort_menu),
        stringResource(Res.string.songs),
        stringResource(Res.string.artists),
        stringResource(Res.string.albums),
        stringResource(Res.string.playlists),
        stringResource(Res.string.local_playlist_sort),
        stringResource(Res.string.history),
    )
    val toolbarCtx = settingsSearchCtx(
        search.inputValue,
        stringResource(Res.string.toolbar),
        stringResource(Res.string.songs),
        stringResource(Res.string.artists),
        stringResource(Res.string.albums),
        stringResource(Res.string.playlists),
        stringResource(Res.string.local_playlist_sort),
    )
    val settingsResetCtx = settingsSearchCtx(search.inputValue, stringResource(Res.string.settings_reset))

    Column(
        modifier = Modifier
            .background(colorPalette().background0)
            .fillMaxHeight()
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    ) {
        HeaderWithIcon(
            title = stringResource(Res.string.user_interface),
            iconId = Res.drawable.ui,
            enabled = false,
            showIcon = true,
            modifier = Modifier,
            onClick = {},
        )

        SettingsDescription(
            text = stringResource(Res.string.ui_settings_description),
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )

        // Search Section
        search.ToolBarButton()
        search.SearchBar()

        // Theme Section (the phone's UIS:748, spec §B)
        AnimatedVisibility(
            visible = themeCtx,
            enter = fadeIn(animationSpec = tween(600)) + scaleIn(animationSpec = tween(600), initialScale = 0.9f),
        ) {
            SettingsSectionCard(
                title = stringResource(Res.string.theme),
                icon = Res.drawable.color_palette,
                content = {
                    // "Interface in use" (UIS:754, the phone's `UiTypeKey`, RiMusic) — INERT: the PC is
                    // always the RiMusic interface (there is no `UiType` on the PC), so the phone's
                    // default is shown as a static value, no dialog, a click has no effect; the phone's
                    // row shows a value, not a description → no toast
                    OtherSettingsEntry(
                        title = stringResource(Res.string.interface_in_use),
                        text = "RiMusic",
                        icon = Res.drawable.ui,
                        onClick = {},
                    )

                    // "Theme" (UIS:840, the phone's `colorPaletteNameKey`, Dynamic) — INERT: the PC's
                    // color palettes are coded (the phone's palette selector has no PC equivalent), so
                    // the phone's default is shown, no dialog, a click has no effect; no description →
                    // no toast
                    OtherSettingsEntry(
                        title = stringResource(Res.string.theme),
                        text = stringResource(Res.string.dynamic),
                        icon = Res.drawable.color_palette,
                        onClick = {},
                    )

                    /**
                     * NOT RENDERED (static, spec §B): the phone's "Custom colors" block, 23 rows, all
                     * only shown when `colorPaletteName` is `CustomColor` or `Customized` (both false on
                     * the phone's default Dynamic, and with no PC key — the condition is statically
                     * false on the PC; the color pickers are not ported): "Custom Color" (UIS:869,
                     * `customColorKey`), "Reset customized light colors" (UIS:885), Background 1-5
                     * Light (UIS:894 / 902 / 910 / 918 / 926) + Text / Text secondary / Text disabled /
                     * Player button / Color accent Light (UIS:934 / 942 / 950 / 958 / 966, the phone's
                     * `customThemeLight_*Key`), "Reset customized dark colors" (UIS:976), Background
                     * 1-5 Dark (UIS:985 / 993 / 1001 / 1009 / 1017) + Text / Text secondary / Text
                     * disabled / Player button / Color accent Dark (UIS:1025 / 1033 / 1041 / 1049 /
                     * 1057, the phone's `customThemeDark_*Key`).
                     */

                    // "Theme mode" (UIS:1075, the phone's `colorPaletteModeKey`, Dark) — INERT: the
                    // phone's condition (`themeModeEnabled`, true with the default Dynamic ≠
                    // PureBlack / ModernBlack) is met, so the entry is rendered; the PC's palettes are
                    // coded, so the phone's default is shown, no dialog, a click has no effect; no
                    // description → no toast
                    OtherSettingsEntry(
                        title = stringResource(Res.string.theme_mode),
                        text = stringResource(Res.string.dark),
                        icon = Res.drawable.sparkles,
                        onClick = {},
                    )
                },
            )
        }

        // User interface Section (the phone's UIS:1112, spec §B)
        AnimatedVisibility(
            visible = userInterfaceCtx,
            enter = fadeIn(animationSpec = tween(700)) + scaleIn(animationSpec = tween(700), initialScale = 0.9f),
        ) {
            SettingsSectionCard(
                title = stringResource(Res.string.user_interface),
                icon = Res.drawable.ui,
                content = {
                    // "Navigation bar position" (UIS:1118) — WIRED to the PC's `navigationBarPosition`
                    // (the phone's `NavigationBarPosition`, 5 values): MOVED from the PC's former
                    // "Others" card (the old Autres tab), re-ported with its value-selector dialog
                    OtherSettingsEntry(
                        title = stringResource(Res.string.navigation_bar_position),
                        text = stringResource(navigationBarPosition.textId),
                        icon = Res.drawable.locate,
                        onClick = { showNavPosDialog = true },
                    )

                    // "Navigation bar style" (UIS:1138) — WIRED to the PC's `navigationBarType` (the
                    // phone's `NavigationBarType`, 2 values): MOVED from the old Autres tab, idem
                    OtherSettingsEntry(
                        title = stringResource(Res.string.navigation_bar_type),
                        text = stringResource(navigationBarType.textId),
                        icon = Res.drawable.burger,
                        onClick = { showNavTypeDialog = true },
                    )

                    // "Player position" (UIS:1158, the phone's `playerPositionKey`, Bottom) — INERT:
                    // the phone's default (Bottom) shown, no dialog, a click has no effect; no
                    // description → no toast
                    OtherSettingsEntry(
                        title = stringResource(Res.string.player_position),
                        text = stringResource(Res.string.position_bottom),
                        icon = Res.drawable.position,
                        onClick = {},
                    )

                    // "Menu style" (UIS:1178, the phone's `menuStyleKey`, List) — INERT: the phone's
                    // default (List) shown, no dialog, a click has no effect; no description → no toast
                    OtherSettingsEntry(
                        title = stringResource(Res.string.menu_style),
                        text = stringResource(Res.string.style_list),
                        icon = Res.drawable.reorder,
                        onClick = {},
                    )

                    // "Popup message style" (UIS:1198, the phone's `messageTypeKey`, Modern) — INERT:
                    // the phone's default (Modern) shown, no dialog, a click has no effect; no
                    // description → no toast
                    OtherSettingsEntry(
                        title = stringResource(Res.string.message_type),
                        text = stringResource(Res.string.message_type_modern),
                        icon = Res.drawable.information,
                        onClick = {},
                    )

                    // "Default page" (UIS:1218) — WIRED to the PC's `indexNavigationTab`: MOVED from
                    // the old Autres tab, re-ported with its value-selector dialog — PC constraint on
                    // the value list: `HomeScreenTabs.Default` + the four visible tabs (the phone's
                    // Quick Picks and Search tabs are absent on the PC, so not offered)
                    OtherSettingsEntry(
                        title = stringResource(Res.string.default_page),
                        text = stringResource(indexNavigationTab.textId),
                        icon = Res.drawable.star_brilliant,
                        onClick = { showStartTabDialog = true },
                    )

                    // "Home page" (UIS:1237, the phone's `HomeTabsSettingsDialog`) — INERT: the phone's
                    // dialog is 100 % phone-side (it reorders / shows / hides the phone's home tabs); on
                    // the PC a click has no effect, a long click toasts the phone's description
                    OtherSettingsEntry(
                        title = stringResource(Res.string.home_tabs_settings),
                        text = stringResource(Res.string.home_tabs_settings_description),
                        icon = Res.drawable.sparkles,
                        onClick = {},
                        onLongClick = { Toaster.i(Res.string.home_tabs_settings_description) },
                    )

                    // "Transition effect" (UIS:1247, the phone's `transitionEffectKey`, Fade) — INERT:
                    // the phone's default (Fade) shown (it equals the PC's coded default —
                    // `spec-remove-ui-sync`: the phone's appearance never reaches the PC's UI, so no
                    // live read of the contract), no dialog, a click has no effect; no description →
                    // no toast
                    OtherSettingsEntry(
                        title = stringResource(Res.string.transition_effect),
                        text = stringResource(Res.string.te_fade),
                        icon = Res.drawable.images_sharp,
                        onClick = {},
                    )

                    // "Go back directly to home" (UIS:1266, the phone's `disableNavigationBackStackKey`,
                    // off by default) — INERT: the default switch state, a click has no effect, a long
                    // click toasts the phone's description
                    ToggleSettingsEntry(
                        title = stringResource(Res.string.disable_navigation_back_stack),
                        text = stringResource(Res.string.disable_navigation_back_stack_description),
                        icon = Res.drawable.chevron_back,
                        isChecked = false,
                        enabled = false,
                        onCheckedChange = {},
                        onLongClick = { Toaster.i(Res.string.disable_navigation_back_stack_description) },
                    )

                    // "Online search display order" (UIS:1277, the phone's `searchDisplayOrderKey`,
                    // SuggestionsFirst) — INERT: the phone's default (Suggestions first) shown, no
                    // dialog, a click has no effect; the phone's row shows a value, not a description →
                    // no toast
                    OtherSettingsEntry(
                        title = stringResource(Res.string.search_display_order),
                        text = stringResource(Res.string.search_display_order_suggestions_first),
                        icon = Res.drawable.search,
                        onClick = {},
                    )

                    /**
                     * NOT RENDERED ⛔ (spec §B): the phone's "Show the search button in the navigation
                     * bar" (UIS:1302, `showSearchTab`, off) and "Show statistics in navigation bar"
                     * (UIS:1311, `showStatsInNavbar`, off) — both only shown when
                     * `UiType.ViMusic.isCurrent()`; the PC is always the RiMusic interface, so the
                     * condition is never true on the PC.
                     */

                    // "Show floating search icon" (UIS:1323) — WIRED to the PC's `showFloatingIcon`
                    // (off by default, aligned to the phone): MOVED from the old Autres tab; the phone's
                    // row shows no description → no toast
                    ToggleSettingsEntry(
                        title = stringResource(Res.string.show_floating_icon),
                        text = "",
                        icon = Res.drawable.maximize,
                        isChecked = settings.showFloatingIcon,
                        onCheckedChange = {
                            preferences.update { s -> s.copy(showFloatingIcon = it) }
                        },
                    )

                    // "Remember queue scroll position" (UIS:1334, the phone's
                    // `rememberQueueScrollPositionKey`, on by default) — INERT: the default switch
                    // state, a click has no effect, a long click toasts the phone's description
                    ToggleSettingsEntry(
                        title = stringResource(Res.string.remember_queue_scroll_position),
                        text = stringResource(Res.string.remember_queue_scroll_position_description),
                        icon = Res.drawable.reorder,
                        isChecked = true,
                        enabled = false,
                        onCheckedChange = {},
                        onLongClick = { Toaster.i(Res.string.remember_queue_scroll_position_description) },
                    )
                },
            )
        }

        // Font type Section (the phone's UIS:1357, spec §B)
        AnimatedVisibility(
            visible = fontCtx,
            enter = fadeIn(animationSpec = tween(800)) + scaleIn(animationSpec = tween(800), initialScale = 0.9f),
        ) {
            SettingsSectionCard(
                title = stringResource(Res.string.settings_use_font_type),
                icon = Res.drawable.text,
                content = {
                    // "Font type" (UIS:1363, the phone's `fontTypeKey`, Rubik) — INERT: the phone's
                    // default (the phone shows the enum name) shown, no dialog, a click has no effect;
                    // no description → no toast
                    OtherSettingsEntry(
                        title = stringResource(Res.string.settings_use_font_type),
                        text = "Rubik",
                        icon = Res.drawable.text,
                        onClick = {},
                    )

                    // "Use system font" (UIS:1382, the phone's `useSystemFont`, off by default) —
                    // INERT: the default switch state, a click has no effect, a long click toasts the
                    // phone's description
                    ToggleSettingsEntry(
                        title = stringResource(Res.string.use_system_font),
                        text = stringResource(Res.string.use_font_by_the_system),
                        icon = Res.drawable.settings,
                        isChecked = false,
                        enabled = false,
                        onCheckedChange = {},
                        onLongClick = { Toaster.i(Res.string.use_font_by_the_system) },
                    )

                    // "Apply font padding" (UIS:1392, the phone's `applyFontPadding`, off by default) —
                    // INERT: the default switch state, a click has no effect, a long click toasts the
                    // phone's description
                    ToggleSettingsEntry(
                        title = stringResource(Res.string.apply_font_padding),
                        text = stringResource(Res.string.add_spacing_around_texts),
                        icon = Res.drawable.resize,
                        isChecked = false,
                        enabled = false,
                        onCheckedChange = {},
                        onLongClick = { Toaster.i(Res.string.add_spacing_around_texts) },
                    )

                    /**
                     * NOT RENDERED ⛔ (spec §B): the phone's "Hide status bar" (UIS:1402,
                     * `hideStatusBar`, off) — the Android status bar; there is no equivalent on the PC.
                     */
                },
            )
        }

        // Swipe for action Section (the phone's UIS:1422, spec §B)
        AnimatedVisibility(
            visible = swipeCtx,
            enter = fadeIn(animationSpec = tween(900)) + scaleIn(animationSpec = tween(900), initialScale = 0.9f),
        ) {
            SettingsSectionCard(
                title = stringResource(Res.string.swipe_to_action),
                icon = Res.drawable.arrow_forward,
                content = {
                    // "Swipe for action" (UIS:1427) — WIRED to the PC's `isSwipeToActionEnabled`:
                    // MOVED from the old Autres tab (G2 #62), re-ported as its own section with its
                    // sub-entries
                    ToggleSettingsEntry(
                        title = stringResource(Res.string.swipe_to_action),
                        text = stringResource(Res.string.activate_the_action_menu_by_swiping_the_song_left_or_right),
                        icon = Res.drawable.arrow_forward,
                        isChecked = settings.isSwipeToActionEnabled,
                        onCheckedChange = {
                            preferences.update { s -> s.copy(isSwipeToActionEnabled = it) }
                        },
                    )

                    // The phone's 6 sub-entries (UIS:1435-1556), each WIRED to its own PC key, with the
                    // phone's value-selector dialog of its enum — shown only while
                    // `isSwipeToActionEnabled` is on, evaluated LIVE from the PC key (the condition is
                    // dynamic, never static): a toggle flip makes them appear / disappear in place
                    AnimatedVisibility(visible = uiVis.swipeSubEntries) {
                        Column(modifier = Modifier.padding(start = 25.dp)) {
                            // "Queue and Local Playlists Left Swipe" (UIS:1440, the PC key
                            // `queueSwipeLeftAction`, 6 values)
                            OtherSettingsEntry(
                                title = stringResource(Res.string.queue_and_local_playlists_left_swipe),
                                text = stringResource(settings.queueSwipeLeftActionValue.textId),
                                icon = Res.drawable.arrow_left,
                                onClick = { showQSLeftDialog = true },
                            )

                            // "Playlist Left Swipe" (UIS:1460, the PC key `playlistSwipeLeftAction`, 5
                            // values)
                            OtherSettingsEntry(
                                title = stringResource(Res.string.playlist_left_swipe),
                                text = stringResource(settings.playlistSwipeLeftActionValue.textId),
                                icon = Res.drawable.arrow_left,
                                onClick = { showPLLeftDialog = true },
                            )

                            // "Playlist Right Swipe" (UIS:1480, the PC key `playlistSwipeRightAction`, 5
                            // values)
                            OtherSettingsEntry(
                                title = stringResource(Res.string.playlist_right_swipe),
                                text = stringResource(settings.playlistSwipeRightActionValue.textId),
                                icon = Res.drawable.arrow_right,
                                onClick = { showPLRightDialog = true },
                            )

                            // "Album Left Swipe" (UIS:1500, the PC key `albumSwipeLeftAction`, 4 values)
                            OtherSettingsEntry(
                                title = stringResource(Res.string.album_left_swipe),
                                text = stringResource(settings.albumSwipeLeftActionValue.textId),
                                icon = Res.drawable.arrow_left,
                                onClick = { showALeftDialog = true },
                            )

                            // "Album Right Swipe" (UIS:1520, the PC key `albumSwipeRightAction`, 4
                            // values)
                            OtherSettingsEntry(
                                title = stringResource(Res.string.album_right_swipe),
                                text = stringResource(settings.albumSwipeRightActionValue.textId),
                                icon = Res.drawable.arrow_right,
                                onClick = { showARightDialog = true },
                            )

                            // "Queue and Local Playlists Right Swipe" (UIS:1540, the PC key
                            // `queueSwipeRightAction`, 6 values)
                            OtherSettingsEntry(
                                title = stringResource(Res.string.queue_and_local_playlists_right_swipe),
                                text = stringResource(settings.queueSwipeRightActionValue.textId),
                                icon = Res.drawable.arrow_right,
                                onClick = { showQSRightDialog = true },
                            )
                        }
                    }
                },
            )
        }

        // Home page Section (the phone's UIS:1576, spec §B)
        AnimatedVisibility(
            visible = homePageCtx,
            enter = fadeIn(animationSpec = tween(1000)) + scaleIn(animationSpec = tween(1000), initialScale = 0.9f),
        ) {
            SettingsSectionCard(
                title = stringResource(Res.string.home_tabs_settings),
                icon = Res.drawable.library,
                content = {
                    // The phone's 5 library-visibility dialogs (UIS:1581 / 1589 / 1597 / 1605 / 1613,
                    // the `QuickPicksContentSettingsDialog` / `HomeSongsSettingsDialog` /
                    // `HomeArtistsSettingsDialog` / `HomeAlbumsSettingsDialog` /
                    // `HomePlaylistsSettingsDialog`) — all 100 % phone-side: INERT, a click has no
                    // effect, a long click toasts the phone's description
                    OtherSettingsEntry(
                        title = stringResource(Res.string.quick_picks),
                        text = stringResource(Res.string.library_visibility_description),
                        icon = Res.drawable.sparkles,
                        onClick = {},
                        onLongClick = { Toaster.i(Res.string.library_visibility_description) },
                    )
                    OtherSettingsEntry(
                        title = stringResource(Res.string.songs),
                        text = stringResource(Res.string.library_visibility_description),
                        icon = Res.drawable.musical_notes,
                        onClick = {},
                        onLongClick = { Toaster.i(Res.string.library_visibility_description) },
                    )
                    OtherSettingsEntry(
                        title = stringResource(Res.string.artists),
                        text = stringResource(Res.string.library_visibility_description),
                        icon = Res.drawable.people,
                        onClick = {},
                        onLongClick = { Toaster.i(Res.string.library_visibility_description) },
                    )
                    OtherSettingsEntry(
                        title = stringResource(Res.string.albums),
                        text = stringResource(Res.string.library_visibility_description),
                        icon = Res.drawable.album,
                        onClick = {},
                        onLongClick = { Toaster.i(Res.string.library_visibility_description) },
                    )
                    OtherSettingsEntry(
                        title = stringResource(Res.string.playlists),
                        text = stringResource(Res.string.library_visibility_description),
                        icon = Res.drawable.library,
                        onClick = {},
                        onLongClick = { Toaster.i(Res.string.library_visibility_description) },
                    )
                },
            )
        }

        // Sort Menu Section (the phone's UIS:1636, spec §B): the phone's search quirk (the literal
        // "sort" in its ctx, UIS:1624) is NOT ported — the ctx is computed from the entries' labels,
        // as on the PC
        AnimatedVisibility(
            visible = sortMenuCtx,
            enter = fadeIn(animationSpec = tween(1000)) + scaleIn(animationSpec = tween(1000), initialScale = 0.9f),
        ) {
            SettingsSectionCard(
                title = stringResource(Res.string.sort_menu),
                icon = Res.drawable.arrow_up,
                content = {
                    // The phone's 6 sort dialogs (UIS:1641 / 1649 / 1657 / 1665 / 1673 / 1681, the
                    // `HomeSongsSortSettingsDialog` / `HomeArtistsSortSettingsDialog` /
                    // `HomeAlbumsSortSettingsDialog` / `HomeLibrarySortSettingsDialog` /
                    // `LocalPlaylistSortSettingsDialog` / `HistorySortSettingsDialog`) — all 100 %
                    // phone-side: INERT, a click has no effect, a long click toasts the phone's
                    // description
                    OtherSettingsEntry(
                        title = stringResource(Res.string.songs),
                        text = stringResource(Res.string.customize_sort_options),
                        icon = Res.drawable.musical_notes,
                        onClick = {},
                        onLongClick = { Toaster.i(Res.string.customize_sort_options) },
                    )
                    OtherSettingsEntry(
                        title = stringResource(Res.string.artists),
                        text = stringResource(Res.string.customize_sort_options),
                        icon = Res.drawable.people,
                        onClick = {},
                        onLongClick = { Toaster.i(Res.string.customize_sort_options) },
                    )
                    OtherSettingsEntry(
                        title = stringResource(Res.string.albums),
                        text = stringResource(Res.string.customize_sort_options),
                        icon = Res.drawable.album,
                        onClick = {},
                        onLongClick = { Toaster.i(Res.string.customize_sort_options) },
                    )
                    OtherSettingsEntry(
                        title = stringResource(Res.string.playlists),
                        text = stringResource(Res.string.customize_sort_options),
                        icon = Res.drawable.library,
                        onClick = {},
                        onLongClick = { Toaster.i(Res.string.customize_sort_options) },
                    )
                    OtherSettingsEntry(
                        title = stringResource(Res.string.local_playlist_sort),
                        text = stringResource(Res.string.customize_sort_options),
                        icon = Res.drawable.playlist,
                        onClick = {},
                        onLongClick = { Toaster.i(Res.string.customize_sort_options) },
                    )
                    OtherSettingsEntry(
                        title = stringResource(Res.string.history),
                        text = stringResource(Res.string.customize_sort_options),
                        icon = Res.drawable.time,
                        onClick = {},
                        onLongClick = { Toaster.i(Res.string.customize_sort_options) },
                    )
                },
            )
        }

        // Toolbar Section (the phone's UIS:1703, spec §B)
        AnimatedVisibility(
            visible = toolbarCtx,
            enter = fadeIn(animationSpec = tween(1000)) + scaleIn(animationSpec = tween(1000), initialScale = 0.9f),
        ) {
            SettingsSectionCard(
                title = stringResource(Res.string.toolbar),
                icon = Res.drawable.ellipsis_horizontal,
                content = {
                    // The phone's 5 toolbar dialogs (UIS:1708 / 1716 / 1724 / 1732 / 1740, the
                    // `HomeSongsToolbarSettingsDialog` / `HomeArtistsToolbarSettingsDialog` /
                    // `HomeAlbumsToolbarSettingsDialog` / `HomeLibraryToolbarSettingsDialog` /
                    // `LocalPlaylistToolbarSettingsDialog`) — all 100 % phone-side: INERT, a click has
                    // no effect, a long click toasts the phone's description
                    OtherSettingsEntry(
                        title = stringResource(Res.string.songs),
                        text = stringResource(Res.string.customize_toolbar),
                        icon = Res.drawable.musical_notes,
                        onClick = {},
                        onLongClick = { Toaster.i(Res.string.customize_toolbar) },
                    )
                    OtherSettingsEntry(
                        title = stringResource(Res.string.artists),
                        text = stringResource(Res.string.customize_toolbar),
                        icon = Res.drawable.people,
                        onClick = {},
                        onLongClick = { Toaster.i(Res.string.customize_toolbar) },
                    )
                    OtherSettingsEntry(
                        title = stringResource(Res.string.albums),
                        text = stringResource(Res.string.customize_toolbar),
                        icon = Res.drawable.album,
                        onClick = {},
                        onLongClick = { Toaster.i(Res.string.customize_toolbar) },
                    )
                    OtherSettingsEntry(
                        title = stringResource(Res.string.playlists),
                        text = stringResource(Res.string.customize_toolbar),
                        icon = Res.drawable.library,
                        onClick = {},
                        onLongClick = { Toaster.i(Res.string.customize_toolbar) },
                    )
                    OtherSettingsEntry(
                        title = stringResource(Res.string.local_playlist_sort),
                        text = stringResource(Res.string.customize_toolbar),
                        icon = Res.drawable.playlist,
                        onClick = {},
                        onLongClick = { Toaster.i(Res.string.customize_toolbar) },
                    )
                },
            )
        }

        // Reset Section (the phone's UIS:1758, spec §B)
        AnimatedVisibility(
            visible = settingsResetCtx,
            enter = fadeIn(animationSpec = tween(1100)) + scaleIn(animationSpec = tween(1100), initialScale = 0.9f),
        ) {
            SettingsSectionCard(
                title = stringResource(Res.string.settings_reset),
                icon = Res.drawable.refresh,
                content = {
                    // "Reset" (UIS:1765) — INERT: the phone resets ITS ~50 UI settings here (its
                    // `DefaultUiSettings` + its per-tab dialog resets); on the PC a click has no effect
                    // (the PC keeps its own settings), a long click toasts the phone's description
                    OtherSettingsEntry(
                        title = stringResource(Res.string.settings_reset),
                        text = stringResource(Res.string.settings_restore_default_settings),
                        icon = Res.drawable.refresh,
                        onClick = {},
                        onLongClick = { Toaster.i(Res.string.settings_restore_default_settings) },
                    )
                },
            )
        }

        // ===== The value-selector dialogs of the wired entries (the phone's dialogs, ported) =====

        if (showNavPosDialog) {
            ValueSelectorDialog(
                title = stringResource(Res.string.navigation_bar_position),
                values = NavigationBarPosition.entries.toList(),
                selectedValue = navigationBarPosition,
                onValueSelected = {
                    preferences.update { s -> s.copy(navigationBarPosition = it.name) }
                    showNavPosDialog = false
                },
                onDismiss = { showNavPosDialog = false },
                valueText = { stringResource(it.textId) },
            )
        }

        if (showNavTypeDialog) {
            ValueSelectorDialog(
                title = stringResource(Res.string.navigation_bar_type),
                values = NavigationBarType.entries.toList(),
                selectedValue = navigationBarType,
                onValueSelected = {
                    preferences.update { s -> s.copy(navigationBarType = it.name) }
                    showNavTypeDialog = false
                },
                onDismiss = { showNavTypeDialog = false },
                valueText = { stringResource(it.textId) },
            )
        }

        if (showStartTabDialog) {
            ValueSelectorDialog(
                title = stringResource(Res.string.default_page),
                // Default plus the four visible tabs (the phone's Quick Picks and Search tabs are
                // absent on the PC, so not offered — spec `spec-fill-pc-ui-gaps-settings` §B, the
                // constraint of the old Autres tab's dialog)
                values = listOf(HomeScreenTabs.Default, HomeScreenTabs.Songs, HomeScreenTabs.Artists, HomeScreenTabs.Albums, HomeScreenTabs.Playlists),
                selectedValue = indexNavigationTab,
                onValueSelected = {
                    preferences.update { s -> s.copy(indexNavigationTab = it.name) }
                    showStartTabDialog = false
                },
                onDismiss = { showStartTabDialog = false },
                valueText = { stringResource(it.textId) },
            )
        }

        if (showQSLeftDialog) {
            ValueSelectorDialog(
                title = stringResource(Res.string.queue_and_local_playlists_left_swipe),
                values = QueueSwipeAction.entries.toList(),
                selectedValue = settings.queueSwipeLeftActionValue,
                onValueSelected = {
                    preferences.update { s -> s.copy(queueSwipeLeftAction = it.name) }
                    showQSLeftDialog = false
                },
                onDismiss = { showQSLeftDialog = false },
                valueText = { stringResource(it.textId) },
            )
        }

        if (showPLLeftDialog) {
            ValueSelectorDialog(
                title = stringResource(Res.string.playlist_left_swipe),
                values = PlaylistSwipeAction.entries.toList(),
                selectedValue = settings.playlistSwipeLeftActionValue,
                onValueSelected = {
                    preferences.update { s -> s.copy(playlistSwipeLeftAction = it.name) }
                    showPLLeftDialog = false
                },
                onDismiss = { showPLLeftDialog = false },
                valueText = { stringResource(it.textId) },
            )
        }

        if (showPLRightDialog) {
            ValueSelectorDialog(
                title = stringResource(Res.string.playlist_right_swipe),
                values = PlaylistSwipeAction.entries.toList(),
                selectedValue = settings.playlistSwipeRightActionValue,
                onValueSelected = {
                    preferences.update { s -> s.copy(playlistSwipeRightAction = it.name) }
                    showPLRightDialog = false
                },
                onDismiss = { showPLRightDialog = false },
                valueText = { stringResource(it.textId) },
            )
        }

        if (showALeftDialog) {
            ValueSelectorDialog(
                title = stringResource(Res.string.album_left_swipe),
                values = AlbumSwipeAction.entries.toList(),
                selectedValue = settings.albumSwipeLeftActionValue,
                onValueSelected = {
                    preferences.update { s -> s.copy(albumSwipeLeftAction = it.name) }
                    showALeftDialog = false
                },
                onDismiss = { showALeftDialog = false },
                valueText = { stringResource(it.textId) },
            )
        }

        if (showARightDialog) {
            ValueSelectorDialog(
                title = stringResource(Res.string.album_right_swipe),
                values = AlbumSwipeAction.entries.toList(),
                selectedValue = settings.albumSwipeRightActionValue,
                onValueSelected = {
                    preferences.update { s -> s.copy(albumSwipeRightAction = it.name) }
                    showARightDialog = false
                },
                onDismiss = { showARightDialog = false },
                valueText = { stringResource(it.textId) },
            )
        }

        if (showQSRightDialog) {
            ValueSelectorDialog(
                title = stringResource(Res.string.queue_and_local_playlists_right_swipe),
                values = QueueSwipeAction.entries.toList(),
                selectedValue = settings.queueSwipeRightActionValue,
                onValueSelected = {
                    preferences.update { s -> s.copy(queueSwipeRightAction = it.name) }
                    showQSRightDialog = false
                },
                onDismiss = { showQSRightDialog = false },
                valueText = { stringResource(it.textId) },
            )
        }
    }
}
