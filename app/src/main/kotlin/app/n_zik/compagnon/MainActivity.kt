package app.n_zik.compagnon

import androidx.compose.animation.AnimatedContent
import app.n_zik.compagnon.components.navigation.pageTransition
import app.n_zik.compagnon.components.navigation.BarsScrollHide
import androidx.compose.ui.ExperimentalComposeUiApi
import app.n_zik.compagnon.core.navigation.backStep
import app.n_zik.compagnon.core.navigation.LocalBackDispatcher
import app.n_zik.compagnon.core.navigation.BackStep
import app.n_zik.compagnon.components.ui.screens.home.CollectionHeader
import app.n_zik.compagnon.components.player.APP_HEADER_HEIGHT
import app.n_zik.compagnon.components.player.MINIPLAYER_APPEAR_FADE_MS
import app.n_zik.compagnon.components.player.MINIPLAYER_APPEAR_FADE_SKIPPED_FRAMES
import app.n_zik.compagnon.components.player.MINIPLAYER_POP_ANIMATION_MS
import kotlinx.coroutines.Job
import kotlin.math.roundToInt
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.layout
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.geometry.Offset
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.State
import androidx.compose.runtime.DisposableEffect
import androidx.compose.foundation.layout.offset
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import app.n_zik.compagnon.bridge.ConnectionState
import app.n_zik.compagnon.bridge.library.LibraryRepository
import app.n_zik.compagnon.bridge.pairing.PairingRecord
import app.n_zik.compagnon.bridge.state.PlayerNotice
import app.n_zik.compagnon.bridge.state.PlayerRepository
import app.n_zik.compagnon.bridge.state.SessionContract
import app.n_zik.compagnon.components.BottomSheetMenu
import app.n_zik.compagnon.components.LocalMenuState
import app.n_zik.compagnon.components.MenuState
import app.n_zik.compagnon.components.player.MiniPlayer
import app.n_zik.compagnon.components.player.MiniPlayerQueueOverlay
import app.n_zik.compagnon.components.player.PLAYER_ARTWORK_SIZE_PX
import app.n_zik.compagnon.components.player.PaletteFade
import app.n_zik.compagnon.components.player.Player
import app.n_zik.compagnon.components.player.PlayerSheet
import app.n_zik.compagnon.components.player.VIOLET_ACCENT
import app.n_zik.compagnon.components.player.m3eDynamicColorPaletteOf
import app.n_zik.compagnon.components.styling.Dimensions
import app.n_zik.compagnon.components.theme.Appearance
import app.n_zik.compagnon.components.theme.BoundedCornerSize
import app.n_zik.compagnon.components.theme.colorPaletteOf
import app.n_zik.compagnon.components.theme.dynamicColorPaletteOf
import app.n_zik.compagnon.components.theme.typographyOf
import app.n_zik.compagnon.components.theme.withColor
import app.n_zik.compagnon.components.ui.header.AppHeader
import app.n_zik.compagnon.components.ui.screens.bridge.ConnectionBanner
import app.n_zik.compagnon.components.ui.screens.bridge.PhonePanel
import app.n_zik.compagnon.components.ui.screens.bridge.noticeText
import app.n_zik.compagnon.components.ui.screens.bridge.phoneToastType
import app.n_zik.compagnon.components.ui.screens.home.HomeScreen
import app.n_zik.compagnon.components.ui.screens.home.LibraryActions
import app.n_zik.compagnon.components.ui.screens.home.LibraryLists
import app.n_zik.compagnon.core.network.ArtworkKey
import app.n_zik.compagnon.core.palette.toPaletteBitmap
import app.n_zik.compagnon.enums.ColorPaletteMode
import app.n_zik.compagnon.bridge.state.UiSettings
import app.n_zik.compagnon.bridge.state.LocalUiSettings
import app.n_zik.compagnon.components.theme.ColorPalette
import app.n_zik.compagnon.components.theme.applyPitchBlack
import app.n_zik.compagnon.enums.ColorPaletteName
import app.n_zik.compagnon.utils.Toaster
import app.n_zik.compagnon.utils.coroutines.NzikDispatchers
import app.n_zik.compagnon.utils.formatMessage
import app.n_zik.compagnon.components.ui.screens.settings.SettingsScreen
import app.n_zik.compagnon.components.ui.screens.settings.UpdateScreen
import app.n_zik.compagnon.components.ui.screens.settings.cancelDownloadOnBack
import app.n_zik.compagnon.playback.cache.AudioCache
import app.n_zik.compagnon.playback.services.LocalPlayback
import app.n_zik.compagnon.playback.services.LocalPlaybackNotice
import app.n_zik.compagnon.utils.LocalPreferences
import app.n_zik.compagnon.generated.resources.*
import org.jetbrains.compose.resources.getString

/**
 * The phone's `appearance` / `fadeFromAppearance` state of `MainActivity.setContent`
 * (`app/n_zik/android/MainActivity.kt` 1024-1066): the appearance shown by `AnimatedAppearance`, and the one
 * it fades from when a change is animated.
 */
@Stable
class AppearanceState(initial: Appearance) {
    var appearance by mutableStateOf(initial)
    var fadeFromAppearance by mutableStateOf<Appearance?>(null)

