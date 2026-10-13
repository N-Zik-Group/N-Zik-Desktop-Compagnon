package app.n_zik.compagnon.bridge.pairing

import app.n_zik.compagnon.AppInfo
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.FileVisitResult
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardCopyOption
import java.util.logging.Logger
import kotlinx.serialization.Serializable

/**
 * Holds the one secret of the pairing, the `deviceToken`. The platform implementations are the
 * Credential Manager on Windows ([WindowsCredentialSecretStore]) and the system keyring on Linux
 * ([LinuxSecretStore]), with an in-memory session fallback on keyring-less Linux
 * ([SessionSecretStore]); tests use an in-memory one.
 */
interface SecretStore {
    /** The stored secret, or `null` when absent or unreadable. */
    fun read(): String?

    /** `false` when the secret could not be stored (then nothing is paired). */
    fun write(secret: String): Boolean

    fun delete()
}

/** Off the supported platforms (Windows, Linux), no secret can be stored: pairing never persists. */
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

        /** `%APPDATA%\N-Zik Desktop Compagnon\pairing.json` (Windows) / `~/.config/N-Zik Desktop Compagnon/pairing.json` (Linux). */
        fun defaultPairingFile(): Path = appDirectory().resolve(FILE_NAME)

        /**
         * The app's data dir (the single convergence point — pairing, settings (`settings.json`),
         * audio cache (`cache/audio/`), updates and the MPRIS artwork all resolve from it):
         * `%APPDATA%\<name>\` on Windows, the XDG config dir (`$XDG_CONFIG_HOME` or `~/.config`)
         * + `<name>\` on Linux (the 2026-10-12 move off the bare home dir — inside a Flatpak
         * sandbox this is the per-app config dir the runtime maps under the host's `~/.config/`),
         * `~\<name>\` elsewhere. Resolved once and cached.
         */
        fun appDirectory(): Path = appDataDir

        private val appDataDir: Path by lazy {
            appDataDirFor(
                System.getProperty("os.name").orEmpty(),
                System.getenv("APPDATA"),
                System.getenv("XDG_CONFIG_HOME"),
                System.getProperty("user.home").orEmpty(),
                log,
            )
        }

        /**
         * The data dir + the one-time legacy migration from their inputs (pure — the tests pin the
         * wiring): [resolveAppDataDir] and, on Linux only, [migrateLegacyAppDataDir] of the legacy
         * `~/N-Zik Desktop Compagnon/` into the resolved dir. Dropping the migration call (or
         * gating it on the wrong OS) would strand the legacy dir — every consumer would read the
         * empty new dir (forced re-pair) with a green suite.
         */
        internal fun appDataDirFor(osName: String, appData: String?, xdgConfigHome: String?, userHome: String, log: Logger): Path {
            val dir = resolveAppDataDir(osName, appData, xdgConfigHome, userHome)
            if (isLinuxOs(osName)) {
                migrateLegacyAppDataDir(legacyAppDataDir(userHome), dir, log)
            }
            return dir
        }

        /**
         * The data dir from its inputs (pure — the tests pin the matrix): Windows prefers the
         * `APPDATA` dir (blank/absent falls back to the home dir), Linux the XDG config dir
         * (the `XDG_CONFIG_HOME` override wins over `~/.config`), any other OS the home dir.
         */
        internal fun resolveAppDataDir(osName: String, appData: String?, xdgConfigHome: String?, userHome: String): Path = when {
            osName.startsWith("Windows", ignoreCase = true) && appData?.isNotBlank() == true ->
                Paths.get(appData, AppInfo.NAME)
            osName.startsWith("Linux", ignoreCase = true) ->
                Paths.get(xdgConfigHome?.takeIf { it.isNotBlank() } ?: Paths.get(userHome, ".config").toString(), AppInfo.NAME)
            else -> Paths.get(userHome, AppInfo.NAME)
        }

        /** The pre-2026-10-12 Linux data dir (`~/N-Zik Desktop Compagnon/`) — the migration source. */
        private fun legacyAppDataDir(userHome: String): Path = Paths.get(userHome, AppInfo.NAME)

        private fun isLinuxOs(osName: String): Boolean = osName.startsWith("Linux", ignoreCase = true)

        private val log = Logger.getLogger("CredentialStore")

        /**
         * The one-time Linux migration of the legacy data dir into the XDG dir: when the new dir
         * does not exist yet the legacy dir is moved whole (atomic when the filesystem allows,
         * plain move otherwise, a copy when the dir is non-empty AND sits on another filesystem —
         * a cross-mount rename is impossible); when both exist (a partial first run with the new
         * code, then again with the old one) the legacy files the new dir does not have yet are
         * copied in, then the legacy dir is removed. Every failure only logs: the app continues
         * with the new dir — a failed migration costs a re-pair, never a crash (no-crash
         * constraint).
         */
        internal fun migrateLegacyAppDataDir(legacy: Path, target: Path, log: Logger) {
            if (!Files.isDirectory(legacy) || legacy == target) return
            if (Files.exists(target)) {
                mergeLegacyInto(legacy, target, log)
                return
            }
            runCatching {
                target.parent?.let { Files.createDirectories(it) } // the move creates no parents
                try {
                    Files.move(legacy, target, StandardCopyOption.ATOMIC_MOVE)
                } catch (_: AtomicMoveNotSupportedException) {
                    Files.move(legacy, target)
                }
                log.info("Migrated the legacy app data dir $legacy to $target")
            }.onFailure { first ->
                // A cross-filesystem move of a NON-EMPTY dir fails where the rename cannot cross
                // the mount (DirectoryNotEmptyException — plain `Files.move` included): fall back
                // to the copy + merge routine, which works on any filesystem
                log.warning("Could not move the legacy app data dir $legacy to $target (${first::class.simpleName}) — falling back to a copy")
                runCatching { mergeLegacyInto(legacy, target, log) }
                    .onFailure { t -> log.warning("Could not migrate the legacy app data dir $legacy to $target (${t::class.simpleName})") }
            }
        }

        /** Copies the legacy files the target lacks into [target], then removes [legacy] (the merge branch). */
        private fun mergeLegacyInto(legacy: Path, target: Path, log: Logger) {
            try {
                Files.walkFileTree(legacy, object : SimpleFileVisitor<Path>() {
                    override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                        val dest = target.resolve(legacy.relativize(file))
                        if (!Files.exists(dest)) {
                            dest.parent?.let { Files.createDirectories(it) }
                            if (Files.isSymbolicLink(file)) {
                                // A symlink: recreate the LINK itself at the destination, never
                                // follow it — a plain `Files.copy` follows, and a dangling (or
                                // cross-dir) target would throw and abort the whole walk,
                                // stranding the legacy dir (and the merge) on every launch
                                runCatching { Files.createSymbolicLink(dest, Files.readSymbolicLink(file)) }
                                    .onFailure { log.warning("Could not copy the symlink $file (${it::class.simpleName}) — skipped") }
                            } else {
                                Files.copy(file, dest)
                            }
                        }
                        return FileVisitResult.CONTINUE
                    }
                })
                deleteTree(legacy)
                log.info("Merged the legacy app data dir $legacy into $target")
            } catch (t: Throwable) {
                log.warning("Could not merge the legacy app data dir $legacy into $target (${t::class.simpleName})")
            }
        }

        private fun deleteTree(dir: Path) {
            Files.walkFileTree(dir, object : SimpleFileVisitor<Path>() {
                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    Files.deleteIfExists(file)
                    return FileVisitResult.CONTINUE
                }

                override fun postVisitDirectory(directory: Path, exc: IOException?): FileVisitResult {
                    if (exc == null) Files.deleteIfExists(directory)
                    return FileVisitResult.CONTINUE
                }
            })
        }

        fun defaultSecretStore(): SecretStore = defaultStore

        /**
         * The one platform store for this process: probed once (the Linux probe is a D-Bus call)
         * and shared by every [CredentialStore] and the [isSessionOnly] check, so a second probe
         * can never disagree with the first.
         */
        private val defaultStore: SecretStore by lazy {
            selectSecretStore(System.getProperty("os.name").orEmpty())
        }

        /**
         * The platform's secret store: the Credential Manager on Windows, the keyring on Linux
         * (the in-memory [SessionSecretStore] when no keyring daemon is running), no storage
         * anywhere else.
         */
        internal fun selectSecretStore(osName: String): SecretStore = when {
            osName.startsWith("Windows", ignoreCase = true) -> WindowsCredentialSecretStore(AppInfo.NAME)
            osName.startsWith("Linux", ignoreCase = true) -> LinuxSecretStore.create() ?: SessionSecretStore
            else -> UnsupportedSecretStore
        }

        /** The token survives only for the session (Linux without a keyring daemon). */
        val isSessionOnly: Boolean
            get() = defaultStore === SessionSecretStore
    }
}
