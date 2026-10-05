package app.n_zik.compagnon.bridge.state

import androidx.compose.ui.graphics.ImageBitmap
import app.n_zik.compagnon.bridge.ConnectionState
import app.n_zik.compagnon.core.network.ArtworkKey
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

    /** More than 500 tracks (contract §9): only the first [sent] of [total] were sent. */
    data class Truncated(override val command: CommandKind, val sent: Int, val total: Int) : PlayerNotice

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

    /**
     * Contract §7.2 (since 1.7.3, feature `library.live`): one of the phone's library families
     * changed — the event is the invalidated family's `kind` (`songs`, `albums`, `artists`,
     * `playlists`), so the loaded lists of that family are re-read. The oldest unseen event is
     * dropped: the flow must never suspend the WS receive loop that emits it.
     */
    val libraryChanged: SharedFlow<String>

    /** Estimated phone clock, for [PlayerState.extrapolatedPositionMs]. */
    fun serverNowMs(): Long

    /** Track, album or artist artwork through the phone; `null` when there is none (or without `artwork`). */
    suspend fun artwork(key: ArtworkKey): ImageBitmap?
    fun cachedArtwork(key: ArtworkKey): ImageBitmap?

    suspend fun play()
    suspend fun pause()
    suspend fun seek(positionMs: Long)
    suspend fun next()
    suspend fun previous()
    suspend fun setSpeed(speed: Float)
    suspend fun setRepeat(mode: RepeatMode)
    suspend fun setShuffle(enabled: Boolean)

    /** `/player/output` (contract §8.5, since 1.2): where the phone's playback sounds. */
    suspend fun setAudioOutput(output: AudioOutput)

    /**
     * Contract §8.4: `true` while kicked, or within 2 s of a `4001` close. An audio `401 DEVICE_REVOKED`
     * then keeps the pairing.
     */
    fun inKickWindow(): Boolean

    /** Queue items are designated by their index in the effective order **and** their `trackId` (contract §9). */
    suspend fun jump(index: Int, trackId: String)
    suspend fun remove(index: Int, trackId: String)
    suspend fun move(fromIndex: Int, toIndex: Int, trackId: String)
    suspend fun clearQueue()

    /**
     * `/queue/play`: the queue becomes [trackIds] and playback starts at [startIndex]. Over 500 ids, only
     * 500 are sent (the window keeps [startIndex]) and a [PlayerNotice.Truncated] tells it. [total] is the
     * real size of the list when the caller only read part of it (a collection read up to 501 tracks, the
     * loaded pages of a list): fewer ids sent than [total] also gives the notice.
     */
    suspend fun playTracks(trackIds: List<String>, startIndex: Int = 0, total: Int = trackIds.size)

    /** `/queue/add` at [position]; over 500 ids, the first 500 only, with a [PlayerNotice.Truncated] (see [playTracks] for [total]). */
    suspend fun addTracks(trackIds: List<String>, position: QueuePosition, total: Int = trackIds.size)

    fun start()

    /** "Reconnect" / "Retry": a user action, never automatic. */
    fun reconnect()

    fun close()
}
