package app.n_zik.compagnon.components.ui.screens.home

import androidx.compose.runtime.mutableStateOf
import app.n_zik.compagnon.bridge.library.RemoteLibraryRepository
import app.n_zik.compagnon.bridge.library.SongFilter
import app.n_zik.compagnon.bridge.pairing.BridgeJson
import app.n_zik.compagnon.bridge.pairing.RevocationPolicy
import app.n_zik.compagnon.bridge.state.Recommendation
import app.n_zik.compagnon.bridge.state.SongRecommendations
import app.n_zik.compagnon.bridge.state.Track
import app.n_zik.compagnon.components.MenuState
import app.n_zik.compagnon.components.showsPlaylistIndicator
import app.n_zik.compagnon.components.tab.ItemSelector
import app.n_zik.compagnon.core.network.BridgeClient
import app.n_zik.compagnon.core.network.ServerAddress
import app.n_zik.compagnon.generated.resources.Res
import app.n_zik.compagnon.generated.resources.checked_filled
import app.n_zik.compagnon.generated.resources.item_deselect
import app.n_zik.compagnon.generated.resources.item_select
import app.n_zik.compagnon.generated.resources.unchecked_outline
import app.n_zik.compagnon.utils.Preferences
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * Step 1 of the PC UI gaps plan (spec `spec-fill-pc-ui-gaps-items`, contract 1.12.0): `Track.inPlaylist`,
 * the smart-shuffle recommendations (wire, feature gate, insertion), the item selector toggle and the
 * `playlistindicator` setting.
 */
class PcItemsGapsTest {

    @TempDir
    lateinit var dir: Path

    private val token = "T".repeat(43)
    private val address = ServerAddress("192.168.1.14", 42420)

    private fun track(id: String, title: String = "Title $id", artists: String? = "Artist") = Track(id = id, title = title, artists = artists)

    private fun recommendations(filter: String?, vararg items: Pair<Int, Track>) =
        SongRecommendations(filter, items.map { (position, track) -> Recommendation(position, track) })

    @Test
    fun `Track inPlaylist decodes when sent and defaults to false from an older phone`() {
        assertTrue(BridgeJson.decodeFromString(Track.serializer(), """{"id":"a","inPlaylist":true}""").inPlaylist)
        assertFalse(BridgeJson.decodeFromString(Track.serializer(), """{"id":"a","title":"Song"}""").inPlaylist)
    }

    @Test
    fun `the recommendations answer decodes with and without its fields`() {
        val full = BridgeJson.decodeFromString(
            SongRecommendations.serializer(),
            """{"filter":"all","items":[{"position":4,"track":{"id":"aaaaaaaaaaa","title":"Rec"}}],"extra":1}""",
        )
        assertEquals("all", full.filter)
        assertEquals(4, full.items.single().position)
        assertEquals("aaaaaaaaaaa", full.items.single().track.id)

        val empty = BridgeJson.decodeFromString(SongRecommendations.serializer(), """{}""")
        assertNull(empty.filter)
        assertTrue(empty.items.isEmpty())
    }

    @Test
    fun `the repository reads the route only with the feature, a failed read is null`() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        var status = HttpStatusCode.OK
        val api = BridgeClient(
            MockEngine { request ->
                requests += request
                respond(
                    """{"filter":"liked","items":[{"position":1,"track":{"id":"r"}}]}""",
                    status,
                    headersOf(HttpHeaders.ContentType, "application/json; charset=utf-8"),
                )
            },
        )
        val without = RemoteLibraryRepository(api, address, token, setOf("library.songs"), RevocationPolicy(onRevoked = {}))
        assertNull(without.songRecommendations())
        assertTrue(requests.isEmpty())

        val with = RemoteLibraryRepository(api, address, token, setOf("library.recommendations"), RevocationPolicy(onRevoked = {}))
        assertEquals("liked", with.songRecommendations()?.filter)
        assertEquals("/api/v1/library/songs/recommendations", requests.single().url.encodedPath)

