package app.n_zik.compagnon.playback.services

import app.n_zik.compagnon.bridge.ConnectionState
import app.n_zik.compagnon.bridge.pairing.BridgeErrorCode
import app.n_zik.compagnon.bridge.pairing.RevocationPolicy
import app.n_zik.compagnon.bridge.state.AudioOutput
import app.n_zik.compagnon.bridge.state.PlayerRepository
import app.n_zik.compagnon.bridge.state.PlayerState
import app.n_zik.compagnon.bridge.state.SessionContract
import app.n_zik.compagnon.core.network.AudioApi
import app.n_zik.compagnon.core.network.AudioProbe
import app.n_zik.compagnon.core.network.ForgeResult
import app.n_zik.compagnon.core.network.ServerAddress
import app.n_zik.compagnon.playback.cache.AudioCache
import app.n_zik.compagnon.playback.vlc.AudioEngine
import app.n_zik.compagnon.playback.vlc.AudioSource
import app.n_zik.compagnon.playback.vlc.EngineEvent
import app.n_zik.compagnon.utils.UserSettings
import java.util.logging.Logger
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/** Why the PC could not play the current track; shown as a toast. */
sealed interface LocalPlaybackNotice {
    /** The track is not (or no longer) in the phone's library (`404`). */
    data object NotFound : LocalPlaybackNotice

    /** The phone could not get the audio (`502 AUDIO_UPSTREAM_FAILED`). */
    data object UpstreamFailed : LocalPlaybackNotice

    /** The signed URL stayed invalid after one new one (`403 AUDIO_URL_INVALID`). */
    data object InvalidUrl : LocalPlaybackNotice

    /** Another PC holds the session (`409 CONFLICT_ACTIVE_CLIENT`). */
    data class OtherActive(val deviceName: String?) : LocalPlaybackNotice

    /** The phone did not answer. */
    data object Unreachable : LocalPlaybackNotice

    /** Any other failure (an unreadable stream, an unexpected answer). */
    data object Failed : LocalPlaybackNotice
}

/**
 * The PC's own player (contract §8, §8.5, story 12): it follows the WS state and never sends a command.
 *
 * It plays only when the session is `Live`, `audioOutput = pc`, the phone has the `audio` and
 * `audio.output` features, and libvlc is loaded ([engine] not `null`). It then plays the current track
 * at the extrapolated position: from the [cache] on a hit (no network at all), else from a freshly forged
 * signed URL, the cache being filled in parallel (current track only, cancelled when the track changes).
 *
 * Alignment on the WS state: pause / resume, rate, and a `seek` to the extrapolated position when the
 * phone's position jumped (a seek) or the local player drifted more than [driftToleranceMs].
 *
 * Failures (contract §8.1–§8.4): the URL is re-forged only after `403 AUDIO_URL_EXPIRED`; one new URL on
 * `403 AUDIO_URL_INVALID`, then a failure; `404` / `502` fail at once, without loop; an audio
 * `401 DEVICE_REVOKED` revokes through [RevocationPolicy.revokeNow] unless the kick window holds (§8.4).
 * A failed track is not retried until the track, the output or the session changes.
 *
 * Every decision runs on one actor coroutine; network calls run beside it and post their result back,
 * stale results (an older [generation]) being dropped.
 */
