package app.n_zik.compagnon

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
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
 * mini-player's queue overlay, the menu sheet, the toasts. PC only: the connection banners and the "Phone"
 * panel (opened from the header).
 *
 * The dynamic palette follows the current track's cover (`setDynamicPalette` on each track change).
 * Dropped: navigation routes other than home, the player sheet's drag (the full player opens and closes with
 * a slide), the system bars, the scroll-hide of the bars.
 */
@Composable
fun MainActivity(
    repository: PlayerRepository,
    library: LibraryRepository,
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
    var navBarVisible by remember { mutableStateOf(false) }
    val menuState = remember { MenuState() }
    val onCommand: CommandLauncher = { command -> scope.launch { repository.command() } }
    val showMessage: (String) -> Unit = { text -> Toaster.n(text) }
    val lists = remember(library) { LibraryLists(library, scope) }
    val actions = remember(repository, library) { LibraryActions(repository, library, scope) { showMessage(it) } }

    LaunchedEffect(repository) {
        repository.notices.collect { notice ->
            val text = noticeText(notice)
            if (notice is PlayerNotice.Truncated) Toaster.w(text) else Toaster.e(text)
        }
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

    CompositionLocalProvider(
        LocalPlayerRepository provides repository,
        LocalCommandLauncher provides onCommand,
        LocalMenuState provides menuState,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                AppHeader(connection, onPhone = { phonePanel = true })
                ConnectionBanner(connection, onReconnect = repository::reconnect)
                HomeScreen(
                    lists = lists,
                    library = library,
                    actions = actions,
                    live = connection == ConnectionState.Live,
                    onMessage = showMessage,
                    onNavBarVisible = { navBarVisible = it },
                    modifier = Modifier.weight(1f),
                )
            }

            // Palette fade scope: the global palette switches in one step, only the mini-player and the
            // full player animate the transition
            PaletteFade {
                Box(modifier = Modifier.fillMaxSize()) {
                    if (track != null) {
                        // PlayerPosition.Bottom with the floating bar: above the bar when it is shown
                        val playerPadBottom by animateDpAsState(
                            targetValue = if (navBarVisible) {
                                Dimensions.floatingNavBarIconOnlyHeight + Dimensions.navBarBottomPadding + 4.dp
                            } else {
                                Dimensions.navBarBottomPadding
                            },
                            animationSpec = tween(250, easing = FastOutSlowInEasing),
                            label = "playerPadBottom",
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = playerPadBottom)
                                .fillMaxWidth()
                                .height(Dimensions.collapsedPlayer),
                        ) {
                            MiniPlayer(
                                showPlayer = { showPlayer = true },
                                hidePlayer = { showPlayer = false },
                                onShowQueue = { showQueueOverlay = true },
                            )
                        }
                    }
                    AnimatedVisibility(
                        visible = showPlayer && track != null,
                        enter = slideInVertically { it } + fadeIn(),
                        exit = slideOutVertically { it } + fadeOut(),
                    ) {
                        Player(onDismiss = { showPlayer = false })
                    }
                }
            }

            MiniPlayerQueueOverlay(
                showSheet = showQueueOverlay,
                onDismiss = { showQueueOverlay = false },
            )
            BottomSheetMenu(menuState)
            if (phonePanel) {
                PhonePanel(record, connection, onClose = { phonePanel = false }, onForget = onForget)
            }
            with(Toaster) { Host() }
        }
    }
}
