package app.n_zik.compagnon.components.player

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * The player sheet's hand-over curves (port of the phone's `miniPlayerFade` / `playerContentFade`): the
 * mini-player is fully faded out exactly when the player content starts fading in — no pop, no overlap,
 * no empty gap.
 */
class PlayerSheetTest {

    @Test
    fun `the mini-player is fully faded out when the player fade-in starts`() {
        assertEquals(1f, miniPlayerFade(0f))
        // A float delta: the hand-over midpoint is not exactly representable
        assertEquals(0.5f, miniPlayerFade(0.225f), 1e-4f)
        assertEquals(0f, miniPlayerFade(PLAYER_SHEET_HANDOVER_PROGRESS))
        assertEquals(0f, miniPlayerFade(1f))
    }

    @Test
    fun `the player content is fully visible only at progress 1`() {
        assertEquals(0f, playerContentFade(0f))
        assertEquals(0f, playerContentFade(PLAYER_SHEET_HANDOVER_PROGRESS))
        assertEquals(0.5f, playerContentFade(0.725f), 1e-4f)
        assertEquals(1f, playerContentFade(1f))
    }

    @Test
    fun `the card grows from the collapsed card to the full screen`() {
        val collapsed = cardGeometry(0f, 800f, 600f, 72f, 16f, 25f, 28f)
        assertEquals(16f, collapsed.left)
        assertEquals(768f, collapsed.width)
        assertEquals(72f, collapsed.height)
        assertEquals(25f, collapsed.cornerPx)

        val expanded = cardGeometry(1f, 800f, 600f, 72f, 16f, 25f, 28f)
        assertEquals(0f, expanded.left)
        assertEquals(800f, expanded.width)
        assertEquals(600f, expanded.height)
        assertEquals(0f, expanded.cornerPx)
    }
}
