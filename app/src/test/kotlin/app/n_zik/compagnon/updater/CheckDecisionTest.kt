package app.n_zik.compagnon.updater

import app.n_zik.compagnon.updater.services.Updater
import app.n_zik.compagnon.updater.services.Updater.CheckDecision
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

/**
 * Pins [Updater.decideCheck] — the pure skip / result decision of `checkForUpdate` (spec
 * `spec-updater`, loop 2): the updater gate (debug / -git never check — anti-downgrade), the
 * "dialog cancelled" skip (a non-forced check only — the forced check ignores the flag; the
 * flag is the IN-MEMORY `NewUpdateAvailableDialog.isCancelled` — the persisted
 * `updateCancelled` setting is write-only and part of no skip rule, like on the phone), the
 * provenance skip (a pacman `/opt` install without the release marker = an AUR install — the
 * AUR entry owns the update, spec `spec-arch-binary-package-release`; the block is as absolute
 * as the build gate, a forced check does NOT bypass it), and the version-comparison outcome
 * (the best same-channel tag vs the current build).
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
    fun `an aur blocked install always skips, even when forced`() {
        // The provenance block (spec `spec-arch-binary-package-release`): a pacman /opt install
        // without the release marker is an AUR install — the AUR entry owns the update, so no
        // check at all: the block is as absolute as the build gate, a FORCED check does not
        // bypass it (the updater is disabled for this install, not cancelled by the user)
        assertEquals(
            CheckDecision.Skip,
            Updater.decideCheck(updaterEnabled = true, aurBlocked = true, updateCancelled = false, isForced = true, bestTagName = "v0.0.2", currentVersion = "0.0.1"),
        )
        assertEquals(
            CheckDecision.Skip,
            Updater.decideCheck(updaterEnabled = true, aurBlocked = true, updateCancelled = true, isForced = false, bestTagName = "v0.0.2", currentVersion = "0.0.1"),
        )
        // A non-blocked install is untouched by the flag (its default)
        assertEquals(
            CheckDecision.Result(true),
            Updater.decideCheck(updaterEnabled = true, aurBlocked = false, updateCancelled = false, isForced = false, bestTagName = "v0.0.2", currentVersion = "0.0.1"),
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

    @Test
    fun `a provenance blocked check skips before any fetch starts`() {
        // The call-site wiring (spec `spec-arch-binary-package-release`, review loop 3 — L3-VG3):
        // the pure [decideCheck] pins above do not settle that `checkForUpdate` actually PASSES
        // the block down — this drives the fire-and-forget check through its seam with the block
        // forced on. A blocked install (even forced) must not start a fetch at all: the fetch
        // flag never arms and the release state stays untouched. The skip path touches no
        // network (safe on any host) — a regression of the wiring would arm
        // [Updater.isCheckingForUpdate] before the network call and fail here.
        val buildBefore = Updater.build
        Updater.checkForUpdate(isForced = true, showDialog = true, aurBlocked = true)
        // The skip settles in milliseconds on the DATA pool — a 1 s observation window is
        // generous (and the regression fails fast, the moment the fetch flag arms)
        val deadline = System.currentTimeMillis() + 1_000
        while (System.currentTimeMillis() < deadline) {
            assertFalse(
                Updater.isCheckingForUpdate,
                "the blocked check started a fetch — the provenance block was not passed to the check",
            )
            Thread.sleep(10)
        }
        assertSame(buildBefore, Updater.build, "the skip path must not touch the release state")
    }
}
