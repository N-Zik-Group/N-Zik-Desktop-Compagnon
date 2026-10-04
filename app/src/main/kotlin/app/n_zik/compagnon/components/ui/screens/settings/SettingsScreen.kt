package app.n_zik.compagnon.components.ui.screens.settings

import app.n_zik.compagnon.components.ui.screens.bridge.OverlayPanel
import app.n_zik.compagnon.components.themed.CacheSpaceIndicator
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import app.n_zik.compagnon.colorPalette
import app.n_zik.compagnon.components.settings.CacheSettingsEntry
import app.n_zik.compagnon.components.settings.OtherSettingsEntry
import app.n_zik.compagnon.components.settings.ToggleSettingsEntry
import app.n_zik.compagnon.components.settings.SettingsDescription
import app.n_zik.compagnon.components.settings.SettingsSectionCard
import app.n_zik.compagnon.components.themed.ConfirmationDialog
import app.n_zik.compagnon.components.themed.HeaderWithIcon
import app.n_zik.compagnon.components.themed.InputNumericDialog
import app.n_zik.compagnon.components.themed.ValueSelectorDialog
import app.n_zik.compagnon.components.ui.screens.bridge.PairingButton
import app.n_zik.compagnon.enums.AudioQualityFormat
import app.n_zik.compagnon.enums.ExoPlayerDiskCacheMaxSize
import app.n_zik.compagnon.generated.resources.Res
import app.n_zik.compagnon.generated.resources.audio_quality
import app.n_zik.compagnon.generated.resources.audio_quality_automatic
import app.n_zik.compagnon.generated.resources.audio_quality_format
import app.n_zik.compagnon.generated.resources.audio_quality_format_high
import app.n_zik.compagnon.generated.resources.audio_quality_format_low
import app.n_zik.compagnon.generated.resources.cache
import app.n_zik.compagnon.generated.resources.cache_cleared
import app.n_zik.compagnon.generated.resources.close
import app.n_zik.compagnon.generated.resources.custom
import app.n_zik.compagnon.generated.resources.data_settings_description
import app.n_zik.compagnon.generated.resources.disable_scrolling_text
import app.n_zik.compagnon.generated.resources.do_you_really_want_to_delete_cache
import app.n_zik.compagnon.generated.resources.enter_value_in_mb
import app.n_zik.compagnon.generated.resources.music_file
import app.n_zik.compagnon.generated.resources.other
import app.n_zik.compagnon.generated.resources.quality
import app.n_zik.compagnon.generated.resources.scrolling_text_is_used_for_long_texts
import app.n_zik.compagnon.generated.resources.server
import app.n_zik.compagnon.generated.resources.set_custom_cache
import app.n_zik.compagnon.generated.resources.settings
import app.n_zik.compagnon.generated.resources.song_cache_max_size
import app.n_zik.compagnon.generated.resources.speaker
import app.n_zik.compagnon.generated.resources.text
import app.n_zik.compagnon.generated.resources.turn_off
import app.n_zik.compagnon.generated.resources.unlimited
import app.n_zik.compagnon.generated.resources.used
import app.n_zik.compagnon.playback.cache.AudioCache
import app.n_zik.compagnon.utils.Preferences
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource

/**
 * The Compagnon's settings, opened from the header's settings icon as an overlay (like the "Phone" panel).
 * Only what applies to the PC is kept, its cards ported from the phone:
 * - "Cache" with "Song cache max size" (phone's `DataSettings.kt` 364-412: `CacheSettingsEntry`,
 *   `ValueSelectorDialog`, the custom size dialog and the "used" line), here the local audio cache;
 * - "Quality" with "Audio Quality" (phone's `NetworkSettings.kt` 248-266 and its dialog 294-310), the
 *   quality asked when the PC forges an audio URL;
 * - "Others" with "Disable scrolling text" (phone's `OtherSwitchSettingEntry`, its
 *   `ui/screens/settings/SettingsScreen.kt` 277-367), a Compagnon-local setting: it drops the `SongItem`
 *   title / artists marquee.
 * The song cache shows the phone's `CacheSpaceIndicator` (`DataSettings.kt` 410, 20 dp sides, no info line)
 * of the local audio cache, then the "used" line. The cards follow each other without extra spacing (the
 * phone's settings column).
 * Dropped: the phone's other settings (image cache, downloads, other qualities, search…) and the player
 * service restart.
 */
