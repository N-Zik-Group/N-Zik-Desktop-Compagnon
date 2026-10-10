package app.n_zik.compagnon.components.themed

import app.n_zik.compagnon.components.tab.toolbar.Descriptive
import org.jetbrains.compose.resources.StringResource
import androidx.compose.runtime.Composable
import app.n_zik.compagnon.components.LocalMenuState
import app.n_zik.compagnon.components.MenuState
import app.n_zik.compagnon.components.tab.toolbar.MenuIcon
import app.n_zik.compagnon.generated.resources.*
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.stringResource

/*
 * Port of `PlayNext` and `Enqueue` (phone's `app/it/fast4x/rimusic/ui/components/themed/MenuComponent.kt` 16, 35).
 * [enabled] is the Compagnon's live-session rule (actions disabled outside a `Live` session).
 */

@Composable
fun PlayNext(
    enabled: Boolean = true,
    /** The phone's empty playlist (its `LocalPlaylistItemMenu.kt` 392-423): `no_song_found`, the menu kept open. */
    isEmpty: Boolean = false,
    onClick: () -> Unit,
): MenuIcon = object : MenuIcon, Descriptive {

    val menuState: MenuState = LocalMenuState.current
    override val messageId: StringResource = Res.string.play_next
    override val iconId: DrawableResource = Res.drawable.play_skip_forward
    override val isEnabled: Boolean = enabled
    override val menuIconTitle: String
        @Composable
        get() = stringResource(Res.string.play_next)

    override fun onShortClick() {
        if (isEmpty) {
            app.n_zik.compagnon.utils.Toaster.e(Res.string.no_song_found)
            return
        }
        onClick()
        menuState.hide()
    }
}

@Composable
fun Enqueue(
    enabled: Boolean = true,
    /** The phone's empty playlist (its `LocalPlaylistItemMenu.kt` 392-423): `no_song_found`, the menu kept open. */
    isEmpty: Boolean = false,
    onClick: () -> Unit,
): MenuIcon = object : MenuIcon, Descriptive {

    val menuState: MenuState = LocalMenuState.current
    override val messageId: StringResource = Res.string.enqueue
    override val iconId: DrawableResource = Res.drawable.enqueue
    override val isEnabled: Boolean = enabled
    override val menuIconTitle: String
        @Composable
        get() = stringResource(Res.string.enqueue)

    override fun onShortClick() {
        if (isEmpty) {
            app.n_zik.compagnon.utils.Toaster.e(Res.string.no_song_found)
            return
        }
        onClick()
        menuState.hide()
    }
}

/**
 * "Add to favorites" (contract §10.2, since 1.7): the phone's `LikeComponent` — an explicit like
 * (`state = liked`), not a rotation; it keeps the phone's label and heart icon.
 */
@Composable
fun AddToFavorites(
    enabled: Boolean = true,
    onClick: () -> Unit,
): MenuIcon = object : MenuIcon, Descriptive {
    // The phone's button is `Descriptive`: a right click shows its description
    override val messageId: StringResource = Res.string.add_to_favorites

    val menuState: MenuState = LocalMenuState.current
    override val iconId: DrawableResource = Res.drawable.heart
    override val isEnabled: Boolean = enabled
    override val menuIconTitle: String
        @Composable
        get() = stringResource(Res.string.add_to_favorites)

    override fun onShortClick() {
        onClick()
        menuState.hide()
    }
}

/**
 * "Bookmark" (the phone's `Bookmark`, its `MenuComponent.kt` 112): `bookmark` in its state,
 * `bookmark_outline` otherwise; the PC shows it in a local playlist's header (the phone's right column),
 * and the toggle is the 1.7.2 write `POST /library/playlists/{id}/bookmark`.
 */
@Composable
fun Bookmark(
    isBookmarked: Boolean,
    onClick: () -> Unit,
): MenuIcon = object : MenuIcon, Descriptive {
    // The phone's button is `Descriptive` but its long press is empty (`onLongClick() {}`): no help
    override val messageId: StringResource = Res.string.bookmark

    val menuState: MenuState = LocalMenuState.current
    override val iconId: DrawableResource = if (isBookmarked) Res.drawable.bookmark else Res.drawable.bookmark_outline
    override val menuIconTitle: String
        @Composable
        get() = stringResource(Res.string.bookmark)

    override fun onShortClick() {
        onClick()
        menuState.hide()
    }

    override fun onLongClick() {}
}

/**
 * Port of the phone's toolbar `PinPlaylist` (`components/playlist/PinPlaylist.kt`, contract §10.2 since 1.7:
 * the `pinned:` name prefix toggled through `POST /library/playlists/{id}/pin`): `pin_filled`, a
 * `DynamicColor` (text when pinned, `textDisabled` otherwise), `Descriptive` (`info_pin_unpin_playlist`).
 * Shown in the local playlist's toolbar; the phone has no pin entry in the playlist's item menu.
 */
@Composable
fun PinPlaylist(
    isPinned: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
): MenuIcon = object : MenuIcon, app.n_zik.compagnon.components.tab.toolbar.DynamicColor, app.n_zik.compagnon.components.tab.toolbar.Descriptive {
    override val iconId: DrawableResource = Res.drawable.pin_filled
    override val messageId: StringResource = Res.string.info_pin_unpin_playlist
    override val isEnabled: Boolean = enabled
    override var isFirstColor: Boolean
        get() = isPinned
        set(_) {}
    override val menuIconTitle: String
        @Composable
        get() = stringResource(messageId)

    override fun onShortClick() = onClick()
}

/**
 * Compagnon only: "Remove from queue" (`queue/remove`) in the menu of a queue item. The phone removes a queue
 * item with a swipe, which the PC does not have; the entry reuses the phone's label and trash icon (its
 * `DeleteFromQueue` button).
 */
@Composable
fun RemoveFromQueue(
    enabled: Boolean = true,
    onClick: () -> Unit,
): MenuIcon = object : MenuIcon {

    val menuState: MenuState = LocalMenuState.current
    override val iconId: DrawableResource = Res.drawable.trash
    override val isEnabled: Boolean = enabled
    override val menuIconTitle: String
        @Composable
        get() = stringResource(Res.string.remove_from_queue)

    override fun onShortClick() {
        onClick()
        menuState.hide()
    }
}
