package app.n_zik.compagnon.platform

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The single-instance guard's decision ([SingleInstance.duplicateLaunch]): the launch exits only
 * when the marker bus name is owned by a live first instance, and the first window is raised in
 * that case — best-effort (a failed raise still exits).
 */
class SingleInstanceTest {

    @Test
    fun `without the name owned the launch is the only instance`() {
        var raised = false
        assertFalse(SingleInstance.duplicateLaunch(false) { raised = true })
        assertFalse(raised, "the first window must not be raised without a first instance")
    }

    @Test
    fun `a live first instance is raised and this launch exits`() {
        var raised = false
        assertTrue(SingleInstance.duplicateLaunch(true) { raised = true })
        assertTrue(raised, "the first instance's window must be re-shown")
    }

    @Test
    fun `a failed raise still exits`() {
        // the name is owned, so the first instance is alive either way — the raise failing
        // (a bus hiccup) must not turn the duplicate into a parallel player
        assertTrue(SingleInstance.duplicateLaunch(true) { throw IllegalStateException("no bus") })
    }
}
