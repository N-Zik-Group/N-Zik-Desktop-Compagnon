package app.n_zik.compagnon.components.player

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
import app.n_zik.compagnon.bridge.state.QueuePosition
import app.n_zik.compagnon.bridge.state.SessionContract
import app.n_zik.compagnon.colorPalette
import app.n_zik.compagnon.components.LocalMenuState
import app.n_zik.compagnon.components.menu.song.SongItemMenu
import app.n_zik.compagnon.components.theme.collapsedPlayerProgressBar
import app.n_zik.compagnon.components.theme.dynamicColorPaletteOf
import app.n_zik.compagnon.components.themed.animateBrushRotation
import app.n_zik.compagnon.components.ui.screens.home.ItemActions
import app.n_zik.compagnon.core.network.ArtworkKey
import app.n_zik.compagnon.core.palette.toPaletteBitmap
import app.n_zik.compagnon.generated.resources.Res
import app.n_zik.compagnon.generated.resources.cd_app_icon_in_player
import app.n_zik.compagnon.generated.resources.cd_background_image
import app.n_zik.compagnon.generated.resources.chevron_down
import app.n_zik.compagnon.generated.resources.ellipsis_vertical
import app.n_zik.compagnon.generated.resources.ic_launcher_monochrome
import app.n_zik.compagnon.generated.resources.time
import app.n_zik.compagnon.generated.resources.unknown_artist
import app.n_zik.compagnon.generated.resources.unknown_title
import app.n_zik.compagnon.utils.hasExplicitPrefix
import app.n_zik.compagnon.typography
import app.n_zik.compagnon.uiRoundnessShape
import app.n_zik.compagnon.utils.formatAsTime
import app.n_zik.compagnon.utils.positionAndDurationState
import app.n_zik.compagnon.utils.semiBold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** The phone's default violet, the accent of the dynamic palette when there is no cover (`MainActivity.kt` 1090). */
internal val VIOLET_ACCENT = Color(0.54509807f, 0.36078432f, 0.9647059f)