    /**
     * The PC's effective UI settings ([MainActivity]: its coded defaults plus the phone's `topN` once
     * read), for the dialogs drawn at the window's root, outside its [LocalUiSettings] provider (the
     * updater's, `Main.kt`): their PitchBlack background.
     */
    var uiSettings by mutableStateOf(UiSettings())

    fun updateAppearance(newAppearance: Appearance, animateGlobal: Boolean = false) {
        if (animateGlobal) fadeFromAppearance = appearance
        appearance = newAppearance
    }

    /**
     * Port of `setDynamicPalette` (`MainActivity.kt` 1170-1300): with the phone's `Dynamic` palette, the
     * palette of the current track's cover ([bitmap], read through the phone), or the dynamic palette of
     * N-Zik's violet when there is no cover, in the phone's mode — dark for `Dark` / `PitchBlack` / a dark
     * `System`, with the `PitchBlack` black backgrounds. Since contract 1.10.0 the palette name and mode
     * are the phone's ([settings], `ui.settings`); another palette name keeps the static palette of
     * [computeAppearance] (the phone returns early). The global palette changes in one step; the player's
     * [PaletteFade] fades it.
     */
    suspend fun setDynamicPalette(
        bitmap: ImageBitmap?,
        settings: UiSettings = UiSettings(),
        isSystemInDarkTheme: Boolean = true,
        animateTheme: Boolean = false,
    ) {
        val name = ColorPaletteName.entries.firstOrNull { it.name == settings.colorPaletteName } ?: ColorPaletteName.Dynamic
        val mode = ColorPaletteMode.entries.firstOrNull { it.name == settings.colorPaletteMode } ?: ColorPaletteMode.Dark
        if (name != ColorPaletteName.Dynamic) {
            val static = staticColorPaletteOf(settings, isSystemInDarkTheme)
            if (appearance.colorPalette != static) {
                updateAppearance(appearance.copy(colorPalette = static, typography = appearance.typography.withColor(static.text)), animateTheme)
            }
            return
        }
        val isPitchBlack = mode == ColorPaletteMode.PitchBlack
        val isDark = mode == ColorPaletteMode.Dark || isPitchBlack || (mode == ColorPaletteMode.System && isSystemInDarkTheme)
        val finalPalette = if (bitmap == null) {
            null
        } else {
            withContext(NzikDispatchers.MEDIA) { m3eDynamicColorPaletteOf(bitmap.toPaletteBitmap(), isDark) }
        }
        val targetPalette = when {
            // The phone's cover palette in PitchBlack: black backgrounds, white text, dark
            finalPalette != null && isPitchBlack -> finalPalette.applyPitchBlack
            finalPalette != null -> finalPalette
            else -> dynamicColorPaletteOf(VIOLET_ACCENT, isDark).let { violet ->
                // The phone's violet fallback in PitchBlack: black backgrounds only
                if (!isPitchBlack) violet else violet.copy(
                    background0 = Color.Black, background1 = Color.Black, background2 = Color.Black,
                    background3 = Color.Black, background4 = Color.Black,
                )
            }
        }
        if (appearance.colorPalette == targetPalette) return
        updateAppearance(
            appearance.copy(
                colorPalette = targetPalette,
                typography = appearance.typography.withColor(targetPalette.text),
            ),
            animateTheme,
        )
    }
}

/**
 * The phone's static palette of `computeAppearance` (`MainActivity.kt` 1095-1111) for its palette name and
 * mode (contract 1.10.0, `ui.settings`). PC adaptations: `MaterialYou` (the Android wallpaper's monet
 * accent) and `CustomColor` (its custom colour, not in the contract) take N-Zik's violet dynamic palette in
 * the phone's dark / light mode; `Customized` (its custom colour set, not in the contract) falls back to
 * the default palette (`colorPaletteOf`). In `PitchBlack` the phone's preference listener blackens the
 * static palette (`MainActivity.kt` 1517-1525: black backgrounds, white text) — the PC mirrors the
 * phone's changes, so it applies the same.
 */
fun staticColorPaletteOf(settings: UiSettings, isSystemInDarkTheme: Boolean): ColorPalette {
    val name = ColorPaletteName.entries.firstOrNull { it.name == settings.colorPaletteName } ?: ColorPaletteName.Dynamic
    val mode = ColorPaletteMode.entries.firstOrNull { it.name == settings.colorPaletteMode } ?: ColorPaletteMode.Dark
    val palette = staticColorPaletteBase(name, mode, isSystemInDarkTheme)
    return if (mode != ColorPaletteMode.PitchBlack) palette else palette.copy(
        background0 = Color.Black, background1 = Color.Black, background2 = Color.Black,
        background3 = Color.Black, background4 = Color.Black, text = Color.White,
    )
}

private fun staticColorPaletteBase(name: ColorPaletteName, mode: ColorPaletteMode, isSystemInDarkTheme: Boolean): ColorPalette {
    return when (name) {
        // The phone's `dynamicColorPaletteOf(color, !lightTheme)`: dark from its chosen mode
        ColorPaletteName.MaterialYou, ColorPaletteName.CustomColor -> dynamicColorPaletteOf(
            VIOLET_ACCENT,
            mode == ColorPaletteMode.Dark || mode == ColorPaletteMode.PitchBlack ||
                (mode == ColorPaletteMode.System && isSystemInDarkTheme),
        )
        else -> colorPaletteOf(name, mode, isSystemInDarkTheme)
    }
}

