package app.n_zik.compagnon.components.tab

import androidx.compose.runtime.Composable
import app.n_zik.compagnon.components.LocalMenuState
import app.n_zik.compagnon.components.MenuState
import app.n_zik.compagnon.components.tab.toolbar.MenuIcon
import app.n_zik.compagnon.generated.resources.Res
import app.n_zik.compagnon.generated.resources.shuffle
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.stringResource

/**
 * Port of `SongShuffler` (phone's `app/n_zik/android/components/tab/SongShuffler.kt`): plays the
 * collection shuffled. Here [onShuffle] sends `queue/play` with the ids shuffled on the PC
 * (`LibraryActions`). Dropped: the 1 s `shuffle_ok` confirmation icon (it follows the phone's local
 * player binder).
 */
class SongShuffler private constructor(
    private val menuState: MenuState,
    private val enabled: Boolean,
    private val onShuffle: () -> Unit,
) : MenuIcon {

    companion object {
        @Composable
        operator fun invoke(enabled: Boolean = true, onShuffle: () -> Unit) =
            SongShuffler(LocalMenuState.current, enabled, onShuffle)
    }

    override val iconId: DrawableResource = Res.drawable.shuffle
    override val isEnabled: Boolean get() = enabled
    override val menuIconTitle: String
        @Composable
        get() = stringResource(Res.string.shuffle)

    override fun onShortClick() {
        onShuffle()
        menuState.hide()
    }
}
