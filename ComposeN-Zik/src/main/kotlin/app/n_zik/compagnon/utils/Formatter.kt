package app.n_zik.compagnon.utils

import androidx.compose.runtime.Composable
import app.n_zik.compagnon.generated.resources.*
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date
import org.jetbrains.compose.resources.stringResource

internal fun formatWholePercent(percent: Number): String =
    NumberFormat.getPercentInstance().format(percent.toDouble() / 100)

internal fun formatDecimal(value: Number, fractionDigits: Int): String =
    NumberFormat.getNumberInstance().apply {
        minimumFractionDigits = fractionDigits
        maximumFractionDigits = fractionDigits
        isGroupingUsed = false
    }.format(value)

internal fun formatLastUpdateCheck(timeMillis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(timeMillis))

/** Formats decimal file sizes with locale-specific numbers and unit labels. */
@Composable
fun formatShortFileSize(bytes: Long): String {
    val units = listOf(
        Res.string.common_file_size_bytes_format,
        Res.string.common_file_size_kilobytes_format,
        Res.string.common_file_size_megabytes_format,
        Res.string.common_file_size_gigabytes_format,
        Res.string.common_file_size_terabytes_format,
    )
    var value = bytes.toDouble()
    var unit = 0
    while (value >= 1_000 && unit < units.lastIndex) {
        value /= 1_000
        unit++
    }
    val decimals = if (unit > 0 && value < 100) 1 else 0
    val text = if (unit == 0) NumberFormat.getIntegerInstance().format(bytes) else NumberFormat.getNumberInstance().apply {
        minimumFractionDigits = decimals
        maximumFractionDigits = decimals
    }.format(value)
    return formatText(stringResource(units[unit]), text)
}
