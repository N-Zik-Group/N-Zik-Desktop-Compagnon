package app.n_zik.compagnon.bridge.state

import app.n_zik.compagnon.bridge.pairing.BridgeJson
import java.util.logging.Logger
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Wire values of the WebSocket session and the commands, contract v1 §6, §7, §9 (read from the BMAD
 * workspace, never copied here).
 */
object SessionContract {
    const val WS_PATH = "/ws"

    /** §6.3 / §14: client `ping` period. */
    const val PING_INTERVAL_MS = 15_000L

    /** §9 / §14: a `changed: true` command expects a revision ≥ its own within this delay. */
    const val COMMAND_DELTA_TIMEOUT_MS = 3_000L

    /** §6.4 / §14: reconnection backoff, the last value being the ceiling. */
    val RECONNECT_BACKOFF_MS: List<Long> = listOf(1_000L, 2_000L, 4_000L, 8_000L, 16_000L, 30_000L)

    /** §9: `commandId` length bound. */
    const val COMMAND_ID_MAX_LENGTH = 64

    /** §9 / §14: `speed` bounds. */
    const val SPEED_MIN = 0.25f
    const val SPEED_MAX = 4.0f

    /** §6.4 close codes. */
    const val CLOSE_NORMAL: Short = 1000
    const val CLOSE_SERVER_STOPPED: Short = 1001
    const val CLOSE_SESSION_REPLACED: Short = 4000
    const val CLOSE_KICKED: Short = 4001
    const val CLOSE_DEVICE_REVOKED: Short = 4003
    const val CLOSE_PING_TIMEOUT: Short = 4008

    /** §5 `features` the player UI depends on. */
    const val FEATURE_PLAYBACK = "playback"
    const val FEATURE_QUEUE = "queue"
    const val FEATURE_ARTWORK = "artwork"

    /**
     * §5 / §10 (since 1.6): the phone sorts its library in its own database — the songs, albums,
     * artists and playlists lists with their `sort` and `reverse`, the songs `filter` values beyond
     * all / liked / local / downloaded, and a local playlist's tracks.
     */
    const val FEATURE_LIBRARY_SORT = "library.sort"

    /** §5 / §8: signed audio URLs. */
    const val FEATURE_AUDIO = "audio"

    /** §5 / §8.5 (since 1.2): the audio output (`audioOutput`, `outputChanged`, `player/output`). */
    const val FEATURE_AUDIO_OUTPUT = "audio.output"

    /** §8.4 / §14: an audio `401 DEVICE_REVOKED` this soon after a `4001` close keeps the pairing. */
    const val KICK_AUDIO_WINDOW_MS = 2_000L

    /** §5 `features` of the library screens (contract §10): a missing one hides its screen. */
    const val FEATURE_LIBRARY_SONGS = "library.songs"
    const val FEATURE_LIBRARY_PLAYLISTS = "library.playlists"
    const val FEATURE_LIBRARY_ALBUMS = "library.albums"
    const val FEATURE_LIBRARY_ARTISTS = "library.artists"

    /** §5 / §10.2 (since 1.7): the explicit library writes (like, bookmark, follow, pin), local Room only. */
    const val FEATURE_LIBRARY_WRITE = "library.write"

    /** §5 / §10 (since 1.7.1): `GET /library/cache`, the phone's disk caches behind the cache bar. */
    const val FEATURE_LIBRARY_CACHE = "library.cache"

    /**
     * §5 (since 1.7.2): the `rewind` parameter of `GET /library/playlists` (applied and persisted by
     * the phone) and `GET /library/rewind`, the state of the phone's Month / Year / All row.
     */
    const val FEATURE_LIBRARY_REWIND = "library.rewind"

    /**
     * §5 (since 1.7.2): `GET /library/dislikeMode`, the phone's "disliked" mode per collection —
     * the Disliked chips' visibility and the like rotation / toggle of the menus.
     */
    const val FEATURE_LIBRARY_DISLIKE_MODE = "library.dislikeMode"

