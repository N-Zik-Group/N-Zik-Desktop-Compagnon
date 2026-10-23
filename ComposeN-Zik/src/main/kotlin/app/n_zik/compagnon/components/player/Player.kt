package app.n_zik.compagnon.components.player

import app.n_zik.compagnon.LocalLibraryActions
import app.n_zik.compagnon.bridge.state.LocalUiSettings
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.n_zik.compagnon.LocalCommandLauncher
import app.n_zik.compagnon.LocalPlayerRepository
import app.n_zik.compagnon.bridge.ConnectionState
import app.n_zik.compagnon.bridge.state.PlayerBackgroundColors
import app.n_zik.compagnon.bridge.state.QueuePosition
import app.n_zik.compagnon.bridge.state.SessionContract
import app.n_zik.compagnon.colorPalette
import app.n_zik.compagnon.components.LocalMenuState
import app.n_zik.compagnon.components.menu.song.SongItemMenu
import app.n_zik.compagnon.components.player.background.Shader
import app.n_zik.compagnon.components.player.background.animatedGradient
import app.n_zik.compagnon.components.player.background.shaderBackground
import app.n_zik.compagnon.components.player.background.shaders.BlackCherryCosmos
import app.n_zik.compagnon.components.player.background.shaders.GlossyGradients
import app.n_zik.compagnon.components.player.background.shaders.GoldenMagma
import app.n_zik.compagnon.components.player.background.shaders.GradientFlow
import app.n_zik.compagnon.components.player.background.shaders.IceReflection
import app.n_zik.compagnon.components.player.background.shaders.InkFlow
import app.n_zik.compagnon.components.player.background.shaders.MeshGradient
import app.n_zik.compagnon.components.player.background.shaders.MesmerizingLens
import app.n_zik.compagnon.components.player.background.shaders.OilFlow
import app.n_zik.compagnon.components.player.background.shaders.PurpleLiquid
import app.n_zik.compagnon.components.player.background.shaders.Stage
import app.n_zik.compagnon.components.theme.collapsedPlayerProgressBar
import app.n_zik.compagnon.components.theme.dynamicColorPaletteOf
import app.n_zik.compagnon.components.themed.animateBrushRotation
import app.n_zik.compagnon.components.ui.screens.home.ItemActions
import app.n_zik.compagnon.core.network.ArtworkKey
import app.n_zik.compagnon.core.palette.toPaletteBitmap
import app.n_zik.compagnon.enums.AnimatedGradient
import app.n_zik.compagnon.generated.resources.*
import app.n_zik.compagnon.utils.LocalPreferences
import app.n_zik.compagnon.utils.hasExplicitPrefix
import app.n_zik.compagnon.typography
import app.n_zik.compagnon.uiRoundnessShape
import app.n_zik.compagnon.utils.UserSettings
import app.n_zik.compagnon.utils.formatAsTime
import app.n_zik.compagnon.utils.positionAndDurationState
import app.n_zik.compagnon.utils.semiBold
import app.n_zik.compagnon.utils.coroutines.NzikDispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlin.random.Random

/** The phone's default violet, the accent of the dynamic palette when there is no cover (`MainActivity.kt` 1090). */
internal val VIOLET_ACCENT = Color(0.54509807f, 0.36078432f, 0.9647059f)

