package app.n_zik.compagnon.playback.vlc

import java.io.File
import java.util.logging.Logger
import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery

/**
 * The libvlc runtime for local playback — one rule for every build variant:
 *
 * the resources directory carrying this platform's libvlc → the embedded runtime (pointed at
 * through `jna.library.path`, then vlcj's [NativeDiscovery] loads it; the embedded plugin dir is
 * pointed at through `VLC_PLUGIN_PATH`, which the Flatpak wrapper / the AppImage AppRun export —
 * in VLC 3.0.24 the `--plugin-path` CLI option is gone, so the env var is the only lever);
 * no embedded runtime → the system libvlc (e.g. `sudo apt install vlc`) found by [NativeDiscovery]
 * alone; the embedded libvlc present but failing to load (corrupt) → the same system discovery
 * retried once.
 *
 * That single decision covers the Windows build (embedded, `libvlc.dll`), the Linux AppImage
 * (embedded, `libvlc.so`) and the other four Linux install paths (`.deb`/`.rpm`/the AUR entries/the
 * portable zip, whose app-image carries no `resources/vlc` — system libvlc, spec
 * `spec-linux-system-libvlc`; the AppImage exception is spec `spec-linux-appimage`).
 *
 * No libvlc at all (neither embedded nor system) → playback is [Availability.Unavailable] and the
 * rest of the app keeps working.
 */
object VlcRuntime {
    private val log = Logger.getLogger("VlcRuntime")

    sealed interface Availability {
        /** libvlc loaded from [directory] (the embedded runtime dir; `system` on the system-library path). */
        data class Available(val directory: String) : Availability

        /** libvlc could not be loaded; [reason] is for the log only, [installHint] the user-facing install hint. */
        data class Unavailable(val reason: String, val installHint: String? = null) : Availability
    }

    /** Resolved once, on first use; never throws. */
    val availability: Availability by lazy {
        load(
            osName = System.getProperty(OS_NAME_PROPERTY).orEmpty(),
            resourcesDir = System.getProperty(RESOURCES_DIR_PROPERTY),
            discover = { NativeDiscovery().discover() },
        )
    }

    val isAvailable: Boolean get() = availability is Availability.Available

    /**
     * Which "local playback unavailable" message the UI shows when libvlc could not be loaded:
     * pure in [osName] so the menu's selection is testable per platform. [true] on Windows (the
     * embedded-VLC message: the Windows build embeds VLC by design, so a load failure there is an
     * app problem, not a missing system package) and [false] on Linux (the system-install message:
     * an unavailable runtime means no usable system libvlc, and the per-distro hint was resolved
     * with the load).
     */
    internal fun unavailableMessageIsEmbedded(osName: String): Boolean =
        osName.startsWith("Windows", ignoreCase = true)

    /**
     * The platform split of the load, one rule for every variant: the embedded runtime is the
     * platform libvlc ([LIBVLC_WINDOWS] on Windows, [LIBVLC_LINUX] on Linux) inside
     * `<resourcesDir>/[RUNTIME_DIR]`; when it is absent (no resources dir, no `vlc` subfolder, or
     * the platform libvlc missing), [discover] alone loads the system libvlc, and when the
     * platform libvlc is present but fails to load (corrupt), the embedded branch is rolled back
     * (the previous `jna.library.path` restored) and [discover] is retried on the system path.
     * [discover] is injected so the branches are testable without the native call.
     */
    internal fun load(osName: String, resourcesDir: String?, discover: () -> Boolean): Availability {
        val directory = embeddedRuntimeDirectory(resourcesDir, osName)
        if (directory == null) {
            return if (discoverSafely(discover)) {
                log.info("libvlc loaded from the system")
                Availability.Available(SYSTEM_LIBRARY)
            } else {
                // The hint is resolved here (with the load) so the UI reads a field, not a lazy that does file IO.
                unavailable("no libvlc on the system", vlcInstallHint(readOsRelease()))
            }
        }
        val path = directory.absolutePath
        val previous = System.getProperty(JNA_LIBRARY_PATH)
        System.setProperty(JNA_LIBRARY_PATH, if (previous.isNullOrBlank()) path else "$path${File.pathSeparator}$previous")
        return if (discoverSafely(discover)) {
            log.info("libvlc loaded from the embedded runtime")
            Availability.Available(path)
        } else {
            // The embedded libvlc is present but failed to load (missing deps, corrupt file): roll
            // back the embedded branch (restore the previous jna.library.path) and retry the system
            // discovery once; only a failed system discovery leaves playback unavailable (with the
            // distro install hint, resolved like the absent-runtime case).
            if (previous.isNullOrBlank()) System.clearProperty(JNA_LIBRARY_PATH) else System.setProperty(JNA_LIBRARY_PATH, previous)
            if (discoverSafely(discover)) {
                log.info("embedded runtime failed to load; libvlc loaded from the system")
                Availability.Available(SYSTEM_LIBRARY)
            } else {
                unavailable("no libvlc on the system", vlcInstallHint(readOsRelease()))
            }
        }
    }

