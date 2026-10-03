package app.n_zik.compagnon.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import app.n_zik.compagnon.bridge.state.PlayerRepository
import app.n_zik.compagnon.bridge.state.PlayerState

/** `C.TIME_UNSET` (media3): the duration of a track the phone does not know (`durationMs: null`). */
const val TIME_UNSET: Long = Long.MIN_VALUE + 1

/**
 * Port of `Player.positionAndDurationState` (phone's `app/it/fast4x/rimusic/utils/PlayerState.kt` 49): the
 * position and duration, polled once per frame while [active]. The phone reads its player; here the position
 * is extrapolated from the last WS anchor (contract §7), never sent anywhere, and frozen while the session is
 * not live ([live]: the phone's state is then unknown). The duration is [TIME_UNSET] when unknown.
 */
@Composable
fun PlayerRepository.positionAndDurationState(
    state: PlayerState?,
    live: Boolean,
    active: Boolean = true,
): State<Pair<Long, Long>> {
    val duration = state?.currentTrack?.durationMs ?: TIME_UNSET
    val positionAndDuration = remember { mutableStateOf((state?.extrapolatedPositionMs(serverNowMs()) ?: 0L) to duration) }
    LaunchedEffect(this, state, live, active) {
        // Without a live session the phone's state is unknown: the position freezes where it was.
        if (!live) return@LaunchedEffect
        positionAndDuration.value = (state?.extrapolatedPositionMs(serverNowMs()) ?: 0L) to duration
        if (!active) return@LaunchedEffect
        while (state?.isPlaying == true) {
            withFrameNanos { }
            positionAndDuration.value = state.extrapolatedPositionMs(serverNowMs()) to duration
        }
    }
    return positionAndDuration
}
