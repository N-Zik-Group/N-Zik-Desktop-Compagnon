package app.n_zik.compagnon

import app.n_zik.compagnon.components.navigation.BarsScrollHide
import androidx.compose.ui.ExperimentalComposeUiApi
import app.n_zik.compagnon.core.navigation.backStep
import app.n_zik.compagnon.core.navigation.LocalBackDispatcher
import app.n_zik.compagnon.core.navigation.BackStep
import app.n_zik.compagnon.components.ui.screens.home.CollectionHeader
import app.n_zik.compagnon.components.player.APP_HEADER_HEIGHT
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import app.n_zik.compagnon.bridge.ConnectionState
import app.n_zik.compagnon.bridge.library.LibraryRepository
import app.n_zik.compagnon.bridge.pairing.PairingRecord
import app.n_zik.compagnon.bridge.state.PlayerNotice
import app.n_zik.compagnon.bridge.state.PlayerRepository
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
import app.n_zik.compagnon.components.ui.screens.home.HomeScreen
import app.n_zik.compagnon.components.ui.screens.home.LibraryActions
import app.n_zik.compagnon.components.ui.screens.home.LibraryLists
import app.n_zik.compagnon.core.network.ArtworkKey
import app.n_zik.compagnon.core.palette.toPaletteBitmap
import app.n_zik.compagnon.enums.ColorPaletteMode
import app.n_zik.compagnon.enums.ColorPaletteName
import app.n_zik.compagnon.utils.Toaster
import app.n_zik.compagnon.components.ui.screens.settings.SettingsScreen
import app.n_zik.compagnon.playback.cache.AudioCache
import app.n_zik.compagnon.playback.services.LocalPlayback
import app.n_zik.compagnon.playback.services.LocalPlaybackNotice
import app.n_zik.compagnon.utils.LocalPreferences
import app.n_zik.compagnon.generated.resources.Res
import app.n_zik.compagnon.generated.resources.local_playback_failed
import app.n_zik.compagnon.generated.resources.local_playback_invalid_url
import app.n_zik.compagnon.generated.resources.local_playback_not_found
import app.n_zik.compagnon.generated.resources.local_playback_other_active
import app.n_zik.compagnon.generated.resources.local_playback_unreachable
import app.n_zik.compagnon.generated.resources.local_playback_upstream_failed
import app.n_zik.compagnon.generated.resources.paired_other_active_unknown
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

    fun updateAppearance(newAppearance: Appearance, animateGlobal: Boolean = false) {
        if (animateGlobal) fadeFromAppearance = appearance
        appearance = newAppearance
    }

    /**
     * Port of `setDynamicPalette` (`MainActivity.kt` 1067-1196) with the default preferences (`Dynamic`
     * palette, `Dark` mode): the palette of the current track's cover ([bitmap], read through the phone), or
     * the dynamic palette of N-Zik's violet when there is no cover. The global palette changes in one step;
     * the player's [PaletteFade] fades it.
     */
    suspend fun setDynamicPalette(bitmap: ImageBitmap?, animateTheme: Boolean = false) {
        val isDark = true
        val finalPalette = if (bitmap == null) {
            null
        } else {
            withContext(Dispatchers.Default) { m3eDynamicColorPaletteOf(bitmap.toPaletteBitmap(), isDark) }
        }
        val targetPalette = finalPalette ?: dynamicColorPaletteOf(VIOLET_ACCENT, isDark)
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
 * Port of `computeAppearance` (`MainActivity.kt` 988-1025) with the phone's default preferences: `Dynamic`
 * palette in `Dark` mode (its static start, `DefaultDarkColorPalette`, until the first cover), Rubik without
 * font padding, thumbnails at 12 dp (at most 25 %), artists in a circle (48 dp), UI at 25 dp (at most 40 %).
 */
fun computeAppearance(fontFamily: FontFamily): Appearance {
    val colorPaletteName = ColorPaletteName.Dynamic
    val colorPaletteMode = ColorPaletteMode.Dark
    val thumbnailRoundnessDp = 12f
    val artistThumbnailRoundnessDp = 48f
    val uiRoundnessDp = 25f

    val colorPalette = colorPaletteOf(colorPaletteName, colorPaletteMode, true)

    return Appearance(
        colorPalette = colorPalette,
        typography = typographyOf(colorPalette.text, fontFamily),
        thumbnailShape = if (thumbnailRoundnessDp >= 48f) CircleShape else RoundedCornerShape(BoundedCornerSize(thumbnailRoundnessDp.dp, 0.25f)),
        uiRoundnessShape = RoundedCornerShape(BoundedCornerSize(uiRoundnessDp.dp, 0.4f)),
        artistThumbnailShape = if (artistThumbnailRoundnessDp >= 48f) CircleShape else RoundedCornerShape(BoundedCornerSize(artistThumbnailRoundnessDp.dp, 0.25f)),
    )
}

/**
 * The phone's `MainActivity` content once paired (`app/n_zik/android/MainActivity.kt` `setContent`): the
 * [AppHeader], the home ([HomeScreen]: library tabs and floating navigation bar), the player sheet in its
 * palette fade (the [MiniPlayer] floating above the navigation bar, the full [Player] opened from it), the
 * mini-player's queue overlay, the menu sheet, the toasts. PC only: the connection banners, the "Phone"
 * panel (opened from the header), the settings overlay ([SettingsScreen], from the header's settings icon)
 * and the toasts of the PC's own player ([localPlayback], story 12).
 *
 * The dynamic palette follows the current track's cover (`setDynamicPalette` on each track change).
 * The open page ([CollectionHeader], the phone's album / artist / playlist route) lives here so the header
 * shows its back button. The back (story 11c): the window's Escape key ([LocalBackDispatcher]) and the
 * mouse's back button close, in order, the menu, the panels, the queue, the player, the page ([backStep]).
 * The scroll-hide of the bars (phone's `MainActivity.kt` 1660-1785): a scroll moves the header up to 64 dp
 * out ([LocalTopBarOffset], the content following it) and the floating bar and mini-player up to 240 dp down
 * ([LocalBottomBarOffset]); on release they snap in 150 ms to shown or hidden. Off while the player or the
 * queue is open; opening the player brings them back in 800 ms.
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
    var phonePanel by remember { mutableStateOf(false) }
    var settingsPanel by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf<CollectionHeader?>(null) }
    val preferences = LocalPreferences.current
    var navBarVisible by remember { mutableStateOf(false) }
    val menuState = remember { MenuState() }
    val onCommand: CommandLauncher = { command -> scope.launch { repository.command() } }
    // The screens' messages are the locator's, an information toast on the phone (`Locator.kt` 79, 88)
    val showMessage: (String) -> Unit = { text -> Toaster.i(text) }
    val lists = remember(library) { LibraryLists(library, scope) }
    val actions = remember(repository, library) { LibraryActions(repository, library, scope, info = { Toaster.i(it) }) { Toaster.e(it) } }

    LaunchedEffect(repository) {
        repository.notices.collect { notice ->
            val text = noticeText(notice)
            if (notice is PlayerNotice.Truncated) Toaster.w(text) else Toaster.e(text)
        }
    }

    LaunchedEffect(localPlayback) {
        localPlayback?.notices?.collect { notice -> Toaster.e(localPlaybackNoticeText(notice)) }
    }

    val track = state?.currentTrack
    // Dynamic palette: the phone calls setDynamicPalette on each media item transition
    LaunchedEffect(track?.id, track?.hasArtwork) {
        val bitmap = if (track != null && track.hasArtwork) {
            repository.artwork(ArtworkKey.track(track.id, PLAYER_ARTWORK_SIZE_PX))
        } else {
            null
        }
        appearanceState.setDynamicPalette(bitmap)
    }

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

    // Back: one step per press, in the phone's order (menu, panels, queue, player, page)
    val onBackPress: () -> Boolean = {
        val step = backStep(menuState.isDisplayed, phonePanel || settingsPanel, showQueueOverlay, showPlayer, detail != null)
        when (step) {
            BackStep.Menu -> menuState.hide()
            BackStep.Panel -> if (settingsPanel) settingsPanel = false else phonePanel = false
            BackStep.Queue -> showQueueOverlay = false
            BackStep.Player -> showPlayer = false
            BackStep.Page -> detail = null
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
                        isHome = detail == null,
                        onBack = { detail = null },
                        onHome = { detail = null },
                        onPhone = { phonePanel = true },
                        onSettings = { settingsPanel = true },
                    )
                }
                ConnectionBanner(connection, onReconnect = repository::reconnect)
                HomeScreen(
                    lists = lists,
                    library = library,
                    actions = actions,
                    live = connection == ConnectionState.Live,
                    onMessage = showMessage,
                    detail = detail,
                    onDetail = { detail = it },
                    onNavBarVisible = { navBarVisible = it },
                    modifier = Modifier.weight(1f),
                )
            }

            // PlayerPosition.Bottom with the floating bar: the sheet sits above the bar when it is shown
            val playerPadBottom by animateDpAsState(
                targetValue = if (navBarVisible) {
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
                if (mediaPresent) {
                    // The mini-player leaves with the floating bar on scroll (phone's `MainActivity.kt` 2289)
                    Box(Modifier.fillMaxSize().offset { IntOffset(0, bottomBarOffsetState.value.roundToInt()) }) {
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
            // hidden by the PlayerSheet's full-size box; the menu, the panels and the toasters
            // (composed after the overlay) stay above all
            MiniPlayerQueueOverlay(
                showSheet = showQueueOverlay,
                onDismiss = { showQueueOverlay = false },
            )
            BottomSheetMenu(menuState)
            if (phonePanel) {
                PhonePanel(record, connection, onClose = { phonePanel = false }, onForget = onForget)
            }
            if (settingsPanel && preferences != null) {
                SettingsScreen(preferences, audioCache, onClose = { settingsPanel = false })
            }
            with(Toaster) { Host() }
        }
    }
}

/** Text of a failure of the PC's own player (story 12), decided from its kind; shown as a toast. */
private suspend fun localPlaybackNoticeText(notice: LocalPlaybackNotice): String = when (notice) {
    LocalPlaybackNotice.NotFound -> getString(Res.string.local_playback_not_found)
    LocalPlaybackNotice.UpstreamFailed -> getString(Res.string.local_playback_upstream_failed)
    LocalPlaybackNotice.InvalidUrl -> getString(Res.string.local_playback_invalid_url)
    is LocalPlaybackNotice.OtherActive ->
        getString(Res.string.local_playback_other_active, notice.deviceName ?: getString(Res.string.paired_other_active_unknown))
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
