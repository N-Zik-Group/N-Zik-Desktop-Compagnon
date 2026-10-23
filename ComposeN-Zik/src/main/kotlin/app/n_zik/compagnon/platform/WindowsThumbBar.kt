package app.n_zik.compagnon.platform

import app.n_zik.compagnon.bridge.ConnectionState
import app.n_zik.compagnon.bridge.state.PlayerState
import app.n_zik.compagnon.bridge.state.SessionContract
import app.n_zik.compagnon.components.player.PLAYER_ARTWORK_SIZE_PX
import app.n_zik.compagnon.core.network.ArtworkKey
import app.n_zik.compagnon.utils.coroutines.NzikDispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import com.sun.jna.Callback
import com.sun.jna.CallbackReference
import com.sun.jna.Function
import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.Guid
import com.sun.jna.ptr.PointerByReference
import java.awt.Color
import java.awt.Graphics2D
import java.awt.image.BufferedImage
import java.util.logging.Logger
import javax.swing.Timer

/**
 * The Windows taskbar Thumbnail Toolbar (spec `spec-tray-taskbar-media-controls`): ⏮⏯⏭ on the window's
 * taskbar thumbnail, through `ITaskbarList3` (COM, vtable-only — no IDispatch, so the calls go through
 * the raw vtable) with a JNA WndProc callback (**strongly referenced** — the frozen spec's
 * constraint; a GC'd trampoline would crash on the next taskbar click).
 *
 * Live-verified on the dev machine (SDK 10.0.26100.0 `ShObjIdl_core.h` + a compiled C probe, both S_OK):
 * the vtable slots are [SLOT_HR_INIT] = 3, [SLOT_THUMBBAR_ADD_BUTTONS] = 15, [SLOT_THUMBBAR_UPDATE_BUTTONS] = 16
 * (the frozen spec's "16/17" was an off-by-one of `ITaskbarList2` — corrected per the spec's own
 * "valider en direct … et corriger si besoin"), and [THUMBBUTTON_SIZE] = 552 bytes with
 * `dwMask@0, iId@4, iBitmap@8, hIcon@16, szTip@24, dwFlags@544`.
 *
 * The platform is app-lifetime: set up once on the first [TaskbarMediaControls.attach] (the window is
 * then attached to the [WindowController]), never torn down — the process exit reclaims the COM object,
 * the window proc and the icons. Without a live session with the `playback` feature the buttons render
 * disabled ([THBF_DISABLED]); a click is forwarded to the attached repository only (AD-1: no local
 * state — the ⏯ decides play vs. pause from the last `isPlaying` it saw). The buttons are restored
 * on every re-show ([reShowButtons] on WM_SHOWWINDOW): hiding the window (the X = minimize-to-tray
 * behavior) drops the taskbar thumbnail WITH the toolbar, so the fresh thumbnail would come back
 * empty.
 *
 * Live-verified JNA findings on this machine (JNA 5.19.1, JDK 25 — standalone probes; see the spec's
 * Implementation Notes): `Pointer.NULL` is literally `null`; the jna-platform `HANDLE` mappings
 * sign-extend 32-bit handles to 64; the `WinDef.WPARAM`/`LPARAM` callback arguments surface as opaque
 * pointers (reading them threw on every window message); `CreateIconIndirect` rejects the machine's
 * DIB sections with ERROR_INVALID_PARAMETER; user32 exports only the `...W` variants of
 * `RegisterWindowMessage`/`GetWindowLongPtr`/`SetWindowLongPtr`/`DefWindowProc` (plain names are
 * not exports, and JNA does not fall back to the suffix on custom interfaces). Hence: raw 64-bit
 * handles through [User32RawApi] named by their exact export names, a primitive-long
 * [WndProcCallback], icons built from raw planes via the legacy `CreateIcon`, and [setup] wrapped
 * so a native failure can never escape into the EDT (frozen spec: the app must not crash).
 */
class WindowsThumbBar(private val windowController: WindowController) : TaskbarMediaControls() {
    override val log: Logger = Logger.getLogger("WindowsThumbBar")

    // The taskbar side of the window: 0 until the setup ran
    private var hwnd: Long = 0
    private var taskbarButtonCreatedMsg: Int = 0
    /** The `ITaskbarList3` COM pointer (vtable at offset 0); `null` before the setup. */
    private var taskbar: Pointer? = null

    // The WndProc subclass — every field kept for the app's lifetime: `wndProcCallback` is the strong
    // reference that keeps the JNA trampoline alive, `wndProcPointer` the trampoline installed in the

    // window, `originalWndProc` what every non-taskbar message is forwarded to (the Compose window
    // proc — forwarding to DefWindowProc instead would drop the Compose behavior, so no).
    private var wndProcCallback: WndProcCallback? = null
    private var wndProcPointer: Long = 0
    private var originalWndProc: Long = 0

