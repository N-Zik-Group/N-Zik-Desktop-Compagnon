package app.n_zik.compagnon.components.tab

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.n_zik.compagnon.utils.coroutines.NzikDispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The app-wide shuffle-OK flash — port of the phone's binder-driven confirmation (issue #866,
 * `PlayerServiceModern.SHUFFLE_OK_FLASH_MS = 1000L`, its `shuffleButtonIcon()`): any shuffle press
 * (a tab toolbar's [SongShuffler], the artist's 24 dp shuffle button, the player's shuffle toggle)
 * lights — for one second, whatever the screen — every shuffle icon that follows the flash: each
 * [SongShuffler], the artist's shuffle button and the player's toggle. The home toolbars' inert
 * shuffle placeholders (no contract route, their click only toasts) do not follow it — the phone's
 * disabled `SongShuffler` instances do, its state being binder-wide.
 *
 * [app.n_zik.compagnon.utils.Toaster]'s precedent: a fire-and-forget scope
 * ([NzikDispatchers.fireAndForget] on [NzikDispatchers.UI]) outside the composition.
 */
object ShuffleOkFlash {

    /** The phone's `SHUFFLE_OK_FLASH_MS` (`PlayerServiceModern.kt`). */
    private const val SHUFFLE_OK_FLASH_MS = 1_000L

    private val scope = NzikDispatchers.fireAndForget(NzikDispatchers.UI)
    private var resetJob: Job? = null

    var active by mutableStateOf(false)
        private set

    /** Lights the flash for [SHUFFLE_OK_FLASH_MS]; a re-press restarts the timer. */
    fun trigger() {
        resetJob?.cancel()
        active = true
        resetJob = scope.launch {
            delay(SHUFFLE_OK_FLASH_MS)
            active = false
        }
    }
}
