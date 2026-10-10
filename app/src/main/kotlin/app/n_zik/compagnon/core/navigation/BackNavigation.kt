package app.n_zik.compagnon.core.navigation

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type

/**
 * The PC's back (story 11c): the phone's system back, as the window's Escape key and the mouse's back
 * button. Each press closes one thing, in the phone's order: the menu sheet, then the sheets drawn over the
 * pages — the queue overlay, the full player's inline queue, the full player (the phone's sheets handle
 * the back above its NavHost) — then the navigation page (spec `spec-settings-navigation` NAV-7: the
 * update sub-page, then the settings or the Serveur PC page), then the open page (album, artist, playlist),
 * then — after a title / logo click, which pushes the phone's home route — the page that click left
 * ([BackStep.HomeReturn], one level).
 * Open (pass 5, not verified at runtime): Escape while the header's DropdownMenu or a dialog is open is
 * consumed by them first only when they hold the focus; the window handler does not know of them.
 */
enum class BackStep { Menu, Panel, Queue, PlayerQueue, Player, Page, HomeReturn }

/** What a back press closes, or `null` when nothing is open (the home stays, like the phone's root). */
fun backStep(
    menuOpen: Boolean,
    panelOpen: Boolean,
    queueOpen: Boolean,
    playerOpen: Boolean,
    pageOpen: Boolean,
    /** The full player's inline queue panel (the phone's `BackHandler { showQueue = false }`): closed before the player. */
    playerQueueOpen: Boolean = false,
    /** A home pushed by the title / logo click (the phone's home route): back returns to the page it left. */
    homeReturnPending: Boolean = false,
): BackStep? = when {
    menuOpen -> BackStep.Menu
    // The queue overlay and the player sheet are drawn over the pages (the phone's sheets have their own
    // BackHandler above its NavHost): they close before the page under them
    queueOpen -> BackStep.Queue
    playerOpen && playerQueueOpen -> BackStep.PlayerQueue
    playerOpen -> BackStep.Player
    panelOpen -> BackStep.Panel
    pageOpen -> BackStep.Page
    homeReturnPending -> BackStep.HomeReturn
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

/** The window's "home" navigation (the header title's): the player's logo goes home with it. */
val LocalGoHome = staticCompositionLocalOf<(() -> Unit)?> { null }

/** The full player's inline queue state, hoisted to the window so the back closes it first. */
val LocalPlayerQueueState = staticCompositionLocalOf<androidx.compose.runtime.MutableState<Boolean>?> { null }
