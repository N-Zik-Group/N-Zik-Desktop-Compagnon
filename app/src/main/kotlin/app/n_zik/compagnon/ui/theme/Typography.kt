package app.n_zik.compagnon.ui.theme

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

/** Text scale of the N-Zik phone app (`ui/styling/Typography.kt`): same sizes, Rubik font. */
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
)

/** The Rubik family bundled with the app (same files as the phone). */
@Composable
fun rubikFontFamily(): FontFamily = FontFamily(
    Font(Res.font.rubik_w300, FontWeight.Light),
    Font(Res.font.rubik_w400, FontWeight.Normal),
    Font(Res.font.rubik_w500, FontWeight.Medium),
    Font(Res.font.rubik_w600, FontWeight.SemiBold),
    Font(Res.font.rubik_w700, FontWeight.Bold),
)

fun typographyOf(color: Color, fontFamily: FontFamily): Typography {
    val textStyle = TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.Normal, color = color)
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
    )
}

val TextStyle.medium: TextStyle get() = copy(fontWeight = FontWeight.Medium)
val TextStyle.semiBold: TextStyle get() = copy(fontWeight = FontWeight.SemiBold)
val TextStyle.bold: TextStyle get() = copy(fontWeight = FontWeight.Bold)
