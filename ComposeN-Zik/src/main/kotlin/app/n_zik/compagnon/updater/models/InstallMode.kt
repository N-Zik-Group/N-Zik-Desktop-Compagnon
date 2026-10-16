package app.n_zik.compagnon.updater.models

import app.n_zik.compagnon.AppInfo
import app.n_zik.compagnon.generated.AppVersion
import com.sun.jna.platform.win32.Advapi32
import com.sun.jna.platform.win32.WinReg
import com.sun.jna.ptr.IntByReference
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.util.logging.Logger
import kotlin.concurrent.Volatile

private val log = Logger.getLogger("InstallMode")

/**
 * The installation mode of this app instance (spec `spec-updater`, AD-4): it decides which release
 * asset the update check wants and which install gesture the update dialog offers. Detection is
 * pure (OS name + environment + executable path as inputs) so it is pinned by
 * `InstallModeDetectionTest`.
 */
enum class InstallMode {
    /**
     * The Windows NSIS install: interactive in-place reinstall. The scope defaults to the
     * current install's scope (per-user by default — no UAC prompt; the global scope relaunches
     * the installer elevated, the visible UAC prompt appears at that moment) — NO frozen
     * upgrade GUID (spec `spec-updater` AD-8: the registry key `Software\N-Zik\DesktopCompagnon\{channel}`
     * is the identity).
     */
    WINDOWS,
    /**
     * The portable Windows build (spec `spec-windows-portable-zip`): the app-image unzipped
     * anywhere, launched without the NSIS uninstaller marker — the updater offers the
     * `-windows-portable.zip` and the manual "replace the app folder" gesture (the zip's top
     * level IS the product folder: delete the current folder, unzip the new one in its place).
     */
    PORTABLE_WINDOWS,
    /** Running inside a Flatpak sandbox: update in place with `flatpak install <file>`. */
    FLATPAK,
    /** Installed by a package manager under `/opt` (the .deb / .rpm / Arch pkg / AUR share the path). */
    PACKAGE_MANAGED,
    /** Any other path (the portable app-image extracted/unzipped anywhere — the Linux .tar.gz or the Windows .zip). */
    PORTABLE,
}

/**
 * Whether a Windows launch is an NSIS install (spec `spec-windows-portable-zip`, decision D1 —
 * pure, testable): the launcher's folder carries the NSIS uninstaller `Uninstall <product>.exe`
 * AND EITHER the folder is named after the PER-CHANNEL product (AD-8: `N-Zik Desktop Compagnon`
 * / `… BETA` / `… DEV`) OR the channel's registry key `Software\N-Zik\DesktopCompagnon\{channel}`
 * (HKCU per-user / HKLM global) records `InstallDir` = that folder. The uninstaller marker is
 * the DISCRIMINATOR — `installer.nsi` writes it into `$INSTDIR` always (`WriteUninstaller`), and
 * a portable extract never carries it. The registry cross-check (review loopback 2 — the
 * installer's directory page is EDITABLE, so an install can sit in a custom folder not named
 * after the product): the installer records its `$INSTDIR` under the per-channel key at install
 * time, so marker + recorded dir cover custom-dir installs; an absent or unreadable key degrades
 * to the folder-name rule (the pre-loopback-2 behavior). The scope location
 * (`AppData\Local\` / `Program Files\`) is a secondary indicator, NOT a requirement: a per-user
 * install under a redirected `%LOCALAPPDATA%` must still detect as installed, and a portable
 * extract unzipped INTO a scope dir must NOT.
 *
 * @param registryInstallDir reads the installer's recorded `InstallDir` for a channel
 * ([windowsInstallDirFromRegistry] in production — the tests inject theirs).
 */
