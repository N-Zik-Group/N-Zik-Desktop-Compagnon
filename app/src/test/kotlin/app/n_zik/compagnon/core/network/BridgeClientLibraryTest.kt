package app.n_zik.compagnon.core.network

import app.n_zik.compagnon.bridge.library.Album
import app.n_zik.compagnon.bridge.library.Artist
import app.n_zik.compagnon.bridge.library.CollectionFilter
import app.n_zik.compagnon.bridge.library.CollectionKind
import app.n_zik.compagnon.bridge.library.CollectionRef
import app.n_zik.compagnon.bridge.library.Playlist
import app.n_zik.compagnon.bridge.library.SongFilter
import app.n_zik.compagnon.bridge.library.SongSort
import app.n_zik.compagnon.bridge.library.SongsQuery
import app.n_zik.compagnon.bridge.state.Track
import app.n_zik.compagnon.bridge.state.TrackSource
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class BridgeClientLibraryTest {

    private val token = "T".repeat(43)
    private val address = ServerAddress("192.168.1.14", 42420)
    private val requests = mutableListOf<HttpRequestData>()

    private fun client(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) =
        BridgeClient(MockEngine { request -> requests += request; handler(request) })

    private fun MockRequestHandleScope.json(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
        respond(body, status, headersOf(HttpHeaders.ContentType, "application/json; charset=utf-8"))

    private fun MockRequestHandleScope.empty() = json("""{"items":[],"total":0,"offset":0,"limit":100}""")

    private val request get() = requests.single()

    @Test
    fun `songs sends pagination, query, filter and sort with the Bearer`() = runTest {
        val result = client {
            json(
                """{"items":[{"id":"dQw4w9WgXcQ","title":"T","artists":null,"durationMs":212000,"source":"weird",
                   "isDownloaded":false,"isLiked":true,"hasArtwork":true,"extra":1}],"total":230,"offset":100,"limit":100}""",
            )
        }.songs(address, token, 100, 100, SongsQuery("a b&c", SongFilter.Downloaded, SongSort.PlayTime))
        val page = (result as LibraryResult.Ok).page
        assertEquals(230, page.total)
        assertEquals(Track("dQw4w9WgXcQ", "T", null, 212_000, TrackSource.Online, false, true, true), page.items.single())
        assertEquals(HttpMethod.Get, request.method)
        assertEquals("/api/v1/library/songs", request.url.encodedPath)
        assertEquals("100", request.url.parameters["offset"])
        assertEquals("100", request.url.parameters["limit"])
        assertEquals("a b&c", request.url.parameters["query"])
        assertEquals("downloaded", request.url.parameters["filter"])
        assertEquals("playTime", request.url.parameters["sort"])
        assertEquals("Bearer $token", request.headers[HttpHeaders.Authorization])
    }

    @Test
    fun `songs without text sends no query`() = runTest {
        client { empty() }.songs(address, token, 0, 100, SongsQuery())
        assertNull(request.url.parameters["query"])
        assertEquals("all", request.url.parameters["filter"])
        assertEquals("title", request.url.parameters["sort"])
    }

    @Test
    fun `playlists, albums and artists`() = runTest {
        val api = client { req ->
            when {
                req.url.encodedPath.endsWith("playlists") ->
                    json("""{"items":[{"id":"12","name":"Ma playlist","trackCount":42,"artworkTrackId":null}],"total":1,"offset":0,"limit":100}""")
                req.url.encodedPath.endsWith("albums") ->
                    json("""{"items":[{"id":"MPREb_x","title":"A","artists":"X","year":"2021","trackCount":12,"hasArtwork":true}],"total":1,"offset":0,"limit":100}""")
                else -> json("""{"items":[{"id":"UCx","name":"X","trackCount":34,"hasArtwork":false}],"total":1,"offset":0,"limit":100}""")
            }
        }
        assertEquals(Playlist("12", "Ma playlist", 42, null), (api.playlists(address, token, 0, 100) as LibraryResult.Ok).page.items.single())
        assertEquals(
            Album("MPREb_x", "A", "X", "2021", 12, true),
            (api.albums(address, token, 0, 100, CollectionFilter.Bookmarked) as LibraryResult.Ok).page.items.single(),
        )
        assertEquals(Artist("UCx", "X", 34, false), (api.artists(address, token, 0, 100, CollectionFilter.Library) as LibraryResult.Ok).page.items.single())
        assertEquals(listOf("/api/v1/library/playlists", "/api/v1/library/albums", "/api/v1/library/artists"), requests.map { it.url.encodedPath })
        assertNull(requests[0].url.parameters["filter"])
        assertEquals("bookmarked", requests[1].url.parameters["filter"])
        assertEquals("library", requests[2].url.parameters["filter"])
    }

    @Test
    fun `collection ids are percent-encoded in the path`() = runTest {
        val api = client { empty() }
        api.collectionSongs(address, token, CollectionRef(CollectionKind.Playlist, "12"), 0, 100)
        api.collectionSongs(address, token, CollectionRef(CollectionKind.Album, "MPREb_x/y"), 0, 100)
        api.collectionSongs(address, token, CollectionRef(CollectionKind.Artist, "UC x?#"), 200, 100)
        assertEquals(
            listOf(
                "/api/v1/library/playlists/12/songs",
                "/api/v1/library/albums/MPREb_x%2Fy/songs",
                "/api/v1/library/artists/UC%20x%3F%23/songs",
            ),
            requests.map { it.url.encodedPath },
        )
        assertEquals("200", requests[2].url.parameters["offset"])
    }

    @Test
    fun `errors are mapped from the code`() = runTest {
        suspend fun answer(body: String, status: HttpStatusCode) =
            client { json(body, status) }.collectionSongs(address, token, CollectionRef(CollectionKind.Playlist, "12"), 0, 100)

        assertEquals(LibraryResult.NotFound, answer("""{"code":"NOT_FOUND","message":"x"}""", HttpStatusCode.NotFound))
        assertEquals(LibraryResult.Revoked, answer("""{"code":"DEVICE_REVOKED","message":"x"}""", HttpStatusCode.Unauthorized))
        assertEquals(
            LibraryResult.OtherActive("PC-BUREAU"),
            answer("""{"code":"CONFLICT_ACTIVE_CLIENT","message":"x","activeDevice":{"deviceId":"d","deviceName":"PC-BUREAU"}}""", HttpStatusCode.Conflict),
        )
        assertEquals(LibraryResult.Failed(401, "UNAUTHORIZED"), answer("""{"code":"UNAUTHORIZED","message":"DEVICE_REVOKED"}""", HttpStatusCode.Unauthorized))
        assertEquals(LibraryResult.Failed(503, "SERVER_STOPPING"), answer("""{"code":"SERVER_STOPPING","message":"x"}""", HttpStatusCode.ServiceUnavailable))
        assertEquals(LibraryResult.Failed(500, null), answer("not json", HttpStatusCode.InternalServerError))
        assertEquals(LibraryResult.Failed(200, null), answer("not json", HttpStatusCode.OK))
        assertEquals(LibraryResult.Unreachable, client { throw IOException("refused") }.playlists(address, token, 0, 100))
    }
}
