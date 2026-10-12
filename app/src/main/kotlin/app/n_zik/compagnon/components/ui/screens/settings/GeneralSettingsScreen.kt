package app.n_zik.compagnon.components.ui.screens.settings

import app.n_zik.compagnon.colorPalette
import app.n_zik.compagnon.components.settings.OtherSettingsEntry
import app.n_zik.compagnon.components.settings.SettingsDescription
import app.n_zik.compagnon.components.settings.SettingsSectionCard
import app.n_zik.compagnon.components.settings.ToggleSettingsEntry
import app.n_zik.compagnon.components.tab.Search
import app.n_zik.compagnon.components.themed.HeaderWithIcon
import app.n_zik.compagnon.components.themed.ValueSelectorDialog
import app.n_zik.compagnon.enums.CloseBehavior
import app.n_zik.compagnon.generated.resources.*
import app.n_zik.compagnon.tray.isSystemTraySupported
import app.n_zik.compagnon.utils.AppLanguage
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
import java.util.Locale
import org.jetbrains.compose.resources.stringResource

/**
 * The Général tab: the phone's `GeneralSettings.kt` (its 289-308 header / description / search and
 * the Languages card 310-348, with the PC's own "App language" entry) + the phone's 6 cards ported
 * in the phone's order between the Languages card and the PC-only cards (Window / App shortcuts) —
 * Playback (GS:362), Player controls (GS:659), Queue management (GS:735), App behavior (GS:879),
 * Audio effects (GS:995) and Settings reset (GS:1396) (spec `spec-fill-pc-ui-gaps-settings` §A1).
 *
 * The phone entries without a PC key are **inert** (the PC's inert-entry convention): their normal
 * appearance, the phone's default state / value shown, no effect on a click — a long click toasts
 * the phone's description string (the entries whose phone row shows a value instead of a
 * description have no toast, as the phone has none for them). The only wired entry is "Playlist
 * indicator" (the PC's `playlistIndicator`, aligned to the phone's off default at step 1). The
 * entries whose phone condition is false on the phone's defaults, or Android-only (⛔), are not
 * rendered — a KDoc at the site names the phone entry (file:line) and the reason; a section with no
 * rendered entry (Gestures & events, Picture in picture, App shortcuts) is not rendered either.
 */