    // The buttons: the last specs (driving the enable/glyph updates) and the per-glyph HICONs —
    // created once, kept until the process exit (the taskbar references them through the buttons)

    // (internal read: the tests check a push landing before the setup is kept)
    internal var specs: List<ThumbBarSpec> = thumbBarSpecs(null, ConnectionState.Connecting, emptySet())
        private set
    private var icons: MutableMap<IconGlyph, IconHandle> = mutableMapOf()

    // The retry timer for an unrealized window (see [scheduleSetupRetry]) — `null` while not retrying
    private var setupRetry: Timer? = null

    // `true` once [setup] hit a hard failure (a native error — e.g. a Win32 function this Windows
    // build does not export): [setup] is then permanently off, so the app stays stable and there is

    // no retry log on every state push (frozen spec: the taskbar buttons must never crash the app)
    private var setupFatal = false

    // The taskbar's thumbnail button is re-created when the window is hidden and re-shown, and it
    // picks up the stored toolbar ONLY at its own creation (live-verified 2026-10-11: both the add

    // and the update report S_OK into the void on the fresh button — the buttons never appeared).
    // So [onWindowShow] (the pre-show hook, [WindowController.onShowHook]) re-adds the toolbar

    // BEFORE the show, and [reShowButtons] (WM_SHOWWINDOW) runs the bounded retry as the
    // belt-and-braces. `hiddenSinceLastShow` distinguishes the first show from a re-show
    private var hiddenSinceLastShow = false

    // `true` when the setup ran before the window's first show: the toolbar added then never
    // appears on the button the show creates (live-observed 2026-10-12), so the first

    // WM_SHOWWINDOW recreates the button a beat later ([scheduleFirstShowRecreate])
    private var firstShowPending = false

    // The bounded re-add retry cycle of [scheduleRepushRetry] (`null` while not retrying) — the
    // backstop for a state push that raced a (re)created thumbnail button
    private var stateRetimer: Timer? = null

    // The « Now Playing » panel (SMTC) on the same window — bound right after the taskbar setup.
    // AD-1: its buttons only forward to the attached repository
    private val smtc = WindowsSmtc(
        log,
        onButton = { button ->
            when (button) {
                SmtcButton.Play -> forward { play() }
                SmtcButton.Pause -> forward { pause() }
                SmtcButton.Next -> forward { next() }
                SmtcButton.Previous -> forward { previous() }
            }
        },
        // The panel's progress bar: a seek forwarded like the app's own seek bar
        onSeek = { positionMs -> forward { seek(positionMs) } },
    )

    // The last state push (EDT) — the SMTC artwork is fetched for its current track
    private var lastState: PlayerState? = null
    private var lastConnection: ConnectionState = ConnectionState.Connecting
    private var lastFeatures: Set<String> = emptySet()

    // The SMTC artwork: latest-wins per track, PNG bytes streamed to the panel
    private val artScope = NzikDispatchers.fireAndForget(NzikDispatchers.DATA)
    private var artJob: Job? = null
    private var artForTrackId: String? = null

    override fun onPlatformSetup() {
        onEdt {
            if (taskbar == null) setup()
        }
    }

    override fun onWindowShow() {
        // The taskbar's thumbnail button (re)created on the show reads the stored toolbar at its
        // own creation: (re)add it BEFORE the show makes the window visible (the caller —

        // WindowController.showAndFocus — runs it on the EDT, right before setVisible(true))
        val ok = reAddButtons()
        log.info("ThumbBar: pre-show toolbar re-add — ok=$ok, enabled=${specs.all { it.enabled }}")
    }

    // Public (widened from the base's protected) so the tests can push a state without a session
    public override fun onStateChanged(state: PlayerState?, connection: ConnectionState, features: Set<String>) {
        // Kept even before the setup: the first addButtons then carries the live state, not the
        // disabled defaults (a push that lands before the setup would otherwise be lost)
        specs = thumbBarSpecs(state, connection, features)
        lastState = state
        lastConnection = connection
        lastFeatures = features
        // The panel keeps the view until its setup when it is not up yet
        // The progress bar's position: the phone's position extrapolated to now (the panel then runs

        // the bar on its own from the rate, until the next push)
        val positionMs = attachedRepository?.serverNowMs()?.let { now -> state?.extrapolatedPositionMs(now) } ?: 0L
        smtc.show(smtcView(mprisSnapshot(state, connection, features, artUrl = null), positionMs))
        scheduleSmtcArtwork()
        if (taskbar == null) {
            // The window may not have been realized when the first attach ran the setup (a session
            // auto-reconnects at startup, before the window's first show): retry on each state push

            // until the buttons are installed — setup is guarded, a no-op once the taskbar is up
            setup()
            return
        }
        if (!updateButtons()) {
            // The push raced a (re)created thumbnail button — re-add with bounded retries, or the
            // buttons would stay in the last successfully-pushed state (e.g. the disabled specs the

            // very first addButtons carried at setup); the full hide→show case is [reShowButtons]
            scheduleRepushRetry("state push")
        }
    }

