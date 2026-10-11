package app.n_zik.compagnon.platform

import androidx.compose.ui.graphics.ImageBitmap
import app.n_zik.compagnon.generated.AppVersion
import app.n_zik.compagnon.bridge.ConnectionState
import app.n_zik.compagnon.bridge.state.PlayerState
import app.n_zik.compagnon.bridge.state.SessionContract
import app.n_zik.compagnon.bridge.pairing.CredentialStore
import app.n_zik.compagnon.core.network.ArtworkKey
import app.n_zik.compagnon.utils.coroutines.NzikDispatchers
import java.nio.file.Files
import java.nio.file.Path
import java.util.logging.Logger
import javax.imageio.ImageIO
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.freedesktop.dbus.annotations.DBusBoundProperty
import org.freedesktop.dbus.annotations.DBusInterfaceName
import org.freedesktop.dbus.annotations.DBusMemberName
import org.freedesktop.dbus.connections.impl.DBusConnection
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder
import org.freedesktop.dbus.exceptions.DBusException
import org.freedesktop.dbus.interfaces.DBusInterface
import org.freedesktop.dbus.types.Variant

/**
 * The MPRIS media interface (spec `spec-tray-taskbar-media-controls`): the Linux taskbar/dock media
 * controls, exposed on the D-Bus session bus as `org.mpris.MediaPlayer2.nzik_compagnon`. The PC
 * invents nothing (AD-1) — every property is a straight mapping of the phone's `PlayerState`
 * (see [mprisSnapshot]) and every method forwards to the attached [app.n_zik.compagnon.bridge.state.PlayerRepository].
 *
 * dbus-java's reflection rules drive the shape (verified against the 5.2.0 sources): two public
 * interfaces extending [DBusInterface], one per MPRIS D-Bus interface, each with
 * `@get:DBusBoundProperty` vals (property names strip the `get` — `Identity`, `CanRaise`, …) and
 * `@DBusMemberName` methods (`Raise`, `Quit`, `Next` …); the concrete [MprisObject] implements BOTH
 * (a class implementing [DBusInterface] directly would be exported as a third, bogus interface).
 * `org.freedesktop.DBus.Properties` is added to the export automatically by the `@DBusBoundProperty`
 * annotations, and its `Get`/`GetAll` are answered from the bound getters.
 */
@DBusInterfaceName(MPRIS_ROOT_IFACE)
interface MPRISRoot : DBusInterface {
    @get:DBusBoundProperty
    val identity: String

    /** The Linux package name of the build (`DesktopEntry`, so the desktop environment can open the app). */
    @get:DBusBoundProperty
    val desktopEntry: String

    @get:DBusBoundProperty
    val canRaise: Boolean

    @get:DBusBoundProperty
    val canQuit: Boolean

    /** The MPRIS protocol versions this implementation validates against (KDE reads the list). */
    @get:DBusBoundProperty
    val validated: List<String>

    /** The desktop session type (KDE's `ConnectedDesktop` — the `XDG_SESSION_TYPE` environment, `x11` fallback). */
    @get:DBusBoundProperty
    val connectedDesktop: String

    @DBusMemberName("Raise")
    fun raise()

    @DBusMemberName("Quit")
    fun quit()
}

/** The `org.mpris.MediaPlayer2.Player` interface — the properties and the transport controls. */
@DBusInterfaceName(MPRIS_PLAYER_IFACE)
interface MPRISPlayer : DBusInterface {
    @get:DBusBoundProperty
    val playbackStatus: String

    /**
     * The current track's metadata (`a{sv}` on the wire) — EMPTY while nothing can be shown (MPRIS's
     * "no current track" representation; dbus-java cannot serialize a `null` Variant). Note the
     * introspection XML declares it `a{vv}` (dbus-java's default for an unparameterized Map) while
     * `Get`/`GetAll`/`PropertiesChanged` marshal it `a{sv}` — MPRIS clients consume the wire, not the
     * XML (frozen spec's Implementation Notes).
     */
    @get:DBusBoundProperty
    val metadata: Map<String, Variant<out Any>>

    @get:DBusBoundProperty
    val canGoNext: Boolean

    @get:DBusBoundProperty
    val canGoPrevious: Boolean

    @get:DBusBoundProperty
    val canPlay: Boolean

    @get:DBusBoundProperty
    val canPause: Boolean

    @get:DBusBoundProperty
    val canControl: Boolean

    @DBusMemberName("Next")
    fun next()

    @DBusMemberName("Previous")
    fun previous()

    @DBusMemberName("PlayPause")
    fun playPause()

    @DBusMemberName("Play")
    fun play()

    @DBusMemberName("Pause")
    fun pause()
}