fun isNsisWindowsInstall(
    executablePath: String,
    productName: String,
    channel: String = AppVersion.channel,
    registryInstallDir: (String) -> String? = {
        // The cross-scope reader (spec `spec-updater-parity-fixes`): the instance dir is this
        // launcher's folder — the hive whose record matches it wins
        windowsInstallDirFromRegistry(it, File(executablePath).parentFile?.path.orEmpty())
    },
): Boolean {
    val launcherDir = File(executablePath).parentFile ?: return false
    if (!File(launcherDir, "Uninstall $productName.exe").isFile) return false
    if (launcherDir.name.equals(productName, ignoreCase = true)) return true
    val recorded = registryInstallDir(channel) ?: return false
    return sameWindowsDir(recorded, launcherDir.path)
}

/**
 * Windows dir comparison for the D1 registry cross-check: case-insensitive (Windows paths are)
 * and trailing-backslash-insensitive (NSIS records `$INSTDIR` without one — a trailing separator
 * is not a different dir).
 */
private fun sameWindowsDir(recorded: String, dir: String): Boolean {
    val strip = { s: String -> s.trim().removeSuffix("\\") }
    return strip(recorded).equals(strip(dir), ignoreCase = true)
}

/**
 * The install dir the NSIS installer recorded for a channel (spec `spec-windows-portable-zip`,
 * review loopback 2 — D1; the cross-scope fix, spec `spec-updater-parity-fixes`): `installer.nsi`
 * writes `InstallDir = $INSTDIR` into the per-channel key at install time (HKCU for the per-user
 * scope, HKLM for the global — a per-user AND a global install of the same channel can coexist,
 * so both are consulted). The returned record is the hive whose `InstallDir` matches the running
 * instance's directory ([instanceDir], [preferMatchingInstallDir] — the mobile principle: the
 * selection is a pure function of the current instance), degrading to the HKCU-first, then HKLM
 * read when neither hive matches (the pre-audit behavior, now an explicit fallback).
 * Windows-only by construction (reached only from the Windows branch of [detectInstallMode] via
 * [isNsisWindowsInstall]); `null` when the key or the value is absent or unreadable (a manually
 * cleaned key degrades to the folder-name rule). A degenerate empty [instanceDir] (the default
 * seam's `orEmpty()` — an executable path with no parent) can never match a hive and falls
 * back to the HKCU-first read as well.
 */
fun windowsInstallDirFromRegistry(channel: String, instanceDir: String): String? {
    val key = "Software\\N-Zik\\DesktopCompagnon\\$channel"
    return preferMatchingInstallDir(
        hkcu = registryInstallDirUnder(WinReg.HKEY_CURRENT_USER, key),
        hklm = registryInstallDirUnder(WinReg.HKEY_LOCAL_MACHINE, key),
        instanceDir = instanceDir,
    )
}

/**
 * The hive-selection decision of [windowsInstallDirFromRegistry] (the cross-scope fix, spec
 * `spec-updater-parity-fixes`, pure — unit-testable off-Windows): a per-user AND a global NSIS
 * install of the same channel can coexist, so the reader must not blindly trust the HKCU-first
 * read — a global instance in a custom dir was claimed by the coexisting per-user record
 * (misdetected `PORTABLE_WINDOWS`, wrong asset + gesture). The per-user (HKCU) record wins when
 * it matches the running instance's directory, then the global (HKLM) record when it does (the
 * match is [sameWindowsDir] — case + trailing-slash insensitive); when NEITHER hive matches,
 * the explicit degradation is HKCU first, then HKLM (the pre-audit reader order).
 */
internal fun preferMatchingInstallDir(hkcu: String?, hklm: String?, instanceDir: String): String? = when {
    hkcu != null && sameWindowsDir(hkcu, instanceDir) -> hkcu
    hklm != null && sameWindowsDir(hklm, instanceDir) -> hklm
    else -> hkcu ?: hklm
}

/**
 * Reads the `InstallDir` REG_SZ value of [key] under [root] (`null` when the key or value is
 * absent or unreadable). Windows-only by construction: the Advapi32 bindings load on first use,
 * so this is never reached on a non-Windows host.
 */
