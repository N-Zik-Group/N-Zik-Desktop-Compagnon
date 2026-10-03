package app.n_zik.compagnon.components.theme

import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp

/**
 * Port of the phone's `app/it/fast4x/rimusic/ui/styling/Appearance.kt` (17-43, 83). Dropped: the `Saver`
 * (no UI state is saved across restarts on the PC). The accessors (`colorPalette()`, `thumbnailShape()`…)
 * are in `GlobalVars.kt`, as on the phone.
 */
class BoundedCornerSize(val dp: Dp, val maxFraction: Float) : CornerSize {
    override fun toPx(shapeSize: Size, density: Density): Float {
        val requestedPx = with(density) { dp.toPx() }
        val maxPx = shapeSize.minDimension * maxFraction
        return kotlin.math.min(requestedPx, maxPx)
    }
}

data class Appearance(
    val colorPalette: ColorPalette,
    val typography: Typography,
    val thumbnailShape: Shape,
    val uiRoundnessShape: Shape,
    val artistThumbnailShape: Shape,
)

val LocalAppearance = staticCompositionLocalOf<Appearance> { error("No Appearance provided") }
