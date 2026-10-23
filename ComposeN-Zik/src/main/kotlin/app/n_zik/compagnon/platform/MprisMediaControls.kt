package app.n_zik.compagnon.platform

import androidx.compose.ui.graphics.ImageBitmap
import app.n_zik.compagnon.generated.AppVersion
import app.n_zik.compagnon.generated.resources.Res
import app.n_zik.compagnon.bridge.ConnectionState
import app.n_zik.compagnon.bridge.state.PlayerState
import app.n_zik.compagnon.bridge.state.SessionContract
import app.n_zik.compagnon.bridge.pairing.CredentialStore
import app.n_zik.compagnon.components.player.PLAYER_ARTWORK_SIZE_PX
import app.n_zik.compagnon.core.network.ArtworkKey
import app.n_zik.compagnon.utils.coroutines.NzikDispatchers
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.logging.Logger
import javax.imageio.ImageIO
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import app.n_zik.compagnon.updater.models.ArtifactNames
import org.freedesktop.dbus.DBusPath
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

    /** The live position (µs) — never in `PropertiesChanged` (the MPRIS rule): a jump is announced by `Seeked`. */
    @get:DBusBoundProperty
    val position: Long

    @get:DBusBoundProperty
    val canSeek: Boolean

    @get:DBusBoundProperty
    val rate: Double

    @DBusMemberName("Seek")
    fun seek(offset: Long)

    @DBusMemberName("SetPosition")
    fun setPosition(trackId: DBusPath, position: Long)

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

    // The last state the state stream pushed (written on the EDT — every push lands there; read by the
    // `Position` getter / `Seek` on dbus-java's receiving threads, hence `@Volatile`)
    @Volatile
    private var lastState: PlayerState? = null
    @Volatile
    private var lastConnection: ConnectionState = ConnectionState.Connecting
    private var lastFeatures: Set<String> = emptySet()

    // The track artwork (the `mpris:artUrl` entry): latest-wins per track, ONE PNG file per track
    // in the app directory (the desktops cache the notification artwork by URI — see [artworkFileName]).
    // When no track artwork applies (no track, no artwork feature, a failed fetch) the app's own
    // fallback tile ([artFallbackUrl]) shows instead — live request 2026-10-12.
    private val artScope = NzikDispatchers.fireAndForget(NzikDispatchers.DATA)
    private var artJob: Job? = null
    private var artForTrackId: String? = null
    private var artFile: Path? = null
    private var artUrl: String? = null
    @Volatile
    private var artFallbackFile: Path? = null
    @Volatile
    private var artFallbackUrl: String? = null

    /** The exported object — inner so it can forward through the base's [forward]/[onEdt]. */
    private inner class MprisObject : MPRISRoot, MPRISPlayer {
        @Volatile
        var snapshot: MprisSnapshot = stoppedSnapshot()

        // ---- org.mpris.MediaPlayer2 (the root) --------------------------------------------------------

        // The per-channel product name — the same name the Windows panel shows (the installed shortcut's)
        override val identity: String get() = ArtifactNames.productName(AppVersion.channel)
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
        override val position: Long get() = currentPositionUs()
        override val canSeek: Boolean get() = snapshot.canSeek
        override val rate: Double get() = snapshot.rate

        /** `Seek(offset)`: relative, from the live position, clamped to the track (dbus-java's thread). */
        override fun seek(offset: Long) {
            if (!snapshot.canSeek) return
            val lengthUs = snapshot.metadata?.lengthUs ?: return
            val targetUs = (currentPositionUs() + offset).coerceIn(0L, lengthUs)
            forward { seek(targetUs / 1000L) }
        }

        /** `SetPosition(trackId, position)`: absolute, ignored when the track is no longer the current one (the MPRIS rule). */
        override fun setPosition(trackId: DBusPath, position: Long) {
            if (!snapshot.canSeek) return
            val metadata = snapshot.metadata ?: return
            val lengthUs = metadata.lengthUs ?: return
            if (trackId.path != mprisTrackPath(metadata.trackId) || position !in 0L..lengthUs) return
            forward { seek(position / 1000L) }
        }

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
        val previous = lastState
        val serverNow = attachedRepository?.serverNowMs()
        lastState = state
        lastConnection = connection
        lastFeatures = features
        scheduleArtwork() // clears the stale artwork (and pushes) before the fresh push below
        pushCurrentSnapshot()
        // A seek (from the phone or any client): the clients extrapolate from Position + Rate, so the
        // discontinuity is announced
        if (serverNow != null && connection is ConnectionState.Live && positionJumped(previous, state, serverNow)) {
            emitSeeked(currentPositionUs())
        }
    }

    /** The live position (µs) for `Position` / `Seek` — any thread (reads the `@Volatile` state). */
    private fun currentPositionUs(): Long {
        val serverNow = attachedRepository?.serverNowMs() ?: return 0L
        return mprisPositionUs(lastState, lastConnection, serverNow)
    }

    /** The `Seeked(x)` signal of the `.Player` interface. */
    private fun emitSeeked(positionUs: Long) {
        val conn = connection ?: return
        if (!busNameOwned) return
        val signal = try {
            conn.getMessageFactory().createSignal(MPRIS_BUS_NAME, MPRIS_OBJECT_PATH, MPRIS_PLAYER_IFACE, SEEKED, "x", positionUs)
        } catch (e: DBusException) {
            log.warning("MPRIS: could not build the $SEEKED signal (${e::class.simpleName})")
            return
        }
        runCatching { conn.sendMessage(signal) }
            .onFailure { log.warning("MPRIS: could not send $SEEKED (${it::class.simpleName})") }
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

    /** Builds the connection, exports the object, owns the bus name — all in [NzikDispatchers.DATA]. */
    private suspend fun setup() {
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
        clearStaleArtwork() // the per-track PNGs of a previous run (one at most — the app was killed mid-track)
        writeArtworkFallback() // the notification's fallback tile — before any state push reads it
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

    /** One `PropertiesChanged` (`.Player` interface, no invalidated properties — every one always exists). */
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
        "CanSeek" -> Variant(snapshot.canSeek)
        "Rate" -> Variant(snapshot.rate)
        else -> throw IllegalStateException("Unknown MPRIS property $name")
    }

    // ---- Track artwork (the `mpris:artUrl` entry) ----------------------------------------------------------

    /**
     * Keeps the `mpris:artUrl` entry in sync with the current track (called on the EDT): the PNG is
     * written from the cover the app already loaded and cached in the app directory — the
     * desktop's `file://` URL is read by KDE/GNOME, and the phone's artwork endpoint is Bearer-only.
     * The cover is looked up under the **player's canonical key** (`track id` + [PLAYER_ARTWORK_SIZE_PX]),
     * the same key the player screen and the M3 dynamic palette use, so the shared in-memory loader
     * serves the already-loaded bitmap instead of a second, separate fetch through the phone (one
     * fetch per track change, not two — and no phone round-trip at all when the app has the cover).
     * The PNG goes to a per-track file ([artworkFileName]) — the desktops cache the notification
     * artwork by its URI, so overwriting one fixed file in place would keep showing the FIRST
     * track's image (live-verified 2026-10-12). Latest-wins per track: a superseded fetch drops
     * its result. When no track artwork applies — no track, no artwork feature, a failed fetch —
     * the app's own fallback tile shows instead ([useFallbackArtwork]).
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
            useFallbackArtwork() // no artwork for this state: the notification shows the app's tile
            return
        }
        if (artForTrackId == track.id) return // the same track is already fetched or fetching
        artJob?.cancel()
        useFallbackArtwork() // the track changed: the app's tile replaces the old cover while the new one loads
        artForTrackId = track.id
        artJob = artScope.launch {
            // Reuse the player's already-loaded cover (the shared in-memory loader's key) rather than
            // re-fetching a second copy through the phone — see the method's KDoc.
            val bitmap = runCatching { repo.artwork(ArtworkKey.track(track.id, PLAYER_ARTWORK_SIZE_PX)) }.getOrNull()
            if (artForTrackId != track.id) return@launch // a newer track superseded this fetch
            if (bitmap == null) {
                onEdt { if (artForTrackId == track.id) useFallbackArtwork() } // the cover could not be loaded
                return@launch
            }
            val file = runCatching { writeArtworkFile(bitmap, track.id) }.onFailure {
                log.warning("MPRIS: could not cache the track artwork (${it::class.simpleName})")
            }.getOrNull() ?: run {
                onEdt { if (artForTrackId == track.id) useFallbackArtwork() }
                return@launch
            }
            onEdt {
                if (artForTrackId != track.id) return@onEdt // a newer track cleared the artwork meanwhile
                artFile = file
                artUrl = file.toUri().toString()
                pushCurrentSnapshot() // the Metadata entry appears: one `PropertiesChanged`
            }
        }
    }

    /**
     * Makes the app's fallback tile the current artwork (EDT): the track has no artwork, its fetch
     * failed, or the cover could not be cached. [artFallbackUrl] is null when the tile could not
     * be written at setup — then the artwork is simply gone (the pre-fallback behavior).
     */
    private fun useFallbackArtwork() {
        val fallbackUrl = artFallbackUrl ?: run {
            clearArtwork()
            return
        }
        if (artForTrackId == null && artUrl == fallbackUrl) return // already on the fallback tile
        artJob?.cancel()
        artJob = null
        artForTrackId = null
        deleteArtFile() // the per-track file goes; the fallback file is [artFallbackFile] — never deleted here
        artFile = null
        artUrl = fallbackUrl
        pushCurrentSnapshot()
    }

    /** Drops the cached artwork and its URL (EDT) — no push: the next state push carries the gap. */
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

    /** The [bitmap] as a PNG in the app directory — one file per track ([artworkFileName]). */
    private fun writeArtworkFile(bitmap: ImageBitmap, trackId: String): Path {
        val image = squareCrop(bitmap.toBufferedImage())
        val dir = CredentialStore.appDirectory()
        Files.createDirectories(dir)
        val file = dir.resolve(artworkFileName(trackId))
        require(ImageIO.write(image, "png", file.toFile())) { "ImageIO.write returned false" }
        return file
    }

    /**
     * Caches the app's artwork fallback tile (the `ic_launcher_box` the player shows for a track
     * without artwork — read through [Res], no graphics context needed) as a static PNG next to the
     * per-track files: the notification shows it while no track artwork applies. The desktops cache
     * it by URI — fine, it never changes; the previous run's copy was already swept by
     * [clearStaleArtwork] (same prefix).
     */
    private suspend fun writeArtworkFallback() {
        runCatching {
            val image = ImageIO.read(ByteArrayInputStream(Res.readBytes(ART_FALLBACK_RESOURCE_PATH)))
                as? BufferedImage
                ?: throw IllegalStateException("the artwork fallback resource could not be decoded")
            val dir = CredentialStore.appDirectory()
            Files.createDirectories(dir)
            val file = dir.resolve(ART_FALLBACK_FILE_NAME)
            require(ImageIO.write(image, "png", file.toFile())) { "ImageIO.write returned false" }
            artFallbackFile = file
            artFallbackUrl = file.toUri().toString()
        }.onFailure { log.warning("MPRIS: could not cache the artwork fallback (${it::class.simpleName})") }
    }

    /** Deletes the per-track artwork files of a previous run (the app was killed between tracks). */
    private fun clearStaleArtwork() {
        val dir = CredentialStore.appDirectory()
        if (!Files.isDirectory(dir)) return
        runCatching {
            Files.list(dir).use { files ->
                files.filter { it.fileName.toString().startsWith(ART_FILE_PREFIX) && it.fileName.toString().endsWith(ART_FILE_SUFFIX) }
                    .forEach { runCatching { Files.deleteIfExists(it) } }
            }
        }.onFailure { log.warning("MPRIS: could not clean the stale artwork files (${it::class.simpleName})") }
    }
}

