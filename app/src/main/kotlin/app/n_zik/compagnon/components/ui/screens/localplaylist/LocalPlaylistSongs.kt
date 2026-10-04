package app.n_zik.compagnon.components.ui.screens.localplaylist

import app.n_zik.compagnon.components.themed.FloatingActionsContainerWithScrollToTop
import app.n_zik.compagnon.uiRoundnessShape
import app.n_zik.compagnon.generated.resources.smart_shuffle
import app.n_zik.compagnon.components.themed.HeaderIconButton
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import app.n_zik.compagnon.bridge.library.LibraryRepository
import app.n_zik.compagnon.components.LocalMenuState
import app.n_zik.compagnon.components.SongItem
import app.n_zik.compagnon.components.items.playlistThumbnails
import app.n_zik.compagnon.components.menu.song.SongItemMenu
import app.n_zik.compagnon.components.navigation.header.TabToolBar
import app.n_zik.compagnon.components.styling.Dimensions
import app.n_zik.compagnon.components.tab.Locator
import app.n_zik.compagnon.components.tab.SongShuffler
import app.n_zik.compagnon.components.tab.toolbar.Button
import app.n_zik.compagnon.colorPalette
import app.n_zik.compagnon.utils.semiBold
import app.n_zik.compagnon.typography
import app.n_zik.compagnon.components.themed.Enqueue
import app.n_zik.compagnon.components.themed.HeaderWithIcon
import app.n_zik.compagnon.components.themed.IconInfo
import app.n_zik.compagnon.components.themed.PlayNext
import app.n_zik.compagnon.components.themed.Playlist
import app.n_zik.compagnon.components.ui.screens.home.CollectionHeader
import app.n_zik.compagnon.components.ui.screens.home.LibraryActions
import app.n_zik.compagnon.components.ui.screens.home.LoadMoreEffect
import app.n_zik.compagnon.components.ui.screens.home.PagedStatus
import app.n_zik.compagnon.components.ui.screens.home.rememberCollectionSongs
import app.n_zik.compagnon.generated.resources.Res
import app.n_zik.compagnon.generated.resources.library_empty
import app.n_zik.compagnon.generated.resources.musical_notes
import app.n_zik.compagnon.generated.resources.playlist
import app.n_zik.compagnon.generated.resources.time
import app.n_zik.compagnon.thumbnailShape
import app.n_zik.compagnon.utils.formatAsTime
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** The playlist card's cover is `Dimensions.thumbnails.playlist` = 128 dp: `size` ≈ 2× in px. */
private const val PLAYLIST_CARD_SIZE_PX = 256

/**
 * Port of `LocalPlaylistSongs` (phone's `app/it/fast4x/rimusic/ui/screens/localplaylist/LocalPlaylistSongs.kt`
 * 1164-1560).
 *
 * Kept: the `HeaderWithIcon` title (no icon); the `background1` card in thumbnail shape (16 dp sides) with
 * the `Playlist` cover of 128 dp (14 dp above, mosaic of the first four tracks or the artwork of
 * `artworkTrackId`, count pill), the info column (track count with the note icon, duration with the clock
 * icon, 10 / 5 / 30 dp spacers, 80 % or 90 % in landscape) and the column of buttons: the smart-shuffle
 * button in its 48 dp box (recommendations off: `textDisabled`, no contract route so no action), 10 dp,
 * Shuffle (`LocalPlaylistSongs.kt` 1290-1320); the `TabToolBar` (play next, enqueue); the row with the
 * locator at its end; the `SongItem` list, its `Dimensions.bottomSpacer` footer and the scroll-to-top button
 * (`FloatingActionsContainerWithScrollToTop`, 1575-1583).
 * Dropped (contract v1 or PC): smart recommendations (counter, related songs), bookmark, the
 * sort button (the contract fixes the playlist order), position lock and drag to reorder, renumber, pin,
 * search, match, download all / delete downloads, multi-selection, add to favorites / to a playlist, sync,
 * listen on YouTube, import / export, rename, delete, thumbnail picker / reset, update, swipe actions,
 * the play-time overlays.
 * Added by the Compagnon: the paging row, "Nothing here." for an empty playlist. The duration shows once
 * all tracks are loaded.
 */
