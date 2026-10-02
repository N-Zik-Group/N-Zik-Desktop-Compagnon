package app.n_zik.compagnon.player

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/** A command failure or a late error, shown as a short message (snackbar). [command] is `null` when unknown. */
sealed interface PlayerNotice {
    val command: CommandKind?

    /** `changed: true` but no revision ≥ the announced one within 3 s (contract §9); a snapshot was requested. */
    data class NoDelta(override val command: CommandKind) : PlayerNotice

    /** `409 QUEUE_MISMATCH`: the queue changed meanwhile; a snapshot was requested, the user may retry. */
    data class QueueMismatch(override val command: CommandKind) : PlayerNotice

    /** `422 PLAYER_REJECTED`. */
    data class Rejected(override val command: CommandKind) : PlayerNotice

    /** `503 PLAYER_UNAVAILABLE`. */
    data class Unavailable(override val command: CommandKind) : PlayerNotice

    /** `503 SERVER_STOPPING`. */
    data class ServerStopping(override val command: CommandKind) : PlayerNotice

    /** `409 CONFLICT_ACTIVE_CLIENT`. */
    data class OtherActive(override val command: CommandKind, val deviceName: String?) : PlayerNotice

    /** `404 NOT_FOUND`. */
    data class NotFound(override val command: CommandKind) : PlayerNotice

    /** The phone did not answer. */
    data class Unreachable(override val command: CommandKind) : PlayerNotice

    /** Any other answer. */
    data class Failed(override val command: CommandKind, val status: Int, val code: String?) : PlayerNotice

    /** WS `error` frame (contract §7.6): a command already confirmed failed later. */
    data class LateError(override val command: CommandKind?, val code: String, val commandId: String?) : PlayerNotice
}

/**
 * What the screens see of the player, whatever drives it. [RemotePlayerRepository] talks to the
 * phone's bridge; a local implementation (standalone desktop) will come later.
 *
 * No optimistic UI: commands never change [state]; it only follows the phone.
 */
interface PlayerRepository {
    /** `null` until the first snapshot. */
    val state: StateFlow<PlayerState?>
    val connection: StateFlow<ConnectionState>

    /** The phone's `features` (contract §5): a missing one hides the matching controls. */
    val features: Set<String>
    val notices: SharedFlow<PlayerNotice>

    /** Estimated phone clock, for [PlayerState.extrapolatedPositionMs]. */
    fun serverNowMs(): Long

    suspend fun artwork(trackId: String): ImageBitmap?
    fun cachedArtwork(trackId: String): ImageBitmap?

    suspend fun play()
    suspend fun pause()
    suspend fun seek(positionMs: Long)
    suspend fun next()
    suspend fun previous()
    suspend fun setSpeed(speed: Float)
    suspend fun setRepeat(mode: RepeatMode)
    suspend fun setShuffle(enabled: Boolean)

    /** Queue items are designated by their index in the effective order **and** their `trackId` (contract §9). */
    suspend fun jump(index: Int, trackId: String)
    suspend fun remove(index: Int, trackId: String)
    suspend fun move(fromIndex: Int, toIndex: Int, trackId: String)
    suspend fun clearQueue()

    fun start()

    /** "Reconnect" / "Retry": a user action, never automatic. */
    fun reconnect()

    fun close()
}
