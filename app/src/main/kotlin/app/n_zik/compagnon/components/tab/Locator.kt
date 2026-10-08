package app.n_zik.compagnon.components.tab

import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.Color
import app.n_zik.compagnon.LocalPlayerRepository
import app.n_zik.compagnon.bridge.state.PlayerRepository
import app.n_zik.compagnon.bridge.state.Track
import app.n_zik.compagnon.components.tab.toolbar.MenuIcon
import app.n_zik.compagnon.colorPalette
import app.n_zik.compagnon.generated.resources.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Port of `Locator` (phone's `app/n_zik/android/components/tab/Locator.kt`): scrolls to the phone's current
 * track in the loaded list, or tells why it cannot ([onMessage], the phone's toast). [indexOffset] is the
 * number of list items before the tracks (headers).
 */
class Locator private constructor(
    private val repository: PlayerRepository?,
    private val scrollableState: ScrollableState,
    private val getSongs: () -> List<Track>,
    private val coroutineScope: CoroutineScope,
    private val indexOffset: Int,
    private val onMessage: (StringResource) -> Unit,
) : MenuIcon {

    companion object {
        @Composable
        operator fun invoke(
            scrollableState: ScrollableState,
            getSongs: () -> List<Track>,
            indexOffset: Int = 0,
            onMessage: (StringResource) -> Unit,
        ): Locator = Locator(
            repository = LocalPlayerRepository.current,
            scrollableState = scrollableState,
            getSongs = getSongs,
            coroutineScope = rememberCoroutineScope(),
            indexOffset = indexOffset,
            onMessage = onMessage,
        )
    }

    private val currentTrackId: String?
        get() = repository?.state?.value?.currentTrackId

    val position: Int
        get() = getSongs().map(Track::id).indexOf(currentTrackId)

    override val iconId: DrawableResource = Res.drawable.locate

    /** `DynamicColor`: `text` while a track is current, `textDisabled` otherwise. */
    override val color: Color
        @Composable
        get() {
            val state = repository?.state?.collectAsState()?.value
            return if (state?.currentTrackId != null) colorPalette().text else colorPalette().textDisabled
        }
    override val menuIconTitle: String
        @Composable
        get() = stringResource(Res.string.info_find_the_song_that_is_playing)

    override fun onShortClick() {
        if (currentTrackId != null) {
            val position = position
            if (position == -1) {
                onMessage(Res.string.playing_song_not_found_on_current_list)
            } else {
                coroutineScope.launch {
                    when (scrollableState) {
                        is LazyListState -> scrollableState.scrollToItem(position + indexOffset)
                        is LazyGridState -> scrollableState.scrollToItem(position + indexOffset)
                    }
                }
            }
        } else {
            onMessage(Res.string.no_songs_playing)
        }
    }
}
