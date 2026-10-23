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

        /** `pairing.json` under the app data dir ([appDirectory] — the per-channel storage dir, or `<root>/data/` of a self-contained portable). */
        fun defaultPairingFile(): Path = appDirectory().resolve(FILE_NAME)

        /**
         * The app's data dir (the single convergence point — pairing, settings (`settings.json`),
         * audio cache (`cache/audio/`), updates and the MPRIS artwork all resolve from it):
         * `%APPDATA%\<storage name>\` on Windows, the XDG config dir (`$XDG_CONFIG_HOME` or
         * `~/.config`) + `<storage name>\` on Linux (the 2026-10-12 move off the bare home dir
         * — inside a Flatpak sandbox this is the per-app config dir the runtime maps under the
         * host's `~/.config/`), `~\<storage name>\` elsewhere. The name is the per-channel
         * STORAGE name ([AppInfo.dataName] — the base name, shared by stable AND beta,
         * `N-Zik Desktop Compagnon DEV` / `… DEBUG` / `… GIT` for dev / debug / -git — the
         * 2026-10-16 storage regrouping): the isolated channels keep their own pairing, settings
         * and cache, stable and beta share one dir (the beta tests against the stable user's
         * real data). A SELF-CONTAINED PORTABLE (the `portable.txt` marker at the app root,
         * written only by the portable archive tasks) instead stores its data in
         * `<root>/data/` next to the launcher ([portableDataRootOf]) — the device token STILL
         * lives in the OS secret store, never in the portable dir. Resolved once and cached.
         */
        fun appDirectory(): Path = appDataDir

        private val appDataDir: Path by lazy {
            val osName = System.getProperty("os.name").orEmpty()
            val appData = System.getenv("APPDATA")
            val xdgConfigHome = System.getenv("XDG_CONFIG_HOME")
            val home = System.getProperty("user.home").orEmpty()
            val channel = AppVersion.channel
            // The self-contained-portable decision wins over the per-channel dir (spec
            // `spec-channel-storage-portable-targz`)

            // The marker is the sole signal, and every resolution failure degrades to the
            // per-channel dir below (safe — no data lost)
            portableDataRootOf(osName, System.getenv(), liveExecutablePath(), AppInfo.productName(channel))
                ?.let { portableRoot ->
                    val portableDir = portableRoot.resolve(PORTABLE_DATA_DIR_NAME)
                    // The one-time copy from the base data dir (same semantics as the channel
                    // copy: the base's files the portable dir lacks, once)

                    // The walk is skipped on re-launch: the portable dir owns its state
                    copySharedIntoChannel(
                        resolveAppDataDir(osName, appData, xdgConfigHome, home, AppInfo.NAME),
                        portableDir,
                        log,
                    )
                    portableDir
                }
                ?: appDataDirFor(
                    osName,
                    appData,
                    xdgConfigHome,
                    home,
                    AppInfo.dataName(channel),
                    legacyDataDirNamesFor(channel),
                    log,
                )
        }

        /**
         * The data dir + the one-time migrations from their inputs (pure — the tests pin the
         * wiring): [resolveAppDataDir] and, on Linux for the BASE name only, [migrateLegacyAppDataDir]
         * of the legacy `~/N-Zik Desktop Compagnon/` into the base dir (a channel-suffixed build
         * must not move it into its own dir — that would strand stable / beta: the data stranded
         * in the channel dir, forced re-pair on the base), then — for a channel-suffixed name
         * only — [copySharedIntoChannel] of the pre-channel shared dir (the base name) into the
         * channel dir, then — for the legacy per-channel dir names only —
         * [mergeLegacyChannelDirInto] of the dirs once isolated by the 2026-10-16 channel
         * separation (the legacy beta `… BETA`) back into the dir (the base wins, the legacy
         * dir SURVIVES). Dropping a migration call (or gating it on the wrong OS / name) would
         * strand the data — every consumer would read the empty new dir (forced re-pair) with a
         * green suite.
         *
         * @param legacyDataDirNames the storage names once isolated for this channel, now
         * merged back into [dataDirName] (production: beta → [`$NAME BETA`], the 2026-10-16
         * isolated-beta dir; every other channel → none)
         */
        internal fun appDataDirFor(osName: String, appData: String?, xdgConfigHome: String?, userHome: String, dataDirName: String, legacyDataDirNames: List<String>, log: Logger): Path {
            val dir = resolveAppDataDir(osName, appData, xdgConfigHome, userHome, dataDirName)
            if (isLinuxOs(osName) && dataDirName == AppInfo.NAME) {
                // The BASE dir only: the legacy dir is the pre-channel home of the data, and a
                // channel-suffixed build launching first must not move it into its own dir

                // The base channels would then read an empty dir — forced re-pair, the data
                // stranded in the channel dir

                // The channel build picks the data up through the base → channel copy below,
                // once the base has it
                migrateLegacyAppDataDir(legacyAppDataDir(userHome), dir, log)
            }
            if (dataDirName != AppInfo.NAME) {
                copySharedIntoChannel(resolveAppDataDir(osName, appData, xdgConfigHome, userHome, AppInfo.NAME), dir, log)
            }
            for (legacyName in legacyDataDirNames) {
                if (legacyName != dataDirName) {
                    mergeLegacyChannelDirInto(resolveAppDataDir(osName, appData, xdgConfigHome, userHome, legacyName), dir, log)
                }
            }
            return dir
        }

        /**
         * The data dir from its inputs (pure — the tests pin the matrix): Windows prefers the
         * `APPDATA` dir (blank/absent falls back to the home dir), Linux the XDG config dir
         * (the `XDG_CONFIG_HOME` override wins over `~/.config`), any other OS the home dir —
         * each under [dataDirName] ([AppInfo.dataName] of the build's channel — the storage
         * regrouping: base name for stable / beta, the `… DEV` / `… DEBUG` / `… GIT` suffix for
         * the isolated channels).
         */
        internal fun resolveAppDataDir(osName: String, appData: String?, xdgConfigHome: String?, userHome: String, dataDirName: String): Path = when {
            osName.startsWith("Windows", ignoreCase = true) && appData?.isNotBlank() == true ->
                Paths.get(appData, dataDirName)
            osName.startsWith("Linux", ignoreCase = true) ->
                Paths.get(xdgConfigHome?.takeIf { it.isNotBlank() } ?: Paths.get(userHome, ".config").toString(), dataDirName)
            else -> Paths.get(userHome, dataDirName)
        }

        /** The self-contained-portable marker file (written as an archive entry ONLY by the two portable build tasks). */
        const val PORTABLE_MARKER_FILE = "portable.txt"

        /** The EXACT content of [PORTABLE_MARKER_FILE] (the strict reader: any other content = no marker). */
        const val PORTABLE_MARKER_CONTENT = "portable"

        /** The data dir name inside a portable root (the portable's data lives in `<root>/data/`). */
        const val PORTABLE_DATA_DIR_NAME = "data"

        /**
         * The storage names once isolated for this channel, now merged back into its live dir
         * (the 2026-10-16 storage regrouping): only beta had a per-channel dir before the
         * regrouping — the 2026-10-16 isolated-beta `… BETA` (the install identity,
         * [AppInfo.productName] of beta). Beta now SHARES the base with stable, so the beta
         * build merges those legacy files back into the base dir once (the base wins, the
         * `… BETA` dir SURVIVES — never deleted). Every other channel never had a legacy dir of
         * its own.
         */
        internal fun legacyDataDirNamesFor(channel: String): List<String> =
            if (channel == "beta") listOf(AppInfo.productName("beta")) else emptyList()

        /**
         * The self-contained-portable decision (pure — the tests pin the matrix): the app root
         * of a self-contained portable distribution, or `null` (→ the data dir stays the
         * per-channel one — the safe degradation: no marker, no data lost).
         *
         * The marker is [PORTABLE_MARKER_FILE] at the app root with the EXACT content
         * [PORTABLE_MARKER_CONTENT] (the strict reader: any other content counts as no marker —
         * a stray or forged marker must not relocate the data dir). The marker is written as an
         * archive entry ONLY by the two portable build tasks, so it can exist in a real
         * portable distribution only (never in the shared app-image, the NSIS installer, the
         * Flatpak, `packageArch` or a run-source launch).
         *
         * The exclusions (each degrades to `null` → per-channel dir):
         *  * the executable path is blank (no launch context to resolve);
         *  * the Flatpak environment (a non-blank `FLATPAK_ID` or `container=flatpak` — the
         *    sandbox data dir is the runtime-mapped per-app config dir, never a portable one);
         *  * the package-managed install prefix (`/opt/n-zik-desktop-compagnon` — the
         *    .deb/.rpm/Arch/AUR layout, whose data lives in the XDG dir);
         *  * the NSIS Windows install (the launcher folder carries the uninstaller
         *    `Uninstall <product>.exe` — the same discriminator the updater's
         *    `isNsisWindowsInstall` uses, DUPLICATED here by documentation: the `bridge/` layer
         *    must not import the `updater/` layer).
         *
         * The app root (mirroring the updater's layout semantics, duplicated by documentation —
         * the updater is NOT imported): on Windows the launcher sits at the image ROOT (the
         * executable's own dir); on Linux the launcher lives at `<root>/bin/<launcher>` (the
         * parent dir named `bin` → its parent IS the app root); any other OS → `null`.
         *
         * @param env the process environment (`System.getenv()` — the Flatpak signals)
         * @param executablePath the RESOLVED path of the running executable ([liveExecutablePath]
         * in production)
         * @param windowsProductName the per-channel product name of this build (the NSIS
         * uninstaller is named after it)
         */
        internal fun portableDataRootOf(osName: String, env: Map<String, String>, executablePath: String, windowsProductName: String): Path? {
            if (executablePath.isBlank()) return null
            // The Flatpak sandbox (the same signals the updater's detection reads)
            if (env["FLATPAK_ID"]?.isNotBlank() == true || env["container"] == "flatpak") return null
            // The package-managed install (the same prefix rule the updater's detection reads)
            if (executablePath.startsWith("/opt/n-zik-desktop-compagnon", ignoreCase = true)) return null
            val exe = runCatching { Paths.get(executablePath).toAbsolutePath().normalize() }.getOrNull() ?: return null
            val appRoot = when {
                osName.startsWith("Windows", ignoreCase = true) -> exe.parent
                osName.startsWith("Linux", ignoreCase = true) ->
                    exe.parent?.takeIf { it.fileName?.toString()?.equals("bin", ignoreCase = true) == true }?.parent
                else -> null
            } ?: return null
            // The NSIS Windows install (the same uninstaller-file discriminator the updater uses —
            // duplicated here: bridge/ must not import updater/)
            if (Files.exists(exe.resolveSibling("Uninstall $windowsProductName.exe"))) return null
            val marker = appRoot.resolve(PORTABLE_MARKER_FILE)
            if (!Files.isRegularFile(marker)) return null
            val content = runCatching { Files.readString(marker, Charsets.UTF_8) }.getOrNull() ?: return null
            return if (content == PORTABLE_MARKER_CONTENT) appRoot else null
        }

        /**
         * The live executable path of this process, RESOLVED (the same semantics as the updater's
         * `liveExecutablePath`, duplicated here by documentation — the `bridge/` layer must not
         * import the `updater/` layer): `ProcessHandle.command()` is argv[0] UNRESOLVED; on Linux
         * the kernel-resolved `/proc/self/exe` is authoritative (its `toRealPath()` walks the
         * full chain, the binary itself included). Any failure degrades silently to the
         * command-line path — the portable decision then simply finds no marker (per-channel
         * dir, no data lost).
         */
        private fun liveExecutablePath(): String = commandLinePath().let { fallback ->
            val procSelfExe = Paths.get("/proc/self/exe")
            if (Files.isSymbolicLink(procSelfExe)) {
                runCatching { procSelfExe.toRealPath().toString() }.getOrNull() ?: fallback
            } else {
                fallback
            }
        }

        private fun commandLinePath(): String =
            runCatching { ProcessHandle.current().info().command().orElse("") }.getOrDefault("")

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
                // the mount (DirectoryNotEmptyException — plain `Files.move` included)

                // Fall back to the copy + merge routine, which works on any filesystem
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
         * The copy is FILE-level: empty directories are not reproduced (a dir appears in the
         * target only because a file inside it is copied — the consumers create their dirs on
         * write). Symlinks are recreated as links, never followed — a dangling (or cross-dir)
         * target must not abort the whole walk, stranding the migration on every launch.
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
         * The channel-separation copy (2026-10-16): the ONE-TIME copy of the pre-channel SHARED
         * data dir (the base [AppInfo.NAME]) into the per-channel dir on first launch — the files
         * the channel dir lacks, the channel dir's own files winning. Once the channel dir
         * carries its own files the walk is skipped entirely (the channels are isolated after
         * first launch: the base's later files are the base's own, never channel data — and the
         * per-launch re-walk would cost a full stat per base file, visible on a portable's USB
         * stick with a large audio cache). Also the one-time copy into a self-contained
         * portable's `<root>/data/` dir (same semantics). Unlike [migrateLegacyAppDataDir] the
         * shared source SURVIVES: it stays the live data dir of the base-named channels (stable
         * AND beta keep [AppInfo.NAME] after the 2026-10-16 storage regrouping; dev / debug /
         * -git are isolated as `… DEV` / `… DEBUG` / `… GIT`), so a side-by-side stable + dev
         * install keeps its own data on its own — the one-time cost is a copy, never a forced
         * re-pair on either side.
         */
        internal fun copySharedIntoChannel(shared: Path, channel: Path, log: Logger) {
            if (!Files.isDirectory(shared) || shared == channel) return
            // The one-time skip: the walk runs on a channel dir that carries no files of its own
            // (an absent or file-less dir — a wiped channel dir, or a first copy that died partway

            // having only created subdirs, recovers the base files on the next launch)

            // A subdir alone is NOT "initialized": the walk creates dirs only as parents of
            // copied files, so a file-less dir is by definition a dir the walk never finished
            val channelFiles = runCatching { channel.toFile().listFiles() }.getOrNull()
            if (Files.isDirectory(channel) && channelFiles.orEmpty().any { it.isFile }) return
            if (copyMissingFilesInto(shared, channel, log)) {
                log.info("Copied the shared app data dir $shared into the channel dir $channel (the one-time copy)")
            }
        }

        /**
         * The 2026-10-16 storage regrouping, legacy-beta side: merges the pre-regrouping
         * PER-CHANNEL dir (the 2026-10-16 isolated-beta `… BETA`) into the channel's live dir —
         * the files the live dir lacks only, the live dir's own files winning (the base wins).
         * Unlike [migrateLegacyAppDataDir] the legacy source SURVIVES: it is a MERGE-BACK ONLY —
         * the `… BETA` dir is never deleted (a copy, never a move: the pairing can never be
         * lost, and the dir may still be referenced by the user or by an older launch path).
         */
        internal fun mergeLegacyChannelDirInto(legacy: Path, target: Path, log: Logger) {
            if (!Files.isDirectory(legacy) || legacy == target) return
            if (copyMissingFilesInto(legacy, target, log)) {
                log.info("Merged the legacy channel data dir $legacy into $target (the base files win, $legacy survives)")
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
         * can never disagree with the first. Since the 2026-10-16 storage regrouping it targets
         * the per-channel STORAGE name ([AppInfo.dataName] — the base name, shared by stable AND
         * beta), and — for a channel-suffixed name whose channel secret does not exist yet —
         * copies the device token from the pre-channel shared secret once (the shared store is
         * constructed only in that case — see [migrateSharedSecret]).
         */
        private val defaultStore: SecretStore by lazy {
            val name = AppInfo.dataName(AppVersion.channel)
            val osName = System.getProperty("os.name").orEmpty()
            val store = selectSecretStore(osName, name)
            if (name != AppInfo.NAME && store.read() == null) {
                // The channel secret already exists → nothing to migrate: the shared store is
                // never constructed

                // An isolated-channel Linux build would otherwise pay the JNA load + the D-Bus
                // probe + a lookup on EVERY launch
                migrateSharedSecret(store, selectSecretStore(osName, AppInfo.NAME))
            }
            store
        }

        /**
         * The platform's secret store: the Credential Manager on Windows (a generic credential
         * named [targetName]), the keyring on Linux (the in-memory [SessionSecretStore] when no
         * keyring daemon is running), no storage anywhere else. [targetName] is the per-channel
         * storage name ([AppInfo.dataName] of the build's channel: the base name is shared by
         * stable AND beta; dev / debug / -git use `… DEV` / `… DEBUG` / `… GIT`).
         */
        internal fun selectSecretStore(osName: String, targetName: String = AppInfo.NAME): SecretStore = when {
            osName.startsWith("Windows", ignoreCase = true) -> WindowsCredentialSecretStore(targetName)
            osName.startsWith("Linux", ignoreCase = true) -> LinuxSecretStore.create(targetName) ?: SessionSecretStore
            else -> UnsupportedSecretStore
        }

        /**
         * The channel separation, secret side: copies the device token from the pre-channel
         * SHARED secret ([AppInfo.NAME] target) into the per-channel secret when the channel
         * secret does not exist yet. The shared entry SURVIVES — it stays the live secret of
         * the base-named channels (stable AND beta — dev / debug / -git are isolated as
         * `… DEV` / `… DEBUG` / `… GIT`) — and an existing channel
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
