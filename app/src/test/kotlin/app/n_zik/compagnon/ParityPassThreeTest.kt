package app.n_zik.compagnon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import app.n_zik.compagnon.bridge.library.Album
import app.n_zik.compagnon.bridge.library.AlbumLike
import app.n_zik.compagnon.bridge.library.AlbumsQuery
import app.n_zik.compagnon.bridge.library.Artist
import app.n_zik.compagnon.bridge.library.ArtistFollow
import app.n_zik.compagnon.bridge.library.ArtistsQuery
import app.n_zik.compagnon.bridge.library.CollectionRef
import app.n_zik.compagnon.bridge.library.DislikeMode
import app.n_zik.compagnon.bridge.library.LibraryCache
import app.n_zik.compagnon.bridge.library.LibraryRepository
import app.n_zik.compagnon.bridge.library.Page
import app.n_zik.compagnon.bridge.library.Playlist
import app.n_zik.compagnon.bridge.library.PlaylistSongSort
import app.n_zik.compagnon.bridge.library.PlaylistSongsQuery
import app.n_zik.compagnon.bridge.library.PlaylistsQuery
import app.n_zik.compagnon.bridge.library.RemoteLibraryRepository
import app.n_zik.compagnon.bridge.library.RewindState
import app.n_zik.compagnon.bridge.library.SongsQuery
import app.n_zik.compagnon.bridge.pairing.RevocationPolicy
import app.n_zik.compagnon.bridge.state.ListRef
import app.n_zik.compagnon.bridge.state.Track
import app.n_zik.compagnon.bridge.state.TrackLike
import app.n_zik.compagnon.bridge.state.UiSettings
import app.n_zik.compagnon.components.tab.locatorRow
import app.n_zik.compagnon.components.theme.DefaultDarkColorPalette
import app.n_zik.compagnon.components.theme.DefaultLightColorPalette
import app.n_zik.compagnon.components.theme.PureBlackColorPalette
import app.n_zik.compagnon.components.ui.screens.bridge.phoneToastText
import app.n_zik.compagnon.components.ui.screens.home.LibraryLists
import app.n_zik.compagnon.core.navigation.BackStep
import app.n_zik.compagnon.core.navigation.backStep
import app.n_zik.compagnon.core.network.BridgeClient
import app.n_zik.compagnon.core.network.LibraryResult
import app.n_zik.compagnon.core.network.ServerAddress
import app.n_zik.compagnon.core.network.WriteResult
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Tests asked by the 2026-10-10 code review (rows 16-20 of the spec's triage log). */
class ParityPassThreeTest {

    private val token = "T".repeat(43)
    private val address = ServerAddress("192.168.1.14", 42420)

    private fun client(requests: MutableList<HttpRequestData>, status: HttpStatusCode, body: String) =
        BridgeClient(
            MockEngine { request ->
                requests += request
                respond(body, status, headersOf(HttpHeaders.ContentType, "application/json; charset=utf-8"))
            },
        )

    // ---- Row 16: locate / uiSettings on the wire ----

    @Test
    fun `locate sends the list reference parameters and reads the index`() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val answer = client(requests, HttpStatusCode.OK, """{"index":1500,"total":2000}""")
            .locate(address, token, ListRef("songs", filter = "liked", sort = "title", reverse = true, text = "abc"), "t1500")
        assertEquals(1500, answer?.index)
        val url = requests.single().url
        assertEquals("/api/v1/library/locate", url.encodedPath)
        assertEquals("t1500", url.parameters["trackId"])
        assertEquals("songs", url.parameters["kind"])
        assertEquals("liked", url.parameters["filter"])
        assertEquals("true", url.parameters["reverse"])
        assertEquals("abc", url.parameters["text"])
        assertNull(url.parameters["id"])
    }

    @Test
    fun `locate and uiSettings are null on an error answer`() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val api = client(requests, HttpStatusCode.NotFound, """{"code":"NOT_FOUND","message":"x"}""")
        assertNull(api.locate(address, token, ListRef("playlist", id = "9"), "t"))
        assertNull(api.uiSettings(address, token))
    }

    @Test
    fun `uiSettings decodes the phone's settings`() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val settings = client(requests, HttpStatusCode.OK, """{"colorPaletteName":"PureBlack","topN":null}""").uiSettings(address, token)
        assertEquals("PureBlack", settings?.colorPaletteName)
        assertNull(settings?.topN)
        assertEquals("/api/v1/ui/settings", requests.single().url.encodedPath)
    }

    @Test
    fun `the repository gates locate and uiSettings on their features`() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val api = client(requests, HttpStatusCode.OK, """{"index":3,"total":9}""")
        val without = RemoteLibraryRepository(api, address, token, setOf("library.songs"), RevocationPolicy(onRevoked = {}))
        assertNull(without.locate(ListRef("songs"), "t"))
        assertNull(without.uiSettings())
        assertTrue(requests.isEmpty())
        val with = RemoteLibraryRepository(api, address, token, setOf("library.locate"), RevocationPolicy(onRevoked = {}))
        assertEquals(3, with.locate(ListRef("songs"), "t"))
    }

    // ---- Row 16: the locator's target row in a de-duplicated list ----

    @Test
    fun `the locator maps a raw index to its de-duplicated row`() {
        val ids = listOf("a", "b", "a", "c")
        assertEquals(2, locatorRow(ids, 3))
        assertEquals(0, locatorRow(ids, 2))
        assertEquals(-1, locatorRow(ids, -1))
        assertEquals(-1, locatorRow(ids, 9))
    }

    // ---- Row 17: phoneToastText ----

    @Test
    fun `a relayed toast uses the PC string of its key, numbers formatted, else the phone's text`() = runTest {
        val texts = withContext(Dispatchers.Default) {
            withTimeoutOrNull(5_000) {
                listOf(
                    phoneToastText("songs_shuffled", listOf("12"), "12 morceaux mélangés"),
                    phoneToastText("unknown_key_xyz", emptyList(), "Texte du téléphone"),
                )
            }
        }
        // `songs_shuffled` has no locale copy yet: its English text whatever the host's language
        assertEquals(listOf("12 songs shuffled", "Texte du téléphone"), texts)
    }

    // ---- Row 18: the palette from the phone's settings ----

    @Test
    fun `static palettes follow the phone's name and mode`() {
        assertEquals(DefaultLightColorPalette, staticColorPaletteOf(UiSettings(colorPaletteName = "Default", colorPaletteMode = "Light"), true))
        assertEquals(DefaultDarkColorPalette, staticColorPaletteOf(UiSettings(colorPaletteName = "Default", colorPaletteMode = "System"), true))
        assertEquals(DefaultLightColorPalette, staticColorPaletteOf(UiSettings(colorPaletteName = "Default", colorPaletteMode = "System"), false))
        assertEquals(PureBlackColorPalette, staticColorPaletteOf(UiSettings(colorPaletteName = "PureBlack"), false))
        // MaterialYou: the violet dynamic palette, dark from the phone's Dark mode even on a light desktop
        assertTrue(staticColorPaletteOf(UiSettings(colorPaletteName = "MaterialYou", colorPaletteMode = "Dark"), false).isDark)
        assertTrue(!staticColorPaletteOf(UiSettings(colorPaletteName = "MaterialYou", colorPaletteMode = "Light"), true).isDark)
    }

    @Test
    fun `the dynamic palette applies PitchBlack and leaves non-Dynamic palettes static`() = runTest {
        val state = AppearanceState(computeAppearance(FontFamily.Default))
        state.setDynamicPalette(null, UiSettings(colorPaletteMode = "PitchBlack"), isSystemInDarkTheme = false)
        assertEquals(Color.Black, state.appearance.colorPalette.background0)
        assertTrue(state.appearance.colorPalette.isDark)
        state.setDynamicPalette(null, UiSettings(colorPaletteMode = "Light"), isSystemInDarkTheme = true)
        assertTrue(!state.appearance.colorPalette.isDark)
        state.setDynamicPalette(null, UiSettings(colorPaletteName = "PureBlack"), isSystemInDarkTheme = true)
        assertEquals(PureBlackColorPalette, state.appearance.colorPalette)
    }

    // ---- Row 19: the player's inline queue closes before the player ----

    @Test
    fun `the back closes the player's queue, then the player`() {
        assertEquals(BackStep.PlayerQueue, backStep(false, false, false, playerOpen = true, pageOpen = true, playerQueueOpen = true))
        assertEquals(BackStep.Player, backStep(false, false, false, playerOpen = true, pageOpen = true, playerQueueOpen = false))
        // A closed player never reports its queue
        assertEquals(BackStep.Page, backStep(false, false, false, playerOpen = false, pageOpen = true, playerQueueOpen = true))
    }

    // ---- Row 20: the sorted mosaic path ----

    @Test
    fun `with library sort the mosaic reads one reversed play-time page and keeps four with artwork`() = runTest {
        val reads = mutableListOf<PlaylistSongsQuery?>()
        // The reversed play-time order: z (most played) first; "n*" have no artwork
        val reversed = listOf(Track("z", hasArtwork = true), Track("n1"), Track("y", hasArtwork = true), Track("n2"),
            Track("x", hasArtwork = true), Track("w", hasArtwork = true), Track("v", hasArtwork = true))
        val library = MosaicLibrary(setOf("library.playlists", "library.sort")) { _, _, limit, query ->
            reads += query
            LibraryResult.Ok(Page(reversed.take(limit), reversed.size, 0, limit))
        }
        val lists = LibraryLists(library, backgroundScope)
        lists.loadPlaylistFirstTracks("7")
        withContext(Dispatchers.Default) { withTimeoutOrNull(5_000) { while (lists.playlistFirstTracks.value["7"] == null) delay(10) } }
        assertEquals(PlaylistSongsQuery(sort = PlaylistSongSort.PlayTime, reverse = true), reads.single())
        // The phone's takeLast(4) of the play-time order, artwork-less tracks dropped first
        assertEquals(listOf("w", "x", "y", "z"), lists.playlistFirstTracks.value["7"]?.map { it.id })
    }

    private class MosaicLibrary(
        override val features: Set<String>,
        private val read: (CollectionRef, Int, Int, PlaylistSongsQuery?) -> LibraryResult<Track>,
    ) : LibraryRepository {
        override suspend fun songs(offset: Int, limit: Int, query: SongsQuery): LibraryResult<Track> = LibraryResult.Ok(Page(emptyList(), 0, offset, limit))
        override suspend fun playlists(offset: Int, limit: Int, query: PlaylistsQuery): LibraryResult<Playlist> = LibraryResult.Ok(Page(emptyList(), 0, offset, limit))
        override suspend fun albums(offset: Int, limit: Int, query: AlbumsQuery): LibraryResult<Album> = LibraryResult.Ok(Page(emptyList(), 0, offset, limit))
        override suspend fun artists(offset: Int, limit: Int, query: ArtistsQuery): LibraryResult<Artist> = LibraryResult.Ok(Page(emptyList(), 0, offset, limit))
        override suspend fun collectionSongs(collection: CollectionRef, offset: Int, limit: Int, query: PlaylistSongsQuery?): LibraryResult<Track> =
            read(collection, offset, limit, query)
        override suspend fun songLike(songId: String, state: TrackLike): WriteResult = WriteResult.SongLike(state)
        override suspend fun albumBookmark(albumId: String, bookmarked: Boolean): WriteResult = WriteResult.AlbumBookmark(bookmarked)
        override suspend fun artistFollow(artistId: String, state: ArtistFollow): WriteResult = WriteResult.ArtistFollow(state)
        override suspend fun playlistPin(playlistId: String, pinned: Boolean): WriteResult = WriteResult.PlaylistPin(pinned)
        override suspend fun albumLike(albumId: String, state: AlbumLike): WriteResult = WriteResult.AlbumLike(state)
        override suspend fun playlistBookmark(playlistId: String, bookmarked: Boolean): WriteResult = WriteResult.PlaylistBookmark(bookmarked)
        override suspend fun cacheSpace(): LibraryCache? = null
        override suspend fun rewindState(): RewindState? = null
        override suspend fun dislikeMode(): DislikeMode? = null
    }
}
