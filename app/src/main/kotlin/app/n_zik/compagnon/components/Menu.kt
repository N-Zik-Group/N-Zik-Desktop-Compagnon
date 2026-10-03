package app.n_zik.compagnon.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import app.n_zik.compagnon.topUiRoundnessShape

/**
 * Port of `MenuState` (phone's `app/it/fast4x/rimusic/ui/components/Menu.kt` 32): the content of the menu
 * sheet. The phone's back stack of menus (`pop`) is not ported: no library menu opens a sub-menu.
 */
@Stable
class MenuState {
    var content: (@Composable () -> Unit)? by mutableStateOf(null)
        private set

    var isDisplayed: Boolean by mutableStateOf(false)
        private set

    fun display(content: @Composable () -> Unit) {
        this.content = content
        isDisplayed = true
    }

    fun hide() {
        isDisplayed = false
    }
}

val LocalMenuState = staticCompositionLocalOf { MenuState() }

/**
 * The menu sheet, after the phone's `CustomModalBottomSheet` of `MainActivity.kt` (2268-2300): a Material 3
 * modal bottom sheet with its defaults (scrim black at 32 %, at most 640 dp wide), transparent container,
 * top corners at the UI roundness, no drag handle of its own (each menu draws its own). A click on the
 * scrim or Escape closes it. The phone's swipe-down to close has no desktop equivalent.
 */
@Composable
fun BottomSheetMenu(state: MenuState, modifier: Modifier = Modifier) {
    AnimatedVisibility(visible = state.isDisplayed, enter = fadeIn(), exit = fadeOut()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.32f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = state::hide),
        )
    }
    BoxWithConstraints(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        val maxSheetHeight = maxHeight
        AnimatedVisibility(
            visible = state.isDisplayed,
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
        ) {
            Box(
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .padding(top = 24.dp)
                    .heightIn(max = maxSheetHeight)
                    .clip(topUiRoundnessShape())
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                            state.hide()
                            true
                        } else {
                            false
                        }
                    }
                    // Clicks inside the sheet never reach the scrim
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
            ) {
                state.content?.invoke()
            }
        }
    }
}