private fun registryInstallDirUnder(root: WinReg.HKEY, key: String): String? =
    runCatching {
        val opened = WinReg.HKEYByReference()
        // A query-only open (KEY_READ) — the updater never writes the installer's identity.
        if (Advapi32.INSTANCE.RegOpenKeyEx(root, key, 0, KEY_READ, opened) != ERROR_SUCCESS) {
            return@runCatching null
        }
        val handle = opened.getValue() ?: return@runCatching null
        try {
            // Two-pass REG_SZ read: the size first (bytes, the terminating zero included), then
            // the value. The probe reports the size via lpcbData (lpData = null) and returns
            // either SUCCESS (zero-length value) or ERROR_INSUFFICIENT_BUFFER.
            val size = IntByReference(0)
            val probe = Advapi32.INSTANCE.RegQueryValueEx(
                handle, "InstallDir", 0, IntByReference(), null as CharArray?, size,
            )
            if (probe != ERROR_SUCCESS && probe != ERROR_INSUFFICIENT_BUFFER) return@runCatching null
            val buffer = CharArray(size.value / 2)
            val read = Advapi32.INSTANCE.RegQueryValueEx(
                handle, "InstallDir", 0, IntByReference(), buffer, IntByReference(buffer.size * 2),
            )
            if (read != ERROR_SUCCESS) return@runCatching null
            String(buffer).trimEnd('\u0000')
        } finally {
            Advapi32.INSTANCE.RegCloseKey(handle)
        }
    }.onFailure { log.fine("Registry InstallDir read failed ($key): ${it.message}") }.getOrNull()
        ?.takeIf { it.isNotBlank() }

/** Read-only registry open (the updater never writes the installer's identity). */
private const val KEY_READ = 0x20019
/** `RegOpenKeyEx` / `RegQueryValueEx` success (the probe additionally tolerates 122). */
private const val ERROR_SUCCESS = 0
/** `RegQueryValueEx` "the buffer is too small" — the expected probe outcome (size in lpcbData). */
private const val ERROR_INSUFFICIENT_BUFFER = 122

/**
 * Detects the [InstallMode] (spec AD-4, pure; loop-2 refinement): on Windows, an NSIS install
 * (the launcher folder carrying the `Uninstall <product>.exe` marker — product-named folder or
 * the installer's recorded `InstallDir` in the channel registry key, see
 * [isNsisWindowsInstall]) is [InstallMode.WINDOWS], any other launch is [InstallMode.PORTABLE_WINDOWS];
 * on Linux, the Flatpak environment wins — a NON-BLANK `FLATPAK_ID` or the exact marker
 * `container=flatpak` — then the package-managed `/opt` path (stable and dev share
 * `/opt/n-zik-desktop-compagnon` — spec AD-8 as amended 2026-10-15: the dev channel's marker is
 * its dated version suffix, not a separate path); anything else is portable.
 *
 * @param osName `System.getProperty("os.name")`
 * @param env the process environment (`System.getenv()`)
 * @param executablePath the RESOLVED path of the running executable (see [liveExecutablePath] —
 * the production launch goes through the `/usr/bin/<pkg>` symlink, so the unresolved argv[0]
 * would misclassify an `/opt` install as portable)
 * @param windowsProductName the per-channel product name of this build (AD-8 — the jpackage
 * app name the NSIS installer lays out; the production default is this build's own channel
 * product name, the tests inject theirs)
 * @param registryInstallDir the D1 registry cross-check source (the production default reads
 * the installer's recorded `InstallDir`, the tests inject theirs)
 */
