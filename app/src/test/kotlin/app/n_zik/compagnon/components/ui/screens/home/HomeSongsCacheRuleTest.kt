package app.n_zik.compagnon.components.ui.screens.home

import app.n_zik.compagnon.generated.resources.Res
import app.n_zik.compagnon.generated.resources.turn_off
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The phone-cache cap label rule (spec `spec-remove-ui-sync`): the wire's `maxText` is no longer
 * consumed, the label is computed from the phone's `maxBytes` — the `Disabled` cap (1 MB decimal,
 * the phone's `ExoPlayerDiskCacheMaxSize.Disabled`) reads "Turn off" ([Res.string.turn_off]), every
 * other cap is formatted (`null` label id), and `null` (the phone's `Unlimited`) hides the bar.
 */
class HomeSongsCacheRuleTest {

    @Test
    fun `the disabled sentinel is the phone's disabled bytes`() {
        // The phone's `ExoPlayerDiskCacheMaxSize.Disabled` is 1 MB decimal (its `megabytes` = 1)
        assertEquals(1_000_000L, PHONE_CACHE_DISABLED_MAX_BYTES)
        assertTrue(isPhoneCacheDisabled(1_000_000L))
    }

    @Test
    fun `the phone's ladder caps are never read as disabled`() {
        // 32 MB, 512 MB, 1 GB, 2 GB, 4 GB, 8 GB (the phone's `ExoPlayerDiskCacheMaxSize`, megabytes × 10⁶)
        listOf(32_000_000L, 512_000_000L, 1_024_000_000L, 2_048_000_000L, 4_096_000_000L, 8_192_000_000L)
            .forEach { cap -> assertFalse(isPhoneCacheDisabled(cap), "cap $cap misread as disabled") }
    }

    @Test
    fun `a custom cap of at least 32 MB is never read as disabled`() {
        // The phone's custom cap is at least 32 MB (its `valueMin`): it can never collide with the
        // disabled sentinel
        assertFalse(isPhoneCacheDisabled(32_000_000L))
        assertFalse(isPhoneCacheDisabled(48_000_000L))
        assertFalse(isPhoneCacheDisabled(10_000_000_000L))
    }

    @Test
    fun `an unlimited cap is not the disabled sentinel`() {
        // `null` hides the bar; it must not be mistaken for the disabled label either
        assertFalse(isPhoneCacheDisabled(null))
    }

    @Test
    fun `the disabled cap labels the phone's Turn off string, every other cap formats its bytes`() {
        // The sentinel reads the phone's own "Turn off" (its `Disabled.text`)
        assertEquals(Res.string.turn_off, phoneCacheCapLabelId(1_000_000L))
        assertEquals(Res.string.turn_off, phoneCacheCapLabelId(PHONE_CACHE_DISABLED_MAX_BYTES))
        // Ladder caps, custom caps and `null` all mean "format the bytes" (or hide the bar)
        val caps = listOf(
            32_000_000L, 512_000_000L, 1_024_000_000L, 2_048_000_000L, 4_096_000_000L, 8_192_000_000L,
            48_000_000L, 10_000_000_000L,
        )
        caps.forEach { cap -> assertNull(phoneCacheCapLabelId(cap), "cap $cap got a label id") }
        assertNull(phoneCacheCapLabelId(null))
    }
}
