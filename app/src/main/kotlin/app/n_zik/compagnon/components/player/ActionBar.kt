package app.n_zik.compagnon.components.player

import androidx.compose.foundation.layout.Box
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.material3.CircularWavyProgressIndicator
import app.n_zik.compagnon.bridge.state.TrackDownloadState
import androidx.compose.ui.graphics.graphicsLayer
import app.n_zik.compagnon.bridge.state.QueuePosition
import app.n_zik.compagnon.components.ui.screens.home.ItemActions
import app.n_zik.compagnon.components.menu.song.SongItemMenu
import app.n_zik.compagnon.components.LocalMenuState
import androidx.compose.foundation.shape.CircleShape
import app.n_zik.compagnon.generated.resources.*
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
 * The ⋮ "menu" button is shown in landscape only (the phone's `showButtonPlayerMenu || isLandscape`, the
 * portrait ⋮ living in the top bar): the phone's `PlayerMenu`. In landscape the bar is a `CircleShape` pill.
 * Dropped: discover, loop, expanded player, sleep timer, visualizer, equalizer, radio (hidden by default; `showButtonPlayerMenu` off); the "next songs" strip (off by default); the Listen Together lock. [live] `false` makes shuffle
 * do nothing.
 */
@Composable
fun ActionBar(
    state: PlayerState,
    showQueueState: MutableState<Boolean>,
    live: Boolean,
    showShuffle: Boolean,
    /** The phone's landscape bar (its 212, 229-231): a `CircleShape` pill, aligned bottom-end by the caller. */
    isLandscape: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val barShape = if (isLandscape) CircleShape else uiRoundnessShape()
    val onCommand = LocalCommandLauncher.current
    val transparentBackgroundActionBarPlayer = true
    val tapQueue = true
    val swipeUpQueue = true

    var showQueue by showQueueState

    Row(
        modifier = modifier
            .requiredHeight(50.dp)
            .fillMaxWidth()
            .clip(barShape).clickable(enabled = tapQueue) {
                showQueue = true
            }
            .background(
                color = colorPalette().background2.copy(
                    alpha = if (transparentBackgroundActionBarPlayer) 0.0f else 0.7f,
                ),
                shape = barShape,
            )
            .clip(barShape)
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
                // "download": the phone's `DownloadStateIconButton` — its wavy ring while the track downloads
                // or is queued (the progress once it advances), else the downloaded / download icon
                val current = state.currentTrack
                val isDownloaded = current?.let { it.isDownloaded || it.source == TrackSource.Local } == true
                when (current?.downloadState) {
                    TrackDownloadState.Downloading, TrackDownloadState.Queued -> {
                        // The phone's `DOWNLOAD_INDICATOR_SIZE_NORMAL` (18 dp, 2 dp strokes) in the 24 dp slot
                        val progress = current.downloadProgress
                        val stroke = Stroke(width = with(LocalDensity.current) { 2.dp.toPx() })
                        Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                            if (current.downloadState == TrackDownloadState.Downloading && progress != null && progress > 0.01f) {
                                CircularWavyProgressIndicator(
                                    progress = { progress },
                                    color = colorPalette().accent,
                                    trackColor = colorPalette().textDisabled,
                                    modifier = Modifier.size(18.dp),
                                    stroke = stroke,
                                    trackStroke = stroke,
                                )
                            } else {
                                CircularWavyProgressIndicator(
                                    color = colorPalette().accent,
                                    trackColor = colorPalette().textDisabled,
                                    modifier = Modifier.size(18.dp),
                                    stroke = stroke,
                                    trackStroke = stroke,
                                )
                            }
                        }
                    }
                    else -> IconButton(
                        icon = if (isDownloaded) Res.drawable.downloaded else Res.drawable.download,
                        color = if (isDownloaded) colorPalette().accent else Color.Gray,
                        onClick = {},
                        modifier = Modifier.size(24.dp),
                    )
                }
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
                // "menu" (last of the phone's default order): always shown in landscape, where the top
                // bar and its ⋮ are gone — the phone's `PlayerMenu`, its icon turned 90° (`ActionBar.kt` 775-805)
                if (isLandscape) {
                    val menuState = LocalMenuState.current
                    IconButton(
                        icon = Res.drawable.ellipsis_vertical,
                        color = colorPalette().accent,
                        onClick = {
                            state.currentTrack?.let { track ->
                                menuState.display {
                                    SongItemMenu(
                                        song = track,
                                        actions = ItemActions(
                                            onPlay = {},
                                            onPlayNext = { onCommand { addTracks(listOf(track.id), QueuePosition.Next) } },
                                            onEnqueue = { onCommand { addTracks(listOf(track.id), QueuePosition.End) } },
                                            enabled = live,
                                        ),
                                        playerMenu = true,
                                    ).MenuComponent()
                                }
                            }
                        },
                        modifier = Modifier
                            .size(24.dp)
                            .graphicsLayer { rotationZ = 90f },
                    )
                }
            }
        }
    }
}