    /** §5 (since 1.7.2): the phone's build ships FFmpeg — its "export cache" and "edit metadata" entries. */
    const val FEATURE_LIBRARY_FFMPEG = "library.ffmpeg"

    /**
     * §5 (since 1.7.3): the `libraryChanged` delta (§7.2): an in-app write of the phone (its own UI,
     * or a §10.2 write performed from the PC) invalidates a library family — the PC's loaded lists
     * of that family re-read.
     */
    const val FEATURE_LIBRARY_LIVE = "library.live"

    /** §5 / §10.1 (since 1.7.3): the `sortMenu` of `GET /library/songs`: the phone's effective sort menu of the chip. */
    const val FEATURE_LIBRARY_SORT_MENU = "library.sortMenu"
}

/** `RepeatMode` (contract §1.1); an unknown value reads as [Off]. */
@Serializable(with = RepeatModeSerializer::class)
enum class RepeatMode(val wire: String) {
    Off("off"),
    One("one"),
    All("all");

    companion object {
        fun fromWire(value: String?): RepeatMode = entries.firstOrNull { it.wire == value } ?: Off
    }
}

object RepeatModeSerializer : KSerializer<RepeatMode> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("RepeatMode", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: RepeatMode) = encoder.encodeString(value.wire)
    override fun deserialize(decoder: Decoder): RepeatMode = RepeatMode.fromWire(decoder.decodeString())
}

/** `AudioOutput` (contract §1.1, since 1.2): the device the phone's playback sounds on; an unknown value reads as [Phone]. */
@Serializable(with = AudioOutputSerializer::class)
enum class AudioOutput(val wire: String) {
    Phone("phone"),
    Pc("pc");

    companion object {
        fun fromWire(value: String?): AudioOutput = entries.firstOrNull { it.wire == value } ?: Phone
    }
}

object AudioOutputSerializer : KSerializer<AudioOutput> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("AudioOutput", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: AudioOutput) = encoder.encodeString(value.wire)
    override fun deserialize(decoder: Decoder): AudioOutput = AudioOutput.fromWire(decoder.decodeString())
}

/** `Track.source` (contract §1.1); an unknown value reads as [Online]. */
@Serializable(with = TrackSourceSerializer::class)
enum class TrackSource(val wire: String) {
    Online("online"),
    Local("local");

    companion object {
        fun fromWire(value: String?): TrackSource = entries.firstOrNull { it.wire == value } ?: Online
    }
}

object TrackSourceSerializer : KSerializer<TrackSource> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("TrackSource", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: TrackSource) = encoder.encodeString(value.wire)
    override fun deserialize(decoder: Decoder): TrackSource = TrackSource.fromWire(decoder.decodeString())
}

/** `Track.like` (contract §1.1, since 1.7): the tri-state like; an unknown value reads as [Neutral]. */
@Serializable(with = TrackLikeSerializer::class)
enum class TrackLike(val wire: String) {
    Liked("liked"),
    Neutral("neutral"),
    Disliked("disliked");

    companion object {
        fun fromWire(value: String?): TrackLike = entries.firstOrNull { it.wire == value } ?: Neutral
    }
}

/**
 * The phone's like rotation (its `rotateSongLikeState`, default `excludeDislikedSongs`):
 * neutral → liked → disliked → neutral.
 */
fun TrackLike.nextRotation(): TrackLike = when (this) {
    TrackLike.Neutral -> TrackLike.Liked
    TrackLike.Liked -> TrackLike.Disliked
    TrackLike.Disliked -> TrackLike.Neutral
}

/**
 * The phone's binary like toggle (its `toggleSongLikeState`, used when its "disliked" mode is off —
 * contract 1.7.2 `library.dislikeMode`): neutral → liked, liked → neutral, disliked → liked.
 */
