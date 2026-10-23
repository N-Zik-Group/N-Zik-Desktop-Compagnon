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
    /** The progress bar can seek: the `playback` feature AND a known duration (the timeline needs an end). */
    val canSeek: Boolean = false,
    /** The playback rate (the phone's speed) — MPRIS clients extrapolate the position from it. */
    val rate: Double = 1.0,
)

/**
 * The MPRIS snapshot from the phone's state (spec `spec-tray-taskbar-media-controls`): no session →
 * `Stopped` and every `Can*` false; live with a current track → `Playing` / `Paused` from `isPlaying`
 * (MPRIS defines no buffering status — the phone's rebuffer is a momentary `Playing`); live without
 * one → `Stopped` (nothing loaded). Per MPRIS v2.2, `CanPlay` / `CanPause` are properties of the
 * CURRENT TRACK, not the playback state — both track the contract's `playback` feature AND a loaded
 * track, independent of `isPlaying` ("if a track is currently playing (and CanControl is true), this
 * should be true"; a state-based `CanPlay` would drop the player from GNOME 51's media area while
 * playing — the shell filters its players on `CanPlay`). The duration is the track's `durationMs`
 * with the player's standing in (the phone's own fallback), omitted on sentinel.
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
    // MPRIS v2.2: `CanPlay` / `CanPause` are properties of the CURRENT TRACK, not of the playback
    // state ("the value should not depend on whether the track is currently paused or playing") —

    // both track the `playback` feature AND a loaded track, open in every state of a loaded track.
    val hasTrack = metadata != null
    val canPlay = playback && hasTrack
    val canPause = playback && hasTrack
    return MprisSnapshot(
        // Nothing loaded → `Stopped`; a live track → `Playing` / `Paused` from the phone.
        playbackStatus = if (!hasTrack) STOPPED else if (isPlaying) PLAYING else PAUSED,
        metadata = metadata,
        canGoNext = playback,
        canGoPrevious = playback,
        canPlay = canPlay,
        canPause = canPause,
        canControl = canPlay || canPause,
        canSeek = playback && metadata?.lengthUs != null,
        rate = (state?.speed ?: 1f).toDouble(),
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
    "CanSeek",
    "Rate",
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
            "CanSeek" -> old.canSeek != new.canSeek
            "Rate" -> old.rate != new.rate
            else -> false
        }
    }
}

/**
 * The MPRIS `mpris:trackid` of a phone track: an OBJECT PATH, as the MPRIS spec requires (KDE's
 * `SetPosition` — the progress bar's seek — matches it and ignores a plain string). The phone's id is
 * hex-encoded TWO DIGITS PER BYTE (unsigned — a signed `Byte` would sign-extend into 8–16 digit
 * segments for UTF-8 bytes ≥ 0x80): an object path element only allows `[A-Za-z0-9_]`, and the
 * encoding keeps two ids distinct.
 */
fun mprisTrackPath(trackId: String): String =
    MPRIS_TRACK_PATH_PREFIX + trackId.toByteArray(Charsets.UTF_8).joinToString("") { "%02x".format(it.toInt() and 0xFF) }

/** The current position for MPRIS `Position` (µs): the phone's extrapolated position while live, 0 otherwise. */
fun mprisPositionUs(state: PlayerState?, connection: ConnectionState, serverNowMs: Long): Long =
    if (connection is ConnectionState.Live && state != null) state.extrapolatedPositionMs(serverNowMs) * 1000L else 0L

/**
 * Whether the position jumped between two pushes of the SAME track (a seek, from the phone or another
 * client): MPRIS clients extrapolate from `Position` + `Rate`, so a discontinuity must be announced with
 * `Seeked`. A track change is not a seek (the clients reset on the new `Metadata`).
 */
fun positionJumped(old: PlayerState?, new: PlayerState?, serverNowMs: Long): Boolean {
    if (old == null || new == null) return false
    val track = new.currentTrack ?: return false
    if (old.currentTrack?.id != track.id) return false
    return kotlin.math.abs(old.extrapolatedPositionMs(serverNowMs) - new.extrapolatedPositionMs(serverNowMs)) > POSITION_JUMP_MS
}

private const val MPRIS_TRACK_PATH_PREFIX = "/app/n_zik/compagnon/track/t"

/** The drift beyond which a position change is a seek, not the extrapolation's own jitter. */
private const val POSITION_JUMP_MS = 1500L
