package app.n_zik.compagnon.utils

import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * The persisted "App language" fields (contract 1.9.0, `ui.language`) through the real
 * [Preferences] file I/O: a pre-1.9 `settings.json` (no `language` / `lastPhoneLanguage` key)
 * decodes with the defaults, and the `onPhoneLanguage` transform of the composition root
 * persists a fresh phone language alongside the pre-existing values.
 */
class PreferencesTest {

    @TempDir
    lateinit var dir: Path

    private fun file() = dir.resolve("settings.json")

    /** A pre-1.9 `settings.json`: the old keys only, no `language` / `lastPhoneLanguage`. */
    private fun writePre19() {
        Files.writeString(
            file(),
            """{"exoPlayerCustomCache":48,"playbackVolume":0.7,"disableScrollingText":true}""",
        )
    }

    @Test
    fun `a pre-1_9 settings file decodes with the defaults for the language fields`() {
        writePre19()

        val settings = Preferences(file()).settings.value
        assertEquals(AppLanguage.AUTO_TEL, settings.language)
        assertNull(settings.lastPhoneLanguage)
        // The pre-existing values are preserved.
        assertEquals(48, settings.exoPlayerCustomCache)
        assertEquals(0.7f, settings.playbackVolume)
        assertEquals(true, settings.disableScrollingText)
    }

    @Test
    fun `the onPhoneLanguage transform persists the phone language alongside the pre-existing values`() {
        writePre19()
        val preferences = Preferences(file())
        // The same transform the `App()` composition root applies (the `onPhoneLanguage` body in
        // `Main.kt`): only a non-null phone language is filed, and an unchanged one is a no-op.
        preferences.update {
            if (it.lastPhoneLanguage == "fr") it else it.copy(lastPhoneLanguage = "fr")
        }

        // Reloaded from the file (a fresh [Preferences] over the same path).
        val reloaded = Preferences(file()).settings.value
        assertEquals("fr", reloaded.lastPhoneLanguage)
        // The pre-existing values survive the write.
        assertEquals(48, reloaded.exoPlayerCustomCache)
        assertEquals(0.7f, reloaded.playbackVolume)
        assertEquals(true, reloaded.disableScrollingText)
        // The `auto_tel` fallback of the "App language" setting resolves to the persisted language.
        assertEquals("fr", AppLanguage.resolveLanguageTag(reloaded.language, null, reloaded.lastPhoneLanguage))
    }
}