    override fun onSessionEnded() {
        lastState = null
        lastConnection = ConnectionState.Connecting
        lastFeatures = emptySet()
        smtc.show(smtcView(stoppedSnapshot()))
        scheduleSmtcArtwork()
        if (taskbar == null) return
        // The session is gone: the buttons stay on the thumbnail, disabled (the frozen spec's WIN_NO_SESSION row)
        specs = thumbBarSpecs(null, ConnectionState.Connecting, emptySet())
        updateButtons()
    }

    private fun setup() {
        if (setupFatal) return
        val window = windowController.currentWindow()
        // The HWND is the pointer's OWN value (`Pointer.nativeValue`), not a dereference of it —
        // `getLong(0)` reads memory AT the handle address and dies with "Invalid memory access"

        // (probe-verified: nativeValue → IsWindow=true, getLong(0) → java.lang.Error)
        val hwndValue = window
            ?.let { runCatching { Native.getComponentPointer(it) }.getOrNull() }
            ?.let { Pointer.nativeValue(it) }
        if (window == null || hwndValue == null || hwndValue == 0L) {
            // The window is not realized yet (a session auto-reconnects at startup, before the window's
            // first show) — state pushes are sparse, so retry on a timer, not only on state changes
            log.warning("ThumbBar: no realized window to install the buttons on — retrying")
            scheduleSetupRetry()
            return
        }
        try {
            // The COM apartment of the thread that pumps the window's messages (the Swing EDT);
            // HRESULTs come back as plain ints through the [Ole32Api] mapping (the house JNA pattern)
            val coResult = Ole32Api.INSTANCE.CoInitializeEx(null, COINIT_APARTMENTTHREADED)
            if (coResult != S_OK && coResult != S_FALSE && coResult != RPC_E_CHANGED_MODE) {
                log.warning("ThumbBar: CoInitializeEx failed (0x${Integer.toHexString(coResult)})")
                return
            }
            val com = PointerByReference()
            val hr = Ole32Api.INSTANCE.CoCreateInstance(
                Guid.CLSID(CLSID_TASKBAR_LIST),
                null,
                CLSCTX_INPROC_SERVER,
                Guid.IID(IID_IUNKNOWN),
                com,
            )
            if (hr != S_OK || com.value == null) {
                log.warning("ThumbBar: CoCreateInstance(IID_IUnknown) failed (0x${Integer.toHexString(hr)})")
                return
            }
            hwnd = hwndValue
            // Explorer broadcasts the registered message when its taskbar (re)starts: the buttons are
            // then (re)added from the WndProc, so an explorer restart never loses them — a 0 result

            // means the registration failed (rare): the buttons still work, they just won't self-heal
            // after an explorer restart, and the WndProc guards against matching WM_NULL
            taskbarButtonCreatedMsg = User32RawApi.INSTANCE.RegisterWindowMessageW(TASKBAR_BUTTON_CREATED)
            if (taskbarButtonCreatedMsg == 0) {
                log.warning("ThumbBar: TaskbarButtonCreated registration failed — no self-heal after an explorer restart")
            }
            if (!installWindowProc()) {
                scheduleSetupRetry()
                return
            }
            taskbar = com.value
            stopSetupRetry()
            comCall(SLOT_HR_INIT)
            addButtons()
            if (window.isShowing) recreateTaskbarButton() else firstShowPending = true
            log.info("ThumbBar: taskbar media buttons installed")
            // The Now Playing panel on the same window (its own failure keeps the taskbar buttons up)
            if (smtc.setup(hwnd)) scheduleSmtcArtwork()
        } catch (t: Throwable) {
            // A native failure (e.g. an `UnsatisfiedLinkError` for a function this Windows build does
            // not export) is permanent — it used to escape into the EDT and kill the event thread.

            // The buttons stay off for the app's lifetime, the window keeps its original proc (the
            // subclass is installed only after [installWindowProc] succeeds), the rest of the app is

            // untouched (frozen spec: no crash under any condition)
            setupFatal = true
            taskbar = null
            stopSetupRetry()
            log.log(java.util.logging.Level.WARNING, "ThumbBar: setup failed permanently — the taskbar buttons stay off", t)
        }
    }

