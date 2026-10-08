package app.n_zik.compagnon.components.ui.screens.settings

import app.n_zik.compagnon.colorPalette
import app.n_zik.compagnon.components.settings.CacheSettingsEntry
import app.n_zik.compagnon.components.settings.SettingsDescription
import app.n_zik.compagnon.components.settings.SettingsSectionCard
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
 * The Données tab (phone's `DataSettings.kt` 255-276 + its song-cache card): the tab-style header,
 * the description, the search, and the **Cache** card — "Song cache max size" (its
 * [CacheSettingsEntry], its [ValueSelectorDialog], the custom size dialog, its [CacheSpaceIndicator]
 * of the local audio cache and the "used" line, all unchanged).
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
    val cacheCtx = settingsSearchCtx(
        search.inputValue,
        stringResource(Res.string.cache),
        stringResource(Res.string.song_cache_max_size),
    )

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

        // Cache Section
        AnimatedVisibility(
            visible = cacheCtx,
            enter = fadeIn(animationSpec = tween(600)) + scaleIn(animationSpec = tween(600), initialScale = 0.9f),
        ) {
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
                            if (maxBytes != null && maxBytes > 0) "${diskCacheSize * 100 / maxBytes}%" else stringResource(Res.string.unlimited)
                        })",
                    )
                },
            )
        }
    }
}

private const val CACHE_SIZE_REFRESH_MS = 2_000L
