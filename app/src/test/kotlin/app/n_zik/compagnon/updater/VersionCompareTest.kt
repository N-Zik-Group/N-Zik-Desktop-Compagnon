package app.n_zik.compagnon.updater

import app.n_zik.compagnon.updater.services.Updater
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Pins [Updater.isVersionNewer] (spec `spec-updater`, AD-1): the phone's comparison logic,
 * ported UNCHANGED — the numeric base first (padded to the longest), then the dev date suffix
 * ("…-dev-YYYYMMDD") as the tiebreak. The `v` prefix is stripped on both sides.
 */
class VersionCompareTest {

    @Test
    fun `a strictly newer base version wins`() {
        assertTrue(Updater.isVersionNewer("0.0.2", "0.0.1"))
        assertTrue(Updater.isVersionNewer("1.0.0", "0.9.9"))
        assertTrue(Updater.isVersionNewer("0.10.0", "0.9.0"))
    }

    @Test
    fun `an equal base version is not newer`() {
        assertFalse(Updater.isVersionNewer("0.0.1", "0.0.1"))
        assertFalse(Updater.isVersionNewer("1.2.3", "1.2.3"))
    }

    @Test
    fun `an older base version is not newer`() {
        assertFalse(Updater.isVersionNewer("0.0.1", "0.0.2"))
        assertFalse(Updater.isVersionNewer("0.9.0", "0.10.0"))
    }

    @Test
    fun `a shorter base is padded with zeros before comparing`() {
        // 1.0 == 1.0.0, 1.0 < 1.0.1 — the phone's pad-to-maxParts rule
        assertFalse(Updater.isVersionNewer("1.0", "1.0.0"))
        assertFalse(Updater.isVersionNewer("1.0.0", "1.0"))
        assertFalse(Updater.isVersionNewer("1.0", "1.0.1"))
        assertTrue(Updater.isVersionNewer("1.0.1", "1.0"))
    }

    @Test
    fun `the v prefix is stripped on both sides`() {
        assertTrue(Updater.isVersionNewer("v0.0.2", "0.0.1"))
        assertFalse(Updater.isVersionNewer("0.0.2", "v0.0.3"))
        assertFalse(Updater.isVersionNewer("v0.0.2", "v0.0.2"))
    }

    @Test
    fun `the dev date suffix breaks a base tie`() {
        // The phone's documented example (its KDoc L102-104): the later date wins, the same date does not
        assertFalse(Updater.isVersionNewer("v7.3.2-dev-20260806", "7.3.2-dev-20260806"))
        assertFalse(Updater.isVersionNewer("7.3.2-dev-20260801", "v7.3.2-dev-20260806"))
        assertTrue(Updater.isVersionNewer("v7.3.2-dev-20260806", "7.3.2-dev-20260801"))
    }

    @Test
    fun `a missing date suffix counts as zero`() {
        // The phone's tiebreak: no third part → 0, so any dated dev build of the same base is "newer"
        assertTrue(Updater.isVersionNewer("0.0.2-dev-20260901", "0.0.2"))
        assertFalse(Updater.isVersionNewer("0.0.2", "0.0.2-dev-20260901"))
    }
}
