package app.n_zik.compagnon.platform

import com.sun.jna.Callback
import com.sun.jna.CallbackReference
import com.sun.jna.Function
import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.WString
import com.sun.jna.platform.win32.Guid
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.LongByReference
import com.sun.jna.ptr.PointerByReference
import java.util.logging.Level
import java.util.logging.Logger

/**
 * What the Windows « Now Playing » panel (SMTC) shows: the pure projection of the MPRIS snapshot —
 * the same mapping of the phone's state (AD-1), so the two OS surfaces never disagree. No session →
 * the controls are disabled (the panel drops the entry); a live session → the status, the title /
 * artist and the transport gates.
 */
data class SmtcView(
    val enabled: Boolean,
    /** `MediaPlaybackStatus`: 2 = Stopped, 3 = Playing, 4 = Paused (the SDK's `windows.media.idl`). */
    val status: Int,
    val title: String,
    val artist: String,
    val canPlay: Boolean,
    val canPause: Boolean,
    val canNext: Boolean,
    val canPrevious: Boolean,
    /** The progress bar: the position, the duration (`null` = unknown — no bar), seekable or not, the rate. */
    val positionMs: Long = 0L,
    val durationMs: Long? = null,
    val canSeek: Boolean = false,
    val rate: Double = 1.0,
)

/** The [SmtcView] of an MPRIS [snapshot] at [positionMs] — `Stopped` (no session) disables the controls. */
fun smtcView(snapshot: MprisSnapshot, positionMs: Long = 0L): SmtcView {
    val live = snapshot.playbackStatus != STOPPED
    return SmtcView(
        enabled = live,
        status = when (snapshot.playbackStatus) {
            PLAYING -> SMTC_STATUS_PLAYING
            PAUSED -> SMTC_STATUS_PAUSED
            else -> SMTC_STATUS_STOPPED
        },
        title = snapshot.metadata?.title.orEmpty(),
        artist = snapshot.metadata?.artist?.joinToString(", ").orEmpty(),
        canPlay = snapshot.canPlay,
        canPause = snapshot.canPause,
        canNext = snapshot.canGoNext,
        canPrevious = snapshot.canGoPrevious,
        positionMs = if (live) positionMs else 0L,
        durationMs = snapshot.metadata?.lengthUs?.let { it / 1000L },
        canSeek = snapshot.canSeek,
        rate = snapshot.rate,
    )
}

internal const val SMTC_STATUS_STOPPED = 2
internal const val SMTC_STATUS_PLAYING = 3
internal const val SMTC_STATUS_PAUSED = 4

/** The SMTC buttons this app answers (`SystemMediaTransportControlsButton`, the SDK's `windows.media.idl`). */
internal enum class SmtcButton(val code: Int) {
    Play(0),
    Pause(1),
    Next(6),
    Previous(7);

    companion object {
        fun of(code: Int): SmtcButton? = entries.firstOrNull { it.code == code }
    }
}

/**
 * The Windows « Now Playing » panel (System Media Transport Controls — the media flyout of the
 * volume / action center and the lock screen, like Spotify's): WinRT through raw vtables over JNA,
 * the same house pattern as [WindowsThumbBar] (no IDispatch, no projection library).
 *
 * The slots and IIDs are read from the dev machine's SDK 10.0.26100.0 (`winrt/windows.media.idl`,
 * `winrt/windows.storage.streams.idl`, `um/SystemMediaTransportControlsInterop.idl`, `winrt/shcore.h`;
 * the handler's parameterized IID from `winrt/windows.media.h`). Every WinRT interface derives from
 * `IInspectable` (6 slots), so the first own method is slot 6.
 *
 * Owned by [WindowsThumbBar] (one Windows media platform, one window): [setup] runs on the EDT
 * right after the taskbar setup (COM already initialized there — STA), every [show] / [setThumbnail]
 * on the EDT too. The ButtonPressed handler is a hand-built COM object (vtable of JNA callbacks,
 * **strongly referenced** for the app's lifetime — a GC'd trampoline would crash on the next press);
 * it fires on a WinRT worker thread and only forwards through [onButton] (thread-safe). A native
 * failure turns the panel off for the process, never the app (the no-crash constraint).
 */
