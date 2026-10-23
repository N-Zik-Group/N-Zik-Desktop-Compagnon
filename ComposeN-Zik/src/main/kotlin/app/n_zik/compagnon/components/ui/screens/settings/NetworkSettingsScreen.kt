package app.n_zik.compagnon.components.ui.screens.settings

import app.n_zik.compagnon.colorPalette
import app.n_zik.compagnon.components.settings.OtherSettingsEntry
import app.n_zik.compagnon.components.settings.SettingsDescription
import app.n_zik.compagnon.components.settings.SettingsInfoEntry
import app.n_zik.compagnon.components.settings.SettingsSectionCard
import app.n_zik.compagnon.components.settings.ToggleSettingsEntry
import app.n_zik.compagnon.components.tab.Search
import app.n_zik.compagnon.components.themed.HeaderWithIcon
import app.n_zik.compagnon.components.themed.ValueSelectorDialog
import app.n_zik.compagnon.enums.AudioQualityFormat
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
 * The Réseau tab (the phone's `NetworkSettings.kt`, spec `spec-fill-pc-ui-gaps-settings` §A3): the
 * tab-style header, the description and the search, then the phone's cards in the phone's order —
 * **PC server** (NS:165-179, WIRED nav: the click opens the window's own Serveur PC page through
 * [onOpenServer], the phone's label / description / icon ported as is), **Network status** (NS:183,
 * its 3 read-only rows: the audio row live with the PC's current `audioQualityFormat`, image /
 * download the phone's default — no PC key), the **Quality** card (NS:250, existing — extended with
 * the phone's "Image quality format" and "Download quality format" inert rows and its "Update
 * downloads" inert action), **Download** (NS:405, its inert "Auto-download song" toggle) and
 * **Settings reset** (NS:469, inert).
 *
 * The phone entries without a PC key are **inert** (the PC's inert-entry convention): their normal
 * appearance, the phone's default state / value shown, no effect on a click — a long click toasts
 * the phone's description string (the entries whose phone row shows a value instead of a
 * description have no toast, as the phone has none for them). The phone's "Enable on metered
 * connection" (NS:345, ⛔ the Android metered-connection API) is not rendered — a KDoc at the site
 * names the phone entry (file:line) and the reason; its section (Connection settings, NS:340) has
 * no rendered entry, so it is not rendered either. The phone's auto-download sub-entries (NS:436 /
 * 448) are only shown when `autoDownloadSong`, false on the phone's default and with no PC key —
 * not rendered (KDoc).
 */
@Composable
fun NetworkSettingsScreen(
    preferences: Preferences,
    query: String,
    onOpenServer: () -> Unit,
    onQuery: (String) -> Unit,
) {
    val settings by preferences.settings.collectAsState()
    var showAudioQualityDialog by remember { mutableStateOf(false) }
    val search = Search(query, onQuery, lazyListState = null)
    // The card's search ctx (spec §A3): the card's title + its rendered entries' titles
    val pcServerCtx = settingsSearchCtx(search.inputValue, stringResource(Res.string.bridge_server))
    val networkStatusCtx = settingsSearchCtx(
        search.inputValue,
        stringResource(Res.string.network_status),
        stringResource(Res.string.image_quality_format),
        stringResource(Res.string.audio_quality_format),
        stringResource(Res.string.download_quality_format),
    )
    val qualityCtx = settingsSearchCtx(
        search.inputValue,
        stringResource(Res.string.quality),
        stringResource(Res.string.audio_quality_format),
        stringResource(Res.string.image_quality_format),
        stringResource(Res.string.download_quality_format),
        stringResource(Res.string.update_downloads),
    )
    val downloadCtx = settingsSearchCtx(
        search.inputValue,
        stringResource(Res.string.download),
        stringResource(Res.string.settings_enable_autodownload_song),
    )
    val settingsResetCtx = settingsSearchCtx(search.inputValue, stringResource(Res.string.settings_reset))

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

        // PC server Section (the phone's NS:165-179, spec §A3)
        AnimatedVisibility(
            visible = pcServerCtx,
            enter = fadeIn(animationSpec = tween(600)) + scaleIn(animationSpec = tween(600), initialScale = 0.9f),
        ) {
            SettingsSectionCard(
                title = stringResource(Res.string.bridge_server),
                icon = Res.drawable.server,
                content = {
                    // "PC server" (NS:168 / 172, the phone's `bridge_server`) — WIRED (nav): the click
                    // opens the window's own Serveur PC page (the phone navigates to its

                    // `bridgeServer` route), its label / description / icon ported as is
                    OtherSettingsEntry(
                        title = stringResource(Res.string.bridge_server),
                        text = stringResource(Res.string.bridge_server_description),
                        icon = Res.drawable.devices,
                        onClick = onOpenServer,
                    )
                },
            )
        }

        // Network status Section (the phone's NS:183, spec §A3)
        AnimatedVisibility(
            visible = networkStatusCtx,
            enter = fadeIn(animationSpec = tween(700)) + scaleIn(animationSpec = tween(700), initialScale = 0.9f),
        ) {
            SettingsSectionCard(
                title = stringResource(Res.string.network_status),
                icon = Res.drawable.network,
                content = {
                    // The phone's status rows (NS:205 / 222 / 239) show the format plus its detected
                    // network quality; the PC has neither a network-quality detection nor image /

                    // download quality keys (the PC's image / download qualities are the phone's) —
                    // read-only rows: the audio row live with the PC's current `audioQualityFormat`,

                    // image / download the phone's default ("Auto")
                    SettingsInfoEntry(
                        title = stringResource(Res.string.image_quality_format),
                        text = stringResource(Res.string.audio_quality_automatic),
                        icon = Res.drawable.image,
                    )
                    SettingsInfoEntry(
                        title = stringResource(Res.string.audio_quality_format),
                        text = stringResource(audioQualityFormat.textId),
                        icon = Res.drawable.audio_quality,
                    )
                    SettingsInfoEntry(
                        title = stringResource(Res.string.download_quality_format),
                        text = stringResource(Res.string.audio_quality_automatic),
                        icon = Res.drawable.download,
                    )
                },
            )
        }

        // Quality Settings Section (the phone's NS:250, extended — spec §A3)
        AnimatedVisibility(
            visible = qualityCtx,
            enter = fadeIn(animationSpec = tween(800)) + scaleIn(animationSpec = tween(800), initialScale = 0.9f),
        ) {
            SettingsSectionCard(
                title = stringResource(Res.string.quality),
                icon = Res.drawable.audio_quality,
                content = {
                    // "Audio quality format" (NS:256, `audioQualityFormatKey`): WIRED — the quality
                    // asked when the PC forges an audio URL — unchanged
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

                    // "Image quality format" (NS:270, `imageQualityFormatKey`, Auto) — INERT: the
                    // phone's default (Auto) shown, no dialog, a click has no effect (no PC key: the

                    // PC's image quality is the phone's); the phone's row shows a value, not a
                    // description → no toast
                    OtherSettingsEntry(
                        title = stringResource(Res.string.image_quality_format),
                        text = stringResource(Res.string.audio_quality_automatic),
                        icon = Res.drawable.image,
                        onClick = {},
                    )

                    // "Download quality format" (DQS:48, `downloadQualityFormatKey`, Auto) — INERT:
                    // the phone's default (Auto) shown, no dialog, a click has no effect (no PC key:

                    // the PC has no download cache); the phone's row shows a value, not a
                    // description → no toast
                    OtherSettingsEntry(
                        title = stringResource(Res.string.download_quality_format),
                        text = stringResource(Res.string.audio_quality_automatic),
                        icon = Res.drawable.download,
                        onClick = {},
                    )

                    // "Update downloads" (DQS:95, action) — INERT: the phone re-downloads its
                    // non-compliant downloads; the PC has none, a click has no effect, a long click

                    // toasts the phone's description
                    OtherSettingsEntry(
                        title = stringResource(Res.string.update_downloads),
                        text = stringResource(Res.string.update_downloads_description),
                        icon = Res.drawable.download,
                        onClick = {},
                        onLongClick = { Toaster.i(Res.string.update_downloads_description) },
                    )

                    /**
                     * NOT RENDERED (spec §A3): the phone's "Restart player service" prompt
                     * (`RestartPlayerService`, NS:288-290) — it restarts the phone's own player
                     * service; the PC has no player service to restart (its audio-quality change
                     * takes effect on the next URL).
                     */
                },
            )
        }

        /**
         * NOT RENDERED ⛔ (no rendered entry, spec §A3): the phone's section "Connection settings"
         * (NS:340) — its only entry, "Enable on metered connection" (NS:345,
         * `isConnectionMeteredEnabledKey`, off) — the Android metered-connection API; there is no
         * equivalent on the PC. A section with no rendered entry is not rendered.
         */

        // Download Section (the phone's NS:405, spec §A3)
        AnimatedVisibility(
            visible = downloadCtx,
            enter = fadeIn(animationSpec = tween(900)) + scaleIn(animationSpec = tween(900), initialScale = 0.9f),
        ) {
            SettingsSectionCard(
                title = stringResource(Res.string.download),
                icon = Res.drawable.arrow_down,
                content = {
                    // "Auto-download song" (NS:410, `autoDownloadSongKey`, off by default) — INERT:
                    // the default switch state, a click has no effect, a long click toasts the

                    // phone's description
                    ToggleSettingsEntry(
                        title = stringResource(Res.string.settings_enable_autodownload_song),
                        text = stringResource(Res.string.auto_download_song_description),
                        icon = Res.drawable.download,
                        isChecked = false,
                        enabled = false,
                        onCheckedChange = {},
                        onLongClick = { Toaster.i(Res.string.auto_download_song_description) },
                    )

                    /**
                     * NOT RENDERED (static, spec §A3): the phone's "Auto-download when liked"
                     * (NS:436, `autoDownloadSongWhenLikedKey`, off) and "Auto-download when album
                     * bookmarked" (NS:448, `autoDownloadSongWhenAlbumBookmarkedKey`, off) — both
                     * only shown when `autoDownloadSong`, off on the phone's default and with no PC
                     * key (the condition is statically false on the PC).
                     */
                },
            )
        }

        // Settings reset Section (the phone's NS:469, spec §A3)
        AnimatedVisibility(
            visible = settingsResetCtx,
            enter = fadeIn(animationSpec = tween(1100)) + scaleIn(animationSpec = tween(1100), initialScale = 0.9f),
        ) {
            SettingsSectionCard(
                title = stringResource(Res.string.settings_reset),
                icon = Res.drawable.refresh,
                content = {
                    // "Settings reset" (NS:476) — INERT: the phone resets ITS settings here (its
                    // `DefaultNetworkSettings`); on the PC a click has no effect, a long click toasts

                    // the phone's description
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
