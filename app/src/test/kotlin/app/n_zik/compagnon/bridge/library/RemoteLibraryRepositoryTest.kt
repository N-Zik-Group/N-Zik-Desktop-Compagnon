package app.n_zik.compagnon.bridge.library

import app.n_zik.compagnon.bridge.pairing.RevocationPolicy
import app.n_zik.compagnon.core.network.BridgeClient
import app.n_zik.compagnon.core.network.LibraryResult
import app.n_zik.compagnon.core.network.ServerAddress
import app.n_zik.compagnon.core.network.WriteResult
import app.n_zik.compagnon.bridge.state.Track
import app.n_zik.compagnon.bridge.state.TrackLike
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import java.io.IOException
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RemoteLibraryRepositoryTest {

    private val token = "T".repeat(43)
    private val address = ServerAddress("192.168.1.14", 42420)
    private val requests = mutableListOf<HttpRequestData>()
    private var revoked = 0

    private fun MockRequestHandleScope.json(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
        respond(body, status, headersOf(HttpHeaders.ContentType, "application/json; charset=utf-8"))

    private fun MockRequestHandleScope.revokedAnswer() = json("""{"code":"DEVICE_REVOKED","message":"x"}""", HttpStatusCode.Unauthorized)

    private fun MockRequestHandleScope.tracks(offset: Int, count: Int, total: Int) = json(
        """{"items":[${(offset until offset + count).joinToString(",") { """{"id":"t$it"}""" }}],"total":$total,"offset":$offset,"limit":100}""",
    )

    private fun TestScope.repository(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): RemoteLibraryRepository {
        val engine = MockEngine.create {
            dispatcher = StandardTestDispatcher(testScheduler)
            addHandler { request ->
                requests += request
                handler(request)
            }
        }
        return RemoteLibraryRepository(
            BridgeClient(engine),
            address,
            token,
            setOf("library.songs"),
            RevocationPolicy(onRevoked = { revoked++ }),
        )
    }

    @Test
    fun `a 401 DEVICE_REVOKED confirmed 2 s later revokes the pairing`() = runTest {
        val repository = repository { revokedAnswer() }
        val result = async { repository.playlists(0, 100, PlaylistsQuery()) }
        runCurrent()
        assertEquals(1, requests.size)
        advanceTimeBy(1_900)
        runCurrent()
        assertEquals(1, requests.size, "no retry before 2 s")
        advanceUntilIdle()
        assertEquals(LibraryResult.Revoked, result.await())
        assertEquals(2, requests.size)
        assertEquals(1, revoked)
    }

    @Test
    fun `an unconfirmed 401 gives back the second answer and erases nothing`() = runTest {
        var first = true
        val repository = repository {
            if (first) {
                first = false
                revokedAnswer()
            } else {
                json("""{"items":[],"total":0,"offset":0,"limit":100}""")
            }
        }
        val result = repository.songs(0, 100, SongsQuery())
        assertEquals(LibraryResult.Ok(Page<Track>(emptyList(), 0, 0, 100)), result)
        assertEquals(0, revoked)
    }

    @Test
    fun `a 401 DEVICE_REVOKED confirmed 2 s later revokes the pairing on a write too`() = runTest {
        val repository = repository { revokedAnswer() }
        val result = async { repository.songLike("dQw4w9WgXcQ", TrackLike.Liked) }
        runCurrent()
        assertEquals(1, requests.size)
        advanceTimeBy(1_900)
        runCurrent()
        assertEquals(1, requests.size, "no retry before 2 s")
        advanceUntilIdle()
        assertEquals(WriteResult.Revoked, result.await())
        assertEquals(2, requests.size)
        assertEquals(1, revoked)
    }

    @Test
    fun `an unconfirmed 401 on a write gives back the second answer and erases nothing`() = runTest {
        var first = true
        val repository = repository {
            if (first) {
                first = false
                revokedAnswer()
            } else {
                json("""{"state":"liked"}""")
            }
        }
        val result = repository.songLike("dQw4w9WgXcQ", TrackLike.Liked)
        assertEquals(WriteResult.SongLike(TrackLike.Liked), result)
        assertEquals(0, revoked)
    }

    @Test
    fun `409 another PC is reported without retry nor revocation`() = runTest {
        val repository = repository {
            json("""{"code":"CONFLICT_ACTIVE_CLIENT","message":"x","activeDevice":{"deviceId":"d","deviceName":"PC-BUREAU"}}""", HttpStatusCode.Conflict)
        }
        assertEquals(LibraryResult.OtherActive("PC-BUREAU"), repository.albums(0, 100, AlbumsQuery()))
        assertEquals(1, requests.size)
        assertEquals(0, revoked)
    }

    @Test
    fun `collection tracks are read by pages up to one more than 500`() = runTest {
        val repository = repository { request ->
            val offset = request.url.parameters["offset"]!!.toInt()
            val limit = request.url.parameters["limit"]!!.toInt()
            tracks(offset, minOf(limit, 730 - offset), 730)
        }
        val result = repository.collectionTracks(CollectionRef(CollectionKind.Artist, "UCx")) as LibraryResult.Ok
        assertEquals(501, result.page.items.size)
        assertEquals(730, result.page.total)
        assertEquals(listOf(0, 100, 200, 300, 400, 500), requests.map { it.url.parameters["offset"]!!.toInt() })
        assertEquals(listOf(100, 100, 100, 100, 100, 1), requests.map { it.url.parameters["limit"]!!.toInt() })
    }

    @Test
    fun `a small collection is read whole`() = runTest {
        val repository = repository { tracks(0, 12, 12) }
        val result = repository.collectionTracks(CollectionRef(CollectionKind.Album, "MPREb_x")) as LibraryResult.Ok
        assertEquals(List(12) { "t$it" }, result.page.items.map { it.id })
        assertEquals(1, requests.size)
    }

    @Test
    fun `a collection gone is not found`() = runTest {
        val repository = repository { json("""{"code":"NOT_FOUND","message":"x"}""", HttpStatusCode.NotFound) }
        assertEquals(LibraryResult.NotFound, repository.collectionTracks(CollectionRef(CollectionKind.Playlist, "12")))
    }

    @Test
    fun `a failure on page 2 gives that failure, never a partial list`() = runTest {
        val unreachable = repository { request ->
            val offset = request.url.parameters["offset"]!!.toInt()
            if (offset == 0) tracks(0, 100, 300) else throw IOException("phone gone")
        }
        assertEquals(LibraryResult.Unreachable, unreachable.collectionTracks(CollectionRef(CollectionKind.Artist, "UCx")))

        requests.clear()
        val failed = repository { request ->
            val offset = request.url.parameters["offset"]!!.toInt()
            if (offset == 0) tracks(0, 100, 300) else json("""{"code":"INTERNAL","message":"x"}""", HttpStatusCode.InternalServerError)
        }
        val result = failed.collectionTracks(CollectionRef(CollectionKind.Artist, "UCx"))
        assertEquals(LibraryResult.Failed(500, "INTERNAL"), result)
        assertEquals(listOf(0, 100), requests.map { it.url.parameters["offset"]!!.toInt() })
    }

    @Test
    fun `an empty page stops the reading even when total is higher`() = runTest {
        val repository = repository { request ->
            val offset = request.url.parameters["offset"]!!.toInt()
            if (offset == 0) tracks(0, 100, 300) else tracks(offset, 0, 300)
        }
        val result = repository.collectionTracks(CollectionRef(CollectionKind.Playlist, "12")) as LibraryResult.Ok
        assertEquals(100, result.page.items.size)
        assertEquals(listOf(0, 100), requests.map { it.url.parameters["offset"]!!.toInt() })
    }
}
