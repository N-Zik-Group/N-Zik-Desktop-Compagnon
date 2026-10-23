package app.n_zik.compagnon.bridge.pairing

import java.io.File
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
    fun `selectSecretStore carries the per-channel target name`() {
        // The 2026-10-16 storage regrouping: the secret store targets the per-channel STORAGE
        // name ([AppInfo.dataName] — the base name shared by stable AND beta; `… DEV` /

        // `… DEBUG` / `… GIT` for dev / debug / -git)
        val dev = CredentialStore.selectSecretStore("Windows 11", "N-Zik Desktop Compagnon DEV")
        assertTrue(dev is WindowsCredentialSecretStore)
        assertEquals("N-Zik Desktop Compagnon DEV", (dev as WindowsCredentialSecretStore).targetName)
        val default = CredentialStore.selectSecretStore("Windows 11")
        assertEquals("N-Zik Desktop Compagnon", (default as WindowsCredentialSecretStore).targetName)
    }

    @Test
    fun `the shared token is copied into the channel secret on first launch`() {
        // The channel separation, secret side: the pre-channel shared secret holds the
        // token — the channel build copies it into its OWN secret, and the shared entry SURVIVES

        // (it stays the live secret of the base-named channels — stable AND beta; dev / debug /
        // -git are isolated)
        val shared = InMemorySecretStore(token)
        val channel = InMemorySecretStore()

        CredentialStore.migrateSharedSecret(channel, shared)

        assertEquals(token, channel.secret, "the channel secret must receive the shared token")
        assertEquals(token, shared.secret, "the shared secret must SURVIVE (base-named channels still read it)")
    }

    @Test
    fun `the channel secret is never overwritten by a stale shared one`() {
        // A channel that already paired (its own token) must NOT be reset to the shared token by
        // a later launch
        val shared = InMemorySecretStore(token)
        val channel = InMemorySecretStore("B".repeat(43))

        CredentialStore.migrateSharedSecret(channel, shared)

        assertEquals("B".repeat(43), channel.secret, "the channel's own token must win")
        assertEquals(token, shared.secret)
    }

    @Test
    fun `no shared token leaves the channel secret untouched`() {
        val channel = InMemorySecretStore()

        CredentialStore.migrateSharedSecret(channel, InMemorySecretStore())

        assertNull(channel.secret, "nothing to copy: the channel secret stays empty")
    }

    @Test
    fun `isSessionOnly is the identity of the default store (source pin)`() {
        // Source pin (the repo idiom for wiring checks): the live `defaultSecretStore()` call
        // used to materialize the REAL platform store for the build channel HERE — on a paired

        // Windows machine every `gradlew build` (the default channel is debug) did a live
        // Credential Manager read + write of the real device token as an unasserted side effect

        // of the test run. The getter must stay the identity of the process-wide default store,
        // pinned without touching any OS state
        val sourceFile = File("src/main/kotlin/app/n_zik/compagnon/bridge/pairing/CredentialStore.kt")
        if (!sourceFile.isFile) error("the source pin needs $sourceFile — run the tests with the ComposeN-Zik module dir as the working directory (cwd=${File(".").absolutePath})")
        val source = sourceFile.readText()
        assertTrue(
            source.contains("get() = defaultStore === SessionSecretStore"),
            "isSessionOnly must be the identity of the default store",
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