    /**
     * Retries [setup] on the Swing EDT every [SETUP_RETRY_DELAY_MS] until the window is realized — the
     * first attach can land before the window's first show (the session auto-reconnects at startup) and
     * state pushes are too sparse to rely on. Bounded by [SETUP_RETRY_MAX_ATTEMPTS]; the state-push
     * retry in [onStateChanged] stays as the backstop after the attempts are exhausted.
     */
    private fun scheduleSetupRetry() {
        if (setupFatal || setupRetry != null || taskbar != null) return
        var attempts = 0
        val timer = Timer(SETUP_RETRY_DELAY_MS) {
            if (taskbar != null) {
                stopSetupRetry()
            } else {
                attempts += 1
                if (attempts >= SETUP_RETRY_MAX_ATTEMPTS) {
                    log.warning("ThumbBar: window still not realized after $attempts retries — giving up (state pushes keep the backstop)")
                    stopSetupRetry()
                } else {
                    setup()
                }
            }
        }
        // The (int, ActionListener) constructor already repeats
        setupRetry = timer
        timer.start()
    }

    private fun stopSetupRetry() {
        // `stop()` — this JDK's `javax.swing.Timer` has no `cancel()` (javap-verified on JDK 25)
        setupRetry?.stop()
        setupRetry = null
    }

    /** Subclasses the window proc: the taskbar's clicks and the TaskbarButtonCreated broadcast are
     *  intercepted, every other message is forwarded to the ORIGINAL proc (the Compose one).
     *  Returns `false` when the subclass could not be installed — the caller must not proceed with
     *  the taskbar setup, because a half-installed proc would swallow the window's messages. */
    private fun installWindowProc(): Boolean {
        val user32 = User32RawApi.INSTANCE
        val original = user32.GetWindowLongPtrW(hwnd, GWLP_WNDPROC)
        if (original == 0L) {
            log.warning("ThumbBar: no original window proc to forward to — backing out")
            return false
        }
        originalWndProc = original
        // Primitive longs for HWND/WPARAM/LPARAM/LRESULT: the jna-platform `WinDef.WPARAM`/`LPARAM`
        // structures surface as OPAQUE pointers in the callback — `toPointer().getLong(0)` threw

        // on every single window message, freezing the Compose window and crashing the JVM in
        // awt.dll (probe-verified on this machine)
        val callback = object : WndProcCallback {
            override fun callback(hWnd: Long, nMsg: Int, wParam: Long, lParam: Long): Long {
                if (taskbarButtonCreatedMsg != 0 && nMsg == taskbarButtonCreatedMsg) {
                    log.info("ThumbBar: TaskbarButtonCreated received — re-adding the toolbar")
                    addButtons()
                    return 0L
                }
                if (nMsg == WM_COMMAND) {
                    // HIWORD(wParam) == 0x1800 marks a taskbar thumbnail click; LOWORD is the button id
                    if ((wParam ushr 16).toInt() == THBN_CLICKED) {
                        onButtonClicked((wParam and 0xFFFFL).toInt())
                        return 0L
                    }
                }
                if (nMsg == WM_SHOWWINDOW) {
                    if (wParam == 0L) {
                        hiddenSinceLastShow = true
                    } else if (firstShowPending) {
                        firstShowPending = false
                        scheduleFirstShowRecreate()
                    } else if (hiddenSinceLastShow) {
                        // The window came back from a full hide (the tray): the taskbar's fresh
                        // thumbnail button may not exist yet — run the bounded re-add retry
                        hiddenSinceLastShow = false
                        reShowButtons()
                    }
                }
                return forwardToOriginal(nMsg, wParam, lParam)
            }
        }
        wndProcCallback = callback
        val trampoline = CallbackReference.getFunctionPointer(callback)
        if (trampoline == null) {
            log.warning("ThumbBar: could not obtain the window-proc trampoline")
            return false
        }
        wndProcPointer = Pointer.nativeValue(trampoline)
        if (user32.SetWindowLongPtrW(hwnd, GWLP_WNDPROC, wndProcPointer) == 0L) {
            log.warning("ThumbBar: SetWindowLongPtr(GWLP_WNDPROC) failed — the window proc was not subclassed")
            return false
        }
        return true
    }

    private fun forwardToOriginal(nMsg: Int, wParam: Long, lParam: Long): Long {
        val original = originalWndProc
        if (original != 0L) {
            // JNA's Function.invokeLong takes the argument array (not varargs); the WndProc ABI is
            // (HWND, UINT, WPARAM, LPARAM) — on x64 Windows the single calling convention already is

            // stdcall, [Function.ALT_CONVENTION] being the 32-bit-compatible spelling
            return Function.getFunction(Pointer(original), Function.ALT_CONVENTION)
                .invokeLong(arrayOf<Any?>(hwnd, nMsg, wParam, lParam))
        }
        return User32RawApi.INSTANCE.DefWindowProcW(hwnd, nMsg, wParam, lParam)
    }

