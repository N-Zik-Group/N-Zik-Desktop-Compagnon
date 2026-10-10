package app.n_zik.compagnon.components.ui.screens.settings

import app.n_zik.compagnon.colorPalette
import app.n_zik.compagnon.components.settings.SettingsDescription
import app.n_zik.compagnon.components.settings.SettingsSectionCard
import app.n_zik.compagnon.components.settings.ToggleSettingsEntry
import app.n_zik.compagnon.components.tab.Search
import app.n_zik.compagnon.components.themed.HeaderWithIcon
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import org.jetbrains.compose.resources.stringResource

/**
 * The Autres tab (phone's `OtherSettings.kt` 215-237 + the PC's own entry): the tab-style header,
 * the description, the search, and the **Others** card — "Disable scrolling text" (the Compagnon's
 * own setting that drops the marquees; its [ToggleSettingsEntry] unchanged). The phone's "Disable
 * scrolling text" sits in its Player Appearance tab (not ported) — the user placed it here; after the
 * "remove UI sync" spec it stays a PC-only setting, never driven by the phone.
 */
@Composable
fun OtherSettingsScreen(preferences: Preferences, query: String, onQuery: (String) -> Unit) {
    val settings by preferences.settings.collectAsState()
    val search = Search(query, onQuery, lazyListState = null)
    val othersCtx = settingsSearchCtx(
        search.inputValue,
        stringResource(Res.string.other),
        stringResource(Res.string.disable_scrolling_text),
    )

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
        // not port — the user placed it here; it stays a PC-only setting (no longer driven by the phone)
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
                },
            )
        }
    }
}
