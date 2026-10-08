package app.n_zik.compagnon.components.ui.screens.settings

import app.n_zik.compagnon.colorPalette
import app.n_zik.compagnon.components.settings.OtherSettingsEntry
import app.n_zik.compagnon.components.settings.SettingsDescription
import app.n_zik.compagnon.components.settings.SettingsSectionCard
import app.n_zik.compagnon.components.tab.Search
import app.n_zik.compagnon.components.themed.HeaderWithIcon
import app.n_zik.compagnon.components.themed.ValueSelectorDialog
import app.n_zik.compagnon.generated.resources.*
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
 * header, the description, the search, and the **Languages** card (contract 1.9.0 `ui.language`) with
 * the PC's "App language" entry (its dialog unchanged).
 */
@Composable
fun GeneralSettingsScreen(preferences: Preferences, query: String, onQuery: (String) -> Unit) {
    val settings by preferences.settings.collectAsState()
    var showLanguageDialog by remember { mutableStateOf(false) }
    val search = Search(query, onQuery, lazyListState = null)
    val languagesCtx = settingsSearchCtx(
        search.inputValue,
        stringResource(Res.string.languages),
        stringResource(Res.string.app_language),
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

        // Language Section (contract 1.9.0, `ui.language`): the PC's own "App language"
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
                        text = languageLabel(settings.language),
                        icon = Res.drawable.translate,
                        onClick = { showLanguageDialog = true },
                    )
                },
            )
        }

        if (showLanguageDialog) {
            // The phone's title (its `GeneralSettings.kt` 218/336): the label + the current
            // locale (the desktop's equivalent of the phone's system locale)
            ValueSelectorDialog(
                title = stringResource(Res.string.app_language) + ": " + Locale.getDefault(),
                values = remember { (listOf(AppLanguage.AUTO_PC, AppLanguage.AUTO_TEL) + AppLanguage.LANGUAGES.map { it.first }) },
                selectedValue = settings.language,
                onValueSelected = {
                    preferences.update { s -> s.copy(language = it) }
                    showLanguageDialog = false
                },
                onDismiss = { showLanguageDialog = false },
                valueText = { languageLabel(it) },
            )
        }
    }
}

/**
 * The label of an "App language" value: the two static sentinel labels (like the endonyms, they
 * are deliberately not localized), else the endonym of the phone's language ([AppLanguage.labelOf]).
 */
private fun languageLabel(value: String): String = when (value) {
    AppLanguage.AUTO_PC -> "Auto PC"
    AppLanguage.AUTO_TEL -> "Auto Tel"
    else -> AppLanguage.labelOf(value)
}
