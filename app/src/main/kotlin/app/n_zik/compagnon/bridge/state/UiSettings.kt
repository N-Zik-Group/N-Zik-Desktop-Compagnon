package app.n_zik.compagnon.bridge.state

import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.serialization.Serializable

/**
 * The phone's UI settings the PC mirrors (contract §10.5, since 1.10.0, feature `ui.settings`),
 * read from `GET /ui/settings`. Every field defaults to the phone's own default, so a phone without
 * the feature (or a failed read) keeps the PC on the phone's defaults — its behaviour before 1.10.0.
 * Enum values are the phone's enum names; the typed accessors map an unknown name to the default.
 */
@Serializable
data class UiSettings(
    val colorPaletteName: String = "Dynamic",
    val colorPaletteMode: String = "Dark",
    val playerBackgroundColors: String = "AnimatedGradient",
    val blurStrength: Float = 25f,
    val blurDarkenFactor: Float = 0.2f,
    val playerBackdrop: Float = 0f,
    val rotatingAlbumCover: Boolean = false,
    val showThumbnail: Boolean = true,
    val noBlur: Boolean = true,
    val bottomGradient: Boolean = false,
    val iconLikeType: String = "Essential",
    val playerInfoShowIcons: Boolean = true,
    val showSkipTimeButtons: Boolean = true,
    val textOutline: Boolean = false,
    val transitionEffect: String = "Fade",
    val maxTopPlaylistItems: String = "10",
    /** The phone's effective "Top N"; `null` = unlimited. */
    val topN: Int? = 10,
    val menuStyle: String = "List",
    val disableScrollingText: Boolean = false,
    val navigationBarPosition: String = "BottomFloating",
    /** The phone's now-playing indicator (`MusicAnimationType` name); `Bubbles`, its default, when unknown. */
    val nowPlayingIndicator: String = "Bubbles",
) {
    /** The phone's `ColorPaletteMode`: `Light` and `System` (resolved to the desktop's theme by the caller) are light candidates. */
    val isLightMode: Boolean get() = colorPaletteMode == "Light"
    val isSystemMode: Boolean get() = colorPaletteMode == "System"
    val isPitchBlack: Boolean get() = colorPaletteMode == "PitchBlack"

    /** The phone's `ColorPaletteName.PureBlack` / `ModernBlack`: their black player branches. */
    val isBlackPalette: Boolean get() = colorPaletteName == "PureBlack" || colorPaletteName == "ModernBlack"

    /** `NavigationBarPosition.BottomFloating`: the toasts and the mini-player shadow depend on it. */
    val isFloatingNavigationBar: Boolean get() = navigationBarPosition == "BottomFloating"

    /** The phone's `TransitionEffect`, unknown → `Fade` (its default). */
    val transition: TransitionEffect
        get() = TransitionEffect.entries.firstOrNull { it.name == transitionEffect } ?: TransitionEffect.Fade

    /** The phone's `IconLikeType`, unknown → `Essential` (its default). */
    val likeIcon: IconLikeType
        get() = IconLikeType.entries.firstOrNull { it.name == iconLikeType } ?: IconLikeType.Essential

    /** The phone's `PlayerBackgroundColors`, unknown → `AnimatedGradient` (its default). */
    val playerBackground: PlayerBackgroundColors
        get() = PlayerBackgroundColors.entries.firstOrNull { it.name == playerBackgroundColors } ?: PlayerBackgroundColors.AnimatedGradient

    /** `MenuStyle.Grid` (the PC draws its list menus: the grid style is deferred). */
    val isGridMenu: Boolean get() = menuStyle == "Grid"
}

/** The phone's `TransitionEffect` (`AppNavigation.kt`). */
enum class TransitionEffect { SlideVertical, SlideHorizontal, Scale, Fade, Expand, None }

/** The phone's `IconLikeType` (`IconsLikeType.kt`), in its order. */
enum class IconLikeType { Apple, Breaked, Brilliant, Essential, Gift, Shape, Striped }

/** The phone's `PlayerBackgroundColors`, in its order. */
enum class PlayerBackgroundColors {
    CoverColor, ThemeColor, CoverColorGradient, ThemeColorGradient, BlurredCoverColor, ColorPalette, AnimatedGradient,
}

/** The phone's UI settings seen by every screen; the phone's defaults until the first read. */
val LocalUiSettings = staticCompositionLocalOf { UiSettings() }

/** Whether [LocalUiSettings] holds a value actually read from the phone (not its defaults). */
val LocalUiSettingsRead = staticCompositionLocalOf { false }
