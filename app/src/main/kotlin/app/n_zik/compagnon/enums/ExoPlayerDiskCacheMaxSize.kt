package app.n_zik.compagnon.enums

import androidx.compose.runtime.Composable
import app.n_zik.compagnon.generated.resources.*
import org.jetbrains.compose.resources.stringResource

/**
 * Port of the phone's `app/it/fast4x/rimusic/enums/ExoPlayerDiskCacheMaxSize.kt`: the values of "Song cache
 * max size", here the ceiling of the Compagnon's audio cache (`playback/cache/AudioCache`). Same values,
 * same sizes (`megabytes` × 1000 × 1000), default `2GB` like the phone. [Custom] reads its size from
 * `exoPlayerCustomCache`.
 */
enum class ExoPlayerDiskCacheMaxSize(
    val megabytes: Int,
) {
    `Disabled`(1),
    `32MB`(32),
    `512MB`(512),
    `1GB`(1024),
    `2GB`(2048),
    `4GB`(4096),
    `8GB`(8192),
    Unlimited(0),
    Custom(1_000_000);

    val bytes: Long = megabytes.times(1000L).times(1000)

    val text: String
        @Composable
        get() = when (this) {
            Disabled -> stringResource(Res.string.turn_off)
            Unlimited -> stringResource(Res.string.unlimited)
            Custom -> stringResource(Res.string.custom)
            else -> this.name
        }

    /**
     * Ceiling of the audio cache in bytes: `0` = nothing is downloaded ([Disabled]), `null` = no ceiling
     * ([Unlimited]); [Custom] is [customMegabytes] MB.
     */
    fun cacheBytes(customMegabytes: Int): Long? = when (this) {
        Disabled -> 0L
        Unlimited -> null
        Custom -> customMegabytes.toLong() * 1000L * 1000L
        else -> bytes
    }
}
