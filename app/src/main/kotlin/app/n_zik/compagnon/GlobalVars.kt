package app.n_zik.compagnon

import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Shape
import app.n_zik.compagnon.bridge.state.PlayerRepository
import app.n_zik.compagnon.components.theme.BoundedCornerSize
import app.n_zik.compagnon.components.theme.LocalAppearance

/*
 * Port of the phone's `app/n_zik/android/GlobalVars.kt` (26-88): the accessors of the `Appearance`.
 * Dropped: `gridMenuShape` (no grid menu: the default `MenuStyle` is `List`) and the preference / context
 * helpers (Android only).
 */

@Composable
fun typography() = LocalAppearance.current.typography

@Composable
@ReadOnlyComposable
fun colorPalette() = LocalAppearance.current.colorPalette

@Composable
fun thumbnailShape() = LocalAppearance.current.thumbnailShape

@Composable
fun artistThumbnailShape() = LocalAppearance.current.artistThumbnailShape

@Composable
fun uiRoundnessShape() = LocalAppearance.current.uiRoundnessShape

@Composable
fun exactUiRoundnessShape(): Shape {
    val appearance = LocalAppearance.current
    val shape = appearance.uiRoundnessShape
    return if (shape is RoundedCornerShape) {
        val size = shape.topStart
        if (size is BoundedCornerSize) {
            RoundedCornerShape(size.dp)
        } else {
            shape
        }
    } else {
        shape
    }
}

@Composable
fun topUiRoundnessShape(): Shape {
    val appearance = LocalAppearance.current
    val shape = appearance.uiRoundnessShape
    return if (shape is RoundedCornerShape) {
        RoundedCornerShape(
            topStart = shape.topStart,
            topEnd = shape.topEnd,
            bottomEnd = CornerSize(0),
            bottomStart = CornerSize(0),
        )
    } else {
        shape
    }
}

/**
 * The phone's `LocalPlayerServiceBinder`: here the remote player (state from the WS, artwork through the
 * phone). `null` outside the main window.
 */
val LocalPlayerRepository = staticCompositionLocalOf<PlayerRepository?> { null }

/** Runs a command of the remote player (launched in the main window's scope, so closing a panel never cancels it). */
typealias CommandLauncher = (suspend PlayerRepository.() -> Unit) -> Unit

/**
 * How the ported player screens reach the phone's player: where the phone calls `binder.player.*`, the
 * Compagnon launches the matching command (contract §9). Provided by the main window.
 */
val LocalCommandLauncher = staticCompositionLocalOf<CommandLauncher> { {} }
