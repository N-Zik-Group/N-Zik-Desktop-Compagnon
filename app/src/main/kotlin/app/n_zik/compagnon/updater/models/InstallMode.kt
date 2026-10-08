package app.n_zik.compagnon.updater.models

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
    /** Installed by a package manager under `/opt` (the .deb / .rpm / AUR share the path). */
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
 * @param executablePath the path of the running executable (`ProcessHandle.current().info()`)
 */
fun detectInstallMode(osName: String, env: Map<String, String>, executablePath: String): InstallMode = when {
    osName.startsWith("Windows", ignoreCase = true) -> InstallMode.WINDOWS
    env["FLATPAK_ID"].isNullOrBlank().not() || env["container"] == "flatpak" -> InstallMode.FLATPAK
    executablePath.startsWith("/tmp/.mount_", ignoreCase = true) -> InstallMode.APPIMAGE
    executablePath.startsWith("/opt/n-zik-desktop-compagnon", ignoreCase = true) -> InstallMode.PACKAGE_MANAGED
    else -> InstallMode.PORTABLE
}

/** The real detection inputs of this process (the pure [detectInstallMode] over live data). */
fun currentInstallMode(): InstallMode = detectInstallMode(
    osName = System.getProperty("os.name", ""),
    env = System.getenv(),
    executablePath = runCatching { ProcessHandle.current().info().command().orElse("") }.getOrDefault(""),
)

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
     * The jpackage Windows installer name (verified against jpackage's real output: the product
     * name + version, no architecture suffix — the product name is per-channel, spec AD-8).
     */
    fun exe(version: String, channel: String = channelOf(version)): String =
        "${productName(channel)}-$version.exe"

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

    /** The asset name the update wants for this install mode (`null` = none: the pacman hint). */
    fun forMode(mode: InstallMode, version: String, packageManager: PackageManager, channel: String = channelOf(version)): String? = when (mode) {
        InstallMode.WINDOWS -> exe(version, channel)
        InstallMode.FLATPAK -> flatpak(version, channel)
        InstallMode.PACKAGE_MANAGED -> when (packageManager) {
            PackageManager.DEB -> deb(version, channel)
            PackageManager.RPM -> rpm(version, channel)
            // pacman builds from source: no binary asset, the dialog shows the AUR entry hint.
            PackageManager.AUR, PackageManager.NONE -> null
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
 * [PackageManager.RPM], `pacman` → [PackageManager.AUR] (the AUR entry hint — pacman builds from
 * source, there is no binary asset); nothing found → [PackageManager.NONE] (the dialog shows the
 * manual commands).
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