fun detectInstallMode(
    osName: String,
    env: Map<String, String>,
    executablePath: String,
    windowsProductName: String = ArtifactNames.productName(AppVersion.channel),
    registryInstallDir: (String) -> String? = {
        // The cross-scope reader (spec `spec-updater-parity-fixes`): the instance dir is this
        // launcher's folder — the hive whose record matches it wins
        windowsInstallDirFromRegistry(it, File(executablePath).parentFile?.path.orEmpty())
    },
): InstallMode = when {
    osName.startsWith("Windows", ignoreCase = true) ->
        if (isNsisWindowsInstall(executablePath, windowsProductName, AppVersion.channel, registryInstallDir))
            InstallMode.WINDOWS
        else InstallMode.PORTABLE_WINDOWS
    env["FLATPAK_ID"].isNullOrBlank().not() || env["container"] == "flatpak" -> InstallMode.FLATPAK
    executablePath.startsWith("/opt/n-zik-desktop-compagnon", ignoreCase = true) -> InstallMode.PACKAGE_MANAGED
    else -> InstallMode.PORTABLE
}

/**
 * The live executable path of this process, RESOLVED (spec `spec-arch-binary-package-release`,
 * loop 2 — G9): `ProcessHandle.command()` is argv[0] UNRESOLVED — a process launched through a
 * symlink (the production launch: the `.desktop` → `Exec=/usr/bin/<pkg>` → the kernel execs the
 * target) carries the SYMLINK path in its own cmdline. On Linux the kernel-resolved
 * `/proc/self/exe` is therefore authoritative (its `toRealPath()` walks the full chain, the
 * binary itself included); when `/proc/self/exe` is unavailable (non-Linux hosts) the fallback
 * is `ProcessHandle.command()`.
 */
fun liveExecutablePath(): String = commandLinePath().let { fallback ->
    val procSelfExe = Paths.get("/proc/self/exe")
    if (Files.isSymbolicLink(procSelfExe)) {
        return runCatching { procSelfExe.toRealPath().toString() }.onFailure {
            // The provenance read degrades to the pre-G9 behavior (the command-line path) on a
            // resolution failure — this is the only diagnostic of that degradation (review loop 3
            // — L3-BH2): an unresolvable /proc/self/exe means an /opt install would be misdetected
            // PORTABLE with a null marker, and the block would invert silently
            log.warning("Failed to resolve /proc/self/exe — the provenance falls back to the command-line path: ${it.message}")
        }.getOrNull() ?: fallback
    }
    fallback
}

private fun commandLinePath(): String =
    runCatching { ProcessHandle.current().info().command().orElse("") }.getOrDefault("")

/**
 * The app root of a RESOLVED executable path (pure, injectable — loop 2 G9): a package-managed
 * install's launcher lives at `<appRoot>/bin/<launcher>` — the parent dir named `bin`
 * (case-insensitive) → its parent IS the app root (where the payload and the `distribution.txt`
 * marker live); anything else → `null` (no app root: no marker to read).
 */
fun appRootOfResolvedPath(resolvedPath: String): String? {
    val binDir = File(resolvedPath).parentFile ?: return null
    if (!binDir.name.equals("bin", ignoreCase = true)) return null
    // The contract models the Linux /opt install layout: the result is normalized to POSIX
    // separators on every host (a deterministic, platform-independent path)
    return binDir.parentFile?.path?.replace('\\', '/')
}

/**
 * The content of the release provenance marker (spec `spec-arch-binary-package-release`): the
 * SOLE provenance signal per install — `packageArch` writes it into `distribution.txt` at the app
 * root of the release-pkg payload (the build side pins the same literal through the `arch` props,
 * `ArchPkgPinTest`); the AUR entries and every other route carry the file not.
 */
const val RELEASE_MARKER_CONTENT = "github-release"

/**
 * Reads the provenance marker (spec `spec-arch-binary-package-release`): the app root resolved
 * from [resolvedPath] + `distribution.txt`, trimmed. Absent / not exactly
 * [RELEASE_MARKER_CONTENT] / unreadable → `null` (no marker — the install is not a release-pkg
 * install: the AUR entries and every other route carry the file not). The reader is strict
 * (review loop 3 — L3-EC2): a stray or forged `distribution.txt` with any OTHER content must not
 * unblock the updater — the marker is the sole provenance signal, so only its exact content
 * counts (the write side is equally strict: `packageArch` fails the build on any other content).
 */
