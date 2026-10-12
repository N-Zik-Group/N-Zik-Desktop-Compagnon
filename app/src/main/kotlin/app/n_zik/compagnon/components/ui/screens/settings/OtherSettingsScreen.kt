package app.n_zik.compagnon.components.ui.screens.settings

import app.n_zik.compagnon.colorPalette
import app.n_zik.compagnon.components.settings.OtherSettingsEntry
import app.n_zik.compagnon.components.settings.SettingsDescription
import app.n_zik.compagnon.components.settings.SettingsInfoEntry
import app.n_zik.compagnon.components.settings.SettingsSectionCard
import app.n_zik.compagnon.components.settings.ToggleSettingsEntry
import app.n_zik.compagnon.components.tab.Search
import app.n_zik.compagnon.components.themed.HeaderWithIcon
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import org.jetbrains.compose.resources.stringResource

/**
 * The Autres tab (the phone's `OtherSettings.kt` header / description / search 224-240, kept as is;
 * spec `spec-fill-pc-ui-gaps-settings` §A4): the tab-style header, the description and the search,
 * then the phone's cards in the phone's order — **Proxy** (OS:448-451: the phone's "restart
 * required" note + its inert "Enable proxy" toggle, the 6 sub-entries not rendered statically),
 * **Parental control** (OS:634, its inert toggle), **Maintenance** (the phone's
 * `MaintenanceSettingsCard.kt`, inserted at OS:667: its 4 read-only status rows showing the
 * phone's defaults, its inert "Open maintenance" action, its inert "Enable debug log" toggle + the
 * phone's restart note, its inert "Export logs" action) and **Settings reset** (OS:678, inert).
 *
 * The phone entries without a PC key are **inert** (the PC's inert-entry convention): their normal
 * appearance, the phone's default state / value shown, no effect on a click — a long click toasts
 * the phone's description string (the entries whose phone row shows a value instead of a
 * description have no toast, as the phone has none for them). The phone sections "On device"
 * (OS:253), "Android head unit" (OS:355) and "Service lifetime" (OS:382) are Android-only (⛔) —
 * not rendered, a KDoc at each site names the phone entries (file:line) and the reason. The PC's
 * former "Others" card (OSS:103-144) is gone from this tab: its 5 entries were MOVED to the two
 * new phone tabs (4 → the UI tab, "Disable scrolling text" → the Player appearance tab — see the
 * KDoc at the site, spec §A4 / §B / §C).
 */
