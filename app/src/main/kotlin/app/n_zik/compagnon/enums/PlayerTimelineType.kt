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
    Default(Res.string._default),
    Wavy(Res.string.wavy_timeline),
    PinBar(Res.string.pin_bar),
    BodiedBar(Res.string.bodied_bar),
    AudioWaves(Res.string.fake_audio_bar),
    ThinBar(Res.string.thin_bar),
    ColoredBar(Res.string.colored_bar),
    VisualizerBar(Res.string.visualizer_bar);
}
