package app.n_zik.compagnon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import java.awt.Color as AwtColor
import java.awt.Component
import java.awt.Container
import java.awt.Window
import java.util.logging.Logger
import javax.swing.JFrame

private val log = Logger.getLogger("WindowBackground")

/**
 * AWT system property (Windows toolkit) that makes AWT erase the pixels exposed by a window
 * resize natively, with each component's background color, as soon as the OS reports the new
 * size. Without it, Windows shows the window class's white brush and the compositor's black
 * fill on the new area until the next Compose frame (the white / black resize flash — known
 * Compose Desktop behaviour, JetBrains issues CMP-3420 / CMP-7700). Read once when the AWT
 * toolkit loads, so it must be set before the first window is created.
 */
internal const val ERASE_BACKGROUND_ON_RESIZE_PROPERTY = "sun.awt.erasebackgroundonresize"

/**
 * Converts this Compose [Color] to its AWT equivalent, keeping the alpha channel.
 *
 * Pure: no AWT component is created, so it is unit-testable headless.
 */
internal fun Color.toAwt(): AwtColor {
    val argb = toArgb()
    return AwtColor((argb shr 16) and 0xFF, (argb shr 8) and 0xFF, argb and 0xFF, (argb shr 24) and 0xFF)
}

/**
 * Paints [color] on every AWT layer of the Compose Desktop window, from the frame down to
 * the Skiko canvas.
 *
 * The `Window { }` content is hosted by a `ComposeWindowPanel` (a `JLayeredPane`) whose child
 * is a Skiko `SkiaLayer` (a `JComponent`), whose child is the actual `java.awt.Canvas` surface.
 * Compose Desktop sets no background on any of those layers. With
 * [ERASE_BACKGROUND_ON_RESIZE_PROPERTY] on, AWT erases the pixels exposed by a resize with
 * these backgrounds, so painting the whole tree with the app's own background makes the
 * exposed pixels match the app.
 *
 * The tree is walked recursively rather than cast to the framework's panel classes, so it keeps
 * working if the framework changes its hierarchy; if the window is ever re-laid out, this
 * degrades to "no background applied" instead of a crash.
 */
internal fun applyWindowBackground(window: Window, color: Color) {
    val awtColor = color.toAwt()
    val frame = window as? JFrame ?: return
    val painted = paintComponentTree(frame, awtColor)
    log.fine { "Window background $awtColor painted on: ${painted.joinToString { it.javaClass.name }}" }
}

/**
 * Paints [color] on [component] and recursively on every component below it.
 *
 * Internal (not private) so it is unit-testable headless, like [toAwt].
 */
internal fun paintComponentTree(component: Component, color: AwtColor): List<Component> {
    component.background = color
    val children = if (component is Container) component.components.toList() else emptyList<Component>()
    return listOf(component) + children.flatMap { paintComponentTree(it, color) }
}
