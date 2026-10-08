package app.n_zik.compagnon.updater

import app.n_zik.compagnon.utils.Preferences
import app.n_zik.compagnon.updater.models.CheckUpdateState
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * The three-state update-check choice (spec `spec-updater`, AD-9, loop 2): the
 * [CheckUpdateState.fromWire] decoder (the wire values decode, null / unknown fall back to
 * [CheckUpdateState.On]) and the `settings.json` round-trip through the real [Preferences] file
 * I/O (the [PreferencesTest] idiom): the key absent → the default On, a persisted wire value
 * survives a reload alongside the pre-existing settings.
 */
class CheckUpdateStateTest {

    @TempDir
    lateinit var dir: Path

    private fun file() = dir.resolve("settings.json")

    /** A `settings.json` without the `checkUpdateState` key (a pre-loop-2 file). */
    private fun writePreLoop2() {
        Files.writeString(file(), """{"playbackVolume":0.7}""")
    }

    @Test
    fun `the wire values decode to their state`() {
        assertEquals(CheckUpdateState.On, CheckUpdateState.fromWire("on"))
        assertEquals(CheckUpdateState.Ask, CheckUpdateState.fromWire("ask"))
        assertEquals(CheckUpdateState.Off, CheckUpdateState.fromWire("off"))
    }

    @Test
    fun `null and unknown wire values fall back to the default On`() {
        assertEquals(CheckUpdateState.On, CheckUpdateState.fromWire(null))
        assertEquals(CheckUpdateState.On, CheckUpdateState.fromWire(""))
        assertEquals(CheckUpdateState.On, CheckUpdateState.fromWire("bogus"))
        // The wire values are lowercase — a casing mismatch decodes, never crashes
        assertEquals(CheckUpdateState.On, CheckUpdateState.fromWire("ON"))
    }

    @Test
    fun `a settings file without the checkUpdateState key decodes with the default On`() {
        writePreLoop2()
        val settings = Preferences(file()).settings.value
        assertEquals(CheckUpdateState.On.wire, settings.checkUpdateState)
        assertEquals(CheckUpdateState.On, settings.checkUpdateStateValue)
        // The pre-existing values are preserved
        assertEquals(0.7f, settings.playbackVolume)
    }

    @Test
    fun `a persisted checkUpdateState choice survives a settings reload`() {
        writePreLoop2()
        Preferences(file()).update { it.copy(checkUpdateState = CheckUpdateState.Off.wire) }

        val reloaded = Preferences(file()).settings.value
        assertEquals(CheckUpdateState.Off.wire, reloaded.checkUpdateState)
        assertEquals(CheckUpdateState.Off, reloaded.checkUpdateStateValue)
        // The pre-existing values survive the write
        assertEquals(0.7f, reloaded.playbackVolume)
    }
}
