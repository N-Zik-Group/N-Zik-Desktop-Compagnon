package app.n_zik.compagnon.components.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.n_zik.compagnon.LocalCommandLauncher
import app.n_zik.compagnon.LocalPlayerRepository
import app.n_zik.compagnon.components.LocalMenuState
import app.n_zik.compagnon.bridge.ConnectionState
import app.n_zik.compagnon.bridge.state.AudioOutput
import app.n_zik.compagnon.bridge.state.SessionContract
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
import app.n_zik.compagnon.generated.resources.heart
import app.n_zik.compagnon.generated.resources.pause
import app.n_zik.compagnon.generated.resources.play
import app.n_zik.compagnon.generated.resources.play_skip_back
import app.n_zik.compagnon.generated.resources.play_skip_forward
import app.n_zik.compagnon.generated.resources.unknown_artist
import app.n_zik.compagnon.generated.resources.unknown_title
import app.n_zik.compagnon.thumbnailShape
import app.n_zik.compagnon.typography
import app.n_zik.compagnon.uiRoundnessShape
import app.n_zik.compagnon.utils.TIME_UNSET
import app.n_zik.compagnon.utils.onSecondaryClick
import app.n_zik.compagnon.utils.positionAndDurationState
import app.n_zik.compagnon.utils.semiBold
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.absoluteValue

/**
 * Port of `MiniPlayer` (phone's `app/it/fast4x/rimusic/ui/screens/player/MiniPlayer.kt` 205-815), with its
 * default preferences: `MiniPlayerType.Essential`, the floating navigation bar (72 dp, 16 dp from the sides,
 * 8 dp shadow, UI roundness), `background2`, the progress drawn under the content in `favoritesOverlay`
 * (`BackgroundProgress.MiniPlayer`), `Monochrome` controls, scrolling texts, the default buttons
 * (previous, play / pause in its 42 dp box, next).
 *
 * Kept: the 48 dp cover in the thumbnail shape with the now-playing animation and the liked heart (10 dp,
 * -5 dp at the bottom left: `Track.isLiked` is only a boolean, so no disliked heart), title and artists in
 * xxs.semiBold. A click opens the player ([showPlayer]); a long press (a right click on the PC) opens the
 * queue ([onShowQueue], the phone's queue route intercepted into its overlay).
 * Dropped: the swipe actions (like / previous / next: no swipe on the PC, no like in contract v1), the
 * explicit badge (not in the contract), the buffering ring (no buffering state), the other optional buttons
 * (off by default), the rotation effect (off by default), the mini-player's own cover palette (only used by
 * the non-default `Cover` controls colour).
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
    val mediaItem = currentState.currentTrack ?: return
    val shouldBePlaying = currentState.isPlaying

    // PlayerControlsColors.Monochrome follows the effective palette tone
    val controlsColorText = monochromeControlsColor(colorPalette())

    val positionAndDurationState = repository.positionAndDurationState(currentState, live)
    val durationState = positionAndDurationState.value.second

    val isRotated by rememberSaveable { mutableStateOf(false) }
    val rotationAngle by animateFloatAsState(
        targetValue = if (isRotated) 360F else 0f,
        animationSpec = tween(durationMillis = 200), label = "",
    )
    val disableScrollingText = false

    val shape = uiRoundnessShape()

    Box(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .shadow(elevation = 8.dp, shape = shape)
            .clip(shape),
    ) {
        val colorPalette = colorPalette()
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
            modifier = Modifier
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
                        contentScale = ContentScale.FillHeight,
                        modifier = Modifier.clip(thumbnailShape())
                            .fillMaxSize(),
                    )

                    NowPlayingSongIndicator(isPlaying = shouldBePlaying, containerSize = 48.dp)

                    if (mediaItem.isLiked) {
                        HeaderIconButton(
                            onClick = {},
                            icon = Res.drawable.heart,
                            color = colorPalette().favoritesIcon,
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
                    BasicText(
                        text = mediaItem.title.ifBlank { stringResource(Res.string.unknown_title) },
                        style = typography().xxs.semiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = if (!disableScrollingText) Modifier.basicMarquee(iterations = Int.MAX_VALUE) else Modifier,
                    )
                }

                BasicText(
                    text = mediaItem.artists?.takeIf { it.isNotBlank() } ?: stringResource(Res.string.unknown_artist),
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
