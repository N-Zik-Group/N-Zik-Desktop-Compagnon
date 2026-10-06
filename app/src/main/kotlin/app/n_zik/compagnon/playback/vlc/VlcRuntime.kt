package app.n_zik.compagnon.playback.vlc

import java.io.File
import java.util.logging.Logger
import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery

/**
 * The libvlc runtime for local playback.
 *
 * Windows: the embedded libvlc 3.0.24 (story 12) — the build extracts it into the app resources
 * (`<compose.application.resources.dir>/vlc`, under `gradlew run` as in `createDistributable`); it is
 * pointed at through `jna.library.path`, then vlcj's [NativeDiscovery] loads it and sets the plugin path.
 *
 * Linux: the system libvlc (e.g. `sudo apt install vlc`) — there is no embedded runtime, so the embedded
 * gate is skipped and [NativeDiscovery] alone finds the system `libvlc.so` (spec `spec-linux-system-libvlc`).
 *
 * No libvlc at all → playback is [Availability.Unavailable] and the rest of the app keeps working.
 */
object VlcRuntime {
    private val log = Logger.getLogger("VlcRuntime")

    sealed interface Availability {
        /** libvlc loaded from [directory] (the embedded runtime dir on Windows; `system` on the system-library path). */
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

    /** [true] on Windows, the only platform with an embedded runtime; elsewhere the system libvlc is used. */
    val usesEmbeddedRuntime: Boolean
        get() = isEmbeddedPlatform(System.getProperty(OS_NAME_PROPERTY).orEmpty())

    /**
     * The platform split of the load. On the embedded platform the resources directory must carry
     * [LIBVLC] before [discover] runs (and [JNA_LIBRARY_PATH] is prefixed with it); on other platforms
     * [discover] alone loads the system libvlc. [discover] is injected so the branches are testable
     * without the native call.
     */
    internal fun load(osName: String, resourcesDir: String?, discover: () -> Boolean): Availability {
        if (!isEmbeddedPlatform(osName)) {
            return if (discoverSafely(discover)) {
                log.info("libvlc loaded from the system")
                Availability.Available(SYSTEM_LIBRARY)
            } else {
                // The hint is resolved here (with the load) so the UI reads a field, not a lazy that does file IO.
                unavailable("no libvlc on the system", vlcInstallHint(readOsRelease()))
            }
        }
        val directory = resourcesDir?.takeIf { it.isNotBlank() }?.let { File(it, RUNTIME_DIR) }
            ?: return unavailable("$RESOURCES_DIR_PROPERTY is not set")
        if (!File(directory, LIBVLC).isFile) return unavailable("no $LIBVLC in the embedded runtime")
        val path = directory.absolutePath
        val previous = System.getProperty(JNA_LIBRARY_PATH)
        System.setProperty(JNA_LIBRARY_PATH, if (previous.isNullOrBlank()) path else "$path${File.pathSeparator}$previous")
        return if (discoverSafely(discover)) {
            log.info("libvlc loaded from the embedded runtime")
            Availability.Available(path)
        } else {
            unavailable("NativeDiscovery found no libvlc")
        }
    }

    /** Runs the native discovery so a failure degrades to "not available" instead of escaping [load] (the "never throws" contract). */
    private fun discoverSafely(discover: () -> Boolean): Boolean =
        runCatching { discover() }
            .onFailure { log.warning("libvlc discovery failed: ${it::class.simpleName}: ${it.message}") }
            .getOrDefault(false)

    /** [true] on the embedded-runtime platform (Windows). */
    internal fun isEmbeddedPlatform(osName: String): Boolean =
        osName.startsWith("Windows", ignoreCase = true)

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
    private const val LIBVLC = "libvlc.dll"
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
