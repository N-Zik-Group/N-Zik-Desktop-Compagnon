package app.n_zik.compagnon.bridge.library

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RewindFilterTest {

    @Test
    fun `the rewind filter reads its lowercase wire values`() {
        assertEquals(RewindFilter.Month, RewindFilter.fromWire("month"))
        assertEquals(RewindFilter.Year, RewindFilter.fromWire("year"))
        assertEquals(RewindFilter.All, RewindFilter.fromWire("all"))
    }

    @Test
    fun `an unknown or absent wire value reads as All, the phone's default`() {
        // renegotiated 2026-10-11: the phone's rewind filter default is All (was Month)
        assertEquals(RewindFilter.All, RewindFilter.fromWire(null))
        assertEquals(RewindFilter.All, RewindFilter.fromWire("bogus"))
    }

    @Test
    fun `the rewind state defaults to the phone's All filter with both creation toggles on`() {
        val state = RewindState()
        assertEquals(RewindFilter.All, state.filter)
        assertEquals(true, state.monthlyEnabled)
        assertEquals(true, state.yearlyEnabled)
        assertEquals(true, state.rowVisible)
    }
}
