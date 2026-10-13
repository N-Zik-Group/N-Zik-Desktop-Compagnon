package app.n_zik.compagnon.components.tab.toolbar

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import app.n_zik.compagnon.colorPalette

/**
 * Port of the phone's `app/it/fast4x/rimusic/ui/components/tab/toolbar/DynamicColor.kt`: an icon drawn with
 * its [color] or [secondColor] (by default `textDisabled`) depending on [isFirstColor].
 */
interface DynamicColor : Icon {

    val secondColor: Color
        @Composable
        get() = colorPalette().textDisabled

    var isFirstColor: Boolean

    override val color: Color
        @Composable
        get() = if (isFirstColor) super.color else secondColor
}