fun distributionMarkerAt(resolvedPath: String): String? {
    val appRoot = appRootOfResolvedPath(resolvedPath) ?: return null
    val markerFile = File(appRoot, "distribution.txt")
    if (!markerFile.isFile) return null
    return runCatching { markerFile.readText().trim() }.getOrNull()?.takeIf { it == RELEASE_MARKER_CONTENT }
}

/** The provenance marker of THIS install (the live, kernel-resolved path — G9). */
fun currentDistributionMarker(): String? = distributionMarkerAt(liveExecutablePath())

/** The real detection of this process (the pure [detectInstallMode] over live, RESOLVED data). */
fun currentInstallMode(): InstallMode = detectInstallMode(
    osName = System.getProperty("os.name", ""),
    env = System.getenv(),
    executablePath = liveExecutablePath(),
)

/**
 * The package manager of THIS install — the production composition, memoized for the process
 * lifetime (spec `spec-arch-binary-package-release`, review loop 3 — L3-BH3): the probe is a
 * blocking `apt`/`dnf`/`pacman --version` process-spawn chain and the answer cannot change
 * during the process lifetime — the startup gate, the update check, the About card, the update
 * page, the update dialog and the fetch defaults each used to re-run the whole chain (2-4 probe
 * chains per launch on a package-managed install). Computed once, on first use, by the caller
 * (the production sites run it off the composition thread, `NzikDispatchers.DATA`).
 * [probePackageManager] stays pure and un-memoized — the tests inject their own probes through it.
 */
fun livePackageManager(): PackageManager =
    memoizedLivePackageManager ?: probePackageManager().also { memoizedLivePackageManager = it }

@Volatile
private var memoizedLivePackageManager: PackageManager? = null

/** Clears the [livePackageManager] memo (the test seam — the memo is process-lifetime state). */
internal fun resetLivePackageManagerForTests() {
    memoizedLivePackageManager = null
}

/**
 * Whether the in-app updater is BLOCKED by provenance (spec
 * `spec-arch-binary-package-release`): a package-managed install on a pacman host WITHOUT the
 * release marker is an AUR install — the AUR entry owns the update, so the updater is off (same
 * treatment as the git channel). The marker is checked BEFORE the probe: a marked install
 * (release pkg) never spawns a probe, and a non-package-managed mode never does either (loop 2
 * G13 — no blocking `apt`/`dnf`/`pacman` spawn on the composition thread; the production gate
 * additionally runs the composition on `NzikDispatchers.DATA`, off the UI thread).
 *
 * @param probe the package-manager probe, injectable for the tests (the production default
 * spawns `apt`/`dnf`/`pacman --version` via [probePackageManager]).
 */
fun isAurBlocked(mode: InstallMode, marker: String?, probe: () -> PackageManager): Boolean =
    mode == InstallMode.PACKAGE_MANAGED && marker == null && probe() == PackageManager.AUR

/** The block state of THIS install (injectable by default — the live composition is testable). */
fun currentIsAurBlocked(
    installMode: InstallMode = currentInstallMode(),
    marker: String? = currentDistributionMarker(),
    probe: () -> PackageManager = { livePackageManager() },
): Boolean {
    val blocked = isAurBlocked(installMode, marker, probe)
    // The provenance verdict (review loop 3 — L3-BH2): FINE by default — enable it to see why
    // the in-app updater is off on a given install (the silent degradation otherwise leaves no
    // trace in the log)
    log.fine("Updater provenance: mode=$installMode, marker=$marker, aurBlocked=$blocked")
    return blocked
}

/**
 * Whether the updater is effectively enabled for this install (spec
 * `spec-arch-binary-package-release`): the build's own gate (the channel — git/debug builds ship
 * without the updater) AND the provenance gate (an AUR install is blocked).
 */
fun updaterEffectivelyEnabled(
    buildEnabled: Boolean = AppVersion.updaterEnabled,
    aurBlocked: Boolean = currentIsAurBlocked(),
): Boolean = buildEnabled && !aurBlocked