/**
 * Port of the full `Player` (phone's `app/it/fast4x/rimusic/ui/screens/player/Player.kt` 390-2523):
 * `PlayerType.Essential`, top actions bar shown, cover shown, total queue time shown, the `Modern` queue
 * opened in its inline panel. The player's appearance keys (spec `spec-fill-pc-ui-gaps-player`) come from
 * `UserSettings` with the phone's defaults, so the default render is the phone's
 * `PlayerBackgroundColors.AnimatedGradient` with `AnimatedGradient.M3EMorphingCover`.
 *
 * - Background: the phone's six modes (`Player.kt` 906-1184) — blurred cover, palette, cover colour, theme
 *   colour, the two gradients and the animated gradient (its 17 styles: the fluid / M3E brush rotations,
 *   the 30 s linear rotation and the phone's HypnoticCanvas shaders).
 * - Top bar (90 % wide, 30 dp): the chevron that closes the player, the app logo (closes it too, the phone
 *   then goes home), the ⋮ menu (the phone's `PlayerMenu` layout, `SongItemMenu(playerMenu = true)`).
 * - The cover ([Thumbnail]) shrinking while paused, the total queue time, [Controls], the [ActionBar].
 * - The queue: [QueuePanel] at 65 % of the window, opened from the action bar.
 * - The [BlurredCover] under the background (`Player.kt` 1418, 1967).
 * - Landscape when the window is wider than tall ([isPlayerLandscape], the phone's orientation test): the
 *   cover in the left half (30 % thumbnail size), the controls bottom-aligned and the action bar on the right,
 *   no top bar (`Player.kt` 1300-1737).
 *
 * - The cover's double tap rotates the like (`Player.kt` 1205-1215, both orientations); the cover shrinks
 *   with `shouldBePlaying` (playing or buffering).
 * - The back (Escape) closes the inline queue first, then the player (the phone's `BackHandler`, through the
 *   window's `LocalPlayerQueueState`).
 * - Since contract 1.10.0 (`ui.settings`): the phone's light / system / dark mode, its blur adjuster.
 *
 * Dropped (contract v1 or PC): the `Modern` layout and the expanded player (not the default), lyrics,
 * visualizer, stats for nerds, sleep timer, the video search sheet, the system bar insets, the cover's tap /
 * long press (lyrics / stats: not ported). The cover's horizontal swipe is ported (right = previous, left =
 * next, the phone's `playerSwipeSensitivity`, its `Player.kt` 942-974, 1218-1241) beside the double tap.
 * Not in `UserSettings` (the phone's keys, off by default): `bottomGradient` / `blackgradient` (the PC
 * renders their false branches) and `shakeSensitivityTheme` (its shape perturbation is ported in
 * [shakeShapes], never triggered — no accelerometer on a PC).
 * Not linked (audit 2026-10-10 pass 5): a click on the title / artists (the phone's `Modern.kt`
 * 172-180, 333-345: its album / artist route) does nothing — the open page needs the album / artist
 * header the player state does not carry; only the icons' routes are documented above.
 */
