package app.n_zik.compagnon.utils

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.n_zik.compagnon.LocalCommandLauncher
import app.n_zik.compagnon.bridge.state.PlayerState
import app.n_zik.compagnon.components.LocalMenuState
import app.n_zik.compagnon.components.menu.player.PlaybackSettingsMenu
import app.n_zik.compagnon.components.player.controls.ControlsEssential
import app.n_zik.compagnon.components.theme.ColorPalette
import app.n_zik.compagnon.enums.PlayerPlayButtonType

/**
 * Port of `GetControls` (phone's `app/it/fast4x/rimusic/utils/GetControlsType.kt` 33), with its default
 * preferences: `PlayerControlsType.Essential`, `PlayerPlayButtonType.CircularRibbed`, the `AnimatedGradient`
 * background (so no gradient background behind the play button). The long press on play opens the
 * [PlaybackSettingsMenu] in the menu sheet.
 * Dropped: the `Modern` controls (not the default) and `MedleyMode` (local playback only).
 */
@Composable
fun GetControls(
    state: PlayerState,
    live: Boolean,
    dynamicColorPalette: ColorPalette,
) {
    val onCommand = LocalCommandLauncher.current
    val playerPlayButtonType = PlayerPlayButtonType.CircularRibbed
    // ThemeColorGradient / CoverColorGradient only (the default is AnimatedGradient)
    val isGradientBackgroundEnabled = false

    val menuState = LocalMenuState.current

    val playbackSettingsMenu = remember(menuState, onCommand) {
        PlaybackSettingsMenu.create(
            onSpeed = { speed -> onCommand { setSpeed(speed) } },
        )
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly,
        modifier = Modifier
            .fillMaxWidth(),
    ) {
        ControlsEssential(
            playbackSpeed = state.speed,
            shouldBePlaying = state.isPlaying,
            repeatMode = state.repeatMode,
            playerPlayButtonType = playerPlayButtonType,
            isGradientBackgroundEnabled = isGradientBackgroundEnabled,
            onShowSpeedPlayerDialog = { menuState.display { playbackSettingsMenu.MenuComponent() } },
            dynamicColorPalette = dynamicColorPalette,
            enabled = live,
        )
    }
}
