package app.n_zik.compagnon.bridge.library

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SongsQueryTest {

    @Test
    fun `the text is trimmed, cut at 100 characters, null when blank`() {
        assertEquals("abc", SongsQuery.normalizeText("  abc "))
        assertNull(SongsQuery.normalizeText("   "))
        assertEquals(100, SongsQuery.normalizeText("x".repeat(150))!!.length)
    }

    @Test
    fun `a cut through a surrogate pair drops the lone high surrogate`() {
        // 99 letters then an emoji (2 chars): the 100-char cut would keep only its high surrogate
        val text = "x".repeat(99) + "\uD83C\uDFB5" + "y"
        val normalized = SongsQuery.normalizeText(text)!!
        assertEquals("x".repeat(99), normalized)
        assertEquals("x".repeat(98) + "\uD83C\uDFB5", SongsQuery.normalizeText("x".repeat(98) + "\uD83C\uDFB5" + "y"))
    }
}
