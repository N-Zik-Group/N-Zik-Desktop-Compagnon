package app.n_zik.compagnon.components.menu.song

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.n_zik.compagnon.bridge.state.Track
import app.n_zik.compagnon.components.SongItem
import app.n_zik.compagnon.components.menu.ListMenu
import app.n_zik.compagnon.colorPalette
import app.n_zik.compagnon.components.themed.Enqueue
import app.n_zik.compagnon.components.themed.PlayNext
import app.n_zik.compagnon.components.themed.RemoveFromQueue
import app.n_zik.compagnon.components.ui.screens.home.ItemActions
import app.n_zik.compagnon.generated.resources.Res
import app.n_zik.compagnon.generated.resources.playback
import org.jetbrains.compose.resources.stringResource

/**
 * Port of `SongItemMenu` (phone's `app/n_zik/android/components/menu/song/SongItemMenu.kt`), list style
 * (the phone's default `MenuStyle.List`), for an online or local song.
 *
 * Kept: the header (drag handle, the song's `SongItem` on `background1`, divider) and the "Playback"
 * section reduced to "Play next" and "Enqueue" (`queue/add` `next` / `end`).
 * Dropped (contract v1 or PC): the like / share buttons of the header, the sections Information,
 * Listen Together, Management, Navigation and Last.fm, and in Playback "Start radio", "Add to favorites"
 * and "Add to playlist".
 * PC only: in the queue, [onRemoveFromQueue] adds "Remove from queue" (`queue/remove`), the phone's swipe
 * having no desktop equivalent.
 */
class SongItemMenu(
    private val song: Track,
    private val actions: ItemActions,
    private val onRemoveFromQueue: (() -> Unit)? = null,
) {

    @Composable
    fun ListMenu() = ListMenu.Menu(title = null, showDragHandle = false) {
        ListMenu.SectionTitle(stringResource(Res.string.playback))
        PlayNext(enabled = actions.enabled, onClick = actions.onPlayNext).ListMenuItem()
        Enqueue(enabled = actions.enabled, onClick = actions.onEnqueue).ListMenuItem()
        // PC only: the queue item's "Remove from queue" (the phone swipes it away)
        onRemoveFromQueue?.let { RemoveFromQueue(enabled = actions.enabled, onClick = it).ListMenuItem() }
    }

    @Composable
    fun MenuComponent() {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colorPalette().background0),
        ) {
            // Song info header
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.background(colorPalette().background1),
            ) {
                ListMenu.DragHandle(Color.White)
                SongItem(
                    song = song,
                    backgroundColor = Color.Transparent,
                    modifier = Modifier.padding(
                        top = 5.dp,
                        bottom = 10.dp,
                    ),
                )
                HorizontalDivider(Modifier.height(1.dp))
            }
            ListMenu()
        }
    }
}
