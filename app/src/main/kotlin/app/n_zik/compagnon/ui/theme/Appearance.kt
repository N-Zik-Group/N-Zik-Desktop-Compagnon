package app.n_zik.compagnon.ui.theme

import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Look of the app, provided once at the root (same idea as the phone's `Appearance`). */
@Immutable
data class Appearance(
    val colorPalette: ColorPalette,
    val typography: Typography,
    val uiRoundnessShape: Shape,
)

val LocalAppearance = staticCompositionLocalOf<Appearance> { error("No Appearance provided") }

@Composable
@ReadOnlyComposable
fun colorPalette(): ColorPalette = LocalAppearance.current.colorPalette

@Composable
@ReadOnlyComposable
fun typography(): Typography = LocalAppearance.current.typography

@Composable
@ReadOnlyComposable
fun uiRoundnessShape(): Shape = LocalAppearance.current.uiRoundnessShape

/** The phone's default UI roundness: 25 dp, never more than 40 % of the shorter side. */
private const val UI_ROUNDNESS_DP = 25f
private const val UI_ROUNDNESS_MAX_FRACTION = 0.4f

/** Corner of [radius], capped at [maxFraction] of the shorter side (the phone's `BoundedCornerSize`). */
private class BoundedCornerSize(private val radius: Dp, private val maxFraction: Float) : CornerSize {
    override fun toPx(shapeSize: Size, density: Density): Float =
        minOf(with(density) { radius.toPx() }, shapeSize.minDimension * maxFraction)
}

/** Root theme: N-Zik dark palette, Rubik typography and rounded shapes, mirrored into Material 3. */
@Composable
fun NZikTheme(content: @Composable () -> Unit) {
    val palette = DefaultDarkColorPalette
    val fontFamily = rubikFontFamily()
    val appearance = remember(fontFamily) {
        Appearance(
            colorPalette = palette,
            typography = typographyOf(palette.text, fontFamily),
            uiRoundnessShape = RoundedCornerShape(BoundedCornerSize(UI_ROUNDNESS_DP.dp, UI_ROUNDNESS_MAX_FRACTION)),
        )
    }
    val colorScheme = darkColorScheme(
        primary = palette.accent,
        onPrimary = palette.onAccent,
        secondary = palette.accent,
        onSecondary = palette.onAccent,
        background = palette.background0,
        onBackground = palette.text,
        surface = palette.background1,
        onSurface = palette.text,
        surfaceVariant = palette.background2,
        onSurfaceVariant = palette.textSecondary,
        outline = palette.background3,
        error = palette.red,
    )
    CompositionLocalProvider(LocalAppearance provides appearance) {
        MaterialTheme(colorScheme = colorScheme, content = content)
    }
}
