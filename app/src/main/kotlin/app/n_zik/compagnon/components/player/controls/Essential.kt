package app.n_zik.compagnon.components.player.controls

import app.n_zik.compagnon.components.theme.favoritesIcon
import app.n_zik.compagnon.generated.resources.heart_outline
import app.n_zik.compagnon.generated.resources.heart
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import app.n_zik.compagnon.LocalCommandLauncher
import app.n_zik.compagnon.bridge.state.RepeatMode
import app.n_zik.compagnon.colorPalette
import app.n_zik.compagnon.components.player.bounceClick
import app.n_zik.compagnon.components.player.monochromeControlsColor
import app.n_zik.compagnon.components.theme.ColorPalette
import app.n_zik.compagnon.components.themed.IconButton
import app.n_zik.compagnon.enums.PlayerPlayButtonType
import app.n_zik.compagnon.enums.QueueLoopType
import app.n_zik.compagnon.generated.resources.Res
import app.n_zik.compagnon.generated.resources.a13shape
import app.n_zik.compagnon.generated.resources.cd_background_image
import app.n_zik.compagnon.generated.resources.pause
import app.n_zik.compagnon.generated.resources.play
import app.n_zik.compagnon.generated.resources.play_skip_back
import app.n_zik.compagnon.generated.resources.play_skip_forward
import app.n_zik.compagnon.typography
import app.n_zik.compagnon.uiRoundnessShape
import app.n_zik.compagnon.utils.onSecondaryClick
import app.n_zik.compagnon.utils.semiBold
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Port of `ControlsEssential` (phone's `app/it/fast4x/rimusic/ui/screens/player/components/controls/Essential.kt`
 * 392-649), with its default preferences: `Monochrome` controls (the palette's text), the `CircularRibbed`
 * play button (100 dp, `a13shape` tinted `background2`), the `Dynamic` palette.
 *
 * Kept: previous (`player/previous`: the phone applies its own "back to 0 after 3 s" rule), play / pause, its
 * long press (a right click too) opening [onShowSpeedPlayerDialog], the speed shown in the button when it is not
 * 1.0x, next, the repeat button (`off` → `one` → `all`, `player/repeat`).
 * The 26 dp like button comes first (phone's 442-470: `heart` in `favoritesIcon` when the track is liked,
 * `heart_outline` otherwise); contract v1 has no like route, so it is shown without action.
 * The buffering ring (contract 1.4: `isBuffering`) replaces the play / pause icon while the phone buffers
 * (phone's 567-576: `CircularWavyProgressIndicator` in accent over the text track, 30 dp, stroke 4 dp).
 * Dropped: the rotation effect (off by default), the Listen Together lock. The play / pause icon keeps full
 * opacity (the phone never locks it).
 * PC: a right click is the long press (speed menu); outside a `Live` session the buttons are dimmed like the
 * phone's locked ones.
 * [enabled] is `false` outside a `Live` session: the buttons then do nothing.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ControlsEssential(
    playbackSpeed: Float,
    shouldBePlaying: Boolean,
    isBuffering: Boolean,
    isLiked: Boolean,
    repeatMode: RepeatMode,
    playerPlayButtonType: PlayerPlayButtonType,
    isGradientBackgroundEnabled: Boolean,
    onShowSpeedPlayerDialog: () -> Unit,
    dynamicColorPalette: ColorPalette,
    enabled: Boolean,
) {
    val onCommand = LocalCommandLauncher.current
    // PlayerControlsColors.Monochrome (the default) follows the effective palette tone
    val controlsColor = monochromeControlsColor(colorPalette())
    val disabledAlpha = if (enabled) 1f else 0.5f
    var isRotated by rememberSaveable { mutableStateOf(false) }
    val rotationAngle by animateFloatAsState(
        targetValue = if (isRotated) 360F else 0f,
        animationSpec = tween(durationMillis = 200), label = "",
    )
    val shouldBePlayingTransition = updateTransition(shouldBePlaying, label = "shouldBePlaying")
    @Suppress("UNUSED_VARIABLE")
    val playPauseRoundness by shouldBePlayingTransition.animateDp(
        transitionSpec = { tween(durationMillis = 100, easing = LinearEasing) },
        label = "playPauseRoundness",
        targetValueByState = { if (it) 32.dp else 16.dp },
    )

    val queueLoopType = QueueLoopType.from(repeatMode)

    Box {
        IconButton(
            color = colorPalette().favoritesIcon,
            icon = if (isLiked) Res.drawable.heart else Res.drawable.heart_outline,
            onClick = {},
            modifier = Modifier
                .size(26.dp),
        )
    }

    Image(
        painter = painterResource(Res.drawable.play_skip_back),
        contentDescription = null,
        colorFilter = ColorFilter.tint(controlsColor),
        modifier = Modifier
            .clip(uiRoundnessShape()).combinedClickable(
                indication = ripple(bounded = false),
                interactionSource = remember { MutableInteractionSource() },
                enabled = enabled,
                onClick = { onCommand { previous() } },
                onLongClick = {},
            )
            .rotate(rotationAngle)
            .padding(10.dp)
            .size(26.dp)
            .alpha(disabledAlpha),
    )

    Box(
        modifier = Modifier
            .clip(uiRoundnessShape()).combinedClickable(
                indication = ripple(bounded = false),
                interactionSource = remember { MutableInteractionSource() },
                enabled = enabled,
                onClick = {
                    if (shouldBePlaying) {
                        onCommand { pause() }
                    } else {
                        onCommand { play() }
                    }
                },
                onLongClick = onShowSpeedPlayerDialog,
            )
            .onSecondaryClick(onShowSpeedPlayerDialog.takeIf { enabled })
            .bounceClick()
            .clip(uiRoundnessShape())
            .background(
                // ColorPaletteName.Dynamic
                when (playerPlayButtonType) {
                    PlayerPlayButtonType.CircularRibbed, PlayerPlayButtonType.Disabled -> Color.Transparent
                    else -> {
                        if (isGradientBackgroundEnabled) colorPalette().background1
                        else colorPalette().background2
                    }
                },
            )
            .width(playerPlayButtonType.width.dp)
            .height(playerPlayButtonType.height.dp),
    ) {

        if (playerPlayButtonType == PlayerPlayButtonType.CircularRibbed) {
            Image(
                painter = painterResource(Res.drawable.a13shape),
                colorFilter = ColorFilter.tint(
                    if (isGradientBackgroundEnabled) colorPalette().background1
                    else colorPalette().background2,
                ),
                modifier = Modifier
                    .fillMaxSize()
                    .rotate(rotationAngle)
                    .bounceClick(),
                contentDescription = stringResource(Res.string.cd_background_image),
                contentScale = ContentScale.Fit,
            )
        }

        if (isBuffering) {
            // The phone's buffering ring (phone's 567-576, contract 1.4)
            CircularWavyProgressIndicator(
                color = colorPalette().accent,
                trackColor = colorPalette().text,
                modifier = Modifier
                    .rotate(rotationAngle)
                    .align(Alignment.Center)
                    .size(if (playerPlayButtonType == PlayerPlayButtonType.Disabled) 40.dp else 30.dp),
                stroke = Stroke(width = with(LocalDensity.current) { 4.dp.toPx() }),
                trackStroke = Stroke(width = with(LocalDensity.current) { 4.dp.toPx() }),
            )
        } else {
            Image(
                painter = painterResource(if (shouldBePlaying) Res.drawable.pause else Res.drawable.play),
                contentDescription = null,
                colorFilter = ColorFilter.tint(if (playerPlayButtonType == PlayerPlayButtonType.Disabled) colorPalette().accent else controlsColor),
                modifier = Modifier
                    .rotate(rotationAngle)
                    .align(Alignment.Center)
                    .size(if (playerPlayButtonType == PlayerPlayButtonType.Disabled) 40.dp else 30.dp)
                    .bounceClick(),
            )
        }

        val fmtSpeed = "%.1fx".format(playbackSpeed).replace(",", ".")
        if (fmtSpeed != "1.0x") {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter),
            ) {
                BasicText(
                    text = fmtSpeed,
                    style = TextStyle(
                        color = colorPalette().text,
                        fontStyle = typography().xxxs.semiBold.fontStyle,
                        fontSize = typography().xxxs.semiBold.fontSize,
                    ),
                    maxLines = 1,
                    modifier = Modifier
                        .padding(bottom = if (playerPlayButtonType != PlayerPlayButtonType.CircularRibbed) 5.dp else 15.dp),
                )
            }
        }
    }

    Image(
        painter = painterResource(Res.drawable.play_skip_forward),
        contentDescription = null,
        colorFilter = ColorFilter.tint(controlsColor),
        modifier = Modifier
            .clip(uiRoundnessShape()).combinedClickable(
                indication = ripple(bounded = false),
                interactionSource = remember { MutableInteractionSource() },
                enabled = enabled,
                onClick = { onCommand { next() } },
                onLongClick = {},
            )
            .rotate(rotationAngle)
            .padding(10.dp)
            .size(26.dp)
            .alpha(disabledAlpha),
    )

    IconButton(
        icon = queueLoopType.iconId,
        color = colorPalette().text,
        enabled = enabled,
        onClick = {
            val next = queueLoopType.next().type
            onCommand { setRepeat(next) }
        },
        modifier = Modifier.size(26.dp).alpha(disabledAlpha),
    )
}