/**
 * Port of `computeAppearance` (`MainActivity.kt` 1085-1125): the phone's palette name and mode ([settings],
 * contract 1.10.0 `ui.settings`; the phone's defaults `Dynamic` / `Dark` without it — its static start,
 * `DefaultDarkColorPalette`, until the first cover), Rubik without font padding, thumbnails at 12 dp (at
 * most 25 %), artists in a circle (48 dp), UI at 25 dp (at most 40 %).
 */
fun computeAppearance(fontFamily: FontFamily, settings: UiSettings = UiSettings(), isSystemInDarkTheme: Boolean = true): Appearance {
    val thumbnailRoundnessDp = 12f
    val artistThumbnailRoundnessDp = 48f
    val uiRoundnessDp = 25f

    val colorPalette = staticColorPaletteOf(settings, isSystemInDarkTheme)

    return Appearance(
        colorPalette = colorPalette,
        typography = typographyOf(colorPalette.text, fontFamily),
        thumbnailShape = if (thumbnailRoundnessDp >= 48f) CircleShape else RoundedCornerShape(BoundedCornerSize(thumbnailRoundnessDp.dp, 0.25f)),
        uiRoundnessShape = RoundedCornerShape(BoundedCornerSize(uiRoundnessDp.dp, 0.4f)),
        artistThumbnailShape = if (artistThumbnailRoundnessDp >= 48f) CircleShape else RoundedCornerShape(BoundedCornerSize(artistThumbnailRoundnessDp.dp, 0.25f)),
    )
}

/**
 * The state of the window's navigation pages (spec `spec-settings-navigation` NAV-2 / NAV-4 / NAV-7 /
 * NAV-10): the settings page (with its sub-tab), the update sub-page of it, the "Serveur PC" page. The
 * pages replace the home content, one at a time — opening one closes the other and resets the settings
 * tab to Général (the phone's destination recreation); the update page stacks over the settings page and
 * back lands on its About tab (the settings page stays open). Pure and [internal] so it is testable;
 * [MainActivity] owns the instance.
 */
internal data class NavPageState(
    val settingsOpen: Boolean = false,
    val settingsTab: Int = 0,
    val updateOpen: Boolean = false,
    val phoneOpen: Boolean = false,
) {
    companion object {
        /** All the pages closed (the home). */
        val closed = NavPageState()

        /** The About sub-tab, the last one (NAV-3). */
        const val ABOUT_TAB = 4
    }

    /** Any navigation page shown (the home content and the connection banner are hidden). */
    val anyPageOpen: Boolean
        get() = settingsOpen || updateOpen || phoneOpen

    /** The header's "Settings" button: the settings page replaces the Serveur PC page, the tab reset to
     * Général; a press on the already-open settings page does nothing (its tab is kept). */
    fun onSettingsPress(): NavPageState =
        if (settingsOpen) this
        else copy(phoneOpen = false, settingsOpen = true, settingsTab = 0, updateOpen = false)

    /** The header's "Serveur PC" button: the Serveur PC page replaces the settings page (with its update
     * sub-page); a press on the already-open Serveur PC page does nothing. */
    fun onPhonePress(): NavPageState =
        if (phoneOpen) this
        else copy(settingsOpen = false, updateOpen = false, phoneOpen = true)

    /** The settings' sub-tab switch: only the tab moves. */
    fun onTabChanged(tab: Int): NavPageState = copy(settingsTab = tab)

    /** The About tab's update card (NAV-4): the update page over the settings page, the tab on About. */
    fun openUpdate(): NavPageState = copy(updateOpen = true, settingsTab = ABOUT_TAB)

    /** All the pages closed, the tab reset (the home under the pages). */
    fun closeAll(): NavPageState = copy(settingsOpen = false, settingsTab = 0, updateOpen = false, phoneOpen = false)

    /**
     * The window's back (NAV-7): the update sub-page first, then the settings page (the tab reset to
     * Général) or the Serveur PC page; `null` when no page is open (the caller goes on to the queue,
     * the player or the detail page — [backStep]).
     */
    fun back(): NavPageState? =
        when {
            updateOpen -> copy(updateOpen = false)
            settingsOpen -> copy(settingsOpen = false, updateOpen = false, settingsTab = 0)
            phoneOpen -> copy(phoneOpen = false)
            else -> null
        }
}

