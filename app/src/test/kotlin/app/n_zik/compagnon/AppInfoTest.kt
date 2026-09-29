package app.n_zik.compagnon

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AppInfoTest {

    @Test
    fun `app name matches the repository product name`() {
        assertEquals("N-Zik Desktop Compagnon", AppInfo.NAME)
    }
}