@Composable
fun SettingsScreen(
    preferences: Preferences,
    cache: AudioCache?,
    onClose: () -> Unit,
) {
    val settings by preferences.settings.collectAsState()
    val scope = rememberCoroutineScope()
    var showSongCacheDialog by remember { mutableStateOf(false) }
    var showExoPlayerCustomCacheDialog by remember { mutableStateOf(false) }
    var showAudioQualityDialog by remember { mutableStateOf(false) }
    var cleanCacheOfflineSongs by remember { mutableStateOf(false) }
    var cacheCleanedCounter by remember { mutableIntStateOf(0) }

    val exoPlayerDiskCacheMaxSize = settings.exoPlayerDiskCacheMaxSize
    val exoPlayerCustomCache = settings.exoPlayerCustomCache
    val audioQualityFormat = settings.audioQualityFormat

    // The cache fills while a track plays: its size is read again every few seconds, off the UI thread
    val diskCacheSize by produceState(0L, cache, cacheCleanedCounter) {
        while (true) {
            value = cache?.let { withContext(Dispatchers.IO) { it.totalBytes() } } ?: 0L
            delay(CACHE_SIZE_REFRESH_MS)
        }
    }

    fun trimCache() {
        scope.launch { withContext(Dispatchers.IO) { cache?.trim() } }
    }

    if (cleanCacheOfflineSongs) {
        ConfirmationDialog(
            text = stringResource(Res.string.do_you_really_want_to_delete_cache),
            onDismiss = { cleanCacheOfflineSongs = false },
            onConfirm = {
                cleanCacheOfflineSongs = false
                scope.launch {
                    withContext(Dispatchers.IO) { cache?.clear() }
                    cacheCleanedCounter++
                }
            },
        )
    }

    OverlayPanel(onClose = onClose) {
        run {
            HeaderWithIcon(
                title = stringResource(Res.string.settings),
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

            // Cache Section
            SettingsSectionCard(
                title = stringResource(Res.string.cache),
                icon = Res.drawable.server,
                description = stringResource(Res.string.cache_cleared),
                content = {
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
                                preferences.update { settings -> settings.copy(exoPlayerDiskCacheMaxSize = it) }
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
                                preferences.update { settings -> settings.copy(exoPlayerCustomCache = it.toInt()) }
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
                            if (maxBytes != null && maxBytes > 0) "${diskCacheSize * 100 / maxBytes}%" else stringResource(Res.string.unlimited)
                        })",
                    )
                },
            )

            // Quality Settings Section
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

            // Others Section (the PC's "Disable scrolling text" — a Compagnon-local setting, not in the contract)
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

            if (showAudioQualityDialog) {
                ValueSelectorDialog(
                    title = stringResource(Res.string.audio_quality_format),
                    values = AudioQualityFormat.entries.toList(),
                    selectedValue = audioQualityFormat,
                    onValueSelected = {
                        preferences.update { settings -> settings.copy(audioQualityFormat = it) }
                        showAudioQualityDialog = false
                    },
                    onDismiss = { showAudioQualityDialog = false },
                    valueText = { stringResource(it.textId) },
                )
            }

            PairingButton(
                text = stringResource(Res.string.close),
                onClick = onClose,
                containerColor = colorPalette().background2,
                contentColor = colorPalette().text,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
    }
}

/** Android's `Formatter.formatShortFileSize`: decimal units, one decimal under 100 (`"1.2 GB"`, `"345 MB"`). */
fun formatShortFileSize(bytes: Long): String {
    if (bytes < 1_000) return "$bytes B"
    val units = listOf("kB", "MB", "GB", "TB")
    var value = bytes.toDouble() / 1_000
    var unit = 0
    while (value >= 1_000 && unit < units.lastIndex) {
        value /= 1_000
        unit++
    }
    val text = if (value < 100) String.format(Locale.ROOT, "%.1f", value) else String.format(Locale.ROOT, "%.0f", value)
    return "$text ${units[unit]}"
}

private const val CACHE_SIZE_REFRESH_MS = 2_000L