    private fun onButtonClicked(id: Int) {
        // AD-1: no local state — the last-seen glyph decides play vs. pause, and every command goes
        // to the attached repository (the base's no-op when the session is gone)
        when (id) {
            ThumbBarButton.Previous.id -> forward { previous() }
            ThumbBarButton.PlayPause.id ->
                if (specs.firstOrNull { it.button == ThumbBarButton.PlayPause }?.glyph == PlayPauseGlyph.Pause) forward { pause() }
                else forward { play() }
            ThumbBarButton.Next.id -> forward { next() }
        }
    }

    /** One vtable call on the `ITaskbarList3` pointer: slot [slot] (0-based, live-verified — see the class KDoc). */
    private fun comCall(slot: Int, vararg args: Any?): Int {
        val comPtr = taskbar ?: return E_FAIL
        val vtable = comPtr.getPointer(0) ?: return E_FAIL
        val entry = vtable.getPointer(slot.toLong() * VTABLE_STRIDE) ?: return E_FAIL
        // The COM `this` pointer goes first, then the method's own arguments (JNA's invokeInt takes
        // the argument array — not varargs); see [forwardToOriginal] for the calling-convention note
        return Function.getFunction(entry, Function.ALT_CONVENTION).invokeInt(arrayOf(comPtr, *args))
    }

    private fun addButtons() {
        val comPtr = taskbar ?: return
        val buttons = buildButtons()
        try {
            val hr = comCall(SLOT_THUMBBAR_ADD_BUTTONS, hwnd, BUTTONS, buttons)
            if (hr != S_OK) log.warning("ThumbBar: ThumbBarAddButtons failed (0x${Integer.toHexString(hr)})")
        } finally {
            buttons.close()
        }
    }

    /** Re-pushes the current specs on every state change — it doubles as the self-heal for a taskbar
     *  that lost the buttons without broadcasting the registered message (then the add is retried).
     *  Returns `false` when no taskbar is up or both COM calls failed ([scheduleRepushRetry] then
     *  re-adds, with bounded retries). */
    private fun updateButtons(): Boolean {
        val comPtr = taskbar ?: return false
        val buttons = buildButtons()
        try {
            var hr = comCall(SLOT_THUMBBAR_UPDATE_BUTTONS, hwnd, BUTTONS, buttons)
            if (hr != S_OK) hr = comCall(SLOT_THUMBBAR_ADD_BUTTONS, hwnd, BUTTONS, buttons)
            return hr == S_OK
        } finally {
            buttons.close()
        }
    }

    /** Re-pushes the buttons after the window comes back from a full hide (the X = minimize-to-tray
     *  behavior). The pre-show hook ([onWindowShow]) already (re)added the toolbar before the taskbar
     *  (re)created its button; this covers the rare case where the taskbar creates the button AFTER
     *  the show — the retries run regardless of the immediate result, because the add reports S_OK
     *  into the void when it lands before the button exists (live-verified 2026-10-11). On the EDT
     *  (the WndProc thread). */
    private fun reShowButtons() {
        if (taskbar == null) return
        reAddButtons()
        scheduleRepushRetry("re-show")
    }

    /** The setup usually lands AFTER the window's first show (the window is attached during its own
     *  composition, the setup retries on a timer): the taskbar button then already exists, the
     *  TaskbarButtonCreated broadcast went by before the subclass, and the add just made reports
     *  S_OK into the void (the button reads the toolbar only at its creation — see
     *  [hiddenSinceLastShow]). Deleting and re-adding the tab makes the taskbar create a fresh
     *  button that reads the stored toolbar — the tray's hide→show path, without hiding the window.
     *  ActivateTab keeps the button marked as the active one. */
    private fun recreateTaskbarButton() {
        val deleted = comCall(SLOT_DELETE_TAB, hwnd)
        val added = comCall(SLOT_ADD_TAB, hwnd)
        comCall(SLOT_ACTIVATE_TAB, hwnd)
        log.info("ThumbBar: taskbar button recreated after the first show — delete=0x${Integer.toHexString(deleted)}, add=0x${Integer.toHexString(added)}")
        // Belt-and-braces: the taskbar may create the fresh button a beat later
        scheduleRepushRetry("first show")
    }

