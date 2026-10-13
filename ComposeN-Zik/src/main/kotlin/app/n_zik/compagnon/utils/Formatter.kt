package app.n_zik.compagnon.utils

import java.util.Locale

/**
 * Android's `Formatter.formatShortFileSize` (the phone's `readableSize` de-Androided): decimal
 * units, one decimal under 100 (`"1.2 GB"`, `"345 MB"`). Pure, so the updater's
 * `GithubRelease.Build.readableSize` reuses it.
 */
fun formatShortFileSize(bytes: Long): String {
    if (bytes < 1_000) return "$bytes B"
    val units = listOf("kB", "MB", "GB", "TB")
    var value = bytes.toDouble() / 1_000
    var unit = 0
    while (value >= 1_000 && unit < units.lastIndex) {
        value /= 1_000
        unit++
    }
    val text = if (value < 100) String.format(Locale.ROOT, "%.1f", value) else String.format(Locale.ROOT, "%.0f", value)
    return "$text ${units[unit]}"
}
