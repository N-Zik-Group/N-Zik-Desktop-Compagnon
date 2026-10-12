package app.n_zik.compagnon.enums

import app.n_zik.compagnon.generated.resources.*
import org.jetbrains.compose.resources.StringResource

/**
 * Port of the phone's `app/it/fast4x/rimusic/enums/ThumbnailCoverType.kt`: the style of the player's
 * central cover ([Vinyl] is its default). [textId] is the phone's settings-entry label of the style
 * (the Cover & thumbnail selector of the PC's Player-appearance tab).
 */
enum class ThumbnailCoverType(
    val textId: StringResource,
) {
    Vinyl(Res.string.cover_type_vinyl),
    CD(Res.string.cover_type_cd),
    CDwithCover(Res.string.cover_type_cd_with_cover);
}
