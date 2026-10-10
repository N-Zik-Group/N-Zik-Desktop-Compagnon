package app.n_zik.compagnon.bridge.state

import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.serialization.Serializable

/**
 * The phone's UI settings seen by the PC (contract §10.5, since 1.10.0, feature `ui.settings`),
 * read from `GET /ui/settings`. The PC consumes only [topN] (the Top chip's effective cap): every
 * other field stays at its default, which is the phone's own default (the parity audit verified them
 * one by one) — the phone's appearance is the phone's own UI, the PC renders on its coded defaults.
 * A phone without the feature (or a failed read) keeps the PC on those defaults.
 * Enum values are the phone's enum names; the typed accessors map an unknown name to the default.
 * Since the "remove UI sync" spec the model no longer carries the wire's `playerBackgroundColors`,
 * `blurDarkenFactor`, `bottomGradient`, `maxTopPlaylistItems`, `menuStyle`, `disableScrollingText`
 * and `navigationBarPosition` — the phone still serves them, they are ignored at decode, and they
 * are re-added in phase 2.
 */
@Serializable
data class UiSettings(
    val colorPaletteName: String = "Dynamic",
    val colorPaletteMode: String = "Dark",
    val blurStrength: Float = 25f,
    val playerBackdrop: Float = 0f,
    val rotatingAlbumCover: Boolean = false,
    val showThumbnail: Boolean = true,
    val noBlur: Boolean = true,
    val iconLikeType: String = "Essential",
    val playerInfoShowIcons: Boolean = true,
    val showSkipTimeButtons: Boolean = true,
    val textOutline: Boolean = false,
    val transitionEffect: String = "Fade",
    /** The phone's effective "Top N"; `null` = unlimited. The only field the PC consumes. */
    val topN: Int? = 10,
    /** The phone's now-playing indicator (`MusicAnimationType` name); `Bubbles`, its default, when unknown. */
    val nowPlayingIndicator: String = "Bubbles",
) {
    /** The phone's `ColorPaletteMode`: `Light` and `System` (resolved to the desktop's theme by the caller) are light candidates. */
    val isLightMode: Boolean get() = colorPaletteMode == "Light"
    val isSystemMode: Boolean get() = colorPaletteMode == "System"
    val isPitchBlack: Boolean get() = colorPaletteMode == "PitchBlack"

    /**
     * The phone's `ColorPaletteName.PureBlack` / `ModernBlack`: their black player branches.
     * Decode-only since the "remove UI sync" spec: [toPcEffective] keeps the coded defaults, so this
     * never carries the phone's value in the PC's effective settings.
     */
    val isBlackPalette: Boolean get() = colorPaletteName == "PureBlack" || colorPaletteName == "ModernBlack"

    /**
     * The phone's `TransitionEffect`, unknown → `Fade` (its default). Decode-only since the "remove
     * UI sync" spec: [toPcEffective] keeps the coded defaults, so this never carries the phone's
     * value in the PC's effective settings.
     */
    val transition: TransitionEffect
        get() = TransitionEffect.entries.firstOrNull { it.name == transitionEffect } ?: TransitionEffect.Fade

    /**
     * The phone's `IconLikeType`, unknown → `Essential` (its default). Decode-only since the "remove
     * UI sync" spec: [toPcEffective] keeps the coded defaults, so this never carries the phone's
     * value in the PC's effective settings.
     */
    val likeIcon: IconLikeType
        get() = IconLikeType.entries.firstOrNull { it.name == iconLikeType } ?: IconLikeType.Essential

    /**
     * The PC's effective settings of a wire read (spec `spec-remove-ui-sync`): only [topN] is
     * consumed — a `null` (the phone's unlimited) stays `null`; every other field is reset to its
     * (the phone's) default, so the wire's appearance never reaches the PC's UI.
     */
    fun toPcEffective(): UiSettings = UiSettings(topN = topN)
}

/** The phone's `TransitionEffect` (`AppNavigation.kt`). */
enum class TransitionEffect { SlideVertical, SlideHorizontal, Scale, Fade, Expand, None }

/** The phone's `IconLikeType` (`IconsLikeType.kt`), in its order. */
enum class IconLikeType { Apple, Breaked, Brilliant, Essential, Gift, Shape, Striped }

/** The phone's `PlayerBackgroundColors`, in its order. */
enum class PlayerBackgroundColors {
    CoverColor, ThemeColor, CoverColorGradient, ThemeColorGradient, BlurredCoverColor, ColorPalette, AnimatedGradient,
}

/** The PC's effective UI settings seen by every screen: its coded defaults, the phone's [UiSettings.topN] once read. */
val LocalUiSettings = staticCompositionLocalOf { UiSettings() }
