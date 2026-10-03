package app.n_zik.compagnon.bridge.pairing

import app.n_zik.compagnon.AppInfo
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.logging.Logger
import kotlinx.serialization.Serializable

/**
 * Holds the one secret of the pairing, the `deviceToken`. The Windows implementation is the
 * Credential Manager ([WindowsCredentialSecretStore]); tests use an in-memory one.
 */
interface SecretStore {
    /** The stored secret, or `null` when absent or unreadable. */
    fun read(): String?

    /** `false` when the secret could not be stored (then nothing is paired). */
    fun write(secret: String): Boolean

    fun delete()
}

/** Off Windows (outside the target), no secret can be stored: pairing never persists. */
object UnsupportedSecretStore : SecretStore {
    override fun read(): String? = null
    override fun write(secret: String): Boolean = false
    override fun delete() = Unit
}

/** Non-secret pairing fields, the content of `pairing.json` (contract §12). The token is never here. */
@Serializable
data class PairingRecord(
    val serverIps: List<String>,
    val serverPort: Int,
    val serverName: String,
    val deviceId: String,
    val deviceName: String,
)

/** A complete pairing: [record] from the file, [deviceToken] from the [SecretStore]. */
data class StoredPairing(val record: PairingRecord, val deviceToken: String) {
    /** Never prints the token. */
    override fun toString(): String = "StoredPairing(record=$record, deviceToken=***)"
}

/**
 * Pairing persistence (contract §12, story 10): the token in the [SecretStore], the other fields in
 * [file]. A missing or unreadable half, or a corrupt file, reads as "not paired", never as a crash.
 */
class CredentialStore(
    private val file: Path = defaultPairingFile(),
    private val secrets: SecretStore = defaultSecretStore(),
) {
    private val log = Logger.getLogger("CredentialStore")

    fun load(): StoredPairing? {
        if (!Files.exists(file)) return null
        val record = runCatching {
            BridgeJson.decodeFromString(PairingRecord.serializer(), Files.readString(file, Charsets.UTF_8))
        }.getOrNull()?.takeIf(::isValid)
        if (record == null) {
            log.warning("pairing.json is unreadable or invalid: treated as not paired")
            return null
        }
        val token = runCatching { secrets.read() }.getOrNull()?.takeIf { it.isNotBlank() }
        if (token == null) {
            log.warning("Device token missing from the secret store: treated as not paired")
            return null
        }
        return StoredPairing(record, token)
    }

    /** Stores both halves; on any failure, nothing stays behind and `false` is returned. */
    fun save(pairing: StoredPairing): Boolean {
        val stored = runCatching { secrets.write(pairing.deviceToken) }.getOrDefault(false)
        if (!stored) {
            log.warning("Could not store the device token in the secret store")
            return false
        }
        return runCatching { writeRecord(pairing.record) }
            .onFailure {
                log.warning("Could not write pairing.json: ${it::class.simpleName}")
                clear()
            }
            .isSuccess
    }

    /** Replaces the phone's IP only, keeping token and port ("phone unreachable" → "Edit IP", contract §4.7). */
    fun updateServerIps(serverIps: List<String>): StoredPairing? {
        val current = load() ?: return null
        val updated = current.copy(record = current.record.copy(serverIps = serverIps))
        return runCatching { writeRecord(updated.record); updated }
            .onFailure { log.warning("Could not update pairing.json: ${it::class.simpleName}") }
            .getOrNull()
    }

    /** "Forget this phone" and revocation: both halves erased. */
    fun clear() {
        runCatching { secrets.delete() }.onFailure { log.warning("Could not delete the device token: ${it::class.simpleName}") }
        runCatching { Files.deleteIfExists(file) }.onFailure { log.warning("Could not delete pairing.json: ${it::class.simpleName}") }
    }

    private fun writeRecord(record: PairingRecord) {
        file.parent?.let { Files.createDirectories(it) }
        val tmp = file.resolveSibling("${file.fileName}.tmp")
        Files.writeString(tmp, BridgeJson.encodeToString(PairingRecord.serializer(), record), Charsets.UTF_8)
        try {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun isValid(record: PairingRecord): Boolean =
        record.serverIps.isNotEmpty() && record.serverIps.all(PairingRules::isIpv4) &&
            PairingRules.isValidPort(record.serverPort) &&
            record.deviceId.isNotBlank() &&
            PairingRules.normalizeDeviceName(record.deviceName) != null

    companion object {
        const val FILE_NAME = "pairing.json"

        /** `%APPDATA%\N-Zik Desktop Compagnon\pairing.json`. */
        fun defaultPairingFile(): Path = appDirectory().resolve(FILE_NAME)

        /** `%APPDATA%\N-Zik Desktop Compagnon\`: pairing, settings (`settings.json`) and audio cache (`cache\audio\`). */
        fun appDirectory(): Path {
            val base = System.getenv("APPDATA")?.takeIf { it.isNotBlank() } ?: System.getProperty("user.home")
            return Paths.get(base, AppInfo.NAME)
        }

        fun defaultSecretStore(): SecretStore =
            if (System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)) {
                WindowsCredentialSecretStore(AppInfo.NAME)
            } else {
                UnsupportedSecretStore
            }
    }
}