        status = HttpStatusCode.InternalServerError
        assertNull(with.songRecommendations())
    }

    @Test
    fun `the recommendations are inserted at their bounded positions, in order`() {
        val loaded = listOf(track("a"), track("b"), track("c"))
        val recs = recommendations("all", 1 to track("r1"), 99 to track("r2"), -3 to track("r3"))

        val rows = songRows(loaded, recs, SongFilter.All, null)

        assertEquals(listOf("r3", "a", "r1", "b", "c", "r2"), rows.map { it.track.id })
        assertEquals(listOf(null, 0, null, 1, 2, null), rows.map { it.loadedIndex })
        assertTrue(rows.first().isRecommended)
        assertFalse(rows[1].isRecommended)
    }

    @Test
    fun `a duplicated id keeps its first row`() {
        val loaded = listOf(track("a"), track("b"))
        val rows = songRows(loaded, recommendations("all", 2 to track("a"), 0 to track("r"), 0 to track("r")), SongFilter.All, null)

        assertEquals(listOf("r", "a", "b"), rows.map { it.track.id })
        assertEquals(0, rows[1].loadedIndex)
    }

    @Test
    fun `another chip, a PC-only chip or no answer inserts nothing`() {
        val loaded = listOf(track("a"))
        val recs = recommendations("liked", 0 to track("r"))

        assertEquals(listOf("a"), songRows(loaded, recs, SongFilter.All, null).map { it.track.id })
        assertEquals(listOf("a"), songRows(loaded, recs, null, null).map { it.track.id })
        assertEquals(listOf("a"), songRows(loaded, null, SongFilter.Liked, null).map { it.track.id })
        assertEquals(listOf("a"), songRows(loaded, recommendations(null), SongFilter.All, null).map { it.track.id })
        assertTrue(matchingRecommendations(recs, SongFilter.All).isEmpty())
        assertEquals(1, matchingRecommendations(recs, SongFilter.Liked).size)
    }

    @Test
    fun `the search keeps only the recommendations whose title or artists match`() {
        val loaded = listOf(track("a", title = "Loaded"))
        val recs = recommendations(
            "all",
            0 to track("r1", title = "Blue Moon"),
            0 to track("r2", title = "Other", artists = "Moonlight Band"),
            0 to track("r3", title = "Nothing"),
        )

        val rows = songRows(loaded, recs, SongFilter.All, "moon")

        // The loaded rows come filtered by the phone's `query`: they are kept as they are
        assertEquals(setOf("r1", "r2", "a"), rows.map { it.track.id }.toSet())
    }

    @Test
    fun `the item selector toggles its icon and title, and hides the open menu`() {
        val menu = MenuState()
        menu.display {}
        val selector = ItemSelector(mutableStateOf(false), menu)
        assertFalse(selector.isActive)
        assertEquals(Res.drawable.unchecked_outline, selector.iconId)
        assertEquals(Res.string.item_select, selector.messageId)

        selector.onShortClick()

        assertTrue(selector.isActive)
        assertFalse(menu.isDisplayed)
        assertEquals(Res.drawable.checked_filled, selector.iconId)
        assertEquals(Res.string.item_deselect, selector.messageId)

        selector.onShortClick()
        assertFalse(selector.isActive)
    }

    @Test
    fun `the playlist indicator shows for a track in a playlist, never on the playlist's own screen`() {
        assertTrue(showsPlaylistIndicator(setting = true, showOnScreen = true, inPlaylist = true))
        // A playlist's own screen (LocalPlaylistSongs passes `false`)
        assertFalse(showsPlaylistIndicator(setting = true, showOnScreen = false, inPlaylist = true))
        // Not in a playlist, or an older phone (`inPlaylist` absent → false)
        assertFalse(showsPlaylistIndicator(setting = true, showOnScreen = true, inPlaylist = false))
        // The setting off
        assertFalse(showsPlaylistIndicator(setting = false, showOnScreen = true, inPlaylist = true))
    }

    @Test
    fun `the playlist indicator defaults to hidden and round-trips through settings json`() {
        val file = dir.resolve("settings.json")
        Files.writeString(file, """{"disableScrollingText":true}""")
        assertFalse(Preferences(file).settings.value.playlistIndicator)

        Preferences(file).update { it.copy(playlistIndicator = true) }

        assertTrue(Files.readString(file).contains("\"playlistindicator\""))
        val reloaded = Preferences(file).settings.value
        assertTrue(reloaded.playlistIndicator)
        assertTrue(reloaded.disableScrollingText)
    }
}