    /**
     * The embedded runtime directory — the platform libvlc inside `<resourcesDir>/[RUNTIME_DIR]` —
     * or [null] when there is no embedded runtime: no resources dir, no `vlc` subfolder, or the
     * platform libvlc is missing. One rule for all variants: the Windows build and the AppImage
     * carry `resources/vlc`; the `.deb`/`.rpm`/AUR/portable app-image does not, so their system
     * behavior is preserved by construction.
     */
    internal fun embeddedRuntimeDirectory(resourcesDir: String?, osName: String): File? =
        resourcesDir
            ?.takeIf { it.isNotBlank() }
            ?.let { File(it, RUNTIME_DIR) }
            ?.takeIf { File(it, libVlcName(osName)).isFile }

    /** The platform libvlc file name: the `.dll` on Windows, the `.so` on Linux (the project's targets). */
    internal fun libVlcName(osName: String): String =
        if (osName.startsWith("Windows", ignoreCase = true)) LIBVLC_WINDOWS else LIBVLC_LINUX

    /** Runs the native discovery so a failure degrades to "not available" instead of escaping [load] (the "never throws" contract). */
    private fun discoverSafely(discover: () -> Boolean): Boolean =
        runCatching { discover() }
            .onFailure { log.warning("libvlc discovery failed: ${it::class.simpleName}: ${it.message}") }
            .getOrDefault(false)

    /**
     * The package-manager command that installs VLC on the distribution described by [osRelease]
     * (the raw content of `/etc/os-release`): [GENERIC_VLC_INSTALL_HINT] when the family is not
     * recognised or [osRelease] is null. The family tables are a curated best-effort subset — the
     * generic hint is the designed escape hatch for everything else (gentoo, nixos, …); note that on
     * RHEL-family distros `vlc` may need an extra repo (e.g. RPM Fusion on Rocky/Alma).
     */
    internal fun vlcInstallHint(osRelease: String?): String {
        val ids = (osRelease ?: "").lineSequence()
            .map { it.trim() }
            .filter { line -> OS_RELEASE_ID_KEYS.any { key -> line.startsWith("$key=") } }
            .flatMap { line ->
                // The value is quoted as a whole (`ID_LIKE="suse opensuse"`): strip the quotes before
                // splitting, or the inner tokens keep their quotes and never match a family.
                line.substringAfter('=').removeSurrounding("\"")
                    .split(' ')
                    .map { it.lowercase() }
                    .filter { it.isNotEmpty() }
            }
        return when {
            ids.any { it in APT_FAMILIES } -> "sudo apt install vlc"
            ids.any { it in DNF_FAMILIES } -> "sudo dnf install vlc"
            ids.any { it in PACMAN_FAMILIES } -> "sudo pacman -S vlc"
            ids.any { it in ZYPPER_FAMILIES } -> "sudo zypper install vlc"
            ids.any { it in APK_FAMILIES } -> "sudo apk add vlc"
            else -> GENERIC_VLC_INSTALL_HINT
        }
    }

    private fun unavailable(reason: String, installHint: String? = null): Availability {
        log.warning("Local playback unavailable: $reason")
        return Availability.Unavailable(reason, installHint)
    }

    private fun readOsRelease(): String? =
        runCatching { File(OS_RELEASE_PATH).readText() }
            .onFailure { log.warning("Could not read $OS_RELEASE_PATH: ${it::class.simpleName}") }
            .getOrNull()

    const val RESOURCES_DIR_PROPERTY = "compose.application.resources.dir"

    /** Fills the `%s` of the Linux unavailable message when no distro family is recognised. */
    const val GENERIC_VLC_INSTALL_HINT = "your distribution's package manager"

    private const val RUNTIME_DIR = "vlc"
    private const val LIBVLC_WINDOWS = "libvlc.dll"
    private const val LIBVLC_LINUX = "libvlc.so"
    private const val JNA_LIBRARY_PATH = "jna.library.path"
    private const val OS_NAME_PROPERTY = "os.name"
    private const val OS_RELEASE_PATH = "/etc/os-release"
    private const val SYSTEM_LIBRARY = "system"

    private val OS_RELEASE_ID_KEYS = listOf("ID", "ID_LIKE")
    private val APT_FAMILIES = setOf("debian", "ubuntu", "linuxmint", "pop", "kali", "raspbian", "deepin")
    private val DNF_FAMILIES = setOf("fedora", "rhel", "centos", "rocky", "almalinux", "nobara")
    private val PACMAN_FAMILIES = setOf("arch", "manjaro", "endeavouros", "garuda")
    private val ZYPPER_FAMILIES = setOf("opensuse", "opensuse-leap", "suse", "sles")
    private val APK_FAMILIES = setOf("alpine", "postmarketos")
}
