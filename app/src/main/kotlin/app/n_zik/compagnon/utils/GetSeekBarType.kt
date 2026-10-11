package app.n_zik.compagnon.utils

import app.n_zik.compagnon.components.player.skipBasePosition
import app.n_zik.compagnon.components.player.shouldReleasePendingSeekPosition
import app.n_zik.compagnon.components.player.PENDING_SEEK_POLL_INTERVAL_MS
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import app.n_zik.compagnon.components.player.SeekBar
import app.n_zik.compagnon.components.player.SeekBarWaved
import app.n_zik.compagnon.components.player.timeline.DurationIndicator
import app.n_zik.compagnon.components.player.timeline.SeekBarColored
import app.n_zik.compagnon.components.player.timeline.SeekBarCustom
import app.n_zik.compagnon.components.player.timeline.SeekBarStaticAudioWaves
import app.n_zik.compagnon.components.player.timeline.SeekBarThin
import app.n_zik.compagnon.components.player.timeline.SeekBarVisualizer
import app.n_zik.compagnon.components.player.timeline.ProgressPercentage
import app.n_zik.compagnon.components.theme.collapsedPlayerProgressBar
import app.n_zik.compagnon.enums.PlayerTimelineType
import app.n_zik.compagnon.uiRoundnessShape
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow

const val DURATION_INDICATOR_HEIGHT = 20

/**
 * Port of `GetSeekBar` (phone's `app/it/fast4x/rimusic/utils/GetSeekBarType.kt` 78-342): the player
 * timeline switches on the phone's `playerTimelineType` setting (the phone's default is
 * `PlayerTimelineType.Wavy`, transparent bar background). The eight phone timeline types are all
 * ported: [app.n_zik.compagnon.components.player.SeekBar] (Default), `SeekBarWaved` (Wavy),
 * [app.n_zik.compagnon.components.player.timeline.SeekBarCustom] (PinBar / BodiedBar),
 * [app.n_zik.compagnon.components.player.timeline.SeekBarThin] (ThinBar),
 * [app.n_zik.compagnon.components.player.timeline.SeekBarColored] (ColoredBar),
 * [app.n_zik.compagnon.components.player.timeline.SeekBarStaticAudioWaves] (AudioWaves, the
 * deterministic per-track bars — the phone's waveform extraction is dropped: no waveform
 * computation on the PC) and
 * [app.n_zik.compagnon.components.player.timeline.SeekBarVisualizer] (VisualizerBar, the static
 * pill waves — the phone's mic FFT is dropped). While the duration is unknown, a progress line
 * (indeterminate while the phone should be playing — the phone's `shouldBePlaying`,
 * `playWhenReady && state != ENDED`, phone's `utils/Player.kt` 69, true while buffering; derived from
 * the wire as `PlayerState.shouldBePlaying`: contract 1.4 `isBuffering`, 1.10.0 `playWhenReady`). The
 * wave bar's `isActive` stays the phone's `binder.player.isPlaying` (phone's 374): the wire's
 * `isPlaying`. The scrubber follows the pointer while dragging (the one local value of the player,
 * as on the phone); `player/seek` is sent on release.
 * Port of the phone's held seek target (`GetSeekBarType.kt` 102-153, 332-366, 500-519, issue #881): on
 * release (drag or tap) and on a skip button, the target stays on the bar and the label until the
 * phone's position converges on it (or 10 s pass), so the bar never flashes back to the old
 * position while the command and its delta travel. Then 8 dp and the [DurationIndicator].
 *
 * The phone's `SeekBarCustom` guard only admits `PinBar` (its exclusion list forgets nothing else,
 * so the phone's `BodiedBar` renders an empty row — a phone-side gap); per the spec the PC routes
 * both `PinBar` and `BodiedBar` into `SeekBarCustom`, whose own BodiedBar branch is ported.
 * Dropped: the Listen Together guest lock (there is no session lock on the PC — [live] `false`
 * makes every bar inert instead, no session to receive the seek).
 */