    /**
     * Keeps the SMTC artwork on the current track (EDT): looked up under the player's canonical
     * key ([smtcArtworkKey] — `track id` + [PLAYER_ARTWORK_SIZE_PX], the same key the player screen
     * uses, gated by `FEATURE_ARTWORK` + `hasArtwork` like MPRIS), so the shared in-memory loader
     * serves the already-loaded bitmap instead of a second fetch through the phone (one fetch per
     * track change, not two — the MPRIS `artUrl` twin's rule). Latest-wins: a superseded fetch
     * drops its result.
     */
    private fun scheduleSmtcArtwork() {
        if (!smtc.isUp) return
        val track = lastState?.currentTrack
        val wantsArtwork = lastConnection is ConnectionState.Live &&
            SessionContract.FEATURE_ARTWORK in lastFeatures &&
            track != null && track.hasArtwork
        if (!wantsArtwork) {
            if (artForTrackId != null) {
                artJob?.cancel()
                artForTrackId = null
                smtc.setThumbnail(null)
            }
            return
        }
        if (artForTrackId == track.id) return
        val repo = attachedRepository ?: return
        artJob?.cancel()
        artForTrackId = track.id
        artJob = artScope.launch {
            val png = runCatching { repo.artwork(smtcArtworkKey(track.id)) }.getOrNull()
                ?.let { bitmap -> runCatching { bitmap.toPngBytes() }.getOrNull() }
            onEdt {
                if (artForTrackId == track.id) smtc.setThumbnail(png)
            }
        }
    }

    /** The first show after a pre-show setup: the button is recreated once the show has settled — never from inside WM_SHOWWINDOW itself (the taskbar is mid-way through creating the button). */
    private fun scheduleFirstShowRecreate() {
        log.info("ThumbBar: first show — recreating the taskbar button in ${FIRST_SHOW_RECREATE_DELAY_MS}ms")
        val timer = Timer(FIRST_SHOW_RECREATE_DELAY_MS) { event ->
            // One-shot: the firing timer stops itself (see [scheduleRepushRetry])
            (event.source as? Timer)?.stop()
            recreateTaskbarButton()
        }
        timer.start()
    }

    /** Re-adds the current specs (the same button ids replace the existing ones). */
    private fun reAddButtons(): Boolean {
        val comPtr = taskbar ?: return false
        val buttons = buildButtons()
        try {
            return comCall(SLOT_THUMBBAR_ADD_BUTTONS, hwnd, BUTTONS, buttons) == S_OK
        } finally {
            buttons.close()
        }
    }

    /** Bounded delayed re-adds of the current specs: the taskbar may (re)create its thumbnail button
     *  a beat after a window show — a push that lands before the button exists is reported S_OK into
     *  the void, so the cycle runs ALL its attempts (no early stop on a "successful" push — S_OK is
     *  not evidence the buttons are visible; the add is idempotent, same button ids replace).
     *  Without it, the buttons would stay in whatever state the last push the taskbar honored left
     *  them (e.g. the disabled specs the very first [addButtons] carried). */
    private fun scheduleRepushRetry(why: String) {
        stateRetimer?.stop()
        stateRetimer = null
        var attempt = 0
        val timer = Timer(RESHOW_REPUSH_DELAY_MS) { event ->
            attempt += 1
            val ok = reAddButtons()
            log.info("ThumbBar: repush retry ($why) — attempt $attempt, ok=$ok")
            if (attempt >= RESHOW_REPUSH_MAX_ATTEMPTS) {
                stateRetimer = null
                // Self-stopping: the firing timer is the event source (a one-shot built from the
                // repeating constructor — this JDK's `Timer` has no `cancel()` and no `repeats` setter)
                (event.source as? Timer)?.stop()
            }
        }
        stateRetimer = timer
        timer.start()
    }

    /** The three [THUMBBUTTON] rows, written at their live-verified offsets (icon-only: no tooltip). */
    private fun buildButtons(): Memory {
        val memory = Memory(BUTTONS.toLong() * THUMBBUTTON_SIZE)
        specs.forEachIndexed { index, spec ->
            val base = index.toLong() * THUMBBUTTON_SIZE
            memory.setInt(base + OFFSET_DW_MASK, THB_ICON or THB_FLAGS)
            memory.setInt(base + OFFSET_ID, spec.button.id)
            memory.setLong(base + OFFSET_H_ICON, iconFor(spec))
            memory.setInt(base + OFFSET_DW_FLAGS, if (spec.enabled) 0 else THBF_DISABLED)
        }
        return memory
    }

    private fun iconFor(spec: ThumbBarSpec): Long {
        val glyph = when (spec.button) {
            ThumbBarButton.Previous -> IconGlyph.Previous
            ThumbBarButton.PlayPause -> when (spec.glyph) {
                PlayPauseGlyph.Pause -> IconGlyph.Pause
                else -> IconGlyph.Play
            }
            ThumbBarButton.Next -> IconGlyph.Next
        }
        return icons.getOrPut(glyph) { createIcon(glyph) }.hIcon
    }

    // Icon building (Java2D glyph → 1-bit AND mask + 32-bit color plane → legacy CreateIcon)

    private enum class IconGlyph { Previous, Play, Pause, Next }

    /** The HICON handle, created once and kept until the process exit (the taskbar references it
     *  through the button); `0` when creation failed — the button then carries no icon. */
    private class IconHandle(val hIcon: Long)

