package app.n_zik.compagnon.components.player.background

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asComposeShader
import org.jetbrains.skia.RuntimeShaderBuilder

/*
 * Port of `com.mikepenz.hypnoticcanvas` 0.4.1's `RuntimeEffect` (the phone pins the library in its
 * `libs.versions.toml`, its `AnimatedGradient` background styles are built on it), merged in one file:
 * the library's common interface and its non-Android (Skiko) actual — the PC has only this platform.
 * (HypnoticCanvas, MIT; the shader sources carry their own headers in `shaders/`.)
 */

/** Describes a platform-independent runtime effect (the library's `RuntimeEffect`). */
interface RuntimeEffect {

    /** Indicates if the current platform is supported */
    val supported: Boolean

    /** Defines if the effect is ready to be displayed */
    val ready: Boolean

    /** Sets a float array uniform for this shader */
    fun setFloatUniform(name: String, value1: Float) {}

    /** Sets a float array uniform for this shader */
    fun setFloatUniform(name: String, value1: Float, value2: Float) {}

    /** Sets a float array uniform for this shader */
    fun setFloatUniform(name: String, value1: Float, value2: Float, value3: Float) {}

    /** Sets a float array uniform for this shader */
    fun setFloatUniform(name: String, values: FloatArray) {}

    /** Updates the uniforms for the shader, on changes of the size or time. */
    fun update(shader: Shader, time: Float, width: Float, height: Float) {}

    /** Builds an updated [ShaderBrush] */
    fun build(): Brush
}

/** The library's non-Android actual (Skiko's `RuntimeEffect.makeForShader` + `RuntimeShaderBuilder`). */
internal fun buildEffect(shader: Shader): RuntimeEffect = SkikoRuntimeEffect(shader)

private class SkikoRuntimeEffect(shader: Shader) : RuntimeEffect {
    private val compositeRuntimeEffect = org.jetbrains.skia.RuntimeEffect.makeForShader(shader.sksl)
    private val compositeShaderBuilder = RuntimeShaderBuilder(compositeRuntimeEffect)

    override val supported: Boolean = true
    override var ready: Boolean = false

    override fun setFloatUniform(name: String, value1: Float) {
        compositeShaderBuilder.uniform(name, value1)
    }

    override fun setFloatUniform(name: String, value1: Float, value2: Float) {
        compositeShaderBuilder.uniform(name, value1, value2)
    }

    override fun setFloatUniform(name: String, value1: Float, value2: Float, value3: Float) {
        compositeShaderBuilder.uniform(name, value1, value2, value3)
    }

    override fun setFloatUniform(name: String, values: FloatArray) {
        compositeShaderBuilder.uniform(name, values)
    }

    override fun update(shader: Shader, time: Float, width: Float, height: Float) {
        shader.applyUniforms(this, time, width, height)
        ready = width > 0 && height > 0
    }

    override fun build(): Brush {
        return ShaderBrush(compositeShaderBuilder.makeShader().asComposeShader())
    }
}
