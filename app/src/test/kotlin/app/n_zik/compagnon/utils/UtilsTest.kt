package app.n_zik.compagnon.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The phone's title-prefix helpers, ported for the queue, mini-player and player titles. */
class UtilsTest {

    @Test
    fun `the explicit prefix is detected alone and behind other prefixes`() {
        assertTrue("e:Title".hasExplicitPrefix())
        assertTrue("pinned:e:Title".hasExplicitPrefix())
        assertFalse("Title".hasExplicitPrefix())
        assertFalse("pinned:Title".hasExplicitPrefix())
        assertFalse("local:file".hasExplicitPrefix())
    }

    @Test
    fun `only the leading prefixes are stripped`() {
        assertEquals("Title", cleanPrefix("e:Title"))
        assertEquals("Title", cleanPrefix("pinned:e:Title"))
        assertEquals("Title:e:Title", cleanPrefix("e:Title:e:Title"))
        assertEquals("Title", cleanPrefix("Title"))
        assertEquals("", cleanPrefix("e:"))
    }
}
