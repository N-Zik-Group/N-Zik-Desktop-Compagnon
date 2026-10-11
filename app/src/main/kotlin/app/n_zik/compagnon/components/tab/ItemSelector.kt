package app.n_zik.compagnon.components.tab

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import app.n_zik.compagnon.colorPalette
import app.n_zik.compagnon.components.LocalMenuState
import app.n_zik.compagnon.components.MenuState
import app.n_zik.compagnon.components.tab.toolbar.Descriptive
import app.n_zik.compagnon.components.tab.toolbar.MenuIcon
import app.n_zik.compagnon.generated.resources.*
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Port of the toolbar toggle of the phone's `ItemSelector` (`app/n_zik/android/components/tab/ItemSelector.kt`):
 * a click hides the open menu and flips [isActive] — `checked_filled` / "Deselect" while active,
 * `unchecked_outline` / "Select" otherwise. While active, the lists show an unchecked box on each row or
 * card ([SongRowSelectionBox], [CardSelectionBox]).
 *
 * Adaptation: no item can be checked — the PC has no group action, so the boxes stay unchecked and a click
 * on one does nothing; the row or card click keeps its own action. The state is the screen's own
 * (`remember`, [rememberItemSelector]), never persisted.
 */
class ItemSelector(
    private val activeState: MutableState<Boolean>,
    private val menuState: MenuState? = null,
) : MenuIcon, Descriptive {

    var isActive: Boolean
        get() = activeState.value
        set(value) {
            activeState.value = value
        }

    override val iconId: DrawableResource
        get() = if (isActive) Res.drawable.checked_filled else Res.drawable.unchecked_outline

    override val messageId: StringResource
        get() = if (isActive) Res.string.item_deselect else Res.string.item_select

    override val menuIconTitle: String
        @Composable
        get() = stringResource(messageId)

    override fun onShortClick() {
        menuState?.hide()
        isActive = !isActive
    }
}

/** The screen's [ItemSelector], inactive at first and never persisted (the phone's `remember`). */
@Composable
fun rememberItemSelector(): ItemSelector {
    val menuState = LocalMenuState.current
    val active = remember { mutableStateOf(false) }
    return remember(menuState) { ItemSelector(active, menuState) }
}

/**
 * The phone's song-row box (its `SongItem.kt` 438-461), unchecked: `unchecked_outline` in `text`, scaled
 * to 0.8 with 8 dp of padding, before the row's trailing content. Inert: a click does nothing.
 */
@Composable
fun SongRowSelectionBox() {
    Icon(
        painter = painterResource(Res.drawable.unchecked_outline),
        contentDescription = null,
        tint = colorPalette().text,
        modifier = Modifier
            .scale(0.8f)
            // The phone's box modifier: the click is consumed (inert here), never the row's
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {}
            .padding(8.dp),
    )
}

/**
 * The phone's card box (its `HomeAlbum.kt` 709-721, `HomeArtist.kt`, `HomeLibrary.kt`), unchecked: a 24 dp
 * `unchecked_outline` in `text` at the top start of the thumbnail, 4 dp of padding, above the badges.
 * Drawn through the cards' `thumbnailOverlay` slot. Inert: a click does nothing.
 */
@Composable
fun CardSelectionBox() {
    Box(Modifier.fillMaxSize().zIndex(2f)) {
        Icon(
            painter = painterResource(Res.drawable.unchecked_outline),
            contentDescription = null,
            tint = colorPalette().text,
            modifier = Modifier
                .padding(4.dp)
                .size(24.dp)
                .align(Alignment.TopStart)
                .zIndex(2f)
                // The click is consumed (inert), never the card's
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {},
        )
    }
}
