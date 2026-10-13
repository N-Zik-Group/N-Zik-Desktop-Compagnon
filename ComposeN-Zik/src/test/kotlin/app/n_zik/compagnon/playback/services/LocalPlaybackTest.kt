package app.n_zik.compagnon.playback.services

import androidx.compose.ui.graphics.ImageBitmap
import app.n_zik.compagnon.bridge.ConnectionState
import app.n_zik.compagnon.bridge.pairing.ApiError
import app.n_zik.compagnon.bridge.pairing.RevocationPolicy
import app.n_zik.compagnon.bridge.state.AudioOutput
import app.n_zik.compagnon.bridge.state.PlayerNotice
import app.n_zik.compagnon.bridge.state.PlayerRepository
import app.n_zik.compagnon.bridge.state.PlayerState
import app.n_zik.compagnon.bridge.state.QueuePosition
import app.n_zik.compagnon.bridge.state.RepeatMode
import app.n_zik.compagnon.bridge.state.Track
import app.n_zik.compagnon.core.network.ArtworkKey
import app.n_zik.compagnon.core.network.AudioApi
import app.n_zik.compagnon.core.network.AudioProbe
import app.n_zik.compagnon.core.network.AudioUrlResponse
import app.n_zik.compagnon.core.network.DownloadResult
import app.n_zik.compagnon.core.network.ForgeResult
import app.n_zik.compagnon.core.network.ServerAddress
import app.n_zik.compagnon.enums.AudioQualityFormat
import app.n_zik.compagnon.enums.ExoPlayerDiskCacheMaxSize
import app.n_zik.compagnon.playback.cache.AudioCache
import app.n_zik.compagnon.playback.vlc.AudioEngine
import app.n_zik.compagnon.playback.vlc.AudioSource
import app.n_zik.compagnon.playback.vlc.EngineEvent
import app.n_zik.compagnon.utils.UserSettings
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.opentest4j.AssertionFailedError
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class LocalPlaybackTest {

    @TempDir
    lateinit var dir: Path

    private val a = Track("dQw4w9WgXcQ", "A", durationMs = 212_000)
    private val b = Track("local:42", "B", durationMs = 180_000)

    /** Every call the engine received, in order. */
    private class FakeEngine : AudioEngine {
        val calls = mutableListOf<String>()
        val sources = mutableListOf<AudioSource>()
        var time: Long? = null
        override val events = MutableSharedFlow<EngineEvent>(extraBufferCapacity = 16)
        override fun play(source: AudioSource, positionMs: Long, rate: Float) {
            sources += source
            calls += "play ${if (source is AudioSource.File) "file" else (source as AudioSource.Stream).url} at $positionMs x$rate"
        }
        override fun pause() { calls += "pause" }
        override fun resume() { calls += "resume" }
        override fun seek(positionMs: Long) { calls += "seek $positionMs" }
        override fun setRate(rate: Float) { calls += "rate $rate" }
        override fun setVolume(percent: Int) { calls += "volume $percent" }
        override fun stop() { calls += "stop" }
        override fun timeMs(): Long? = time
        override fun release() { calls += "release" }
        fun playCalls() = calls.filter { it.startsWith("play") }
    }

    /** A phone seen through the WS only; any command fails the test (the local player never sends one). */
    private class FakeRepository(
        override val features: Set<String> = setOf("playback", "queue", "audio", "audio.output"),
    ) : PlayerRepository {
        override val state = MutableStateFlow<PlayerState?>(null)
        override val connection = MutableStateFlow<ConnectionState>(ConnectionState.Live)
        override val notices: SharedFlow<PlayerNotice> = MutableSharedFlow()
        override val libraryChanged: SharedFlow<String> = MutableSharedFlow()
        var now = 1_000L
        var kickWindow = false
        override fun serverNowMs(): Long = now
        override fun inKickWindow(): Boolean = kickWindow
        override suspend fun artwork(key: ArtworkKey): ImageBitmap? = null
        override fun cachedArtwork(key: ArtworkKey): ImageBitmap? = null
        private fun command(): Nothing = error("the local player must never send a command")
        override suspend fun play() = command()
        override suspend fun pause() = command()
        override suspend fun seek(positionMs: Long) = command()
        override suspend fun next() = command()
        override suspend fun previous() = command()
        override suspend fun setSpeed(speed: Float) = command()
        override suspend fun setRepeat(mode: RepeatMode) = command()
        override suspend fun setShuffle(enabled: Boolean) = command()
        override suspend fun setAudioOutput(output: AudioOutput) = command()
        override suspend fun jump(index: Int, trackId: String) = command()
        override suspend fun remove(index: Int, trackId: String) = command()
        override suspend fun move(fromIndex: Int, toIndex: Int, trackId: String) = command()
        override suspend fun clearQueue() = command()
        override suspend fun playTracks(trackIds: List<String>, startIndex: Int, total: Int) = command()
        override suspend fun addTracks(trackIds: List<String>, position: QueuePosition, total: Int) = command()
        override fun start() = Unit
        override fun reconnect() = Unit
        override fun close() = Unit
    }

    private class FakeAudio : AudioApi {
        val forges = mutableListOf<String>()
        val forgeResults = ArrayDeque<ForgeResult>()
        val probes = ArrayDeque<AudioProbe>()
        var probed = 0
        var downloads = 0
        var download: suspend (Path) -> DownloadResult = { DownloadResult.Failed(null) }
        private var urls = 0
        override suspend fun forgeAudioUrl(address: ServerAddress, deviceToken: String, trackId: String, quality: String): ForgeResult {
            forges += "$trackId/$quality"
            return forgeResults.removeFirstOrNull()
                ?: ForgeResult.Ok(AudioUrlResponse(trackId, quality, "http://phone/audio/$trackId?t=${++urls}", 0, null))
        }
        override suspend fun probeAudioUrl(url: String): AudioProbe {
            probed++
            return probes.removeFirstOrNull() ?: AudioProbe.Ok
        }
        override suspend fun downloadAudio(url: String, target: Path): DownloadResult {
            downloads++
            return download(target)
        }
    }

    private val engine = FakeEngine()
    private val repo = FakeRepository()
    private val audio = FakeAudio()
    private val settings = MutableStateFlow(UserSettings())
    private val notices = mutableListOf<LocalPlaybackNotice>()
    private var revoked = 0
    private lateinit var cache: AudioCache

    private fun TestScope.playback(
        engine: AudioEngine? = this@LocalPlaybackTest.engine,
        repository: FakeRepository = repo,
    ): LocalPlayback {
        cache = AudioCache(dir.resolve("cache"), maxBytes = { settings.value.songCacheMaxBytes }, io = UnconfinedTestDispatcher(testScheduler))
        val playback = LocalPlayback(
            repository = repository,
            engine = engine,
            audio = audio,
            address = ServerAddress("192.168.1.14", 42420),
            deviceToken = "T".repeat(43),
            cache = cache,
            settings = settings,
            revocation = RevocationPolicy(onRevoked = { revoked++ }, confirmDelayMs = 2_000),
            scope = backgroundScope,
            tickMs = 1_000,
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { playback.notices.collect { notices += it } }
        playback.start()
        runCurrent()
        return playback
    }

    private fun state(
        output: AudioOutput = AudioOutput.Pc,
        track: Track = a,
        playing: Boolean = true,
        position: Long = 10_000,
        at: Long = 1_000,
        speed: Float = 1f,
    ) = PlayerState(
        queue = listOf(a, b),
        currentIndex = listOf(a, b).indexOf(track),
        currentTrackId = track.id,
        isPlaying = playing,
        speed = speed,
        positionMs = position,
        serverTimeMs = at,
        audioOutput = output,
    )

    /** A fingerprint of everything the actor's work leaves behind (engine calls, forges, probes,
     *  downloads, notices): [settle]'s progress marker. */
    private fun workSignal(): Int =
        engine.calls.size * 1_000_000_000 +
            audio.forges.size * 1_000_000 +
            audio.probed * 1_000 +
            audio.downloads * 10 +
            notices.size

    /**
     * Pumps virtual time until the scheduler is quiet for three consecutive rounds. The actor runs
     * the cache's file I/O on the real data dispatcher (`withContext(NzikDispatchers.DATA)`), a hop
     * out of virtual time: right after a state change or engine event it may still be mid-hop when
     * [runCurrent] returns, so the pump breathes in real time until the hop has drained.
     */
    private fun TestScope.settle() {
        var quiet = 0
        while (quiet < 3) {
            val before = workSignal()
            runCurrent()
            if (workSignal() == before) {
                quiet += 1
                Thread.sleep(10)
            } else {
                quiet = 0
            }
        }
    }

    private fun TestScope.set(state: PlayerState?) {
        repo.state.value = state
        settle()
    }

    /**
     * Waits until the engine has received [expectedCalls] play call(s) (real-time pump:
     * [runCurrent] + a real-time breath for the data-dispatcher hop, like [settle]). Unlike
     * [settle] this is condition-based — it returns as soon as the expected count is reached,
     * and FAILS EXPLICITLY at the budget expiry (the missing play, not a misleading late
     * assertion on the last call — spec `spec-arch-binary-package-release`, loop 2 G15).
     */
    private fun TestScope.awaitPlay(expectedCalls: Int, budgetMs: Long = 300) {
        val deadline = System.currentTimeMillis() + budgetMs
        while (engine.playCalls().size < expectedCalls) {
            runCurrent()
            Thread.sleep(10)
            if (System.currentTimeMillis() > deadline) {
                // The budget is exhausted before the expected play call(s) arrive: fail the test explicitly
                // (a late, misleading assertion on the last call would hide the missing play — spec
                // `spec-arch-binary-package-release`, loop 2 G15)
                throw AssertionFailedError("awaitPlay: expected $expectedCalls play call(s), got ${engine.playCalls().size} (calls: ${engine.calls})")
            }
        }
    }

    private fun TestScope.emit(event: EngineEvent) {
        engine.events.tryEmit(event)
        settle()
    }

    @Test
    fun `choosing the PC plays the current track at the extrapolated position, phone silent`() = runTest {
        playback()
        set(state(output = AudioOutput.Phone))
        assertTrue(engine.playCalls().isEmpty())

        repo.now = 3_000
        set(state(output = AudioOutput.Pc, position = 10_000, at = 1_000))
        awaitPlay(1)
        assertEquals(listOf("${a.id}/auto"), audio.forges)
        assertEquals(listOf("play http://phone/audio/${a.id}?t=1 at 12000 x1.0"), engine.playCalls())
    }

    @Test
    fun `the forge asks the quality of the settings`() = runTest {
        settings.value = UserSettings(audioQualityFormat = AudioQualityFormat.High)
        playback()
        set(state())
        assertEquals(listOf("${a.id}/high"), audio.forges)
    }

    @Test
    fun `back to the phone stops the local player`() = runTest {
        playback()
        set(state())
        set(state(output = AudioOutput.Phone))
        assertEquals("stop", engine.calls.last())
    }

    @Test
    fun `pause, resume, seek and speed follow the WS state`() = runTest {
        playback()
        set(state(position = 10_000, at = 1_000))
        emit(EngineEvent.Playing)
        engine.time = 10_000

        set(state(playing = false, position = 10_000, at = 1_000))
        assertEquals("pause", engine.calls.last())

        set(state(playing = true, position = 10_000, at = 1_000))
        assertEquals("resume", engine.calls.last())

        set(state(playing = true, position = 60_000, at = 1_000))
        assertTrue("seek 60000" in engine.calls)

        engine.time = 60_000
        set(state(playing = true, position = 60_000, at = 1_000, speed = 1.5f))
        assertTrue("rate 1.5" in engine.calls)
    }

    @Test
    fun `a drift over 1500 ms is re-aligned on the next tick, a small one is not`() = runTest {
        playback()
        set(state(position = 10_000, at = 1_000))
        emit(EngineEvent.Playing)

        repo.now = 2_000
        engine.time = 10_000 // 1 s behind: tolerated
        advanceTimeBy(1_001)
        assertTrue(engine.calls.none { it.startsWith("seek") })

        repo.now = 5_000
        engine.time = 11_000 // 3 s behind
        advanceTimeBy(1_000)
        assertEquals("seek 14000", engine.calls.last())
    }

    @Test
    fun `a track change stops, then forges the new track`() = runTest {
        playback()
        set(state(track = a))
        set(state(track = b, position = 0))
        assertEquals(listOf("${a.id}/auto", "${b.id}/auto"), audio.forges)
        val last = engine.calls.takeLast(2)
        assertEquals("stop", last[0])
        assertTrue(last[1].startsWith("play http://phone/audio/${b.id}"))
    }

    @Test
    fun `a forge in 404 shows a notice and stops, without retry`() = runTest {
        audio.forgeResults += ForgeResult.Error(404, ApiError("NOT_FOUND"))
        playback()
        set(state())
        assertEquals(listOf<LocalPlaybackNotice>(LocalPlaybackNotice.NotFound), notices)
        assertTrue(engine.playCalls().isEmpty())
        set(state(position = 20_000))
        assertEquals(1, audio.forges.size)
    }

    @Test
    fun `an expired URL is forged again and resumed at the extrapolated position`() = runTest {
        playback()
        set(state(position = 10_000, at = 1_000))
        emit(EngineEvent.Playing)
        audio.probes += AudioProbe.Expired
        repo.now = 6_000
        emit(EngineEvent.Error)
        assertEquals(1, audio.probed)
        assertEquals(2, audio.forges.size)
        assertEquals("play http://phone/audio/${a.id}?t=2 at 15000 x1.0", engine.playCalls().last())
        assertTrue(notices.isEmpty())
    }

    @Test
    fun `a failed re-forge after expiry shows a notice`() = runTest {
        playback()
        set(state())
        audio.probes += AudioProbe.Expired
        audio.forgeResults += ForgeResult.Unreachable
        emit(EngineEvent.Error)
        assertEquals(listOf<LocalPlaybackNotice>(LocalPlaybackNotice.Unreachable), notices)
    }

    @Test
    fun `an invalid URL is forged again once, then fails`() = runTest {
        playback()
        set(state())
        audio.probes += AudioProbe.Invalid
        emit(EngineEvent.Error)
        assertEquals(2, audio.forges.size)
        audio.probes += AudioProbe.Invalid
        emit(EngineEvent.Error)
        assertEquals(2, audio.forges.size)
        assertEquals(listOf<LocalPlaybackNotice>(LocalPlaybackNotice.InvalidUrl), notices)
    }

    @Test
    fun `an upstream failure shows a notice, without loop`() = runTest {
        val playback = playback()
        set(state())
        audio.probes += AudioProbe.UpstreamFailed
        emit(EngineEvent.Error)
        assertEquals(1, audio.forges.size)
        assertEquals(listOf<LocalPlaybackNotice>(LocalPlaybackNotice.UpstreamFailed), notices)
        assertNull(playback.playingTrackId)
    }

    @Test
    fun `a kick stops the player, and an audio 401 within the window keeps the pairing`() = runTest {
        playback()
        set(state())
        emit(EngineEvent.Playing)
        repo.kickWindow = true
        audio.probes += AudioProbe.Revoked
        emit(EngineEvent.Error)
        assertEquals(0, revoked)

        set(state(position = 30_000))
        repo.connection.value = ConnectionState.Kicked
        runCurrent()
        assertEquals("stop", engine.calls.last())
        assertEquals(0, revoked)
    }

    @Test
    fun `an audio 401 outside the kick window revokes at once`() = runTest {
        playback()
        set(state())
        audio.probes += AudioProbe.Revoked
        emit(EngineEvent.Error)
        assertEquals(1, revoked)
        assertEquals("stop", engine.calls.last())
    }

    @Test
    fun `a revoked forge is confirmed once, 2 s later, then revokes`() = runTest {
        audio.forgeResults += ForgeResult.Error(401, ApiError("DEVICE_REVOKED"))
        audio.forgeResults += ForgeResult.Error(401, ApiError("DEVICE_REVOKED"))
        playback()
        set(state())
        assertEquals(1, audio.forges.size)
        advanceTimeBy(2_001)
        assertEquals(2, audio.forges.size)
        assertEquals(1, revoked)
        assertTrue(engine.playCalls().isEmpty())
    }

    @Test
    fun `a complete cache entry is played without any audio request`() = runTest {
        playback()
        val file = dir.resolve("song.webm")
        Files.write(file, ByteArray(100) { 1 })
        cache.fill(a.id) { target -> Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING); DownloadResult.Done(100) }

        set(state())
        assertTrue(audio.forges.isEmpty())
        assertTrue(engine.sources.single() is AudioSource.File)
    }

    @Test
    fun `an unreadable cache entry is dropped, then played from the phone`() = runTest {
        playback()
        cache.fill(a.id) { target -> Files.write(target, ByteArray(10)); DownloadResult.Done(10) }
        set(state())
        assertTrue(engine.sources.single() is AudioSource.File)

        emit(EngineEvent.Error)
        assertEquals(1, audio.forges.size)
        assertTrue(engine.sources.last() is AudioSource.Stream)
        assertTrue(!cache.contains(a.id))
    }

    @Test
    fun `the stream fills the cache in parallel, and the next play needs no request`() = runTest {
        audio.download = { target -> Files.write(target, ByteArray(64) { 2 }); DownloadResult.Done(64) }
        playback()
        set(state())
        assertEquals(1, audio.downloads)
        set(state(output = AudioOutput.Phone))
        set(state())
        assertEquals(1, audio.forges.size)
        assertTrue(engine.sources.last() is AudioSource.File)
    }

    @Test
    fun `a disabled cache downloads nothing`() = runTest {
        settings.value = UserSettings(exoPlayerDiskCacheMaxSize = ExoPlayerDiskCacheMaxSize.Disabled)
        audio.download = { target -> Files.write(target, ByteArray(64)); DownloadResult.Done(64) }
        playback()
        set(state())
        assertEquals(0, audio.downloads)
    }

    @Test
    fun `a 1_1 phone without audio_output never plays locally`() = runTest {
        val old = FakeRepository(features = setOf("playback", "queue", "audio"))
        playback(repository = old)
        old.state.value = state()
        runCurrent()
        assertTrue(audio.forges.isEmpty())
        assertTrue(engine.playCalls().isEmpty())
    }

    @Test
    fun `without libvlc nothing is played and nothing breaks`() = runTest {
        playback(engine = null)
        set(state())
        assertTrue(audio.forges.isEmpty())
    }

    @Test
    fun `a paused phone loads nothing until it plays`() = runTest {
        playback()
        set(state(playing = false))
        assertTrue(audio.forges.isEmpty())
        set(state(playing = true))
        assertEquals(1, audio.forges.size)
    }

    @Test
    fun `a session that is not Live stops the player`() = runTest {
        playback()
        set(state())
        repo.connection.value = ConnectionState.Reconnecting(1_000)
        runCurrent()
        assertEquals("stop", engine.calls.last())
    }

    @Test
    fun `the playback volume is local and applied to the engine`() = runTest {
        playback()
        settings.value = UserSettings(playbackVolume = 0.4f)
        runCurrent()
        assertEquals("volume 40", engine.calls.last())
    }

    @Test
    fun `close stops the tick and the engine, and later state changes are ignored`() = runTest {
        val playback = playback()
        set(state(position = 10_000, at = 1_000))
        emit(EngineEvent.Playing)
        engine.time = 10_000

        playback.close()

        // The tick is stopped: a large drift would seek on the next tick if it were still alive.
        repo.now = 50_000
        engine.time = 50_000
        set(state(position = 40_000, at = 50_000))
        advanceTimeBy(3_000)
        runCurrent()

        assertEquals("stop", engine.calls.last(), "no input may be processed after close")
    }

    @Test
    fun `an in-flight revocation survives close, fire-and-forget by design`() = runTest {
        audio.forgeResults += ForgeResult.Error(401, ApiError("DEVICE_REVOKED"))
        audio.forgeResults += ForgeResult.Error(401, ApiError("DEVICE_REVOKED"))
        val playback = playback()
        set(state())
        // The first 401 is being confirmed: the 2 s wait is in flight.
        playback.close()

        advanceTimeBy(2_001)
        runCurrent()

        assertEquals(1, revoked, "the untracked forge/revocation job must survive close")
    }
}