class LocalPlayback(
    private val repository: PlayerRepository,
    private val engine: AudioEngine?,
    private val audio: AudioApi,
    private val address: ServerAddress,
    private val deviceToken: String,
    private val cache: AudioCache,
    private val settings: StateFlow<UserSettings>,
    private val revocation: RevocationPolicy,
    private val scope: CoroutineScope,
    private val driftToleranceMs: Long = DRIFT_TOLERANCE_MS,
    private val tickMs: Long = TICK_MS,
) {
    private val log = Logger.getLogger("LocalPlayback")

    private val _notices = MutableSharedFlow<LocalPlaybackNotice>(extraBufferCapacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val notices: SharedFlow<LocalPlaybackNotice> = _notices.asSharedFlow()

    private sealed interface Input {
        data object StateChanged : Input
        data object Tick : Input
        data class Engine(val event: EngineEvent) : Input
        data class Forged(val generation: Long, val trackId: String, val result: ForgeResult, val revoked: Boolean) : Input
        data class Probed(val generation: Long, val probe: AudioProbe) : Input
        data class Volume(val volume: Float) : Input
    }

    /** What the engine holds now. [url] is set for a stream, never logged. */
    private data class Loaded(
        val trackId: String,
        val url: String?,
        val fromCache: Boolean,
        val started: Boolean = false,
        val paused: Boolean = false,
        val rate: Float = 1f,
    )

    private val inputs = Channel<Input>(Channel.UNLIMITED)
    private val jobs = mutableListOf<Job>()
    private var loaded: Loaded? = null
    private var pending: String? = null
    private var generation = 0L
    private var failedTrackId: String? = null
    private var invalidRetried = false
    private var expiredRetries = 0
    private var lastAnchor: PlayerState? = null
    private var download: Job? = null
    @Volatile private var closed = false

    /** The track the engine plays now (tests, diagnostics). */
    val playingTrackId: String? get() = loaded?.trackId

    fun start() {
        if (engine == null) {
            log.info("Local playback disabled: libvlc is not loaded")
            return
        }
        jobs += scope.launch { for (input in inputs) handle(input) }
        jobs += scope.launch { repository.state.collect { inputs.trySend(Input.StateChanged) } }
        jobs += scope.launch { repository.connection.collect { inputs.trySend(Input.StateChanged) } }
        jobs += scope.launch { engine.events.collect { inputs.trySend(Input.Engine(it)) } }
        jobs += scope.launch { settings.collect { inputs.trySend(Input.Volume(it.playbackVolume)) } }
        jobs += scope.launch {
            while (true) {
                delay(tickMs)
                inputs.trySend(Input.Tick)
            }
        }
    }

    /** Stops the player; the engine itself is released by its owner. */
    fun close() {
        if (closed) return
        closed = true
        jobs.forEach { it.cancel() }
        download?.cancel()
        inputs.close()
        engine?.stop()
    }

    // ---- Actor ------------------------------------------------------------------------------------

    private fun handle(input: Input) {
        if (closed) return
        when (input) {
            Input.StateChanged -> reconcile()
            Input.Tick -> if (loaded?.started == true) align(repository.state.value ?: return)
            is Input.Engine -> onEngine(input.event)
            is Input.Forged -> if (input.generation == generation) onForged(input)
            is Input.Probed -> if (input.generation == generation) onProbed(input.probe)
            is Input.Volume -> engine?.setVolume((input.volume.coerceIn(0f, 1f) * 100).toInt())
        }
    }

    private fun isActive(): Boolean {
        val state = repository.state.value ?: return false
        return engine != null &&
            repository.connection.value == ConnectionState.Live &&
            state.audioOutput == AudioOutput.Pc &&
            SessionContract.FEATURE_AUDIO in repository.features &&
            SessionContract.FEATURE_AUDIO_OUTPUT in repository.features
    }

    private fun reconcile() {
        val state = repository.state.value
        if (state == null || !isActive()) {
            if (loaded != null || pending != null) log.info("Local playback stopped")
            unload()
            failedTrackId = null
            lastAnchor = null
            return
        }
        val track = state.currentTrack
        if (track == null) {
            unload()
            lastAnchor = state
            return
        }
        val current = loaded?.trackId ?: pending
        if (track.id != current) {
            if (track.id != failedTrackId) failedTrackId = null
            unload()
            lastAnchor = state
            // A paused track is loaded once the phone plays it: nothing to align before.
            if (state.isPlaying && failedTrackId == null) load(track.id)
            return
        }
        if (loaded?.started == true) align(state)
        lastAnchor = state
    }

    private fun unload() {
        generation++
        download?.cancel()
        download = null
        if (loaded != null || pending != null) engine?.stop()
        loaded = null
        pending = null
    }

    private fun load(trackId: String) {
        invalidRetried = false
        expiredRetries = 0
        val cached = cache.lookup(trackId)
        if (cached != null) {
            log.info("Playing from the cache")
            loaded = Loaded(trackId, url = null, fromCache = true)
            play(AudioSource.File(cached))
            return
        }
        forge(trackId)
    }

    private fun forge(trackId: String) {
        generation++
        val forGeneration = generation
        pending = trackId
        loaded = null
        val quality = settings.value.audioQualityFormat.wire
        scope.launch {
            val outcome = revocation.confirmRest(
                call = { audio.forgeAudioUrl(address, deviceToken, trackId, quality) },
                isRevoked = { it is ForgeResult.Error && it.error?.code == BridgeErrorCode.DEVICE_REVOKED },
            )
            inputs.trySend(Input.Forged(forGeneration, trackId, outcome.result, outcome.revoked))
        }
    }

    private fun onForged(input: Input.Forged) {
        pending = null
        if (input.revoked) {
            loaded = null
            return
        }
        when (val result = input.result) {
            is ForgeResult.Ok -> {
                loaded = Loaded(input.trackId, url = result.response.url, fromCache = false)
                play(AudioSource.Stream(result.response.url))
                startDownload(input.trackId, result.response.url)
            }
            is ForgeResult.Error -> fail(
                input.trackId,
                when (result.error?.code) {
                    BridgeErrorCode.NOT_FOUND -> LocalPlaybackNotice.NotFound
                    BridgeErrorCode.CONFLICT_ACTIVE_CLIENT -> LocalPlaybackNotice.OtherActive(result.error.activeDevice?.deviceName)
                    else -> LocalPlaybackNotice.Failed
                },
            )
            ForgeResult.Unreachable -> fail(input.trackId, LocalPlaybackNotice.Unreachable)
        }
    }

    private fun play(source: AudioSource) {
        val state = repository.state.value ?: return
        val position = state.extrapolatedPositionMs(repository.serverNowMs())
        loaded = loaded?.copy(started = false, paused = false, rate = state.speed)
        engine?.play(source, position, state.speed)
    }

    private fun startDownload(trackId: String, url: String) {
        download?.cancel()
        if (!cache.isEnabled) return
        download = scope.launch { cache.fill(trackId) { target -> audio.downloadAudio(url, target) } }
    }

    private fun onEngine(event: EngineEvent) {
        val current = loaded ?: return
        when (event) {
            EngineEvent.Playing -> {
                loaded = current.copy(started = true, paused = false)
                expiredRetries = 0
                repository.state.value?.let(::align)
            }
            EngineEvent.Paused -> loaded = current.copy(paused = true)
            // The phone moves to the next track itself: silence until its `trackChanged`.
            EngineEvent.Finished -> loaded = current.copy(started = false)
            EngineEvent.Error -> onEngineError(current)
        }
    }

    private fun onEngineError(current: Loaded) {
        if (current.fromCache) {
            log.info("Cache entry unreadable: dropped, playing from the phone")
            cache.remove(current.trackId)
            forge(current.trackId)
            return
        }
        val url = current.url ?: return fail(current.trackId, LocalPlaybackNotice.Failed)
        generation++
        val forGeneration = generation
        scope.launch { inputs.trySend(Input.Probed(forGeneration, audio.probeAudioUrl(url))) }
    }

    private fun onProbed(probe: AudioProbe) {
        val current = loaded ?: return
        when (probe) {
            AudioProbe.Expired -> {
                if (expiredRetries >= MAX_EXPIRED_RETRIES) return fail(current.trackId, LocalPlaybackNotice.Failed)
                expiredRetries++
                log.info("Audio URL expired: forging a new one")
                forge(current.trackId)
            }
            AudioProbe.Invalid, AudioProbe.Ok -> {
                if (invalidRetried) {
                    return fail(current.trackId, if (probe == AudioProbe.Invalid) LocalPlaybackNotice.InvalidUrl else LocalPlaybackNotice.Failed)
                }
                invalidRetried = true
                forge(current.trackId)
            }
            AudioProbe.Revoked -> {
                unload()
                if (repository.inKickWindow()) {
                    log.info("Audio 401 within the kick window: pairing kept")
                } else {
                    scope.launch { revocation.revokeNow() }
                }
            }
            AudioProbe.NotFound -> fail(current.trackId, LocalPlaybackNotice.NotFound)
            AudioProbe.UpstreamFailed -> fail(current.trackId, LocalPlaybackNotice.UpstreamFailed)
            AudioProbe.Unreachable -> fail(current.trackId, LocalPlaybackNotice.Unreachable)
            is AudioProbe.Failed -> fail(current.trackId, LocalPlaybackNotice.Failed)
        }
    }

    private fun fail(trackId: String, notice: LocalPlaybackNotice) {
        log.info("Local playback failed: ${notice::class.simpleName}")
        unload()
        failedTrackId = trackId
        _notices.tryEmit(notice)
    }

    /** Pause / resume, rate, and a seek on a phone-side jump or a local drift. */
    private fun align(state: PlayerState) {
        val current = loaded ?: return
        if (!current.started) return
        val target = state.extrapolatedPositionMs(repository.serverNowMs())
        var next = current
        if (state.speed != current.rate) {
            engine?.setRate(state.speed)
            next = next.copy(rate = state.speed)
        }
        val local = engine?.timeMs()
        val jumped = lastAnchor?.let { previous ->
            previous.currentTrackId == state.currentTrackId &&
                abs(previous.extrapolatedPositionMs(state.serverTimeMs) - state.positionMs) > SEEK_JUMP_MS
        } == true
        if (jumped || (local != null && abs(local - target) > driftToleranceMs)) {
            engine?.seek(target)
        }
        if (state.isPlaying && current.paused) {
            engine?.resume()
            next = next.copy(paused = false)
        } else if (!state.isPlaying && !current.paused) {
            engine?.pause()
            next = next.copy(paused = true)
        }
        loaded = next
        lastAnchor = state
    }

    companion object {
        /** Story 12: a local position more than this away from the extrapolated one is re-aligned. */
        const val DRIFT_TOLERANCE_MS = 1_500L

        /** A phone-side position jump larger than this, on the same track, is a seek to follow at once. */
        const val SEEK_JUMP_MS = 500L

        const val TICK_MS = 1_000L

        /** Consecutive expirations without the track ever playing before giving up (no loop). */
        const val MAX_EXPIRED_RETRIES = 3
    }
}