/**
 * The release asset names of one channel (the build's rename convention, spec AD-2: the channel
 * suffix sits right after the base version, e.g. `…-0.0.2-beta-x86_64.flatpak`). The version
 * argument is the release version WITH its channel suffix.
 *
 * Channel-aware identity (spec AD-8, loop 2 + the 2026-10-09 naming rework, amended 2026-10-15):
 * the channel is INFERRED from the version suffix (`-dev-*` → dev, `-beta` → beta, else stable)
 * — the release's assets carry the channel in their names, and ONLY there: since the 2026-10-15
 * decision all seven app assets of every channel share the single package base
 * (`n-zik-desktop-compagnon`) — the dev channel's marker is its dated version suffix (on Linux
 * it replaces the stable install in place, like beta; Windows keeps its per-channel registry
 * key and the Flatpak keeps its own app-id, so both stay separate installations there). The
 * product name is per-channel ("… BETA" / "… DEV" — the jpackage app name the build sets, from
 * [AppInfo.productName] so the window title and the install identity agree; the app data dir
 * and the secret store target follow the per-channel STORAGE name ([AppInfo.dataName]) instead
 * — the 2026-10-16 storage regrouping). The .exe file name is the
 * shared base + the channel-suffixed version (`n-zik-desktop-compagnon-0.0.1-dev-20261015.exe`);
 * the build writes the installer under that exact name, so the release asset and the in-app
 * updater agree on one name. The functions take the channel as a defaulted parameter so
 * callers with an explicit channel can override the inference.
 */
object ArtifactNames {

    /**
     * The jpackage linux package name — shared by EVERY channel since the 2026-10-15 naming
     * decision (the dev channel's own `-dev` base was removed: its marker is the dated version
     * suffix only, so on Linux dev replaces the stable install in place, like beta — Windows
     * keeps its per-channel registry key, the Flatpak keeps its own app-id). The frozen install
     * identity — `/opt/n-zik-desktop-compagnon`.
     */
    const val LINUX_PACKAGE_BASE = "n-zik-desktop-compagnon"

    /**
     * The channel of a release version (the build's suffix convention): `-dev-<date>` → dev,
     * `-beta` → beta, anything else (plain, and the never-published debug / -git) → stable.
     */
    fun channelOf(version: String): String = when {
        version.contains(UpdaterConstants.SUFFIX_DEV) -> UpdaterConstants.TYPE_DEV
        version.endsWith(UpdaterConstants.SUFFIX_BETA) -> UpdaterConstants.TYPE_BETA
        else -> UpdaterConstants.TYPE_STABLE
    }

    /**
     * The per-channel product name (spec AD-8: the jpackage app name — the window title, the
     * install directory name, the Linux launcher, the Flatpak `exec`). Delegates to
     * [AppInfo.productName] — the single source of truth of the INSTALL identity (the app data
     * dir and the secret store target follow [AppInfo.dataName] — the 2026-10-16 storage
     * regrouping).
     */
    fun productName(channel: String): String = AppInfo.productName(channel)

    /**
     * The release asset name of the Windows installer (spec AD-2 / AD-8, the 2026-10-09 naming
     * rework, amended 2026-10-15): the shared package base + the channel-suffixed version — the
     * same base every Linux artifact of every channel shares. The build writes the installer
     * under this exact name (the spaced jpackage byproduct is deleted by the task), so the
     * release asset, the SHA-256 table and the in-app updater all agree on one name.
     */
    fun exe(version: String, channel: String = channelOf(version)): String =
        "$LINUX_PACKAGE_BASE-$version.exe"

    fun deb(version: String, channel: String = channelOf(version)): String =
        "${LINUX_PACKAGE_BASE}_$version-1_amd64.deb"

    fun rpm(version: String, channel: String = channelOf(version)): String =
        "$LINUX_PACKAGE_BASE-$version-1.x86_64.rpm"

