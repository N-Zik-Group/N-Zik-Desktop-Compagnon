package app.n_zik.compagnon.components.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import app.n_zik.compagnon.LocalCommandLauncher
import app.n_zik.compagnon.bridge.state.PlayerState
import app.n_zik.compagnon.colorPalette
import app.n_zik.compagnon.components.themed.IconButton
import app.n_zik.compagnon.generated.resources.Res
import app.n_zik.compagnon.generated.resources.chevron_up
import app.n_zik.compagnon.generated.resources.shuffle
import app.n_zik.compagnon.uiRoundnessShape

/**
 * Port of the player's `ActionBar` (phone's `app/kreate/android/themed/rimusic/screen/player/ActionBar.kt`
 * 165-805) with its default preferences: 50 dp high, transparent background, a tap or an upward drag opens
 * the queue, the buttons spread evenly.
 *
 * Kept (contract v1 actions): shuffle (`player/shuffle`; the phone shuffles the queue, the contract turns the
 * shuffle mode on or off, so the button toggles it) and the arrow that opens the queue.
 * Dropped: video, download, add to playlist, lyrics (shown by default on the phone, not in the contract),
 * and discover, loop, expanded player, sleep timer, visualizer, equalizer, radio, menu (hidden by default);
 * the "next songs" strip (off by default); the Listen Together lock. [live] `false` makes shuffle do nothing.
 */
@Composable
fun ActionBar(
    state: PlayerState,
    showQueueState: MutableState<Boolean>,
    live: Boolean,
    showShuffle: Boolean,
) {
    val onCommand = LocalCommandLauncher.current
    val transparentBackgroundActionBarPlayer = true
    val tapQueue = true
    val swipeUpQueue = true

    var showQueue by showQueueState

    Row(
        modifier = Modifier
            .requiredHeight(50.dp)
            .fillMaxWidth()
            .clip(uiRoundnessShape()).clickable(enabled = tapQueue) {
                showQueue = true
            }
            .background(
                color = colorPalette().background2.copy(
                    alpha = if (transparentBackgroundActionBarPlayer) 0.0f else 0.7f,
                ),
                shape = uiRoundnessShape(),
            )
            .clip(uiRoundnessShape())
            .pointerInput(Unit) {
                if (swipeUpQueue) {
                    detectVerticalDragGestures(
                        onVerticalDrag = { _, dragAmount ->
                            if (dragAmount < 0) showQueue = true
                        },
                    )
                }
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            verticalArrangement = Arrangement.SpaceAround,
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize(),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .fillMaxWidth(),
            ) {
                // "shuffle"
                if (showShuffle) {
                    IconButton(
                        icon = Res.drawable.shuffle,
                        color = colorPalette().accent,
                        enabled = live,
                        onClick = {
                            val enabled = !state.shuffle
                            onCommand { setShuffle(enabled) }
                        },
                        modifier = Modifier
                            .size(24.dp)
                            .alpha(if (live) 1f else 0.5f),
                    )
                }
                // "arrow"
                IconButton(
                    icon = Res.drawable.chevron_up,
                    color = colorPalette().accent,
                    enabled = true,
                    onClick = {
                        showQueue = true
                    },
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}
