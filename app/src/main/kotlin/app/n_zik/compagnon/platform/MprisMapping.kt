package app.n_zik.compagnon.platform

import app.n_zik.compagnon.bridge.ConnectionState
import app.n_zik.compagnon.bridge.state.PlayerState
import app.n_zik.compagnon.bridge.state.SessionContract

/**
 * The MPRIS `Metadata` dictionary of the current track (spec `spec-tray-taskbar-media-controls`):
 * the phone's wire fields only — **no `xesam:album`** (the wire contract carries no album field and
 * the PC never invents data — frozen decision). [lengthUs] is `null` when no duration is known (the
 * phone's sentinel — the entry is omitted, MPRIS clients read the missing key as "unknown").
 * [artUrl] is the `file://` URL of the artwork already cached locally — `null` while nothing is cached
 * (the phone's artwork endpoint is Bearer-only; KDE cannot reach it).
 */
data class MprisMetadata(
    val trackId: String,
    val title: String,
    /** The phone's `artists` string verbatim, single-element (no parsing — AD-1); empty when absent. */
    val artist: List<String>,
    val lengthUs: Long?,
    val artUrl: String?,
) {
    companion object {
        const val TRACK_ID = "mpris:trackid"
        const val TITLE = "xesam:title"
        const val ARTIST = "xesam:artist"
        const val LENGTH = "mpris:length"
        const val ART_URL = "mpris:artUrl"
    }
}

/**
 * The MPRIS surface state: the `PlaybackStatus`, the `Can*` gates and the current [metadata]
 * (`null` while nothing can be shown).
 */
data class MprisSnapshot(
    val playbackStatus: String,
    val metadata: MprisMetadata?,
    val canGoNext: Boolean,
    val canGoPrevious: Boolean,
    val canPlay: Boolean,
    val canPause: Boolean,
    val canControl: Boolean,
)

/**
 * The MPRIS snapshot from the phone's state (spec `spec-tray-taskbar-media-controls`): no session →
 * `Stopped` and every `Can*` false; live → `Playing` / `Paused` from `isPlaying` (MPRIS defines no
 * buffering status — the phone's rebuffer is a momentary `Playing`) and the `Can*` gates on the
 * contract's `playback` feature alone, like the ThumbBar's enable gate. The duration is the track's
 * `durationMs` with the player's standing in (the phone's own fallback), omitted on sentinel.
 */
fun mprisSnapshot(
    state: PlayerState?,
    connection: ConnectionState,
    features: Set<String>,
    artUrl: String?,
): MprisSnapshot {
    if (connection !is ConnectionState.Live) return stoppedSnapshot()
    val playback = SessionContract.FEATURE_PLAYBACK in features
    val isPlaying = state?.isPlaying == true
    val metadata = state?.currentTrack?.let { track ->
        val lengthMs = track.durationMs ?: state.playerDurationMs
        MprisMetadata(
            trackId = track.id,
            title = track.title,
            artist = track.artists?.let { artists -> listOf(artists) } ?: emptyList(),
            lengthUs = lengthMs?.let { ms -> ms * 1000L },
            artUrl = artUrl,
        )
    }
    val canPlay = playback && !isPlaying
    val canPause = playback && isPlaying
    return MprisSnapshot(
        playbackStatus = if (isPlaying) PLAYING else PAUSED,
        metadata = metadata,
        canGoNext = playback,
        canGoPrevious = playback,
        canPlay = canPlay,
        canPause = canPause,
        canControl = canPlay || canPause,
    )
}

/** The "nothing" snapshot: no session, no metadata, every gate closed (the first state, and the state at detach). */
fun stoppedSnapshot(): MprisSnapshot = MprisSnapshot(
    playbackStatus = STOPPED,
    metadata = null,
    canGoNext = false,
    canGoPrevious = false,
    canPlay = false,
    canPause = false,
    canControl = false,
)

/** The MPRIS `PlaybackStatus` values (the spec's vocabulary). */
const val STOPPED = "Stopped"
const val PLAYING = "Playing"
const val PAUSED = "Paused"

/** The `org.mpris.MediaPlayer2.Player` properties — the [propertiesChanged] diff compares them. */
val ALL_PLAYER_PROPERTIES: List<String> = listOf(
    "PlaybackStatus",
    "Metadata",
    "CanGoNext",
    "CanGoPrevious",
    "CanPlay",
    "CanPause",
    "CanControl",
)

/**
 * The `.Player` properties to announce in a `PropertiesChanged` from [old] to [new]: all of them when
 * [old] is `null` (the first push after the bus name is owned), otherwise only the changed ones.
 * The root interface's properties (`Identity`, `DesktopEntry`, `CanRaise`, `CanQuit`, `Validated`,
 * `ConnectedDesktop`) never change during the process's life, so they never appear in the diff.
 */
fun propertiesChanged(old: MprisSnapshot?, new: MprisSnapshot): List<String> {
    if (old == null) return ALL_PLAYER_PROPERTIES
    return ALL_PLAYER_PROPERTIES.filter { name ->
        when (name) {
            "PlaybackStatus" -> old.playbackStatus != new.playbackStatus
            "Metadata" -> old.metadata != new.metadata
            "CanGoNext" -> old.canGoNext != new.canGoNext
            "CanGoPrevious" -> old.canGoPrevious != new.canGoPrevious
            "CanPlay" -> old.canPlay != new.canPlay
            "CanPause" -> old.canPause != new.canPause
            "CanControl" -> old.canControl != new.canControl
            else -> false
        }
    }
}
