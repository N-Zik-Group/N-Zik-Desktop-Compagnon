package app.n_zik.compagnon.utils

import java.util.Locale
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The PC's "App language" setting (spec `spec-remove-ui-sync`): the resolver and the runtime
 * application. [AppLanguage.applyTag] mutates the JVM default locale, so `applyTag(null)` (which
 * restores the OS locale and resets the recomposition key) runs after every test.
 */
class AppLanguageTest {

    @AfterEach
    fun restoreDefaultLocale() {
        AppLanguage.applyTag(null)
    }

    // ---- resolveLanguageTag (pure) ----------------------------------------------------------------

    @Test
    fun `SYSTEM resolves to the OS locale`() {
        assertNull(AppLanguage.resolveLanguageTag(AppLanguage.SYSTEM))
    }

    @Test
    fun `a manual code is used as is`() {
        assertEquals("pt-BR", AppLanguage.resolveLanguageTag("pt-BR"))
    }

    @Test
    fun `an unknown code resolves as a tag, never a crash`() {
        assertEquals("xy", AppLanguage.resolveLanguageTag("xy"))
    }

    // ---- applyTag ----------------------------------------------------------------------------------

    @Test
    fun `applying a tag sets the JVM locale and bumps the recomposition key`() {
        AppLanguage.applyTag("fr")
        assertEquals(Locale.forLanguageTag("fr"), Locale.getDefault())
        assertEquals("fr", AppLanguage.appliedTag.value)
    }

    @Test
    fun `applying the same tag twice is a no-op on the key`() = runTest {
        AppLanguage.applyTag("fr")
        val emissions = mutableListOf<String?>()
        val job = launch { AppLanguage.appliedTag.take(3).collect { emissions += it } }
        AppLanguage.applyTag("fr")
        advanceUntilIdle()
        job.cancel()
        // The collector sees the current value exactly once: the second apply of the same tag dedups.
        assertEquals(listOf<String?>("fr"), emissions)
    }

    @Test
    fun `an undetermined tag is never applied, in any case variant`() {
        AppLanguage.applyTag("fr")
        AppLanguage.applyTag("und")
        assertNull(AppLanguage.appliedTag.value)
        assertNotEquals("und", Locale.getDefault().language)
        AppLanguage.applyTag("und-FR")
        assertNull(AppLanguage.appliedTag.value)
        // Case variants normalize to the `und` language through `Locale.forLanguageTag`.
        AppLanguage.applyTag("UND")
        assertNull(AppLanguage.appliedTag.value)
        AppLanguage.applyTag("Und-FR")
        assertNull(AppLanguage.appliedTag.value)
    }

    @Test
    fun `a blank tag keeps the OS locale`() {
        AppLanguage.applyTag("fr")
        AppLanguage.applyTag("   ")
        assertNull(AppLanguage.appliedTag.value)
    }

    @Test
    fun `an unparseable tag keeps the OS locale`() {
        AppLanguage.applyTag("fr")
        AppLanguage.applyTag("-")
        assertNull(AppLanguage.appliedTag.value)
    }

    @Test
    fun `the legacy code iw is normalised to he`() {
        AppLanguage.applyTag("iw")
        assertEquals("he", AppLanguage.appliedTag.value)
        assertEquals("he", Locale.getDefault().language)
    }

    @Test
    fun `the legacy code in is normalised to id`() {
        AppLanguage.applyTag("in")
        assertEquals("id", AppLanguage.appliedTag.value)
        assertEquals("id", Locale.getDefault().language)
    }

    // ---- LANGUAGES + labelOf -----------------------------------------------------------------------

    @Test
    fun `the picker has the phone's 48 languages with distinct codes`() {
        assertEquals(48, AppLanguage.LANGUAGES.size)
        assertEquals(48, AppLanguage.LANGUAGES.map { it.first }.distinct().size)
        AppLanguage.LANGUAGES.forEach { (code, endonym) ->
            assertTrue(code.isNotBlank(), "blank picker code")
            assertTrue(endonym.isNotBlank(), "blank endonym for $code")
        }
    }

    @Test
    fun `the spec-pinned endonyms use the standard forms`() {
        assertEquals("Suomi", AppLanguage.labelOf("fi"))
        assertEquals("Srpski", AppLanguage.labelOf("sr-CS"))
        assertEquals("Bahasa Indonesia", AppLanguage.labelOf("in"))
        assertEquals("Filipino", AppLanguage.labelOf("fil"))
        assertEquals("한국어", AppLanguage.labelOf("ko"))
        assertEquals("Русский", AppLanguage.labelOf("ru"))
        // The Cyrillic "Српски" of `sr`, built from code points.
        assertEquals(String(intArrayOf(0x0421, 0x0440, 0x043F, 0x0441, 0x043A, 0x0438), 0, 6), AppLanguage.labelOf("sr"))
    }

    @Test
    fun `tamil, telugu, bengali and hindi use the phone's standard endonyms`() {
        // Built from code points: the non-Latin glyphs must not be retyped by hand.
        val tamil = String(intArrayOf(0x0B95, 0x0BAE, 0x0BBF, 0x0BBB, 0x0BCD), 0, 5)
        val telugu = String(intArrayOf(0x0C24, 0x0C46, 0x0C32, 0x0C41, 0x0C17, 0x0C41), 0, 6)
        val bengali = String(intArrayOf(0x09AC, 0x09BE, 0x0982, 0x09B2, 0x09BE), 0, 5)
        val hindi = String(intArrayOf(0x0939, 0x093F, 0x0928, 0x094D, 0x0926, 0x0940), 0, 6)
        assertEquals(tamil, AppLanguage.LANGUAGES.first { it.first == "ta" }.second)
        assertEquals(telugu, AppLanguage.LANGUAGES.first { it.first == "te" }.second)
        assertEquals(bengali, AppLanguage.LANGUAGES.first { it.first == "bn" }.second)
        assertEquals(hindi, AppLanguage.LANGUAGES.first { it.first == "hi" }.second)
    }

    @Test
    fun `an unknown code labels itself`() {
        assertEquals("xy", AppLanguage.labelOf("xy"))
    }

    @Test
    fun `the system sentinel labels itself`() {
        assertEquals("System", AppLanguage.labelOf(AppLanguage.SYSTEM))
    }

    @Test
    fun `the dialog values are System first then the phone's languages in order`() {
        val values = AppLanguage.dialogValues()
        assertEquals(AppLanguage.SYSTEM, values.first())
        assertEquals(AppLanguage.LANGUAGES.map { it.first }, values.drop(1))
        assertEquals(49, values.size)
    }
}