@Composable
fun GetSeekBar(
    position: () -> Long,
    duration: () -> Long,
    mediaId: String,
    shouldBePlaying: Boolean,
    isPlaying: Boolean,
    live: Boolean,
) {
    val onCommand = LocalCommandLauncher.current
    var scrubbingPosition by remember(mediaId) {
        mutableStateOf<Long?>(null)
    }
    var pendingSeekTarget by remember(mediaId) {
        mutableStateOf<Long?>(null)
    }
    var pendingSeekIssuedAtMs by remember(mediaId) {
        mutableLongStateOf(0L)
    }
    fun holdSeekTarget(target: Long) {
        pendingSeekTarget = target
        pendingSeekIssuedAtMs = monotonicMs()
    }
    val preferences = LocalPreferences.current
    val settings by (preferences?.settings ?: remember { MutableStateFlow(UserSettings()) }).collectAsState()
    val playerTimelineType = settings.playerTimelineTypeValue
    val transparentbar = true

    // The value / min / max bar callbacks shared by the phone's Default, PinBar, BodiedBar,
    // ThinBar and ColoredBar branches.
    fun captureScrub(positionMs: Long) {
        if (live) {
            // A new drag takes over the display: drop any held seek target
            pendingSeekTarget = null
            scrubbingPosition = positionMs
        }
    }

    fun adjustScrub(deltaMs: Long) {
        scrubbingPosition = if (duration() != TIME_UNSET) {
            scrubbingPosition?.plus(deltaMs)?.coerceIn(0, duration())
        } else {
            null
        }
    }

    fun releaseScrub() {
        // Hold the released position on the bar until the phone confirms the seek
        scrubbingPosition?.let { target ->
            holdSeekTarget(target)
            onCommand { seek(target) }
        }
        scrubbingPosition = null
    }

    // Release the held target once the phone's position converges on it, or after the safety timeout.
    // Keyed on the target: a new tap / drag restarts it, `remember(mediaId)` drops it on a track change.
    LaunchedEffect(pendingSeekTarget) {
        val target = pendingSeekTarget ?: return@LaunchedEffect
        val startedAtMs = monotonicMs()
        while (pendingSeekTarget == target) {
            if (shouldReleasePendingSeekPosition(target, position(), monotonicMs() - startedAtMs)) {
                pendingSeekTarget = null
                break
            }
            delay(PENDING_SEEK_POLL_INTERVAL_MS)
        }
    }

    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(horizontal = 10.dp)
            .fillMaxWidth(),
    ) {

        if (duration() == TIME_UNSET) {
            if (shouldBePlaying) {
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
            when (playerTimelineType) {
                PlayerTimelineType.Default ->
                    SeekBar(
                        value = scrubbingPosition ?: pendingSeekTarget ?: position(),
                        minimumValue = 0,
                        maximumValue = duration(),
                        onDragStart = { captureScrub(it) },
                        onDrag = { adjustScrub(it) },
                        onDragEnd = { releaseScrub() },
                        color = colorPalette().collapsedPlayerProgressBar,
                        backgroundColor = if (transparentbar) Color.Transparent else colorPalette().textSecondary,
                        shape = uiRoundnessShape(),
                    )

                PlayerTimelineType.PinBar,
                PlayerTimelineType.BodiedBar ->
                    SeekBarCustom(
                        type = playerTimelineType,
                        value = scrubbingPosition ?: pendingSeekTarget ?: position(),
                        minimumValue = 0,
                        maximumValue = duration(),
                        onDragStart = { captureScrub(it) },
                        onDrag = { adjustScrub(it) },
                        onDragEnd = { releaseScrub() },
                        color = colorPalette().collapsedPlayerProgressBar,
                        backgroundColor = if (transparentbar) Color.Transparent else colorPalette().textSecondary,
                        shape = uiRoundnessShape(),
                    )

                PlayerTimelineType.Wavy ->
                    SeekBarWaved(
                        position = { (scrubbingPosition ?: pendingSeekTarget)?.toFloat() ?: position().toFloat() },
                        range = 0f..duration().toFloat(),
                        onSeekStarted = {
                            // No live session: the bar is inert, nothing is captured
                            if (live) {
                                // A new drag takes over the display: drop any held seek target
                                pendingSeekTarget = null
                                scrubbingPosition = it.toLong()
                            }
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
                            // Hold the released position on the bar until the phone confirms the seek
                            scrubbingPosition?.let { target ->
                                holdSeekTarget(target)
                                onCommand { seek(target) }
                            }
                            scrubbingPosition = null
                        },
                        color = colorPalette().collapsedPlayerProgressBar,
                        isActive = isPlaying,
                        backgroundColor = if (transparentbar) Color.Transparent else colorPalette().textSecondary,
                        shape = uiRoundnessShape(),
                    )

                PlayerTimelineType.ThinBar ->
                    SeekBarThin(
                        value = scrubbingPosition ?: pendingSeekTarget ?: position(),
                        minimumValue = 0,
                        maximumValue = duration(),
                        onDragStart = { captureScrub(it) },
                        onDrag = { adjustScrub(it) },
                        onDragEnd = { releaseScrub() },
                        color = colorPalette().collapsedPlayerProgressBar,
                        backgroundColor = if (transparentbar) Color.Transparent else colorPalette().textSecondary,
                        shape = uiRoundnessShape(),
                    )

                PlayerTimelineType.ColoredBar ->
                    SeekBarColored(
                        value = scrubbingPosition ?: pendingSeekTarget ?: position(),
                        minimumValue = 0,
                        maximumValue = duration(),
                        onDragStart = { captureScrub(it) },
                        onDrag = { adjustScrub(it) },
                        onDragEnd = { releaseScrub() },
                        color = colorPalette().collapsedPlayerProgressBar,
                        // The phone's ColoredBar call site is unconditional: no transparent bar
                        backgroundColor = colorPalette().textSecondary,
                        shape = uiRoundnessShape(),
                    )

                PlayerTimelineType.AudioWaves ->
                    SeekBarStaticAudioWaves(
                        mediaId = mediaId,
                        position = scrubbingPosition ?: pendingSeekTarget ?: position(),
                        duration = duration(),
                        isPlaying = isPlaying,
                        onPositionChange = { captureScrub(it) },
                        onPositionChangeFinished = { releaseScrub() },
                        unplayedColor = if (transparentbar) Color.Transparent else colorPalette().textSecondary.copy(alpha = 0.3f),
                        modifier = Modifier.fillMaxWidth().height(40.dp),
                    )

                PlayerTimelineType.VisualizerBar ->
                    SeekBarVisualizer(
                        isPlaying = isPlaying,
                        progressPercentage = {
                            val held = pendingSeekTarget ?: position()
                            ProgressPercentage.safeValue((held.toFloat() / duration().toFloat()).coerceIn(0f, 1f))
                        },
                        playedColor = colorPalette().accent,
                        notPlayedColor = if (transparentbar) Color.Transparent else colorPalette().textSecondary,
                        waveInteraction = {
                            // The phone issues the seek on every interaction (tap / drag step);
                            // the held target keeps the label on the target while it travels.
                            if (live) {
                                val target = (it.value * duration().toFloat()).toLong()
                                holdSeekTarget(target)
                                onCommand { seek(target) }
                            }
                        },
                        modifier = Modifier.height(50.dp),
                    )
            }
        }
    }

    Spacer(modifier = Modifier.height(8.dp))

    DurationIndicator(
        scrubbingPosition = scrubbingPosition ?: pendingSeekTarget,
        position = scrubbingPosition ?: pendingSeekTarget ?: position(),
        duration = duration(),
        live = live,
        onSeekIssued = { holdSeekTarget(it) },
        seekBasePosition = {
            skipBasePosition(
                scrubbingMs = scrubbingPosition,
                pendingTargetMs = pendingSeekTarget,
                pendingHeldForMs = monotonicMs() - pendingSeekIssuedAtMs,
                livePlayerPositionMs = position(),
            )
        },
    )
}

/** The PC stand-in for the phone's `SystemClock.elapsedRealtime()`. */
private fun monotonicMs(): Long = System.nanoTime() / 1_000_000