/**
 * The phone's `MainActivity` content once paired (`app/n_zik/android/MainActivity.kt` `setContent`): the
 * [AppHeader], the home ([HomeScreen]: library tabs and floating navigation bar), the player sheet in its
 * palette fade (the [MiniPlayer] floating above the navigation bar, the full [Player] opened from it), the
 * mini-player's queue overlay, the menu sheet, the toasts. PC only: the connection banners, the
 * navigation pages — the "Serveur PC" page ([PhonePanel]) and the settings page ([SettingsScreen], its
 * About tab and its update sub-page [UpdateScreen]), opened from the header — and the toasts of the
 * PC's own player ([localPlayback], story 12).
 *
 * The dynamic palette follows the current track's cover (`setDynamicPalette` on each track change).
 * The open page ([CollectionHeader], the phone's album / artist / playlist route) lives here so the header
 * shows its back button; a navigation page does too (its [NavPageState] here) — the header's back arrow
 * closes the innermost page. The back (story 11c): the window's Escape key ([LocalBackDispatcher]) and
 * the mouse's back button close, in order, the menu, the navigation pages (the update page, then the
 * settings or the Serveur PC page), the queue, the player, the page ([backStep]).
 * The scroll-hide of the bars (phone's `MainActivity.kt` 1660-1785): a scroll moves the header up to 64 dp
 * out ([LocalTopBarOffset], the content following it) and the floating bar and mini-player up to 240 dp down
 * ([LocalBottomBarOffset]); on release they snap in 150 ms to shown or hidden. Off while the player or the
 * queue is open; opening the player brings them back in 800 ms.
 * Since contract 1.10.0 (`ui.settings`): the phone's UI settings are read at session start and on each
 * `libraryChanged`; the PC consumes only `topN` from that read — its palette, page / tab transitions and
 * every other appearance setting stay the PC's coded defaults. The PC's own navigation bar stays the
 * phone's default floating bar (so the toasts, loader and mini-player shadow keep its offsets), and the
 * phone's `disableNavigationBackStack` is not mirrored (the PC's back always walks its page stack).
 * Dropped: the navigation routes the contract has no data for, the player sheet's drag / fling (the full
 * player deploys from the mini-player in a 400 ms slide; no touch on the PC), the system bars.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun MainActivity(
    repository: PlayerRepository,
    library: LibraryRepository,
    localPlayback: LocalPlayback?,
    audioCache: AudioCache?,
    record: PairingRecord,
    appearanceState: AppearanceState,
    onForget: () -> Unit,
) {
    val state by repository.state.collectAsState()
    val connection by repository.connection.collectAsState()
    val scope = rememberCoroutineScope()
    var showPlayer by remember { mutableStateOf(false) }
    var showQueueOverlay by remember { mutableStateOf(false) }
    // The navigation pages (spec `spec-settings-navigation` NAV-2 / NAV-4 / NAV-7): the settings page
    // (with its sub-tab), the update sub-page, the Serveur PC page — they replace the home content
    var pages by remember { mutableStateOf(NavPageState.closed) }
    var detail by remember { mutableStateOf<CollectionHeader?>(null) }
    val preferences = LocalPreferences.current
    var navBarVisible by remember { mutableStateOf(false) }
    val menuState = remember { MenuState() }
    val onCommand: CommandLauncher = { command -> scope.launch { repository.command() } }
    // The screens' messages are the locator's, an information toast on the phone (`Locator.kt` 79, 88)
    val showMessage: (String) -> Unit = { text -> Toaster.i(text) }
    val lists = remember(library, audioCache) { LibraryLists(library, scope, audioCache) }
    val actions = remember(repository, library, lists) {
        LibraryActions(repository, library, scope, info = { Toaster.i(it) }, lists = lists) { Toaster.e(it) }
    }

    LaunchedEffect(repository) {
        repository.notices.collect { notice ->
            val text = noticeText(notice)
            when (notice) {
                // Contract §7.9 (since 1.10.0): the phone's own toast, with its own type
                is PlayerNotice.PhoneToast -> Toaster.toast(text, phoneToastType(notice.toastType))
                is PlayerNotice.Truncated -> Toaster.w(text)
                else -> Toaster.e(text)
            }
        }
    }

    // Since 1.10.0 (feature `ui.settings`): the phone's UI settings are fetched at session start and
    // on each `libraryChanged` (§10.5) and applied through [UiSettingsHolder] — the PC consumes only
    // `topN`, everything else stays the PC's coded defaults. The last read value survives a
    // reconnection; the defaults until a first successful read (or without the feature)
    val uiSettingsHolder = remember(library) { UiSettingsHolder(library) }
    val uiSettings by uiSettingsHolder.settings.collectAsState()
    val sessionLive = connection == ConnectionState.Live
    LaunchedEffect(repository, library, sessionLive) {
        if (sessionLive && SessionContract.FEATURE_UI_SETTINGS in library.features) {
            uiSettingsHolder.fetch()
            repository.libraryChanged.collect { uiSettingsHolder.fetch() }
        }
    }
    val systemDark = isSystemInDarkTheme()

    // Since 1.7.3 (feature `library.live`): the phone's library moved — the loaded lists of the
    // invalidated family re-read (coalesced in the lists)
    LaunchedEffect(repository, lists) {
        if (SessionContract.FEATURE_LIBRARY_LIVE in repository.features) {
            repository.libraryChanged.collect { kind ->
                lists.onLibraryChanged(kind)
            }
        }
    }

    LaunchedEffect(localPlayback) {
        localPlayback?.notices?.collect { notice -> Toaster.e(localPlaybackNoticeText(notice)) }
    }

    val track = state?.currentTrack
    // Dynamic palette: the phone calls setDynamicPalette on each media item transition (in one step) and
    // on a palette name / mode or system theme change (animated: its `animateTheme` / `animateGlobal`,
    // `MainActivity.kt` 1318, 1323, 1486, 1527)
    val lastTheme = remember { arrayOfNulls<Any>(1) }
    LaunchedEffect(track?.id, track?.hasArtwork, uiSettings.colorPaletteName, uiSettings.colorPaletteMode, systemDark) {
        val theme = Triple(uiSettings.colorPaletteName, uiSettings.colorPaletteMode, systemDark)
        val animate = lastTheme[0] != null && lastTheme[0] != theme
        lastTheme[0] = theme
        val bitmap = if (track != null && track.hasArtwork) {
            repository.artwork(ArtworkKey.track(track.id, PLAYER_ARTWORK_SIZE_PX))
        } else {
            null
        }
        appearanceState.setDynamicPalette(bitmap, uiSettings, systemDark, animateTheme = animate)
    }
    // The root dialogs (the updater's) read the phone's settings through the appearance state
    androidx.compose.runtime.SideEffect { appearanceState.uiSettings = uiSettings }

    // Media presence grace: a transient null between two track changes must not drop the sheet
    // (port of the phone's 400 ms grace, phone's `MainActivity.kt` 2198-2223). Once the absence persists,
    // the player is dismissed and the queue overlay closed (`showQueueOverlay = false`, phone's 2215).
    var mediaPresent by remember { mutableStateOf(repository.state.value?.currentTrack != null) }
    LaunchedEffect(track?.id) {
        if (track == null) {
            if (mediaAbsencePersists { repository.state.value?.currentTrack }) {
                mediaPresent = false
                showPlayer = false
                showQueueOverlay = false
            }
        } else {
            mediaPresent = true
        }
    }

    // The phone's mini-player pop/depop port (its commit 765d811, its `MainActivity.kt` 2112-2155): the
    // sheet subtree stays composed until the animated leave has settled (its `shouldComposePlayerSheet`),
    // each presentation fades in with the 300 ms appear fade (its `appearAlpha`, the 2 heavy frames
    // skipped first — the mini-player keeps its last track so the whole slide + fade stays visible), and
    // the leave is the phone's animated `dismiss()` — the sheet slides below the screen while the
    // dismissed-zone alpha fades it (its `miniPlayerDismissAlpha`, linear across the travel) — instead
    // of vanishing in place. A presentation after an in-flight leave resumes the pop from wherever it
    // stopped (the phone's race fix). No AnimatedVisibility: it would add a second full-screen layer
    // with the default Auto compositing strategy (the phone's note: lag).
    val sheetDismiss = remember { Animatable(0f) } // 0 = presented, 1 = dismissed (below the screen)
    val appearAlpha = remember { Animatable(0f) }  // the phone's `appearAlpha`: 0 hidden → 1 shown
    var sheetComposed by remember { mutableStateOf(repository.state.value?.currentTrack != null) }
    LaunchedEffect(sheetComposed) {
        if (sheetComposed) {
            // The phone's `appearAlpha`: replayed each time the subtree presents, the 2 heavy frames
            // skipped before the 300 ms fade starts
            appearAlpha.snapTo(0f)
            repeat(MINIPLAYER_APPEAR_FADE_SKIPPED_FRAMES) { withFrameNanos { } }
            appearAlpha.animateTo(1f, tween(MINIPLAYER_APPEAR_FADE_MS.toInt()))
            if (sheetDismiss.value > 0f) {
                // The phone's pop (its `showMiniplayerIfDismissed`): from wherever the leave stopped,
                // back to rest with the 250 ms tween, the dismissed-zone alpha fading the bar in with it
                sheetDismiss.animateTo(0f, tween(MINIPLAYER_POP_ANIMATION_MS.toInt()))
            }
        }
    }
    LaunchedEffect(mediaPresent) {
        if (mediaPresent) {
            sheetComposed = true
        } else {
            // The leave: the phone's animated `dismiss()` (its default spring) — the sheet slides
            // below the screen while the alpha fades it; the subtree is removed once settled
            sheetDismiss.animateTo(1f, spring())
            sheetComposed = false
        }
    }

    // Whether the last navigation was a back (the phone's pop transitions) or a forward one (a click)
    var navIsBack by remember { mutableStateOf(false) }
    // The phone's home navigation (title click, the player's logo) PUSHES its home route: back returns to
    // the page it left. One level kept (the page and navigation pages left), restored by the back
    var homeReturn by remember { mutableStateOf<Pair<NavPageState, CollectionHeader?>?>(null) }
    val goHome: () -> Unit = {
        navIsBack = false
        if (detail != null || pages.anyPageOpen) homeReturn = pages to detail
        detail = null
        pages = pages.closeAll()
    }

    // The settings page's state, kept while the update page covers it (the phone's back stack); a closed
    // settings page starts afresh next time (the phone recreates its destination)
    val settingsStateHolder = androidx.compose.runtime.saveable.rememberSaveableStateHolder()
    LaunchedEffect(pages.settingsOpen) {
        if (!pages.settingsOpen) settingsStateHolder.removeState(SETTINGS_STATE_KEY)
    }

    // The full player's inline queue, hoisted so the back closes it before the player (phone's BackHandler)
    val playerQueueState = remember { mutableStateOf(false) }
    LaunchedEffect(showPlayer) { if (!showPlayer) playerQueueState.value = false }

    // Back: one step per press — the menu, the overlays drawn over the pages (queue, player's queue, player),
    // then the navigation pages, then the open page
    val onBackPress: () -> Boolean = {
        val step = backStep(
            menuState.isDisplayed, pages.anyPageOpen, showQueueOverlay, showPlayer, detail != null, playerQueueState.value,
            homeReturnPending = homeReturn != null,
        )
        if (step == BackStep.Panel || step == BackStep.Page || step == BackStep.HomeReturn) navIsBack = true
        when (step) {
            BackStep.Menu -> menuState.pop()
            // The pages stack: update (innermost) -> settings (the tab reset) or Serveur PC
            BackStep.Panel -> {
                // Back / Escape on the update page while a download runs cancels the download, then closes
                // the page (the phone's BackHandler: toast + cancel + leave, the loop-2 closure)
                if (pages.updateOpen) cancelDownloadOnBack()
                val closed = pages.back()
                if (closed != null) pages = closed
            }
            BackStep.Queue -> showQueueOverlay = false
            BackStep.PlayerQueue -> playerQueueState.value = false
            BackStep.Player -> showPlayer = false
            BackStep.Page -> detail = null
            // The pushed home popped: the page the title click left
            BackStep.HomeReturn -> homeReturn?.let { (returnPages, returnDetail) ->
                pages = returnPages
                detail = returnDetail
                homeReturn = null
            }
            null -> Unit
        }
        step != null
    }
    val currentOnBackPress by rememberUpdatedState(onBackPress)
    val backDispatcher = LocalBackDispatcher.current
    DisposableEffect(backDispatcher) {
        val handler: () -> Boolean = { currentOnBackPress() }
        backDispatcher?.handler = handler
        onDispose { if (backDispatcher?.handler === handler) backDispatcher.handler = null }
    }

    // Scroll-hide of the header and of the floating bar / mini-player (phone's `MainActivity.kt` 1660-1785)
    val density = LocalDensity.current
    val topBarHeightPx = with(density) { APP_HEADER_HEIGHT.roundToPx().toFloat() }
    val bottomBarHeightPx = with(density) { 240.dp.roundToPx().toFloat() } // Enough to hide floating bar + miniplayer
    var topBarOffset by remember { mutableFloatStateOf(0f) }
    var bottomBarOffset by remember { mutableFloatStateOf(0f) }
    val offsetAnimationJob = remember { mutableStateOf<Job?>(null) }
    val scrollHideOff by rememberUpdatedState(showPlayer || showQueueOverlay)

    // Opening the player must restore the hidden bars (phone's `restoreHiddenBars`, 800 ms)
    LaunchedEffect(showPlayer) {
        if (!showPlayer || (topBarOffset == 0f && bottomBarOffset == 0f)) return@LaunchedEffect
        offsetAnimationJob.value?.cancel()
        offsetAnimationJob.value = scope.launch {
            launch { Animatable(topBarOffset).animateTo(0f, tween(800, easing = FastOutSlowInEasing)) { topBarOffset = value } }
            launch { Animatable(bottomBarOffset).animateTo(0f, tween(800, easing = FastOutSlowInEasing)) { bottomBarOffset = value } }
        }
    }

    val barsHide = remember(topBarHeightPx, bottomBarHeightPx) { BarsScrollHide(topBarHeightPx, bottomBarHeightPx) }

    fun snapBars() {
        offsetAnimationJob.value?.cancel()
        offsetAnimationJob.value = scope.launch {
            // No fling from a mouse wheel: snap once the scroll pauses (the phone snaps after its fling)
            delay(BARS_SNAP_DELAY_MS)
            val shown = barsHide.snapShown()
            val targetTop = if (shown) 0f else -topBarHeightPx
            val targetBottom = if (shown) 0f else bottomBarHeightPx
            launch { Animatable(topBarOffset).animateTo(targetTop, tween(150, easing = LinearEasing)) { topBarOffset = value; barsHide.set(topBarOffset, bottomBarOffset) } }
            launch { Animatable(bottomBarOffset).animateTo(targetBottom, tween(150, easing = LinearEasing)) { bottomBarOffset = value; barsHide.set(topBarOffset, bottomBarOffset) } }
        }
    }

    fun applyBars() {
        topBarOffset = barsHide.top
        bottomBarOffset = barsHide.bottom
    }

    val nestedScrollConnection = remember(barsHide) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // Disable scroll-hide while the full player is on screen or the queue is open
                if (scrollHideOff || available.y == 0f) return Offset.Zero
                offsetAnimationJob.value?.cancel()
                barsHide.set(topBarOffset, bottomBarOffset)
                val consumedY = barsHide.scroll(available.y)
                applyBars()
                snapBars()
                return Offset(0f, consumedY)
            }
        }
    }
    val topBarOffsetState = remember { derivedStateOf { topBarOffset } }
    val bottomBarOffsetState = remember { derivedStateOf { bottomBarOffset } }

    CompositionLocalProvider(
        LocalPlayerRepository provides repository,
        LocalLibraryActions provides actions,
        LocalUiSettings provides uiSettings,
        app.n_zik.compagnon.core.navigation.LocalPlayerQueueState provides playerQueueState,
        app.n_zik.compagnon.core.navigation.LocalGoHome provides goHome,
        LocalCommandLauncher provides onCommand,
        LocalMenuState provides menuState,
        LocalTopBarOffset provides topBarOffsetState,
        LocalBottomBarOffset provides bottomBarOffsetState,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                // A wheel step up shows the bars again even over a list already at its top (no nested scroll)
                .onPointerEvent(PointerEventType.Scroll, PointerEventPass.Initial) { event ->
                    val notches = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                    if (!scrollHideOff && notches < 0f && (topBarOffset != 0f || bottomBarOffset != 0f)) {
                        offsetAnimationJob.value?.cancel()
                        barsHide.set(topBarOffset, bottomBarOffset)
                        barsHide.wheel(-notches * topBarHeightPx)
                        applyBars()
                        snapBars()
                    }
                }
                // The mouse's back button: the phone's system back
                .onPointerEvent(PointerEventType.Press, PointerEventPass.Initial) { event ->
                    if (event.button == PointerButton.Back) currentOnBackPress()
                },
        ) {
            // The scroll-hide connection rides the content, not the window: the player sheet, the queue
            // overlay and the menus are the Column's siblings, so their nested scroll never hides the
            // bars — on the phone they are separate windows (`CustomModalBottomSheet`) and reach none of
            // the activity's scroll listeners
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(nestedScrollConnection),
            ) {
                // The header slides out with the scroll and the content follows it (phone's
                // `AppNavigation.kt` 303-345: the content's top padding is the header plus its offset)
                Box(
                    modifier = Modifier.layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)
                        val offsetPx = topBarOffsetState.value.roundToInt()
                        val height = (placeable.height + offsetPx).coerceAtLeast(0)
                        layout(placeable.width, height) {
                            placeable.place(0, offsetPx)
                        }
                    },
                ) {
                    AppHeader(
                        isHome = detail == null && !pages.anyPageOpen,
                        onBack = { currentOnBackPress() },
                        onHome = goHome,
                        onPhone = { navIsBack = false; pages = pages.onPhonePress() },
                        onSettings = { navIsBack = false; pages = pages.onSettingsPress() },
                    )
                }
                // The navigation pages replace the home (the connection banner and the lists are
                // hidden with it — like the phone's destinations replacing the home content)
                // The phone's page transition (its `transitionEffect`, `ui.settings` since 1.10.0) between
                // the home and the navigation pages, keyed on which page shows
                AnimatedContent(
                    targetState = pages,
                    // Back (a page closed, or the update page left): the phone's pop transitions
                    transitionSpec = { pageTransition(uiSettings.transition, navIsBack) },
                    label = "navPages",
                    // A sub-tab switch is not a page change: only the shown page animates
                    contentKey = { Triple(it.phoneOpen, it.settingsOpen, it.updateOpen) },
                    modifier = Modifier.weight(1f),
                ) { shown ->
                    if (shown.anyPageOpen) {
                        val prefs = preferences
                        Box(modifier = Modifier.fillMaxSize()) {
                            if (shown.phoneOpen) {
                                PhonePanel(record, connection, onForget = onForget)
                            } else if (prefs != null) {
                                if (shown.updateOpen) {
                                    // The update page replaces the settings page (NAV-7): back closes it
                                    // first, landing on the settings' About tab
                                    UpdateScreen(prefs, onClose = { navIsBack = true; pages = pages.copy(updateOpen = false) })
                                } else {
                                    // The phone keeps its settings destination in the back stack under the
                                    // update page: its state (scroll, per-tab search) survives the visit
                                    settingsStateHolder.SaveableStateProvider(SETTINGS_STATE_KEY) {
                                        SettingsScreen(
                                            prefs,
                                            audioCache,
                                            shown.settingsTab,
                                            onTabChanged = { pages = pages.onTabChanged(it) },
                                            onOpenUpdate = { navIsBack = false; pages = pages.openUpdate() },
                                        )
                                    }
                                }
                            }
                        }
                    } else Column(Modifier.fillMaxSize()) {
                        ConnectionBanner(connection, onReconnect = repository::reconnect)
                        HomeScreen(
                            lists = lists,
                            library = library,
                            actions = actions,
                            live = connection == ConnectionState.Live,
                            onMessage = showMessage,
                            detail = detail,
                            onDetail = { navIsBack = false; detail = it },
                            // A page closing itself ("not found", its back arrow): the phone's pop transition
                            onDetailBack = { navIsBack = true; detail = null },
                            isBackNavigation = navIsBack,
                            onNavBarVisible = { navBarVisible = it },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            // PlayerPosition.Bottom with the floating bar: the sheet sits above the bar when it is shown —
            // the home's navigation bar (reported by [HomeScreen]) or the settings page's sub-tab bar
            // (the Serveur PC page and the update page have none)
            val barUnderMiniPlayer = if (pages.anyPageOpen) pages.settingsOpen && !pages.updateOpen else navBarVisible
            val playerPadBottom by animateDpAsState(
                targetValue = if (barUnderMiniPlayer) {
                    Dimensions.floatingNavBarIconOnlyHeight + Dimensions.navBarBottomPadding + 4.dp
                } else {
                    Dimensions.navBarBottomPadding
                },
                animationSpec = tween(250, easing = FastOutSlowInEasing),
                label = "playerPadBottom",
            )

            // Palette fade scope: the global palette switches in one step, only the mini-player and the
            // full player animate the transition
            PaletteFade {
                if (sheetComposed) {
                    // The mini-player leaves with the floating bar on scroll (phone's `MainActivity.kt` 2289)
                    Box(
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                // The phone's sheet alpha (its commit 765d811): the 300 ms appear fade x
                                // the dismissed-zone fade — the linear fade across the dismissed travel
                                // (its `miniPlayerDismissAlpha`) that follows the leave's spring and the
                                // pop's tween
                                alpha = appearAlpha.value * (1f - sheetDismiss.value)
                                // The leave slides the sheet below the screen (its animated `dismiss()`);
                                // at rest there is no translation
                                translationY = sheetDismiss.value * size.height
                                // The phone's performance fix: with the default Auto strategy, an alpha
                                // below 1 renders the whole full-screen sheet subtree into a full-screen
                                // offscreen buffer on every frame; ModulateAlpha applies the alpha per
                                // draw op (the phone's trade-off note)
                                compositingStrategy = CompositingStrategy.ModulateAlpha
                            }
                            .offset { IntOffset(0, bottomBarOffsetState.value.roundToInt()) },
                    ) {
                        PlayerSheet(
                            showPlayer = showPlayer,
                            onShowPlayer = { showPlayer = it },
                            onShowQueue = { showQueueOverlay = true },
                            phoneName = record.serverName,
                            bottomPadding = playerPadBottom,
                        )
                    }
                }
            }

            // The queue overlay is drawn ABOVE the sheet so its panel (65 % height) is not
            // hidden by the PlayerSheet's full-size box; the menu and the toasters (composed after
            // the overlay) stay above all
            MiniPlayerQueueOverlay(
                showSheet = showQueueOverlay,
                onDismiss = { showQueueOverlay = false },
            )
            BottomSheetMenu(menuState)
            with(Toaster) { Host() }
        }
    }
}

/**
 * The PC's effective UI settings of the `GET /ui/settings` wire read (spec `spec-remove-ui-sync`): each
 * fetch applies [UiSettings.toPcEffective] — only the phone's `topN` carries over, every other field
 * stays the coded PC default (the wire's appearance fields are decoded but never mirrored). Fetched at
 * session start and on each `libraryChanged` (contract §10.5); a failed read (or a phone without the
 * feature) keeps the last value, the coded defaults until the first successful read.
 */