fun TrackLike.nextToggle(): TrackLike = when (this) {
    TrackLike.Neutral -> TrackLike.Liked
    TrackLike.Liked -> TrackLike.Neutral
    TrackLike.Disliked -> TrackLike.Liked
}

object TrackLikeSerializer : KSerializer<TrackLike> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("TrackLike", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: TrackLike) = encoder.encodeString(value.wire)
    override fun deserialize(decoder: Decoder): TrackLike = TrackLike.fromWire(decoder.decodeString())
}

/** `Track.downloadState` (contract §1.1, since 1.7.1): the phone's active download states; an unknown value reads as [None]. */
@Serializable(with = TrackDownloadStateSerializer::class)
enum class TrackDownloadState(val wire: String) {
    /** No active download: the row icon derives from [Track.isDownloaded] (and [Track.isCached] for its color). */
    None("none"),
    /** Queued or restarting: the phone's `download_progress` icon. */
    Queued("queued"),
    /** Downloading: the phone's progress ring, [Track.downloadProgress] in 0..1. */
    Downloading("downloading");

    companion object {
        fun fromWire(value: String?): TrackDownloadState = entries.firstOrNull { it.wire == value } ?: None
    }
}

object TrackDownloadStateSerializer : KSerializer<TrackDownloadState> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("TrackDownloadState", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: TrackDownloadState) = encoder.encodeString(value.wire)
    override fun deserialize(decoder: Decoder): TrackDownloadState = TrackDownloadState.fromWire(decoder.decodeString())
}

/** `Track` (contract §1.1). */
@Serializable
data class Track(
    val id: String,
    val title: String = "",
    val artists: String? = null,
    val durationMs: Long? = null,
    val source: TrackSource = TrackSource.Online,
    val isDownloaded: Boolean = false,
    val isLiked: Boolean = false,
    /**
     * Since 1.7: the tri-state like, [isLiked] being its `liked` projection. A ≤ 1.6 phone does not
     * send it (→ [TrackLike.Neutral]): with no `library.write` the UI reads [isLiked] instead.
     */
    val like: TrackLike = TrackLike.Neutral,
    val hasArtwork: Boolean = false,
    /**
     * Since 1.3: the track carries the phone's explicit mark (`e:` title prefix); [title] never carries it.
     * A 1.2 phone does not send it (→ `false`).
     */
    val isExplicit: Boolean = false,
    /** Since 1.7.1: the song's total play time in ms (the phone's `totalPlayTimeMs` column); the library rows only. */
    val totalPlayTimeMs: Long = 0L,
    /** Since 1.7.1: the song's play count (the phone's `playCount` column); the library rows only. */
    val playCount: Int = 0,
    /**
     * Since 1.7.1: the phone's active download state, [None] when settled (the row icon then derives
     * from [isDownloaded]); a ≤ 1.7 phone does not send it (→ [None]).
     */
    val downloadState: TrackDownloadState = TrackDownloadState.None,
    /** Since 1.7.1: the download progress, 0..1, only while [downloadState] is [TrackDownloadState.Downloading]. */
    val downloadProgress: Float? = null,
    /** Since 1.7.1: in the phone's streaming cache (the phone's row icon color, apart from its state). */
    val isCached: Boolean = false,
    /**
     * Since 1.7.2: the track's artwork is one of the phone's local custom images — the phone's
     * `isCustomImage` predicate (its `SongItem.kt` 251-253, its `MiniPlayer.kt` 631): the
     * `file://` prefix, the `app_covers` covers directory or the `modified:` prefix of its
     * thumbnail url. The phone shows it with `Crop` and every other artwork with `FillHeight`.
     * A ≤ 1.7.1 phone does not send it (→ `false`).
     */
    val isCustomArtwork: Boolean = false,
)

/**
 * The tri-state like to display (contract 1.7): a ≤ 1.6 phone sends only [Track.isLiked] (no
 * [Track.like]), so a liked track whose `like` is still the default still shows as liked. With
 * `library.write` the phone sends the full tri-state and it is used as is.
 */
