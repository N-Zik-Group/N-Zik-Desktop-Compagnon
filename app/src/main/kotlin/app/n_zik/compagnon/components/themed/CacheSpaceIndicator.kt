package app.n_zik.compagnon.components.themed

import app.n_zik.compagnon.utils.formatShortFileSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.n_zik.compagnon.colorPalette
import app.n_zik.compagnon.typography

/**
 * Port of `CacheSpaceIndicator` (phone's `app/it/fast4x/rimusic/ui/components/themed/CacheSpaceIndicator.kt`
 * 42-138) for `CacheType.CachedSongs`, the only cache of the PC (the local audio cache, story 12): the
 * linear `ProgressIndicator` (`text` on `textDisabled`, round caps, 2 dp gap) of [usedBytes] over
 * [maxBytes], 12 dp above and below. Nothing is drawn for an unlimited cache ([maxBytes] `null`), like the
 * phone's `Unlimited`.
 */
@Composable
fun CacheSpaceIndicator(
    usedBytes: Long,
    maxBytes: Long?,
    maxText: String,
    horizontalPadding: Dp = 12.dp,
    showCacheInfo: Boolean = true,
) {
    if (maxBytes == null) return

    val targetProgress = (usedBytes.toFloat() / maxBytes.coerceAtLeast(1)).coerceIn(0f, 1f)
    val progressValue by animateFloatAsState(targetValue = targetProgress)

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = horizontalPadding, vertical = 12.dp)) {
        // Port of `ProgressIndicator` (phone's `ProgressIndicator.kt` 13-28)
        LinearProgressIndicator(
            modifier = Modifier.fillMaxWidth(),
            color = colorPalette().text,
            trackColor = colorPalette().textDisabled,
            strokeCap = StrokeCap.Round,
            progress = { progressValue },
            gapSize = 2.dp,
        )

        if (showCacheInfo) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                BasicText(
                    text = "${formatShortFileSize(usedBytes)} / $maxText",
                    style = typography().xxs.copy(fontWeight = FontWeight.SemiBold, color = colorPalette().textSecondary),
                )
            }
        }
    }
}
