package app.n_zik.compagnon.updater

import app.n_zik.compagnon.updater.services.Updater
import app.n_zik.compagnon.updater.services.Updater.CheckDecision
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Pins [Updater.decideCheck] — the pure skip / result decision of `checkForUpdate` (spec
 * `spec-updater`, loop 2): the updater gate (debug / -git never check — anti-downgrade), the
 * "dialog cancelled" skip (a non-forced check only — the forced check ignores the flag; the
 * flag is the IN-MEMORY `NewUpdateAvailableDialog.isCancelled` — the persisted
 * `updateCancelled` setting is write-only and part of no skip rule, like on the phone), and
 * the version-comparison outcome (the best same-channel tag vs the current build).
 */
class CheckDecisionTest {

    @Test
    fun `the updater off always skips`() {
        // debug / -git: anti-downgrade — the check never runs, whatever the other inputs
        assertEquals(
            CheckDecision.Skip,
            Updater.decideCheck(updaterEnabled = false, updateCancelled = false, isForced = true, bestTagName = "v0.0.2", currentVersion = "0.0.1"),
        )
        assertEquals(
            CheckDecision.Skip,
            Updater.decideCheck(updaterEnabled = false, updateCancelled = true, isForced = false, bestTagName = "v0.0.2", currentVersion = "0.0.1"),
        )
    }

    @Test
    fun `a cancelled non forced check skips`() {
        // The in-memory "don't check" gesture (the dialog cancelled this run) skips the
        // background startup check
        assertEquals(
            CheckDecision.Skip,
            Updater.decideCheck(updaterEnabled = true, updateCancelled = true, isForced = false, bestTagName = "v0.0.2", currentVersion = "0.0.1"),
        )
    }

    @Test
    fun `a cancelled forced check still runs`() {
        // The explicit user actions (the settings page's check, the About card's check) ignore
        // the flag
        assertEquals(
            CheckDecision.Result(true),
            Updater.decideCheck(updaterEnabled = true, updateCancelled = true, isForced = true, bestTagName = "v0.0.2", currentVersion = "0.0.1"),
        )
    }

    @Test
    fun `the result is the version comparison of the best same channel tag`() {
        assertEquals(
            CheckDecision.Result(true),
            Updater.decideCheck(updaterEnabled = true, updateCancelled = false, isForced = false, bestTagName = "v0.0.2", currentVersion = "0.0.1"),
        )
        assertEquals(
            CheckDecision.Result(false),
            Updater.decideCheck(updaterEnabled = true, updateCancelled = false, isForced = false, bestTagName = "v0.0.1", currentVersion = "0.0.2"),
        )
        // the same version is not an update
        assertEquals(
            CheckDecision.Result(false),
            Updater.decideCheck(updaterEnabled = true, updateCancelled = false, isForced = false, bestTagName = "v0.0.2", currentVersion = "0.0.2"),
        )
        // no release found at all: the check runs and reports no update
        assertEquals(
            CheckDecision.Result(false),
            Updater.decideCheck(updaterEnabled = true, updateCancelled = false, isForced = false, bestTagName = null, currentVersion = "0.0.1"),
        )
    }
}
