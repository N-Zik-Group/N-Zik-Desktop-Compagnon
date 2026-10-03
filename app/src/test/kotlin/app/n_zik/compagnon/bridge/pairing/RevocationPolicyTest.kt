package app.n_zik.compagnon.bridge.pairing

import app.n_zik.compagnon.core.network.ProbeResult
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RevocationPolicyTest {

    private var erased = 0
    private val policy = RevocationPolicy(onRevoked = { erased++ })

    @Test
    fun `two consecutive DEVICE_REVOKED 2 s apart erase the pairing`() = runTest {
        val callTimes = mutableListOf<Long>()
        val outcome = policy.confirmRest<ProbeResult>(
            call = { callTimes += currentTime; ProbeResult.Revoked },
            isRevoked = { it is ProbeResult.Revoked },
        )
        assertTrue(outcome.revoked)
        assertEquals(listOf(0L, 2_000L), callTimes)
        assertEquals(1, erased)
    }

    @Test
    fun `DEVICE_REVOKED then 200 erases nothing`() = runTest {
        val answers = ArrayDeque(listOf<ProbeResult>(ProbeResult.Revoked, ProbeResult.Ok))
        val outcome = policy.confirmRest(call = { answers.removeFirst() }, isRevoked = { it is ProbeResult.Revoked })
        assertFalse(outcome.revoked)
        assertEquals(ProbeResult.Ok, outcome.result)
        assertEquals(0, erased)
        assertEquals(2_000L, currentTime)
    }

    @Test
    fun `first answer not revoked means no retry`() = runTest {
        var calls = 0
        val outcome = policy.confirmRest<ProbeResult>(
            call = { calls++; ProbeResult.Failed(401, BridgeErrorCode.UNAUTHORIZED) },
            isRevoked = { it is ProbeResult.Revoked },
        )
        assertFalse(outcome.revoked)
        assertEquals(1, calls)
        assertEquals(0, erased)
        assertEquals(0L, currentTime)
    }

    @Test
    fun `revokeNow erases at once`() = runTest {
        policy.revokeNow()
        assertEquals(1, erased)
    }
}
