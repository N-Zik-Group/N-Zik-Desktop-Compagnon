package app.n_zik.compagnon.components.player

import androidx.compose.material3.SwipeToDismissBoxValue

/** What a swipe on the mini-player does (the phone's `MiniPlayerSwipeAction.kt` 6-11). */
enum class MiniPlayerSwipeAction {
    /** Cycles the like state of the song playing when the swipe happens. */
    LikeCurrentSong,
    SeekToNext,
}

/**
 * Maps a swipe direction to its action (the phone's `MiniPlayerSwipeAction.kt` 35-44).
 *
 * The PC's mini-player is the phone's `Essential` type (its default): it has no previous button, so
 * its right swipe likes the song instead of going back — the phone's `SeekToPrevious` branch never
 * applies here.
 *
 * @return null while the box is at rest.
 */
fun miniPlayerSwipeAction(
    direction: SwipeToDismissBoxValue,
): MiniPlayerSwipeAction? = when (direction) {
    SwipeToDismissBoxValue.StartToEnd -> MiniPlayerSwipeAction.LikeCurrentSong
    SwipeToDismissBoxValue.EndToStart -> MiniPlayerSwipeAction.SeekToNext
    SwipeToDismissBoxValue.Settled -> null
}
