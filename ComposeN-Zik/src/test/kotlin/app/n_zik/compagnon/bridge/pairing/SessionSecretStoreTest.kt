package app.n_zik.compagnon.bridge.pairing

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SessionSecretStoreTest {

    private val token = "B".repeat(43)

    @AfterEach
    fun tearDown() {
        SessionSecretStore.delete()
    }

    @Test
    fun `round trip then delete`() {
        assertNull(SessionSecretStore.read())
        assertTrue(SessionSecretStore.write(token))
        assertEquals(token, SessionSecretStore.read())
        SessionSecretStore.delete()
        assertNull(SessionSecretStore.read())
    }

    @Test
    fun `repeated writes keep the latest secret`() {
        assertTrue(SessionSecretStore.write(token))
        assertTrue(SessionSecretStore.write("C".repeat(43)))
        assertEquals("C".repeat(43), SessionSecretStore.read())
    }
}
