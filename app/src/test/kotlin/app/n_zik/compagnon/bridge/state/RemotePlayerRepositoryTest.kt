package app.n_zik.compagnon.bridge.state

import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.registerSkikoComposeImplementation
import app.n_zik.compagnon.bridge.ConnectionState
import app.n_zik.compagnon.bridge.StateChannel
import app.n_zik.compagnon.bridge.library.ArtworkLoader
import app.n_zik.compagnon.bridge.pairing.BridgeJson
import app.n_zik.compagnon.bridge.pairing.RevocationPolicy
import app.n_zik.compagnon.core.network.ArtworkKey
import app.n_zik.compagnon.core.network.BridgeClient
import app.n_zik.compagnon.core.network.ProbeResult
import app.n_zik.compagnon.core.network.ServerAddress
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import java.io.IOException
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class RemotePlayerRepositoryTest {

    /** Headless: the skiko graphics backend registers itself only when a window opens. */
    @OptIn(InternalComposeUiApi::class)
    @BeforeEach
    fun registerSkiko() = registerSkikoComposeImplementation()

    private val token = "T".repeat(43)
    private val address = ServerAddress("192.168.1.14", 42420)
    private val requests = mutableListOf<HttpRequestData>()
    private val bodies = mutableListOf<String>()
    private val notices = mutableListOf<PlayerNotice>()
    private var revoked = 0

    private class FakeChannel : StateChannel {
        override val connection = MutableStateFlow<ConnectionState>(ConnectionState.Live)
        override val clock = ServerClock()
        var onMessage: suspend (ServerMessage) -> Unit = {}
        var snapshotRequests = 0
        var started = false
        var closes = 0
        val closed get() = closes > 0
        var reconnects = 0
        override fun start(onMessage: suspend (ServerMessage) -> Unit) {
            started = true
            this.onMessage = onMessage
        }
        override fun requestSnapshot() {
            snapshotRequests++
        }
        override fun reconnect() {
            reconnects++
        }
        override fun close() {
            closes++
        }
        override var lastKickAtMs: Long? = null
    }

    private val channel = FakeChannel()

    private fun MockRequestHandleScope.json(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
        respond(body, status, headersOf(HttpHeaders.ContentType, "application/json; charset=utf-8"))

    private fun TestScope.repository(
        features: Set<String> = setOf("playback", "queue", "artwork"),
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): RemotePlayerRepository {
        val engine = MockEngine.create {
            dispatcher = StandardTestDispatcher(testScheduler)
            addHandler { request ->
                requests += request
                bodies += String(request.body.toByteArray(), Charsets.UTF_8)
                handler(request)
            }
        }
        var ids = 0
        val api = BridgeClient(engine)
        val repository = RemotePlayerRepository(
            channel = channel,
            api = api,
            address = address,
            deviceToken = token,
            features = features,
            revocation = RevocationPolicy(onRevoked = { revoked++ }),
            scope = backgroundScope,
            artworkLoader = ArtworkLoader(api, address, token, decode = { ImageBitmap(1, 1) }, scope = backgroundScope),
            newCommandId = { "cmd-${++ids}" },
        )
        repository.start()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { repository.notices.collect { notices += it } }
        return repository
    }

    private val a = Track("dQw4w9WgXcQ", "A", durationMs = 212_000, hasArtwork = true)
    private val b = Track("local:42", "B", source = TrackSource.Local)

    private suspend fun deliver(message: ServerMessage) = channel.onMessage(message)

    private fun snapshot(revision: Long) = SnapshotMessage(revision, 1_000, listOf(a, b), 0, a.id, isPlaying = true, positionMs = 83_000)

    private fun applied(revision: Long, changed: Boolean = true) = """{"applied":true,"changed":$changed,"revision":$revision}"""

    @Test
    fun `libraryChanged emits the invalidated family and moves no player state`() = runTest {
        val repository = repository { json(applied(1)) }
        val kinds = mutableListOf<String>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { repository.libraryChanged.collect { kinds += it } }
        deliver(snapshot(1))
        runCurrent()
        deliver(LibraryChangedMessage(2, 2_000, kind = "songs"))
        deliver(LibraryChangedMessage(3, 3_000, kind = "albums"))
        runCurrent()
        assertEquals(listOf("songs", "albums"), kinds)
        assertEquals(2, repository.state.value!!.queue.size, "the delta moves no player state")
        assertEquals(3, repository.lastRevision)
    }

    @Test
    fun `a rejected libraryChanged emits nothing, the applied one after it does`() = runTest {
        val repository = repository { json(applied(1)) }
        val kinds = mutableListOf<String>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { repository.libraryChanged.collect { kinds += it } }
        deliver(snapshot(3))
        runCurrent()
        // A duplicated delta (revision ≤ last): rejected by the revision rules — no reload
        deliver(LibraryChangedMessage(3, 3_000, kind = "songs"))
        runCurrent()
        assertEquals(emptyList<String>(), kinds, "a rejected delta moved no state")
        // The next in-order delta applies: its family reloads
        deliver(LibraryChangedMessage(4, 4_000, kind = "albums"))
        runCurrent()
        assertEquals(listOf("albums"), kinds)
    }

    @Test
    fun `state comes only from the WS, revision rules applied`() = runTest {
        val repository = repository { json(applied(1)) }
        assertTrue(channel.started)
        assertNull(repository.state.value)
        deliver(snapshot(57))
        assertEquals(a, repository.state.value!!.currentTrack)
        deliver(PlaybackChangedMessage(58, 2_000, isPlaying = false, speed = 1f, positionMs = 84_000))
        assertEquals(false, repository.state.value!!.isPlaying)
        assertEquals(58L, repository.lastRevision)
        deliver(PlaybackChangedMessage(60, 3_000, isPlaying = true, speed = 1f, positionMs = 1))
        assertEquals(1, channel.snapshotRequests)
        assertEquals(false, repository.state.value!!.isPlaying)
    }

    @Test
    fun `play sends the Bearer and a commandId, and the state still comes from the delta`() = runTest {
        val repository = repository { json(applied(59)) }
        deliver(snapshot(58))
        val job = launch { repository.pause() }
        advanceTimeBy(1_000)
        runCurrent()
        val request = requests.single()
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("http://192.168.1.14:42420/api/v1/player/pause", request.url.toString())
        assertEquals("Bearer $token", request.headers[HttpHeaders.Authorization])
        assertEquals("""{"commandId":"cmd-1"}""", bodies.single())
        assertEquals(true, repository.state.value!!.isPlaying, "no optimistic UI")

        deliver(PlaybackChangedMessage(59, 2_000, isPlaying = false, speed = 1f, positionMs = 84_000))
        advanceUntilIdle()
        assertTrue(job.isCompleted)
        assertEquals(false, repository.state.value!!.isPlaying)
        assertEquals(0, channel.snapshotRequests)
        assertTrue(notices.isEmpty())
    }

    @Test
    fun `changed without a delta within 3 s shows a failure and requests a snapshot`() = runTest {
        val repository = repository { json(applied(59)) }
        deliver(snapshot(58))
        launch { repository.next() }
        advanceTimeBy(2_900)
        runCurrent()
        assertTrue(notices.isEmpty())
        advanceTimeBy(200)
        runCurrent()
        assertEquals(listOf<PlayerNotice>(PlayerNotice.NoDelta(CommandKind.Next)), notices)
        assertEquals(1, channel.snapshotRequests)
    }

    @Test
    fun `a revision reached by a snapshot also satisfies the wait`() = runTest {
        val repository = repository { json(applied(59)) }
        deliver(snapshot(58))
        val job = launch { repository.play() }
        advanceTimeBy(500)
        runCurrent()
        deliver(snapshot(60))
        advanceUntilIdle()
        assertTrue(job.isCompleted)
        assertTrue(notices.isEmpty())
    }

    @Test
    fun `changed false waits for nothing`() = runTest {
        val repository = repository { json(applied(58, changed = false)) }
        deliver(snapshot(58))
        repository.play()
        assertTrue(notices.isEmpty())
        assertEquals(0, channel.snapshotRequests)
    }

    @Test
    fun `every command has its route and body`() = runTest {
        val repository = repository { json(applied(1, changed = false)) }
        repository.play()
        repository.previous()
        repository.seek(30_000)
        repository.setSpeed(1.5f)
        repository.setSpeed(10f)
        repository.setRepeat(RepeatMode.All)
        repository.setShuffle(true)
        repository.jump(1, b.id)
        repository.remove(1, b.id)
        repository.move(1, 0, b.id)
        repository.clearQueue()
        assertEquals(
            listOf(
                "player/play", "player/previous", "player/seek", "player/speed", "player/speed", "player/repeat",
                "player/shuffle", "queue/jump", "queue/remove", "queue/move", "queue/clear",
            ).map { "http://192.168.1.14:42420/api/v1/$it" },
            requests.map { it.url.toString() },
        )
        assertEquals(
            listOf(
                """{"commandId":"cmd-1"}""",
                """{"commandId":"cmd-2"}""",
                """{"positionMs":30000,"commandId":"cmd-3"}""",
                """{"speed":1.5,"commandId":"cmd-4"}""",
                """{"speed":4.0,"commandId":"cmd-5"}""",
                """{"mode":"all","commandId":"cmd-6"}""",
                """{"enabled":true,"commandId":"cmd-7"}""",
                """{"index":1,"trackId":"local:42","commandId":"cmd-8"}""",
                """{"index":1,"trackId":"local:42","commandId":"cmd-9"}""",
                """{"fromIndex":1,"toIndex":0,"trackId":"local:42","commandId":"cmd-10"}""",
                """{"commandId":"cmd-11"}""",
            ),
            bodies,
        )
        assertTrue(requests.all { it.headers[HttpHeaders.Authorization] == "Bearer $token" })
    }

    @Test
    fun `queue play and queue add bodies`() = runTest {
        val repository = repository { json(applied(1, changed = false)) }
        repository.playTracks(listOf("t1", "t2", "local:3"), 2)
        repository.addTracks(listOf("t1"), QueuePosition.Next)
        repository.addTracks(listOf("t1", "t2"), QueuePosition.End)
        repository.playTracks(emptyList(), 0)
        repository.addTracks(emptyList(), QueuePosition.End)
        assertEquals(
            listOf("queue/play", "queue/add", "queue/add").map { "http://192.168.1.14:42420/api/v1/$it" },
            requests.map { it.url.toString() },
        )
        assertEquals(
            listOf(
                """{"trackIds":["t1","t2","local:3"],"startIndex":2,"positionMs":0,"commandId":"cmd-1"}""",
                """{"trackIds":["t1"],"position":"next","commandId":"cmd-2"}""",
                """{"trackIds":["t1","t2"],"position":"end","commandId":"cmd-3"}""",
            ),
            bodies,
        )
        assertTrue(notices.isEmpty())
    }

    @Test
    fun `more than 500 tracks are truncated to 500 with a notice`() = runTest {
        val repository = repository { json(applied(1, changed = false)) }
        val ids = List(730) { "id$it" }
        repository.playTracks(ids, 0)
        repository.addTracks(ids, QueuePosition.End)
        val play = BridgeJson.parseToJsonElement(bodies[0]).jsonObject
        assertEquals(ids.take(500), play["trackIds"]!!.jsonArray.map { it.jsonPrimitive.content })
        assertEquals(0, play["startIndex"]!!.jsonPrimitive.content.toInt())
        val add = BridgeJson.parseToJsonElement(bodies[1]).jsonObject
        assertEquals(ids.take(500), add["trackIds"]!!.jsonArray.map { it.jsonPrimitive.content })
        assertEquals(
            listOf<PlayerNotice>(
                PlayerNotice.Truncated(CommandKind.QueuePlay, 500, 730),
                PlayerNotice.Truncated(CommandKind.QueueAdd, 500, 730),
            ),
            notices,
        )
    }

    @Test
    fun `the notice tells the real size of a collection read in part`() = runTest {
        val repository = repository { json(applied(1, changed = false)) }
        val ids = List(501) { "id$it" }
        repository.playTracks(ids, 0, total = 730)
        assertEquals(listOf<PlayerNotice>(PlayerNotice.Truncated(CommandKind.QueuePlay, 500, 730)), notices)
    }

    @Test
    fun `a start index past 500 keeps its track inside the window`() = runTest {
        val repository = repository { json(applied(1, changed = false)) }
        val ids = List(900) { "id$it" }
        repository.playTracks(ids, 649)
        val play = BridgeJson.parseToJsonElement(bodies.single()).jsonObject
        val sent = play["trackIds"]!!.jsonArray.map { it.jsonPrimitive.content }
        val start = play["startIndex"]!!.jsonPrimitive.content.toInt()
        assertEquals(500, sent.size)
        assertEquals("id649", sent[start])
    }

    @Test
    fun `queue play 404 is a notice`() = runTest {
        val repository = repository { json("""{"code":"NOT_FOUND","message":"x"}""", HttpStatusCode.NotFound) }
        repository.playTracks(listOf("gone"), 0)
        repository.addTracks(listOf("gone"), QueuePosition.Next)
        assertEquals(
            listOf<PlayerNotice>(PlayerNotice.NotFound(CommandKind.QueuePlay), PlayerNotice.NotFound(CommandKind.QueueAdd)),
            notices,
        )
    }

    @Test
    fun `queue play waits for the delta like every command`() = runTest {
        val repository = repository { json(applied(59)) }
        deliver(snapshot(58))
        launch { repository.playTracks(listOf("t1"), 0) }
        advanceTimeBy(3_100)
        runCurrent()
        assertEquals(listOf<PlayerNotice>(PlayerNotice.NoDelta(CommandKind.QueuePlay)), notices)
        assertEquals(1, channel.snapshotRequests)
    }

    @Test
    fun `QUEUE_MISMATCH requests a snapshot and tells the user`() = runTest {
        val repository = repository { json("""{"code":"QUEUE_MISMATCH","message":"x","revision":61}""", HttpStatusCode.Conflict) }
        deliver(snapshot(58))
        repository.remove(1, b.id)
        assertEquals(1, channel.snapshotRequests)
        assertEquals(listOf<PlayerNotice>(PlayerNotice.QueueMismatch(CommandKind.Remove)), notices)
        assertEquals(listOf(a, b), repository.state.value!!.queue, "nothing changed locally")
    }

    @Test
    fun `errors are decided from the code`() = runTest {
        var answer = """{"code":"PLAYER_REJECTED","message":"QUEUE_MISMATCH"}""" to HttpStatusCode.UnprocessableEntity
        val repository = repository { json(answer.first, answer.second) }
        repository.move(0, 1, a.id)
        answer = """{"code":"PLAYER_UNAVAILABLE","message":"x"}""" to HttpStatusCode.ServiceUnavailable
        repository.play()
        answer = """{"code":"SERVER_STOPPING","message":"x"}""" to HttpStatusCode.ServiceUnavailable
        repository.play()
        answer = """{"code":"CONFLICT_ACTIVE_CLIENT","message":"x","activeDevice":{"deviceId":"d","deviceName":"PC-BUREAU"}}""" to HttpStatusCode.Conflict
        repository.next()
        answer = """{"code":"BAD_REQUEST","message":"x"}""" to HttpStatusCode.BadRequest
        repository.seek(1)
        assertEquals(
            listOf(
                PlayerNotice.Rejected(CommandKind.Move),
                PlayerNotice.Unavailable(CommandKind.Play),
                PlayerNotice.ServerStopping(CommandKind.Play),
                PlayerNotice.OtherActive(CommandKind.Next, "PC-BUREAU"),
                PlayerNotice.Failed(CommandKind.Seek, 400, "BAD_REQUEST"),
            ),
            notices,
        )
        assertEquals(0, channel.snapshotRequests)
    }

    @Test
    fun `the audio output is a command, a 422 becomes a notice`() = runTest {
        val repository = repository { json("""{"code":"PLAYER_REJECTED","message":"no session"}""", HttpStatusCode.UnprocessableEntity) }
        repository.setAudioOutput(AudioOutput.Pc)
        val request = requests.single()
        assertEquals("http://192.168.1.14:42420/api/v1/player/output", request.url.toString())
        assertEquals("""{"output":"pc","commandId":"cmd-1"}""", bodies.single())
        assertEquals(listOf<PlayerNotice>(PlayerNotice.Rejected(CommandKind.Output)), notices)
    }

    @Test
    fun `the kick window holds while kicked and 2 s after the 4001 close`() = runTest {
        val repository = repository { json(applied(1)) }
        assertTrue(!repository.inKickWindow())
        channel.lastKickAtMs = channel.clock.nowMonotonicMs()
        assertTrue(repository.inKickWindow())
        channel.lastKickAtMs = channel.clock.nowMonotonicMs() - 2_001
        assertTrue(!repository.inKickWindow())
        channel.connection.value = ConnectionState.Kicked
        assertTrue(repository.inKickWindow())
    }

    @Test
    fun `unreachable phone is reported`() = runTest {
        val repository = repository { throw IOException("refused") }
        repository.play()
        assertEquals(listOf<PlayerNotice>(PlayerNotice.Unreachable(CommandKind.Play)), notices)
    }

    @Test
    fun `a late WS error names its command`() = runTest {
        val repository = repository { json(applied(59)) }
        deliver(snapshot(58))
        launch { repository.jump(1, b.id) }
        runCurrent()
        deliver(PlaybackChangedMessage(59, 2_000, isPlaying = true, speed = 1f, positionMs = 0))
        advanceUntilIdle()
        deliver(ErrorMessage("PLAYER_REJECTED", "cannot load", "cmd-1"))
        deliver(ErrorMessage("PLAYER_REJECTED", "cannot load", null))
        assertEquals(
            listOf(
                PlayerNotice.LateError(CommandKind.Jump, "PLAYER_REJECTED", "cmd-1"),
                PlayerNotice.LateError(null, "PLAYER_REJECTED", null),
            ),
            notices,
        )
        assertEquals(59L, repository.lastRevision, "an error never touches the revision")
    }

    @Test
    fun `REST 401 DEVICE_REVOKED twice revokes, once does not`() = runTest {
        var answers = ArrayDeque(listOf(401, 200))
        val repository = repository {
            if (answers.removeFirst() == 401) json("""{"code":"DEVICE_REVOKED","message":"x"}""", HttpStatusCode.Unauthorized)
            else json(applied(1, changed = false))
        }
        repository.play()
        assertEquals(0, revoked)
        assertEquals(2, requests.size)
        assertTrue(notices.isEmpty())

        answers = ArrayDeque(listOf(401, 401))
        repository.play()
        assertEquals(1, revoked)
        assertTrue(notices.isEmpty())
    }

    @Test
    fun `reconnect and close go to the channel`() = runTest {
        val repository = repository { json(applied(1)) }
        repository.reconnect()
        repository.close()
        assertEquals(1, channel.reconnects)
        assertTrue(channel.closed)
    }

    @Test
    fun `close is idempotent and clears the artwork cache`() = runTest {
        val repository = repository { respond(byteArrayOf(1, 2, 3), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "image/jpeg")) }
        repository.artwork(ArtworkKey.track(a.id))
        assertTrue(repository.cachedArtwork(ArtworkKey.track(a.id)) != null)
        repository.close()
        repository.close()
        assertEquals(1, channel.closes)
        assertNull(repository.cachedArtwork(ArtworkKey.track(a.id)))
    }

    @Test
    fun `a lost requestSnapshot is sent again every 3 s while the snapshot is awaited`() = runTest {
        val repository = repository { json(applied(1)) }
        deliver(snapshot(58))
        deliver(PlaybackChangedMessage(60, 3_000, isPlaying = false, speed = 1f, positionMs = 1))
        assertEquals(1, channel.snapshotRequests)
        advanceTimeBy(2_900)
        runCurrent()
        assertEquals(1, channel.snapshotRequests)
        advanceTimeBy(200)
        runCurrent()
        assertEquals(2, channel.snapshotRequests)
        advanceTimeBy(3_000)
        runCurrent()
        assertEquals(3, channel.snapshotRequests)

        deliver(snapshot(61))
        advanceTimeBy(10_000)
        runCurrent()
        assertEquals(3, channel.snapshotRequests)
        assertEquals(61L, repository.lastRevision)
    }

    @Test
    fun `upgrade 401 is confirmed by exactly one probe 2 s later`() = runTest {
        var revokedCount = 0
        val policy = RevocationPolicy(onRevoked = { revokedCount++ })
        var probes = 0
        var answer: ProbeResult = ProbeResult.Revoked
        val hooks = RemotePlayerRepository.sessionRevocation(policy) { probes++; answer }

        val confirmed = async { hooks.confirmUpgradeRevoked() }
        advanceTimeBy(1_900)
        runCurrent()
        assertEquals(0, probes, "the upgrade answer is the first 401: no probe before 2 s")
        advanceUntilIdle()
        assertTrue(confirmed.await())
        assertEquals(1, probes)
        assertEquals(1, revokedCount)

        answer = ProbeResult.Ok
        val notConfirmed = async { hooks.confirmUpgradeRevoked() }
        advanceUntilIdle()
        assertEquals(false, notConfirmed.await())
        assertEquals(2, probes)
        assertEquals(1, revokedCount)

        hooks.revokeNow()
        assertEquals(2, revokedCount)
        assertEquals(2, probes)
    }

    @Test
    fun `without the artwork feature no artwork is requested`() = runTest {
        val repository = repository(features = setOf("playback", "queue")) { json(applied(1)) }
        assertNull(repository.artwork(ArtworkKey.track(a.id)))
        assertTrue(requests.isEmpty())
    }
}
