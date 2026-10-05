package app.n_zik.compagnon.bridge.pairing

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS

/**
 * Real keyring round trip on Linux (the Windows convention of a test-target that is always
 * cleaned up), skipped on machines without a Secret Service daemon, where the session fallback
 * is asserted instead.
 */
@EnabledOnOs(OS.LINUX)
class LinuxSecretStoreTest {

    private val store: LinuxSecretStore? = LinuxSecretStore.create()

    @AfterEach
    fun cleanUp() {
        store?.delete()
    }

    @Test
    fun `write, read, overwrite and delete a keyring item`() {
        assumeTrue(store != null, "no keyring daemon: the session fallback is exercised instead")
        val keyring = store!! // the assumeTrue above guarantees a keyring is available
        keyring.delete()
        assertNull(keyring.read())
        val token = "Ab-_".repeat(10) + "xyz"
        assertTrue(keyring.write(token))
        assertEquals(token, keyring.read())
        assertTrue(keyring.write("second"))
        assertEquals("second", keyring.read())
        keyring.delete()
        assertNull(keyring.read())
        keyring.delete() // deleting a missing item is harmless
    }

    @Test
    fun `without a keyring the platform store is the session fallback`() {
        assumeTrue(LinuxSecretStore.create() == null, "a keyring daemon is available")
        assertSame(SessionSecretStore, CredentialStore.selectSecretStore("Linux"))
    }
}
