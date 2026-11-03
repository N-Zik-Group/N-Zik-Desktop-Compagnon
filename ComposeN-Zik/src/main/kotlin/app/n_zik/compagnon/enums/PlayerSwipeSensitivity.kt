package app.n_zik.compagnon.enums

import app.n_zik.compagnon.generated.resources.*
import org.jetbrains.compose.resources.StringResource

/**
 * Port of the phone's `app/n_zik/android/enums/PlayerSwipeSensitivity.kt`: the horizontal swipe
 * sensitivity on the player's cover, the accumulated drag distance (px) before the action fires —
 * the phone's `Player.kt` 1218-1241: right = previous, left = next. [textId] is the phone's
 * settings-entry label of the level (the player-swipe-sensitivity selector of the PC's
 * Player-appearance tab).
 */
enum class PlayerSwipeSensitivity(
    val textId: StringResource,
    val threshold: Float,
) {
    Low(Res.string.player_swipe_sensitivity_low_label, 150f),
    Medium(Res.string.player_swipe_sensitivity_medium_label, 75f),
    High(Res.string.player_swipe_sensitivity_high_label, 40f),
    VeryHigh(Res.string.player_swipe_sensitivity_very_high_label, 15f);
}
