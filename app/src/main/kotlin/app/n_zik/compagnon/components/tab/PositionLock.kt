package app.n_zik.compagnon.components.tab

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.n_zik.compagnon.components.tab.toolbar.Descriptive
import app.n_zik.compagnon.components.tab.toolbar.DualIcon
import app.n_zik.compagnon.components.tab.toolbar.DynamicColor
import app.n_zik.compagnon.components.tab.toolbar.MenuIcon
import app.n_zik.compagnon.generated.resources.Res
import app.n_zik.compagnon.generated.resources.info_lock_unlock_reorder_songs
import app.n_zik.compagnon.generated.resources.info_reorder_is_possible_only_in_ascending_sort
import app.n_zik.compagnon.generated.resources.locked
import app.n_zik.compagnon.generated.resources.unlocked
import app.n_zik.compagnon.utils.Toaster
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Port of `PositionLock` (phone's `app/kreate/android/themed/rimusic/component/playlist/PositionLock.kt`): the
 * reorder lock of a list (locked by default). Unlocked, the queue shows its drag handles. [isEnabled] is
 * `false` while the queue cannot be changed (no `queue` feature, no `Live` session), as the phone's Listen
 * Together lock.
 */
class PositionLock(
    colorState: MutableState<Boolean>,
    private val enabled: () -> Boolean,
) : MenuIcon, DualIcon, DynamicColor, Descriptive {

    constructor(enabled: () -> Boolean) : this(mutableStateOf(true), enabled)

    override val secondIconId: DrawableResource = Res.drawable.unlocked
    override val iconId: DrawableResource = Res.drawable.locked
    override val messageId: StringResource = Res.string.info_lock_unlock_reorder_songs
    override val menuIconTitle: String
        @Composable
        get() = stringResource(messageId)

    // This is inverted, first icon is locked icon
    override var isFirstIcon: Boolean by mutableStateOf(true)
    override var isFirstColor: Boolean by colorState

    override val isEnabled: Boolean
        get() = enabled()

    fun isLocked(): Boolean = isFirstIcon

    override fun onShortClick() {
        if (!enabled()) return
        if (!isFirstColor) {
            Toaster.e(Res.string.info_reorder_is_possible_only_in_ascending_sort)
        } else {
            isFirstIcon = !isFirstIcon
        }
    }
}
