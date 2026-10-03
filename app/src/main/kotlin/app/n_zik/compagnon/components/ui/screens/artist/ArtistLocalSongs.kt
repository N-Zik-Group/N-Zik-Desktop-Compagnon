package app.n_zik.compagnon.components.ui.screens.artist

import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.n_zik.compagnon.bridge.library.Artist
import app.n_zik.compagnon.bridge.library.LibraryRepository
import app.n_zik.compagnon.components.LocalMenuState
import app.n_zik.compagnon.components.SongItem
import app.n_zik.compagnon.components.menu.song.SongItemMenu
import app.n_zik.compagnon.components.styling.Dimensions
import app.n_zik.compagnon.colorPalette
import app.n_zik.compagnon.utils.semiBold
import app.n_zik.compagnon.typography
import app.n_zik.compagnon.uiRoundnessShape
import app.n_zik.compagnon.components.themed.AutoResizeText
import app.n_zik.compagnon.components.themed.FontSizeRange
import app.n_zik.compagnon.components.themed.HeaderIconButton
import app.n_zik.compagnon.components.ui.screens.album.DETAIL_COVER_SIZE_PX
import app.n_zik.compagnon.components.ui.screens.home.BackButton
import app.n_zik.compagnon.components.ui.screens.home.CollectionHeader
import app.n_zik.compagnon.components.ui.screens.home.LibraryActions
import app.n_zik.compagnon.components.ui.screens.home.LoadMoreEffect
import app.n_zik.compagnon.components.ui.screens.home.PagedStatus
import app.n_zik.compagnon.components.ui.screens.home.rememberCollectionSongs
import app.n_zik.compagnon.core.coil.ImageCacheFactory
import app.n_zik.compagnon.core.network.ArtworkKey
import app.n_zik.compagnon.generated.resources.Res
import app.n_zik.compagnon.generated.resources.artist_songs_count_duration
import app.n_zik.compagnon.generated.resources.enqueue
import app.n_zik.compagnon.generated.resources.info_no_songs_yet
import app.n_zik.compagnon.generated.resources.shuffle
import app.n_zik.compagnon.utils.fadingEdge
import org.jetbrains.compose.resources.stringResource

/**
 * Port of `ArtistLocalSongs` (phone's `app/it/fast4x/rimusic/ui/screens/artist/ArtistLocalSongs.kt` 127-397),
 * the artist's "Library" tab, with its `ArtistHeader` (703).
 *
 * Kept: the header (full-width 4:3 cover with faded edges in portrait only, `AutoResizeText` name
 * 32–38 sp), the row of 24 dp header buttons (enqueue, shuffle; greyed when there is no track),
 * "N Songs • duration" (`bodyLarge`), the `SongItem` list and "No songs available".
 * Dropped (contract v1 or PC): follow, download all, delete downloads (and their dialogs), share, the
 * subscribers line (online page), swipe actions, the long-press hints (toasts).
 * Added by the Compagnon: the back arrow, the paging row. The duration shows once all tracks are loaded.
 */
