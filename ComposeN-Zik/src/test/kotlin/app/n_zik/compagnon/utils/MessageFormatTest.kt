package app.n_zik.compagnon.utils

import app.n_zik.compagnon.generated.resources.*
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Desktop formatting for the phone's printf placeholders and Compose's indexed plural arguments.
 */
class MessageFormatTest {
    @Test
    fun `formatText applies the placeholders`() {
        assertEquals("Disliked «X»", formatText("Disliked %s", "«X»"))
        assertEquals("Paired with 2 devices", formatText("Paired with %d devices", 2))
        assertEquals("A and B", formatText("%s and %s", "A", "B"))
    }

    @Test
    fun `French keeps translated labels until complete formats are available`() = runBlocking {
        AppLanguage.applyTag("fr")
        try {
            assertEquals("0", getString(Res.string.ui_translation_available_updates_channel_available_format))
            assertEquals("0", getString(Res.string.ui_translation_available_library_song_count_with_duration))
            assertEquals("0", getString(Res.string.ui_translation_available_settings_cache_custom_size_format))
            assertEquals("0", getString(Res.string.ui_translation_available_settings_cache_usage_percent_format))
            assertEquals("0", getString(Res.string.ui_translation_available_settings_cache_usage_unlimited_format))
            assertEquals("Mise à jour disponible", getString(Res.string.updates_available))
        } finally {
            AppLanguage.applyTag(null)
        }
    }

    @Test
    fun `desktop audit resources format counts and literal percentages`() = runBlocking {
        AppLanguage.applyTag("en")
        try {
            assertEquals("1 song", getPluralString(Res.plurals.library_song_count, 1, 1))
            assertEquals("2 songs", getPluralString(Res.plurals.library_song_count, 2, 2))
            assertEquals("1 song • 3:42", getPluralString(Res.plurals.library_song_count_with_duration, 1, 1, "3:42"))
            assertEquals("2 songs • …", getPluralString(Res.plurals.library_song_count_with_duration, 2, 2, "…"))
            assertEquals("Battery: 75%", formatMessage(Res.string.audio_device_battery_format, 75))
            assertEquals("Custom: 512 MB", formatMessage(Res.string.settings_cache_custom_size_format, "512 MB"))
            assertEquals("128 MB used (25%)", formatMessage(Res.string.settings_cache_usage_percent_format, "128 MB", 25L))
            assertEquals("128 MB used (Unlimited)", formatMessage(Res.string.settings_cache_usage_unlimited_format, "128 MB"))
            assertEquals("BETA update available", getString(Res.string.updates_channel_available_format, "BETA"))
        } finally {
            AppLanguage.applyTag(null)
        }
    }

    @Test
    fun `dislike toasts fill missing labels with localized metadata`() = runBlocking {
        AppLanguage.applyTag("en")
        try {
            assertEquals(
                "Disliked Unknown Title",
                formatLabeledMessage(Res.string.library_dislikes_added, null, Res.string.metadata_unknown_title),
            )
            assertEquals(
                "Removed Unknown Title from dislikes",
                formatLabeledMessage(Res.string.library_dislikes_removed, null, Res.string.metadata_unknown_title),
            )
            assertEquals(
                "Disliked Unknown Artist",
                formatLabeledMessage(Res.string.library_dislikes_added, null, Res.string.metadata_unknown_artist),
            )
            assertEquals(
                "Disliked «X»",
                formatLabeledMessage(Res.string.library_dislikes_added, "«X»", Res.string.metadata_unknown_artist),
            )
        } finally {
            AppLanguage.applyTag(null)
        }
    }

    @Test
    fun `whole percentages use the selected locale`() {
        AppLanguage.applyTag("fr")
        try {
            val expected = java.text.NumberFormat.getPercentInstance(java.util.Locale.FRENCH).format(0.25)
            assertEquals(expected, formatWholePercent(25))
        } finally {
            AppLanguage.applyTag(null)
        }
    }

    @Test
    fun `last check dates use the selected locale's date and time styles`() {
        AppLanguage.applyTag("en-US")
        try {
            val time = 1_700_000_000_000L
            val expected = java.text.DateFormat.getDateTimeInstance(
                java.text.DateFormat.MEDIUM,
                java.text.DateFormat.SHORT,
            ).format(java.util.Date(time))
            assertEquals(expected, formatLastUpdateCheck(time))
        } finally {
            AppLanguage.applyTag(null)
        }
    }

    @Test
    fun `playback decimals and elapsed time use the active locale without grouping`() {
        AppLanguage.applyTag("fr")
        try {
            assertEquals("1,5", formatDecimal(1.5f, 1))
            assertEquals("1,00", formatDecimal(1f, 2))
            assertEquals("1:05", formatAsDuration(65_000))
            assertEquals("25:01:02", formatAsDuration(90_062_000))
        } finally {
            AppLanguage.applyTag(null)
        }
    }

    @Test
    fun `formatText without args returns the raw message`() {
        assertEquals("Done", formatText("Done"))
    }

    @Test
    fun `formatText with a mismatch keeps the raw message instead of throwing`() {
        assertEquals("Disliked %s", formatText("Disliked %s"))
        assertEquals("%s %s", formatText("%s %s", "only-one"))
    }

    @Test
    fun `formatMessage applies the args to a real resource`() = runBlocking {
        // The phone's `added_to_dislikes` is "Disliked %s" in English; with the l10n strings the
        // resolved language depends on the JVM locale — locale-agnostic: the argument must be

        // applied (no raw `%s` left) whatever the resolved language is.
        val result = formatMessage(Res.string.library_dislikes_added, "«X»")
        assertTrue(result.endsWith("«X»"))
        assertFalse("%s" in result)
    }

    @Test
    fun `Arabic plural selection keeps localized digits in the rendered count`() = runBlocking {
        AppLanguage.applyTag("ar")
        try {
            val count = getPluralString(Res.plurals.library_song_count, 1, formatDecimal(1, 0))
            assertEquals(true, count.contains('١'))
        } finally {
            AppLanguage.applyTag(null)
        }
    }
}
