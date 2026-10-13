package app.n_zik.compagnon.components.player

import androidx.compose.ui.graphics.Color
import app.n_zik.compagnon.components.theme.ColorPalette

/*
 * Port of the phone's `app/n_zik/android/components/player/CoverContrastColors.kt` (the helpers the
 * ported player uses). Dropped: `m3eRecapRestoredDynamicPalette` (no palette is restored across restarts).
 */

/**
 * The outline (halo) color for the player's title/artist text: white at [alpha] on a light-tone ramp,
 * black at [alpha] on a dark-tone one, keyed on the effective tone of the palette.
 */
internal fun textOutlineColor(effectiveIsDark: Boolean, alpha: Float): Color =
    if (effectiveIsDark) Color.Black.copy(alpha = alpha) else Color.White.copy(alpha = alpha)

/** The transport color for the `Monochrome` player controls option (the phone's default): the palette's text. */
internal fun monochromeControlsColor(palette: ColorPalette): Color = palette.text

/**
 * The base color for the dimmed title/artist placeholder `compositeOver`: white on a dark-tone ramp,
 * black on a light-tone one.
 */
internal fun placeholderCompositeBase(effectiveIsDark: Boolean): Color =
    if (effectiveIsDark) Color.White else Color.Black

/** The duration indicator's outline: transparent without the text outline option (its default). */
internal fun durationOutlineColorOf(textOutline: Boolean, palette: ColorPalette): Color =
    if (!textOutline) Color.Transparent else textOutlineColor(palette.isDark, 0.5f)