val Track.displayedLike: TrackLike
    get() = if (like == TrackLike.Neutral && isLiked) TrackLike.Liked else like

/**
 * The phone's "unmatched" mark (phone's `HomeSongs.kt` 589): a track not matched to the phone's
 * library — an id that is not a YouTube one (11 chars), or the phone's `match all` sentinel (00:00
 * and `totalPlayTimeMs == 1L`), and not a local file. Its row shows the phone's orange alert and it
 * cannot be played (the phone's toast).
 */
fun Track.unmatched(): Boolean =
    (id.length != 11 || (durationMs == 0L && totalPlayTimeMs == 1L)) && !id.startsWith("local:")

/**
 * Port of the phone's `Song.formattedTotalPlayTime` (phone's `models/Song.kt` 39-50): `« 45m »`,
 * `« 2h »` or `« 3d »` — the format of the phone's row overlay of the play-time sorts (the phone's
 * `formatAsTime` is not used there).
 */
val Track.formattedTotalPlayTime: String
    get() {
        val seconds = totalPlayTimeMs / 1000
        val hours = seconds / 3600
        return when {
            hours == 0L -> "${seconds / 60}m"
            hours < 24L -> "${hours}h"
            else -> "${hours / 24}d"
        }
    }

/** Why the server stopped (contract §7.7); an unknown value reads as [StopUser]. */
@Serializable(with = StopCodeSerializer::class)
enum class StopCode(val wire: String) {
    StopUser("STOP_USER"),
    AutoStop("AUTO_STOP"),
    Timeout("TIMEOUT");

    companion object {
        fun fromWire(value: String?): StopCode = entries.firstOrNull { it.wire == value } ?: StopUser
    }
}

object StopCodeSerializer : KSerializer<StopCode> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("StopCode", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: StopCode) = encoder.encodeString(value.wire)
    override fun deserialize(decoder: Decoder): StopCode = StopCode.fromWire(decoder.decodeString())
}

// ---- Server → client messages (contract §6.3, §7) -------------------------------------------------

/** One decoded WS frame. Decoding is tolerant: an unknown `type` becomes [UnknownMessage]. */
sealed interface ServerMessage

/** Deltas of §7.2: each one is exactly `revision + 1`. */
sealed interface DeltaMessage : ServerMessage {
    val revision: Long
    val serverTimeMs: Long
}

/** §7.1: full state, applied unconditionally. */
@Serializable
data class SnapshotMessage(
    val revision: Long,
    val serverTimeMs: Long,
    val queue: List<Track> = emptyList(),
    val currentIndex: Int = -1,
    val currentTrackId: String? = null,
    val isPlaying: Boolean = false,
    /** Since 1.4 (contract §7.1); a ≤ 1.3 phone does not send it. */
    val isBuffering: Boolean = false,
    /** Since 1.5 (contract §7.1), the player's live duration; a ≤ 1.4 phone does not send it. */
    val durationMs: Long = PlayerState.DURATION_UNREPORTED,
    val speed: Float = 1f,
    val positionMs: Long = 0,
    val repeatMode: RepeatMode = RepeatMode.Off,
    val shuffle: Boolean = false,
    /** Since 1.2; a 1.1 phone does not send it. */
    val audioOutput: AudioOutput = AudioOutput.Phone,
) : ServerMessage

@Serializable
data class PlaybackChangedMessage(
    override val revision: Long,
    override val serverTimeMs: Long,
    val isPlaying: Boolean,
    /** Since 1.4 (contract §7.2); a ≤ 1.3 phone does not send it. */
    val isBuffering: Boolean = false,
    /** Since 1.5 (contract §7.2), the player's live duration; a ≤ 1.4 phone does not send it. */
    val durationMs: Long = PlayerState.DURATION_UNREPORTED,
    val speed: Float,
    val positionMs: Long,
) : DeltaMessage

