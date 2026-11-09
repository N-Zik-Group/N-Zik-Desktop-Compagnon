package app.n_zik.compagnon.utils

import androidx.compose.runtime.Composable
import app.n_zik.compagnon.generated.resources.*
import java.util.Locale
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** Whether to use a new format in place of its previously localized fragments. */
@Composable
internal fun hasLocalizedFormat(availability: StringResource): Boolean =
    Locale.getDefault().language == "en" || stringResource(availability) == "1"

@Composable
internal fun localizedSongCountWithDuration(count: Int, duration: String?): String {
    if (duration != null && hasLocalizedFormat(Res.string.ui_translation_available_library_song_count_with_duration)) {
        return pluralStringResource(Res.plurals.library_song_count_with_duration, count, formatDecimal(count, 0), duration)
    }
    val localizedCount = pluralStringResource(Res.plurals.library_song_count, count, formatDecimal(count, 0))
    return if (duration == null) localizedCount else "$localizedCount - $duration"
}

@Composable
internal fun formatCompactPlayTime(millis: Long): String {
    val minutes = millis.coerceAtLeast(0) / 60_000
    return when {
        minutes < 60 -> formatText(stringResource(Res.string.common_duration_minutes_format), minutes)
        minutes < 1_440 -> formatText(stringResource(Res.string.common_duration_hours_format), minutes / 60)
        else -> formatText(stringResource(Res.string.common_duration_days_format), minutes / 1_440)
    }
}
