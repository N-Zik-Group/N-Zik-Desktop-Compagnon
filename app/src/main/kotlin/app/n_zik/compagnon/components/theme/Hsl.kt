package app.n_zik.compagnon.components.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import app.n_zik.compagnon.core.palette.ColorUtils

/**
 * Port of the phone's `app/it/fast4x/rimusic/ui/styling/Hsl.kt`. Dropped: the `Saver` (the Compagnon
 * saves no UI state across restarts).
 */
@Suppress("NOTHING_TO_INLINE")
@JvmInline
value class Hsl(@PublishedApi internal val raw: FloatArray) {

    init {
        assert(raw.size == 3) { "Invalid Hsl value! Expected size: 3, actual size: ${raw.size}" }
    }

    inline val hue get() = raw[0]
    inline val saturation get() = raw[1]
    inline val lightness get() = raw[2]

    inline val color
        get() = Color.hsl(
            hue = hue,
            saturation = saturation,
            lightness = lightness,
        )

    inline operator fun component1() = hue
    inline operator fun component2() = saturation
    inline operator fun component3() = lightness
}

val FloatArray.hsl get() = Hsl(raw = this)
val Color.hsl: Hsl
    get() {
        val color = this
        return FloatArray(3)
            .apply { ColorUtils.colorToHSL(color.toArgb(), this) }
            .hsl
    }