@Serializable
data class TrackChangedMessage(
    override val revision: Long,
    override val serverTimeMs: Long,
    val currentIndex: Int,
    val currentTrackId: String? = null,
    val positionMs: Long,
    val isPlaying: Boolean,
    /** Since 1.4 (contract §7.2); a ≤ 1.3 phone does not send it. */
    val isBuffering: Boolean = false,
    /** Since 1.5 (contract §7.2), the player's live duration (reset on a new track); a ≤ 1.4 phone does not send it. */
    val durationMs: Long = PlayerState.DURATION_UNREPORTED,
) : DeltaMessage

@Serializable
data class QueueChangedMessage(
    override val revision: Long,
    override val serverTimeMs: Long,
    val queue: List<Track> = emptyList(),
    val currentIndex: Int = -1,
    val currentTrackId: String? = null,
) : DeltaMessage

@Serializable
data class ModesChangedMessage(
    override val revision: Long,
    override val serverTimeMs: Long,
    val repeatMode: RepeatMode = RepeatMode.Off,
    val shuffle: Boolean = false,
) : DeltaMessage

/** §7.2 (since 1.2): the audio output changed. */
@Serializable
data class OutputChangedMessage(
    override val revision: Long,
    override val serverTimeMs: Long,
    val audioOutput: AudioOutput = AudioOutput.Phone,
) : DeltaMessage

/**
 * §7.2 (since 1.7.3): the phone's library moved (an in-app write, or its songs sort menu) — the
 * PC's loaded lists of [kind]'s family re-read. The player state is untouched by this delta.
 */
@Serializable
data class LibraryChangedMessage(
    override val revision: Long,
    override val serverTimeMs: Long,
    /** The invalidated family (contract §7.2): `songs`, `albums`, `artists` or `playlists`. */
    val kind: String,
) : DeltaMessage

/** §7.3: light, non-revised snapshot. */
@Serializable
data class HeartbeatMessage(
    val revision: Long,
    val serverTimeMs: Long,
    val positionMs: Long,
    val isPlaying: Boolean,
    val speed: Float,
) : ServerMessage

/** §6.3: answer to a client `ping`. */
@Serializable
data class PongMessage(
    val clientTimeMs: Long,
    val serverReceiveTimeMs: Long,
    val serverSendTimeMs: Long,
) : ServerMessage

/** §7.6: late failure of a command already confirmed over REST. Never affects the revision. */
@Serializable
data class ErrorMessage(
    val code: String,
    val message: String = "",
    val commandId: String? = null,
) : ServerMessage

/** §7.7: last message of the session, followed by a `1001` close. */
@Serializable
data class ServerStoppedMessage(
    val code: StopCode = StopCode.StopUser,
    val message: String = "",
) : ServerMessage

/** §7.5: a message of unknown `type`, kept only for revision accounting when it carries an integer `revision`. */
data class UnknownMessage(val type: String?, val revision: Long?) : ServerMessage

/** Tolerant decoder of server frames (contract §1 "Tolérance", §7.5). */
object ServerMessages {
    private val log = Logger.getLogger("ServerMessages")

    /** `null` when the frame is not a JSON object, or when a known type is malformed (logged, ignored). */
    fun decode(text: String): ServerMessage? {
        val json = runCatching { BridgeJson.parseToJsonElement(text) as? JsonObject }.getOrNull() ?: return null
        val type = (json["type"] as? JsonPrimitive)?.takeIf { it.isString }?.content
        val serializer: KSerializer<out ServerMessage> = when (type) {
            "snapshot" -> SnapshotMessage.serializer()
            "playbackChanged" -> PlaybackChangedMessage.serializer()
            "trackChanged" -> TrackChangedMessage.serializer()
            "queueChanged" -> QueueChangedMessage.serializer()
            "modesChanged" -> ModesChangedMessage.serializer()
            "outputChanged" -> OutputChangedMessage.serializer()
            "libraryChanged" -> LibraryChangedMessage.serializer()
            "heartbeat" -> HeartbeatMessage.serializer()
            "pong" -> PongMessage.serializer()
            "error" -> ErrorMessage.serializer()
            "serverStopped" -> ServerStoppedMessage.serializer()
            else -> {
                val revision = (json["revision"] as? JsonPrimitive)?.takeIf { !it.isString }?.longOrNull
                return UnknownMessage(type, revision)
            }
        }
        return runCatching { BridgeJson.decodeFromJsonElement(serializer, json) }
            .onFailure { log.warning("Malformed '$type' frame ignored: ${it::class.simpleName}") }
            .getOrNull()
    }
}

