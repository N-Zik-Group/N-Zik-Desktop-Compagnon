package app.n_zik.compagnon.bridge.state

import androidx.compose.ui.graphics.ImageBitmap
import app.n_zik.compagnon.bridge.BridgeSession
import app.n_zik.compagnon.bridge.ConnectionState
import app.n_zik.compagnon.bridge.SessionRevocation
import app.n_zik.compagnon.bridge.StateChannel
import app.n_zik.compagnon.bridge.command.PlayWindow
import app.n_zik.compagnon.bridge.library.ArtworkLoader
import app.n_zik.compagnon.bridge.library.LibraryContract
import app.n_zik.compagnon.bridge.pairing.ActivePairing
import app.n_zik.compagnon.bridge.pairing.BridgeErrorCode
import app.n_zik.compagnon.bridge.pairing.BridgeJson
import app.n_zik.compagnon.bridge.pairing.RevocationPolicy
import app.n_zik.compagnon.core.network.ArtworkKey
import app.n_zik.compagnon.core.network.BridgeApi
import app.n_zik.compagnon.core.network.CommandResult
import app.n_zik.compagnon.core.network.PlayerApi
import app.n_zik.compagnon.core.network.ProbeResult
import app.n_zik.compagnon.core.network.ServerAddress
import io.ktor.client.HttpClient
import java.util.UUID
import java.util.logging.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.KSerializer

/**
 * [PlayerRepository] backed by the phone's bridge: state from the WebSocket ([StateChannel], rules of
 * [StateReducer]), commands over REST (contract §9) through story 10's client.
 *
 * Every command carries a fresh `commandId`. A `changed: true` answer waits up to [deltaTimeoutMs] for
 * a revision ≥ the announced one, else shows a failure and requests a snapshot. Errors are decided from
 * the contract `code`. A REST `401 DEVICE_REVOKED` goes through [RevocationPolicy.confirmRest].
 *
 * While a snapshot requested by the revision rules is awaited (every delta ignored), the request is sent
 * again every [snapshotRetryMs]: a lost `requestSnapshot` must not freeze the state until a reconnection.
 */
