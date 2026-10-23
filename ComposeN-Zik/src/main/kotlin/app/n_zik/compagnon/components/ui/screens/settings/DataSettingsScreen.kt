package app.n_zik.compagnon.components.ui.screens.settings

import app.n_zik.compagnon.colorPalette
import app.n_zik.compagnon.components.settings.CacheSettingsEntry
import app.n_zik.compagnon.components.settings.OtherSettingsEntry
import app.n_zik.compagnon.components.settings.SettingsDescription
import app.n_zik.compagnon.components.settings.SettingsSectionCard
import app.n_zik.compagnon.components.settings.ToggleSettingsEntry
import app.n_zik.compagnon.components.tab.Search
import app.n_zik.compagnon.components.themed.CacheSpaceIndicator
import app.n_zik.compagnon.components.themed.ConfirmationDialog
import app.n_zik.compagnon.components.themed.HeaderWithIcon
import app.n_zik.compagnon.components.themed.InputNumericDialog
import app.n_zik.compagnon.components.themed.ValueSelectorDialog
import app.n_zik.compagnon.enums.ExoPlayerDiskCacheMaxSize
import app.n_zik.compagnon.generated.resources.*
import app.n_zik.compagnon.playback.cache.AudioCache
import app.n_zik.compagnon.utils.Preferences
import app.n_zik.compagnon.utils.Toaster
import app.n_zik.compagnon.utils.coroutines.NzikDispatchers
import app.n_zik.compagnon.utils.formatShortFileSize
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource

/**
 * The Données tab (the phone's `DataSettings.kt` 255-276 header / description / search, spec
 * `spec-fill-pc-ui-gaps-settings` §A2): the **Cache** card (its "Song cache" [CacheSettingsEntry],
 * its [ValueSelectorDialog], the custom size dialog and its [CacheSpaceIndicator] of the local audio
 * cache — all unchanged — extended with the phone's "Image cache max size" and "Song download max
 * size" rows in the phone's order), then the phone's 4 cards in the phone's order: Backup & restore
 * (DS:499), Auto backup (DS:553), History (DS:572) and Settings reset (DS:661).
 *
 * The phone entries without a PC key are **inert** (the PC's inert-entry convention): their normal
 * appearance, the phone's default state / value shown, no effect on a click — a long click toasts
 * the phone's description string (the entries whose phone row shows a value instead of a
 * description have no toast, as the phone has none for them). The entries whose phone condition is
 * false on the phone's defaults, or Android-only (⛔), are not rendered — a KDoc at the site names
 * the phone entry (file:line) and the reason.
 */
