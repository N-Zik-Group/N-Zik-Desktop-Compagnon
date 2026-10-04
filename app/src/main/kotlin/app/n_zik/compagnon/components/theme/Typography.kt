package app.n_zik.compagnon.components.theme

import app.n_zik.compagnon.generated.resources.roboto_w500
import app.n_zik.compagnon.generated.resources.roboto_w400
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.n_zik.compagnon.generated.resources.Res
import app.n_zik.compagnon.generated.resources.rubik_w300
import app.n_zik.compagnon.generated.resources.rubik_w400
import app.n_zik.compagnon.generated.resources.rubik_w500
import app.n_zik.compagnon.generated.resources.rubik_w600
import app.n_zik.compagnon.generated.resources.rubik_w700
import org.jetbrains.compose.resources.Font

/**
 * Port of the phone's `app/it/fast4x/rimusic/ui/styling/Typography.kt` (16-120), with its default
 * preferences: Rubik (`FontType.Rubik`), no system font, no font padding.
 * Dropped: the `Saver` (no UI state is saved across restarts), the Poppins font and the system font
 * (settings the Compagnon does not have), and `PlatformTextStyle(includeFontPadding = false)`: desktop
 * text has no font padding (the option does not exist there), which is what `false` gives on the phone.
 */
@Immutable
data class Typography(
    val xxxs: TextStyle,
    val xxs: TextStyle,
    val xs: TextStyle,
    val s: TextStyle,
    val m: TextStyle,
    val l: TextStyle,
    val xl: TextStyle,
    val xxl: TextStyle,
    val xxxl: TextStyle,
    val xlxl: TextStyle,
) {
    fun copy(color: Color) = Typography(
        xxxs = xxs.copy(color = color),
        xxs = xxs.copy(color = color),
        xs = xs.copy(color = color),
        s = s.copy(color = color),
        m = m.copy(color = color),
        l = l.copy(color = color),
        xl = xl.copy(color = color),
        xxl = xxl.copy(color = color),
        xxxl = xxxl.copy(color = color),
        xlxl = xlxl.copy(color = color),
    )
}

/** The Rubik family bundled with the app (same files as the phone's `R.font.rubik_w*`). */
@Composable
fun rubikFontFamily(): FontFamily = FontFamily(
    Font(Res.font.rubik_w300, FontWeight.Light),
    Font(Res.font.rubik_w400, FontWeight.Normal),
    Font(Res.font.rubik_w500, FontWeight.Medium),
    Font(Res.font.rubik_w600, FontWeight.SemiBold),
    Font(Res.font.rubik_w700, FontWeight.Bold),
)

/**
 * Roboto, the font Android gives the Material components the phone draws without its own style (menus,
 * dialogs, buttons…): regular and medium, the weights the Material 3 type scale uses. Bundled with the app
 * (Apache 2.0, see the README); not a dependency.
 */
@Composable
fun robotoFontFamily(): FontFamily = FontFamily(
    Font(Res.font.roboto_w400, FontWeight.Normal),
    Font(Res.font.roboto_w500, FontWeight.Medium),
)

/** The Material 3 default type scale with [fontFamily] as its family: the phone's Roboto default. */
fun materialTypographyOf(fontFamily: FontFamily): androidx.compose.material3.Typography {
    val base = androidx.compose.material3.Typography()
    return base.copy(
        displayLarge = base.displayLarge.copy(fontFamily = fontFamily),
        displayMedium = base.displayMedium.copy(fontFamily = fontFamily),
        displaySmall = base.displaySmall.copy(fontFamily = fontFamily),
        headlineLarge = base.headlineLarge.copy(fontFamily = fontFamily),
        headlineMedium = base.headlineMedium.copy(fontFamily = fontFamily),
        headlineSmall = base.headlineSmall.copy(fontFamily = fontFamily),
        titleLarge = base.titleLarge.copy(fontFamily = fontFamily),
        titleMedium = base.titleMedium.copy(fontFamily = fontFamily),
        titleSmall = base.titleSmall.copy(fontFamily = fontFamily),
        bodyLarge = base.bodyLarge.copy(fontFamily = fontFamily),
        bodyMedium = base.bodyMedium.copy(fontFamily = fontFamily),
        bodySmall = base.bodySmall.copy(fontFamily = fontFamily),
        labelLarge = base.labelLarge.copy(fontFamily = fontFamily),
        labelMedium = base.labelMedium.copy(fontFamily = fontFamily),
        labelSmall = base.labelSmall.copy(fontFamily = fontFamily),
    )
}

fun typographyOf(color: Color, fontFamily: FontFamily): Typography {
    val textStyle = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Normal,
        color = color,
    )

    return Typography(
        xxxs = textStyle.copy(fontSize = 10.sp),
        xxs = textStyle.copy(fontSize = 12.sp),
        xs = textStyle.copy(fontSize = 14.sp),
        s = textStyle.copy(fontSize = 16.sp),
        m = textStyle.copy(fontSize = 18.sp),
        l = textStyle.copy(fontSize = 20.sp),
        xl = textStyle.copy(fontSize = 24.sp),
        xxl = textStyle.copy(fontSize = 28.sp),
        xxxl = textStyle.copy(fontSize = 36.sp),
        xlxl = textStyle.copy(fontSize = 34.sp),
    )
}
