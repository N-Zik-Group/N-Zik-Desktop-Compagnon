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

    companion object {
        fun of(snapshot: SnapshotMessage) = PlayerState(
            queue = snapshot.queue,
            currentIndex = snapshot.currentIndex,
            currentTrackId = snapshot.currentTrackId,
            isPlaying = snapshot.isPlaying,
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
