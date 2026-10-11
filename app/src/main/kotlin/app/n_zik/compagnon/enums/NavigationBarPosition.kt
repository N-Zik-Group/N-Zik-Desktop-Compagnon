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
    Left(Res.string.direction_left),
    Right(Res.string.direction_right),
    Top(Res.string.direction_top),
    Bottom(Res.string.direction_bottom),
    BottomFloating(Res.string.direction_bottom_floating);

    val isHorizontal: Boolean
        get() = this == Top || this == Bottom || this == BottomFloating

    val isBottomType: Boolean
        get() = this == Bottom || this == BottomFloating

    val isFloating: Boolean
        get() = this == BottomFloating
}
