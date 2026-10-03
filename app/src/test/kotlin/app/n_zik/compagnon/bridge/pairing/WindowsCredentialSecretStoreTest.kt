package app.n_zik.compagnon.bridge.pairing

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS

/** Real Credential Manager round trip, under a test-only target that is always removed. */
@EnabledOnOs(OS.WINDOWS)
class WindowsCredentialSecretStoreTest {

    private val store = WindowsCredentialSecretStore("N-Zik Desktop Compagnon (unit test)")

    @AfterEach
    fun cleanUp() = store.delete()

    @Test
    fun `write, read, overwrite and delete a generic credential`() {
        store.delete()
        assertNull(store.read())
        val token = "Ab-_".repeat(10) + "xyz"
        assertTrue(store.write(token))
        assertEquals(token, store.read())
        assertTrue(store.write("second"))
        assertEquals("second", store.read())
        store.delete()
        assertNull(store.read())
        store.delete() // deleting a missing credential is harmless
    }
}
