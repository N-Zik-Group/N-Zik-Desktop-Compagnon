package app.n_zik.compagnon.components.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.n_zik.compagnon.LocalCommandLauncher
import app.n_zik.compagnon.LocalPlayerRepository
import app.n_zik.compagnon.components.LocalMenuState
import app.n_zik.compagnon.bridge.ConnectionState
import app.n_zik.compagnon.bridge.state.AudioOutput
import app.n_zik.compagnon.bridge.state.SessionContract
import app.n_zik.compagnon.bridge.state.Track
import app.n_zik.compagnon.bridge.state.TrackLike
import app.n_zik.compagnon.bridge.state.displayedLike
import app.n_zik.compagnon.components.menu.player.AudioDeviceMenu
import app.n_zik.compagnon.colorPalette
import app.n_zik.compagnon.components.SONG_THUMBNAIL_SIZE_PX
import app.n_zik.compagnon.components.styling.Dimensions
import app.n_zik.compagnon.components.theme.favoritesIcon
import app.n_zik.compagnon.components.theme.favoritesOverlay
import app.n_zik.compagnon.components.themed.HeaderIconButton
import app.n_zik.compagnon.components.themed.IconButton
import app.n_zik.compagnon.components.themed.NowPlayingSongIndicator
import app.n_zik.compagnon.core.coil.ImageCacheFactory
import app.n_zik.compagnon.core.network.ArtworkKey
import app.n_zik.compagnon.generated.resources.Res
import app.n_zik.compagnon.generated.resources.computer
import app.n_zik.compagnon.generated.resources.devices
import app.n_zik.compagnon.generated.resources.explicit
import app.n_zik.compagnon.generated.resources.heart
import app.n_zik.compagnon.generated.resources.heart_dislike
import app.n_zik.compagnon.generated.resources.pause
import app.n_zik.compagnon.generated.resources.play
import app.n_zik.compagnon.generated.resources.play_skip_back
import app.n_zik.compagnon.generated.resources.play_skip_forward
import app.n_zik.compagnon.generated.resources.unknown_artist
import app.n_zik.compagnon.generated.resources.unknown_title
import app.n_zik.compagnon.thumbnailShape
import app.n_zik.compagnon.typography
import app.n_zik.compagnon.uiRoundnessShape
import app.n_zik.compagnon.utils.LocalPreferences
import app.n_zik.compagnon.utils.TIME_UNSET
import app.n_zik.compagnon.utils.cleanPrefix
import app.n_zik.compagnon.utils.hasExplicitPrefix
import app.n_zik.compagnon.utils.onSecondaryClick
import app.n_zik.compagnon.utils.positionAndDurationState
import app.n_zik.compagnon.utils.semiBold
import app.n_zik.compagnon.utils.UserSettings
import kotlinx.coroutines.flow.MutableStateFlow
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

/** Duration of the animated pop that brings the mini-player back from dismissed to rest (the phone's
 * `MiniPlayerAutoExpand.kt` constant, copied). */
const val MINIPLAYER_POP_ANIMATION_MS = 250L

/** Fade-in duration of the sheet subtree (mini-player) when it appears with new media (the phone's
 * `MiniPlayerAutoExpand.kt` constant, copied). */
const val MINIPLAYER_APPEAR_FADE_MS = 300L

/**
 * Frames awaited before the appearance fade starts, so the heavy first composition of the
 * mini-player does not swallow the fade (the phone's `MiniPlayerAutoExpand.kt` constant, copied).
 */
const val MINIPLAYER_APPEAR_FADE_SKIPPED_FRAMES = 2

