package app.n_zik.compagnon.updater.models

import app.n_zik.compagnon.generated.AppVersion
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
    /** The Windows install: interactive in-place reinstall (machine-level with a visible UAC prompt — the frozen per-channel upgrade UUID). */
    WINDOWS,
    /** Running inside a Flatpak sandbox: update in place with `flatpak install <file>`. */
    FLATPAK,
    /** Running from an extracted AppImage mount (`/tmp/.mount_*`). */
    APPIMAGE,
    /** Installed by a package manager under `/opt` (the .deb / .rpm / Arch pkg / AUR share the path). */
    PACKAGE_MANAGED,
    /** Any other path (the portable app-image unzipped anywhere). */
    PORTABLE,
}

/**
 * Detects the [InstallMode] (spec AD-4, pure; loop-2 refinement): a Windows host is always
 * [InstallMode.WINDOWS]; on Linux, the Flatpak environment wins — a NON-BLANK `FLATPAK_ID` or
 * the exact marker `container=flatpak` — then the AppImage mount path, then the package-managed
 * `/opt` path (stable or dev — the dev install lives in `/opt/n-zik-desktop-compagnon-dev`,
 * spec AD-8); anything else is portable.
 *
 * @param osName `System.getProperty("os.name")`
 * @param env the process environment (`System.getenv()`)
 * @param executablePath the RESOLVED path of the running executable (see [liveExecutablePath] —
 * the production launch goes through the `/usr/bin/<pkg>` symlink, so the unresolved argv[0]
 * would misclassify an `/opt` install as portable)
 */
fun detectInstallMode(osName: String, env: Map<String, String>, executablePath: String): InstallMode = when {
    osName.startsWith("Windows", ignoreCase = true) -> InstallMode.WINDOWS
    env["FLATPAK_ID"].isNullOrBlank().not() || env["container"] == "flatpak" -> InstallMode.FLATPAK
    executablePath.startsWith("/tmp/.mount_", ignoreCase = true) -> InstallMode.APPIMAGE
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
 * suffix sits right after the base version, e.g. `…-0.0.2-beta-x86_64.AppImage`). The version
 * argument is the release version WITH its channel suffix.
 *
 * Channel-aware identity (spec AD-8, loop 2): the channel is INFERRED from the version suffix
 * (`-dev-*` → dev, `-beta` → beta, else stable) — the release's assets carry the channel in their
 * names. The Windows product name is per-channel ("… (Beta)" / "… (Dev)" — the jpackage app name
 * the build sets, mirrored here so the updater finds the renamed asset); the Linux dev channel
 * carries its own package base (`n-zik-desktop-compagnon-dev`). The functions take the channel
 * as a defaulted parameter so callers with an explicit channel can override the inference.
 */
object ArtifactNames {
    /** The display name (the jpackage package name — with its spaces, in the Windows installer). */
    const val WINDOWS_PACKAGE_BASE = "N-Zik Desktop Compagnon"

    /** The jpackage linux package name (the frozen install identity — `/opt/n-zik-desktop-compagnon`). */
    const val LINUX_PACKAGE_BASE = "n-zik-desktop-compagnon"

    /** The dev channel's linux package name (spec AD-8 — installs to `/opt/n-zik-desktop-compagnon-dev`). */
    const val LINUX_PACKAGE_BASE_DEV = "n-zik-desktop-compagnon-dev"

    /**
     * The channel of a release version (the build's suffix convention): `-dev-<date>` → dev,
     * `-beta` → beta, anything else (plain, and the never-published debug / -git) → stable.
     */
    fun channelOf(version: String): String = when {
        version.contains(UpdaterConstants.SUFFIX_DEV) -> UpdaterConstants.TYPE_DEV
        version.endsWith(UpdaterConstants.SUFFIX_BETA) -> UpdaterConstants.TYPE_BETA
        else -> UpdaterConstants.TYPE_STABLE
    }

    /** The per-channel product name (spec AD-8: the jpackage app name, the window title). */
    fun productName(channel: String): String = when (channel) {
        UpdaterConstants.TYPE_BETA -> "$WINDOWS_PACKAGE_BASE (Beta)"
        UpdaterConstants.TYPE_DEV -> "$WINDOWS_PACKAGE_BASE (Dev)"
        else -> WINDOWS_PACKAGE_BASE
    }

    private fun linuxPackageBase(channel: String): String =
        if (channel == UpdaterConstants.TYPE_DEV) LINUX_PACKAGE_BASE_DEV else LINUX_PACKAGE_BASE

    /**
     * The release asset name of the Windows installer (spec AD-2 / AD-8): the CI renames
     * jpackage's output (the spaced product name) to this exact name before upload — the GitHub
     * asset upload sanitizes names (space → dot, "(" → "", ")" → ".") and the CI makes that
     * transformation explicit instead of relying on it, so the release asset, the SHA-256 table
     * and the in-app updater all agree on one name.
     */
    fun exe(version: String, channel: String = channelOf(version)): String =
        "${publishedName(productName(channel))}-$version.exe"

    /** GitHub's asset-name sanitization applied to the product name (the rule the CI applies
     *  when it renames the jpackage output — keep the two in sync). */
    private fun publishedName(name: String): String =
        name.replace(" ", ".").replace("(", "").replace(")", ".")

    fun deb(version: String, channel: String = channelOf(version)): String =
        "${linuxPackageBase(channel)}_$version-1_amd64.deb"

    fun rpm(version: String, channel: String = channelOf(version)): String =
        "${linuxPackageBase(channel)}-$version-1.x86_64.rpm"

    fun portableZip(version: String, channel: String = channelOf(version)): String =
        "${linuxPackageBase(channel)}-$version-linux-portable.zip"

    fun appImage(version: String, channel: String = channelOf(version)): String =
        "${linuxPackageBase(channel)}-$version-x86_64.AppImage"

    fun flatpak(version: String, channel: String = channelOf(version)): String =
        "${linuxPackageBase(channel)}-$version-x86_64.flatpak"

    /**
     * The release asset name of the Arch binary package (spec
     * `spec-arch-binary-package-release`): the 7th asset, the pacman `.pkg.tar.zst` built by
     * `:app:packageArch` — the PKGBUILD layout (the `<pkg>` base + the full version WITH the
     * channel suffix + release number 1 + the frozen x86_64 arch).
     */
    fun archPkg(version: String, channel: String = channelOf(version)): String =
        "${linuxPackageBase(channel)}-$version-1-x86_64.pkg.tar.zst"

    /**
     * The asset name the update wants for this install mode (`null` = none: the manual
     * commands). A package-managed install on pacman offers the binary package ONLY when the
     * release marker is present (a release-pkg install, spec
     * `spec-arch-binary-package-release`); an AUR install (no marker) has no binary asset —
     * its updater is blocked before any asset is selected.
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
        InstallMode.FLATPAK -> flatpak(version, channel)
        InstallMode.PACKAGE_MANAGED -> when (packageManager) {
            PackageManager.DEB -> deb(version, channel)
            PackageManager.RPM -> rpm(version, channel)
            PackageManager.AUR -> if (isReleasePackage) archPkg(version, channel) else null
            PackageManager.NONE -> null
        }
        InstallMode.APPIMAGE -> appImage(version, channel)
        InstallMode.PORTABLE -> portableZip(version, channel)
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