@Composable
fun OtherSettingsScreen(preferences: Preferences, query: String, onQuery: (String) -> Unit) {
    val search = Search(query, onQuery, lazyListState = null)
    // The card's search ctx (spec §A4): the card's title + its rendered entries' titles
    val proxyCtx = settingsSearchCtx(
        search.inputValue,
        stringResource(Res.string.proxy),
        stringResource(Res.string.enable_proxy),
    )
    val parentalControlCtx = settingsSearchCtx(
        search.inputValue,
        stringResource(Res.string.parental_control),
    )
    val maintenanceCtx = settingsSearchCtx(
        search.inputValue,
        stringResource(Res.string.maintenance),
        stringResource(Res.string.maintenance_dedup),
        stringResource(Res.string.maintenance_convergence),
        stringResource(Res.string.maintenance_db_cleanup),
        stringResource(Res.string.maintenance_live_subsystems),
        stringResource(Res.string.maintenance_open),
        stringResource(Res.string.enable_log_debug),
        stringResource(Res.string.export_logs),
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

        /**
         * MOVED OUT (spec §A4 / §B / §C — the PC's former "Others" card, OSS:103-144, no longer
         * exists in this tab): its 5 entries move to the two new phone tabs, re-ported there with
         * their former value-selector dialogs (the `showNavPosDialog` / `showNavTypeDialog` /
         * `showStartTabDialog` states):
         * - "Navigation bar position" (the PC key `navigationBarPosition`) → the UI tab (UIS:1118)
         * - "Navigation bar type" (the PC key `navigationBarType`) → the UI tab (UIS:1138)
         * - "Default page" (the PC key `indexNavigationTab`) → the UI tab (UIS:1218) — PC constraint
         *   on the value list: `HomeScreenTabs.Default` + Songs / Artists / Albums / Playlists
         *   (the phone's Quick Picks and Search tabs are absent on the PC, so not offered)
         * - "Swipe for action" (the PC key `isSwipeToActionEnabled`) → the UI tab (UIS:1427)
         * - "Disable scrolling text" (the PC key `disableScrollingText`) → the Player appearance
         *   tab (AS:2137)
         */

        /**
         * NOT RENDERED ⛔ (spec §A4): the phone's section "On device" (OS:253) — its
         * "Blacklisted folders" (OS:260, the `Blacklisted_paths.txt` + the `StringListDialog`),
         * its "Folders" (OS:306, `showFoldersOnDeviceKey`) and its sub-entry "Default folder"
         * (OS:319, `defaultFolderKey`) — the Android MediaStore on-device folders; there is no
         * on-device media library on the PC (the PC plays the phone's library). A section with no
         * rendered entry is not rendered.
         */

        /**
         * NOT RENDERED ⛔ (spec §A4): the phone's section "Android head unit" (OS:355) — its only
         * entry, "Extra space" (OS:360, `extraspaceKey`, off) — Android Auto; there is no
         * equivalent on the PC. A section with no rendered entry is not rendered.
         */

        /**
         * NOT RENDERED ⛔ (spec §A4): the phone's section "Service lifetime" (OS:382) — its
         * "Keep screen on" (OS:387, `isKeepScreenOnEnabledKey`) and "Ignore battery
         * optimizations" (OS:395-405, the `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` intent) —
         * the Android screen-timeout / battery APIs; there is no equivalent on the PC. A section
         * with no rendered entry is not rendered.
         */

        // Proxy Section (the phone's OS:440-621, spec §A4)
        AnimatedVisibility(
            visible = proxyCtx,
            enter = fadeIn(animationSpec = tween(600)) + scaleIn(animationSpec = tween(600), initialScale = 0.9f),
        ) {
            SettingsSectionCard(
                title = stringResource(Res.string.proxy),
                icon = Res.drawable.network,
                content = {
                    // The phone's "restart required" note (OS:451), at its phone's place
                    SettingsDescription(text = stringResource(Res.string.restarting_rimusic_is_required))

                    // "Enable proxy" (OS:455, the phone's `isProxyEnabledKey`, off by default) —
                    // INERT: the default switch state, a click has no effect. The phone's row
                    // shows no description → no toast
                    ToggleSettingsEntry(
                        title = stringResource(Res.string.enable_proxy),
                        text = "",
                        icon = Res.drawable.server,
                        isChecked = false,
                        enabled = false,
                        onCheckedChange = {},
                    )

                    /**
                     * NOT RENDERED (static, spec §A4): the phone's proxy sub-entries, all only
                     * shown when `isProxyEnabled` — "Proxy mode" (OS:477, `proxyModeKey`, HTTP),
                     * "Proxy host" (OS:499, `proxyHostnameKey`, ""), "Proxy port" (OS:523,
                     * `proxyPortKey`, 1080), "Proxy username" (OS:547, `proxyUsernameKey`, ""),
                     * "Proxy password" (OS:570, the encrypted `proxyPasswordEncryptedKey`, "")
                     * and "Region override" (OS:594, `regionOverrideKey`, "") — `isProxyEnabled`
                     * false on the phone's default and with no PC key (the condition is
                     * statically false on the PC).
                     */
                },
            )
        }

        // Parental control Section (the phone's OS:626-648, spec §A4)
        AnimatedVisibility(
            visible = parentalControlCtx,
            enter = fadeIn(animationSpec = tween(700)) + scaleIn(animationSpec = tween(700), initialScale = 0.9f),
        ) {
            SettingsSectionCard(
                title = stringResource(Res.string.parental_control),
                icon = Res.drawable.shield_checkmark,
                content = {
                    // "Parental control" (OS:639, the phone's `parentalControlEnabledKey`, off by
                    // default) — INERT: the default switch state, a click has no effect, a long
                    // click toasts the phone's description
                    ToggleSettingsEntry(
                        title = stringResource(Res.string.parental_control),
                        text = stringResource(Res.string.info_prevent_play_songs_with_age_limitation),
                        icon = Res.drawable.parental_control,
                        isChecked = false,
                        enabled = false,
                        onCheckedChange = {},
                        onLongClick = { Toaster.i(Res.string.info_prevent_play_songs_with_age_limitation) },
                    )
                },
            )
        }

        // Maintenance Section (the phone's `MaintenanceSettingsCard.kt`, inserted at OS:667, spec §A4)
        AnimatedVisibility(
            visible = maintenanceCtx,
            enter = fadeIn(animationSpec = tween(800)) + scaleIn(animationSpec = tween(800), initialScale = 0.9f),
        ) {
            SettingsSectionCard(
                title = stringResource(Res.string.maintenance),
                icon = Res.drawable.shield_checkmark,
                description = stringResource(Res.string.maintenance_card_desc),
                content = {
                    // The phone's pass table (MSC:141 / 150 / 159 / 168): on the PC the pass
                    // states are null (the PC has no maintenance subsystem), so each row shows
                    // the phone's own no-data default — "No data" (the phone's
                    // `lastRunLine` / `maintenanceHealth` on a missing snapshot, MSC:145-171)
                    SettingsInfoEntry(
                        title = stringResource(Res.string.maintenance_dedup),
                        text = stringResource(Res.string.maintenance_no_data),
                        icon = Res.drawable.trash,
                    )
                    SettingsInfoEntry(
                        title = stringResource(Res.string.maintenance_convergence),
                        text = stringResource(Res.string.maintenance_no_data),
                        icon = Res.drawable.sync,
                    )
                    SettingsInfoEntry(
                        title = stringResource(Res.string.maintenance_db_cleanup),
                        text = stringResource(Res.string.maintenance_no_data),
                        icon = Res.drawable.data_migration,
                    )
                    SettingsInfoEntry(
                        title = stringResource(Res.string.maintenance_live_subsystems),
                        text = stringResource(Res.string.maintenance_no_data),
                        icon = Res.drawable.shield_checkmark,
                    )

                    // "Open maintenance" (MSC:174) — INERT: the phone opens its Maintenance sheet
                    // (100 % phone-side — the sheet's jobs, crash log, DB counts, ...); on the PC
                    // a click has no effect, a long click toasts the phone's description
                    OtherSettingsEntry(
                        title = stringResource(Res.string.maintenance_open),
                        text = stringResource(Res.string.maintenance_open_desc),
                        icon = Res.drawable.chevron_forward,
                        onClick = {},
                        onLongClick = { Toaster.i(Res.string.maintenance_open_desc) },
                    )

                    // "Enable debug log" (MSC:184, the phone's `logDebugEnabledKey`, off by
                    // default) — INERT: the default switch state, a click has no effect, a long
                    // click toasts the phone's description
                    ToggleSettingsEntry(
                        title = stringResource(Res.string.enable_log_debug),
                        text = stringResource(Res.string.if_enabled_create_a_log_file_to_highlight_errors),
                        icon = Res.drawable.information,
                        isChecked = false,
                        enabled = false,
                        onCheckedChange = {},
                        onLongClick = { Toaster.i(Res.string.if_enabled_create_a_log_file_to_highlight_errors) },
                    )

                    // The phone's restart note (MSC:200, its `ImportantSettingsDescription`), at
                    // its phone's place
                    SettingsDescription(
                        text = stringResource(Res.string.restarting_rimusic_is_required),
                        important = true,
                    )

                    // "Export logs" (MSC:202) — INERT: the phone's `CopyLogsDialog` is 100 %
                    // phone-side (it exports the phone's debug / crash logs); on the PC a click
                    // has no effect, a long click toasts the phone's description
                    OtherSettingsEntry(
                        title = stringResource(Res.string.export_logs),
                        text = stringResource(Res.string.export_debug_log_description),
                        icon = Res.drawable.copy,
                        onClick = {},
                        onLongClick = { Toaster.i(Res.string.export_debug_log_description) },
                    )
                },
            )
        }

        // Settings reset Section (the phone's OS:672-703, spec §A4)
        AnimatedVisibility(
            visible = settingsResetCtx,
            enter = fadeIn(animationSpec = tween(900)) + scaleIn(animationSpec = tween(900), initialScale = 0.9f),
        ) {
            SettingsSectionCard(
                title = stringResource(Res.string.settings_reset),
                icon = Res.drawable.refresh,
                content = {
                    // "Settings reset" (OS:685) — INERT: the phone resets ITS settings here (its
                    // `DefaultOtherSettings`); on the PC a click has no effect, a long click toasts
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
    }
}
