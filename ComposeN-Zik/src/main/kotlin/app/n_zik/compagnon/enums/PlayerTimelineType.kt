package app.n_zik.compagnon.enums

import app.n_zik.compagnon.generated.resources.*
import org.jetbrains.compose.resources.StringResource

/**
 * Port of the phone's `app/it/fast4x/rimusic/enums/PlayerTimelineType.kt`: the player's timeline styles,
 * in the phone's declaration order ([Wavy] is the phone's default — the one the PC renders today).
 * [textId] is the phone's settings-entry label of the style (the Timeline selector of the PC's
 * Player-appearance tab).
 */
enum class PlayerTimelineType(
    val textId: StringResource,
) {
    Default(Res.string.common_option_default),
    Wavy(Res.string.player_timeline_wavy_label),
    PinBar(Res.string.player_timeline_pin_bar_label),
    BodiedBar(Res.string.player_timeline_fat_bar_label),
    AudioWaves(Res.string.fake_audio_bar),
    ThinBar(Res.string.player_timeline_thin_bar_label),
    ColoredBar(Res.string.player_timeline_colored_bar_label),
    VisualizerBar(Res.string.visualizer_bar);
}
