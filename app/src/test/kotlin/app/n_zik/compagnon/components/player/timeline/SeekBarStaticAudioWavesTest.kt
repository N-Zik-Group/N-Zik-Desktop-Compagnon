package app.n_zik.compagnon.components.player.timeline

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The deterministic fallback amplitudes of the AudioWaves timeline ([audioWaveAmplitudes]): the same
 * media id always yields the same 150 bars (seeded by the track id — the PC never decodes audio), a
 * different id a different pattern, every bar mapped to 5-100.
 */
class SeekBarStaticAudioWavesTest {

    @Test
    fun `the same media id always yields the same bars`() {
        assertEquals(audioWaveAmplitudes("track-123"), audioWaveAmplitudes("track-123"))
    }

    @Test
    fun `a different media id yields a different pattern`() {
        assertNotEquals(audioWaveAmplitudes("track-123"), audioWaveAmplitudes("track-456"))
    }

    @Test
    fun `the bars number 150 and are mapped to 5 to 100`() {
        val amplitudes = audioWaveAmplitudes("track-123")
        assertEquals(150, amplitudes.size)
        assertTrue(amplitudes.all { it in 5..100 })
    }
}
