package app.n_zik.compagnon.enums

/**
 * Port of the phone's `app/n_zik/android/enums/PlayerSwipeSensitivity.kt`: the horizontal swipe
 * sensitivity on the player's cover, the accumulated drag distance (px) before the action fires —
 * the phone's `Player.kt` 1218-1241: right = previous, left = next.
 */
enum class PlayerSwipeSensitivity(
    val threshold: Float,
) {
    Low(150f),
    Medium(75f),
    High(40f),
    VeryHigh(15f);
}
