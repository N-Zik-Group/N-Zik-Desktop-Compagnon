package app.n_zik.compagnon.updater

import app.n_zik.compagnon.updater.ui.UpdateLanguage
import java.util.Locale
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals

class UpdateLanguageTest {
    @Test
    fun `explicit script takes precedence over region`() {
        assertLanguage("zh-Hans-TW", UpdateLanguage.ChineseSimplified)
        assertLanguage("zh-Hant-CN", UpdateLanguage.ChineseTraditional)
        assertLanguage("sr-Cyrl-CS", UpdateLanguage.SerbianCyrillic)
        assertLanguage("sr-Latn-RS", UpdateLanguage.SerbianLatin)
    }

    @Test
    fun `region and canonical language are used as fallbacks`() {
        assertLanguage("zh-TW", UpdateLanguage.ChineseTraditional)
        assertLanguage("sr-CS", UpdateLanguage.SerbianLatin)
        assertLanguage("id-ID", UpdateLanguage.Indonesian)
        assertLanguage("he-IL", UpdateLanguage.Hebrew)
    }

    private fun assertLanguage(tag: String, expected: UpdateLanguage) {
        assertEquals(expected, UpdateLanguage.fromLocale(Locale.forLanguageTag(tag)), tag)
    }
}
