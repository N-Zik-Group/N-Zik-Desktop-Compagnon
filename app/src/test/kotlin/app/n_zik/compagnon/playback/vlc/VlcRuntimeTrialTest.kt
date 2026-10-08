package app.n_zik.compagnon.playback.vlc

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.header
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import java.io.File
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.log.LogLevel
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter

/**
 * Packaging trial of story 12, opt-in (`gradlew test -PvlcTrial=<media dir>`): loads the embedded
 * runtime only (no system VLC), then plays every sample of the media directory from a file and over HTTP
 * with `Range`, at a start position, with a seek. Prints the libvlc modules used (debug log), which froze
 * the plugin list of `app/build.gradle.kts`. Windows host only (the `-PvlcTrial` guard in
 * `app/build.gradle.kts` fails the build on other hosts): it validates the embedded path only.
 */
@EnabledIfSystemProperty(named = "vlc.trial.media", matches = ".+")
class VlcRuntimeTrialTest {

    @Test
    fun `the embedded runtime plays every sample from a file and over HTTP`() {
        val availability = VlcRuntime.availability
        assertTrue(availability is VlcRuntime.Availability.Available) { "runtime: $availability" }
        val samples = File(System.getProperty("vlc.trial.media")).listFiles().orEmpty().filter { it.isFile }.sortedBy { it.name }
        assertTrue(samples.isNotEmpty())

        val server = embeddedServer(CIO, port = 0) {
            routing {
                get("/audio/{name}") {
                    val file = samples.first { it.name == call.parameters["name"] }
                    val bytes = file.readBytes()
                    call.response.header(HttpHeaders.AcceptRanges, "bytes")
                    val range = call.request.headers[HttpHeaders.Range]?.removePrefix("bytes=")?.split('-')
                    if (range == null) {
                        call.respondBytes(bytes, ContentType.Application.OctetStream)
                    } else {
                        val start = range[0].toInt()
                        val end = (range.getOrNull(1)?.takeIf { it.isNotBlank() }?.toInt() ?: bytes.lastIndex).coerceAtMost(bytes.lastIndex)
                        if (start > end) {
                            call.response.header(HttpHeaders.ContentRange, "bytes */${bytes.size}")
                            call.respondBytes(ByteArray(0), status = HttpStatusCode.RequestedRangeNotSatisfiable)
                            return@get
                        }
                        call.response.header(HttpHeaders.ContentRange, "bytes $start-$end/${bytes.size}")
                        call.respondBytes(bytes.copyOfRange(start, end + 1), ContentType.Application.OctetStream, HttpStatusCode.PartialContent)
                    }
                }
            }
        }.start(wait = false)
        val port = runBlocking { server.engine.resolvedConnectors().first().port }

        val modules = Collections.synchronizedSortedSet(sortedSetOf<String>())
        val factory = MediaPlayerFactory("--no-video", "--intf=dummy", "--no-metadata-network-access", "--network-caching=300")
        val log = factory.application().newLog()
        log.setLevel(LogLevel.DEBUG)
        log.addLogListener { level, module, _, _, _, _, _, message ->
            if (level == LogLevel.ERROR || level == LogLevel.WARNING) println("TRIAL vlc $level $module: $message")
            Regex("""using (.+) module "([^"]+)"""").find(message)?.let { modules += "${it.groupValues[1]}: ${it.groupValues[2]}" }
        }
        val failures = mutableListOf<String>()
        try {
            for (sample in samples) {
                for (mrl in listOf(sample.toPath().toUri().toString(), "http://127.0.0.1:$port/audio/${sample.name}")) {
                    val outcome = playOnce(factory, mrl)
                    println("TRIAL ${sample.name} ${if (mrl.startsWith("http")) "http" else "file"}: $outcome")
                    if (!outcome.startsWith("OK")) failures += "${sample.name} $mrl: $outcome"
                }
            }
        } finally {
            log.release()
            factory.release()
            server.stop(0, 0)
        }
        println("TRIAL modules used:")
        modules.forEach { println("TRIAL   $it") }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    /** Polls the player time for up to 4 s until [ok]; the last value read. */
    private fun awaitTime(player: MediaPlayer, ok: (Long) -> Boolean): Long {
        val deadline = System.currentTimeMillis() + 4_000
        var time = player.status().time()
        while (!ok(time) && System.currentTimeMillis() < deadline) {
            Thread.sleep(100)
            time = player.status().time()
        }
        return time
    }

    /** Starts at 1 s, seeks to 3 s, and checks the time keeps moving. */
    private fun playOnce(factory: MediaPlayerFactory, mrl: String): String {
        val player = factory.mediaPlayers().newMediaPlayer()
        val playing = CountDownLatch(1)
        var error = false
        player.events().addMediaPlayerEventListener(object : MediaPlayerEventAdapter() {
            override fun playing(mediaPlayer: MediaPlayer) = playing.countDown()
            override fun error(mediaPlayer: MediaPlayer) {
                error = true
                playing.countDown()
            }
        })
        try {
            player.media().play(mrl, ":start-time=1.0")
            if (!playing.await(10, TimeUnit.SECONDS)) return "TIMEOUT"
            if (error) return "ERROR"
            val first = awaitTime(player) { it >= 900 }
            player.controls().setTime(3_000)
            val afterSeek = awaitTime(player) { it >= 3_000 }
            return if (first >= 900 && afterSeek >= 3_000) "OK start=$first afterSeek=$afterSeek" else "BAD start=$first afterSeek=$afterSeek"
        } finally {
            player.controls().stop()
            player.release()
        }
    }
}