/**
 * The Linux media surface (spec `spec-tray-taskbar-media-controls`): MPRIS over the D-Bus session
 * bus, dbus-java 5.2.0.
 *
 * Degradations (the frozen spec's I/O matrix):
 *  - **no session bus** (headless, no `dbus-run-session` …) → the connection build fails: it is
 *    logged once and every surface hook is a no-op (`connection == null`);
 *  - **bus name already owned** (a zombie instance that never released it) → `requestBusName` throws:
 *    logged, the connection is closed and the surface stays a no-op (we never steal the name of a
 *    live peer);
 *  - **no live session / no `playback` feature** → `PlaybackStatus = Stopped`, every `Can*` false
 *    (the platform itself lives until the process exit, like the Windows ThumbBar — see the base
 *    class's KDoc).
 *
 * `PropertiesChanged` is emitted on every real change (the [propertiesChanged] diff), on every
 * thread-safe `sendMessage` (dbus-java posts it internally). [MprisObject]'s getters run on
 * dbus-java's receiving threads, so the snapshot is `@Volatile`; the pushes themselves (and the
 * artwork file writes' final step) are serialized on the Swing EDT, where the state stream lands.
 */
class MprisMediaControls(
    private val windowController: WindowController,
    private val onQuit: () -> Unit,
) : TaskbarMediaControls() {

    override val log: Logger = Logger.getLogger("MprisMediaControls")

    /** `null` while the session bus is missing (the no-op mode) — the only D-Bus object the surface holds. */
    @Volatile
    private var connection: DBusConnection? = null
    @Volatile
    private var busNameOwned = false
    @Volatile
    private var setupBusy = false
    // The "no session bus" warning is logged once per process (setup retries per session while the
    // bus stays missing — the surface self-heals if a session bus appears later)
    @Volatile
    private var noBusLogged = false

    // The last state the state stream pushed (the EDT fields — every push lands there)
    private var lastState: PlayerState? = null
    private var lastConnection: ConnectionState = ConnectionState.Connecting
    private var lastFeatures: Set<String> = emptySet()

    // The track artwork (the `mpris:artUrl` entry): latest-wins per track, PNG in the app directory
    private val artScope = NzikDispatchers.fireAndForget(NzikDispatchers.DATA)
    private var artJob: Job? = null
    private var artForTrackId: String? = null
    private var artFile: Path? = null
    private var artUrl: String? = null

    /** The exported object — inner so it can forward through the base's [forward]/[onEdt]. */
    private inner class MprisObject : MPRISRoot, MPRISPlayer {
        @Volatile
        var snapshot: MprisSnapshot = stoppedSnapshot()

        // ---- org.mpris.MediaPlayer2 (the root) --------------------------------------------------------

        override val identity: String get() = MPRIS_IDENTITY
        override val desktopEntry: String get() = AppVersion.linuxPackageName
        override val canRaise: Boolean get() = true
        override val canQuit: Boolean get() = true
        override val validated: List<String> get() = MPRIS_VALIDATED
        override val connectedDesktop: String get() = sessionDesktopType()

        /** `Raise` re-shows and focuses the window (dbus-java's receiving thread — the hop is inside). */
        override fun raise() = windowController.showAndFocus()

        /** `Quit` closes the app (on the EDT — Compose's `exitApplication` wants the UI thread). */
        override fun quit() = onEdt { onQuit() }

        // ---- org.mpris.MediaPlayer2.Player -------------------------------------------------------------

        override val playbackStatus: String get() = snapshot.playbackStatus
        override val metadata: Map<String, Variant<out Any>> get() = snapshot.metadata?.toDBusMap() ?: emptyMap()
        override val canGoNext: Boolean get() = snapshot.canGoNext
        override val canGoPrevious: Boolean get() = snapshot.canGoPrevious
        override val canPlay: Boolean get() = snapshot.canPlay
        override val canPause: Boolean get() = snapshot.canPause
        override val canControl: Boolean get() = snapshot.canControl

        // AD-1: the transport controls only forward to the attached repository (no-op when detached)
        override fun next() = forward { next() }
        override fun previous() = forward { previous() }

        /** dbus-java's receiving thread — the decision reads the `@Volatile` snapshot, not the EDT state. */
        override fun playPause() {
            if (snapshot.playbackStatus == PLAYING) forward { pause() } else forward { play() }
        }
        override fun play() = forward { play() }
        override fun pause() = forward { pause() }

        override fun getObjectPath(): String = MPRIS_OBJECT_PATH
    }

    private val mprisObject = MprisObject()

    // ---- Lifecycle hooks --------------------------------------------------------------------------------

    override fun onPlatformSetup() {
        // The D-Bus handshake runs off the EDT (the Unix-socket build + the bus-name request can block)
        if (connection != null || setupBusy) return
        setupBusy = true
        artScope.launch {
            try {
                setup()
            } finally {
                setupBusy = false
            }
        }
    }

    override fun onStateChanged(state: PlayerState?, connection: ConnectionState, features: Set<String>) {
        lastState = state
        lastConnection = connection
        lastFeatures = features
        scheduleArtwork() // clears the stale artwork (and pushes) before the fresh push below
        pushCurrentSnapshot()
    }

    override fun onSessionEnded() {
        // The session is gone: the surface stays up (the bus name lives until the process exit) and
        // reflects "nothing" — the frozen spec's MPRIS Stopped row
        artJob?.cancel()
        artJob = null
        artForTrackId = null
        deleteArtFile()
        artFile = null
        artUrl = null
        push(stoppedSnapshot())
    }

    // ---- Setup ------------------------------------------------------------------------------------------

    /** Builds the connection, exports the object, owns the bus name — all in [NzikDispatchers.DATA]. */
    private fun setup() {
        val conn = try {
            DBusConnectionBuilder.forSessionBus().build()
        } catch (t: Throwable) {
            // The surface self-heals if a session bus appears later (a setup retry on the next attach) —
            // the warning is logged once per process (the KDoc's "logged once" degradation)
            if (!noBusLogged) {
                noBusLogged = true
                log.warning("MPRIS: no session bus — the media surface stays off (${t::class.simpleName})")
            }
            return
        }
        try {
            mprisObject.snapshot = stoppedSnapshot()
            conn.exportObject(mprisObject)
            conn.requestBusName(MPRIS_BUS_NAME)
        } catch (e: Throwable) {
            // The name is taken (a zombie instance) — we never steal a live peer's name; any other
            // failure must not leak the connection or escape the DATA coroutine (no-crash constraint)
            connection = null
            runCatching { conn.close() }
            log.warning("MPRIS: could not own $MPRIS_BUS_NAME — the media surface stays off (${e::class.simpleName})")
            return
        }
        connection = conn
        // The first push lands on the EDT, serialized with the state stream's pushes (a DATA-thread push
        // here could land AFTER a fresh state push and regress the surface to the stopped state)
        onEdt {
            busNameOwned = true
            pushCurrentSnapshot()
        }
        log.info("MPRIS: media player object exported on $MPRIS_BUS_NAME")
    }

    // ---- Snapshot pushes (EDT) ---------------------------------------------------------------------------

    private fun pushCurrentSnapshot() {
        push(mprisSnapshot(lastState, lastConnection, lastFeatures, artUrl))
    }

    /** Stores [snapshot] and, when the bus name is owned and something really changed, announces it. */
    private fun push(snapshot: MprisSnapshot) {
        val old = mprisObject.snapshot
        mprisObject.snapshot = snapshot
        if (!busNameOwned) return
        val changed = propertiesChanged(old, snapshot)
        if (changed.isEmpty()) return
        emitPropertiesChanged(changed, snapshot)
    }

    /** One `PropertiesChanged` (`.Player` interface, no invalidated properties — all seven always exist). */
    private fun emitPropertiesChanged(changed: List<String>, snapshot: MprisSnapshot) {
        val conn = connection ?: return
        val changedValues = changed.associateWith { name -> variantFor(name, snapshot) }
        val signal = try {
            conn.getMessageFactory().createSignal(
                MPRIS_BUS_NAME,
                MPRIS_OBJECT_PATH,
                PROPERTIES_IFACE,
                PROPERTIES_CHANGED,
                "sa{sv}as",
                MPRIS_PLAYER_IFACE,
                changedValues,
                emptyList<String>(),
            )
        } catch (e: DBusException) {
            log.warning("MPRIS: could not build the $PROPERTIES_CHANGED signal (${e::class.simpleName})")
            return
        }
        runCatching { conn.sendMessage(signal) }
            .onFailure { log.warning("MPRIS: could not send $PROPERTIES_CHANGED (${it::class.simpleName})") }
    }

    /** The `a{sv}` value of one changed property — `Metadata` always carries a (possibly empty) dict. */
    private fun variantFor(name: String, snapshot: MprisSnapshot): Variant<out Any> = when (name) {
        "PlaybackStatus" -> Variant(snapshot.playbackStatus)
        "Metadata" -> Variant(snapshot.metadata?.toDBusMap() ?: emptyMap<String, Variant<out Any>>(), METADATA_SIG)
        "CanGoNext" -> Variant(snapshot.canGoNext)
        "CanGoPrevious" -> Variant(snapshot.canGoPrevious)
        "CanPlay" -> Variant(snapshot.canPlay)
        "CanPause" -> Variant(snapshot.canPause)
        "CanControl" -> Variant(snapshot.canControl)
        else -> throw IllegalStateException("Unknown MPRIS property $name")
    }

    // ---- Track artwork (the `mpris:artUrl` entry) ----------------------------------------------------------

    /**
     * Keeps the `mpris:artUrl` entry in sync with the current track (called on the EDT): the PNG is
     * fetched through the phone (gated by `FEATURE_ARTWORK` + `hasArtwork`) and cached in the app
     * directory — KDE reads the `file://` URL, and the phone's own artwork endpoint is Bearer-only.
     * Latest-wins per track: a superseded fetch drops its result.
     */
    private fun scheduleArtwork() {
        val repo = attachedRepository ?: return
        if (connection == null) {
            // The surface is off (no session bus / name not owned): no MPRIS client reads the artwork —
            // do not fetch it over the phone's connection
            if (artForTrackId != null || artFile != null) clearArtwork()
            return
        }
        val track = lastState?.currentTrack
        val wantsArtwork = lastConnection is ConnectionState.Live &&
            SessionContract.FEATURE_ARTWORK in lastFeatures &&
            track != null && track.hasArtwork
        if (!wantsArtwork) {
            clearArtwork()
            return
        }
        if (artForTrackId == track.id) return // the same track is already fetched or fetching
        artJob?.cancel()
        clearArtwork() // the track changed: the old file (and URL) is gone before the fetch even starts
        artForTrackId = track.id
        artJob = artScope.launch {
            val bitmap = runCatching { repo.artwork(ArtworkKey.track(track.id)) }.getOrNull()
            if (artForTrackId != track.id) return@launch // a newer track superseded this fetch
            if (bitmap == null) return@launch
            val file = runCatching { writeArtworkFile(bitmap) }.onFailure {
                log.warning("MPRIS: could not cache the track artwork (${it::class.simpleName})")
            }.getOrNull() ?: return@launch
            onEdt {
                if (artForTrackId != track.id) return@onEdt // a newer track cleared the artwork meanwhile
                artFile = file
                artUrl = file.toUri().toString()
                pushCurrentSnapshot() // the Metadata entry appears: one `PropertiesChanged`
            }
        }
    }

    /** Drops the cached artwork and its URL (EDT) — a no-op push when nothing was cached. */
    private fun clearArtwork() {
        artJob?.cancel()
        artJob = null
        artForTrackId = null
        deleteArtFile()
        artFile = null
        artUrl = null
    }

    private fun deleteArtFile() {
        artFile?.let { file ->
            runCatching { Files.deleteIfExists(file) }
                .onFailure { log.warning("MPRIS: could not delete the cached artwork (${it::class.simpleName})") }
        }
    }

    /** The [bitmap] as a PNG in the app directory (`mpris-artwork.png`, overwritten per track). */
    private fun writeArtworkFile(bitmap: ImageBitmap): Path {
        val image = bitmap.toBufferedImage()
        val dir = CredentialStore.appDirectory()
        Files.createDirectories(dir)
        val file = dir.resolve(ART_FILE_NAME)
        require(ImageIO.write(image, "png", file.toFile())) { "ImageIO.write returned false" }
        return file
    }
}