@Composable
fun LocalPlaylistSongs(
    header: CollectionHeader.OfPlaylist,
    library: LibraryRepository,
    actions: LibraryActions,
    live: Boolean,
    onMessage: (String) -> Unit,
    onBack: () -> Unit,
) {
    val playlist = header.playlist
    val lazyListState = rememberLazyListState()
    val menuState = LocalMenuState.current
    val scope = rememberCoroutineScope()
    val list = rememberCollectionSongs(library, header.ref, onBack)
    val state by list.state.collectAsState()
    val items = state.items
    LoadMoreEffect(list, state, { lazyListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 })

    val collection = actions.collectionActions(header.ref, live)
    val playbackEnabled = live && collection != null
    val shuffle = SongShuffler(enabled = playbackEnabled) { collection?.onShuffle?.invoke() }
    val playNext = PlayNext(enabled = playbackEnabled) { collection?.onPlayNext?.invoke() }
    val enqueue = Enqueue(enabled = playbackEnabled) { collection?.onEnqueue?.invoke() }
    val locator = Locator(lazyListState, { list.state.value.items }, indexOffset = 1) { id ->
        scope.launch { onMessage(getString(id)) }
    }
    val toolbarButtons = buildList<Button> {
        if (collection != null) {
            add(playNext)
            add(enqueue)
        }
    }
    val thumbnails = playlistThumbnails(header.firstTracks ?: items.take(4).takeIf { it.size == 4 }, playlist.artworkTrackId, PLAYLIST_CARD_SIZE_PX)

    Box(
        modifier = Modifier
            .background(colorPalette().background0)
            .fillMaxHeight()
            .fillMaxWidth(),
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val isLandscape = maxWidth > maxHeight
            LazyColumn(
                state = lazyListState,
                modifier = Modifier
                    .background(colorPalette().background0)
                    .fillMaxSize(),
            ) {
                item(
                    key = "header",
                    contentType = 0,
                ) {
                    Column {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth(),
                        ) {
                            HeaderWithIcon(
                                title = playlist.name,
                                iconId = Res.drawable.playlist,
                                enabled = true,
                                showIcon = false,
                                modifier = Modifier
                                    .padding(bottom = 8.dp),
                                onClick = {},
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.Start,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .background(
                                    color = colorPalette().background1,
                                    shape = thumbnailShape(),
                                ),
                        ) {
                            Playlist(
                                name = playlist.name,
                                songCount = state.total ?: playlist.trackCount,
                                thumbnails = thumbnails,
                                thumbnailSizeDp = Dimensions.thumbnails.playlist,
                                alternative = true,
                                showName = false,
                                modifier = Modifier
                                    .padding(top = 14.dp),
                            )

                            Column(
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.Start,
                                modifier = Modifier
                                    .padding(end = 10.dp)
                                    .fillMaxWidth(if (isLandscape) 0.90f else 0.80f),
                            ) {
                                Spacer(modifier = Modifier.height(10.dp))
                                IconInfo(
                                    title = (state.total ?: playlist.trackCount).toString(),
                                    icon = painterResource(Res.drawable.musical_notes),
                                )
                                Spacer(modifier = Modifier.height(5.dp))

                                IconInfo(
                                    title = if (state.endReached) formatAsTime(items.sumOf { it.durationMs ?: 0L }) else "…",
                                    icon = painterResource(Res.drawable.time),
                                )
                                Spacer(modifier = Modifier.height(30.dp))
                            }

                            if (collection != null) {
                                Column(
                                    verticalArrangement = Arrangement.Center,
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Box(
                                        modifier = Modifier.size(48.dp), // Standard IconButton size
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        // isRecommendationEnabled = false (the default)
                                        HeaderIconButton(
                                            icon = Res.drawable.smart_shuffle,
                                            enabled = true,
                                            color = colorPalette().textDisabled,
                                            modifier = Modifier.clip(uiRoundnessShape()),
                                            onClick = {},
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(10.dp))
                                    shuffle.ToolBarButton()
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        TabToolBar.Buttons(toolbarButtons)

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .padding(horizontal = 10.dp)
                                .fillMaxWidth(),
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth(),
                            ) { locator.ToolBarButton() }
                        }
                    }
                }

                itemsIndexed(
                    items = items,
                    key = { index, song -> "$index:${song.id}" },
                ) { index, song ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .zIndex(2f),
                    ) {
                        val menu = actions.trackActions({ list.state.value.items }, index, song.id, live)
                        SongItem(
                            song = song,
                            modifier = Modifier,
                            onLongClick = menu?.let { { menuState.display { SongItemMenu(song, it).MenuComponent() } } },
                            onClick = {
                                if (live && actions.available) actions.playFrom(list.state.value.items, index, song.id)
                            },
                        )
                    }
                }

                item(key = "status") {
                    if (state.endReached && items.isEmpty()) {
                        BasicText(
                            text = stringResource(Res.string.library_empty),
                            style = typography().xs.semiBold.copy(color = colorPalette().textSecondary, textAlign = TextAlign.Center),
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                        )
                    }
                    PagedStatus(state, onRetry = list::retry)
                }

                item(
                    key = "footer",
                    contentType = 0,
                ) {
                    Spacer(modifier = Modifier.height(Dimensions.bottomSpacer))
                }
            }
        }

        FloatingActionsContainerWithScrollToTop(lazyListState = lazyListState)
    }
}
