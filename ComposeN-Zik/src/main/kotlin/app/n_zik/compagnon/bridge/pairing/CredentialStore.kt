package app.n_zik.compagnon.bridge.pairing

import app.n_zik.compagnon.AppInfo
import app.n_zik.compagnon.generated.AppVersion
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

        /** `%APPDATA%\<per-channel product name>\pairing.json` (Windows) / `~/.config/<per-channel product name>/pairing.json` (Linux). */
        fun defaultPairingFile(): Path = appDirectory().resolve(FILE_NAME)

        /**
         * The app's data dir (the single convergence point — pairing, settings (`settings.json`),
         * audio cache (`cache/audio/`), updates and the MPRIS artwork all resolve from it):
         * `%APPDATA%\<per-channel name>\` on Windows, the XDG config dir (`$XDG_CONFIG_HOME` or
         * `~/.config`) + `<per-channel name>\` on Linux (the 2026-10-12 move off the bare home dir
         * — inside a Flatpak sandbox this is the per-app config dir the runtime maps under the
         * host's `~/.config/`), `~\<per-channel name>\` elsewhere. The name is PER CHANNEL
         * ([AppInfo.productName] — `N-Zik Desktop Compagnon DEV` / `… BETA`, the base name for
         * stable / debug / -git, the 2026-10-16 channel separation): each channel build keeps its
         * own pairing, settings and cache instead of sharing one dir across channels. Resolved
         * once and cached.
         */
        fun appDirectory(): Path = appDataDir

        private val appDataDir: Path by lazy {
            appDataDirFor(
                System.getProperty("os.name").orEmpty(),
                System.getenv("APPDATA"),
                System.getenv("XDG_CONFIG_HOME"),
                System.getProperty("user.home").orEmpty(),
                AppInfo.productName(AppVersion.channel),
                log,
            )
        }

        /**
         * The data dir + the one-time migrations from their inputs (pure — the tests pin the
         * wiring): [resolveAppDataDir] and, on Linux only, [migrateLegacyAppDataDir] of the legacy
         * `~/N-Zik Desktop Compagnon/` into the resolved dir, then — for a channel-suffixed name
         * only — [copySharedIntoChannel] of the pre-channel shared dir (the base name) into the
         * channel dir. Dropping a migration call (or gating it on the wrong OS) would strand the
         * data — every consumer would read the empty new dir (forced re-pair) with a green suite.
         */
        internal fun appDataDirFor(osName: String, appData: String?, xdgConfigHome: String?, userHome: String, dataDirName: String, log: Logger): Path {
            val dir = resolveAppDataDir(osName, appData, xdgConfigHome, userHome, dataDirName)
            if (isLinuxOs(osName)) {
                migrateLegacyAppDataDir(legacyAppDataDir(userHome), dir, log)
            }
            if (dataDirName != AppInfo.NAME) {
                copySharedIntoChannel(resolveAppDataDir(osName, appData, xdgConfigHome, userHome, AppInfo.NAME), dir, log)
            }
            return dir
        }

        /**
         * The data dir from its inputs (pure — the tests pin the matrix): Windows prefers the
         * `APPDATA` dir (blank/absent falls back to the home dir), Linux the XDG config dir
         * (the `XDG_CONFIG_HOME` override wins over `~/.config`), any other OS the home dir —
         * each under the per-channel [dataDirName] ([AppInfo.productName] of the build's channel).
         */
        internal fun resolveAppDataDir(osName: String, appData: String?, xdgConfigHome: String?, userHome: String, dataDirName: String): Path = when {
            osName.startsWith("Windows", ignoreCase = true) && appData?.isNotBlank() == true ->
                Paths.get(appData, dataDirName)
            osName.startsWith("Linux", ignoreCase = true) ->
                Paths.get(xdgConfigHome?.takeIf { it.isNotBlank() } ?: Paths.get(userHome, ".config").toString(), dataDirName)
            else -> Paths.get(userHome, dataDirName)
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
            copyMissingFilesInto(legacy, target, log)
            deleteTree(legacy)
            log.info("Merged the legacy app data dir $legacy into $target")
        }

        /**
         * The shared-copy core of both migrations: copies every file [source] has that [target]
         * lacks into [target] (the target's own files win — fresh app state is never clobbered).
         * Symlinks are recreated as links, never followed — a dangling (or cross-dir) target must
         * not abort the whole walk, stranding the migration on every launch.
         *
         * @return `true` when at least one file was copied
         */
        private fun copyMissingFilesInto(source: Path, target: Path, log: Logger): Boolean {
            var copied = false
            try {
                Files.walkFileTree(source, object : SimpleFileVisitor<Path>() {
                    override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                        val dest = target.resolve(source.relativize(file))
                        if (!Files.exists(dest)) {
                            dest.parent?.let { Files.createDirectories(it) }
                            if (Files.isSymbolicLink(file)) {
                                runCatching { Files.createSymbolicLink(dest, Files.readSymbolicLink(file)) }
                                    .onFailure { log.warning("Could not copy the symlink $file (${it::class.simpleName}) — skipped") }
                            } else {
                                Files.copy(file, dest)
                            }
                            copied = true
                        }
                        return FileVisitResult.CONTINUE
                    }
                })
            } catch (t: Throwable) {
                log.warning("Could not copy the app data files $source into $target (${t::class.simpleName})")
            }
            return copied
        }

        /**
         * The 2026-10-16 channel separation: copies the pre-channel SHARED data dir (the base
         * [AppInfo.NAME]) into the per-channel dir — the files the channel dir lacks only, the
         * channel dir's own files winning. Unlike [migrateLegacyAppDataDir] the shared source
         * SURVIVES: it stays the live data dir of the base-named channels (stable / debug / -git
         * keep [AppInfo.NAME]), so a side-by-side stable + dev install keeps its own data on its
         * own — the one-time cost is a copy, never a forced re-pair on either side.
         */
        internal fun copySharedIntoChannel(shared: Path, channel: Path, log: Logger) {
            if (!Files.isDirectory(shared) || shared == channel) return
            if (copyMissingFilesInto(shared, channel, log)) {
                log.info("Copied the shared app data dir $shared into the channel dir $channel")
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
         * can never disagree with the first. Since the 2026-10-16 channel separation it targets
         * the PER-CHANNEL secret name, and — for a channel-suffixed name — copies the device
         * token from the pre-channel shared secret once (see [migrateSharedSecret]).
         */
        private val defaultStore: SecretStore by lazy {
            val name = AppInfo.productName(AppVersion.channel)
            val osName = System.getProperty("os.name").orEmpty()
            val store = selectSecretStore(osName, name)
            if (name != AppInfo.NAME) {
                migrateSharedSecret(store, selectSecretStore(osName, AppInfo.NAME))
            }
            store
        }

        /**
         * The platform's secret store: the Credential Manager on Windows (a generic credential
         * named [targetName]), the keyring on Linux (the in-memory [SessionSecretStore] when no
         * keyring daemon is running), no storage anywhere else. [targetName] is the per-channel
         * secret name ([AppInfo.productName] of the build's channel; the base name is the
         * pre-channel default — what every stable / debug / -git build still uses).
         */
        internal fun selectSecretStore(osName: String, targetName: String = AppInfo.NAME): SecretStore = when {
            osName.startsWith("Windows", ignoreCase = true) -> WindowsCredentialSecretStore(targetName)
            osName.startsWith("Linux", ignoreCase = true) -> LinuxSecretStore.create(targetName) ?: SessionSecretStore
            else -> UnsupportedSecretStore
        }

        /**
         * The 2026-10-16 channel separation, secret side: copies the device token from the
         * pre-channel SHARED secret ([AppInfo.NAME] target) into the per-channel secret when the
         * channel secret does not exist yet. The shared entry SURVIVES — it stays the live
         * secret of the base-named channels (stable / debug / -git) — and an existing channel
         * token is never overwritten (a fresh channel pairing wins over a stale shared one).
         */
        internal fun migrateSharedSecret(channel: SecretStore, shared: SecretStore) {
            val token = shared.read() ?: return
            if (channel.read() == null) {
                channel.write(token)
            }
        }

        /** The token survives only for the session (Linux without a keyring daemon). */
        val isSessionOnly: Boolean
            get() = defaultStore === SessionSecretStore
    }
}