/** The [metadata]`s `Metadata` dictionary as a D-Bus `a{sv}` — the frozen spec's wire entries only. */
internal fun MprisMetadata.toDBusMap(): Map<String, Variant<out Any>> = buildMap {
    put(MprisMetadata.TRACK_ID, Variant(trackId))
    put(MprisMetadata.TITLE, Variant(title))
    // MPRIS's `xesam:artist` is an array of STRINGS (`as` — review 2026-10-11: `a{vs}` is not a valid
    // wire signature for the entry and KDE clients drop it) — the phone's artists verbatim, blank-free;
    // the empty array is the "no artist" representation
    put(MprisMetadata.ARTIST, Variant(artist.filter { it.isNotBlank() }, "as"))
    lengthUs?.let { put(MprisMetadata.LENGTH, Variant(it)) }
    artUrl?.let { put(MprisMetadata.ART_URL, Variant(it)) }
}

/** The desktop session type for KDE's `ConnectedDesktop` — the `XDG_SESSION_TYPE` environment, `x11` when absent. */
private fun sessionDesktopType(): String =
    System.getenv("XDG_SESSION_TYPE")?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: "x11"

private val MPRIS_VALIDATED: List<String> = listOf("2.0")

private const val MPRIS_BUS_NAME = "org.mpris.MediaPlayer2.nzik_compagnon"
private const val MPRIS_OBJECT_PATH = "/org/mpris/MediaPlayer2"
private const val MPRIS_ROOT_IFACE = "org.mpris.MediaPlayer2"
private const val MPRIS_PLAYER_IFACE = "org.mpris.MediaPlayer2.Player"
private const val PROPERTIES_IFACE = "org.freedesktop.DBus.Properties"
private const val PROPERTIES_CHANGED = "PropertiesChanged"
private const val MPRIS_IDENTITY = "N-Zik"
private const val METADATA_SIG = "a{sv}"
private const val ART_FILE_NAME = "mpris-artwork.png"
