package app.n_zik.compagnon.enums

/**
 * Port of the phone's `app/it/fast4x/rimusic/enums/AnimatedGradient.kt`: the animated-gradient background
 * styles of the player, in the phone's declaration order. [Random] (the phone's last value) resolves per
 * track to a random style of the phone's `Player.kt` 897-902 range — `M3EMorphingTheme`..`Stage`, never a
 * fluid gradient nor another `Random`.
 */
enum class AnimatedGradient {
    FluidThemeColorGradient,
    FluidCoverColorGradient,
    M3EMorphingTheme,
    M3EMorphingCover,
    Linear,
    Mesh,
    MesmerizingLens,
    GlossyGradients,
    GradientFlow,
    PurpleLiquid,
    InkFlow,
    OilFlow,
    IceReflection,
    Stage,
    GoldenMagma,
    BlackCherryCosmos,
    Random;
}
