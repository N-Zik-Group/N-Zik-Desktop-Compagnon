package app.n_zik.compagnon.enums

import app.n_zik.compagnon.generated.resources.*
import org.jetbrains.compose.resources.StringResource

/**
 * Port of the phone's `app/it/fast4x/rimusic/enums/AnimatedGradient.kt`: the animated-gradient background
 * styles of the player, in the phone's declaration order. [Random] (the phone's last value) resolves per
 * track to a random style of the phone's `Player.kt` 897-902 range — `M3EMorphingTheme`..`Stage`, never a
 * fluid gradient nor another `Random`. [textId] is the phone's settings-entry label of the style
 * (the Gradient selector of the PC's Player-appearance tab).
 */
enum class AnimatedGradient(
    val textId: StringResource,
) {
    FluidThemeColorGradient(Res.string.bg_colors_fluid_gradient_background_from_theme),
    FluidCoverColorGradient(Res.string.bg_colors_fluid_gradient_background_from_cover),
    M3EMorphingTheme(Res.string.bg_colors_m3e_morphing_theme),
    M3EMorphingCover(Res.string.bg_colors_m3e_morphing_cover),
    Linear(Res.string.linear),
    Mesh(Res.string.mesh),
    MesmerizingLens(Res.string.mesmerizinglens),
    GlossyGradients(Res.string.glossygradient),
    GradientFlow(Res.string.gradientflow),
    PurpleLiquid(Res.string.purpleliquid),
    InkFlow(Res.string.inkflow),
    OilFlow(Res.string.oilflow),
    IceReflection(Res.string.icereflection),
    Stage(Res.string.stage),
    GoldenMagma(Res.string.goldenmagma),
    BlackCherryCosmos(Res.string.blackcherrycosmos),
    Random(Res.string.random);
}
