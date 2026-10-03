package app.n_zik.compagnon.components.menu

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.n_zik.compagnon.colorPalette
import app.n_zik.compagnon.utils.semiBold
import app.n_zik.compagnon.typography
import app.n_zik.compagnon.uiRoundnessShape
import app.n_zik.compagnon.topUiRoundnessShape

/**
 * Port of the phone's `app/n_zik/android/components/menu/ListMenu.kt` (with `MenuConstants.kt`).
 * `CONTENT_HEIGHT_FRACTION` = 1: the sheet host bounds the height. The optional `headerTrailing` slot is
 * not ported (no library menu uses it).
 */
object ListMenu {

    @Composable
    fun Menu(showDragHandle: Boolean = true, title: String? = null, content: @Composable ColumnScope.() -> Unit) {
        val hasHeader = showDragHandle || title != null

        Column(
            Modifier
                .fillMaxWidth()
                .clip(topUiRoundnessShape())
                .background(colorPalette().background0),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = {
                if (hasHeader) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(topUiRoundnessShape())
                            .background(colorPalette().background1),
                    ) {
                        if (showDragHandle) {
                            DragHandle(colorPalette().text)
                        }

                        title?.let {
                            Text(
                                text = it,
                                style = typography().m.copy(color = colorPalette().text),
                                modifier = Modifier.padding(top = 5.dp, bottom = 10.dp),
                            )
                        }

                        HorizontalDivider(Modifier.height(1.dp))
                    }
                }

                val topShape = topUiRoundnessShape()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .background(colorPalette().background0)
                        .then(if (!hasHeader) Modifier.clip(topShape) else Modifier)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    content()
                    Spacer(modifier = Modifier.height(100.dp))
                }
            },
        )
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    fun Entry(
        text: String,
        icon: @Composable RowScope.() -> Unit,
        modifier: Modifier = Modifier,
        enabled: Boolean = true,
        subtitle: String? = null,
        onClick: () -> Unit = {},
        onLongClick: () -> Unit = {},
        trailingContent: @Composable () -> Unit = {},
    ) {
        val alpha = if (enabled) 1f else 0.5f

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = modifier
                .fillMaxWidth()
                .clip(uiRoundnessShape())
                .combinedClickable(
                    enabled = enabled,
                    onClick = onClick,
                    onLongClick = onLongClick,
                )
                .padding(vertical = 10.dp),
        ) {
            icon()

            Column(
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = text,
                    overflow = TextOverflow.Ellipsis,
                    color = colorPalette().text.copy(alpha = alpha),
                    textAlign = TextAlign.Start,
                    style = typography().s.semiBold,
                    maxLines = 1,
                    modifier = Modifier.fillMaxWidth()
                        .basicMarquee(iterations = Int.MAX_VALUE),
                )

                subtitle?.let {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = it,
                        overflow = TextOverflow.Ellipsis,
                        color = colorPalette().textSecondary.copy(alpha = alpha),
                        textAlign = TextAlign.Start,
                        style = typography().xs,
                        maxLines = 1,
                        modifier = Modifier.fillMaxWidth()
                            .basicMarquee(iterations = Int.MAX_VALUE),
                    )
                }
            }

            trailingContent()
        }
    }

    /**
     * The menus' `SectionTitle` (identical private copy in `SongItemMenu.kt`, `AlbumItemMenu.kt` 576,
     * `LocalArtistItemMenu.kt` 389 and `LocalPlaylistItemMenu.kt`).
     */
    @Composable
    fun SectionTitle(title: String) {
        BasicText(
            text = title,
            style = typography().xxs.semiBold.copy(
                color = colorPalette().accent,
                textAlign = TextAlign.Start,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 4.dp),
        )
    }

    /** The menus' drag handle: 40 × 4 dp pill, 18 dp above, 6 dp below (`MenuConstants.DRAG_HANDLE_*`). */
    @Composable
    fun DragHandle(color: Color) {
        Box(
            modifier = Modifier
                .padding(top = 18.dp, bottom = 6.dp)
                .size(width = 40.dp, height = 4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color),
        )
    }
}
