package app.n_zik.compagnon.components.player.timeline

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.n_zik.compagnon.LocalCommandLauncher
import app.n_zik.compagnon.colorPalette
import app.n_zik.compagnon.components.player.durationOutlineColorOf
import app.n_zik.compagnon.components.theme.favoritesIcon
import app.n_zik.compagnon.generated.resources.Res
import app.n_zik.compagnon.generated.resources.forward
import app.n_zik.compagnon.generated.resources.forward_30_seconds
import app.n_zik.compagnon.generated.resources.forward_5_seconds
import app.n_zik.compagnon.generated.resources.play_forward
import app.n_zik.compagnon.generated.resources.rewind
import app.n_zik.compagnon.generated.resources.rewind_30_seconds
import app.n_zik.compagnon.generated.resources.rewind_5_seconds
import app.n_zik.compagnon.typography
import app.n_zik.compagnon.uiRoundnessShape
import app.n_zik.compagnon.utils.DURATION_INDICATOR_HEIGHT
import app.n_zik.compagnon.utils.formatAsDuration
import app.n_zik.compagnon.utils.semiBold
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/*
 * Port of the phone's `app/kreate/android/themed/rimusic/screen/player/timeline/DurationIndicator.kt`, with
 * its default preferences: skip buttons shown, remaining time shown, no pause between songs, no text outline.
 * Dropped: the pause between songs (`PauseBetweenSongs`, local playback only).
 */

/** Skip button: a tap moves 5 s, a double tap 10 s, a long press 30 s, through `player/seek`. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RowScope.SkipTimeButton(
    position: Long,
    operation: Long.(Long) -> Long,
    valueSelector: (Long, Long) -> Long,
    comparedValue: Long,
    contentDescription: String,
    onClickLabel: String,
    onLongClickLabel: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    tapAdjustment: Long = 5_000L,
    doubleTapAdjustment: Long = 10_000L,
    longTapAdjustment: Long = 30_000L,
) {
    val onCommand = LocalCommandLauncher.current
    fun seekTo(adjustment: Long) {
        val adjustedPosition = position.operation(adjustment)
        val newPosition = valueSelector(adjustedPosition, comparedValue)
        onCommand { seek(newPosition) }
    }

    Icon(
        painter = painterResource(Res.drawable.play_forward),
        tint = colorPalette().favoritesIcon,
        contentDescription = contentDescription,
        modifier = modifier.size(DURATION_INDICATOR_HEIGHT.dp)
            .align(Alignment.CenterVertically)
            .clip(uiRoundnessShape()).combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClickLabel = onClickLabel,
                onClick = { seekTo(tapAdjustment) },
                onDoubleClick = { seekTo(doubleTapAdjustment) },
                onLongClickLabel = onLongClickLabel,
                onLongClick = { seekTo(longTapAdjustment) },
            ),
    )
}

@Composable
private fun OutlinedText(text: String, outlineColor: Color) {
    // Main text
    BasicText(
        text = text,
        style = typography().xxs.semiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )

    // Outline
    BasicText(
        text = text,
        style = typography().xxs
            .semiBold
            .merge(
                TextStyle(
                    drawStyle = Stroke(width = 1.0f, join = StrokeJoin.Round),
                    color = outlineColor,
                ),
            ),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * Remaining song time to display: -1 ("unknown", shown as "--:--") when the duration is not positive,
 * otherwise the clamped remaining time.
 */
internal fun timeRemainingOf(duration: Long, position: Long): Long =
    if (duration <= 0) -1L else (duration - position).coerceAtLeast(0)

/**
 * The times under the seek bar: rewind button, position (or the dragged one), remaining time, duration,
 * forward button. [live] `false`: the skip buttons do nothing.
 */
@Composable
fun DurationIndicator(
    scrubbingPosition: Long?,
    position: Long,
    duration: Long,
    live: Boolean,
) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 10.dp)
            .fillMaxWidth(),
    ) {
        val showSkipTimeButtons = true
        if (showSkipTimeButtons) {
            SkipTimeButton(
                position, Long::minus, ::maxOf, 0, stringResource(Res.string.rewind), stringResource(Res.string.rewind_5_seconds),
                stringResource(Res.string.rewind_30_seconds), live, Modifier.rotate(180f),
            )

            Spacer(Modifier.width(5.dp))
        }

        // textoutline: false by default
        val outlineColor = durationOutlineColorOf(false, colorPalette())

        // Scrubbing position
        Box(
            modifier = Modifier.weight(1f)
                .height(DURATION_INDICATOR_HEIGHT.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            OutlinedText(formatAsDuration(scrubbingPosition ?: position), outlineColor)
        }

        // Remaining duration
        val showRemainingSongTime = true
        if (showRemainingSongTime) {
            Box(
                modifier = Modifier.weight(1f)
                    .height(DURATION_INDICATOR_HEIGHT.dp),
                contentAlignment = Alignment.Center,
            ) {
                val timeRemaining = timeRemainingOf(duration, position)
                OutlinedText(if (timeRemaining < 0) "--:--" else formatAsDuration(timeRemaining), outlineColor)
            }
        }

        // Song's duration
        Box(
            modifier = Modifier.weight(1f)
                .height(DURATION_INDICATOR_HEIGHT.dp),
            contentAlignment = Alignment.CenterEnd,
        ) {
            OutlinedText(if (duration <= 0) "--:--" else formatAsDuration(duration), outlineColor)
        }

        if (showSkipTimeButtons) {
            Spacer(Modifier.width(5.dp))

            SkipTimeButton(
                // An unknown duration (`TIME_UNSET`) puts no upper bound: the phone bounds the seek itself
                position, Long::plus, ::minOf, if (duration > 0) duration else Long.MAX_VALUE, stringResource(Res.string.forward), stringResource(Res.string.forward_5_seconds),
                stringResource(Res.string.forward_30_seconds), live,
            )
        }
    }
}
