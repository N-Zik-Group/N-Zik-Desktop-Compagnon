package app.n_zik.compagnon.components.ui.screens.settings

import app.n_zik.compagnon.colorPalette
import app.n_zik.compagnon.components.settings.OtherSettingsEntry
import app.n_zik.compagnon.components.settings.SettingsDescription
import app.n_zik.compagnon.components.settings.SettingsSectionCard
import app.n_zik.compagnon.components.settings.ToggleSettingsEntry
import app.n_zik.compagnon.components.tab.Search
import app.n_zik.compagnon.components.themed.HeaderWithIcon
import app.n_zik.compagnon.components.themed.ValueSelectorDialog
import app.n_zik.compagnon.enums.HomeScreenTabs
import app.n_zik.compagnon.enums.NavigationBarPosition
import app.n_zik.compagnon.enums.NavigationBarType
import app.n_zik.compagnon.generated.resources.*
import app.n_zik.compagnon.utils.Preferences
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
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
import org.jetbrains.compose.resources.stringResource

/**
 * The Autres tab (phone's `OtherSettings.kt` 215-237 + the PC's own entry): the tab-style header,
 * the description, the search, and the **Others** card — "Disable scrolling text" (the Compagnon's
 * own setting that drops the marquees), the home's navigation bar position and style (spec
 * `spec-fill-pc-ui-gaps-home` #45, the phone's `UiSettings.kt` 1115-1153 entries, unchanged except
 * the icons), the start tab (#46, the phone's 1215-1233 entry — the PC offers [HomeScreenTabs.Default]
 * plus the four visible tabs, the phone's Quick Picks and Search tabs being absent), and the swipe
 * actions toggle (#62, the phone's 1422-1430 switch, on the existing `isSwipeToActionEnabled` key).
 * The phone's "Disable scrolling text" sits in its Player Appearance tab (not ported) — the user
 * placed it here; after the "remove UI sync" spec it stays a PC-only setting, never driven by the phone.
 */
@Composable
fun OtherSettingsScreen(preferences: Preferences, query: String, onQuery: (String) -> Unit) {
    val settings by preferences.settings.collectAsState()
    var showNavPosDialog by remember { mutableStateOf(false) }
    var showNavTypeDialog by remember { mutableStateOf(false) }
    var showStartTabDialog by remember { mutableStateOf(false) }
    val search = Search(query, onQuery, lazyListState = null)
    val othersCtx = settingsSearchCtx(
        search.inputValue,
        stringResource(Res.string.other),
        stringResource(Res.string.disable_scrolling_text),
        stringResource(Res.string.navigation_bar_position),
        stringResource(Res.string.navigation_bar_type),
        stringResource(Res.string.default_page),
        stringResource(Res.string.swipe_to_action),
    )

    val navigationBarPosition = settings.navigationBarPositionValue
    val navigationBarType = settings.navigationBarTypeValue
    val indexNavigationTab = settings.indexNavigationTabValue

    Column(
        modifier = Modifier
            .background(colorPalette().background0)
            .fillMaxHeight()
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    ) {
        HeaderWithIcon(
            title = stringResource(Res.string.tab_miscellaneous),
            iconId = Res.drawable.equalizer,
            enabled = false,
            showIcon = true,
            modifier = Modifier,
            onClick = {},
        )

        SettingsDescription(
            text = stringResource(Res.string.other_settings_description),
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )

        // Search Section
        search.ToolBarButton()
        search.SearchBar()

        // Others Section (the PC's "Disable scrolling text"): the phone has its own under Player
        // appearance › player_behavior_and_visuals (`AppearanceSettings.kt` 2129-2134), a tab the PC does
        // not port — the user placed it here; it stays a PC-only setting (no longer driven by the phone).
        // The home's bar position / style, the start tab and the swipe actions toggle join the card
        // (spec `spec-fill-pc-ui-gaps-home` #45, #46, #62)
        AnimatedVisibility(
            visible = othersCtx,
            enter = fadeIn(animationSpec = tween(600)) + scaleIn(animationSpec = tween(600), initialScale = 0.9f),
        ) {
            SettingsSectionCard(
                title = stringResource(Res.string.other),
                icon = Res.drawable.text,
                content = {
                    ToggleSettingsEntry(
                        title = stringResource(Res.string.disable_scrolling_text),
                        text = stringResource(Res.string.scrolling_text_is_used_for_long_texts),
                        icon = Res.drawable.text,
                        isChecked = settings.disableScrollingText,
                        onCheckedChange = { enable ->
                            preferences.update { s -> s.copy(disableScrollingText = enable) }
                        },
                    )
                    OtherSettingsEntry(
                        title = stringResource(Res.string.navigation_bar_position),
                        text = stringResource(navigationBarPosition.textId),
                        icon = Res.drawable.locate,
                        onClick = { showNavPosDialog = true },
                    )
                    OtherSettingsEntry(
                        title = stringResource(Res.string.navigation_bar_type),
                        text = stringResource(navigationBarType.textId),
                        icon = Res.drawable.burger,
                        onClick = { showNavTypeDialog = true },
                    )
                    OtherSettingsEntry(
                        title = stringResource(Res.string.default_page),
                        text = stringResource(indexNavigationTab.textId),
                        icon = Res.drawable.star_brilliant,
                        onClick = { showStartTabDialog = true },
                    )
                    ToggleSettingsEntry(
                        title = stringResource(Res.string.swipe_to_action),
                        text = stringResource(Res.string.activate_the_action_menu_by_swiping_the_song_left_or_right),
                        icon = Res.drawable.arrow_forward,
                        isChecked = settings.isSwipeToActionEnabled,
                        onCheckedChange = { enable ->
                            preferences.update { s -> s.copy(isSwipeToActionEnabled = enable) }
                        },
                    )
                },
            )
        }

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
                // absent on the PC, so not offered — spec `spec-fill-pc-ui-gaps-home` #46)
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
    }
}
