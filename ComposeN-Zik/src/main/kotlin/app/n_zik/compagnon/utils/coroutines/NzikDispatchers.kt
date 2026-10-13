package app.n_zik.compagnon.utils.coroutines

import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicInteger
import java.util.logging.Logger
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher

/**
 * Single source of truth for every named thread/dispatcher in the app (issue #606).
 *
 * Ported in full from the phone app's `app.n_zik.android.utils.coroutines.NzikDispatchers`: same
 * names, same shape. `VISUALIZER` and the Room executors are ported as well — reserved for later
 * use on the PC (no visualizer, no database yet); lazy + daemon, no thread until first access.
 * `UI` is `Dispatchers.Main`, resolved to the Swing EDT by kotlinx-coroutines-swing.
 *
 * Process-lifetime singleton: threads are daemon and never explicitly closed here or by any
 * caller — closing a process-lifetime executor from a scope/cleanup path kills it for the rest
 * of the process (the phone's `Threads.close()`-in-`onDestroy` bug this design avoids).
 *
 * Deliberate deviation from the phone: the exception handlers log the exception's simple name
 * and message through `java.util.logging`, never the throwable or its stack trace (house style,
 * and an exception message must never carry a token).
 */
object NzikDispatchers {

    /** Thread 1 — UI & gestures. Always Dispatchers.Main. */
    val UI: CoroutineDispatcher = Dispatchers.Main

    /** Thread 2 — Audio & playback. Single thread: guarantees ordering. Absorbs the former
     *  `PlaybackDispatchers.STREAM_RESOLVER`. The libvlc `player.*` calls go through vlcj's
     *  `player.submit`, on vlcj's own thread; the `timeMs()` status probe runs on this
     *  playback thread.
     *  Lazy: a Kotlin `object`'s properties all initialize together on first touch of any one
     *  of them, so eagerly declaring five independent thread pools here would spin up every
     *  pool (~12 threads) the moment any single one is first used. */
    val PLAYBACK: CoroutineDispatcher by lazy {
        Executors.newSingleThreadExecutor(namedThreadFactory("nzik-playback")).asCoroutineDispatcher()
    }

    /** Thread 3a — Visualizer FFT capture. Single thread: serializes access to the native
     *  `Visualizer` shared per sessionId (thread-safe by construction, not by locking).
     *  Reserved: the PC has no visualizer yet. */
    val VISUALIZER: CoroutineDispatcher by lazy {
        Executors.newSingleThreadExecutor(namedThreadFactory("nzik-visualizer")).asCoroutineDispatcher()
    }

    /** Thread 3b — CPU-bound media work: cover palette extraction, morphing-shape math,
     *  bitmap work, seek-bar wave sampling. Isolated from VISUALIZER's continuous loop and
     *  from DATA. */
    val MEDIA: CoroutineDispatcher by lazy {
        Executors.newFixedThreadPool(2, indexedThreadFactory("nzik-media")).asCoroutineDispatcher()
    }

    /** Thread 5 — Network & disk IO: album art, song info, downloads, `pairing.json` / `settings.json`. */
    val DATA: CoroutineDispatcher = Dispatchers.IO

    /** Named, bounded Room query executor — same off-main behavior as Room's internal default,
     *  just visible by name in the profiler traces. Wrapped in a plain `Executor` SAM (not exposed
     *  as the underlying `ExecutorService`) so a caller cannot cast it to call `.shutdown()` —
     *  this object is process-lifetime and must never be closed.
     *  Reserved: the PC has no database yet. */
    val ROOM_QUERY_EXECUTOR: Executor by lazy {
        val pool = Executors.newFixedThreadPool(4, indexedThreadFactory("nzik-room-query"))
        Executor { runnable -> pool.execute(runnable) }
    }

    /** Named, bounded Room transaction executor — see [ROOM_QUERY_EXECUTOR]. */
    val ROOM_TX_EXECUTOR: Executor by lazy {
        val pool = Executors.newFixedThreadPool(4, indexedThreadFactory("nzik-room-tx"))
        Executor { runnable -> pool.execute(runnable) }
    }

    /**
     * Creates a hardened fire-and-forget scope (issue #606, Goal G5).
     *
     * Every bare `CoroutineScope(dispatcher)` left uncancelled by design gets exactly two
     * additions here: a [SupervisorJob] (an exception in one coroutine can no longer cancel
     * its siblings) and a [CoroutineExceptionHandler] that turns an otherwise unhandled
     * exception (which on the JVM kills the pool thread or silently ends sibling coroutines)
     * into a `java.util.logging` entry. The scope itself is still never cancelled by this
     * helper — callers that used to cancel their scope keep doing so on the returned
     * [CoroutineScope].
     *
     * @param dispatcher the exact dispatcher this scope must run on (never changed by this helper)
     * @return a `CoroutineScope` on [dispatcher] with `SupervisorJob()` + the logging exception handler
     */
    fun fireAndForget(dispatcher: CoroutineDispatcher): CoroutineScope =
        CoroutineScope(dispatcher + SupervisorJob() + fireAndForgetExceptionHandler)

    /**
     * Same as [fireAndForget] for scopes whose context carries extra elements beside the
     * dispatcher (e.g. a parent [kotlinx.coroutines.Job] that must stay the cancellation
     * root, or a [kotlinx.coroutines.CoroutineName] for traceability).
     *
     * A [Job] already present in [context] is kept as the cancellation root; a
     * [SupervisorJob] is only added when [context] carries none. A dispatcher-less context
     * (e.g. a [SupervisorJob] alone) is allowed: launches then need an explicit dispatcher,
     * otherwise they run on the caller's thread.
     */
    fun fireAndForget(context: CoroutineContext): CoroutineScope =
        CoroutineScope(context + (context[Job] ?: SupervisorJob()) + fireAndForgetExceptionHandler)

    private val log = Logger.getLogger("FireAndForget")

    private val fireAndForgetExceptionHandler = CoroutineExceptionHandler { context, e ->
        // Name + dispatcher identify the failing site for this shared handler.
        log.severe(
            "Unhandled exception in fire-and-forget scope (name=${context[CoroutineName]?.name}, " +
                "dispatcher=${context[ContinuationInterceptor]}): ${e::class.simpleName} (${e.message})",
        )
    }

    private fun namedThreadFactory(name: String) = ThreadFactory { runnable ->
        Thread(runnable, name).apply { isDaemon = true }
    }

    private fun indexedThreadFactory(prefix: String): ThreadFactory {
        val counter = AtomicInteger(1)
        return ThreadFactory { runnable ->
            Thread(runnable, "$prefix-${counter.getAndIncrement()}").apply { isDaemon = true }
        }
    }
}
