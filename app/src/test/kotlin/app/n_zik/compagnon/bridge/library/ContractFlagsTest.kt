package app.n_zik.compagnon.bridge.library

import app.n_zik.compagnon.bridge.pairing.BridgeJson
import app.n_zik.compagnon.bridge.state.ServerMessages
import app.n_zik.compagnon.bridge.state.SnapshotMessage
import app.n_zik.compagnon.bridge.state.Track
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Contract 1.3 `Track.isExplicit`, `Album.isBookmarked`, `Artist.isBookmarked`, with the 1.2 tolerance. */
class ContractFlagsTest {

    @Test
    fun `a 1_3 track carries isExplicit and a cleaned title`() {
        val track = BridgeJson.decodeFromString(Track.serializer(), """{"id":"a","title":"Song","isExplicit":true}""")
        assertTrue(track.isExplicit)
        assertEquals("Song", track.title)
    }

    @Test
    fun `a 1_2 track without isExplicit reads as not explicit`() {
        val track = BridgeJson.decodeFromString(Track.serializer(), """{"id":"a","title":"Song"}""")
        assertFalse(track.isExplicit)
    }

    @Test
    fun `the queue of a snapshot keeps isExplicit`() {
        val message = ServerMessages.decode(
            """{"type":"snapshot","revision":1,"serverTimeMs":0,"queue":[{"id":"a","isExplicit":true},{"id":"b"}]}""",
        )
        val snapshot = message as SnapshotMessage
        assertEquals(listOf(true, false), snapshot.queue.map { it.isExplicit })
    }

    @Test
    fun `albums and artists read isBookmarked, absent as false`() {
        val albums = BridgeJson.decodeFromString(
            Page.serializer(Album.serializer()),
            """{"items":[{"id":"x","isBookmarked":true},{"id":"y"}],"total":2,"offset":0,"limit":50}""",
        )
        assertEquals(listOf(true, false), albums.items.map { it.isBookmarked })
        val artists = BridgeJson.decodeFromString(
            Page.serializer(Artist.serializer()),
            """{"items":[{"id":"x"},{"id":"y","isBookmarked":true}],"total":2,"offset":0,"limit":50}""",
        )
        assertEquals(listOf(false, true), artists.items.map { it.isBookmarked })
    }
}
