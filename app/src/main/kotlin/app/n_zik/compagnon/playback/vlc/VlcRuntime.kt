package app.n_zik.compagnon.playback.vlc

import java.io.File
import java.util.logging.Logger
import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery

/**
 * The embedded libvlc 3.0.24 runtime (story 12). The build extracts it into the app resources
 * (`<compose.application.resources.dir>/vlc`, under `gradlew run` as in `createDistributable`); it is
 * pointed at through `jna.library.path`, then vlcj's [NativeDiscovery] loads it and sets the plugin path.
 *
 * No system VLC is looked for: without the embedded runtime, playback is [Availability.Unavailable] and
 * the rest of the app keeps working.
 */
object VlcRuntime {
    private val log = Logger.getLogger("VlcRuntime")

    sealed interface Availability {
        /** libvlc loaded from [directory]. */
        data class Available(val directory: String) : Availability

        /** libvlc could not be loaded; [reason] is for the log only. */
        data class Unavailable(val reason: String) : Availability
    }

    /** Resolved once, on first use; never throws. */
    val availability: Availability by lazy { load() }

    val isAvailable: Boolean get() = availability is Availability.Available

    /** `<compose.application.resources.dir>/vlc`, or `null` outside a Compose Desktop launch. */
    fun runtimeDirectory(): File? =
        System.getProperty(RESOURCES_DIR_PROPERTY)?.takeIf { it.isNotBlank() }?.let { File(it, RUNTIME_DIR) }

    private fun load(): Availability {
        val directory = runtimeDirectory()
            ?: return unavailable("$RESOURCES_DIR_PROPERTY is not set")
        if (!File(directory, LIBVLC).isFile) return unavailable("no $LIBVLC in the embedded runtime")
        val path = directory.absolutePath
        val previous = System.getProperty(JNA_LIBRARY_PATH)
        System.setProperty(JNA_LIBRARY_PATH, if (previous.isNullOrBlank()) path else "$path${File.pathSeparator}$previous")
        val found = runCatching { NativeDiscovery().discover() }
            .onFailure { log.warning("libvlc discovery failed: ${it::class.simpleName}") }
            .getOrDefault(false)
        return if (found) {
            log.info("libvlc loaded from the embedded runtime")
            Availability.Available(path)
        } else {
            unavailable("NativeDiscovery found no libvlc")
        }
    }

    private fun unavailable(reason: String): Availability {
        log.warning("Local playback unavailable: $reason")
        return Availability.Unavailable(reason)
    }

    const val RESOURCES_DIR_PROPERTY = "compose.application.resources.dir"
    private const val RUNTIME_DIR = "vlc"
    private const val LIBVLC = "libvlc.dll"
    private const val JNA_LIBRARY_PATH = "jna.library.path"
}
