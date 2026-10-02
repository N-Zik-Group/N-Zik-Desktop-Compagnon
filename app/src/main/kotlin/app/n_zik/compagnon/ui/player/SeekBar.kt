package app.n_zik.compagnon.ui.player

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.n_zik.compagnon.ui.theme.colorPalette
import app.n_zik.compagnon.ui.theme.typography

/**
 * Seek bar adapted from the phone's `SeekBar`: thin rounded track, accent progress, round scrubber
 * that grows while dragged. The scrubber follows the pointer **only while dragging** (the one local,
 * non-WS value of the UI); the seek is sent on release or tap, and the bar then follows the phone.
 */
@Composable
fun SeekBar(
    positionMs: Long,
    durationMs: Long?,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    showTimes: Boolean = true,
    barHeight: Dp = 3.dp,
    scrubberRadius: Dp = 6.dp,
    color: Color = colorPalette().accent,
    backgroundColor: Color = colorPalette().background3,
) {
    val duration = durationMs?.takeIf { it > 0 }
    val interactive = enabled && duration != null
    val seek by rememberUpdatedState(onSeek)
    // A drag in progress is dropped when the track (duration) or the interactivity changes under it.
    var dragFraction by remember(duration, interactive) { mutableStateOf<Float?>(null) }
    val fraction = dragFraction ?: if (duration != null) (positionMs.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val radius by animateDpAsState(if (dragFraction != null) scrubberRadius * 1.4f else if (interactive) scrubberRadius else 0.dp)
    val shownPosition = if (dragFraction != null && duration != null) (dragFraction!! * duration).toLong() else positionMs

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (showTimes) TimeText(formatDuration(shownPosition), TextAlign.End)
        Box(
            modifier = Modifier
                .weight(1f)
                .height(scrubberRadius * 4)
                .pointerInput(interactive, duration) {
                    if (!interactive) return@pointerInput
                    detectTapGestures { offset -> seek(((offset.x / size.width).coerceIn(0f, 1f) * duration).toLong()) }
                }
                .pointerInput(interactive, duration) {
                    if (!interactive) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragStart = { offset -> dragFraction = (offset.x / size.width).coerceIn(0f, 1f) },
                        onDragEnd = {
                            dragFraction?.let { seek((it * duration).toLong()) }
                            dragFraction = null
                        },
                        onDragCancel = { dragFraction = null },
                    ) { change, _ ->
                        change.consume()
                        dragFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                    }
                }
                .drawBehind {
                    val y = size.height / 2
                    val stroke = barHeight.toPx()
                    drawLine(backgroundColor, Offset(0f, y), Offset(size.width, y), stroke, StrokeCap.Round)
                    val x = size.width * fraction
                    if (x > 0f) drawLine(color, Offset(0f, y), Offset(x, y), stroke, StrokeCap.Round)
                    if (radius > 0.dp) drawCircle(color, radius.toPx(), Offset(x, y))
                },
        )
        if (showTimes) TimeText(duration?.let(::formatDuration) ?: "--:--", TextAlign.Start)
    }
}

@Composable
private fun TimeText(text: String, align: TextAlign) {
    Text(
        text,
        style = typography().xxs,
        color = colorPalette().textSecondary,
        textAlign = align,
        maxLines = 1,
        modifier = Modifier.widthIn(min = 40.dp),
    )
}
