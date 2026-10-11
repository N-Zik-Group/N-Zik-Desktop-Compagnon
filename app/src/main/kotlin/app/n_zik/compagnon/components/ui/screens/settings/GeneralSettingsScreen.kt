package app.n_zik.compagnon.components.ui.screens.settings

import app.n_zik.compagnon.colorPalette
import app.n_zik.compagnon.components.settings.OtherSettingsEntry
import app.n_zik.compagnon.components.settings.SettingsDescription
import app.n_zik.compagnon.components.settings.SettingsSectionCard
import app.n_zik.compagnon.components.tab.Search
import app.n_zik.compagnon.components.themed.HeaderWithIcon
import app.n_zik.compagnon.components.themed.ValueSelectorDialog
import app.n_zik.compagnon.enums.CloseBehavior
import app.n_zik.compagnon.generated.resources.*
import app.n_zik.compagnon.tray.isSystemTraySupported
import app.n_zik.compagnon.utils.AppLanguage
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
import java.util.Locale
import org.jetbrains.compose.resources.stringResource

/**
 * The Général tab (phone's `GeneralSettings.kt` 281-302 + its Languages card 310-331): the tab-style
 * header, the description, the search, and the **Languages** card with the PC's own "App language"
 * entry: "System" (the PC's OS locale, the default) + the phone's languages.
 */
@Composable
fun GeneralSettingsScreen(preferences: Preferences, query: String, onQuery: (String) -> Unit) {
    val settings by preferences.settings.collectAsState()
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showCloseBehaviorDialog by remember { mutableStateOf(false) }
    val search = Search(query, onQuery, lazyListState = null)
    val languagesCtx = settingsSearchCtx(
        search.inputValue,
        stringResource(Res.string.languages),
        stringResource(Res.string.app_language),
    )
    // The window close-behavior setting only exists where a tray exists (the frozen spec: on Wayland
    // the card is hidden and the X quits the app — the decision lives in the window's onCloseRequest)
    val traySupported = isSystemTraySupported()
    val windowCtx = settingsSearchCtx(
        search.inputValue,
        stringResource(Res.string.window),
        stringResource(Res.string.close_behavior),
    )

    Column(
        modifier = Modifier
            .background(colorPalette().background0)
            .fillMaxHeight()
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    ) {
        HeaderWithIcon(
            title = stringResource(Res.string.tab_general),
            iconId = Res.drawable.ic_launcher_monochrome,
            enabled = false,
            showIcon = true,
            modifier = Modifier,
            onClick = {},
        )

        SettingsDescription(
            text = stringResource(Res.string.general_settings_description),
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )

        // Search Section (the phone's `GeneralSettings.kt` 307-308: the toolbar button, then the bar)
        search.ToolBarButton()
        search.SearchBar()

        // Language Section: the PC's own "App language"
        AnimatedVisibility(
            visible = languagesCtx,
            enter = fadeIn(animationSpec = tween(600)) + scaleIn(animationSpec = tween(600), initialScale = 0.9f),
        ) {
            SettingsSectionCard(
                title = stringResource(Res.string.languages),
                icon = Res.drawable.discover,
                content = {
                    OtherSettingsEntry(
                        title = stringResource(Res.string.app_language),
                        text = AppLanguage.labelOf(settings.language),
                        icon = Res.drawable.translate,
                        onClick = { showLanguageDialog = true },
                    )
                },
            )
        }

        // The window's close behavior (spec `spec-tray-taskbar-media-controls`): "minimize to tray"
        // is the default, "quit" closes the app; hidden where no tray exists (Wayland)
        if (traySupported) {
            AnimatedVisibility(
                visible = windowCtx,
                enter = fadeIn(animationSpec = tween(600)) + scaleIn(animationSpec = tween(600), initialScale = 0.9f),
            ) {
                SettingsSectionCard(
                    title = stringResource(Res.string.window),
                    icon = Res.drawable.close,
                    content = {
                        OtherSettingsEntry(
                            title = stringResource(Res.string.close_behavior),
                            text = stringResource(settings.closeBehaviorValue.textId),
                            icon = Res.drawable.close,
                            onClick = { showCloseBehaviorDialog = true },
                        )
                    },
                )
            }
        }

        if (showLanguageDialog) {
            // The phone's title (its `GeneralSettings.kt` 218/336): the label + the current
            // locale (the desktop's equivalent of the phone's system locale)
            ValueSelectorDialog(
                title = stringResource(Res.string.app_language) + ": " + Locale.getDefault(),
                values = remember { AppLanguage.dialogValues() },
                selectedValue = settings.language,
                onValueSelected = {
                    // The phone only stores the choice (its 338-340); the dialog closes itself on a pick
                    preferences.update { s -> s.copy(language = it) }
                },
                onDismiss = { showLanguageDialog = false },
                valueText = { value -> AppLanguage.labelOf(value) },
            )
        }

        if (showCloseBehaviorDialog) {
            ValueSelectorDialog(
                title = stringResource(Res.string.close_behavior),
                values = remember { CloseBehavior.entries.toList() },
                selectedValue = settings.closeBehaviorValue,
                onValueSelected = {
                    // The window's onCloseRequest reads the wire value straight from the settings
                    preferences.update { s -> s.copy(closeBehavior = it.wire) }
                },
                onDismiss = { showCloseBehaviorDialog = false },
                valueText = { value -> stringResource(value.textId) },
            )
        }
    }
}
