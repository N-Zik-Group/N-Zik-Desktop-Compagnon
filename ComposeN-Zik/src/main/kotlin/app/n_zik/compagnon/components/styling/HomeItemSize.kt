package app.n_zik.compagnon.components.styling

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.n_zik.compagnon.generated.resources.*
import org.jetbrains.compose.resources.StringResource

/**
 * Port of the phone's `HomeItemSize` (`app/it/fast4x/rimusic/enums/HomeItemSize.kt`): the home grid's
 * item size — 100 / 130 / 160 dp.
 */
enum class HomeItemSize(val dp: Dp, val labelId: StringResource) {
    Small(100.dp, Res.string.small),
    Medium(130.dp, Res.string.medium),
    Big(160.dp, Res.string.big),
    ;

    val wire: String get() = name.lowercase()

    companion object {
        /** An unknown or missing value reads as the phone's default (`Small`). */
        fun fromWire(value: String?): HomeItemSize = entries.firstOrNull { it.wire == value } ?: Small
    }
}
