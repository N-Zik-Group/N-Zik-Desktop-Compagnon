package app.n_zik.compagnon.updater.models

import app.n_zik.compagnon.utils.formatShortFileSize
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Port of the phone's `GithubRelease` (phone's
 * `app/n_zik/android/updater/models/GithubRelease.kt`): one GitHub release of the desktop repo —
 * its assets (`builds`) are the release's artifact candidates. The phone's `readableSize`
 * (Android's `Formatter.formatShortFileSize` over the app context) is de-Androided to the pure
 * helper `formatShortFileSize` (`utils/Formatter.kt`, the desktop port).
 */
@Serializable
data class GithubRelease(
    val id: UInt,
    @SerialName("tag_name") val tagName: String,
    val name: String,
    val body: String,
    val prerelease: Boolean = false,
    @SerialName("assets") val builds: List<Build>
) {

    @Serializable
    data class Build(
        val id: UInt,
        val url: String,
        val name: String,
        val size: UInt,
        @SerialName("created_at") val createdAt: String,
        @SerialName("browser_download_url") val downloadUrl: String
    ) {
        /** Human-readable size (Android's `Formatter.formatShortFileSize`, pure port). */
        val readableSize: String
            // Not lazy: the formatter is pure, but kept a property like the phone's.
            get() = formatShortFileSize(this.size.toLong())
    }
}
