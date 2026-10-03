package app.n_zik.compagnon.utils

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.n_zik.compagnon.LocalCommandLauncher
import app.n_zik.compagnon.colorPalette
import app.n_zik.compagnon.components.player.SeekBarWaved
import app.n_zik.compagnon.components.player.timeline.DurationIndicator
import app.n_zik.compagnon.components.theme.collapsedPlayerProgressBar
import app.n_zik.compagnon.uiRoundnessShape

const val DURATION_INDICATOR_HEIGHT = 20

/**
 * Port of `GetSeekBar` (phone's `app/it/fast4x/rimusic/utils/GetSeekBarType.kt` 78-342) with its default
 * preferences: `PlayerTimelineType.Wavy`, transparent bar background. While the duration is unknown, a
 * progress line (indeterminate while playing). The scrubber follows the pointer **only while dragging** (the
 * one local value of the player, as on the phone); `player/seek` is sent on release, then the bar follows the
 * phone again. Then 8 dp and the [DurationIndicator].
 *
 * Dropped: the other timeline types (not the default), the Listen Together lock. [live] `false` makes the bar
 * inert (no session to receive the seek).
 */
@Composable
fun GetSeekBar(
    position: () -> Long,
    duration: () -> Long,
    mediaId: String,
    isPlaying: Boolean,
    live: Boolean,
) {
    val onCommand = LocalCommandLauncher.current
    var scrubbingPosition by remember(mediaId) {
        mutableStateOf<Long?>(null)
    }
    val transparentbar = true

    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(horizontal = 10.dp)
            .fillMaxWidth(),
    ) {

        if (duration() == TIME_UNSET) {
            if (isPlaying) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = colorPalette().collapsedPlayerProgressBar,
                )
            } else {
                LinearProgressIndicator(
                    progress = { 0f },
                    modifier = Modifier.fillMaxWidth(),
                    color = colorPalette().collapsedPlayerProgressBar,
                )
            }
        } else {
            SeekBarWaved(
                position = { scrubbingPosition?.toFloat() ?: position().toFloat() },
                range = 0f..duration().toFloat(),
                onSeekStarted = {
                    // No live session: the bar is inert, nothing is captured
                    if (live) scrubbingPosition = it.toLong()
                },
                onSeek = { delta ->
                    scrubbingPosition = if (duration() != TIME_UNSET) {
                        scrubbingPosition?.plus(delta)?.coerceIn(0F, duration().toFloat())
                            ?.toLong()
                    } else {
                        null
                    }
                },
                onSeekFinished = {
                    scrubbingPosition?.let { target -> onCommand { seek(target) } }
                    scrubbingPosition = null
                },
                color = colorPalette().collapsedPlayerProgressBar,
                isActive = isPlaying,
                backgroundColor = if (transparentbar) Color.Transparent else colorPalette().textSecondary,
                shape = uiRoundnessShape(),
            )
        }
    }

    Spacer(modifier = Modifier.height(8.dp))

    DurationIndicator(scrubbingPosition, position(), duration(), live)
}
