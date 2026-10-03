package app.n_zik.compagnon.components.player

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import app.n_zik.compagnon.bridge.state.PlayerState
import app.n_zik.compagnon.components.player.controls.InfoAlbumAndArtistModern
import app.n_zik.compagnon.components.theme.ColorPalette
import app.n_zik.compagnon.utils.GetControls
import app.n_zik.compagnon.utils.GetSeekBar

/**
 * Port of `Controls` (phone's `app/it/fast4x/rimusic/ui/screens/player/Controls.kt` 130-384), its portrait
 * branch with the default preferences: `PlayerInfoType.Modern`, timeline size `Biggest` (20 dp of side
 * padding), controls under the timeline. The info, then 25 dp, the seek bar, a 0.4 weight, the controls,
 * a 0.5 weight.
 *
 * Dropped: the expanded-player / lyrics branch and the landscape branch (not the default layout), the
 * `Essential` info type (not the default).
 */
@Composable
fun Controls(
    state: PlayerState,
    title: String?,
    artist: String?,
    position: () -> Long,
    duration: () -> Long,
    live: Boolean,
    dynamicColorPalette: ColorPalette,
    modifier: Modifier = Modifier,
) {
    // PlayerTimelineSize.Biggest
    val playerTimelineSize = 20

    Box(
        modifier = Modifier
            .animateContentSize(),
    ) {
        Column(
            horizontalAlignment = Alignment.Start,
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = playerTimelineSize.dp),
        ) {

            InfoAlbumAndArtistModern(
                title = title,
                artist = artist,
            )

            Spacer(
                modifier = Modifier
                    .height(25.dp),
            )

            GetSeekBar(
                position = position,
                duration = duration,
                mediaId = state.currentTrackId.orEmpty(),
                isPlaying = state.isPlaying,
                live = live,
            )
            Spacer(
                modifier = Modifier
                    .weight(0.4f),
            )
            GetControls(
                state = state,
                live = live,
                dynamicColorPalette = dynamicColorPalette,
            )
            Spacer(
                modifier = Modifier
                    .weight(0.5f),
            )
        }
    }
}

private enum class ButtonState { Pressed, Idle }

/** Port of `Modifier.bounceClick` (`Controls.kt` 486): 0.8 scale while pressed (`buttonzoomout`, on by default). */
fun Modifier.bounceClick() = composed {
    var buttonState by remember { mutableStateOf(ButtonState.Idle) }
    val buttonzoomout = true
    val scale by animateFloatAsState(if ((buttonState == ButtonState.Pressed) && (buttonzoomout)) 0.8f else 1f)

    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .pointerInput(buttonState) {
            awaitPointerEventScope {
                buttonState = if (buttonState == ButtonState.Pressed) {
                    waitForUpOrCancellation()
                    ButtonState.Idle
                } else {
                    awaitFirstDown(false)
                    ButtonState.Pressed
                }
            }
        }
}
