package app.n_zik.compagnon.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Colour set of the N-Zik phone app (`ui/styling/ColorPalette.kt`), copied and trimmed for the
 * desktop companion: same roles and same values, so both apps look alike.
 */
@Immutable
data class ColorPalette(
    val background0: Color,
    val background1: Color,
    val background2: Color,
    val background3: Color,
    val background4: Color,
    val accent: Color,
    val onAccent: Color,
    val red: Color = Color(0xffbf4040),
    val blue: Color = Color(0xff4472cf),
    val text: Color,
    val textSecondary: Color,
    val textDisabled: Color,
    val isDark: Boolean,
)

/** N-Zik's default dark palette; the companion's default theme. */
val DefaultDarkColorPalette = ColorPalette(
    background0 = Color(0xff16171d),
    background1 = Color(0xff1f2029),
    background2 = Color(0xff2b2d3b),
    background3 = Color(0xff495057),
    background4 = Color(0xff333333),
    text = Color(0xffe1e1e2),
    textSecondary = Color(0xffa3a4a6),
    textDisabled = Color(0xff6f6f73),
    accent = Color(0xFF8B5CF6),
    onAccent = Color.White,
    isDark = true,
)