    /**
     * The release asset name of the portable Linux build (spec
     * `spec-channel-storage-portable-targz` — the 2026-10-16 switch from `.zip` to `.tar.gz`):
     * the gzip tarball of the shared jpackage app-image (the AUR release entry consumes it —
     * `tar -xzf` in its `package()`; the launcher keeps its execute bit inside the archive,
     * verified fail-loud by the build).
     */
    fun portableTar(version: String, channel: String = channelOf(version)): String =
        "$LINUX_PACKAGE_BASE-$version-linux-portable.tar.gz"

    /**
     * The release asset name of the portable Windows zip (spec `spec-windows-portable-zip`):
     * the same channel base as the other assets — the zip of the shared jpackage app-image
     * (the NSIS installer's own source, the embedded VLC runtime included).
     */
    fun windowsPortableZip(version: String, channel: String = channelOf(version)): String =
        "$LINUX_PACKAGE_BASE-$version-windows-portable.zip"

    fun flatpak(version: String, channel: String = channelOf(version)): String =
        "$LINUX_PACKAGE_BASE-$version-x86_64.flatpak"

    /**
     * The release asset name of the Arch binary package (spec
     * `spec-arch-binary-package-release`): the pacman `.pkg.tar.zst` built by
     * `:ComposeN-Zik:packageArch` — the PKGBUILD layout (the `<pkg>` base + the full version WITH the
     * channel suffix + release number 1 + the frozen x86_64 arch).
     */
    fun archPkg(version: String, channel: String = channelOf(version)): String =
        "$LINUX_PACKAGE_BASE-$version-1-x86_64.pkg.tar.zst"

    /**
     * The asset name the update wants for this install mode (`null` = none: the manual
     * commands). A package-managed install on pacman offers the binary package ONLY when the
     * release marker is present (a release-pkg install, spec
     * `spec-arch-binary-package-release`); an AUR install (no marker) has no binary asset —
     * its updater is blocked before any asset is selected. A portable-Windows install
     * (spec `spec-windows-portable-zip`) gets the Windows portable zip — the manual
     * "replace the app folder" gesture (the zip's top level IS the product folder).
     *
     * @param isReleasePackage the install carries the `distribution.txt` release marker (the
     * production default reads it live from the resolved app root).
     */
    fun forMode(
        mode: InstallMode,
        version: String,
        packageManager: PackageManager,
        isReleasePackage: Boolean = currentDistributionMarker() != null,
        channel: String = channelOf(version),
    ): String? = when (mode) {
        InstallMode.WINDOWS -> exe(version, channel)
        InstallMode.PORTABLE_WINDOWS -> windowsPortableZip(version, channel)
        InstallMode.FLATPAK -> flatpak(version, channel)
        InstallMode.PACKAGE_MANAGED -> when (packageManager) {
            PackageManager.DEB -> deb(version, channel)
            PackageManager.RPM -> rpm(version, channel)
            PackageManager.AUR -> if (isReleasePackage) archPkg(version, channel) else null
            PackageManager.NONE -> null
        }
        InstallMode.PORTABLE -> portableTar(version, channel)
    }
}

/**
 * The system package manager of a package-managed install (spec AD-4). Probed at runtime (which
 * binary answers) — not part of the pure detection, so not pinned by the tests.
 */
enum class PackageManager { DEB, RPM, AUR, NONE }

/**
 * Probes the package manager for a package-managed install: `apt` → [PackageManager.DEB], `dnf` →
 * [PackageManager.RPM], `pacman` → [PackageManager.AUR]; nothing found → [PackageManager.NONE]
 * (the dialog shows the manual commands).
 */
fun probePackageManager(probe: (String) -> Boolean = { binary ->
    runCatching {
        val process = ProcessBuilder(binary, "--version").redirectErrorStream(true).start()
        process.inputStream.readBytes()
        process.waitFor()
    }.isSuccess
}): PackageManager = when {
    probe("apt") -> PackageManager.DEB
    probe("dnf") -> PackageManager.RPM
    probe("pacman") -> PackageManager.AUR
    else -> PackageManager.NONE
}