@Composable
fun GeneralSettingsScreen(preferences: Preferences, query: String, onQuery: (String) -> Unit) {
    val settings by preferences.settings.collectAsState()
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showCloseBehaviorDialog by remember { mutableStateOf(false) }
    var showShortcutsDialog by remember { mutableStateOf(false) }
    val search = Search(query, onQuery, lazyListState = null)
    val languagesCtx = settingsSearchCtx(
        search.inputValue,
        stringResource(Res.string.languages),
        stringResource(Res.string.app_language),
    )
    // The phone cards' search ctx (spec §A1): the card's title + its rendered entries' titles
    val playbackCtx = settingsSearchCtx(
        search.inputValue,
        stringResource(Res.string.playback),
        stringResource(Res.string.jump_previous),
        stringResource(Res.string.min_listening_time),
        stringResource(Res.string.exclude_songs_with_duration_limit),
        stringResource(Res.string.exclude_disliked_songs),
        stringResource(Res.string.exclude_disliked_artists),
        stringResource(Res.string.exclude_disliked_albums),
        stringResource(Res.string.pause_between_songs),
        stringResource(Res.string.crossfade),
    )
    val playerControlsCtx = settingsSearchCtx(
        search.inputValue,
        stringResource(Res.string.player_controls),
        stringResource(Res.string.player_pause_on_volume_zero),
        stringResource(Res.string.player_keep_minimized),
        stringResource(Res.string.player_collapsed_disable_swiping_down),
        stringResource(Res.string.player_auto_load_songs_in_queue),
    )
    val queueManagementCtx = settingsSearchCtx(
        search.inputValue,
        stringResource(Res.string.queue_management),
        stringResource(Res.string.max_songs_in_queue),
        stringResource(Res.string.discover),
        stringResource(Res.string.playlistindicator),
        stringResource(Res.string.now_playing_indicator),
    )
    val appBehaviorCtx = settingsSearchCtx(
        search.inputValue,
        stringResource(Res.string.app_behavior),
        stringResource(Res.string.persistent_queue),
        stringResource(Res.string.skip_media_on_error),
    )
    val audioEffectsCtx = settingsSearchCtx(
        search.inputValue,
        stringResource(Res.string.audio_effects),
        stringResource(Res.string.skip_silence),
        stringResource(Res.string.loudness_normalization),
        stringResource(Res.string.settings_audio_bass_boost),
        stringResource(Res.string.settings_audio_reverb),
    )
    val settingsResetCtx = settingsSearchCtx(search.inputValue, stringResource(Res.string.settings_reset))
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

        // ===== The phone's cards (spec `spec-fill-pc-ui-gaps-settings` §A1), in the phone's order =====

        // Playback Section (the phone's GS:362, spec §A1)
        AnimatedVisibility(
            visible = playbackCtx,
            enter = fadeIn(animationSpec = tween(800)) + scaleIn(animationSpec = tween(800), initialScale = 0.9f),
        ) {
            SettingsSectionCard(
                title = stringResource(Res.string.playback),
                icon = Res.drawable.play_forward,
                content = {
                    // "Jump previous" (GS:369, `jumpPreviousKey`, "3"): the phone's numeric input — INERT
                    // on the PC (no PC key): the default value shown, no dialog, a click has no effect;
                    // the phone's row shows a value, not a description → no long-press toast
                    OtherSettingsEntry(
                        title = stringResource(Res.string.jump_previous),
                        text = "3",
                        icon = Res.drawable.play_skip_back,
                        onClick = {},
                    )

                    // "Min listening time" (GS:395, `exoPlayerMinTimeForEventKey`, 20s) — INERT: the
                    // phone's default value shown, no dialog, a click has no effect; no description →
                    // no toast
                    OtherSettingsEntry(
                        title = stringResource(Res.string.min_listening_time),
                        text = "20s",
                        icon = Res.drawable.time,
                        onClick = {},
                    )

                    // "Exclude songs with duration limit" (GS:432, `DurationInMinutes.Disabled`) — INERT:
                    // the default (Disabled) shown, no dialog, a click has no effect; no description →
                    // no toast
                    OtherSettingsEntry(
                        title = stringResource(Res.string.exclude_songs_with_duration_limit),
                        text = stringResource(Res.string.vt_disabled),
                        icon = Res.drawable.playbackduration,
                        onClick = {},
                    )

                    // The phone's Disliked exclusions (GS:476 / 503 / 530, `DislikeMode.Enabled` by
                    // default) — INERT: the default (On) shown, no dialog, a click has no effect. The
                    // phone's live `dislikeMode` display is not ported (contract read-only, spec
                    // decision); the phone's row shows a value, not a description → no toast
                    OtherSettingsEntry(
                        title = stringResource(Res.string.exclude_disliked_songs),
                        text = stringResource(Res.string.on),
                        icon = Res.drawable.heart_dislike,
                        onClick = {},
                    )
                    OtherSettingsEntry(
                        title = stringResource(Res.string.exclude_disliked_artists),
                        text = stringResource(Res.string.on),
                        icon = Res.drawable.person,
                        onClick = {},
                    )
                    OtherSettingsEntry(
                        title = stringResource(Res.string.exclude_disliked_albums),
                        text = stringResource(Res.string.on),
                        icon = Res.drawable.album,
                        onClick = {},
                    )

                    // "Pause between songs" (GS:557, `PauseBetweenSongs.0`) — INERT: the default (0s)
                    // shown, no dialog, a click has no effect; no description → no toast
                    OtherSettingsEntry(
                        title = stringResource(Res.string.pause_between_songs),
                        text = "0s",
                        icon = Res.drawable.pause,
                        onClick = {},
                    )

                    // "Crossfade" (GS:599, `crossfadeEnabled`, off by default) — INERT: the default
                    // switch state, a click has no effect, a long click toasts the phone's description
                    ToggleSettingsEntry(
                        title = stringResource(Res.string.crossfade),
                        text = stringResource(Res.string.crossfade_description),
                        icon = Res.drawable.shuffle,
                        isChecked = false,
                        enabled = false,
                        onCheckedChange = {},
                        onLongClick = { Toaster.i(Res.string.crossfade_description) },
                    )

                    /**
                     * NOT RENDERED (static, spec §A1): the phone's "Crossfade duration" (GS:614,
                     * `crossfadeDuration`, 3000) and "Crossfade gapless" (GS:634, `crossfadeGapless`,
                     * off) — both only shown when `crossfadeEnabled`, false on the phone's default and
                     * with no PC key (the condition is statically false on the PC).
                     */
                },
            )
        }

        // Player controls Section (the phone's GS:659, spec §A1)
        AnimatedVisibility(
            visible = playerControlsCtx,
            enter = fadeIn(animationSpec = tween(900)) + scaleIn(animationSpec = tween(900), initialScale = 0.9f),
        ) {
            SettingsSectionCard(
                title = stringResource(Res.string.player_controls),
                icon = Res.drawable.player_control,
                content = {
                    // "Pause on volume zero" (GS:664, `isPauseOnVolumeZeroEnabled`, off by default) —
                    // INERT: the default switch state, a click has no effect, a long click toasts the
                    // phone's description
                    ToggleSettingsEntry(
                        title = stringResource(Res.string.player_pause_on_volume_zero),
                        text = stringResource(Res.string.info_pauses_player_when_volume_zero),
                        icon = Res.drawable.volume_up,
                        isChecked = false,
                        enabled = false,
                        onCheckedChange = {},
                        onLongClick = { Toaster.i(Res.string.info_pauses_player_when_volume_zero) },
                    )

                    /**
                     * NOT RENDERED ⛔ (spec §A1): the phone's "Pause on headphone disconnect" (GS:675,
                     * `pauseOnHeadphoneDisconnect`, on by default) — an Android BroadcastReceiver
                     * (the headphone-unplug event); there is no equivalent on the PC.
                     */

                    // "Keep player minimized" (GS:686, `keepPlayerMinimized`, on by default) — INERT:
                    // the default switch state, a click has no effect, a long click toasts the
                    // phone's description
                    ToggleSettingsEntry(
                        title = stringResource(Res.string.player_keep_minimized),
                        text = stringResource(Res.string.when_click_on_a_song_player_start_minimized),
                        icon = Res.drawable.maximize,
                        isChecked = true,
                        enabled = false,
                        onCheckedChange = {},
                        onLongClick = { Toaster.i(Res.string.when_click_on_a_song_player_start_minimized) },
                    )

                    // "Disable closing player by swiping down" (GS:697, `disableClosingPlayerSwipingDown`,
                    // off by default) — INERT: the default switch state, a click has no effect, a long
                    // click toasts the phone's description
                    ToggleSettingsEntry(
                        title = stringResource(Res.string.player_collapsed_disable_swiping_down),
                        text = stringResource(Res.string.avoid_closing_the_player_cleaning_queue_by_swiping_down),
                        icon = Res.drawable.reorder,
                        isChecked = false,
                        enabled = false,
                        onCheckedChange = {},
                        onLongClick = { Toaster.i(Res.string.avoid_closing_the_player_cleaning_queue_by_swiping_down) },
                    )

                    // "Auto load songs in queue" (GS:708, `autoLoadSongsInQueue`, on by default) —
                    // INERT: the default switch state, a click has no effect, a long click toasts the
                    // phone's description
                    ToggleSettingsEntry(
                        title = stringResource(Res.string.player_auto_load_songs_in_queue),
                        text = stringResource(Res.string.player_auto_load_songs_in_queue_description),
                        icon = Res.drawable.playlist,
                        isChecked = true,
                        enabled = false,
                        onCheckedChange = {},
                        onLongClick = { Toaster.i(Res.string.player_auto_load_songs_in_queue_description) },
                    )
                },
            )
        }

        // Queue management Section (the phone's GS:735, spec §A1)
        AnimatedVisibility(
            visible = queueManagementCtx,
            enter = fadeIn(animationSpec = tween(1000)) + scaleIn(animationSpec = tween(1000), initialScale = 0.9f),
        ) {
            SettingsSectionCard(
                title = stringResource(Res.string.queue_management),
                icon = Res.drawable.playlist,
                content = {
                    // "Max songs in queue" (GS:742, `MaxSongs.Unlimited`) — INERT: the default (Unlimited)
                    // shown, no dialog, a click has no effect (the cap is applied on the phone's side,
                    // contract §9); no description → no toast
                    OtherSettingsEntry(
                        title = stringResource(Res.string.max_songs_in_queue),
                        text = stringResource(Res.string.unlimited),
                        icon = Res.drawable.music_file,
                        onClick = {},
                    )

                    /**
                     * NOT RENDERED ⛔ (spec §A1): the phone's "Max songs in queue (Android Auto)"
                     * (GS:759, `maxSongsInQueueAndroidAuto`, Unlimited) — Android Auto only.
                     */

                    // "Discover" (GS:825, `discoverIsEnabled`, off by default) — INERT: no PC key, the
                    // default switch state, a click has no effect, a long click toasts the phone's
                    // description (the same toast the inert Discover button of the mini-player uses)
                    ToggleSettingsEntry(
                        title = stringResource(Res.string.discover),
                        text = stringResource(Res.string.discoverinfo),
                        icon = Res.drawable.search,
                        isChecked = false,
                        enabled = false,
                        onCheckedChange = {},
                        onLongClick = { Toaster.i(Res.string.discoverinfo) },
                    )

                    // "Playlist indicator" (GS:834, the phone's `playlistindicator`, off by default) —
                    // WIRED to the PC's `playlistIndicator` (step 1 aligned the PC default to the
                    // phone's off): persisted, the song rows' "in a playlist" badge follows
                    ToggleSettingsEntry(
                        title = stringResource(Res.string.playlistindicator),
                        text = stringResource(Res.string.playlistindicatorinfo),
                        icon = Res.drawable.playlist,
                        isChecked = settings.playlistIndicator,
                        onCheckedChange = {
                            preferences.update { s -> s.copy(playlistIndicator = it) }
                        },
                    )

                    // "Now playing indicator" (GS:846, `nowPlayingIndicator`, `MusicAnimationType.Bubbles`)
                    // — INERT: the phone's default label (Bubbles) shown, no dialog, a click has no
                    // effect; no description → no toast
                    OtherSettingsEntry(
                        title = stringResource(Res.string.now_playing_indicator),
                        text = stringResource(Res.string.music_animations_bubbles),
                        icon = Res.drawable.playing_indicator,
                        onClick = {},
                    )
                },
            )
        }

        // App behavior Section (the phone's GS:879, spec §A1)
        AnimatedVisibility(
            visible = appBehaviorCtx,
            enter = fadeIn(animationSpec = tween(1100)) + scaleIn(animationSpec = tween(1100), initialScale = 0.9f),
        ) {
            SettingsSectionCard(
                title = stringResource(Res.string.app_behavior),
                icon = Res.drawable.settings,
                content = {
                    /**
                     * NOT RENDERED ⛔ (spec §A1): the phone's "Resume playback when device connected"
                     * (GS:886, `resumePlaybackWhenDeviceConnected`, off; condition
                     * `isAtLeastAndroid6`) — the Android "device connected" broadcast; there is no
                     * equivalent on the PC.
                     */

                    // "Persistent queue" (GS:902, `persistentQueue`, off by default) — INERT: the
                    // default switch state, a click has no effect, a long click toasts the phone's
                    // description
                    ToggleSettingsEntry(
                        title = stringResource(Res.string.persistent_queue),
                        text = stringResource(Res.string.save_and_restore_playing_songs),
                        icon = Res.drawable.download,
                        isChecked = false,
                        enabled = false,
                        onCheckedChange = {},
                        onLongClick = { Toaster.i(Res.string.save_and_restore_playing_songs) },
                    )

                    /**
                     * NOT RENDERED (static, spec §A1): the phone's "Resume playback on start" (GS:919,
                     * `resumePlaybackOnStart`, off) — only shown when `persistentQueue`, off on the
                     * phone's default and with no PC key (the condition is statically false on the PC).
                     *
                     * NOT RENDERED ⛔ (spec §A1): the phone's "Close app with back button" (GS:938,
                     * `closeWithBackButton`, on; condition `SDK_INT >= 33`) and "Close background
                     * player" (GS:954, `closebackgroundPlayer`, off) — the Android back-button /
                     * task-swipe behaviors; there is no equivalent on the PC.
                     */

                    // "Skip media on error" (GS:968, `skipMediaOnError`, off by default) — INERT: the
                    // default switch state, a click has no effect, a long click toasts the phone's
                    // description
                    ToggleSettingsEntry(
                        title = stringResource(Res.string.skip_media_on_error),
                        text = stringResource(Res.string.skip_media_on_error_description),
                        icon = Res.drawable.alert_circle,
                        isChecked = false,
                        enabled = false,
                        onCheckedChange = {},
                        onLongClick = { Toaster.i(Res.string.skip_media_on_error_description) },
                    )
                },
            )
        }

        // Audio effects Section (the phone's GS:995, spec §A1)
        AnimatedVisibility(
            visible = audioEffectsCtx,
            enter = fadeIn(animationSpec = tween(1200)) + scaleIn(animationSpec = tween(1200), initialScale = 0.9f),
        ) {
            SettingsSectionCard(
                title = stringResource(Res.string.audio_effects),
                icon = Res.drawable.sound_effect,
                content = {
                    // "Skip silence" (GS:1000, `skipSilence`, off by default) — INERT: the default switch
                    // state, a click has no effect, a long click toasts the phone's description
                    ToggleSettingsEntry(
                        title = stringResource(Res.string.skip_silence),
                        text = stringResource(Res.string.skip_silent_parts_during_playback),
                        icon = Res.drawable.pause,
                        isChecked = false,
                        enabled = false,
                        onCheckedChange = {},
                        onLongClick = { Toaster.i(Res.string.skip_silent_parts_during_playback) },
                    )

                    /**
                     * NOT RENDERED (static, spec §A1): the phone's "Minimum silence length" (GS:1018,
                     * `minimumSilenceDuration`, 2 000 000 ms slider) — only shown when `skipSilence`,
                     * off on the phone's default and with no PC key (the condition is statically
                     * false on the PC).
                     */

                    // "Loudness normalization" (GS:1043, `volumeNormalization`, off by default) — INERT:
                    // the default switch state, a click has no effect, a long click toasts the
                    // phone's description
                    ToggleSettingsEntry(
                        title = stringResource(Res.string.loudness_normalization),
                        text = stringResource(Res.string.autoadjust_the_volume),
                        icon = Res.drawable.volume_up,
                        isChecked = false,
                        enabled = false,
                        onCheckedChange = {},
                        onLongClick = { Toaster.i(Res.string.autoadjust_the_volume) },
                    )

                    /**
                     * NOT RENDERED (static, spec §A1): the phone's "Loudness base gain" (GS:1063,
                     * `loudnessBaseGain`, 5.00f) and "Loudness boost level" (GS:1103,
                     * `volumeBoostLevel`, 0f) — only shown when `volumeNormalization`, off on the
                     * phone's default and with no PC key (the condition is statically false on the PC).
                     */

                    // "Bass boost" (GS:1146, `bassboostEnabled`, off by default) — INERT: the default
                    // switch state, a click has no effect; the phone's row has no description text →
                    // no toast
                    ToggleSettingsEntry(
                        title = stringResource(Res.string.settings_audio_bass_boost),
                        text = "",
                        icon = Res.drawable.equalizer,
                        isChecked = false,
                        enabled = false,
                        onCheckedChange = {},
                    )

                    /**
                     * NOT RENDERED (static, spec §A1): the phone's "Bass boost level" (GS:1163,
                     * `bassboostLevel`, 0f slider) — only shown when `bassboostEnabled`, off on the
                     * phone's default and with no PC key (the condition is statically false on the PC).
                     */

                    // "Audio reverb" (GS:1206, `audioReverb`, `PresetsReverb.NONE`) — INERT: the phone's
                    // default label (None) shown, no dialog, a click has no effect; no description →
                    // no toast
                    OtherSettingsEntry(
                        title = stringResource(Res.string.settings_audio_reverb),
                        text = stringResource(Res.string.reverb_none),
                        icon = Res.drawable.reverb,
                        onClick = {},
                    )

                    /**
                     * NOT RENDERED ⛔ (spec §A1): the phone's "Audio focus" (GS:1230,
                     * `audioFocusEnabled`, on) and "Equalizer" (GS:1242, no key) — the Android audio-
                     * focus management and the system equalizer intent; there is no equivalent on the
                     * PC (the PC only plays whatever the phone's server sends).
                     */
                },
            )
        }

        /**
         * NOT RENDERED (no rendered entry, spec §A1): the phone's sections "Gestures & events"
         * (GS:1263 — its only entry, the volume keys, GS:1268, ⛔ the Android volume-key broadcast),
         * "Picture in picture" (GS:1295 — GS:1300 / 1317 / 1347, ⛔ the Android PiP API) and "App
         * shortcuts" (GS:1374 — GS:1379, ⛔ the launcher's app shortcuts). A section with no rendered
         * entry is not rendered; the PC keeps its own "App shortcuts" (tray) card below.
         */

        // Settings reset Section (the phone's GS:1396, spec §A1)
        AnimatedVisibility(
            visible = settingsResetCtx,
            enter = fadeIn(animationSpec = tween(1100)) + scaleIn(animationSpec = tween(1100), initialScale = 0.9f),
        ) {
            SettingsSectionCard(
                title = stringResource(Res.string.settings_reset),
                icon = Res.drawable.refresh,
                content = {
                    // "Settings reset" (GS:1403) — INERT: the phone resets ITS settings here (its
                    // `DefaultGeneralSettings`); on the PC a click has no effect, a long click toasts
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

        // ===== The PC-only cards (the phone has no equivalent; they stay after the phone's cards) =====

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

        // The app shortcuts (the phone's General « App shortcuts » card): the tray menu's shortcuts —
        // hidden where no tray exists
        val shortcutsCtx = settingsSearchCtx(search.inputValue, stringResource(Res.string.app_shortcuts_settings))
        if (traySupported) {
            AnimatedVisibility(
                visible = shortcutsCtx,
                enter = fadeIn(animationSpec = tween(1050)) + scaleIn(animationSpec = tween(1050), initialScale = 0.9f),
            ) {
                SettingsSectionCard(
                    title = stringResource(Res.string.app_shortcuts_settings),
                    icon = Res.drawable.shortcut_rescue,
                    content = {
                        OtherSettingsEntry(
                            title = stringResource(Res.string.app_shortcuts_settings),
                            text = stringResource(Res.string.app_shortcuts_settings_description),
                            icon = Res.drawable.shortcut_rescue,
                            onClick = { showShortcutsDialog = true },
                        )
                    },
                )
            }
        }

        if (showShortcutsDialog) {
            AppShortcutsDialog(preferences, onDismiss = { showShortcutsDialog = false })
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
