package app.n_zik.compagnon.bridge.pairing

import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/** In-memory [SecretStore] standing in for the Windows Credential Manager. */
class InMemorySecretStore(var secret: String? = null, private val writable: Boolean = true) : SecretStore {
    override fun read(): String? = secret
    override fun write(secret: String): Boolean {
        if (writable) this.secret = secret
        return writable
    }
    override fun delete() {
        secret = null
    }
}

class CredentialStoreTest {

    @TempDir
    lateinit var dir: Path

    private val token = "A".repeat(43)
    private val record = PairingRecord(
        serverIps = listOf("192.168.1.14"),
        serverPort = 42420,
        serverName = "Pixel 8",
        deviceId = "Ab3dE5fG7hI",
        deviceName = "PC-SALON",
    )

    private fun file() = dir.resolve("N-Zik Desktop Compagnon").resolve(CredentialStore.FILE_NAME)

    @Test
    fun `round trip`() {
        val secrets = InMemorySecretStore()
        assertTrue(CredentialStore(file(), secrets).save(StoredPairing(record, token)))
        assertEquals(StoredPairing(record, token), CredentialStore(file(), secrets).load())
    }

    @Test
    fun `token is never written to pairing json`() {
        val secrets = InMemorySecretStore()
        CredentialStore(file(), secrets).save(StoredPairing(record, token))
        val content = Files.readString(file())
        assertFalse(content.contains(token))
        assertFalse(content.contains("deviceToken"))
        assertEquals(token, secrets.secret)
        assertFalse(StoredPairing(record, token).toString().contains(token))
    }

    @Test
    fun `corrupt file reads as not paired`() {
        val secrets = InMemorySecretStore(token)
        Files.createDirectories(file().parent)
        Files.writeString(file(), "{ not json")
        assertNull(CredentialStore(file(), secrets).load())
    }

    @Test
    fun `invalid record reads as not paired`() {
        val secrets = InMemorySecretStore(token)
        Files.createDirectories(file().parent)
        Files.writeString(file(), """{"serverIps":[],"serverPort":42420,"serverName":"P","deviceId":"x","deviceName":"PC"}""")
        assertNull(CredentialStore(file(), secrets).load())
    }

    @Test
    fun `missing secret reads as not paired`() {
        val secrets = InMemorySecretStore()
        val store = CredentialStore(file(), secrets)
        store.save(StoredPairing(record, token))
        secrets.secret = null
        assertNull(store.load())
    }

    @Test
    fun `no file reads as not paired`() {
        assertNull(CredentialStore(file(), InMemorySecretStore(token)).load())
    }

    @Test
    fun `secret store refusing leaves nothing behind`() {
        val store = CredentialStore(file(), InMemorySecretStore(writable = false))
        assertFalse(store.save(StoredPairing(record, token)))
        assertFalse(Files.exists(file()))
        assertNull(store.load())
        assertFalse(CredentialStore(file(), UnsupportedSecretStore).save(StoredPairing(record, token)))
    }

    @Test
    fun `forget erases both halves`() {
        val secrets = InMemorySecretStore()
        val store = CredentialStore(file(), secrets)
        store.save(StoredPairing(record, token))
        store.clear()
        assertNull(secrets.secret)
        assertFalse(Files.exists(file()))
        assertNull(store.load())
    }

    @Test
    fun `selection follows the platform`() {
        assertTrue(CredentialStore.selectSecretStore("Windows 11") is WindowsCredentialSecretStore)
        val linuxStore = CredentialStore.selectSecretStore("LINUX")
        assertTrue(linuxStore is SessionSecretStore || linuxStore is LinuxSecretStore)
        assertTrue(CredentialStore.selectSecretStore("Mac OS X") === UnsupportedSecretStore)
    }

    @Test
    fun `isSessionOnly matches the default store`() {
        assertEquals(
            CredentialStore.defaultSecretStore() === SessionSecretStore,
            CredentialStore.isSessionOnly,
        )
    }

    @Test
    fun `edit IP keeps token and port`() {
        val secrets = InMemorySecretStore()
        val store = CredentialStore(file(), secrets)
        store.save(StoredPairing(record, token))
        val updated = store.updateServerIps(listOf("192.168.1.99"))
        assertEquals(listOf("192.168.1.99"), updated?.record?.serverIps)
        val reloaded = store.load()!!
        assertEquals(listOf("192.168.1.99"), reloaded.record.serverIps)
        assertEquals(42420, reloaded.record.serverPort)
        assertEquals(token, reloaded.deviceToken)
    }
}
