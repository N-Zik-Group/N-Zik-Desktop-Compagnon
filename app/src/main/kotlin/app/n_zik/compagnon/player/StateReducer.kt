package app.n_zik.compagnon.player

/** Result of [StateReducer.reduce]: the new state and whether a `requestSnapshot` must be sent. */
data class Reduction(val state: SyncState, val requestSnapshot: Boolean = false)

/**
 * Pure revision rules of contract §7.3–§7.5. Knows neither Ktor nor Compose.
 *
 * - `snapshot`: always applied, `last = revision` even when lower (server restarted);
 * - delta: `= last + 1` applied; `≤ last` rejected; `> last + 1` not applied, `requestSnapshot`, and
 *   every delta ignored until the snapshot;
 * - `heartbeat`: `= last` → position, playback and speed re-aligned; `> last` → `requestSnapshot`;
 *   `< last` → ignored;
 * - unknown type with an integer `revision`: accounting only (`= last + 1` → `last = revision`;
 *   `> last + 1` → `requestSnapshot`; otherwise ignored);
 * - anything else (`pong`, `error`, `serverStopped`, unknown type without revision): no change.
 */
object StateReducer {

    fun reduce(state: SyncState, message: ServerMessage): Reduction = when (message) {
        is SnapshotMessage -> Reduction(
            SyncState(player = PlayerState.of(message), last = message.revision, awaitingSnapshot = false),
        )
        is DeltaMessage -> revised(state, message.revision) { player -> apply(player, message) }
        is UnknownMessage -> {
            val revision = message.revision
            if (revision == null) Reduction(state) else revised(state, revision) { it }
        }
        is HeartbeatMessage -> heartbeat(state, message)
        is PongMessage, is ErrorMessage, is ServerStoppedMessage -> Reduction(state)
    }

    private inline fun revised(state: SyncState, revision: Long, apply: (PlayerState) -> PlayerState): Reduction {
        val last = state.last
        val player = state.player
        if (state.awaitingSnapshot || last == null || player == null || revision <= last) return Reduction(state)
        if (revision == last + 1) return Reduction(state.copy(player = apply(player), last = revision))
        return Reduction(state.copy(awaitingSnapshot = true), requestSnapshot = true)
    }

    private fun heartbeat(state: SyncState, message: HeartbeatMessage): Reduction {
        val last = state.last
        val player = state.player
        if (state.awaitingSnapshot || last == null || player == null) return Reduction(state)
        return when {
            message.revision == last -> Reduction(
                state.copy(
                    player = player.copy(
                        positionMs = message.positionMs,
                        isPlaying = message.isPlaying,
                        speed = message.speed,
                        serverTimeMs = message.serverTimeMs,
                    ),
                ),
            )
            message.revision > last -> Reduction(state.copy(awaitingSnapshot = true), requestSnapshot = true)
            else -> Reduction(state)
        }
    }

    private fun apply(player: PlayerState, delta: DeltaMessage): PlayerState = when (delta) {
        is PlaybackChangedMessage -> player.copy(
            isPlaying = delta.isPlaying,
            speed = delta.speed,
            positionMs = delta.positionMs,
            serverTimeMs = delta.serverTimeMs,
        )
        is TrackChangedMessage -> player.copy(
            currentIndex = delta.currentIndex,
            currentTrackId = delta.currentTrackId,
            positionMs = delta.positionMs,
            isPlaying = delta.isPlaying,
            serverTimeMs = delta.serverTimeMs,
        )
        // No position in this delta: the position anchor (positionMs at serverTimeMs) stays as it was.
        is QueueChangedMessage -> player.copy(
            queue = delta.queue,
            currentIndex = delta.currentIndex,
            currentTrackId = delta.currentTrackId,
        )
        is ModesChangedMessage -> player.copy(repeatMode = delta.repeatMode, shuffle = delta.shuffle)
    }
}
