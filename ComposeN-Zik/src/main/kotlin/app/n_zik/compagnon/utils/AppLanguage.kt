package app.n_zik.compagnon.utils

import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The single locale authority of the Compagnon.
 *
 * The PC's own "App language" setting — [SYSTEM] (the PC's OS locale, the default) or a BCP-47 code
 * of the phone's list — is resolved into ONE locale tag ([resolveLanguageTag]) and applied at
 * runtime ([applyTag]): `Locale.setDefault` plus a bump of [appliedTag], the composition root's
 * recomposition key (`stringResource` is not locale-state-tracked on desktop). Nothing ever
 * requires an app restart; an absent or unparseable tag keeps the OS locale, and an unknown code
 * falls back to the PC's `values/` (English) through the resource lookup — never a crash.
 */
object AppLanguage {

    /** "System" (the default): the PC's OS locale. */
    const val SYSTEM = "system"

    private val _appliedTag = MutableStateFlow<String?>(null)

    /**
     * The PC's OS locale, captured at initialisation (before any [applyTag] override): what the
     * [SYSTEM] setting and an absent or unparseable tag resolve back to.
     */
    private val osLocale: Locale = Locale.getDefault()

    /**
     * The tag applied as the JVM default locale, `null` while the OS locale is in effect. Bumped
     * by [applyTag]: the composition root keys its content on it for the visible switch.
     */
    val appliedTag: StateFlow<String?> = _appliedTag.asStateFlow()

    /**
     * Pure resolution of the setting: [SYSTEM] → `null` (the OS locale); anything else is a manual
     * BCP-47 code used as is (an unknown code falls back to the PC's `values/`, the English one).
     */
    fun resolveLanguageTag(setting: String): String? = when (setting) {
        SYSTEM -> null
        else -> setting
    }

    /**
     * Applies [tag] as the JVM default locale and bumps [appliedTag]. An undetermined tag (any
     * case variant of `und`/`und-…`, read from the normalized tag), a blank or unparseable tag
     * never overrides the resolver: the OS locale is restored (an `und` locale is never applied).
     * Applying the same tag twice is a no-op (the [StateFlow] dedups).
     */
    fun applyTag(tag: String?) {
        val locale = tag
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?.let { runCatching { Locale.forLanguageTag(it) }.getOrNull() }
            ?.takeIf { it.language.isNotEmpty() }
            // The guard reads the normalized tag, not the raw input: a case variant like `UND`
            // normalizes to the `und` language and must not be applied either.
            ?.takeIf { normalized ->
                val t = normalized.toLanguageTag()
                !t.equals("und", ignoreCase = true) && !t.startsWith("und-", ignoreCase = true)
            }
        // A `null` locale resolves back to the OS locale (the tag is never applied in that case).
        Locale.setDefault(locale ?: osLocale)
        _appliedTag.value = locale?.toLanguageTag()
    }

    /**
     * The phone's 48 languages as picker entries (code → endonym). The codes are the phone's
     * `Languages.code` verbatim (`iw`, `in`, `sr-CS`…): the JVM normalizes the legacy ones
     * (`iw` → `he`, `in` → `id`) through [Locale.forLanguageTag]. The endonyms are each
     * language's own standard name — static on the PC (no per-locale strings, no wire); the
     * phone's `System` entry is covered by [SYSTEM] and stays out of the list.
     */
    val LANGUAGES: List<Pair<String, String>> = listOf(
        "af" to "Afrikaans",
        "az" to "Azərbaycanca",
        "ar" to "العربية",
        "ba" to "Башҡортса",
        "bn" to "বাংলা",
        "ca" to "Català",
        "zh-CN" to "简体中文",
        "zh-TW" to "繁體中文",
        "da" to "Dansk",
        "nl" to "Nederlands",
        "en" to "English",
        "eo" to "Esperanto",
        "et" to "eesti",
        "fil" to "Filipino",
        "fi" to "Suomi",
        "gl" to "Galego",
        "it" to "Italiano",
        "in" to "Bahasa Indonesia",
        "ga" to "Gaeilge",
        "ja" to "日本語",
        "ko" to "한국어",
        "cs" to "Čeština",
        "de" to "Deutsch",
        "el" to "Ελληνικά",
        "iw" to "עברית",
        "hi" to "हिन्दी",
        "hu" to "Magyar",
        "ia" to "Interlingua",
        "es" to "Español",
        "fr" to "Français",
        "ml" to "മലയാളം",
        "no" to "Norsk",
        "or" to "ଓଡ଼ିଆ",
        "fa" to "فارسی",
        "pl" to "Polski",
        "pt" to "Português",
        "pt-BR" to "Português (Brasil)",
        "ro" to "Română",
        "ru" to "Русский",
        "sr" to "Српски",
        "sr-CS" to "Srpski",
        "si" to "සිංහල",
        "sv" to "Svenska",
        "ta" to "கமி஻்",
        "te" to "తెలుగు",
        "tr" to "Türkçe",
        "uk" to "Українська",
        "vi" to "Tiếng Việt",
    )

    /** The endonym of a picker code, "System" for the [SYSTEM] sentinel; the code itself for a tag that is not in the phone's list. */
    fun labelOf(code: String): String = when (code) {
        SYSTEM -> "System"
        else -> LANGUAGES.firstOrNull { it.first == code }?.second ?: code
    }

    /** The "App language" dialog's values: the [SYSTEM] sentinel first, then [LANGUAGES]' codes in their order. */
    fun dialogValues(): List<String> = listOf(SYSTEM) + LANGUAGES.map { it.first }
}
