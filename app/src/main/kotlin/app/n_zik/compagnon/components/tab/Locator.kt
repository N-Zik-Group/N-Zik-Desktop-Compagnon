package app.n_zik.compagnon.components.tab

import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.Color
import app.n_zik.compagnon.LocalLibraryActions
import app.n_zik.compagnon.LocalPlayerRepository
import app.n_zik.compagnon.bridge.library.LibraryRepository
import app.n_zik.compagnon.bridge.library.PagedList
import app.n_zik.compagnon.bridge.state.ListRef
import app.n_zik.compagnon.bridge.state.PlayerRepository
import app.n_zik.compagnon.bridge.state.Track
import app.n_zik.compagnon.components.tab.toolbar.Descriptive
import app.n_zik.compagnon.components.tab.toolbar.DynamicColor
import app.n_zik.compagnon.components.tab.toolbar.MenuIcon
import app.n_zik.compagnon.colorPalette
import app.n_zik.compagnon.generated.resources.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Port of `Locator` (phone's `app/n_zik/android/components/tab/Locator.kt`): scrolls to the phone's current
 * track in the list, or tells why it cannot ([onMessage], the phone's toast). [indexOffset] is the number of
 * list items before the tracks (headers).
 *
 * The phone searches its whole list. Since contract 1.10.0 (feature `library.locate`), with a [listRef]
 * (the whole list the screen shows) and its [pagedList], the PC asks the phone for the track's position in
 * the WHOLE list and loads the pages up to it before scrolling; without the feature (or for a client-side
 * list: the PC-only chips, the queue) it searches the loaded items, its behavior before 1.10.0.
 *
 * Its long click shows the phone's description (`Descriptive`, `info_find_the_song_that_is_playing`).
 */
class Locator private constructor(
    private val repository: PlayerRepository?,
    private val library: LibraryRepository?,
    private val scrollableState: ScrollableState,
    private val getSongs: () -> List<Track>,
    private val coroutineScope: CoroutineScope,
    private val indexOffset: Int,
    private val listRef: () -> ListRef?,
    private val dedupedRows: Boolean,
    private val pagedList: PagedList<*, Track>?,
    private val onMessage: (StringResource) -> Unit,
) : MenuIcon, Descriptive, DynamicColor {

    companion object {
        @Composable
        operator fun invoke(
            scrollableState: ScrollableState,
            getSongs: () -> List<Track>,
            indexOffset: Int = 0,
            listRef: () -> ListRef? = { null },
            pagedList: PagedList<*, Track>? = null,
            /** The list draws one row per track id (the screens' `distinctBy`); `false` for the queue. */
            dedupedRows: Boolean = true,
            onMessage: (StringResource) -> Unit,
        ): Locator = Locator(
            repository = LocalPlayerRepository.current,
            library = LocalLibraryActions.current?.library,
            scrollableState = scrollableState,
            getSongs = getSongs,
            coroutineScope = rememberCoroutineScope(),
            indexOffset = indexOffset,
            listRef = listRef,
            dedupedRows = dedupedRows,
            pagedList = pagedList,
            onMessage = onMessage,
        )
    }

    private val currentTrackId: String?
        get() = repository?.state?.value?.currentTrackId

    /** Position of the current track among the LOADED items. */
    val position: Int
        get() = getSongs().map(Track::id).indexOf(currentTrackId)

    override val iconId: DrawableResource = Res.drawable.locate

    override val messageId: StringResource = Res.string.info_find_the_song_that_is_playing

    /** The phone's `DynamicColor` state: a track is current (its menu icon turns `textDisabled` otherwise). */
    override var isFirstColor: Boolean
        get() = currentTrackId != null
        set(_) {}

    /** `DynamicColor`: `text` while a track is current, `textDisabled` otherwise (collected, so it recomposes). */
    override val color: Color
        @Composable
        get() {
            val state = repository?.state?.collectAsState()?.value
            return if (state?.currentTrackId != null) colorPalette().text else colorPalette().textDisabled
        }
    override val menuIconTitle: String
        @Composable
        get() = stringResource(Res.string.info_find_the_song_that_is_playing)

    /** The running search (a whole-list locate may load pages): a click while it runs is ignored. */
    private var job: Job? = null

    override fun onShortClick() {
        val trackId = currentTrackId ?: return onMessage(Res.string.no_songs_playing)
        if (job?.isActive == true) return
        val ref = listRef()
        val list = pagedList
        job = coroutineScope.launch {
            // The phone's whole list first (since 1.10.0); `null` = feature absent or a failed read
            val whole = if (ref != null && list != null) library?.locate(ref, trackId) else null
            val raw = when {
                whole == null -> position
                whole < 0 -> -1
                list != null && list.loadThrough(whole) -> whole
                else -> -1
            }
            // The lists draw one row per track id (`distinctBy`): the raw index maps to its row
            val target = if (dedupedRows) locatorRow(getSongs().map(Track::id), raw) else raw
            if (target == -1) {
                onMessage(Res.string.playing_song_not_found_on_current_list)
            } else {
                when (scrollableState) {
                    is LazyListState -> scrollableState.scrollToItem(target + indexOffset)
                    is LazyGridState -> scrollableState.scrollToItem(target + indexOffset)
                }
            }
        }
    }
}

/**
 * The row of the raw list index [raw] in a list drawn one row per id ([ids] de-duplicated, first occurrence
 * kept): `-1` when [raw] is out of the list.
 */
internal fun locatorRow(ids: List<String>, raw: Int): Int {
    val id = ids.getOrNull(raw) ?: return -1
    return ids.distinct().indexOf(id)
}
