package app.n_zik.compagnon.components.player

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import app.n_zik.compagnon.components.theme.ColorPalette
import app.n_zik.compagnon.components.theme.dynamicColorPaletteOf
import app.n_zik.compagnon.core.palette.ColorUtils.colorToHSL
import app.n_zik.compagnon.core.palette.Palette
import app.n_zik.compagnon.core.palette.PaletteBitmap
import app.n_zik.compagnon.core.palette.from

/*
 * Port of the phone's `app/n_zik/android/components/player/CoverPaletteExtractor.kt` (the cover-colour
 * extraction of the player, the mini-player and the app's dynamic theme) and of `computePlayerDynamicPalette`
 * (`app/it/fast4x/rimusic/ui/screens/player/Player.kt` 321-380). The bitmap is the cover's pixels
 * ([PaletteBitmap]), read through the same `Palette` algorithm (`app.n_zik.compagnon.core.palette`).
 * Dropped (used by features the Compagnon does not have): `lyricsThemeColor`, `m3eCoverBackgroundColor`
 * and `m3eCoverForegroundArgb` (lyrics, visualizer, the non-default "cover color" backgrounds).
 */

/** The 7 raw swatches of an album cover extracted from the capped [Palette] (`maximumColorCount(8)`). ARGB ints. */
data class M3ECoverColors(
    val dominant: Int,
    val vibrant: Int,
    val lightVibrant: Int,
    val darkVibrant: Int,
    val muted: Int,
    val lightMuted: Int,
    val darkMuted: Int,
)

/** Maximum channel spread below which a cover is treated as nearly achromatic. */
const val ACHROMATIC_CHANNEL_DELTA_THRESHOLD = 0.10f

/** The lightness ceiling of the lightest background of an achromatic ramp rendered in a dark theme. */
const val ACHROMATIC_RAMP_LIGHT_TONE_MAX_LIGHTNESS = 0.80f

/** The lightness floor of the darkest background of an achromatic ramp rendered in a light theme. */
const val ACHROMATIC_RAMP_DARK_TONE_MIN_LIGHTNESS = 0.30f

/** The normalized channel spread (max − min of the R/G/B channels) of an ARGB color, in [0, 1]. */
internal fun channelDelta(rgb: Int): Float {
    val r = (rgb shr 16) and 0xFF
    val g = (rgb shr 8) and 0xFF
    val b = rgb and 0xFF
    return (maxOf(r, g, b) - minOf(r, g, b)) / 255f
}

/**
 * Extracts the 7 M3E morphing cover swatches from [bitmap]: `maximumColorCount(8)`, each `get*Color`
 * falling back to the dynamic palette's accent; `null` exactly when `dynamicColorPaletteOf` finds no
 * dominant swatch. Nearly achromatic covers are neutralized ([m3eNeutralizeIfAchromatic]).
 */
fun extractM3ECoverColors(bitmap: PaletteBitmap, isDark: Boolean): M3ECoverColors? {
    val palette = dynamicColorPaletteOf(bitmap, isDark) ?: return null
    val swatchPalette = Palette.from(bitmap).maximumColorCount(8).generate()
    val fallback = palette.accent.toArgb()
    return M3ECoverColors(
        dominant = swatchPalette.getDominantColor(fallback),
        vibrant = swatchPalette.getVibrantColor(fallback),
        lightVibrant = swatchPalette.getLightVibrantColor(fallback),
        darkVibrant = swatchPalette.getDarkVibrantColor(fallback),
        muted = swatchPalette.getMutedColor(fallback),
        lightMuted = swatchPalette.getLightMutedColor(fallback),
        darkMuted = swatchPalette.getDarkMutedColor(fallback),
    ).m3eNeutralizeIfAchromatic(swatchPalette, fallback)
}

/**
 * When the maximum channel spread of [swatchPalette]'s swatches is below
 * [ACHROMATIC_CHANNEL_DELTA_THRESHOLD], every swatch becomes a neutral gray (its own lightness, or the
 * dominant's for a swatch that fell back to [fallbackArgb]); otherwise unchanged.
 */
internal fun M3ECoverColors.m3eNeutralizeIfAchromatic(
    swatchPalette: Palette,
    fallbackArgb: Int,
): M3ECoverColors {
    if (swatchPalette.swatches.maxOf { channelDelta(it.rgb) } >= ACHROMATIC_CHANNEL_DELTA_THRESHOLD) {
        return this
    }

    val dominantHsl = FloatArray(3)
    colorToHSL(dominant, dominantHsl)
    val dominantLuminance = dominantHsl[2]

    fun neutralize(rgb: Int): Int {
        val lightness = if (rgb == fallbackArgb) {
            dominantLuminance
        } else {
            val hsl = FloatArray(3)
            colorToHSL(rgb, hsl)
            hsl[2]
        }
        return Color.hsl(0f, 0f, lightness).toArgb()
    }

    return copy(
        dominant = neutralize(dominant),
        vibrant = neutralize(vibrant),
        lightVibrant = neutralize(lightVibrant),
        darkVibrant = neutralize(darkVibrant),
        muted = neutralize(muted),
        lightMuted = neutralize(lightMuted),
        darkMuted = neutralize(darkMuted),
    )
}

/** Whether every swatch is neutral (channel spread below [ACHROMATIC_CHANNEL_DELTA_THRESHOLD]). */
val M3ECoverColors.allAchromatic: Boolean
    get() = listOf(dominant, vibrant, lightVibrant, darkVibrant, muted, lightMuted, darkMuted)
        .all { channelDelta(it) < ACHROMATIC_CHANNEL_DELTA_THRESHOLD }

