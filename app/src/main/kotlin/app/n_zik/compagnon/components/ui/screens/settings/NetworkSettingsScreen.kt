package app.n_zik.compagnon.components.ui.screens.settings

import app.n_zik.compagnon.colorPalette
import app.n_zik.compagnon.components.settings.OtherSettingsEntry
import app.n_zik.compagnon.components.settings.SettingsDescription
import app.n_zik.compagnon.components.settings.SettingsSectionCard
import app.n_zik.compagnon.components.tab.Search
import app.n_zik.compagnon.components.themed.HeaderWithIcon
import app.n_zik.compagnon.components.themed.ValueSelectorDialog
import app.n_zik.compagnon.enums.AudioQualityFormat
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
 * The Réseau tab (phone's `NetworkSettings.kt` 138-158 + its audio-quality card): the tab-style
 * header, the description, the search, and the **Quality** card — "Audio Quality" (its
 * [OtherSettingsEntry] + its [ValueSelectorDialog], unchanged), the quality asked when the PC forges
 * an audio URL. The phone's PC-server entry is not ported (it is the window's own Serveur PC page).
 */
@Composable
fun NetworkSettingsScreen(preferences: Preferences, query: String, onQuery: (String) -> Unit) {
    val settings by preferences.settings.collectAsState()
    var showAudioQualityDialog by remember { mutableStateOf(false) }
    val search = Search(query, onQuery, lazyListState = null)
    val qualityCtx = settingsSearchCtx(
        search.inputValue,
        stringResource(Res.string.quality),
        stringResource(Res.string.audio_quality_format),
    )

    val audioQualityFormat = settings.audioQualityFormat

    Column(
        modifier = Modifier
            .background(colorPalette().background0)
            .fillMaxHeight()
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    ) {
        HeaderWithIcon(
            title = stringResource(Res.string.tab_network),
            iconId = Res.drawable.network,
            enabled = false,
            showIcon = true,
            modifier = Modifier,
            onClick = {},
        )

        SettingsDescription(
            text = stringResource(Res.string.network_settings_description),
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )

        // Search Section
        search.ToolBarButton()
        search.SearchBar()

        // Quality Settings Section
        AnimatedVisibility(
            visible = qualityCtx,
            enter = fadeIn(animationSpec = tween(600)) + scaleIn(animationSpec = tween(600), initialScale = 0.9f),
        ) {
            SettingsSectionCard(
                title = stringResource(Res.string.quality),
                icon = Res.drawable.audio_quality,
                content = {
                    OtherSettingsEntry(
                        title = stringResource(Res.string.audio_quality_format),
                        text = when (audioQualityFormat) {
                            AudioQualityFormat.Auto -> stringResource(Res.string.audio_quality_automatic)
                            AudioQualityFormat.High -> stringResource(Res.string.audio_quality_format_high)
                            AudioQualityFormat.Low -> stringResource(Res.string.audio_quality_format_low)
                        },
                        icon = Res.drawable.speaker,
                        onClick = { showAudioQualityDialog = true },
                    )
                },
            )
        }

        if (showAudioQualityDialog) {
            ValueSelectorDialog(
                title = stringResource(Res.string.audio_quality_format),
                values = AudioQualityFormat.entries.toList(),
                selectedValue = audioQualityFormat,
                onValueSelected = {
                    preferences.update { s -> s.copy(audioQualityFormat = it) }
                    showAudioQualityDialog = false
                },
                onDismiss = { showAudioQualityDialog = false },
                valueText = { stringResource(it.textId) },
            )
        }
    }
}