internal class WindowsSmtc(
    private val log: Logger,
    private val onButton: (SmtcButton) -> Unit,
    /** A seek on the panel's progress bar, in ms (a WinRT worker thread — thread-safe forward only). */
    private val onSeek: (Long) -> Unit,
) {
    private var smtc: Pointer? = null
    // The progress bar (`ISystemMediaTransportControls2` + a timeline object) — `null` when this
    // Windows build lacks them: the panel then simply shows no bar
    private var smtc2: Pointer? = null
    private var timeline: Pointer? = null
    private var updater: Pointer? = null
    private var music: Pointer? = null
    private var streamStatics: Pointer? = null
    private var failed = false

    // The last view applied (or pending until the setup): re-applied right after the setup
    private var lastView: SmtcView? = null

    // The handler COM object — every piece kept for the app's lifetime (see the class KDoc)
    // (one entry per registered handler: the buttons, the progress-bar seek)
    private val handlers = mutableListOf<Any>()

    /** `true` once the panel is up. */
    val isUp: Boolean get() = smtc != null

    /** Binds the panel to [hwnd] (EDT, COM initialized). Returns `false` when the panel stays off. */
    fun setup(hwnd: Long): Boolean {
        if (failed || smtc != null) return smtc != null
        try {
            val interop = activationFactory(CLASS_SMTC, IID_SMTC_INTEROP) ?: return fail("no SMTC interop factory")
            val controls = PointerByReference()
            val hr = try {
                vcall(interop, SLOT_INTEROP_GET_FOR_WINDOW, hwnd, guid(IID_SMTC), controls)
            } finally {
                release(interop)
            }
            val smtcPtr = controls.value
            if (hr != S_OK || smtcPtr == null) return fail("GetForWindow failed (0x${Integer.toHexString(hr)})")

            val updaterRef = PointerByReference()
            check(vcall(smtcPtr, SLOT_SMTC_GET_DISPLAY_UPDATER, updaterRef) == S_OK && updaterRef.value != null) { "no display updater" }
            val updaterPtr = updaterRef.value
            vcall(updaterPtr, SLOT_UPDATER_PUT_TYPE, MEDIA_PLAYBACK_TYPE_MUSIC)
            val musicRef = PointerByReference()
            check(vcall(updaterPtr, SLOT_UPDATER_GET_MUSIC_PROPERTIES, musicRef) == S_OK && musicRef.value != null) { "no music properties" }

            val token = Memory(8)
            val addHr = vcall(smtcPtr, SLOT_SMTC_ADD_BUTTON_PRESSED, buildHandler(IID_BUTTON_PRESSED_HANDLER, ::onButtonPressed), token)
            if (addHr != S_OK) log.warning("SMTC: ButtonPressed registration failed (0x${Integer.toHexString(addHr)}) — the panel shows, its buttons do nothing")
            setupTimeline(smtcPtr)

            smtc = smtcPtr
            updater = updaterPtr
            music = musicRef.value
            streamStatics = activationFactory(CLASS_STREAM_REFERENCE, IID_STREAM_REFERENCE_STATICS)
            if (streamStatics == null) log.warning("SMTC: no RandomAccessStreamReference factory — the panel shows no artwork")
            log.info("SMTC: Now Playing panel bound to the window")
            lastView?.let { apply(it) }
            return true
        } catch (t: Throwable) {
            failed = true
            smtc = null
            log.log(Level.WARNING, "SMTC: setup failed permanently — the Now Playing panel stays off", t)
            return false
        }
    }

    /** The progress bar: `ISystemMediaTransportControls2` (QueryInterface), a timeline object, the seek handler. */
    private fun setupTimeline(smtcPtr: Pointer) {
        guarded("timeline setup") {
            val smtc2Ref = PointerByReference()
            if (vcall(smtcPtr, SLOT_QUERY_INTERFACE, guid(IID_SMTC2), smtc2Ref) != S_OK || smtc2Ref.value == null) {
                log.warning("SMTC: no ISystemMediaTransportControls2 — no progress bar")
                return@guarded
            }
            val inspectable = PointerByReference()
            var hr = E_FAIL
            withHString(CLASS_TIMELINE) { hr = CombaseApi.INSTANCE.RoActivateInstance(it, inspectable) }
            if (hr != S_OK || inspectable.value == null) {
                log.warning("SMTC: no timeline properties object (0x${Integer.toHexString(hr)}) — no progress bar")
                release(smtc2Ref.value)
                return@guarded
            }
            val timelineRef = PointerByReference()
            val qi = vcall(inspectable.value, SLOT_QUERY_INTERFACE, guid(IID_TIMELINE), timelineRef)
            release(inspectable.value)
            if (qi != S_OK || timelineRef.value == null) {
                log.warning("SMTC: no ISystemMediaTransportControlsTimelineProperties — no progress bar")
                release(smtc2Ref.value)
                return@guarded
            }
            val token = Memory(8)
            val addHr = vcall(smtc2Ref.value, SLOT_SMTC2_ADD_POSITION_CHANGE_REQUESTED, buildHandler(IID_POSITION_CHANGE_HANDLER, ::onPositionChangeRequested), token)
            if (addHr != S_OK) log.warning("SMTC: PlaybackPositionChangeRequested registration failed (0x${Integer.toHexString(addHr)}) — the bar shows, seeking does nothing")
            smtc2 = smtc2Ref.value
            timeline = timelineRef.value
            log.info("SMTC: progress bar enabled")
        }
    }

    /** Shows [view] (EDT); kept for the setup when the panel is not up yet. */
    fun show(view: SmtcView) {
        if (view == lastView && smtc != null) return
        lastView = view
        if (smtc != null) guarded("update") { apply(view) }
    }

    /** Shows [png] as the panel's artwork, `null` clears it (EDT). */
    fun setThumbnail(png: ByteArray?) {
        val updaterPtr = updater ?: return
        guarded("thumbnail") {
            if (png == null) {
                vcall(updaterPtr, SLOT_UPDATER_PUT_THUMBNAIL, null as Pointer?)
                vcall(updaterPtr, SLOT_UPDATER_UPDATE)
                return@guarded
            }
            val statics = streamStatics ?: return@guarded
            val buffer = Memory(png.size.toLong()).apply { write(0, png, 0, png.size) }
            val stream = ShlwapiApi.INSTANCE.SHCreateMemStream(buffer, png.size) ?: error("SHCreateMemStream failed")
            try {
                val ras = PointerByReference()
                val hr = ShcoreApi.INSTANCE.CreateRandomAccessStreamOverStream(stream, BSOS_DEFAULT, guid(IID_RANDOM_ACCESS_STREAM), ras)
                check(hr == S_OK && ras.value != null) { "CreateRandomAccessStreamOverStream failed (0x${Integer.toHexString(hr)})" }
                try {
                    val reference = PointerByReference()
                    check(vcall(statics, SLOT_STATICS_CREATE_FROM_STREAM, ras.value, reference) == S_OK && reference.value != null) { "CreateFromStream failed" }
                    try {
                        vcall(updaterPtr, SLOT_UPDATER_PUT_THUMBNAIL, reference.value)
                        vcall(updaterPtr, SLOT_UPDATER_UPDATE)
                    } finally {
                        release(reference.value)
                    }
                } finally {
                    release(ras.value)
                }
            } finally {
                release(stream)
            }
        }
    }

    private fun apply(view: SmtcView) {
        val smtcPtr = smtc ?: return
        vcall(smtcPtr, SLOT_SMTC_PUT_IS_ENABLED, view.enabled.toWinRt())
        vcall(smtcPtr, SLOT_SMTC_PUT_IS_PLAY_ENABLED, view.canPlay.toWinRt())
        vcall(smtcPtr, SLOT_SMTC_PUT_IS_PAUSE_ENABLED, view.canPause.toWinRt())
        vcall(smtcPtr, SLOT_SMTC_PUT_IS_NEXT_ENABLED, view.canNext.toWinRt())
        vcall(smtcPtr, SLOT_SMTC_PUT_IS_PREVIOUS_ENABLED, view.canPrevious.toWinRt())
        vcall(smtcPtr, SLOT_SMTC_PUT_PLAYBACK_STATUS, view.status)
        val musicPtr = music ?: return
        withHString(view.title) { vcall(musicPtr, SLOT_MUSIC_PUT_TITLE, it) }
        withHString(view.artist) { vcall(musicPtr, SLOT_MUSIC_PUT_ARTIST, it) }
        updater?.let { vcall(it, SLOT_UPDATER_UPDATE) }
        applyTimeline(view)
    }

    /** The progress bar: TimeSpans are 100 ns ticks; an unknown duration publishes an empty timeline (no bar). */
    private fun applyTimeline(view: SmtcView) {
        val smtc2Ptr = smtc2 ?: return
        val timelinePtr = timeline ?: return
        val durationMs = view.durationMs ?: 0L
        val endTicks = durationMs * TICKS_PER_MS
        val positionTicks = view.positionMs.coerceIn(0L, durationMs) * TICKS_PER_MS
        vcall(timelinePtr, SLOT_TIMELINE_PUT_START_TIME, 0L)
        vcall(timelinePtr, SLOT_TIMELINE_PUT_END_TIME, endTicks)
        vcall(timelinePtr, SLOT_TIMELINE_PUT_MIN_SEEK_TIME, 0L)
        vcall(timelinePtr, SLOT_TIMELINE_PUT_MAX_SEEK_TIME, if (view.canSeek) endTicks else 0L)
        vcall(timelinePtr, SLOT_TIMELINE_PUT_POSITION, positionTicks)
        vcall(smtc2Ptr, SLOT_SMTC2_PUT_PLAYBACK_RATE, view.rate)
        vcall(smtc2Ptr, SLOT_SMTC2_UPDATE_TIMELINE_PROPERTIES, timelinePtr)
    }

    /** ButtonPressed (a WinRT worker thread): the pressed button, forwarded. */
    private fun onButtonPressed(args: Pointer) {
        val button = IntByReference()
        if (vcall(args, SLOT_ARGS_GET_BUTTON, button) == S_OK) SmtcButton.of(button.value)?.let(onButton)
    }

    /** PlaybackPositionChangeRequested (a WinRT worker thread): the requested position, forwarded as a seek. */
    private fun onPositionChangeRequested(args: Pointer) {
        val ticks = LongByReference()
        if (vcall(args, SLOT_ARGS_GET_REQUESTED_POSITION, ticks) == S_OK) onSeek((ticks.value / TICKS_PER_MS).coerceAtLeast(0L))
    }

    private inline fun guarded(what: String, block: () -> Unit) {
        try {
            block()
        } catch (t: Throwable) {
            log.warning("SMTC: $what failed (${t::class.simpleName}: ${t.message})")
        }
    }

    private fun fail(why: String): Boolean {
        failed = true
        log.warning("SMTC: $why — the Now Playing panel stays off")
        return false
    }

    /** A `TypedEventHandler<SMTC, TArgs>` COM object (QueryInterface / AddRef / Release / Invoke) answering [iid]; [onInvoke] reads the args. */
    private fun buildHandler(iid: String, onInvoke: (Pointer) -> Unit): Pointer {
        val handlerIid = guidBytes(iid)
        val acceptedIids = listOf(guidBytes(IID_IUNKNOWN), guidBytes(IID_IAGILE_OBJECT), handlerIid)
        val queryInterface = object : QueryInterfaceCallback {
            override fun invoke(self: Pointer?, riid: Pointer?, ppv: Pointer?): Int {
                if (ppv == null) return E_POINTER
                val asked = riid?.getByteArray(0, GUID_SIZE)
                return if (asked != null && acceptedIids.any { it.contentEquals(asked) }) {
                    ppv.setPointer(0, self)
                    S_OK
                } else {
                    ppv.setPointer(0, null)
                    E_NOINTERFACE
                }
            }
        }
        // The object is static (app-lifetime): the reference count is a constant
        val addRef = object : RefCountCallback {
            override fun invoke(self: Pointer?): Int = 1
        }
        val releaseRef = object : RefCountCallback {
            override fun invoke(self: Pointer?): Int = 1
        }
        val invoke = object : InvokeCallback {
            override fun invoke(self: Pointer?, sender: Pointer?, args: Pointer?): Int {
                // A WinRT worker thread: read the args, forward, never throw into native code
                try {
                    args?.let(onInvoke)
                } catch (t: Throwable) {
                    log.warning("SMTC: event handling failed (${t::class.simpleName})")
                }
                return S_OK
            }
        }
        val all = listOf(queryInterface, addRef, releaseRef, invoke)
        val vtable = Memory(all.size.toLong() * POINTER_SIZE)
        all.forEachIndexed { index, cb -> vtable.setPointer(index.toLong() * POINTER_SIZE, CallbackReference.getFunctionPointer(cb)) }
        val obj = Memory(POINTER_SIZE.toLong()).apply { setPointer(0, vtable) }
        handlers.addAll(listOf(all, vtable, obj))
        return obj
    }

    private interface QueryInterfaceCallback : Callback {
        fun invoke(self: Pointer?, riid: Pointer?, ppv: Pointer?): Int
    }

    private interface RefCountCallback : Callback {
        fun invoke(self: Pointer?): Int
    }

    private interface InvokeCallback : Callback {
        fun invoke(self: Pointer?, sender: Pointer?, args: Pointer?): Int
    }

    @Suppress("FunctionName")
    private interface CombaseApi : Library {
        fun RoGetActivationFactory(activatableClassId: Pointer?, iid: Pointer, factory: PointerByReference): Int

        fun RoActivateInstance(activatableClassId: Pointer?, instance: PointerByReference): Int

        fun WindowsCreateString(sourceString: WString?, length: Int, string: PointerByReference): Int

        fun WindowsDeleteString(string: Pointer?): Int

        companion object {
            val INSTANCE: CombaseApi by lazy { Native.load("combase", CombaseApi::class.java) }
        }
    }

    @Suppress("FunctionName")
    private interface ShlwapiApi : Library {
        fun SHCreateMemStream(pInit: Pointer?, cbInit: Int): Pointer?

        companion object {
            val INSTANCE: ShlwapiApi by lazy { Native.load("shlwapi", ShlwapiApi::class.java) }
        }
    }

    @Suppress("FunctionName")
    private interface ShcoreApi : Library {
        fun CreateRandomAccessStreamOverStream(stream: Pointer, options: Int, riid: Pointer, ppv: PointerByReference): Int

        companion object {
            val INSTANCE: ShcoreApi by lazy { Native.load("shcore", ShcoreApi::class.java) }
        }
    }

    private fun activationFactory(className: String, iid: String): Pointer? {
        val factory = PointerByReference()
        var hr = E_FAIL
        withHString(className) { hr = CombaseApi.INSTANCE.RoGetActivationFactory(it, guid(iid), factory) }
        if (hr != S_OK) log.warning("SMTC: RoGetActivationFactory($className) failed (0x${Integer.toHexString(hr)})")
        return if (hr == S_OK) factory.value else null
    }

    /** Runs [block] with an HSTRING of [value] (`null` = the empty HSTRING), deleted afterwards. */
    private inline fun withHString(value: String, block: (Pointer?) -> Unit) {
        if (value.isEmpty()) {
            block(null)
            return
        }
        val ref = PointerByReference()
        val hr = CombaseApi.INSTANCE.WindowsCreateString(WString(value), value.length, ref)
        check(hr == S_OK) { "WindowsCreateString failed (0x${Integer.toHexString(hr)})" }
        try {
            block(ref.value)
        } finally {
            CombaseApi.INSTANCE.WindowsDeleteString(ref.value)
        }
    }

    private fun release(obj: Pointer?) {
        if (obj != null) vcall(obj, SLOT_RELEASE)
    }

    private companion object {
        /** One vtable call on [obj]: the COM `this` first, then the method's arguments (see [WindowsThumbBar.comCall]). */
        fun vcall(obj: Pointer, slot: Int, vararg args: Any?): Int {
            val entry = obj.getPointer(0).getPointer(slot.toLong() * POINTER_SIZE)
            return Function.getFunction(entry, Function.ALT_CONVENTION).invokeInt(arrayOf(obj, *args))
        }

        fun Boolean.toWinRt(): Int = if (this) 1 else 0

        /** A native REFIID: the GUID structure written in its in-memory (little-endian) layout —
         *  `Guid.GUID.toByteArray()` is the big-endian TEXT order, which COM rejects (E_NOINTERFACE,
         *  live-observed 2026-10-12). The structure stays alive through the returned pointer's call. */
        fun guid(value: String): Pointer = Guid.GUID.fromString(value).apply { write() }.pointer

        /** The in-memory bytes of a GUID — what an incoming REFIID is compared against. */
        fun guidBytes(value: String): ByteArray = guid(value).getByteArray(0, GUID_SIZE)

        const val POINTER_SIZE = 8
        const val GUID_SIZE = 16

        // ---- Classes / IIDs (SDK 10.0.26100.0) ----------------------------------------------------------
        const val CLASS_SMTC = "Windows.Media.SystemMediaTransportControls"
        const val CLASS_STREAM_REFERENCE = "Windows.Storage.Streams.RandomAccessStreamReference"
        const val CLASS_TIMELINE = "Windows.Media.SystemMediaTransportControlsTimelineProperties"
        const val IID_SMTC_INTEROP = "{DDB0472D-C911-4A1F-86D9-DC3D71A95F5A}"
        const val IID_SMTC = "{99FA3FF4-1742-42A6-902E-087D41F965EC}"
        const val IID_STREAM_REFERENCE_STATICS = "{857309DC-3FBF-4E7D-986F-EF3B1A07A964}"
        const val IID_RANDOM_ACCESS_STREAM = "{905A0FE1-BC53-11DF-8C49-001E4FC686DA}"
        const val IID_BUTTON_PRESSED_HANDLER = "{0557E996-7B23-5BAE-AA81-EA0D671143A4}"
        const val IID_SMTC2 = "{EA98D2F6-7F3C-4AF2-A586-72889808EFB1}"
        const val IID_TIMELINE = "{5125316A-C3A2-475B-8507-93534DC88F15}"
        const val IID_POSITION_CHANGE_HANDLER = "{44E34F15-BDC0-50A7-ACE4-39E91FB753F1}"
        const val IID_IUNKNOWN = "{00000000-0000-0000-C000-000000000046}"
        const val IID_IAGILE_OBJECT = "{94EA2B94-E9CC-49E0-C0FF-EE64CA8F5B90}"

        // ---- vtable slots (IUnknown 0-2, IInspectable 3-5, own methods from 6) --------------------------
        const val SLOT_QUERY_INTERFACE = 0
        const val SLOT_RELEASE = 2
        const val SLOT_INTEROP_GET_FOR_WINDOW = 6
        const val SLOT_SMTC_PUT_PLAYBACK_STATUS = 7
        const val SLOT_SMTC_GET_DISPLAY_UPDATER = 8
        const val SLOT_SMTC_PUT_IS_ENABLED = 11
        const val SLOT_SMTC_PUT_IS_PLAY_ENABLED = 13
        const val SLOT_SMTC_PUT_IS_PAUSE_ENABLED = 17
        const val SLOT_SMTC_PUT_IS_PREVIOUS_ENABLED = 25
        const val SLOT_SMTC_PUT_IS_NEXT_ENABLED = 27
        const val SLOT_SMTC_ADD_BUTTON_PRESSED = 32
        const val SLOT_UPDATER_PUT_TYPE = 7
        const val SLOT_UPDATER_PUT_THUMBNAIL = 11
        const val SLOT_UPDATER_GET_MUSIC_PROPERTIES = 12
        const val SLOT_UPDATER_UPDATE = 17
        const val SLOT_MUSIC_PUT_TITLE = 7
        const val SLOT_MUSIC_PUT_ARTIST = 11
        const val SLOT_STATICS_CREATE_FROM_STREAM = 8
        const val SLOT_ARGS_GET_BUTTON = 6
        const val SLOT_SMTC2_PUT_PLAYBACK_RATE = 11
        const val SLOT_SMTC2_UPDATE_TIMELINE_PROPERTIES = 12
        const val SLOT_SMTC2_ADD_POSITION_CHANGE_REQUESTED = 13
        const val SLOT_TIMELINE_PUT_START_TIME = 7
        const val SLOT_TIMELINE_PUT_END_TIME = 9
        const val SLOT_TIMELINE_PUT_MIN_SEEK_TIME = 11
        const val SLOT_TIMELINE_PUT_MAX_SEEK_TIME = 13
        const val SLOT_TIMELINE_PUT_POSITION = 15
        const val SLOT_ARGS_GET_REQUESTED_POSITION = 6

        /** A WinRT `TimeSpan` counts 100 ns ticks. */
        const val TICKS_PER_MS = 10_000L

        const val MEDIA_PLAYBACK_TYPE_MUSIC = 1
        const val BSOS_DEFAULT = 0

        const val S_OK = 0
        const val E_FAIL = -2147467259 // 0x80004005
        const val E_NOINTERFACE = -2147467262 // 0x80004002
        const val E_POINTER = -2147467261 // 0x80004003
    }
}
