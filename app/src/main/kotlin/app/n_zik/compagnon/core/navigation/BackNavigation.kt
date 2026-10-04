package app.n_zik.compagnon.core.navigation

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type

/**
 * The PC's back (story 11c): the phone's system back, as the window's Escape key and the mouse's back
 * button. Each press closes one thing, in the phone's order: the menu sheet, then the panels (phone, settings),
 * then the queue overlay, then the full player, then the open page (album, artist, playlist).
 */
enum class BackStep { Menu, Panel, Queue, Player, Page }

/** What a back press closes, or `null` when nothing is open (the home stays, like the phone's root). */
fun backStep(
    menuOpen: Boolean,
    panelOpen: Boolean,
    queueOpen: Boolean,
    playerOpen: Boolean,
    pageOpen: Boolean,
): BackStep? = when {
    menuOpen -> BackStep.Menu
    panelOpen -> BackStep.Panel
    queueOpen -> BackStep.Queue
    playerOpen -> BackStep.Player
    pageOpen -> BackStep.Page
    else -> null
}

/** Whether a window key event is the back key (Escape, on its press). */
fun isBackKey(event: KeyEvent): Boolean = event.type == KeyEventType.KeyDown && event.key == Key.Escape

/**
 * Routes the window's back key to the paired screen ([handler], set while it is shown). [dispatch] returns
 * `true` when the press was used, so an unused Escape keeps reaching the focused component.
 */
class BackDispatcher {
    var handler: (() -> Boolean)? = null

    fun dispatch(): Boolean = handler?.invoke() ?: false
}

val LocalBackDispatcher = staticCompositionLocalOf<BackDispatcher?> { null }
