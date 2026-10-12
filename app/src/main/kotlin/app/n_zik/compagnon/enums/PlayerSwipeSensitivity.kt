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
    Low(Res.string.ps_low, 150f),
    Medium(Res.string.ps_medium, 75f),
    High(Res.string.ps_high, 40f),
    VeryHigh(Res.string.ps_very_high, 15f);
}