/**
 * The dynamic [ColorPalette] of the mini-player, the app-wide dynamic theme and the player's local
 * palette, built from the **dominant** swatch; `null` when the bitmap yields no dominant swatch.
 */
fun m3eDynamicColorPaletteOf(bitmap: PaletteBitmap, isDark: Boolean): ColorPalette? =
    extractM3ECoverColors(bitmap, isDark)?.let { m3eDominantDynamicPaletteOf(it, bitmap, isDark) }

/**
 * Achromatic (neutralized) covers get the neutral ramp from the neutralized dominant (tone from its
 * lightness), capped / floored by [m3eCapAchromaticBackgrounds]; colored covers delegate to
 * `dynamicColorPaletteOf(bitmap, isDark)`.
 */
internal fun m3eDominantDynamicPaletteOf(
    colors: M3ECoverColors,
    bitmap: PaletteBitmap,
    isDark: Boolean,
): ColorPalette {
    if (colors.allAchromatic) {
        val dominantHsl = FloatArray(3)
        colorToHSL(colors.dominant, dominantHsl)
        val toneIsDark = dominantHsl[2] < 0.5f
        return dynamicColorPaletteOf(dominantHsl, toneIsDark)
            .m3eCapAchromaticBackgrounds(themeIsDark = isDark, toneIsDark = toneIsDark)
    }
    return requireNotNull(dynamicColorPaletteOf(bitmap, isDark)) {
        "dominant swatch disappeared between extraction and palette build"
    }
}

/**
 * Caps (light tone in a dark theme) / floors (dark tone in a light theme) the five background
 * lightnesses of an achromatic dynamic ramp when the ramp's tone mismatches the theme; the same
 * instance otherwise.
 */
internal fun ColorPalette.m3eCapAchromaticBackgrounds(
    themeIsDark: Boolean,
    toneIsDark: Boolean,
): ColorPalette {
    val lightnesses = listOf(background0, background1, background2, background3, background4)
        .map { background ->
            val hsl = FloatArray(3)
            colorToHSL(background.toArgb(), hsl)
            hsl[2]
        }
    val delta = when {
        themeIsDark && !toneIsDark ->
            lightnesses.max() - ACHROMATIC_RAMP_LIGHT_TONE_MAX_LIGHTNESS
        !themeIsDark && toneIsDark ->
            ACHROMATIC_RAMP_DARK_TONE_MIN_LIGHTNESS - lightnesses.min()
        else -> 0f
    }
    if (delta <= 0f) return this

    fun shift(background: Color): Color {
        val hsl = FloatArray(3)
        colorToHSL(background.toArgb(), hsl)
        hsl[2] = (hsl[2] + if (toneIsDark) delta else -delta).coerceIn(0f, 1f)
        return Color.hsl(hsl[0], hsl[1], hsl[2])
    }

    return copy(
        background0 = shift(background0),
        background1 = shift(background1),
        background2 = shift(background2),
        background3 = shift(background3),
        background4 = shift(background4),
    )
}

/**
 * Pure copy of `Player.saturate()`: +0.35 saturation in a dark theme when the input saturation is at
 * least 0.1, lightness forced to at least 0.5 in a light theme.
 */
fun m3eSaturate(color: Int, lightTheme: Boolean): Color {
    val hsl = FloatArray(3)
    colorToHSL(color, hsl)
    hsl[1] = (hsl[1] + if (lightTheme || hsl[1] < 0.1f) 0f else 0.35f).coerceIn(0f, 1f)
    hsl[2] = if (lightTheme) hsl[2].coerceIn(0.5f, 1f) else hsl[2]
    return Color.hsl(hsl[0], hsl[1], hsl[2])
}

/** Pure copy of `Player.Color.darkenBy()`: RGB × 0.5 in a dark theme, untouched in a light theme. */
fun Color.m3eDarkenBy(lightTheme: Boolean): Color {
    val ratio = if (lightTheme) 1f else 0.5f
    return copy(
        red = red * ratio,
        green = green * ratio,
        blue = blue * ratio,
        alpha = alpha,
    )
}

/** Port of `PlayerDynamicPaletteResult` (`Player.kt` 321): the player's local palette and its 7 swatches. */
internal data class PlayerDynamicPaletteResult(
    val palette: ColorPalette,
    val dominant: Int,
    val vibrant: Int,
    val lightVibrant: Int,
    val darkVibrant: Int,
    val muted: Int,
    val lightMuted: Int,
    val darkMuted: Int,
)

/** Port of `computePlayerDynamicPalette` (`Player.kt` 349). */
internal fun computePlayerDynamicPalette(
    bitmap: PaletteBitmap,
    isDark: Boolean,
    fallbackColor: ColorPalette,
): PlayerDynamicPaletteResult {
    val colors = extractM3ECoverColors(bitmap, isDark)
    if (colors == null) {
        val accent = fallbackColor.accent.toArgb()
        return PlayerDynamicPaletteResult(
            palette = fallbackColor,
            dominant = accent,
            vibrant = accent,
            lightVibrant = accent,
            darkVibrant = accent,
            muted = accent,
            lightMuted = accent,
            darkMuted = accent,
        )
    }
    return PlayerDynamicPaletteResult(
        palette = m3eDominantDynamicPaletteOf(colors, bitmap, isDark),
        dominant = colors.dominant,
        vibrant = colors.vibrant,
        lightVibrant = colors.lightVibrant,
        darkVibrant = colors.darkVibrant,
        muted = colors.muted,
        lightMuted = colors.lightMuted,
        darkMuted = colors.darkMuted,
    )
}
