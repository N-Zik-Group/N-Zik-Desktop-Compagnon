package app.n_zik.compagnon.enums

/**
 * Port of the phone's `app/it/fast4x/rimusic/enums/PlayerPlayButtonType.kt` (the sizes of the player's play
 * button). The Compagnon has no player settings: the phone's default, [CircularRibbed], is used. The labels
 * (`textId`) are dropped with the setting.
 */
enum class PlayerPlayButtonType(
    val height: Int,
    val width: Int,
) {
    Disabled(60, 60),
    Default(60, 60),
    Rectangular(70, 110),
    CircularRibbed(100, 100),
    Square(80, 80),
}
