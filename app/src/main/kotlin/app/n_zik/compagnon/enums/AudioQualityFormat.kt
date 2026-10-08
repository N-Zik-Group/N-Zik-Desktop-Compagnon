package app.n_zik.compagnon.enums

import app.n_zik.compagnon.generated.resources.*
import org.jetbrains.compose.resources.StringResource

/**
 * Port of the phone's `app/it/fast4x/rimusic/enums/AudioQualityFormat.kt`. Here it is the `quality` the
 * Compagnon asks when it forges an audio URL (contract §1.1 `Quality`, §8.1): [Auto] = the phone's own
 * quality setting.
 */
enum class AudioQualityFormat(
    val textId: StringResource,
    /** Wire value of the contract's `Quality`. */
    val wire: String,
) {
    Auto(Res.string.audio_quality_automatic, "auto"),
    High(Res.string.audio_quality_format_high, "high"),
    Low(Res.string.audio_quality_format_low, "low"),
}