    /** The 16×16 glyph of [glyph] as a 32-bit color icon via the legacy `CreateIcon` (the frozen
     *  spec's size, kept as a constant). The DIB route (`CreateIconIndirect`) rejects this
     *  machine's DIB sections with ERROR_INVALID_PARAMETER (probe-verified), so the icon is built
     *  from raw planes: a 1-bit AND mask and a 32-bit bottom-up color plane. */
    private fun createIcon(glyph: IconGlyph): IconHandle {
        val image = BufferedImage(ICON_SIZE, ICON_SIZE, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON)
        g.color = GLYPH_COLOR
        drawGlyph(g, glyph)
        g.dispose()

        // The 1-bit AND mask: ICON_SIZE/8 bytes per row, a set bit = transparent. A pixel is marked
        // transparent below the 128 alpha threshold so antialiased edges never draw a hard box (the

        // color plane keeps the true values either way)
        val andMask = ByteArray(ICON_SIZE / 8 * ICON_SIZE)
        // The 32-bit color (XOR) plane: one 0xAARRGGBB word per pixel, bottom-up (memory row 0 is
        // the icon's bottom row)
        val colorPlane = ByteArray(ICON_SIZE * ICON_SIZE * 4)
        for (y in 0 until ICON_SIZE) {
            for (x in 0 until ICON_SIZE) {
                val argb = image.getRGB(x, y)
                if (argb ushr 24 and 0xFF < TRANSPARENT_ALPHA_THRESHOLD) {
                    val byte = y * (ICON_SIZE / 8) + x / 8
                    andMask[byte] = (andMask[byte].toInt() or (1 shl (7 - x % 8))).toByte()
                }
                val base = (ICON_SIZE - 1 - y) * ICON_SIZE * 4 + x * 4
                colorPlane[base] = argb.toByte()
                colorPlane[base + 1] = (argb shr 8).toByte()
                colorPlane[base + 2] = (argb shr 16).toByte()
                colorPlane[base + 3] = (argb shr 24).toByte()
            }
        }
        val andMem = Memory(ICON_SIZE.toLong() / 8 * ICON_SIZE)
        andMem.write(0, andMask, 0, andMask.size)
        val colorMem = Memory(ICON_SIZE.toLong() * ICON_SIZE * 4)
        colorMem.write(0, colorPlane, 0, colorPlane.size)
        // cPlanes = 1, cBitsPixel = 32 — the documented 32-bit color-icon spelling (the plane sizes
        // above are exactly what that layout reads)
        val hIcon = User32RawApi.INSTANCE.CreateIcon(0, ICON_SIZE, ICON_SIZE, 1, 32, andMem, colorMem)
        if (hIcon == 0L) {
            log.warning("ThumbBar: CreateIcon failed (error ${Native.getLastError()})")
            return IconHandle(0L)
        }
        return IconHandle(hIcon)
    }

    private fun drawGlyph(g: Graphics2D, glyph: IconGlyph) {
        // The geometric glyphs (deterministic, no font): the shapes are within the 16×16 grid,
        // vertically centered on y = 3..13
        when (glyph) {
            IconGlyph.Previous -> {
                g.fill(java.awt.Rectangle(2, 3, 3, 10))
                g.fillPolygon(intArrayOf(13, 13, 5), intArrayOf(3, 13, 8), 3)
            }
            IconGlyph.Play -> g.fillPolygon(intArrayOf(4, 13, 4), intArrayOf(3, 8, 13), 3)
            IconGlyph.Pause -> {
                g.fill(java.awt.Rectangle(3, 3, 3, 10))
                g.fill(java.awt.Rectangle(10, 3, 3, 10))
            }
            IconGlyph.Next -> {
                g.fillPolygon(intArrayOf(3, 11, 3), intArrayOf(3, 8, 13), 3)
                g.fill(java.awt.Rectangle(11, 3, 3, 10))
            }
        }
    }

    /** `ole32` with the COM bootstrap as plain ints — the house JNA pattern (the jna-platform `Ole32` maps the HRESULTs to a JNA `NativeLong` that this codebase does not want to unwrap). */
    @Suppress("FunctionName")
    private interface Ole32Api : Library {
        fun CoInitializeEx(pUnkReserved: Pointer?, dwCoInit: Int): Int

        fun CoCreateInstance(
            rclsid: Guid.GUID,
            pUnkOuter: Pointer?,
            dwClsContext: Int,
            riid: Guid.GUID,
            ppvObj: PointerByReference,
        ): Int

        companion object {
            val INSTANCE: Ole32Api by lazy { Native.load("ole32", Ole32Api::class.java) }
        }
    }

    /** The WndProc signature with primitive longs — see [installWindowProc] for why the jna-platform structures are out. */
    private interface WndProcCallback : Callback {
        fun callback(hWnd: Long, nMsg: Int, wParam: Long, lParam: Long): Long
    }

