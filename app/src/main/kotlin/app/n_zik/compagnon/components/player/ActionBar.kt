package app.n_zik.compagnon.components.player

import app.n_zik.compagnon.generated.resources.song_lyrics
import app.n_zik.compagnon.generated.resources.add_in_playlist
import app.n_zik.compagnon.generated.resources.downloaded
import app.n_zik.compagnon.generated.resources.download
import app.n_zik.compagnon.generated.resources.video
import app.n_zik.compagnon.bridge.state.TrackSource
import androidx.compose.ui.graphics.Color
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
import app.n_zik.compagnon.components.tab.ShuffleOkFlash
import app.n_zik.compagnon.components.themed.IconButton
import app.n_zik.compagnon.generated.resources.Res
import app.n_zik.compagnon.generated.resources.chevron_up
import app.n_zik.compagnon.generated.resources.shuffle
import app.n_zik.compagnon.generated.resources.shuffle_ok
import app.n_zik.compagnon.uiRoundnessShape

/**
 * Port of the player's `ActionBar` (phone's `app/kreate/android/themed/rimusic/screen/player/ActionBar.kt`
 * 165-805) with its default preferences: 50 dp high, transparent background, a tap or an upward drag opens
 * the queue, the buttons spread evenly.
 *
 * The phone's default buttons, in its default order, 24 dp (`ActionBar.kt` 479-756): video (accent),
 * download (`downloaded` in accent when the phone has the track offline, `download` in gray otherwise), add to
 * playlist (accent), shuffle (accent), lyrics (gray: lyrics are not shown), the arrow that opens the queue
 * (accent). Wired to the contract: shuffle (`player/shuffle`; the phone shuffles the queue, the contract
 * turns the shuffle mode on or off, so the button toggles it) and the arrow. Video, download, add to playlist
 * and lyrics have no contract route: shown, without action.
 * Dropped: discover, loop, expanded player, sleep timer, visualizer, equalizer, radio, menu (hidden by
 * default); the "next songs" strip (off by default); the Listen Together lock. [live] `false` makes shuffle
 * do nothing.
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
                // "video"
                IconButton(
                    icon = Res.drawable.video,
                    color = colorPalette().accent,
                    onClick = {},
                    modifier = Modifier.size(24.dp),
                )
                // "download"
                val isDownloaded = state.currentTrack?.let { it.isDownloaded || it.source == TrackSource.Local } == true
                IconButton(
                    icon = if (isDownloaded) Res.drawable.downloaded else Res.drawable.download,
                    color = if (isDownloaded) colorPalette().accent else Color.Gray,
                    onClick = {},
                    modifier = Modifier.size(24.dp),
                )
                // "add_to_playlist"
                IconButton(
                    icon = Res.drawable.add_in_playlist,
                    color = colorPalette().accent,
                    onClick = {},
                    modifier = Modifier.size(24.dp),
                )
                // "shuffle"
                if (showShuffle) {
                    IconButton(
                        // Issue #866: the app-wide shuffle confirmation flash
                        icon = if (ShuffleOkFlash.active) Res.drawable.shuffle_ok else Res.drawable.shuffle,
                        color = colorPalette().accent,
                        enabled = live,
                        onClick = {
                            ShuffleOkFlash.trigger()
                            val enabled = !state.shuffle
                            onCommand { setShuffle(enabled) }
                        },
                        modifier = Modifier
                            .size(24.dp)
                            .alpha(if (live) 1f else 0.5f),
                    )
                }
                // "lyrics"
                IconButton(
                    icon = Res.drawable.song_lyrics,
                    color = Color.Gray,
                    enabled = true,
                    onClick = {},
                    modifier = Modifier.size(24.dp),
                )
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
