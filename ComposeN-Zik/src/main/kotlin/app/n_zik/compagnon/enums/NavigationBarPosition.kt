package app.n_zik.compagnon.enums

import app.n_zik.compagnon.generated.resources.*
import org.jetbrains.compose.resources.StringResource

/**
 * Port of the phone's `app/it/fast4x/rimusic/enums/NavigationBarPosition.kt` (the position of the
 * home's navigation bar), in the phone's declaration order, with its properties: [isHorizontal] is
 * the phone's — the bar is horizontal in [Top], [Bottom], [BottomFloating]; [isBottomType] /
 * [isFloating] select the bottom positions (the phone's `Skeleton.Draw` and the FAB's extra bottom
 * space both key on them).
 */
enum class NavigationBarPosition(
    val textId: StringResource,
) {
    Left(Res.string.lyrics_alignment_left_label),
    Right(Res.string.lyrics_alignment_right_label),
    Top(Res.string.navigation_position_top_label),
    Bottom(Res.string.navigation_position_bottom_label),
    BottomFloating(Res.string.navigation_position_bottom_floating_label);

    val isHorizontal: Boolean
        get() = this == Top || this == Bottom || this == BottomFloating

    val isBottomType: Boolean
        get() = this == Bottom || this == BottomFloating

    val isFloating: Boolean
        get() = this == BottomFloating
}
