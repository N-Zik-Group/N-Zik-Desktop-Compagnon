package app.n_zik.compagnon.utils.coroutines

import java.util.concurrent.CountDownLatch
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Ported from the phone app's `NzikDispatchersTest`: the dispatcher tests use real threads
 * (`runBlocking` + latches), never virtual time — thread identity and ordering cannot be
 * faked on a test dispatcher.
 */
class NzikDispatchersTest {

    @Test
    fun `UI aliases Dispatchers Main`() {
        assertEquals(Dispatchers.Main, NzikDispatchers.UI)
    }

    @Test
    fun `DATA aliases Dispatchers IO`() {
        assertEquals(Dispatchers.IO, NzikDispatchers.DATA)
    }

    @Test
    fun `PLAYBACK and VISUALIZER are distinct single-thread dispatchers`() {
        assertNotSame(NzikDispatchers.PLAYBACK, NzikDispatchers.VISUALIZER)
    }

    @Test
    fun `PLAYBACK runs work on a thread named nzik-playback`() = runBlocking {
        // kotlinx.coroutines debug mode appends " @coroutine#N" to the thread name for tracing -
        // startsWith avoids coupling the assertion to that debug-only suffix.
        val thread = withContext(NzikDispatchers.PLAYBACK) { Thread.currentThread() }
        assertTrue(thread.name.startsWith("nzik-playback"), "expected nzik-playback but was ${thread.name}")
        assertTrue(thread.isDaemon, "the nzik-playback thread must be a daemon")
    }

    @Test
    fun `VISUALIZER runs work on a thread named nzik-visualizer`() = runBlocking {
        val thread = withContext(NzikDispatchers.VISUALIZER) { Thread.currentThread() }
        assertTrue(thread.name.startsWith("nzik-visualizer"), "expected nzik-visualizer but was ${thread.name}")
        assertTrue(thread.isDaemon, "the nzik-visualizer thread must be a daemon")
    }

    @Test
    fun `MEDIA runs work on threads prefixed nzik-media`() = runBlocking {
        val thread = withContext(NzikDispatchers.MEDIA) { Thread.currentThread() }
        assertTrue(thread.name.startsWith("nzik-media-"), "expected nzik-media-* but was ${thread.name}")
        assertTrue(thread.isDaemon, "the nzik-media-N threads must be daemons")
    }

    @Test
    fun `ROOM_QUERY_EXECUTOR runs submitted work on a thread prefixed nzik-room-query`() {
        val threadRef = AtomicReference<Thread>()
        val latch = CountDownLatch(1)
        NzikDispatchers.ROOM_QUERY_EXECUTOR.execute {
            threadRef.set(Thread.currentThread())
            latch.countDown()
        }
        assertTrue(latch.await(5, TimeUnit.SECONDS), "executor did not run the submitted task in time")
        val thread = requireNotNull(threadRef.get()) { "the submitted task must have run" }
        assertTrue(thread.name.startsWith("nzik-room-query-"), "expected nzik-room-query-* but was ${thread.name}")
        assertTrue(thread.isDaemon, "the nzik-room-query-N threads must be daemons")
    }

    @Test
    fun `ROOM_TX_EXECUTOR runs submitted work on a thread prefixed nzik-room-tx`() {
        val threadRef = AtomicReference<Thread>()
        val latch = CountDownLatch(1)
        NzikDispatchers.ROOM_TX_EXECUTOR.execute {
            threadRef.set(Thread.currentThread())
            latch.countDown()
        }
        assertTrue(latch.await(5, TimeUnit.SECONDS), "executor did not run the submitted task in time")
        val thread = requireNotNull(threadRef.get()) { "the submitted task must have run" }
        assertTrue(thread.name.startsWith("nzik-room-tx-"), "expected nzik-room-tx-* but was ${thread.name}")
        assertTrue(thread.isDaemon, "the nzik-room-tx-N threads must be daemons")
    }

    @Test
    fun `DATA asExecutor runs submitted work`() {
        val latch = CountDownLatch(1)
        NzikDispatchers.DATA.asExecutor().execute { latch.countDown() }
        assertTrue(latch.await(5, TimeUnit.SECONDS), "DATA asExecutor() did not run the submitted task in time")
    }

    @Test
    fun `PLAYBACK is single-threaded and preserves submission order`() = runBlocking {
        val order = CopyOnWriteArrayList<Int>()
        val jobs = (1..20).map { i ->
            launch(NzikDispatchers.PLAYBACK) { order.add(i) }
        }
        jobs.forEach { it.join() }
        assertEquals((1..20).toList(), order, "PLAYBACK must run submitted work in submission order")
    }

    @Test
    fun `VISUALIZER is single-threaded and preserves submission order`() = runBlocking {
        val order = CopyOnWriteArrayList<Int>()
        val jobs = (1..20).map { i ->
            launch(NzikDispatchers.VISUALIZER) { order.add(i) }
        }
        jobs.forEach { it.join() }
        assertEquals((1..20).toList(), order, "VISUALIZER must run submitted work in submission order")
    }

    @Test
    fun `Room executors cannot be cast to ExecutorService to shut them down`() {
        assertFalse(NzikDispatchers.ROOM_QUERY_EXECUTOR is ExecutorService, "ROOM_QUERY_EXECUTOR must not expose shutdown()")
        assertFalse(NzikDispatchers.ROOM_TX_EXECUTOR is ExecutorService, "ROOM_TX_EXECUTOR must not expose shutdown()")
    }
}
