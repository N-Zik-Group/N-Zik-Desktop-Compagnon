package app.n_zik.compagnon.components.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Port of the phone's `SwipeActionLatch` (`app/n_zik/android/components/player/SwipeActionLatch.kt`):
 * lets a swipe action run once per gesture.
 *
 * `SwipeToDismissBox` calls `confirmValueChange` again on every drag delta once the finger reaches
 * the end anchor (the state never settles because the callback answers false), so an action placed
 * there ran once per pixel of movement. [tryFire] answers true for the first call only; [release]
 * rearms it once the gesture is over.
 *
 * [isFired] is observable: the swipe is switched off while it is true, which ends a drag that the
 * finger keeps alive so the box can return to its position without waiting for the release.
 *
 * Not thread-safe: meant to be used from the main thread, like the gesture callbacks.
 */
class SwipeActionLatch {
    /** True from the moment the action ran until the latch is released. */
    var isFired by mutableStateOf(false)
        private set

    /** True the first time it is called since the latch was created or last released. */
    fun tryFire(): Boolean {
        if (isFired) return false
        isFired = true
        return true
    }

    /** Rearms the latch so the next gesture can fire its action. */
    fun release() {
        isFired = false
    }
}