    /** The Win32 calls named by their EXACT export names — live-verified (Probe6, JNA 5.19.1): this
     *  Windows build's user32 exports only the `...W` variants of `RegisterWindowMessage` /
     *  `GetWindowLongPtr` / `SetWindowLongPtr` / `DefWindowProc`; the plain names are not exports
     *  and JNA does not fall back to the `W` suffix on custom interfaces. Every handle is a plain
     *  64-bit value (the jna-platform `HANDLE` is a `PointerType` that sign-extends 32-bit handles
     *  to 64 — probe-verified). */
    @Suppress("FunctionName")
    private interface User32RawApi : Library {
        fun RegisterWindowMessageW(lpMessage: String): Int

        fun GetWindowLongPtrW(hwnd: Long, nIndex: Int): Long

        fun SetWindowLongPtrW(hwnd: Long, nIndex: Int, dwNewLong: Long): Long

        fun CreateIcon(
            hdc: Long,
            nWidth: Int,
            nHeight: Int,
            cPlanes: Int,
            cBitsPixel: Int,
            lpbAndMask: Pointer,
            lpbXorMask: Pointer,
        ): Long

        fun DefWindowProcW(hwnd: Long, msg: Int, wParam: Long, lParam: Long): Long

        companion object {
            val INSTANCE: User32RawApi by lazy { Native.load("user32", User32RawApi::class.java) }
        }
    }

    private companion object {
        // vtable (live-verified on this machine — see the class KDoc)
        const val SLOT_HR_INIT = 3
        // ITaskbarList's own methods, right after HrInit (ShObjIdl_core.h order)
        const val SLOT_ADD_TAB = 4
        const val SLOT_DELETE_TAB = 5
        const val SLOT_ACTIVATE_TAB = 6
        const val SLOT_THUMBBAR_ADD_BUTTONS = 15
        const val SLOT_THUMBBAR_UPDATE_BUTTONS = 16

        const val VTABLE_STRIDE = 8

        // THUMBBUTTON layout (552 bytes on x64, live-verified)
        const val THUMBBUTTON_SIZE = 552
        const val OFFSET_DW_MASK = 0
        const val OFFSET_ID = 4
        const val OFFSET_H_ICON = 16
        const val OFFSET_DW_FLAGS = 544

        const val THB_ICON = 0x2
        const val THB_FLAGS = 0x8
        const val THBF_DISABLED = 0x1

        const val CLSID_TASKBAR_LIST = "{56FDF344-FD6D-11D0-958A-006097C9A090}"
        const val IID_IUNKNOWN = "{00000000-0000-0000-C000-000000000046}"
        const val CLSCTX_INPROC_SERVER = 1
        const val COINIT_APARTMENTTHREADED = 2

        // Signed-int spellings: the 0x80010101 / 0x80004005 hex literals would be Longs in Kotlin
        const val S_OK = 0
        const val S_FALSE = 1
        const val RPC_E_CHANGED_MODE = -2147417855 // 0x80010101
        const val E_FAIL = -2147467259 // 0x80004005

        const val GWLP_WNDPROC = -4
        const val WM_COMMAND = 0x0111
        const val WM_SHOWWINDOW = 0x0018
        const val THBN_CLICKED = 0x1800
        const val TASKBAR_BUTTON_CREATED = "TaskbarButtonCreated"

        const val ICON_SIZE = 16
        const val BUTTONS = 3

        /** The alpha below which a pixel is transparent in the AND mask (the color plane keeps the true value). */
        const val TRANSPARENT_ALPHA_THRESHOLD = 128

        val GLYPH_COLOR: Color = Color(0xFFFFFF) // white (user decision 2026-10-11: the glyphs are white, not the app accent)

        // Setup retry (the window may not be realized on the first attach)
        const val SETUP_RETRY_DELAY_MS = 500
        const val SETUP_RETRY_MAX_ATTEMPTS = 20

        // The delayed retry of the re-show repush (the fresh thumbnail button may not exist yet)
        const val RESHOW_REPUSH_DELAY_MS = 500
        const val RESHOW_REPUSH_MAX_ATTEMPTS = 8

        // The settle delay between the first show and the taskbar button's recreation
        const val FIRST_SHOW_RECREATE_DELAY_MS = 1000
    }
}

/**
 * The SMTC thumbnail's shared-loader key (pure — the tests pin it): the player's CANONICAL key
 * (`track id` + [PLAYER_ARTWORK_SIZE_PX], the same key the player screen and the M3 dynamic palette
 * use) — the shared in-memory loader then serves the already-loaded bitmap instead of a second
 * fetch through the phone (one fetch per track change, not two; the MPRIS `artUrl` twin's rule).
 */
internal fun smtcArtworkKey(trackId: String): ArtworkKey = ArtworkKey.track(trackId, PLAYER_ARTWORK_SIZE_PX)