@Composable
fun ArtistLocalSongs(
    header: CollectionHeader.OfArtist,
    library: LibraryRepository,
    actions: LibraryActions,
    live: Boolean,
    onMessage: (String) -> Unit,
    onBack: () -> Unit,
) {
    val lazyListState = rememberLazyListState()
    val menuState = LocalMenuState.current
    val list = rememberCollectionSongs(library, header.ref, onMessage, onBack)
    val state by list.state.collectAsState()
    val songs = state.items
    LoadMoreEffect(list, state, { lazyListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 })

    val songCount = state.total ?: songs.size
    val totalDuration = if (state.endReached) (songs.sumOf { it.durationMs ?: 0L } / 1_000).toInt() else 0
    val totalDurationText = if (totalDuration > 0) {
        val hours = totalDuration / 3600
        val minutes = (totalDuration % 3600) / 60
        val seconds = totalDuration % 60
        if (hours > 0) "%dh %02dm %02ds".format(hours, minutes, seconds) else "%dm %02ds".format(minutes, seconds)
    } else {
        ""
    }
    val collection = actions.collectionActions(header.ref, live)
    val hasSongs = songs.isNotEmpty() && collection != null && collection.enabled

    Box(Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val isLandscape = maxWidth > maxHeight
            LazyColumn(
                state = lazyListState,
                contentPadding = PaddingValues(bottom = Dimensions.bottomSpacer),
                modifier = Modifier.fillMaxSize(),
            ) {
                item {
                    ArtistHeader(header.artist, isLandscape)
                }
                if (collection != null) {
                    item {
                        Row(
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 8.dp),
                        ) {
                            HeaderIconButton(
                                icon = Res.drawable.enqueue,
                                enabled = hasSongs,
                                color = if (hasSongs) colorPalette().text else colorPalette().textDisabled,
                                iconSize = 24.dp,
                                modifier = Modifier.clip(uiRoundnessShape()),
                                onClick = collection.onEnqueue,
                            )
                            HeaderIconButton(
                                icon = Res.drawable.shuffle,
                                enabled = hasSongs,
                                color = if (hasSongs) colorPalette().text else colorPalette().textDisabled,
                                iconSize = 24.dp,
                                modifier = Modifier.clip(uiRoundnessShape()),
                                onClick = { collection.onShuffle?.invoke() },
                            )
                        }
                    }
                }
                item {
                    if (songCount > 0) {
                        Text(
                            text = stringResource(Res.string.artist_songs_count_duration, songCount, totalDurationText),
                            style = MaterialTheme.typography.bodyLarge,
                            color = colorPalette().text,
                            modifier = Modifier
                                .padding(top = 12.dp, bottom = 5.dp)
                                .fillMaxWidth()
                                .wrapContentWidth(Alignment.CenterHorizontally),
                        )
                    }
                }
                if (songs.isEmpty() && state.endReached) {
                    item(key = "empty") {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = stringResource(Res.string.info_no_songs_yet),
                                style = MaterialTheme.typography.bodyLarge,
                                color = colorPalette().textSecondary,
                                modifier = Modifier.align(Alignment.Center),
                            )
                        }
                    }
                } else {
                    itemsIndexed(
                        items = songs,
                        key = { index, song -> "$index:${song.id}" },
                    ) { index, song ->
                        Box(
                            Modifier
                                .fillMaxWidth(),
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
                }
                item(key = "status") {
                    PagedStatus(state, onRetry = list::retry)
                }
            }
        }
        BackButton(onBack)
    }
}

/** Port of `ArtistHeader` (`ArtistLocalSongs.kt` 703). */
@Composable
fun ArtistHeader(artist: Artist, isLandscape: Boolean) {
    Box(Modifier.fillMaxWidth()) {
        if (!isLandscape) {
            ImageCacheFactory.Thumbnail(
                key = if (artist.hasArtwork) ArtworkKey.artist(artist.id, DETAIL_COVER_SIZE_PX) else null,
                contentDescription = null,
                contentScale = ContentScale.FillWidth,
                modifier = Modifier
                    .aspectRatio(4f / 3)
                    .fillMaxWidth()
                    .align(Alignment.Center)
                    .fadingEdge(
                        top = Dimensions.fadeSpacingTop,
                        bottom = Dimensions.fadeSpacingBottom,
                    ),
            )
        }

        Column(Modifier.align(Alignment.BottomCenter)) {
            AutoResizeText(
                text = artist.name.ifBlank { "..." },
                style = typography().l.semiBold,
                fontSizeRange = FontSizeRange(32.sp, 38.sp),
                fontWeight = typography().l.semiBold.fontWeight,
                fontFamily = typography().l.semiBold.fontFamily,
                color = typography().l.semiBold.color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(horizontal = 30.dp)
                    .basicMarquee(iterations = Int.MAX_VALUE)
                    .align(Alignment.CenterHorizontally),
            )
        }
    }
}
