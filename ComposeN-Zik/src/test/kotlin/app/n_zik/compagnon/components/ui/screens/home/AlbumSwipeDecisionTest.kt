package app.n_zik.compagnon.components.ui.screens.home

import app.n_zik.compagnon.bridge.library.AlbumLike
import app.n_zik.compagnon.bridge.library.DislikeMode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * The Albums cards' bookmark swipe write ([albumSwipeDecision]): the three like states
 * (neutral / bookmarked / disliked) crossed with the three "disliked" modes — `null` (the
 * pre-1.7.2 display: the binary toggle through the phone's `bookmark` endpoint), a mode covering
 * the albums (the tri-state rotation through the `like` endpoint) and a mode off for the albums
 * (the binary toggle through the `like` endpoint).
 */
class AlbumSwipeDecisionTest {

    private val rotation = DislikeMode(albums = true)
    private val binary = DislikeMode(albums = false)

    // mode `null`: the binary toggle through the `bookmark` endpoint

    @Test
    fun `without the mode, a neutral album is bookmarked`() {
        assertEquals(AlbumSwipeDecision.Bookmark(true), albumSwipeDecision(false, false, null))
    }

    @Test
    fun `without the mode, a bookmarked album is un-bookmarked`() {
        assertEquals(AlbumSwipeDecision.Bookmark(false), albumSwipeDecision(false, true, null))
    }

    @Test
    fun `without the mode, a disliked album is bookmarked (the toggle from any other state)`() {
        assertEquals(AlbumSwipeDecision.Bookmark(true), albumSwipeDecision(true, false, null))
    }

    // mode covering the albums: the tri-state rotation through the `like` endpoint

    @Test
    fun `with the rotation, a neutral album becomes bookmarked`() {
        assertEquals(AlbumSwipeDecision.Like(AlbumLike.Bookmarked), albumSwipeDecision(false, false, rotation))
    }

    @Test
    fun `with the rotation, a bookmarked album becomes disliked`() {
        assertEquals(AlbumSwipeDecision.Like(AlbumLike.Disliked), albumSwipeDecision(false, true, rotation))
    }

    @Test
    fun `with the rotation, a disliked album becomes neutral`() {
        assertEquals(AlbumSwipeDecision.Like(AlbumLike.Neutral), albumSwipeDecision(true, false, rotation))
    }

    // mode off for the albums: the binary toggle through the `like` endpoint

    @Test
    fun `with the binary toggle, a neutral album becomes bookmarked`() {
        assertEquals(AlbumSwipeDecision.Like(AlbumLike.Bookmarked), albumSwipeDecision(false, false, binary))
    }

    @Test
    fun `with the binary toggle, a bookmarked album becomes neutral`() {
        assertEquals(AlbumSwipeDecision.Like(AlbumLike.Neutral), albumSwipeDecision(false, true, binary))
    }

    @Test
    fun `with the binary toggle, a disliked album becomes bookmarked`() {
        assertEquals(AlbumSwipeDecision.Like(AlbumLike.Bookmarked), albumSwipeDecision(true, false, binary))
    }
}
