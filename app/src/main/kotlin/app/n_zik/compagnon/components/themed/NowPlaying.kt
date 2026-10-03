package app.n_zik.compagnon.components.themed

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import app.n_zik.compagnon.components.MusicAnimation
import app.n_zik.compagnon.components.styling.Dimensions
import app.n_zik.compagnon.components.theme.onOverlay
import app.n_zik.compagnon.colorPalette

/**
 * Port of `NowPlayingSongIndicator` (phone's `app/it/fast4x/rimusic/ui/components/themed/NowPlaying.kt` 21).
 * The caller only shows it for the phone's current track; [isPlaying] is the phone's playback state.
 */
@Composable
fun NowPlayingSongIndicator(
    isPlaying: Boolean,
    containerSize: Dp = Dimensions.thumbnails.song,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(containerSize),
    ) {
        MusicAnimation(
            color = colorPalette().onOverlay,
            isPlaying = isPlaying,
            modifier = Modifier
                .fillMaxHeight(0.5f),
        )
    }
}
