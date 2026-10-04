package app.n_zik.compagnon.bridge.state

/**
 * The phone's playback state as last received over the WebSocket (contract §7). View memory only:
 * nothing of it is ever persisted (contract §12). [positionMs] is the position **at** [serverTimeMs];
 * the displayed one is extrapolated ([extrapolatedPositionMs]).
 */
data class PlayerState(
    val queue: List<Track> = emptyList(),
    val currentIndex: Int = -1,
    val currentTrackId: String? = null,
    val isPlaying: Boolean = false,
    /** Since 1.4 (contract §7.1): the phone's player is loading or rebuffering; a ≤ 1.3 phone reports `false`. */
    val isBuffering: Boolean = false,
    /** Since 1.5 (contract §7.1): the phone's player's live duration; [DURATION_UNREPORTED] when a ≤ 1.4 phone does not report it. */
    val durationMs: Long = DURATION_UNREPORTED,
    val speed: Float = 1f,
    val positionMs: Long = 0,
    val serverTimeMs: Long = 0,
    val repeatMode: RepeatMode = RepeatMode.Off,
    val shuffle: Boolean = false,
    /** Where the phone's playback sounds (contract §8.5, since 1.2); [AudioOutput.Pc] = this PC's local player. */
    val audioOutput: AudioOutput = AudioOutput.Phone,
) {
    /** The current track: the one at [currentIndex] when its id matches, else the first one with [currentTrackId]. */
    val currentTrack: Track?
        get() {
            val atIndex = queue.getOrNull(currentIndex)
            if (atIndex != null && (currentTrackId == null || atIndex.id == currentTrackId)) return atIndex
            return currentTrackId?.let { id -> queue.firstOrNull { it.id == id } }
        }

    /**
     * Contract §7: `positionMs + (serverNow − serverTimeMs) × speed` while playing, bounded by
     * `[0, durationMs]` when the duration is known.
     */
    fun extrapolatedPositionMs(serverNowMs: Long): Long {
        val raw = if (isPlaying) positionMs + ((serverNowMs - serverTimeMs).coerceAtLeast(0) * speed).toLong() else positionMs
        val duration = currentTrack?.durationMs
        return if (duration != null && duration > 0) raw.coerceIn(0, duration) else raw.coerceAtLeast(0)
    }

    /**
     * The duration the phone's player reports for the current track (contract 1.5) — the source of the progress bar
     * and the time labels, like the phone's own bar: `null` while the player does not know it yet (the phone shows
     * `--:--`). A ≤ 1.4 phone never reports it: the track's metadata duration stands in (pre-1.5 behaviour).
     */
    val playerDurationMs: Long?
        get() = when (durationMs) {
            DURATION_UNREPORTED -> currentTrack?.durationMs
            DURATION_TIME_UNSET -> null
            else -> durationMs
        }

    companion object {
        /** The wire `durationMs` of a ≤ 1.4 phone, which never reports it: the track's metadata duration stands in. */
        const val DURATION_UNREPORTED: Long = Long.MIN_VALUE

        /** The phone's `C.TIME_UNSET` (media3): the player does not know the current track's duration yet. */
        const val DURATION_TIME_UNSET: Long = Long.MIN_VALUE + 1

        fun of(snapshot: SnapshotMessage) = PlayerState(
            queue = snapshot.queue,
            currentIndex = snapshot.currentIndex,
            currentTrackId = snapshot.currentTrackId,
            isPlaying = snapshot.isPlaying,
            isBuffering = snapshot.isBuffering,
            durationMs = snapshot.durationMs,
            speed = snapshot.speed,
            positionMs = snapshot.positionMs,
            serverTimeMs = snapshot.serverTimeMs,
            repeatMode = snapshot.repeatMode,
            shuffle = snapshot.shuffle,
            audioOutput = snapshot.audioOutput,
        )
    }
}

/**
 * Synchronisation bookkeeping of contract §7.4 / §7.5: [player] is `null` until the first snapshot,
 * [last] is the last applied revision, [awaitingSnapshot] ignores deltas until the next snapshot.
 */
data class SyncState(
    val player: PlayerState? = null,
    val last: Long? = null,
    val awaitingSnapshot: Boolean = true,
)
