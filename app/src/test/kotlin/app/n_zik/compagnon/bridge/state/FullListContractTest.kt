package app.n_zik.compagnon.bridge.state

import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.registerSkikoComposeImplementation
import app.n_zik.compagnon.bridge.ConnectionState
import app.n_zik.compagnon.bridge.StateChannel
import app.n_zik.compagnon.bridge.library.ArtworkLoader
import app.n_zik.compagnon.bridge.pairing.BridgeJson
import app.n_zik.compagnon.bridge.pairing.RevocationPolicy
import app.n_zik.compagnon.components.ui.screens.bridge.phoneToastType
import app.n_zik.compagnon.core.network.BridgeClient
import app.n_zik.compagnon.core.network.ServerAddress
import app.n_zik.compagnon.utils.Toaster
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** Contract 1.10.0 on the PC: `queue/list`, the `toast` message, `ui.settings`, `library.locate`. */
class FullListContractTest {

    @OptIn(InternalComposeUiApi::class)
    @BeforeEach
    fun registerSkiko() = registerSkikoComposeImplementation()

    private val token = "T".repeat(43)
    private val address = ServerAddress("192.168.1.14", 42420)
    private val requests = mutableListOf<HttpRequestData>()
    private val bodies = mutableListOf<String>()
    private val notices = mutableListOf<PlayerNotice>()

    private class FakeChannel : StateChannel {
        override val connection = MutableStateFlow<ConnectionState>(ConnectionState.Live)
        override val clock = ServerClock()
        var onMessage: suspend (ServerMessage) -> Unit = {}
        override fun start(onMessage: suspend (ServerMessage) -> Unit) {
            this.onMessage = onMessage
        }
        override fun requestSnapshot() = Unit
        override fun reconnect() = Unit
        override fun close() = Unit
        override var lastKickAtMs: Long? = null
    }

    private val channel = FakeChannel()

    private fun TestScope.repository(features: Set<String>): RemotePlayerRepository {
        val engine = MockEngine.create {
            dispatcher = StandardTestDispatcher(testScheduler)
            addHandler { request ->
                requests += request
                bodies += String(request.body.toByteArray(), Charsets.UTF_8)
                respond(
                    """{"applied":true,"changed":false,"revision":7}""",
                    HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, "application/json; charset=utf-8"),
                )
            }
        }
        val api = BridgeClient(engine)
        val repository = RemotePlayerRepository(
            channel = channel,
            api = api,
            address = address,
            deviceToken = token,
            features = features,
            revocation = RevocationPolicy(onRevoked = {}),
            scope = backgroundScope,
            artworkLoader = ArtworkLoader(api, address, token, decode = { ImageBitmap(1, 1) }, scope = backgroundScope),
            newCommandId = { "cmd-1" },
        )
        repository.start()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { repository.notices.collect { notices += it } }
        return repository
    }

    @Test
    fun `playList posts the list reference and the action to queue list`() = runTest {
        val repository = repository(setOf("queue", "queue.fullList"))
        repository.playList(ListRef("songs", filter = "liked", sort = "title"), ListAction.Play, 1500, "t1500")
        assertEquals("http://192.168.1.14:42420/api/v1/queue/list", requests.single().url.toString())
        assertEquals(
            """{"list":{"kind":"songs","id":null,"filter":"liked","sort":"title","reverse":false,"period":null,"text":null},"action":"play","startIndex":1500,"startTrackId":"t1500","commandId":"cmd-1"}""",
            bodies.single(),
        )
        // `changed: false`: nothing is awaited, no failure notice
        assertTrue(notices.isEmpty())
    }

    @Test
    fun `a toast frame decodes and becomes a phone toast notice with the feature`() = runTest {
        val frame = """{"type":"toast","key":"songs_shuffled","args":["12"],"toastType":"success","message":"12 songs shuffled"}"""
        val message = ServerMessages.decode(frame)
        assertEquals(ToastMessage("songs_shuffled", listOf("12"), "success", "12 songs shuffled"), message)
        repository(setOf("queue", "ui.toasts"))
        channel.onMessage(message!!)
        runCurrent()
        assertEquals(listOf<PlayerNotice>(PlayerNotice.PhoneToast("songs_shuffled", listOf("12"), "success", "12 songs shuffled")), notices)
    }

    @Test
    fun `a toast never moves the revision and is ignored without the feature`() = runTest {
        val repository = repository(setOf("queue"))
        channel.onMessage(SnapshotMessage(5, 1_000, emptyList(), -1, null, isPlaying = false, positionMs = 0))
        channel.onMessage(ToastMessage("no_song_to_shuffle", emptyList(), "info", "No song to shuffle"))
        runCurrent()
        assertTrue(notices.isEmpty())
        assertEquals(5L, repository.lastRevision)
    }

    @Test
    fun `phone toast types map to the PC toaster, unknown is normal`() {
        assertEquals(Toaster.Type.SUCCESS, phoneToastType("success"))
        assertEquals(Toaster.Type.INFO, phoneToastType("info"))
        assertEquals(Toaster.Type.WARNING, phoneToastType("warning"))
        assertEquals(Toaster.Type.ERROR, phoneToastType("error"))
        assertEquals(Toaster.Type.NORMAL, phoneToastType("whatever"))
    }

    @Test
    fun `toasts rise 109 dp only with the floating navigation bar`() {
        assertEquals(109, Toaster.toastBottomPaddingDp(floatingBar = true))
        assertEquals(24, Toaster.toastBottomPaddingDp(floatingBar = false))
    }

    @Test
    fun `ui settings keep the phone defaults and map unknown names to them`() {
        val decoded = BridgeJson.decodeFromString(UiSettings.serializer(), """{"transitionEffect":"Warp","iconLikeType":"Gift","topN":null,"futureField":1}""")
        assertEquals(TransitionEffect.Fade, decoded.transition)
        assertEquals(IconLikeType.Gift, decoded.likeIcon)
        assertNull(decoded.topN)
        assertEquals("Dynamic", decoded.colorPaletteName)
        assertTrue(UiSettings().isFloatingNavigationBar)
        assertEquals(PlayerBackgroundColors.AnimatedGradient, UiSettings(playerBackgroundColors = "?").playerBackground)
        assertTrue(UiSettings(colorPaletteName = "ModernBlack").isBlackPalette)
    }
}
