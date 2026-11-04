package app.n_zik.compagnon.utils

import java.text.NumberFormat

/**
 * Android's `Formatter.formatShortFileSize` (the phone's `readableSize` de-Androided): decimal
 * units, one decimal under 100 (`"1.2 GB"`, `"345 MB"`). Pure, so the updater's
 * `GithubRelease.Build.readableSize` reuses it.
 */
fun formatShortFileSize(bytes: Long): String {
    if (bytes < 1_000) return "${NumberFormat.getIntegerInstance().format(bytes)} B"
    val units = listOf("kB", "MB", "GB", "TB")
    var value = bytes.toDouble() / 1_000
    var unit = 0
    while (value >= 1_000 && unit < units.lastIndex) {
        value /= 1_000
        unit++
    }
    val decimals = if (value < 100) 1 else 0
    val text = NumberFormat.getNumberInstance().apply {
        minimumFractionDigits = decimals
        maximumFractionDigits = decimals
    }.format(value)
    return "$text ${units[unit]}"
}