// ---- Client → server messages (contract §7.8) -----------------------------------------------------

@Serializable
data class PingMessage(val clientTimeMs: Long, val type: String = "ping")

@Serializable
data class RequestSnapshotMessage(val type: String = "requestSnapshot")

object ClientMessages {
    fun ping(clientTimeMs: Long): String = BridgeJson.encodeToString(PingMessage.serializer(), PingMessage(clientTimeMs))
    fun requestSnapshot(): String = BridgeJson.encodeToString(RequestSnapshotMessage.serializer(), RequestSnapshotMessage())
}

// ---- Commands (contract §9) ---------------------------------------------------------------------

/** `200` answer of every command. */
@Serializable
data class CommandResponse(val applied: Boolean = true, val changed: Boolean, val revision: Long)

@Serializable
data class EmptyCommandBody(val commandId: String?)

@Serializable
data class SeekCommandBody(val positionMs: Long, val commandId: String?)

@Serializable
data class SpeedCommandBody(val speed: Float, val commandId: String?)

@Serializable
data class RepeatCommandBody(val mode: RepeatMode, val commandId: String?)

@Serializable
data class ShuffleCommandBody(val enabled: Boolean, val commandId: String?)

/** `/player/output` (since 1.2). */
@Serializable
data class OutputCommandBody(val output: AudioOutput, val commandId: String?)

/** `/queue/remove` and `/queue/jump`. */
@Serializable
data class QueueItemCommandBody(val index: Int, val trackId: String, val commandId: String?)

@Serializable
data class QueueMoveCommandBody(val fromIndex: Int, val toIndex: Int, val trackId: String, val commandId: String?)

/** `/queue/play`: replaces the queue and starts at [startIndex] (contract §9). */
@Serializable
data class QueuePlayCommandBody(val trackIds: List<String>, val startIndex: Int, val positionMs: Long = 0, val commandId: String?)

/** `position` of `/queue/add` (contract §9): after the current track, or at the end of the queue. */
@Serializable(with = QueuePositionSerializer::class)
enum class QueuePosition(val wire: String) {
    Next("next"),
    End("end"),
}

object QueuePositionSerializer : KSerializer<QueuePosition> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("QueuePosition", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: QueuePosition) = encoder.encodeString(value.wire)
    override fun deserialize(decoder: Decoder): QueuePosition {
        val wire = decoder.decodeString()
        return QueuePosition.entries.firstOrNull { it.wire == wire } ?: QueuePosition.End
    }
}

@Serializable
data class QueueAddCommandBody(val trackIds: List<String>, val position: QueuePosition, val commandId: String?)

/** Every command the Compagnon sends, with its route under `/api/v1`. */
enum class CommandKind(val route: String) {
    Play("player/play"),
    Pause("player/pause"),
    Seek("player/seek"),
    Next("player/next"),
    Previous("player/previous"),
    Speed("player/speed"),
    Repeat("player/repeat"),
    Shuffle("player/shuffle"),
    Output("player/output"),
    Jump("queue/jump"),
    Remove("queue/remove"),
    Move("queue/move"),
    Clear("queue/clear"),
    QueuePlay("queue/play"),
    QueueAdd("queue/add"),
}