@Composable
fun Player(
    onDismiss: () -> Unit,
) {
    val repository = LocalPlayerRepository.current ?: return
    val onCommand = LocalCommandLauncher.current
    val menuState = LocalMenuState.current
    val playerState by repository.state.collectAsState()
    val connection by repository.connection.collectAsState()
    val live = connection == ConnectionState.Live
    val playback = SessionContract.FEATURE_PLAYBACK in repository.features
    val queueFeature = SessionContract.FEATURE_QUEUE in repository.features

    val rotateState = rememberSaveable { mutableStateOf(false) }
    val isRotated by rotateState
    val rotationAngle by animateFloatAsState(
        targetValue = if (isRotated) 360F else 0f,
        animationSpec = tween(durationMillis = 200), label = "",
    )

    // Hoisted by the window (the back closes the queue first, the phone's BackHandler); local fallback
    val localQueueState = rememberSaveable { mutableStateOf(false) }
    val showQueueState = app.n_zik.compagnon.core.navigation.LocalPlayerQueueState.current ?: localQueueState
    var showQueue by showQueueState

    val state = playerState ?: return
    val mediaItem = state.currentTrack ?: return
    if (state.queue.isEmpty()) return

    val positionAndDurationState = repository.positionAndDurationState(state, live)
    val durationState = positionAndDurationState.value.second

    val color = colorPalette()
    var dynamicColorPalette by remember { mutableStateOf(color) }
    var dominant by remember { mutableIntStateOf(0) }
    var vibrant by remember { mutableIntStateOf(0) }
    var lightVibrant by remember { mutableIntStateOf(0) }
    var darkVibrant by remember { mutableIntStateOf(0) }
    var muted by remember { mutableIntStateOf(0) }
    var lightMuted by remember { mutableIntStateOf(0) }
    var darkMuted by remember { mutableIntStateOf(0) }
    // True once the cover swatches above have been extracted; false until then and after an
    // artwork failure -- the cover backgrounds fall back to the local dynamic palette until loaded.
    @Suppress("VARIABLE_WITH_REDUNDANT_INITIALIZER")
    var coverSwatchesLoaded by remember { mutableStateOf(false) }

    // The phone's `lightTheme` (`Player.kt`): `Light`, or `System` on a light desktop — its
    // `colorPaletteMode`, read since contract 1.10.0 (`ui.settings`; `Dark` by default)
    val uiSettings = LocalUiSettings.current
    val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val lightTheme = uiSettings.isLightMode || (uiSettings.isSystemMode && !systemDark)
    fun saturate(color: Int): Color = m3eSaturate(color, lightTheme)
    fun Color.darkenBy(): Color = m3eDarkenBy(lightTheme)

    LaunchedEffect(mediaItem.id, mediaItem.hasArtwork) {
        try {
            val bitmap = (if (mediaItem.hasArtwork) repository.artwork(ArtworkKey.track(mediaItem.id, PLAYER_ARTWORK_SIZE_PX)) else null)
                ?: throw Exception("Bitmap is null")

            val paletteResult = withContext(NzikDispatchers.MEDIA) {
                computePlayerDynamicPalette(bitmap.toPaletteBitmap(), !lightTheme, color)
            }

            dynamicColorPalette = paletteResult.palette
            dominant = paletteResult.dominant
            vibrant = paletteResult.vibrant
            lightVibrant = paletteResult.lightVibrant
            darkVibrant = paletteResult.darkVibrant
            muted = paletteResult.muted
            lightMuted = paletteResult.lightMuted
            darkMuted = paletteResult.darkMuted
            coverSwatchesLoaded = true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            dynamicColorPalette = dynamicColorPaletteOf(VIOLET_ACCENT, !lightTheme)
            coverSwatchesLoaded = false
        }
    }

    var totalPlayTimes = 0L
    state.queue.forEach {
        totalPlayTimes += it.durationMs ?: 0
    }

    val goHome = app.n_zik.compagnon.core.navigation.LocalGoHome.current
    val likeWritesForCover = LocalLibraryActions.current?.takeIf { it.canWrite }
    // Collected unconditionally (no conditional composable call): a remembered empty flow without the lists
    val dislikeModeFlow = LocalLibraryActions.current?.lists?.dislikeMode
        ?: remember { kotlinx.coroutines.flow.MutableStateFlow<app.n_zik.compagnon.bridge.library.DislikeMode?>(null) }
    val likeRotationForCover = dislikeModeFlow.collectAsState().value?.songs != false
    val onCoverDoubleTap: () -> Unit = {
        if (live) state.currentTrack?.let { rotateTrackLike(it, likeWritesForCover, likeRotationForCover) }
    }
    val title = mediaItem.title.ifBlank { stringResource(Res.string.unknown_title) }
    // The phone's `cleanPrefix(artistTextWithFallback())` (its `Controls.kt` 120)
    val artist = mediaItem.artists?.let { app.n_zik.compagnon.utils.cleanPrefix(it) }?.takeIf { it.isNotBlank() }
        ?: stringResource(Res.string.unknown_artist)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(colorPalette().background0)
            // Clicks on blank areas stop here and never reach the screens underneath
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
    ) {
        val screenWidth = maxWidth
        val screenHeight = maxHeight

        val isLandscape = isPlayerLandscape(screenWidth, screenHeight)

        // The player's appearance (the phone's preference keys in `UserSettings`, spec
        // `spec-fill-pc-ui-gaps-player`): the background mode and its animated-gradient style
        val preferences = LocalPreferences.current
        val settings by (preferences?.settings ?: remember { MutableStateFlow(UserSettings()) }).collectAsState()
        val playerBackgroundColors = settings.playerBackgroundColorsValue
        val animatedGradient = settings.animatedGradientValue
        // The phone's `isGradientBackgroundEnabled` (its `Player.kt` 809-812)
        val isGradientBackgroundEnabled = playerBackgroundColors in setOf(
            PlayerBackgroundColors.ThemeColorGradient,
            PlayerBackgroundColors.CoverColorGradient,
            PlayerBackgroundColors.AnimatedGradient,
        )

        // The phone's `valueGrad` / `tempGradient` (its `Player.kt` 883-902): `Random` picks a style per
        // track — its indices 2..13 (`M3EMorphingTheme`..`Stage`), never a fluid gradient nor another `Random`
        var valueGrad by remember { mutableStateOf(2) }
        var tempGradient by remember { mutableStateOf(AnimatedGradient.Linear) }
        if (animatedGradient == AnimatedGradient.Random) {
            LaunchedEffect(mediaItem.id) {
                valueGrad = (2..13).random()
            }
            tempGradient = AnimatedGradient.entries[valueGrad]
        }

        var sizeShader by remember { mutableStateOf(Size.Zero) }
        var containerModifier: Modifier = Modifier
            .padding(bottom = 0.dp)

        if (!isGradientBackgroundEnabled) {
            when (playerBackgroundColors) {
                // The phone's condition `playerType == Essential || (showthumbnail && !albumCoverRotation)`:
                // the PC is Essential (its only layout), so the branch is unconditional. The phone's

                // `bottomgradient` is off by default and has no PC setting entry: the gradient renders its
                // false branch (transparent), the swipe stays.
                PlayerBackgroundColors.BlurredCoverColor -> {
                    containerModifier = containerModifier
                        .background(
                            Brush.verticalGradient(
                                0.0f to Color.Transparent,
                                1.0f to Color.Transparent,
                                startY = if (isLandscape) 600f else 950f,
                                endY = Float.POSITIVE_INFINITY,
                            )
                        )
                        .background(Color.Transparent)
                        .clip(uiRoundnessShape())
                        .playerCoverSwipe(settings.playerSwipeSensitivityValue.threshold) { direction ->
                            // The live gate every other player command site carries: outside a Live
                            // session the commands would not be sent, so the swipe is inert
                            if (live) {
                                if (direction == CoverSwipeDirection.Previous) {
                                    onCommand { previous() }
                                } else {
                                    onCommand { next() }
                                }
                            }
                        }
                }
                PlayerBackgroundColors.ColorPalette -> {
                    containerModifier = containerModifier
                        .drawBehind {
                            val colors = listOf(
                                Color(dominant), Color(vibrant), Color(lightVibrant), Color(darkVibrant),
                                Color(muted), Color(lightMuted), Color(darkMuted),
                            )
                            val boxheight = (size.height) / 7
                            colors.forEachIndexed { i, _ ->
                                drawRect(
                                    color = colors[i],
                                    topLeft = Offset(0f, i * boxheight),
                                    size = Size(size.width, boxheight),
                                )
                            }
                        }
                }
                // The flat background renders the cover's dominant swatch; until the swatches load (or the
                // artwork failed) it falls back to the local dynamic palette's background
                PlayerBackgroundColors.CoverColor -> {
                    containerModifier = containerModifier
                        .background(
                            if (coverSwatchesLoaded) m3eCoverBackgroundColor(dominant, lightTheme)
                            else dynamicColorPalette.background1,
                        )
                }
                PlayerBackgroundColors.ThemeColor -> {
                    containerModifier = containerModifier
                        .background(color.background1)
                }
                // Defensive: its gradient values are excluded above and render in the other branch
                else -> {}
            }
        } else {
            when (playerBackgroundColors) {
                PlayerBackgroundColors.AnimatedGradient -> {
                    var background by remember {
                        mutableStateOf(Color.Transparent)
                    }
                    // The phone's `shaderCondition` is its `!appRunningInBackground`: always true on the PC
                    var shaderCondition by remember {
                        mutableStateOf(true)
                    }
                    var shader: Shader? by remember {
                        mutableStateOf(null)
                    }
                    val type = remember(animatedGradient, tempGradient) {
                        if (animatedGradient == AnimatedGradient.Random)
                            tempGradient
                        else
                            animatedGradient
                    }

                    when (type) {
                        // The phone's shared branch (its `Player.kt` 1024-1081): the two rotating gradients
                        // of the cover's local palette and the theme, masked, then the M3E morphing shapes

                        // in the cover's saturated / darkened swatches for the M3E styles
                        AnimatedGradient.FluidThemeColorGradient, AnimatedGradient.FluidCoverColorGradient,
                        AnimatedGradient.M3EMorphingTheme, AnimatedGradient.M3EMorphingCover -> {
                            val shaderFrom = Offset(sizeShader.width / 2f, 0f)
                            val shaderTo = Offset(sizeShader.width / 2f, sizeShader.height)
                            val brushA by animateBrushRotation(
                                shaderFrom, shaderTo,
                                listOf(dynamicColorPalette.background2, color.background2),
                                listOf(0f, 1f), sizeShader, 20_000, true,
                            )
                            val brushB by animateBrushRotation(
                                shaderFrom, shaderTo,
                                listOf(color.background1, dynamicColorPalette.accent),
                                listOf(0f, 1f), sizeShader, 12_000, false,
                            )
                            val brushMask by animateBrushRotation(
                                shaderFrom, shaderTo,
                                listOf(color.background2, Color.Transparent),
                                listOf(0f, 1f), sizeShader, 15_000, true,
                            )

                            containerModifier = containerModifier
                                .drawBehind {
                                    drawRect(brush = brushA)
                                    drawRect(brush = brushMask, blendMode = BlendMode.DstOut)
                                    drawRect(brush = brushB, blendMode = BlendMode.DstAtop)
                                }

                            if (type == AnimatedGradient.M3EMorphingTheme || type == AnimatedGradient.M3EMorphingCover) {
                                val isTheme = type == AnimatedGradient.M3EMorphingTheme
                                containerModifier = containerModifier.animatedM3EBackground(
                                    animating = state.isPlaying,
                                    D = if (isTheme) color.background1 else saturate(dominant).darkenBy(),
                                    V = if (isTheme) color.background2 else saturate(vibrant).darkenBy(),
                                    LV = if (isTheme) dynamicColorPalette.background2 else saturate(lightVibrant).darkenBy(),
                                    DV = if (isTheme) color.background1 else saturate(darkVibrant).darkenBy(),
                                    M = if (isTheme) dynamicColorPalette.background1 else saturate(muted).darkenBy(),
                                    LM = if (isTheme) dynamicColorPalette.accent else saturate(lightMuted).darkenBy(),
                                    DM = if (isTheme) color.background2 else saturate(darkMuted).darkenBy(),
                                    skipToken = state.currentTrackId,
                                )
                            }
                        }
                        AnimatedGradient.Linear -> {
                            containerModifier = containerModifier.animatedGradient(
                                state.isPlaying,
                                saturate(dominant).darkenBy(),
                                saturate(vibrant).darkenBy(),
                                saturate(lightVibrant).darkenBy(),
                                saturate(darkVibrant).darkenBy(),
                                saturate(muted).darkenBy(),
                                saturate(lightMuted).darkenBy(),
                                saturate(darkMuted).darkenBy(),
                            )
                        }
                        AnimatedGradient.Mesh -> {
                            shader = MeshGradient(
                                colors = arrayOf(
                                    saturate(vibrant).darkenBy(),
                                    saturate(lightVibrant).darkenBy(),
                                    saturate(darkVibrant).darkenBy(),
                                    saturate(muted).darkenBy(),
                                    saturate(lightMuted).darkenBy(),
                                    saturate(darkMuted).darkenBy(),
                                    saturate(dominant).darkenBy(),
                                ),
                                scale = 1f,
                            )
                        }
                        AnimatedGradient.MesmerizingLens -> shader = MesmerizingLens
                        AnimatedGradient.GlossyGradients -> {
                            if (!lightTheme)
                                background = Color.Black.copy(0.2f)
                            shader = GlossyGradients
                        }
                        AnimatedGradient.GradientFlow -> {
                            if (!lightTheme)
                                background = Color.Black.copy(0.2f)
                            shader = GradientFlow
                        }
                        AnimatedGradient.PurpleLiquid -> shader = PurpleLiquid
                        AnimatedGradient.InkFlow -> {
                            if (lightTheme)
                                background = Color.White.copy(0.4f)
                            shader = InkFlow
                        }
                        AnimatedGradient.OilFlow -> {
                            if (lightTheme)
                                background = Color.White.copy(0.4f)
                            shader = OilFlow
                        }
                        AnimatedGradient.IceReflection -> {
                            background = if (!lightTheme) Color.Black.copy(0.3f) else Color.White.copy(0.4f)
                            shader = IceReflection
                        }
                        AnimatedGradient.Stage -> {
                            if (!lightTheme)
                                background = Color.Black.copy(0.3f)
                            shader = Stage
                        }
                        AnimatedGradient.GoldenMagma -> {
                            background = if (!lightTheme) Color.Black.copy(0.2f) else Color.White.copy(0.3f)
                            shader = GoldenMagma
                        }
                        AnimatedGradient.BlackCherryCosmos -> {
                            if (lightTheme)
                                background = Color.White.copy(0.35f)
                            shader = BlackCherryCosmos
                        }
                        // if [animatedGradient] is [AnimatedGradient.Random] then it should choose
                        // [tempGradient]. If [tempGradient] is [AnimatedGradient.Random], you have

                        // a problem.
                        AnimatedGradient.Random -> throw IllegalStateException("Anything but this")
                    }

                    containerModifier = containerModifier
                        .conditional(shaderCondition && shader != null) {
                            shaderBackground(shader!!)
                        }
                        .background(background)
                        .onSizeChanged {
                            sizeShader = Size(it.width.toFloat(), it.height.toFloat())
                        }
                }
                // The phone's `blackgradient` is off by default and has no PC setting entry: the gradient
                // renders its false branch
                else -> {
                    containerModifier = containerModifier
                        .background(
                            Brush.verticalGradient(
                                0.5f to if (playerBackgroundColors == PlayerBackgroundColors.CoverColorGradient)
                                    (if (coverSwatchesLoaded) m3eCoverBackgroundColor(dominant, lightTheme) else dynamicColorPalette.background1)
                                else color.background1,
                                1.0f to if (playerBackgroundColors == PlayerBackgroundColors.CoverColorGradient)
                                    (if (coverSwatchesLoaded) m3eCoverBackgroundColor(dominant, lightTheme) else dynamicColorPalette.background2)
                                else color.background2,
                                startY = 0.0f,
                                endY = 1500.0f,
                            ),
                        )
                }
            }
        }

        @Composable
        fun Controller(modifier: Modifier) = Controls(
            state = state,
            title = title,
            // The phone's `MediaItem.isExplicit` (`Utils.kt` 405-411) counts the title's `e:` prefix too;
            // the phone's bridge already folds it into `Track.isExplicit` (`TrackMapping.kt` 88)
            isExplicit = mediaItem.isExplicit || mediaItem.title.hasExplicitPrefix(),
            artist = artist,
            position = { positionAndDurationState.value.first },
            duration = { durationState },
            live = live,
            dynamicColorPalette = dynamicColorPalette,
            modifier = modifier,
            isLandscape = isLandscape,
        )

        Box {
            BlurredCover(
                key = if (mediaItem.hasArtwork) ArtworkKey.track(mediaItem.id, PLAYER_ARTWORK_SIZE_PX) else null,
                contentScale = ContentScale.FillHeight,
            )

            if (isLandscape) {
                // The phone's landscape `PlayerType.Essential` layout (`Player.kt` 1418-1737): the cover in the
                // left half, then the controls and the action bar
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = containerModifier,
                ) {
                    Column(
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .fillMaxHeight()
                            .animateContentSize(),
                    ) {
                        // The phone omits the whole cover box without its `showthumbnail` (`Player.kt` 1457)
                        if (uiSettings.showThumbnail) Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxWidth(0.5f),
                        ) {
                            run {
                                Thumbnail(
                                    state = state,
                                    onDoubleTap = onCoverDoubleTap,
                                    // The phone's horizontal swipe on the cover (its `Player.kt` 1218-1241)
                                    swipeThreshold = settings.playerSwipeSensitivityValue.threshold,
                                    onCoverSwipe = { direction ->
                                        // The live gate every other player command site carries
                                        if (live) {
                                            if (direction == CoverSwipeDirection.Previous) {
                                                onCommand { previous() }
                                            } else {
                                                onCommand { next() }
                                            }
                                        }
                                    },
                                    // The phone's rotating cover (its `Thumbnail.kt` 175, 269-288) and its disc style
                                    coverAnimationEnabled = settings.showCoverThumbnailAnimation,
                                    coverStyle = settings.coverThumbnailAnimationValue,
                                    // The phone's `swipeAnimationsNoThumbnail`: the track-change transition
                                    swipeAnimation = settings.swipeAnimationsNoThumbnailValue,
                                    modifier = Modifier
                                        // thumbnailSizeLDp = 30 (the default)
                                        .padding(all = ((100f - 30f) * 0.5f).dp)
                                        .thumbnailpause(
                                            // The phone's `shouldBePlaying` (`playWhenReady`): playing, or buffering to play
                                            shouldBePlaying = state.shouldBePlaying,
                                        ),
                                )
                            }
                        }
                    }
                    Column(
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(top = 40.dp),
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            if (playback) {
                                Controller(
                                    Modifier.padding(vertical = 8.dp)
                                        .fillMaxHeight(),
                                )
                            }
                        }
                        ActionBar(
                            state = state,
                            showQueueState = showQueueState,
                            live = live && queueFeature,
                            showShuffle = playback,
                            isLandscape = true,
                        )
                    }
                }
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = containerModifier,
                ) {
                    // showTopActionsBar
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .height(30.dp),
                    ) {

                        Image(
                            painter = painterResource(Res.drawable.chevron_down),
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(colorPalette().collapsedPlayerProgressBar),
                            modifier = Modifier
                                .clip(uiRoundnessShape()).clickable {
                                    onDismiss()
                                }
                                .rotate(rotationAngle)
                                .size(24.dp),
                        )

                        Image(
                            painter = painterResource(Res.drawable.ic_launcher_monochrome),
                            colorFilter = ColorFilter.tint(colorPalette().collapsedPlayerProgressBar),
                            contentDescription = stringResource(Res.string.cd_app_icon_in_player),
                            modifier = Modifier.size(24.dp)
                                .clip(uiRoundnessShape()).clickable {
                                    // The phone's logo: closes the player and goes home
                                    onDismiss()
                                    goHome?.invoke()
                                },
                        )

                        // !showButtonPlayerMenu
                        Image(
                            painter = painterResource(Res.drawable.ellipsis_vertical),
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(colorPalette().collapsedPlayerProgressBar),
                            modifier = Modifier
                                .clip(uiRoundnessShape()).clickable {
                                    // Always opens, as on the phone; without `queue` its entries stay inert
                                    menuState.display {
                                        SongItemMenu(
                                            song = mediaItem,
                                            actions = ItemActions(
                                                onPlay = {},
                                                onPlayNext = { onCommand { addTracks(listOf(mediaItem.id), QueuePosition.Next) } },
                                                onEnqueue = { onCommand { addTracks(listOf(mediaItem.id), QueuePosition.End) } },
                                                enabled = live && queueFeature,
                                            ),
                                            // The phone's ⋮ opens `PlayerMenu` (its `Player.kt` 2042-2055)
                                            playerMenu = true,
                                        ).MenuComponent()
                                    }
                                }
                                .rotate(rotationAngle)
                                .size(24.dp),
                        )
                    }
                    Spacer(
                        modifier = Modifier
                            .height(5.dp),
                    )

                    BoxWithConstraints(
                        contentAlignment = Alignment.Center,
                        modifier = if (screenWidth <= (screenHeight / 2)) {
                            Modifier.height(screenWidth)
                        } else {
                            Modifier.weight(1f)
                        },
                    ) {
                        // showthumbnail, PlayerType.Essential: thumbnailContent()
                        // The phone shows the cover only with its `showthumbnail` (`Player.kt` 1457, 2104)
                        if (uiSettings.showThumbnail) {
                            Thumbnail(
                                state = state,
                                // The phone's double tap (`Player.kt` 1205-1215): the current track's like rotation
                                onDoubleTap = onCoverDoubleTap,
                                // The phone's horizontal swipe on the cover (its `Player.kt` 1218-1241)
                                swipeThreshold = settings.playerSwipeSensitivityValue.threshold,
                                onCoverSwipe = { direction ->
                                    // The live gate every other player command site carries
                                    if (live) {
                                        if (direction == CoverSwipeDirection.Previous) {
                                            onCommand { previous() }
                                        } else {
                                            onCommand { next() }
                                        }
                                    }
                                },
                                // The phone's rotating cover (its `Thumbnail.kt` 175, 269-288) and its disc style
                                coverAnimationEnabled = settings.showCoverThumbnailAnimation,
                                coverStyle = settings.coverThumbnailAnimationValue,
                                // The phone's `swipeAnimationsNoThumbnail`: the track-change transition
                                swipeAnimation = settings.swipeAnimationsNoThumbnailValue,
                                modifier = Modifier
                                    // thumbnailSizeDp = 90 (the default)
                                    .padding(all = ((100f - 90f) * 1.5f).dp)
                                    .thumbnailpause(
                                        // The phone's `shouldBePlaying` (`playWhenReady`): playing, or buffering to play
                                        shouldBePlaying = state.shouldBePlaying,
                                    ),
                            )
                        }
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f),
                    ) {
                        // showTotalTimeQueue
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier,
                        ) {
                            Image(
                                painter = painterResource(Res.drawable.time),
                                colorFilter = ColorFilter.tint(colorPalette().accent),
                                modifier = Modifier
                                    .size(20.dp)
                                    .padding(horizontal = 5.dp),
                                contentDescription = stringResource(Res.string.cd_background_image),
                                contentScale = ContentScale.Fit,
                            )

                            Box {
                                BasicText(
                                    text = " ${formatAsTime(totalPlayTimes)}",
                                    style = typography().xxs.semiBold.merge(
                                        TextStyle(
                                            textAlign = TextAlign.Center,
                                            color = colorPalette().text,
                                        ),
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                // The phone's text outline over the queue time (`Player.kt` 2305-2321)
                                BasicText(
                                    text = " ${formatAsTime(totalPlayTimes)}",
                                    style = typography().xxs.semiBold.merge(
                                        TextStyle(
                                            textAlign = TextAlign.Center,
                                            drawStyle = androidx.compose.ui.graphics.drawscope.Stroke(
                                                width = 1f,
                                                join = androidx.compose.ui.graphics.StrokeJoin.Round,
                                            ),
                                            color = durationOutlineColorOf(uiSettings.textOutline, colorPalette()),
                                        ),
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }

                        Spacer(
                            modifier = Modifier
                                .height(10.dp),
                        )

                        Box(modifier = Modifier.weight(1f)) {
                            if (playback) {
                                Controller(
                                    Modifier.padding(vertical = 4.dp)
                                        .fillMaxWidth(),
                                )
                            }
                        }

                        ActionBar(
                            state = state,
                            showQueueState = showQueueState,
                            live = live && queueFeature,
                            showShuffle = playback,
                        )
                    }
                }
            }
        }

        // Inline resizable queue panel
        val queuePanelHeightFraction = remember { Animatable(0f) }
        var isQueuePanelVisible by remember { mutableStateOf(false) }
        val queuePanelCoroutineScope = rememberCoroutineScope()

        LaunchedEffect(showQueue) {
            if (showQueue) {
                isQueuePanelVisible = true
                queuePanelHeightFraction.snapTo(0f)
                queuePanelHeightFraction.animateTo(
                    targetValue = 0.65f,
                    animationSpec = spring(dampingRatio = 0.8f, stiffness = 300f),
                )
            } else if (isQueuePanelVisible) {
                queuePanelHeightFraction.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(durationMillis = 200, easing = LinearOutSlowInEasing),
                )
                isQueuePanelVisible = false
            }
        }

        if (isQueuePanelVisible) {
            val density = LocalDensity.current
            val screenHeightPx = with(density) { screenHeight.roundToPx() }
            // The phone's status-bar exclusion: on the PC, the header's height (story 11c)
            val maxFraction = queuePanelMaxFraction(screenHeightPx, with(density) { APP_HEADER_HEIGHT.roundToPx() })

            QueuePanel(
                queuePanelHeightFraction = queuePanelHeightFraction,
                screenHeightPx = screenHeightPx,
                maxFraction = maxFraction,
                onDismiss = { showQueue = false },
                onDrag = { block -> queuePanelCoroutineScope.launch { block() } },
                handleColor = colorPalette().textSecondary,
            )
        }
    }
}

/** Whether the full player takes the phone's landscape layout: the window is wider than tall, the phone's orientation test (`Configuration.ORIENTATION_LANDSCAPE`). */
internal fun isPlayerLandscape(width: Dp, height: Dp): Boolean = width > height

/** The phone's `Modifier.conditional` (its `Player.kt` 888-895): applies a composable modifier only when [condition]. */
@Composable
private fun Modifier.conditional(
    condition: Boolean,
    modifier: @Composable Modifier.() -> Modifier,
): Modifier = if (condition) {
    then(modifier(Modifier))
} else {
    this
}
