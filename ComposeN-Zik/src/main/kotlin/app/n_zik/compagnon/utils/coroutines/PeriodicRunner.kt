package app.n_zik.compagnon.utils.coroutines

import java.util.logging.Logger
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private val log = Logger.getLogger("PeriodicRunner")

/**
 * Runs [action] immediately, then again [intervalMs] after each execution finishes (fixed delay,
 * same semantics as `ScheduledExecutorService.scheduleWithFixedDelay`) until the calling
 * coroutine is cancelled.
 *
 * Ported from the phone app's `app.n_zik.android.utils.coroutines.runPeriodically`: it replaces
 * a dedicated `ScheduledThreadPool` — the loop is bound to the caller's scope, so cancelling
 * that scope (the owner's teardown) stops it and leaves no thread behind. An exception thrown
 * by [action] is logged and does not stop the loop.
 *
 * Deliberate deviation from the phone: the failure is logged through `java.util.logging` as
 * the exception's simple name + message, never the throwable or its stack trace (house style).
 */
suspend fun runPeriodically(intervalMs: Long, action: () -> Unit) {
    while (currentCoroutineContext().isActive) {
        try {
            action()
        } catch (e: Exception) {
            log.severe("Periodic action failed: ${e::class.simpleName} (${e.message})")
        }
        delay(intervalMs)
    }
}
