package app.n_zik.compagnon.utils.coroutines

import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Ported from the phone app's `PeriodicRunnerTest`, driven on `runTest`'s virtual time:
 * the ported exception handler logs to `java.util.logging` (never an uncaught exception),
 * so `runTest`'s strict end-of-test checks no longer couple this class to unrelated tests.
 *
 * Like the phone version, the `advance` helper pairs `advanceTimeBy` with `runCurrent`: the
 * former's window is exclusive on the end, so the task scheduled exactly at the reached
 * instant only runs through the trailing `runCurrent`.
 */
class PeriodicRunnerTest {

    @Test
    fun `runs immediately then once per interval`() = runTest {
        fun advance(ms: Long) {
            advanceTimeBy(ms)
            runCurrent()
        }

        val callTimes = mutableListOf<Long>()
        val job = backgroundScope.launch { runPeriodically(30_000) { callTimes += testScheduler.currentTime } }

        runCurrent()
        assertEquals(listOf(0L), callTimes, "first call must be immediate")

        advance(29_999)
        assertEquals(1, callTimes.size, "no call before the interval elapses")

        advance(1)
        assertEquals(listOf(0L, 30_000L), callTimes)

        advance(30_000)
        assertEquals(listOf(0L, 30_000L, 60_000L), callTimes)

        job.cancel()
    }

    @Test
    fun `cancelling the scope stops further calls`() = runTest {
        fun advance(ms: Long) {
            advanceTimeBy(ms)
            runCurrent()
        }

        var calls = 0
        val job = backgroundScope.launch { runPeriodically(1_000) { calls++ } }

        runCurrent()
        advance(1_000)
        assertEquals(2, calls)

        job.cancel()
        advance(10_000)

        assertEquals(2, calls, "no call may happen after cancellation")
        assertTrue(job.isCancelled)
    }

    @Test
    fun `a slow action extends the period, fixed delay not fixed rate`() = runTest {
        // The KDoc pins `scheduleWithFixedDelay` semantics: the next tick starts only when the
        // action finishes. A slow action (20 virtual seconds) with a 10 s interval must delay
        // the next call by the action's duration — nothing would catch that if the loop were
        // `scheduleAtFixedRate`.
        val callTimes = mutableListOf<Long>()
        var slowAction = true
        val job = backgroundScope.launch {
            runPeriodically(10_000) {
                callTimes += testScheduler.currentTime
                if (slowAction) {
                    slowAction = false
                    advanceTimeBy(20_000) // the action itself burns 20 virtual seconds
                }
            }
        }

        runCurrent()
        assertEquals(listOf(0L), callTimes, "first call must be immediate")

        advanceTimeBy(10_000)
        runCurrent()
        assertEquals(
            listOf(0L, 30_000L),
            callTimes,
            "the interval starts after the slow action finishes: the second call is at t=30 s, not t=10 s",
        )

        advanceTimeBy(10_000)
        runCurrent()
        assertEquals(listOf(0L, 30_000L, 40_000L), callTimes, "the period resumes at the fixed interval")

        job.cancel()
    }

    @Test
    fun `a failing action does not kill the loop`() = runTest {
        fun advance(ms: Long) {
            advanceTimeBy(ms)
            runCurrent()
        }

        var calls = 0
        val job = backgroundScope.launch {
            runPeriodically(1_000) {
                calls++
                if (calls == 1) error("simulated failure")
            }
        }

        runCurrent()
        advance(1_000)

        assertEquals(2, calls, "loop must keep running after the action threw")
        job.cancel()
    }
}