/**
 * Port of the full `Player` (phone's `app/it/fast4x/rimusic/ui/screens/player/Player.kt` 390-2523) with the
 * default preferences: `PlayerType.Essential`, `PlayerBackgroundColors.AnimatedGradient`
 * with `AnimatedGradient.M3EMorphingCover`, top actions bar shown, cover shown, total queue time shown, the
 * `Modern` queue opened in its inline panel.
 *
 * - Background: the two rotating gradients of the cover's local palette and the theme, masked, then the
 *   M3E morphing shapes in the cover's saturated / darkened swatches while it plays.
 * - Top bar (90 % wide, 30 dp): the chevron that closes the player, the app logo (closes it too, the phone
 *   then goes home), the ⋮ menu (`SongItemMenu` of the current track).
 * - The cover ([Thumbnail]) shrinking while paused, the total queue time, [Controls], the [ActionBar].
 * - The queue: [QueuePanel] at 65 % of the window, opened from the action bar.
 * - The [BlurredCover] under the background (`Player.kt` 1418, 1967).
 * - Landscape when the window is wider than tall ([isPlayerLandscape], the phone's orientation test): the
 *   cover in the left half (30 % thumbnail size), the controls bottom-aligned and the action bar on the right,
 *   no top bar (`Player.kt` 1300-1737).
 *
 * Dropped (contract v1 or PC): the `Modern` layout and the expanded player (not the default), lyrics, visualizer,
 * stats for nerds, sleep timer, the video search sheet, the horizontal swipe on the cover (no swipe on the PC),
 * the system bar insets, `BackHandler`. The phone's `PlayerMenu` is replaced by the song menu the Compagnon
 * has (`SongItemMenu`: play next, enqueue).
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

    val showQueueState = rememberSaveable { mutableStateOf(false) }
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

    // ColorPaletteMode.Dark (the default)
    val lightTheme = false
    fun saturate(color: Int): Color = m3eSaturate(color, lightTheme)
    fun Color.darkenBy(): Color = m3eDarkenBy(lightTheme)

    LaunchedEffect(mediaItem.id, mediaItem.hasArtwork) {
        try {
            val bitmap = (if (mediaItem.hasArtwork) repository.artwork(ArtworkKey.track(mediaItem.id, PLAYER_ARTWORK_SIZE_PX)) else null)
                ?: throw Exception("Bitmap is null")

            val paletteResult = withContext(Dispatchers.Default) {
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
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            dynamicColorPalette = dynamicColorPaletteOf(VIOLET_ACCENT, !lightTheme)
            coverSwatchesLoaded = false
        }
    }

    var sizeShader by remember { mutableStateOf(Size.Zero) }

    var totalPlayTimes = 0L
    state.queue.forEach {
        totalPlayTimes += it.durationMs ?: 0
    }

    var containerModifier: Modifier = Modifier
        .padding(bottom = 0.dp)

    // PlayerBackgroundColors.AnimatedGradient + AnimatedGradient.M3EMorphingCover
    run {
        val shaderFrom = Offset(sizeShader.width / 2f, 0f)
        val shaderTo = Offset(sizeShader.width / 2f, sizeShader.height)
        val brushA by animateBrushRotation(
            shaderFrom, shaderTo,
            listOf(dynamicColorPalette.background2, colorPalette().background2),
            listOf(0f, 1f), sizeShader, 20_000, true,
        )
        val brushB by animateBrushRotation(
            shaderFrom, shaderTo,
            listOf(colorPalette().background1, dynamicColorPalette.accent),
            listOf(0f, 1f), sizeShader, 12_000, false,
        )
        val brushMask by animateBrushRotation(
            shaderFrom, shaderTo,
            listOf(colorPalette().background2, Color.Transparent),
            listOf(0f, 1f), sizeShader, 15_000, true,
        )

        containerModifier = containerModifier
            .drawBehind {
                drawRect(brush = brushA)
                drawRect(brush = brushMask, blendMode = BlendMode.DstOut)
                drawRect(brush = brushB, blendMode = BlendMode.DstAtop)
            }
            .animatedM3EBackground(
                animating = state.isPlaying,
                D = saturate(dominant).darkenBy(),
                V = saturate(vibrant).darkenBy(),
                LV = saturate(lightVibrant).darkenBy(),
                DV = saturate(darkVibrant).darkenBy(),
                M = saturate(muted).darkenBy(),
                LM = saturate(lightMuted).darkenBy(),
                DM = saturate(darkMuted).darkenBy(),
                skipToken = state.currentTrackId,
            )
            .background(Color.Transparent)
            .onSizeChanged {
                sizeShader = Size(it.width.toFloat(), it.height.toFloat())
            }
    }

    val title = mediaItem.title.ifBlank { stringResource(Res.string.unknown_title) }
    val artist = mediaItem.artists?.takeIf { it.isNotBlank() } ?: stringResource(Res.string.unknown_artist)

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

        @Composable
        fun Controller(modifier: Modifier) = Controls(
            state = state,
            title = title,
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
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxWidth(0.5f),
                        ) {
                            Thumbnail(
                                state = state,
                                modifier = Modifier
                                    // thumbnailSizeLDp = 30 (the default)
                                    .padding(all = ((100f - 30f) * 0.5f).dp)
                                    .thumbnailpause(
                                        shouldBePlaying = state.isPlaying,
                                    ),
                            )
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
                                    onDismiss()
                                },
                        )

                        // !showButtonPlayerMenu
                        Image(
                            painter = painterResource(Res.drawable.ellipsis_vertical),
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(colorPalette().collapsedPlayerProgressBar),
                            modifier = Modifier
                                .clip(uiRoundnessShape()).clickable(enabled = queueFeature) {
                                    menuState.display {
                                        SongItemMenu(
                                            song = mediaItem,
                                            actions = ItemActions(
                                                onPlay = {},
                                                onPlayNext = { onCommand { addTracks(listOf(mediaItem.id), QueuePosition.Next) } },
                                                onEnqueue = { onCommand { addTracks(listOf(mediaItem.id), QueuePosition.End) } },
                                                enabled = live,
                                            ),
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
                        Thumbnail(
                            state = state,
                            modifier = Modifier
                                // thumbnailSizeDp = 90 (the default)
                                .padding(all = ((100f - 90f) * 1.5f).dp)
                                .thumbnailpause(
                                    shouldBePlaying = state.isPlaying,
                                ),
                        )
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

/**
 * Whether the full player takes the phone's landscape layout: the window is wider than tall, the phone's
 * orientation test (`Configuration.ORIENTATION_LANDSCAPE`).
 */
internal fun isPlayerLandscape(width: Dp, height: Dp): Boolean = width > height
