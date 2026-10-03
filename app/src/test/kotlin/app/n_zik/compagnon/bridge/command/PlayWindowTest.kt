package app.n_zik.compagnon.bridge.command

import kotlin.random.Random
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class PlayWindowTest {

    private fun ids(n: Int) = List(n) { "t$it" }

    @Test
    fun `click on the 3rd of 300 loaded tracks sends the 300 ids from index 2`() {
        val selection = PlayWindow.around(ids(300), 2)
        assertEquals(ids(300), selection.trackIds)
        assertEquals(2, selection.startIndex)
    }

    @Test
    fun `click on the 650th of 900 sends a window of 500 containing it`() {
        val all = ids(900)
        val selection = PlayWindow.around(all, 649)
        assertEquals(500, selection.trackIds.size)
        assertEquals("t649", selection.trackIds[selection.startIndex])
        // Contiguous slice of the list
        val start = all.indexOf(selection.trackIds.first())
        assertEquals(all.subList(start, start + 500), selection.trackIds)
    }

    @Test
    fun `window at the edges stays within the list`() {
        val all = ids(900)
        PlayWindow.around(all, 0).let {
            assertEquals(all.take(500), it.trackIds)
            assertEquals(0, it.startIndex)
        }
        PlayWindow.around(all, 899).let {
            assertEquals(all.takeLast(500), it.trackIds)
            assertEquals(499, it.startIndex)
        }
        PlayWindow.around(ids(500), 499).let {
            assertEquals(500, it.trackIds.size)
            assertEquals(499, it.startIndex)
        }
    }

    @Test
    fun `shuffle sends the same ids mixed, from index 0`() {
        val album = ids(12)
        val selection = PlayWindow.shuffled(album, Random(42))
        assertEquals(0, selection.startIndex)
        assertEquals(album.sorted(), selection.trackIds.sorted())
        assertNotEquals(album, selection.trackIds)
    }
}
