package app.n_zik.compagnon.components.player.background

import kotlin.math.round

/**
 * Port of `com.mikepenz.hypnoticcanvas` 0.4.1's `Shader` interface (the phone pins the library in its
 * `libs.versions.toml`): the SKSL source of an animated background, its speed modifier and its uniforms.
 * (HypnoticCanvas, MIT; each shader in `shaders/` carries its own header.)
 */
interface Shader {
    /** The name for this shader. */
    val name: String

    /** Contains the author name who created the shader. */
    val authorName: String

    /** Contains the url to the author reference. */
    val authorUrl: String

    /** Contains the url to the source of this shader. */
    val credit: String

    /** Contains the name of the license for this shader. */
    val license: String

    /** Contains the url to the license reference. */
    val licenseUrl: String

    /** Default time modifier for this shader */
    val speedModifier: Float
        get() = 0.5f

    /** Contains the sksl shader */
    val sksl: String

    /** Applies the uniforms required for this shader to the effect */
    fun applyUniforms(runtimeEffect: RuntimeEffect, time: Float, width: Float, height: Float) {
        runtimeEffect.setFloatUniform("uResolution", width, height, width / height)
        runtimeEffect.setFloatUniform("uTime", time)
    }
}

/** Port of the library's `utils.round`: the time stepped to [decimals] digits so the uniforms barely change. */
internal fun Float.roundDecimals(decimals: Int): Float {
    var multiplier = 1.0f
    repeat(decimals) { multiplier *= 10 }
    return round(this * multiplier) / multiplier
}