/** The [metadata]`s `Metadata` dictionary as a D-Bus `a{sv}` — the frozen spec's wire entries only. */
internal fun MprisMetadata.toDBusMap(): Map<String, Variant<out Any>> = buildMap {
    // An object path (`o`), as MPRIS requires — KDE's seek (`SetPosition`) matches it
    put(MprisMetadata.TRACK_ID, Variant(DBusPath(mprisTrackPath(trackId))))
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

/** The MPRIS static bus name — also the single-instance marker ([SingleInstance]). */
internal const val MPRIS_BUS_NAME = "org.mpris.MediaPlayer2.nzik_compagnon"

/** The MPRIS root object's path (the `Raise` of [SingleInstance] lands on it). */
internal const val MPRIS_OBJECT_PATH = "/org/mpris/MediaPlayer2"
private const val MPRIS_ROOT_IFACE = "org.mpris.MediaPlayer2"
private const val MPRIS_PLAYER_IFACE = "org.mpris.MediaPlayer2.Player"
private const val PROPERTIES_IFACE = "org.freedesktop.DBus.Properties"
private const val PROPERTIES_CHANGED = "PropertiesChanged"
private const val SEEKED = "Seeked"
private const val METADATA_SIG = "a{sv}"
/**
 * Center-crops [image] to a 1:1 square (the phone's artwork is not pre-cropped — portrait/wide covers
 * arrive as-is — while the desktops' Now Playing tile and the app's player thumbnails are square,
 * the player's `aspectRatio(1f)` + `ContentScale.Crop`). A square input is returned as-is.
 */
internal fun squareCrop(image: BufferedImage): BufferedImage {
    val side = minOf(image.width, image.height)
    if (side == image.width && side == image.height) return image
    val x = (image.width - side) / 2
    val y = (image.height - side) / 2
    return image.getSubimage(x, y, side, side)
}

/**
 * The per-track artwork file name: a fixed prefix + the hex-encoded track id (the same
 * two-digits-per-byte unsigned encoding as [mprisTrackPath] — always a safe file name) + `.png`.
 * The per-track name (and therefore the `file://` [MprisMetadata.artUrl]) is what makes the
 * desktops drop their artwork cache on a track change — they cache the notification image by URI.
 */
internal fun artworkFileName(trackId: String): String =
    ART_FILE_PREFIX + trackId.toByteArray(Charsets.UTF_8).joinToString("") { "%02x".format(it.toInt() and 0xFF) } + ART_FILE_SUFFIX

private const val ART_FILE_PREFIX = "mpris-artwork-"
private const val ART_FILE_SUFFIX = ".png"

/** The cached fallback tile's file name (the [ART_FILE_PREFIX] sweep covers a previous run's copy). */
private const val ART_FALLBACK_FILE_NAME = "mpris-artwork-fallback.png"

/** The app's own artwork fallback drawable (the player's no-artwork tile). */
private const val ART_FALLBACK_RESOURCE_PATH = "drawable/ic_launcher_box.png"
