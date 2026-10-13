package app.n_zik.compagnon

import app.n_zik.compagnon.bridge.library.Page
import app.n_zik.compagnon.bridge.library.PagedList
import app.n_zik.compagnon.bridge.state.Track
import app.n_zik.compagnon.bridge.state.TrackLike
import app.n_zik.compagnon.components.MenuState
import app.n_zik.compagnon.components.items.playlistThumbnails
import app.n_zik.compagnon.components.player.likeToastLabel
import app.n_zik.compagnon.components.player.likeToastMessage
import app.n_zik.compagnon.components.player.timeline.skipTarget
import app.n_zik.compagnon.components.themed.NumericInput
import app.n_zik.compagnon.components.themed.inProgressFraction
import app.n_zik.compagnon.core.network.ArtworkKey
import app.n_zik.compagnon.core.network.LibraryResult
import app.n_zik.compagnon.generated.resources.*
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The pure logic behind the PC UI parity fixes of the 2026-10-09 audit. */
class ParityAuditFixesTest {

    // ---- Zone 4: the menu content stack (the phone's `MenuState`) ----

    @Test
    fun `a sub-menu stacks over its menu and pop goes back, then closes`() {
        val state = MenuState()
        val root: @androidx.compose.runtime.Composable () -> Unit = {}
        val sub: @androidx.compose.runtime.Composable () -> Unit = {}
        state.display(root)
        assertFalse(state.hasPrevious)
        state.display(sub)
        assertTrue(state.hasPrevious)
        assertEquals(2, state.transitionKey)
        state.pop()
        assertTrue(state.isDisplayed)
        assertFalse(state.hasPrevious)
        assertEquals(1, state.transitionKey)
        state.pop()
        assertFalse(state.isDisplayed)
    }

    @Test
    fun `a menu displayed after closing starts a fresh stack`() {
        val state = MenuState()
        state.display {}
        state.display {}
        state.hide()
        state.display {}
        assertFalse(state.hasPrevious)
    }

    // ---- Zone 3: the skip buttons' seek guard (the phone's `newPosition < 0` return) ----

    @Test
    fun `forward while the duration is unknown sends nothing`() {
        val unknown = -9_223_372_036_854_775_807L
        assertNull(skipTarget(10_000L, 5_000L, Long::plus, ::minOf, unknown))
        assertEquals(15_000L, skipTarget(10_000L, 5_000L, Long::plus, ::minOf, 200_000L))
        assertEquals(200_000L, skipTarget(198_000L, 5_000L, Long::plus, ::minOf, 200_000L))
        assertEquals(0L, skipTarget(2_000L, 5_000L, Long::minus, ::maxOf, 0L))
    }

    // ---- Zone 5: the numeric input has no maximum (the phone only checks the minimum) ----

    @Test
    fun `numeric input keeps a value over the maximum and refuses empty or below min`() {
        assertEquals(NumericInput.Valid(50_000), NumericInput.check("50000", "32"))
        assertEquals(NumericInput.BelowMin, NumericInput.check("10", "32"))
        assertEquals(NumericInput.Empty, NumericInput.check("", "32"))
        assertEquals(NumericInput.Empty, NumericInput.check("abc", "32"))
    }

    @Test
    fun `in-progress fraction`() {
        assertEquals(0.5f, inProgressFraction(10, 5))
        assertEquals(0f, inProgressFraction(0, 5))
    }

    // ---- Zone 3: the like toast (the phone's messages, cleaned label) ----

    @Test
    fun `like toast messages follow the rotation or the toggle`() {
        assertEquals(Res.string.added_to_dislikes, likeToastMessage(TrackLike.Disliked, rotationEnabled = true))
        assertEquals(Res.string.removed_from_dislikes, likeToastMessage(TrackLike.Neutral, rotationEnabled = true))
        assertEquals(Res.string.removed_from_favorites, likeToastMessage(TrackLike.Neutral, rotationEnabled = false))
        assertEquals("\"Song - Artist\"", likeToastLabel("Song", "Artist"))
        assertEquals("\"Song\"", likeToastLabel("Song", " "))
        assertNull(likeToastLabel(" ", "Artist"))
    }

    // ---- Zone 1: the playlist mosaic (the phone's four tracks with a thumbnail, else the first) ----

    @Test
    fun `playlist mosaic keeps the tracks that have an artwork`() {
        val four = List(4) { Track("t$it", hasArtwork = true) }
        assertEquals(4, playlistThumbnails(four, "x", 256).size)
        val mixed = listOf(Track("a", hasArtwork = false), Track("b", hasArtwork = true))
        assertEquals(listOf(ArtworkKey.track("b", 256)), playlistThumbnails(mixed, "x", 256))
        assertEquals(listOf(ArtworkKey.track("x", 256)), playlistThumbnails(emptyList(), "x", 256))
    }

    // ---- Transverse: the locator loads the pages up to the phone's position ----

    @Test
    fun `loadThrough reads the pages up to the index, false past the end`() = runTest {
        val total = 450
        val list = PagedList(Unit, backgroundScope, pageSize = 100) { _, offset, limit ->
            LibraryResult.Ok(Page((offset until minOf(offset + limit, total)).map { "i$it" }, total, offset, limit))
        }
        val reached = async { list.loadThrough(320) }.await()
        assertTrue(reached)
        assertEquals(400, list.state.value.items.size)
        assertFalse(async { list.loadThrough(1_000) }.await())
        assertEquals(total, list.state.value.items.size)
    }
}
