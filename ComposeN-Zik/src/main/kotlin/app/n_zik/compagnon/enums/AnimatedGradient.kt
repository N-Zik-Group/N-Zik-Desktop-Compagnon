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
    FluidThemeColorGradient(Res.string.player_background_theme_fluid_gradient_label),
    FluidCoverColorGradient(Res.string.player_background_artwork_fluid_gradient_label),
    M3EMorphingTheme(Res.string.player_background_theme_m3e_morphing_label),
    M3EMorphingCover(Res.string.player_background_artwork_m3e_morphing_label),
    Linear(Res.string.player_background_linear_gradient),
    Mesh(Res.string.player_background_mesh_gradient),
    MesmerizingLens(Res.string.player_background_mesmerizing_lens),
    GlossyGradients(Res.string.player_background_glossy_gradient),
    GradientFlow(Res.string.player_background_gradient_flow),
    PurpleLiquid(Res.string.player_background_purple_liquid),
    InkFlow(Res.string.player_background_ink_flow),
    OilFlow(Res.string.player_background_oil_flow),
    IceReflection(Res.string.player_background_ice_reflection),
    Stage(Res.string.player_background_stage),
    GoldenMagma(Res.string.player_background_golden_magma),
    BlackCherryCosmos(Res.string.player_background_black_cherry_cosmos),
    Random(Res.string.common_option_random);
}
