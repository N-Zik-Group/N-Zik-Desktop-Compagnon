package app.n_zik.compagnon.utils

import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * The PC's "App language" setting (spec `spec-remove-ui-sync`) through the real [Preferences] file
 * I/O: a settings file without the `language` key decodes with the [AppLanguage.SYSTEM] default,
 * and the one-time migration rewrites the old phone-mirroring sentinels (`auto_pc`, `auto_tel`) to
 * `system`, preserving the pre-existing values.
 */
class PreferencesTest {

    @TempDir
    lateinit var dir: Path

    private fun file() = dir.resolve("settings.json")

    /** A settings file written before the spec: the old keys only, no `language`. */
    private fun writeWithoutLanguage() {
        Files.writeString(
            file(),
            """{"exoPlayerCustomCache":48,"playbackVolume":0.7,"disableScrollingText":true}""",
        )
    }

    @Test
    fun `a settings file without the language key decodes with the system default`() {
        writeWithoutLanguage()

        val settings = Preferences(file()).settings.value
        assertEquals(AppLanguage.SYSTEM, settings.language)
        // The pre-existing values are preserved.
        assertPreserved(settings)
    }

    @Test
    fun `the migration rewrites the old auto_pc sentinel to system`() {
        migrateFrom("auto_pc")

        val reloaded = Preferences(file()).settings.value
        assertEquals(AppLanguage.SYSTEM, reloaded.language)
        assertPreserved(reloaded)
    }

    @Test
    fun `the migration rewrites the old auto_tel sentinel to system`() {
        migrateFrom("auto_tel")

        val reloaded = Preferences(file()).settings.value
        assertEquals(AppLanguage.SYSTEM, reloaded.language)
        assertPreserved(reloaded)
    }

    @Test
    fun `the migration is a no-op for a fresh or already-migrated file`() {
        writeWithoutLanguage()
        val preferences = Preferences(file()) // runs the migration on init (inert here)
        val before = Files.readString(file())
        // Nothing was written by the construction.
        assertEquals(before, Files.readString(file()))
        // A manual code is never touched.
        preferences.update { it.copy(language = "fr") }
        val reloaded = Preferences(file()).settings.value
        assertEquals("fr", reloaded.language)
        assertPreserved(reloaded)
    }

    @Test
    fun `the migration is a no-op for an already-migrated file`() {
        // A pre-spec file whose `language` already is the migrated `system` sentinel
        Files.writeString(
            file(),
            """{"exoPlayerCustomCache":48,"playbackVolume":0.7,"disableScrollingText":true,"language":"system"}""",
        )
        val before = Files.readString(file())
        val preferences = Preferences(file()) // runs the migration on init (inert here)
        assertEquals(before, Files.readString(file())) // nothing was rewritten
        assertEquals(AppLanguage.SYSTEM, preferences.settings.value.language)
        assertPreserved(preferences.settings.value)
    }

    /** A pre-spec `settings.json`: the old keys plus the given `language` value. */
    private fun migrateFrom(language: String) {
        Files.writeString(
            file(),
            """{"exoPlayerCustomCache":48,"playbackVolume":0.7,"disableScrollingText":true,"language":"$language"}""",
        )
        Preferences(file()) // runs the migration on init
    }

    private fun assertPreserved(settings: UserSettings) {
        assertEquals(48, settings.exoPlayerCustomCache)
        assertEquals(0.7f, settings.playbackVolume)
        assertEquals(true, settings.disableScrollingText)
    }
}
