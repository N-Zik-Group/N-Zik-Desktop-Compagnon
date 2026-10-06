package app.n_zik.compagnon.bridge.pairing

import com.sun.jna.NativeLibrary
import java.util.logging.Handler
import java.util.logging.LogRecord
import java.util.logging.Logger
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

    /**
     * Regression guard for a JNA argument-order bug: JNA's two-argument `Function.getFunction`
     * takes the library name first, so the glib symbol `g_str_hash` was requested as a library
     * (`libg_str_hash.so`) and `create()` silently downgraded to the session fallback on every
     * machine. When the glib/libsecret runtimes are installed, `create()` must not end in a
     * native link error (a "daemon unreachable" result is the legitimate null path).
     */
    @Test
    fun `the keyring stack does not fail to load when the native runtimes are present`() {
        assumeTrue(
            runCatching { NativeLibrary.getInstance("glib-2.0") }.isSuccess &&
                runCatching { NativeLibrary.getInstance("secret-1") }.isSuccess,
            "glib/libsecret runtime not installed on this machine",
        )
        val captured = mutableListOf<LogRecord>()
        val handler = object : Handler() {
            override fun publish(record: LogRecord) {
                captured.add(record)
            }

            override fun flush() {}

            override fun close() {}
        }
        val logger = Logger.getLogger("LinuxSecretStore")
        logger.addHandler(handler)
        try {
            LinuxSecretStore.create()
        } finally {
            logger.removeHandler(handler)
        }
        // Any exception out of create() is logged as a "Keyring unavailable" warning (a daemon
        // that is merely unreachable is the legitimate INFO/null path, not a warning).
        val warnings = captured
            .filter { it.level.name == "WARNING" }
            .joinToString("\n") { it.message.orEmpty() }
        assertTrue(
            !warnings.contains("Keyring unavailable"),
            "create() threw when the native runtimes are present:\n$warnings",
        )
    }
}
