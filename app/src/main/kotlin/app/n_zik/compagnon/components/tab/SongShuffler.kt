package app.n_zik.compagnon.components.tab

import androidx.compose.runtime.Composable
import app.n_zik.compagnon.components.LocalMenuState
import app.n_zik.compagnon.components.MenuState
import app.n_zik.compagnon.components.tab.toolbar.Descriptive
import app.n_zik.compagnon.components.tab.toolbar.MenuIcon
import org.jetbrains.compose.resources.StringResource
import app.n_zik.compagnon.generated.resources.*
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.stringResource

/**
 * Port of `SongShuffler` (phone's `app/n_zik/android/components/tab/SongShuffler.kt`): plays the
 * collection shuffled. Here [onShuffle] goes through `LibraryActions`: since contract 1.10.0
 * (`queue.fullList`) the phone shuffles its whole list itself (its `Shuffler.play`, filters and toasts);
 * before, `queue/play` with the loaded ids shuffled on the PC. As on the phone (issue #866, its `shuffleButtonIcon()`), any shuffle press
 * lights the app-wide [ShuffleOkFlash] for a second.
 */
class SongShuffler private constructor(
    private val menuState: MenuState,
    private val enabled: Boolean,
    private val onShuffle: () -> Unit,
) : MenuIcon, Descriptive {

    companion object {
        @Composable
        operator fun invoke(enabled: Boolean = true, onShuffle: () -> Unit): SongShuffler {
            return SongShuffler(LocalMenuState.current, enabled) {
                ShuffleOkFlash.trigger()
                onShuffle()
            }
        }
    }

    override val iconId: DrawableResource
        get() = if (ShuffleOkFlash.active) Res.drawable.shuffle_ok else Res.drawable.shuffle
    override val isEnabled: Boolean get() = enabled

    /** The phone's `Descriptive` message (`SongShuffler.kt` 62): shown on a right click (its long press). */
    override val messageId: StringResource = Res.string.info_shuffle
    override val menuIconTitle: String
        @Composable
        get() = stringResource(Res.string.shuffle)

    override fun onShortClick() {
        onShuffle()
        menuState.hide()
    }
}
