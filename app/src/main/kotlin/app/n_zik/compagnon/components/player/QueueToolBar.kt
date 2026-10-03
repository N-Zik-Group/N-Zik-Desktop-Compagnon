package app.n_zik.compagnon.components.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.n_zik.compagnon.colorPalette
import app.n_zik.compagnon.components.navigation.header.TabToolBar
import app.n_zik.compagnon.components.styling.Dimensions
import app.n_zik.compagnon.components.tab.toolbar.Button
import app.n_zik.compagnon.components.tab.toolbar.Icon as ToolbarIcon
import app.n_zik.compagnon.generated.resources.Res
import app.n_zik.compagnon.generated.resources.cd_number_of_songs_in_queue
import app.n_zik.compagnon.generated.resources.musical_notes
import app.n_zik.compagnon.topUiRoundnessShape
import app.n_zik.compagnon.typography
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** Port of `QueueToolBarState` (phone's `app/it/fast4x/rimusic/ui/screens/player/QueueToolBar.kt` 45). */
object QueueToolBarState {
    var mediaItemCount by mutableIntStateOf(0)
    var buttons: List<Button> = emptyList()
    var queueArrow: ToolbarIcon? = null
    var onBarClick: () -> Unit = {}
    var isVisible by mutableIntStateOf(0)

    fun reset() {
        mediaItemCount = 0
        buttons = emptyList()
        queueArrow = null
        onBarClick = {}
        isVisible = 0
    }
}

/**
 * Port of `QueueToolBar` (phone's `app/it/fast4x/rimusic/ui/screens/player/QueueToolBar.kt` 63): the 60 dp bar
 * at the bottom of the queue panel (`background1`, top corners rounded): the number of tracks, the queue's
 * buttons, the arrow that closes the panel; the mini-player sits just above it (higher while the search bar
 * is open). A click on the bar closes the queue.
 * Dropped: the bold line shown when the arrow is hidden (the arrow is shown by default).
 */
@Composable
fun QueueToolBar(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clickable { QueueToolBarState.onBarClick() }
            .background(colorPalette().background1, topUiRoundnessShape())
            .height(60.dp),
    ) {
        // !isLandscape: the player is the phone's portrait one
        val miniPlayerOffset = Dimensions.miniPlayerHeight
        val searchBarHeight = 96.dp
        val yOffset = if (QueueToolBarState.isVisible > 0) -(miniPlayerOffset + searchBarHeight) else -miniPlayerOffset

        Box(
            Modifier.offset(0.dp, yOffset)
                .align(Alignment.TopCenter),
        ) { MiniPlayer({}, {}) }

        val queueArrow = QueueToolBarState.queueArrow

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
            modifier = Modifier
                .padding(horizontal = 8.dp)
                .fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .height(TabToolBar.TOOLBAR_ICON_SIZE)
                    .wrapContentWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(Res.drawable.musical_notes),
                    contentDescription = stringResource(Res.string.cd_number_of_songs_in_queue),
                    tint = colorPalette().text,
                    modifier = Modifier.padding(end = 2.dp),
                )
                BasicText(
                    text = QueueToolBarState.mediaItemCount.toString(),
                    style = TextStyle(
                        color = colorPalette().text,
                        fontStyle = typography().l.fontStyle,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            TabToolBar.Buttons(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.weight(1f),
                buttons = QueueToolBarState.buttons,
            )

            if (queueArrow != null && queueArrow.isEnabled) {
                queueArrow.ToolBarButton()
            }
        }
    }
}