@Composable
fun DataSettingsScreen(preferences: Preferences, cache: AudioCache?, query: String, onQuery: (String) -> Unit) {
    val settings by preferences.settings.collectAsState()
    val scope = rememberCoroutineScope()
    var showSongCacheDialog by remember { mutableStateOf(false) }
    var showExoPlayerCustomCacheDialog by remember { mutableStateOf(false) }
    var cleanCacheOfflineSongs by remember { mutableStateOf(false) }
    var cacheCleanedCounter by remember { mutableIntStateOf(0) }
    val search = Search(query, onQuery, lazyListState = null)
    // The card's search ctx (spec §A2): the card's title + its rendered entries' titles
    val cacheCtx = settingsSearchCtx(
        search.inputValue,
        stringResource(Res.string.cache),
        stringResource(Res.string.image_cache_max_size),
        stringResource(Res.string.song_cache_max_size),
        stringResource(Res.string.song_download_max_size),
    )
    val backupRestoreCtx = settingsSearchCtx(
        search.inputValue,
        stringResource(Res.string.title_backup_and_restore),
        stringResource(Res.string.export_backup),
        stringResource(Res.string.import_backup),
        stringResource(Res.string.title_import_settings_migration),
    )
    val autoBackupCtx = settingsSearchCtx(
        search.inputValue,
        stringResource(Res.string.auto_backup_title),
        stringResource(Res.string.auto_backup_location),
        stringResource(Res.string.auto_backup_target),
        stringResource(Res.string.auto_backup_max_backups),
        stringResource(Res.string.auto_backup_interval),
    )
    val historyCtx = settingsSearchCtx(
        search.inputValue,
        stringResource(Res.string.history),
        stringResource(Res.string.pause_search_history),
        stringResource(Res.string.player_pause_listen_history),
        stringResource(Res.string.clear_search_history),
        stringResource(Res.string.clear_listen_history),
    )
    val settingsResetCtx = settingsSearchCtx(search.inputValue, stringResource(Res.string.settings_reset))

    val exoPlayerDiskCacheMaxSize = settings.exoPlayerDiskCacheMaxSize
    val exoPlayerCustomCache = settings.exoPlayerCustomCache

    // The cache fills while a track plays: its size is read again every few seconds, off the UI thread
    val diskCacheSize by produceState(0L, cache, cacheCleanedCounter) {
        while (true) {
            value = cache?.let { withContext(NzikDispatchers.DATA) { it.totalBytes() } } ?: 0L
            delay(CACHE_SIZE_REFRESH_MS)
        }
    }

    fun trimCache() {
        scope.launch { withContext(NzikDispatchers.DATA) { cache?.trim() } }
    }

    if (cleanCacheOfflineSongs) {
        ConfirmationDialog(
            text = stringResource(Res.string.do_you_really_want_to_delete_cache),
            onDismiss = { cleanCacheOfflineSongs = false },
            onConfirm = {
                cleanCacheOfflineSongs = false
                scope.launch {
                    withContext(NzikDispatchers.DATA) { cache?.clear() }
                    cacheCleanedCounter++
                }
            },
        )
    }

    Column(
        modifier = Modifier
            .background(colorPalette().background0)
            .fillMaxHeight()
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    ) {
        HeaderWithIcon(
            title = stringResource(Res.string.tab_data),
            iconId = Res.drawable.server,
            enabled = false,
            showIcon = true,
            modifier = Modifier,
            onClick = {},
        )

        SettingsDescription(
            text = stringResource(Res.string.data_settings_description),
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )

        // Search Section
        search.ToolBarButton()
        search.SearchBar()

        // Cache Section (the phone's DS:291, spec §A2)
        AnimatedVisibility(
            visible = cacheCtx,
            enter = fadeIn(animationSpec = tween(600)) + scaleIn(animationSpec = tween(600), initialScale = 0.9f),
        ) {
            SettingsSectionCard(
                title = stringResource(Res.string.cache),
                icon = Res.drawable.server,
                description = stringResource(Res.string.cache_cleared),
                content = {
                    // "Image cache max size" (DS:304-352, the phone's `coilDiskCacheMaxSizeKey`,
                    // `CoilDiskCacheMaxSize.128MB`) — INERT: the phone's default value shown (the

                    // enum's name, "128MB", as the phone renders it), a click has no effect. The
                    // phone's row shows a value, not a description → no toast. The phone's trash and

                    // "used" indicator are not ported (the PC's image cache is fixed, spec §A2)
                    OtherSettingsEntry(
                        title = stringResource(Res.string.image_cache_max_size),
                        text = "128MB",
                        icon = Res.drawable.image,
                        onClick = {},
                    )

                    // "Song cache max size" (DS:366-413, `exoPlayerDiskCacheMaxSizeKey`): WIRED to the
                    // PC's local audio cache — unchanged
                    CacheSettingsEntry(
                        title = stringResource(Res.string.song_cache_max_size),
                        text = when (exoPlayerDiskCacheMaxSize) {
                            ExoPlayerDiskCacheMaxSize.Custom -> "${stringResource(Res.string.custom)}: ${exoPlayerCustomCache}MB"
                            ExoPlayerDiskCacheMaxSize.Disabled -> stringResource(Res.string.turn_off)
                            else -> exoPlayerDiskCacheMaxSize.text
                        },
                        icon = Res.drawable.music_file,
                        onClick = { showSongCacheDialog = true },
                        onTrashClick = { cleanCacheOfflineSongs = true },
                    )

                    if (showSongCacheDialog) {
                        ValueSelectorDialog(
                            title = stringResource(Res.string.song_cache_max_size),
                            selectedValue = exoPlayerDiskCacheMaxSize,
                            values = ExoPlayerDiskCacheMaxSize.entries.toList(),
                            onValueSelected = {
                                preferences.update { s -> s.copy(exoPlayerDiskCacheMaxSize = it) }
                                if (it == ExoPlayerDiskCacheMaxSize.Custom) showExoPlayerCustomCacheDialog = true
                                trimCache()
                            },
                            valueText = { it.text },
                            onDismiss = { showSongCacheDialog = false },
                        )
                    }

                    if (showExoPlayerCustomCacheDialog) {
                        InputNumericDialog(
                            title = stringResource(Res.string.set_custom_cache),
                            placeholder = stringResource(Res.string.enter_value_in_mb),
                            value = exoPlayerCustomCache.toString(),
                            valueMin = "32",
                            valueMax = "10000",
                            onDismiss = { showExoPlayerCustomCacheDialog = false },
                            setValue = {
                                preferences.update { s -> s.copy(exoPlayerCustomCache = it.toInt()) }
                                showExoPlayerCustomCacheDialog = false
                                trimCache()
                            },
                        )
                    }

                    val maxBytes = settings.songCacheMaxBytes
                    CacheSpaceIndicator(
                        usedBytes = diskCacheSize,
                        maxBytes = maxBytes,
                        maxText = exoPlayerDiskCacheMaxSize.text,
                        horizontalPadding = 20.dp,
                        showCacheInfo = false,
                    )

                    SettingsDescription(
                        text = "${formatShortFileSize(diskCacheSize)} ${stringResource(Res.string.used)} (${
                            // The phone's divisor (its `DataSettings.kt` 413): the setting's own `bytes` — the Custom entry's
                            // nominal value, not the custom size, as on the phone
                            if (exoPlayerDiskCacheMaxSize.bytes > 0) "${diskCacheSize * 100 / exoPlayerDiskCacheMaxSize.bytes}%" else stringResource(Res.string.unlimited)
                        })",
                    )

                    // "Song download max size" (DS:426-451, the phone's `exoPlayerDiskDownloadCacheMaxSizeKey`,
                    // `ExoPlayerDiskDownloadCacheMaxSize.2GB`) — INERT (user decision 2026-10-12: the phone's

                    // `downloadCache` always exists on the phone, so the phone renders the row): the phone's
                    // default value shown (the enum's name, "2GB", as the phone renders it), a click has no

                    // effect. The phone's row shows a value, not a description → no toast. The phone's trash
                    // and "used" indicator are not ported (the PC has no download cache, spec §A2)
                    OtherSettingsEntry(
                        title = stringResource(Res.string.song_download_max_size),
                        text = "2GB",
                        icon = Res.drawable.download,
                        onClick = {},
                    )

                    /**
                     * NOT RENDERED ⛔ (spec §A2): the phone's "Set cache location" (DS:458-465,
                     * `exoPlayerCacheLocationKey`, System) and its note "private cache can't be cleaned"
                     * (DS:465) — the Android System/Private storage location selector; there is no
                     * equivalent on the PC (the PC's cache lives in its own app-data folder).
                     */
                },
            )
        }

        // Backup & restore Section (the phone's DS:499, spec §A2)
        AnimatedVisibility(
            visible = backupRestoreCtx,
            enter = fadeIn(animationSpec = tween(800)) + scaleIn(animationSpec = tween(800), initialScale = 0.9f),
        ) {
            SettingsSectionCard(
                title = stringResource(Res.string.title_backup_and_restore),
                icon = Res.drawable.server,
                content = {
                    // "Export backup" (DS:507-515) — INERT: the phone's dialog is 100 % phone-side
                    // (it exports the phone's DB / settings to the phone's storage); a click has no

                    // effect, a long click toasts the phone's description
                    OtherSettingsEntry(
                        title = stringResource(Res.string.export_backup),
                        text = stringResource(Res.string.export_backup_description),
                        icon = Res.drawable.export_outline,
                        onClick = {},
                        onLongClick = { Toaster.i(Res.string.export_backup_description) },
                    )

                    /**
                     * NOT RENDERED (spec §A2): the phone's warning notes under "Export backup"
                     * (`personal_preference`, DS:513-515) and under "Import backup"
                     * (`existing_data_will_be_overwritten` with the phone's own app label,
                     * DS:525-528) — they warn about the phone's data; with the import inert on the PC
                     * there is nothing to warn about (the second note also needs the phone's app
                     * label).
                     */

                    // "Import backup" (DS:519-528) — INERT: as "Export backup"
                    OtherSettingsEntry(
                        title = stringResource(Res.string.import_backup),
                        text = stringResource(Res.string.import_backup_description),
                        icon = Res.drawable.import_outline,
                        onClick = {},
                        onLongClick = { Toaster.i(Res.string.import_backup_description) },
                    )

                    // "Import settings migration" (DS:534) — INERT: the phone's `ImportMigration` is
                    // 100 % phone-side (the old app's DB format); a click has no effect, a long click

                    // toasts the phone's description
                    OtherSettingsEntry(
                        title = stringResource(Res.string.title_import_settings_migration),
                        text = stringResource(Res.string.description_import_settings_migration),
                        icon = Res.drawable.data_migration,
                        onClick = {},
                        onLongClick = { Toaster.i(Res.string.description_import_settings_migration) },
                    )
                },
            )
        }

        // Auto backup Section (the phone's DS:553, spec §A2)
        AnimatedVisibility(
            visible = autoBackupCtx,
            enter = fadeIn(animationSpec = tween(800)) + scaleIn(animationSpec = tween(800), initialScale = 0.9f),
        ) {
            SettingsSectionCard(
                title = stringResource(Res.string.auto_backup_title),
                icon = Res.drawable.history,
                content = {
                    // "Auto backup location" (ABK:73, the phone's `autoBackupUri`, "") — INERT: the
                    // phone's SAF folder picker, its default (unset) shown; the phone's row shows a

                    // value, not a description → no toast. The phone's "location not set" warning
                    // (ABK:80) is not ported (nothing is ever set on the PC)
                    OtherSettingsEntry(
                        title = stringResource(Res.string.auto_backup_location),
                        text = stringResource(Res.string.auto_backup_location_not_set),
                        icon = Res.drawable.folder,
                        onClick = {},
                    )

                    // "Auto backup target" (ABK:84, the phone's `autoBackupTarget`, DATABASE) — INERT:
                    // the phone's selector, its default label shown; a click has no effect; the phone's

                    // row shows a value, not a description → no toast
                    OtherSettingsEntry(
                        title = stringResource(Res.string.auto_backup_target),
                        text = stringResource(Res.string.database),
                        icon = Res.drawable.server,
                        onClick = {},
                    )

                    /**
                     * NOT RENDERED ⛔ (spec §A2): the phone's "Include YouTube credentials" (ABK:102,
                     * false) — the YouTube account, Android-only.
                     *
                     * NOT RENDERED (static, spec §A2): the phone's "Include Discord credentials"
                     * (ABK:110, false), "Include Last.fm credentials" (ABK:118, false) and "Include
                     * proxy credentials" (ABK:126, false) — all only shown when the backup target is
                     * SETTINGS / BOTH / ALL, DATABASE on the phone's default (the condition is
                     * statically false on the PC).
                     */

                    // "Max backups" (ABK:137, the phone's `autoBackupMaxBackups`, 5) — INERT: the
                    // phone's numeric input, its default shown, a click has no effect; the phone's row

                    // shows a value, not a description → no toast
                    OtherSettingsEntry(
                        title = stringResource(Res.string.auto_backup_max_backups),
                        text = "5",
                        icon = Res.drawable.server,
                        onClick = {},
                    )

                    /** NOT RENDERED ⛔ (spec §A2): the phone's "Pre-install backup" (ABK:144, `autoBackupPreInstallKey`, off) — the Android APK install hook; there is no equivalent on the PC. */

                    // "Auto backup interval" (ABK:154, the phone's `autoBackupInterval`, NONE) — INERT:
                    // the phone's selector, its default label shown; a click has no effect; the phone's

                    // row shows a value, not a description → no toast
                    OtherSettingsEntry(
                        title = stringResource(Res.string.auto_backup_interval),
                        text = stringResource(Res.string.auto_backup_interval_none),
                        icon = Res.drawable.history,
                        onClick = {},
                    )

                    /**
                     * NOT RENDERED (static, spec §A2): the phone's "Custom interval value" (ABK:201,
                     * 12) and "Custom interval unit" (ABK:208, HOURS) — only shown when the interval
                     * is CUSTOM; "Schedule time" (ABK:272, 2) — only shown when it is DAILY / WEEKLY /
                     * MONTHLY; "Schedule day of week" (ABK:299, 1) — only shown when it is WEEKLY;
                     * "Schedule day of month" (ABK:340, 1) — only shown when it is MONTHLY; and
                     * "Backup on DB change" (ABK:367, off) — only shown when it is not NONE. NONE on
                     * the phone's default (no PC key): all six conditions are statically false on the
                     * PC.
                     */
                },
            )
        }

        // History Section (the phone's DS:572, spec §A2)
        AnimatedVisibility(
            visible = historyCtx,
            enter = fadeIn(animationSpec = tween(1000)) + scaleIn(animationSpec = tween(1000), initialScale = 0.9f),
        ) {
            SettingsSectionCard(
                title = stringResource(Res.string.history),
                icon = Res.drawable.history,
                content = {
                    // "Pause search history" (DS:576, `pauseSearchHistoryKey`, off by default) — INERT:
                    // the default switch state, a click has no effect, a long click toasts the phone's

                    // description
                    ToggleSettingsEntry(
                        title = stringResource(Res.string.pause_search_history),
                        text = stringResource(Res.string.neither_save_new_searched_query),
                        icon = Res.drawable.pause,
                        isChecked = false,
                        enabled = false,
                        onCheckedChange = {},
                        onLongClick = { Toaster.i(Res.string.neither_save_new_searched_query) },
                    )

                    // "Pause listen history" (DS:593, `pauseListenHistoryKey`, off by default) — INERT:
                    // the default switch state, a click has no effect, a long click toasts the phone's

                    // description
                    ToggleSettingsEntry(
                        title = stringResource(Res.string.player_pause_listen_history),
                        text = stringResource(Res.string.player_pause_listen_history_info),
                        icon = Res.drawable.pause,
                        isChecked = false,
                        enabled = false,
                        onCheckedChange = {},
                        onLongClick = { Toaster.i(Res.string.player_pause_listen_history_info) },
                    )

                    // "Clear search history" (DS:609) — INERT: the phone deletes its search DB; the PC
                    // has no search history (the phone's text at 0 queries — "History is empty" —

                    // shown), a click has no effect; the phone's row shows a count, not a description
                    // → no toast
                    OtherSettingsEntry(
                        title = stringResource(Res.string.clear_search_history),
                        text = stringResource(Res.string.history_is_empty),
                        icon = Res.drawable.trash,
                        onClick = {},
                    )

                    // "Clear listen history" (DS:627) — INERT: the phone deletes its events DB; the PC
                    // has no listen history (the phone's text at 0 events — "History is empty" —

                    // shown), a click has no effect; the phone's row shows a count, not a description
                    // → no toast
                    OtherSettingsEntry(
                        title = stringResource(Res.string.clear_listen_history),
                        text = stringResource(Res.string.history_is_empty),
                        icon = Res.drawable.trash,
                        onClick = {},
                    )
                },
            )
        }

        // Settings reset Section (the phone's DS:661, spec §A2)
        AnimatedVisibility(
            visible = settingsResetCtx,
            enter = fadeIn(animationSpec = tween(1100)) + scaleIn(animationSpec = tween(1100), initialScale = 0.9f),
        ) {
            SettingsSectionCard(
                title = stringResource(Res.string.settings_reset),
                icon = Res.drawable.refresh,
                content = {
                    // "Settings reset" (DS:668) — INERT: the phone resets ITS settings here (its
                    // `DefaultDataSettings`); on the PC a click has no effect, a long click toasts

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

private const val CACHE_SIZE_REFRESH_MS = 2_000L
