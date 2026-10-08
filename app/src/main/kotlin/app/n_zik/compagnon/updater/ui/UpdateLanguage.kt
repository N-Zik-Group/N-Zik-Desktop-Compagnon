package app.n_zik.compagnon.updater.ui

import dev.rebelonion.translator.Language

/**
 * The changelog-translation language picker (spec `spec-updater`, AD-9, loop 2 — port of the
 * phone's `Languages` enum, `app/it/fast4x/rimusic/enums/Locales.kt`): the SAME entries, the SAME
 * BCP-47 codes and the SAME `dev.rebelonion.translator.Language` mapping as the phone, with the
 * localized string resources de-Androided to hardcoded endonyms (the desktop's only strings file
 * is English — a language's own name is universal, it needs no translation). [System] means
 * "auto-detect the source language" (the phone's `R.string.system_language`).
 */
enum class UpdateLanguage(
    /** The endonym shown in the picker (the language's own name). */
    val label: String,
    /** The BCP-47 code (the phone's `code` — kept verbatim, quirk included: Hebrew is "iw"). */
    val code: String,
    /** The translator library's language (the phone's `translatorLanguage` — kept verbatim). */
    val translator: Language,
) {
    System("System", "system", Language.AUTO),
    Afrikaans("Afrikaans", "af", Language.AFRIKAANS),
    Arabic("العربية", "ar", Language.ARABIC),
    Azerbaijani("Azərbaycan", "az", Language.AZERBAIJANI),
    Bashkir("Башҡортса", "ba", Language.BASQUE),
    Bengali("বাংলা", "bn", Language.BENGALI),
    Catalan("Català", "ca", Language.CATALAN),
    ChineseSimplified("简体中文", "zh-CN", Language.CHINESE_SIMPLIFIED),
    ChineseTraditional("繁體中文", "zh-TW", Language.CHINESE_TRADITIONAL),
    Czech("Čeština", "cs", Language.CZECH),
    Danish("Dansk", "da", Language.DANISH),
    Dutch("Nederlands", "nl", Language.DUTCH),
    English("English", "en", Language.ENGLISH),
    Esperanto("Esperanto", "eo", Language.ESPERANTO),
    Estonian("Eesti", "et", Language.ESTONIAN),
    Filipino("Filipino", "fil", Language.FILIPINO),
    Finnish("Suomi", "fi", Language.FINNISH),
    French("Français", "fr", Language.FRENCH),
    Galician("Galego", "gl", Language.GALICIAN),
    German("Deutsch", "de", Language.GERMAN),
    Greek("Ελληνικά", "el", Language.GREEK),
    Hebrew("עברית", "iw", Language.HEBREW_HE),
    Hindi("हिन्दी", "hi", Language.HINDI),
    Hungarian("Magyar", "hu", Language.HUNGARIAN),
    Italian("Italiano", "it", Language.ITALIAN),
    Indonesian("Bahasa Indonesia", "in", Language.INDONESIAN),
    Interlingua("Interlingua", "ia", Language.LATIN),
    Irish("Gaeilge", "ga", Language.IRISH),
    Japanese("日本語", "ja", Language.JAPANESE),
    Korean("한국어", "ko", Language.KOREAN),
    Malayalam("മലയാളം", "ml", Language.MALAYALAM),
    Norwegian("Norsk", "no", Language.NORWEGIAN),
    Odia("ଓଡ଼ିଆ", "or", Language.ODIA),
    Persian("فارسی", "fa", Language.PERSIAN),
    Polish("Polski", "pl", Language.POLISH),
    Portuguese("Português", "pt", Language.PORTUGUESE),
    PortugueseBrazilian("Português (Brasil)", "pt-BR", Language.PORTUGUESE),
    Romanian("Română", "ro", Language.ROMANIAN),
    Russian("Русский", "ru", Language.RUSSIAN),
    SerbianCyrillic("Српски", "sr", Language.SERBIAN),
    SerbianLatin("Srpski", "sr-CS", Language.SERBIAN),
    Sinhala("සිංහල", "si", Language.SINHALA),
    Spanish("Español", "es", Language.SPANISH),
    Swedish("Svenska", "sv", Language.SWEDISH),
    Tamil("தமிழ்", "ta", Language.TAMIL),
    Telugu("తెలుగు", "te", Language.TELUGU),
    Turkish("Türkçe", "tr", Language.TURKISH),
    Ukrainian("Українська", "uk", Language.UKRAINIAN),
    Vietnamese("Tiếng Việt", "vi", Language.VIETNAMESE),
}
