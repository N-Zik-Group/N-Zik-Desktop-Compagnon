package app.n_zik.compagnon.enums

import app.n_zik.compagnon.bridge.state.RepeatMode
import app.n_zik.compagnon.generated.resources.Res
import app.n_zik.compagnon.generated.resources.infinite
import app.n_zik.compagnon.generated.resources.repeat
import app.n_zik.compagnon.generated.resources.repeatone
import org.jetbrains.compose.resources.DrawableResource

/**
 * Port of the phone's `app/it/fast4x/rimusic/enums/QueueLoopType.kt`: the repeat mode of the player with its
 * icon. On the phone it maps media3's `REPEAT_MODE_*`; here the contract's [RepeatMode] (§1.1), the phone
 * staying the only owner of the mode (`player/repeat`).
 */
enum class QueueLoopType(
    val type: RepeatMode,
    val iconId: DrawableResource,
) {
    Default(RepeatMode.Off, Res.drawable.repeat),
    RepeatOne(RepeatMode.One, Res.drawable.repeatone),
    RepeatAll(RepeatMode.All, Res.drawable.infinite);

    /** Goes through the values from top to bottom, then back to the first one. */
    fun next(): QueueLoopType = when (this) {
        Default -> RepeatOne
        RepeatOne -> RepeatAll
        RepeatAll -> Default
    }

    companion object {
        fun from(value: RepeatMode): QueueLoopType = when (value) {
            RepeatMode.Off -> Default
            RepeatMode.One -> RepeatOne
            RepeatMode.All -> RepeatAll
        }
    }
}