internal class UiSettingsHolder(private val library: LibraryRepository) {
    private val _settings = MutableStateFlow(UiSettings())
    val settings: StateFlow<UiSettings> = _settings.asStateFlow()

    /** One fetch-and-apply; a `null` read keeps the last value. */
    suspend fun fetch() {
        library.uiSettings()?.let { _settings.value = it.toPcEffective() }
    }
}

/** Text of a failure of the PC's own player (story 12), decided from its kind; shown as a toast. */
private suspend fun localPlaybackNoticeText(notice: LocalPlaybackNotice): String = when (notice) {
    LocalPlaybackNotice.NotFound -> getString(Res.string.local_playback_not_found)
    LocalPlaybackNotice.UpstreamFailed -> getString(Res.string.local_playback_upstream_failed)
    LocalPlaybackNotice.InvalidUrl -> getString(Res.string.local_playback_invalid_url)
    is LocalPlaybackNotice.OtherActive ->
        formatMessage(Res.string.local_playback_other_active, notice.deviceName ?: getString(Res.string.paired_other_active_unknown))
    LocalPlaybackNotice.Unreachable -> getString(Res.string.local_playback_unreachable)
    LocalPlaybackNotice.Failed -> getString(Res.string.local_playback_failed)
}

/** The phone's grace before "no media" is accepted (`MainActivity.kt` 2205). */
internal const val MEDIA_ABSENCE_GRACE_MS = 400L

/**
 * Waits [MEDIA_ABSENCE_GRACE_MS] then tells whether the media is still absent (phone's `MainActivity.kt`
 * 2204-2215): `true` closes the player and the queue overlay, `false` was a transient null between two tracks.
 */
internal suspend fun mediaAbsencePersists(currentTrack: () -> Any?): Boolean {
    delay(MEDIA_ABSENCE_GRACE_MS)
    return currentTrack() == null
}

/** The scroll-hide offset of the header, in px (0 shown, −64 dp hidden): phone's `LocalTopBarOffset`. */
val LocalTopBarOffset = staticCompositionLocalOf<State<Float>> { mutableStateOf(0f) }

/** The scroll-hide offset of the floating bar and mini-player, in px (0 shown, 240 dp hidden). */
val LocalBottomBarOffset = staticCompositionLocalOf<State<Float>> { mutableStateOf(0f) }

/** Pause after the last scroll before the bars snap shown or hidden. */
internal const val BARS_SNAP_DELAY_MS = 150L

/** The settings page's key in its [androidx.compose.runtime.saveable.SaveableStateHolder]. */
private const val SETTINGS_STATE_KEY = "settings"
