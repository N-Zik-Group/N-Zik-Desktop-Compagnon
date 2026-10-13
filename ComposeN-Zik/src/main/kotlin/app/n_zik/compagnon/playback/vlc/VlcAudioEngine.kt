package app.n_zik.compagnon.playback.vlc

import java.nio.file.Path
import java.util.Locale
import java.util.logging.Logger
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import uk.co.caprica.vlcj.player.base.State

/** What the local player reads: the signed URL of the phone (contract §8.1) or a complete cache entry. */
sealed interface AudioSource {
    /** Never printed: the URL is a transfer credential (contract §8.3). */
    class Stream(val url: String) : AudioSource {
        override fun toString(): String = "Stream(***)"
    }

    data class File(val path: Path) : AudioSource
}

/** What the engine reports; never acted on from the native callback itself. */
sealed interface EngineEvent {
    data object Playing : EngineEvent
    data object Paused : EngineEvent
    data object Finished : EngineEvent

    /** libvlc could not open or read the media (HTTP error, unreadable file…). */
    data object Error : EngineEvent
}

/**
 * The local audio player, driven only by [app.n_zik.compagnon.playback.services.LocalPlayback]: it never
 * decides anything by itself. Only the volume is a local setting (contract §8.5).
 */
interface AudioEngine {
    val events: SharedFlow<EngineEvent>

    /** Stops what is loaded and plays [source] from [positionMs] at [rate]. */
    fun play(source: AudioSource, positionMs: Long, rate: Float)
    fun pause()
    fun resume()
    fun seek(positionMs: Long)
    fun setRate(rate: Float)

    /** 0–100. */
    fun setVolume(percent: Int)
    fun stop()

    /** Current position of the loaded media (playing or paused), or `null` when there is none yet. */
    fun timeMs(): Long?

    /** Player and factory released; the engine is unusable afterwards. */
    fun release()
}

/**
 * [AudioEngine] on vlcj 4.12.1 / libvlc 3.0.24 (audio only). Every libvlc call goes through
 * [MediaPlayer.submit], on vlcj's own thread, so libvlc is never called back from one of its callbacks;
 * the callbacks only publish [events].
 */
class VlcAudioEngine : AudioEngine {
    private val log = Logger.getLogger("VlcAudioEngine")

    private val factory = MediaPlayerFactory(*FACTORY_ARGS)
    private val player: MediaPlayer = factory.mediaPlayers().newMediaPlayer()

    private val _events = MutableSharedFlow<EngineEvent>(extraBufferCapacity = 32, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val events: SharedFlow<EngineEvent> = _events.asSharedFlow()

    @Volatile private var volume = 100
    @Volatile private var released = false

    private val listener = object : MediaPlayerEventAdapter() {
        override fun playing(mediaPlayer: MediaPlayer) {
            // The output may reset its volume on a new media: applied again, off the callback.
            submit { it.audio().setVolume(volume) }
            _events.tryEmit(EngineEvent.Playing)
        }

        override fun paused(mediaPlayer: MediaPlayer) {
            _events.tryEmit(EngineEvent.Paused)
        }

        override fun finished(mediaPlayer: MediaPlayer) {
            _events.tryEmit(EngineEvent.Finished)
        }

        override fun error(mediaPlayer: MediaPlayer) {
            _events.tryEmit(EngineEvent.Error)
        }
    }

    init {
        player.events().addMediaPlayerEventListener(listener)
    }

    override fun play(source: AudioSource, positionMs: Long, rate: Float) {
        val mrl = when (source) {
            is AudioSource.Stream -> source.url
            is AudioSource.File -> source.path.toUri().toString()
        }
        val start = String.format(Locale.ROOT, ":start-time=%.3f", positionMs.coerceAtLeast(0) / 1_000.0)
        submit {
            it.controls().stop()
            if (!it.media().play(mrl, start)) {
                log.warning("libvlc refused the media")
                _events.tryEmit(EngineEvent.Error)
                return@submit
            }
            it.controls().setRate(rate)
        }
    }

    override fun pause() = submit { it.controls().setPause(true) }
    override fun resume() = submit { it.controls().setPause(false) }
    override fun seek(positionMs: Long) = submit { it.controls().setTime(positionMs.coerceAtLeast(0)) }
    override fun setRate(rate: Float) = submit { it.controls().setRate(rate) }

    override fun setVolume(percent: Int) {
        volume = percent.coerceIn(0, 100)
        submit { it.audio().setVolume(volume) }
    }

    override fun stop() = submit { it.controls().stop() }

    override fun timeMs(): Long? {
        if (released) return null
        return runCatching {
            val state = player.status().state()
            if (state == State.PLAYING || state == State.PAUSED) player.status().time() else -1L
        }.getOrNull()?.takeIf { it >= 0 }
    }

    override fun release() {
        if (released) return
        released = true
        runCatching {
            player.events().removeMediaPlayerEventListener(listener)
            player.controls().stop()
            player.release()
        }.onFailure { log.warning("Media player release failed: ${it::class.simpleName}") }
        runCatching { factory.release() }.onFailure { log.warning("Factory release failed: ${it::class.simpleName}") }
    }

    private fun submit(action: (MediaPlayer) -> Unit) {
        if (released) return
        player.submit {
            if (released) return@submit
            runCatching { action(player) }.onFailure { log.warning("libvlc call failed: ${it::class.simpleName}") }
        }
    }

    companion object {
        private val FACTORY_ARGS = arrayOf(
            "--no-video",
            "--intf=dummy",
            "--quiet",
            "--no-metadata-network-access",
            "--network-caching=300",
        )
    }
}
