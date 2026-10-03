package app.n_zik.compagnon.bridge.state

/**
 * Estimate of the phone's clock from `ping`/`pong` (contract §6.3, AD-3), used before any position
 * extrapolation. NTP-style, with the client's monotonic clock:
 *
 * - `offset = ((serverReceive − clientSend) + (serverSend − clientReceive)) / 2`
 * - `latency (round trip) = (clientReceive − clientSend) − (serverSend − serverReceive)`
 *
 * The offset of the sample with the smallest round trip among the last [window] ones is kept (the
 * least disturbed by network jitter). Before any `pong`, the wall clock stands in for the server's.
 * Thread-safe.
 */
class ServerClock(
    private val monotonicMs: () -> Long = ::monotonicNowMs,
    private val wallMs: () -> Long = System::currentTimeMillis,
    private val window: Int = 8,
) {
    private data class Sample(val offsetMs: Long, val roundTripMs: Long)

    private val samples = ArrayDeque<Sample>()
    @Volatile private var best: Sample? = null

    /** Monotonic time of the client, the one `ping.clientTimeMs` carries. */
    fun nowMonotonicMs(): Long = monotonicMs()

    /** Records a `pong` received at [clientReceiveMs] (monotonic). Inconsistent samples are dropped. */
    fun onPong(pong: PongMessage, clientReceiveMs: Long = monotonicMs()) {
        val clientSend = pong.clientTimeMs
        val roundTrip = (clientReceiveMs - clientSend) - (pong.serverSendTimeMs - pong.serverReceiveTimeMs)
        if (clientReceiveMs < clientSend || roundTrip < 0) return
        val offset = ((pong.serverReceiveTimeMs - clientSend) + (pong.serverSendTimeMs - clientReceiveMs)) / 2
        synchronized(samples) {
            samples.addLast(Sample(offset, roundTrip))
            while (samples.size > window) samples.removeFirst()
            best = samples.minBy { it.roundTripMs }
        }
    }

    /** `true` once at least one `pong` was recorded. */
    val isSynced: Boolean get() = best != null

    /** Server clock offset relative to the monotonic clock, `null` before any `pong`. */
    val offsetMs: Long? get() = best?.offsetMs

    /** Best round trip measured, `null` before any `pong`. */
    val roundTripMs: Long? get() = best?.roundTripMs

    /** Estimated current server time (epoch UTC ms, phone clock). */
    fun serverNowMs(): Long {
        val now = monotonicMs()
        val offset = best?.offsetMs ?: (wallMs() - now)
        return now + offset
    }

    /** Forget every sample (new session: the phone may have restarted). */
    fun reset() {
        synchronized(samples) {
            samples.clear()
            best = null
        }
    }
}

/** Monotonic clock in ms (contract §6.3: `ping.clientTimeMs` is monotonic, never the wall clock). */
fun monotonicNowMs(): Long = System.nanoTime() / 1_000_000