class RemotePlayerRepository(
    private val channel: StateChannel,
    private val api: PlayerApi,
    private val address: ServerAddress,
    private val deviceToken: String,
    override val features: Set<String>,
    private val revocation: RevocationPolicy,
    private val scope: CoroutineScope,
    private val artworkLoader: ArtworkLoader = ArtworkLoader(api, address, deviceToken),
    private val deltaTimeoutMs: Long = SessionContract.COMMAND_DELTA_TIMEOUT_MS,
    private val newCommandId: () -> String = { UUID.randomUUID().toString() },
    private val snapshotRetryMs: Long = SNAPSHOT_RETRY_MS,
) : PlayerRepository {
    private val log = Logger.getLogger("RemotePlayerRepository")

    private val sync = MutableStateFlow(SyncState())
    private val _state = MutableStateFlow<PlayerState?>(null)
    override val state: StateFlow<PlayerState?> = _state.asStateFlow()
    override val connection: StateFlow<ConnectionState> get() = channel.connection

    // Never suspends an emitter (the WS receive loop among them): the oldest unseen notice is dropped.
    private val _notices = MutableSharedFlow<PlayerNotice>(extraBufferCapacity = 16, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val notices: SharedFlow<PlayerNotice> = _notices.asSharedFlow()

    // The phone's library moved (§7.2, since 1.7.3): the oldest unseen family is dropped, as [notices].
    private val _libraryChanged = MutableSharedFlow<String>(extraBufferCapacity = 16, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val libraryChanged: SharedFlow<String> = _libraryChanged.asSharedFlow()

    /** Last revision applied (contract §7.4), for tests and diagnostics. */
    val lastRevision: Long? get() = sync.value.last

    /** `commandId` → command, so a late WS `error` names the command that failed. */
    private val recentCommands = object : LinkedHashMap<String, CommandKind>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CommandKind>?): Boolean = size > RECENT_COMMANDS
    }

    private var snapshotRetry: Job? = null
    @Volatile private var closed = false

    override fun start() = channel.start(::onMessage)
    override fun reconnect() = channel.reconnect()

    override fun close() {
        if (closed) return
        closed = true
        snapshotRetry?.cancel()
        channel.close()
        artworkLoader.clear()
    }

    override fun serverNowMs(): Long = channel.clock.serverNowMs()

    override suspend fun artwork(key: ArtworkKey): ImageBitmap? =
        if (SessionContract.FEATURE_ARTWORK in features) artworkLoader.load(key) else null

    override fun cachedArtwork(key: ArtworkKey): ImageBitmap? = artworkLoader.cached(key)

    // ---- State ------------------------------------------------------------------------------------

    /** Serializes [patchTrackLike] (UI thread) and the reduction of [onMessage] (WS thread): no lost patch. */
    private val syncLock = Any()

    override fun patchTrackLike(trackId: String, like: TrackLike) {
        // In the sync state too: a delta of another family (playback, modes) re-publishes it
        // without reverting the patch; the phone's queue delta then replaces it
        synchronized(syncLock) {
            val patched = sync.updateAndGet { it.copy(player = it.player?.withTrackLike(trackId, like)) }
            _state.value = patched.player
        }
    }

    private suspend fun onMessage(message: ServerMessage) {
        val reduction = synchronized(syncLock) {
            StateReducer.reduce(sync.value, message).also {
                sync.value = it.state
                _state.value = it.state.player
            }
        }
        if (reduction.requestSnapshot) requestAwaitedSnapshot()
        if (!reduction.state.awaitingSnapshot) snapshotRetry?.cancel()
        if (message is ErrorMessage) {
            val command = message.commandId?.let { id -> synchronized(recentCommands) { recentCommands[id] } }
            _notices.emit(PlayerNotice.LateError(command, message.code, message.commandId))
        }
        // Only an applied delta re-reads a family: a rejected one (an out-of-order or duplicated
        // revision, contract §7.4) moved no state — no reload
        if (message is LibraryChangedMessage && reduction.applied) _libraryChanged.emit(message.kind)
        // Contract §7.9 (since 1.10.0): the phone's relayed toast, shown on the PC as on the phone
        if (message is ToastMessage && SessionContract.FEATURE_UI_TOASTS in features) {
            _notices.emit(PlayerNotice.PhoneToast(message.key, message.args, message.toastType, message.message))
        }
    }

    private fun requestAwaitedSnapshot() {
        channel.requestSnapshot()
        snapshotRetry?.cancel()
        snapshotRetry = scope.launch {
            while (true) {
                delay(snapshotRetryMs)
                if (!sync.value.awaitingSnapshot) break
                log.info("Snapshot still awaited after $snapshotRetryMs ms, requesting it again")
                channel.requestSnapshot()
            }
        }
    }

    // ---- Commands (contract §9) -------------------------------------------------------------------

    override suspend fun play() = send(CommandKind.Play, EmptyCommandBody.serializer()) { EmptyCommandBody(it) }
    override suspend fun pause() = send(CommandKind.Pause, EmptyCommandBody.serializer()) { EmptyCommandBody(it) }
    override suspend fun next() = send(CommandKind.Next, EmptyCommandBody.serializer()) { EmptyCommandBody(it) }
    override suspend fun previous() = send(CommandKind.Previous, EmptyCommandBody.serializer()) { EmptyCommandBody(it) }

    override suspend fun seek(positionMs: Long) =
        send(CommandKind.Seek, SeekCommandBody.serializer()) { SeekCommandBody(positionMs.coerceAtLeast(0), it) }

    override suspend fun setSpeed(speed: Float) = send(CommandKind.Speed, SpeedCommandBody.serializer()) {
        SpeedCommandBody(speed.coerceIn(SessionContract.SPEED_MIN, SessionContract.SPEED_MAX), it)
    }

    override suspend fun setRepeat(mode: RepeatMode) = send(CommandKind.Repeat, RepeatCommandBody.serializer()) { RepeatCommandBody(mode, it) }
    override suspend fun setShuffle(enabled: Boolean) = send(CommandKind.Shuffle, ShuffleCommandBody.serializer()) { ShuffleCommandBody(enabled, it) }

    override suspend fun setAudioOutput(output: AudioOutput) =
        send(CommandKind.Output, OutputCommandBody.serializer()) { OutputCommandBody(output, it) }

    override fun inKickWindow(): Boolean {
        if (channel.connection.value == ConnectionState.Kicked) return true
        val kickedAt = channel.lastKickAtMs ?: return false
        return channel.clock.nowMonotonicMs() - kickedAt <= SessionContract.KICK_AUDIO_WINDOW_MS
    }

    override suspend fun jump(index: Int, trackId: String) =
        send(CommandKind.Jump, QueueItemCommandBody.serializer()) { QueueItemCommandBody(index, trackId, it) }

    override suspend fun remove(index: Int, trackId: String) =
        send(CommandKind.Remove, QueueItemCommandBody.serializer()) { QueueItemCommandBody(index, trackId, it) }

    override suspend fun move(fromIndex: Int, toIndex: Int, trackId: String) =
        send(CommandKind.Move, QueueMoveCommandBody.serializer()) { QueueMoveCommandBody(fromIndex, toIndex, trackId, it) }

    override suspend fun clearQueue() = send(CommandKind.Clear, EmptyCommandBody.serializer()) { EmptyCommandBody(it) }

    override suspend fun playTracks(trackIds: List<String>, startIndex: Int, total: Int) {
        if (trackIds.isEmpty()) return
        val window = PlayWindow.around(trackIds, startIndex.coerceIn(0, trackIds.lastIndex))
        if (window.trackIds.size < maxOf(total, trackIds.size)) {
            _notices.emit(PlayerNotice.Truncated(CommandKind.QueuePlay, window.trackIds.size, maxOf(total, trackIds.size)))
        }
        send(CommandKind.QueuePlay, QueuePlayCommandBody.serializer()) {
            QueuePlayCommandBody(window.trackIds, window.startIndex, commandId = it)
        }
    }

    override suspend fun addTracks(trackIds: List<String>, position: QueuePosition, total: Int) {
        if (trackIds.isEmpty()) return
        val sent = trackIds.take(LibraryContract.TRACK_IDS_MAX)
        if (sent.size < maxOf(total, trackIds.size)) {
            _notices.emit(PlayerNotice.Truncated(CommandKind.QueueAdd, sent.size, maxOf(total, trackIds.size)))
        }
        send(CommandKind.QueueAdd, QueueAddCommandBody.serializer()) { QueueAddCommandBody(sent, position, it) }
    }

    override suspend fun playList(list: ListRef, action: ListAction, startIndex: Int, startTrackId: String?) {
        // `changed` is always false on this route: the phone's queue entries run asynchronously and their
        // result shows through the usual deltas and its `toast` messages (contract §9)
        send(CommandKind.QueueList, QueueListCommandBody.serializer()) {
            QueueListCommandBody(list, action.wire, startIndex.coerceAtLeast(0), startTrackId, it)
        }
    }

    private suspend fun <T> send(kind: CommandKind, serializer: KSerializer<T>, body: (commandId: String) -> T) {
        val commandId = newCommandId().take(SessionContract.COMMAND_ID_MAX_LENGTH)
        synchronized(recentCommands) { recentCommands[commandId] = kind }
        val json = BridgeJson.encodeToString(serializer, body(commandId))
        val outcome = revocation.confirmRest(
            call = { api.command(address, deviceToken, kind.route, json) },
            isRevoked = { it is CommandResult.Error && it.error?.code == BridgeErrorCode.DEVICE_REVOKED },
        )
        if (outcome.revoked) return
        when (val result = outcome.result) {
            is CommandResult.Ok -> if (result.response.changed) awaitRevision(kind, result.response.revision)
            CommandResult.Unreachable -> _notices.emit(PlayerNotice.Unreachable(kind))
            is CommandResult.Error -> onError(kind, result)
        }
    }

    /** Contract §9: no revision ≥ [revision] within [deltaTimeoutMs] → failure message and `requestSnapshot`. */
    private suspend fun awaitRevision(kind: CommandKind, revision: Long) {
        val reached = withTimeoutOrNull(deltaTimeoutMs) { sync.first { (it.last ?: Long.MIN_VALUE) >= revision } }
        if (reached == null) {
            log.info("$kind: no revision ≥ $revision within $deltaTimeoutMs ms, requesting a snapshot")
            _notices.emit(PlayerNotice.NoDelta(kind))
            channel.requestSnapshot()
        }
    }

    private suspend fun onError(kind: CommandKind, result: CommandResult.Error) {
        val code = result.error?.code
        val notice = when (code) {
            BridgeErrorCode.QUEUE_MISMATCH -> {
                channel.requestSnapshot()
                PlayerNotice.QueueMismatch(kind)
            }
            BridgeErrorCode.PLAYER_REJECTED -> PlayerNotice.Rejected(kind)
            BridgeErrorCode.PLAYER_UNAVAILABLE -> PlayerNotice.Unavailable(kind)
            BridgeErrorCode.SERVER_STOPPING -> PlayerNotice.ServerStopping(kind)
            BridgeErrorCode.CONFLICT_ACTIVE_CLIENT -> PlayerNotice.OtherActive(kind, result.error.activeDevice?.deviceName)
            BridgeErrorCode.NOT_FOUND -> PlayerNotice.NotFound(kind)
            else -> PlayerNotice.Failed(kind, result.status, code)
        }
        _notices.emit(notice)
    }

    companion object {
        private const val RECENT_COMMANDS = 64
        const val SNAPSHOT_RETRY_MS = 3_000L

        /**
         * Revocation hooks of the session: `4003` revokes at once; an upgrade `401 DEVICE_REVOKED` counts as
         * the first answer of [RevocationPolicy.confirmRest], confirmed by exactly one [probe] 2 s later.
         */
        fun sessionRevocation(revocation: RevocationPolicy, probe: suspend () -> ProbeResult): SessionRevocation =
            object : SessionRevocation {
                override suspend fun revokeNow() = revocation.revokeNow()
                override suspend fun confirmUpgradeRevoked(): Boolean {
                    var first = true
                    return revocation.confirmRest(
                        call = {
                            if (first) {
                                first = false
                                ProbeResult.Revoked
                            } else {
                                probe()
                            }
                        },
                        isRevoked = { it is ProbeResult.Revoked },
                    ).revoked
                }
            }

        /**
         * Production wiring from story 10's credential: a [BridgeSession] on [wsClient] (see
         * [BridgeSession.httpClient]) and [client] for REST. Upgrade `4003` revokes at once; an upgrade
         * `401 DEVICE_REVOKED` counts as the first answer and is confirmed by one REST probe 2 s later.
         */
        fun <C> create(
            active: ActivePairing,
            client: C,
            wsClient: HttpClient,
            revocation: RevocationPolicy,
            scope: CoroutineScope,
        ): RemotePlayerRepository where C : PlayerApi, C : BridgeApi {
            val token = active.pairing.deviceToken
            val address = active.address
            val hooks = sessionRevocation(revocation) { client.probe(address, token) }
            val session = BridgeSession(wsClient, address, token, scope, hooks)
            return RemotePlayerRepository(session, client, address, token, active.features, revocation, scope)
        }
    }
}