/**
 * Port of `MiniPlayer` (phone's `app/it/fast4x/rimusic/ui/screens/player/MiniPlayer.kt` 205-815), with its
 * default preferences: `MiniPlayerType.Essential`, the floating navigation bar (72 dp, 16 dp from the sides,
 * 8 dp shadow, UI roundness), `background2`, the progress drawn under the content in `favoritesOverlay`
 * (`BackgroundProgress.MiniPlayer`), `Monochrome` controls, the scrolling texts (dropped when the
 * "Disable scrolling text" setting is on, the phone's `MiniPlayer.kt` 518, 685, 695), the default buttons
 * (previous, play / pause in its 42 dp box, next).
 *
 * Kept: the 48 dp cover in the thumbnail shape with the now-playing animation and the like heart (10 dp,
 * -5 dp at the bottom left, the tri-state indicator of contract 1.7: nothing when neutral, `heart` in
 * `favoritesIcon` when liked, the phone's `heart_dislike` in red when disliked), the 14 dp explicit
 * badge before the title (`Track.isExplicit`, contract 1.3), title and artists in xxs.semiBold. A click opens the
 * player ([showPlayer]); a long press (a right click on the PC) opens the queue ([onShowQueue], the
 * phone's queue route intercepted into its overlay).
 * The buffering ring (contract 1.4: `currentState.isBuffering`) replaces the play / pause icon while the
 * phone buffers (phone's 727-737: `CircularWavyProgressIndicator` in accent over the text track, 24 dp,
 * stroke 2 dp; the phone's `&& shouldBePlaying` is implied — `STATE_BUFFERING` only happens while it plays).
 * Kept as a drag: the phone's swipe-down (its `MainActivity.kt` 2298-2306) stops the phone's playback
 * (`stopRadio + clearMediaItems + stopService`) and the player auto-closes in its 400 ms grace; here
 * the same gesture is a downward drag on the bar (beyond its 80 dp dismissed zone), sending the
 * phone's `clear` command (its stop), with the phone's own pop/depop animations copied (its commit
 * 765d811): the `miniPlayerDismissAlpha` fade (the depop, 1 → 0 across the dismissed zone, following
 * the pointer frame by frame) and the 250 ms pop back (its `MiniPlayerAutoExpand.kt` tween). The bar
 * also keeps its last track so it stays rendered through the sheet's appear fade (300 ms, 2 heavy
 * frames skipped) and its animated leave (the phone's `dismiss()`, slide + fade, removed once
 * settled) — both at the sheet level in `MainActivity`, as on the phone. The phone's
 * `disableClosingPlayerSwipingDown` setting is outside the contract, so the PC's bar always answers
 * the drag. Dropped: the swipe actions (like / previous / next: no swipe on the PC), the other
 * optional buttons (off by default), the rotation effect (off by default), the mini-player's own
 * cover palette (only used by the non-default `Cover` controls colour).
 * The "audio output" button (`MiniPlayerButton.AudioOutput`, on by default, phone's 997-1040) opens the
 * clone of `AudioDeviceMenu` (this PC / [phoneName], story 12). Like the phone's, it is accented with the
 * device's icon when the sound leaves its default output: here when it plays on this PC. Hidden without the
 * phone's `audio.output` feature (a 1.1 phone).
 * Outside a `Live` session the buttons are dimmed and do nothing.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MiniPlayer(
    showPlayer: () -> Unit,
    hidePlayer: () -> Unit,
    onShowQueue: (() -> Unit)? = null,
    phoneName: String = "",
) {
    val repository = LocalPlayerRepository.current ?: return
    val onCommand = LocalCommandLauncher.current
    val state by repository.state.collectAsState()
    val connection by repository.connection.collectAsState()
    val live = connection == ConnectionState.Live
    val playback = SessionContract.FEATURE_PLAYBACK in repository.features
    val audioOutputFeature = SessionContract.FEATURE_AUDIO in repository.features &&
        SessionContract.FEATURE_AUDIO_OUTPUT in repository.features
    val menuState = LocalMenuState.current

    val currentState = state ?: return
    val currentTrack = currentState.currentTrack
    // The bar keeps its last track (the phone's mini-player port, its commit 765d811): while the media
    // is gone, the sheet plays its animated leave (its `dismiss()`, in `MainActivity`) and the bar must
    // keep rendering through the whole slide + fade — the phone's 400 ms media grace keeps it composed
    // that long
    val lastTrack = remember { mutableStateOf<Track?>(null) }
    LaunchedEffect(currentTrack?.id) { currentTrack?.let { lastTrack.value = it } }
    val mediaItem = currentTrack ?: lastTrack.value ?: return
    // The phone's `Player.shouldBePlaying` (utils/Player.kt 69): true while buffering too
    // (`STATE_BUFFERING` implies `playWhenReady`) — contract 1.4 `isBuffering`.
    val shouldBePlaying = currentState.isPlaying || currentState.isBuffering

    // PlayerControlsColors.Monochrome follows the effective palette tone
    val controlsColorText = monochromeControlsColor(colorPalette())

    val positionAndDurationState = repository.positionAndDurationState(currentState, live)
    val durationState = positionAndDurationState.value.second

    val isRotated by rememberSaveable { mutableStateOf(false) }
    val rotationAngle by animateFloatAsState(
        targetValue = if (isRotated) 360F else 0f,
        animationSpec = tween(durationMillis = 200), label = "",
    )
    // The phone's "Disable scrolling text" (the phone's `MiniPlayer.kt` 518, 685, 695): the title /
    // artists marquee is dropped when set
    val preferences = LocalPreferences.current
    val settings by (preferences?.settings ?: remember { MutableStateFlow(UserSettings()) }).collectAsState()
    val disableScrollingText = settings.disableScrollingText

    val shape = uiRoundnessShape()

    // The phone's swipe-down (its `MainActivity.kt` 2298-2306): stops the phone's playback
    // (`stopRadio + clearMediaItems + stopService`); here the same drag sends the phone's `clear`
    // command (its stop). The drag travels the phone's dismissed zone (80 dp) with the
    // `miniPlayerDismissAlpha` fade (the depop, following the pointer), and the 250 ms pop brings the
    // bar back. A new track pops it back from a dismissed drag (the phone's `showMiniplayerIfDismissed`).
    val slideDistance = with(LocalDensity.current) { 80.dp.toPx() } // the phone's dismissed zone
    var isDragging by remember { mutableStateOf(false) }
    var dragOffsetY by remember { mutableStateOf(0f) }
    LaunchedEffect(mediaItem.id) { dragOffsetY = 0f } // a new track pops the bar back
    val animatedDragY by animateFloatAsState(
        targetValue = dragOffsetY,
        // The phone's `MiniPlayerAutoExpand`: the return from the dismissed zone is a 250 ms tween,
        // and the drag itself follows the pointer frame by frame
        animationSpec = if (isDragging) tween(0) else tween(MINIPLAYER_POP_ANIMATION_MS.toInt()),
        label = "miniPlayerDrag",
    )
    // The phone's `miniPlayerDismissAlpha` (its `MiniPlayerDismissFade.kt`): 1 at rest, fading to 0 as
    // the bar travels its dismissed distance, linear in between — it follows the pointer on a dismiss
    // drag (the depop) and fades back in on the pop
    val dismissAlpha = (1f - animatedDragY / slideDistance).coerceIn(0f, 1f)

    Box(
        modifier = Modifier
            .offset { IntOffset(0, animatedDragY.roundToInt()) }
            .alpha(dismissAlpha)
            .padding(horizontal = 16.dp)
            .shadow(elevation = 8.dp, shape = shape)
            .clip(shape),
    ) {
        val colorPalette = colorPalette()
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
            modifier = Modifier
                .pointerInput(mediaItem.id) {
                    detectVerticalDragGestures(
                        onDragStart = {
                            isDragging = true
                        },
                        onVerticalDrag = { _, change ->
                            // Only a downward drag counts (the phone's dismiss gesture is down-only),
                            // clamped to its dismissed bound as the phone's sheet
                            dragOffsetY = (dragOffsetY + change).coerceIn(0f, slideDistance)
                        },
                        onDragEnd = {
                            isDragging = false
                            if (dragOffsetY > slideDistance / 2 && live) {
                                // The phone's dismiss: the bar settles in its dismissed zone (the
                                // depop held there) and the phone's stop removes the media, which
                                // then plays the leave on the bar's last track
                                dragOffsetY = slideDistance
                                onCommand { clearQueue() }
                            } else {
                                dragOffsetY = 0f // the pop back
                            }
                        },
                        onDragCancel = {
                            isDragging = false
                            dragOffsetY = 0f
                        },
                    )
                }
                .clip(uiRoundnessShape())
                .onSecondaryClick(onShowQueue)
                .combinedClickable(
                    onLongClick = { onShowQueue?.invoke() },
                    onClick = {
                        showPlayer()
                    },
                )
                .background(colorPalette().background2)
                .fillMaxWidth()
                .drawBehind {
                    // BackgroundProgress.MiniPlayer (the default)
                    val duration = durationState.takeIf { it != TIME_UNSET }?.absoluteValue?.takeIf { it > 0 } ?: 1L
                    drawRect(
                        color = colorPalette.favoritesOverlay,
                        topLeft = Offset.Zero,
                        size = Size(
                            width = positionAndDurationState.value.first.toFloat() / duration * size.width,
                            height = size.maxDimension,
                        ),
                    )
                },
        ) {

            Spacer(
                modifier = Modifier
                    .width(2.dp),
            )

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.height(Dimensions.miniPlayerHeight),
            ) {
                Box(
                    modifier = Modifier.size(48.dp),
                ) {
                    ImageCacheFactory.Thumbnail(
                        key = if (mediaItem.hasArtwork) ArtworkKey.track(mediaItem.id, SONG_THUMBNAIL_SIZE_PX) else null,
                        // As on the phone (its `SongItem`'s scale since contract 1.7): a custom artwork
                        // (the phone's `isCustomArtwork`) crops, the YouTube ones fill the height
                        contentScale = if (mediaItem.hasArtwork && mediaItem.isCustomArtwork) {
                            ContentScale.Crop
                        } else {
                            ContentScale.FillHeight
                        },
                        modifier = Modifier.clip(thumbnailShape())
                            .fillMaxSize(),
                    )

                    NowPlayingSongIndicator(isPlaying = shouldBePlaying, containerSize = 48.dp)

                    // The like tri-state indicator (contract 1.7, phone's `MiniPlayer.kt` 565-568):
                    // nothing for neutral, `heart` in `favoritesIcon` when liked, the phone's
                    // `heart_dislike` in red when disliked. Information only — the action lives
                    // in the player's controls and the song's menu.
                    val like = mediaItem.displayedLike
                    if (like != TrackLike.Neutral) {
                        HeaderIconButton(
                            onClick = {},
                            icon = if (like == TrackLike.Disliked) Res.drawable.heart_dislike else Res.drawable.heart,
                            color = if (like == TrackLike.Disliked) colorPalette().red else colorPalette().favoritesIcon,
                            iconSize = 10.dp,
                            modifier = Modifier.align(Alignment.BottomStart)
                                .absoluteOffset(x = (-5).dp),
                        )
                    }
                }
            }

            Column(
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .height(Dimensions.miniPlayerHeight)
                    .weight(1f),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (mediaItem.isExplicit || mediaItem.title.hasExplicitPrefix()) {
                        // The explicit badge (phone's 14 dp icon, text colour), before the title (contract 1.3
                        // `Track.isExplicit`: the phone sends the cleaned title)
                        IconButton(
                            icon = Res.drawable.explicit,
                            color = colorPalette().text,
                            onClick = {},
                            modifier = Modifier.size(14.dp),
                        )
                    }
                    BasicText(
                        text = cleanPrefix(mediaItem.title.ifBlank { stringResource(Res.string.unknown_title) }),
                        style = typography().xxs.semiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = if (!disableScrollingText) Modifier.basicMarquee(iterations = Int.MAX_VALUE) else Modifier,
                    )
                }

                BasicText(
                    text = cleanPrefix(mediaItem.artists?.takeIf { it.isNotBlank() } ?: stringResource(Res.string.unknown_artist)),
                    style = typography().xxs.semiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = if (!disableScrollingText) Modifier.basicMarquee(iterations = Int.MAX_VALUE) else Modifier,
                )
            }

            Spacer(
                modifier = Modifier
                    .width(2.dp),
            )

            if (playback) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .height(Dimensions.miniPlayerHeight)
                        .alpha(if (live) 1f else 0.5f),
                ) {
                    val buttonModifier = Modifier
                        .rotate(rotationAngle)
                        .padding(horizontal = 2.dp, vertical = 8.dp)
                        .size(24.dp)

                    // MiniPlayerButton.SkipBack
                    IconButton(
                        icon = Res.drawable.play_skip_back,
                        color = controlsColorText,
                        enabled = live,
                        onClick = { onCommand { previous() } },
                        modifier = buttonModifier,
                    )

                    // MiniPlayerButton.PlayPause (distinct styling)
                    Box(
                        modifier = Modifier
                            .clip(uiRoundnessShape()).clickable(enabled = live) {
                                if (shouldBePlaying) {
                                    onCommand { pause() }
                                } else {
                                    onCommand { play() }
                                }
                            }
                            .background(colorPalette().background2)
                            .size(42.dp),
                    ) {
                        if (currentState.isBuffering) {
                            // The phone's buffering ring (phone's 727-737, contract 1.4)
                            CircularWavyProgressIndicator(
                                color = colorPalette().accent,
                                trackColor = colorPalette().text,
                                modifier = Modifier
                                    .rotate(rotationAngle)
                                    .align(Alignment.Center)
                                    .size(24.dp),
                                stroke = Stroke(width = with(LocalDensity.current) { 2.dp.toPx() }),
                                trackStroke = Stroke(width = with(LocalDensity.current) { 2.dp.toPx() }),
                            )
                        } else {
                            Image(
                                painter = painterResource(if (shouldBePlaying) Res.drawable.pause else Res.drawable.play),
                                contentDescription = null,
                                colorFilter = ColorFilter.tint(controlsColorText),
                                modifier = Modifier
                                    .rotate(rotationAngle)
                                    .align(Alignment.Center)
                                    .size(24.dp),
                            )
                        }
                    }

                    // MiniPlayerButton.SkipForward
                    IconButton(
                        icon = Res.drawable.play_skip_forward,
                        color = controlsColorText,
                        enabled = live,
                        onClick = { onCommand { next() } },
                        modifier = buttonModifier,
                    )

                    // MiniPlayerButton.AudioOutput
                    if (audioOutputFeature) {
                        val isExternal = currentState.audioOutput == AudioOutput.Pc
                        val finalColor = if (isExternal) colorPalette().accent else controlsColorText
                        val finalIcon = if (isExternal) Res.drawable.computer else Res.drawable.devices
                        IconButton(
                            icon = finalIcon,
                            color = finalColor,
                            enabled = true,
                            onClick = {
                                menuState.display {
                                    AudioDeviceMenu(onDismiss = menuState::hide, phoneName = phoneName)
                                }
                            },
                            modifier = buttonModifier,
                        )
                    }
                }
            }

            Spacer(
                modifier = Modifier
                    .width(2.dp),
            )
        }
    }
}
