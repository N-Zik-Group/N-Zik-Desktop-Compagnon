package app.n_zik.compagnon.platform

import app.n_zik.compagnon.bridge.ConnectionState
import app.n_zik.compagnon.bridge.state.PlayerRepository
import app.n_zik.compagnon.bridge.state.PlayerState
import app.n_zik.compagnon.utils.coroutines.NzikDispatchers
import java.util.logging.Logger
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.swing.SwingUtilities

/**
 * The system-surface shortcuts to the phone's playback (spec `spec-tray-taskbar-media-controls`): the
 * PC invents nothing of the playback (AD-1) — the buttons only forward ⏮⏯⏭ through [PlayerRepository]
 * and the surface reflects the phone's `PlayerState` / `ConnectionState`.
 *
 * Three implementations, one per OS (the frozen spec's architecture): Windows → [WindowsThumbBar]
 * (taskbar Thumbnail Toolbar, `ITaskbarList3`), Linux → [MprisMediaControls] (MPRIS over the D-Bus
 * session bus), anything else → [NoopMediaControls]. One instance per process, app-lifetime, created
 * by [createTaskbarMediaControls].
 *
 * Two lifetimes, kept separate (the frozen spec's I/O matrix — "no session" disables the surface,
 * it does not remove it):
 *  - **the platform** (the COM object + the WndProc subclass on Windows, the D-Bus connection on
 *    Linux) is set up once per process by [onPlatformSetup] on the first [attach] — and never torn
 *    down: the process exit reclaims it, and the JNA WndProc callback must stay strongly referenced
 *    for the app's whole life (the frozen spec's constraint);
 *  - **the session** (a [PlayerRepository]) is [attach]ed when it starts and [detachIfAttached] when
 *    it closes: the state stream stops and [onSessionEnded] reflects "no session" (disabled buttons /
 *    MPRIS `Stopped`).
 *
 * The identity guard in [detachIfAttached] is what makes the attach/detach ordering safe: the bridge
 * screen closes the old repository in a `DisposableEffect`, which can land AFTER a new session has
 * already [attach]ed — a blind detach there would reflect "no session" on the fresh session's surface.
 *
 * The state stream is collected once per [attach] (`PlayerState?` × `ConnectionState`) and pushed to
 * [onStateChanged] on the UI dispatcher — the Swing EDT, where both the AWT calls and the D-Bus
 * signal send are safe. [forward] hops the command to the data dispatcher (the REST call).
 */
abstract class TaskbarMediaControls {
    /** Each implementation re-logs under its own class name (rules/CODE.md "Logging"). */
    protected open val log: Logger = Logger.getLogger("TaskbarMediaControls")
    private val scope = NzikDispatchers.fireAndForget(NzikDispatchers.UI)
    @Volatile
    private var attached: PlayerRepository? = null
    private var collectJob: Job? = null
    private var platformReady = false

    /**
     * Sets the platform's surface up WITHOUT a session (the frozen matrix's `WIN_NO_SESSION` /
     * MPRIS `Stopped` rows: the surface exists, disabled, before any session starts — the app calls
     * this once at start-up, right after creating the instance). Idempotent — [attach] sets the
     * platform up the same way, and a session [attach] after [setupPlatform] reuses the surface
     * without re-initializing COM / D-Bus.
     */
    fun setupPlatform() {
        if (platformReady) return
        platformReady = true
        onPlatformSetup()
    }

    /**
     * Hooks the surface to [repo]: sets the platform up once per process (idempotent across sessions),
     * then streams the state. [repo.features] is read at every push, not flowed — features are stable
     * for the session's life (the phone announces them at the handshake).
     */
    fun attach(repo: PlayerRepository) {
        if (!platformReady) {
            platformReady = true
            onPlatformSetup()
        }
        attached = repo
        collectJob?.cancel()
        collectJob = scope.launch {
            combine(repo.state, repo.connection) { state, connection -> state to connection }
                .collect { (state, connection) ->
                    onStateChanged(state, connection, repo.features)
                }
        }
    }

    /** Detaches only when [repo] is still the attached repository — see the class KDoc on the ordering hazard. */
    fun detachIfAttached(repo: PlayerRepository) {
        if (attached === repo) detach()
    }

    /**
     * The session ended: stops the state stream and reflects "no session" on the surface (disabled
     * buttons / MPRIS `Stopped`). The platform itself is NOT torn down — it lives until the process
     * exits (see the class KDoc), so a later [attach] re-uses it without re-initializing COM / D-Bus.
     */
    fun detach() {
        collectJob?.cancel()
        collectJob = null
        attached = null
        // [detachIfAttached] is invoked from [app.n_zik.compagnon.bridge.state.PlayerRepository.onClosed],
        // which fires on the CLOSING thread — that may be the WS/data thread, not the EDT: the platform's
        // callback must run where the AWT / COM objects were built (no-crash constraint)
        onEdt { onSessionEnded() }
    }

    /** The attached repository, `null` between sessions — the MPRIS implementation reads it to fetch the track artwork. */
    protected val attachedRepository: PlayerRepository? get() = attached

    /**
     * Forwards one playback command to the attached repository (the fire-and-forget of the frozen spec
     * — the result is ignored, the truth comes back through the WebSocket deltas). No-op when detached.
     */
    protected fun forward(command: suspend PlayerRepository.() -> Unit) {
        val repo = attached ?: return
        scope.launch {
            runCatching {
                withContext(NzikDispatchers.DATA) { repo.command() }
            }.onFailure { log.warning("Taskbar command failed: ${it::class.simpleName}") }
        }
    }

    /** Runs [block] on the Swing EDT (a no-op hop when already there). */
    protected fun onEdt(block: () -> Unit) {
        if (SwingUtilities.isEventDispatchThread()) block() else SwingUtilities.invokeLater(block)
    }

    /**
     * Called on the Swing EDT right before the app's window is (re-)shown (the tray's « Ouvrir »,
     * MPRIS `Raise()`): the platform (re)publishes its surface association for the fresh show. The
     * Windows taskbar's thumbnail button reads the stored toolbar only when it (re)creates itself
     * on the show, so the toolbar must be (re)added BEFORE the show, not after it (live-verified
     * 2026-10-11: add/update calls that land after the fresh button exists report S_OK into the
     * void). Default no-op — MPRIS publishes on the D-Bus, independent of the window's show.
     */
    open fun onWindowShow() {}

    /** Sets the platform's surface up. Called once per process, on the EDT, from [attach]. */
    protected abstract fun onPlatformSetup()

    /** One push of the phone's state; called on the EDT for every `PlayerState?` / `ConnectionState` change. */
    protected abstract fun onStateChanged(state: PlayerState?, connection: ConnectionState, features: Set<String>)

    /**
     * The attached session closed: the platform stays up, the surface reflects "no session" (the frozen
     * spec's `WIN_NO_SESSION` / `Stopped` rows). Called on the EDT, from [detach].
     */
    protected abstract fun onSessionEnded()

    companion object {
        /** The process's one instance, per OS (the frozen spec's three-way split). */
        fun createTaskbarMediaControls(windowController: WindowController, onQuit: () -> Unit): TaskbarMediaControls {
            val os = System.getProperty("os.name", "").lowercase()
            return when {
                os.startsWith(OS_WINDOWS) -> WindowsThumbBar(windowController)
                os.startsWith(OS_LINUX) -> MprisMediaControls(windowController, onQuit)
                else -> NoopMediaControls()
            }
        }

        private const val OS_WINDOWS = "windows"
        private const val OS_LINUX = "linux"
    }
}
