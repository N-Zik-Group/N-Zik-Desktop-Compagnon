package app.n_zik.compagnon.components.ui.screens.album

import app.n_zik.compagnon.components.tab.toolbar.InertButton
import app.n_zik.compagnon.generated.resources.bookmark_outline
import app.n_zik.compagnon.generated.resources.bookmark
import app.n_zik.compagnon.generated.resources.share_social
import app.n_zik.compagnon.components.themed.HeaderIconButton
import app.n_zik.compagnon.components.themed.Loader
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.n_zik.compagnon.bridge.library.LibraryRepository
import app.n_zik.compagnon.components.LocalMenuState
import app.n_zik.compagnon.components.SongItem
import app.n_zik.compagnon.components.menu.song.SongItemMenu
import app.n_zik.compagnon.components.navigation.header.TabToolBar
import app.n_zik.compagnon.components.styling.Dimensions
import app.n_zik.compagnon.components.tab.Locator
import app.n_zik.compagnon.components.tab.SongShuffler
import app.n_zik.compagnon.components.tab.toolbar.Button
import app.n_zik.compagnon.colorPalette
import app.n_zik.compagnon.utils.medium
import app.n_zik.compagnon.utils.semiBold
import app.n_zik.compagnon.typography
import app.n_zik.compagnon.components.themed.AutoResizeText
import app.n_zik.compagnon.components.themed.Enqueue
import app.n_zik.compagnon.components.themed.FontSizeRange
import app.n_zik.compagnon.components.themed.PlayNext
import app.n_zik.compagnon.components.ui.screens.DynamicOrientationLayout
import app.n_zik.compagnon.components.ui.screens.home.CollectionHeader
import app.n_zik.compagnon.components.ui.screens.home.LibraryActions
import app.n_zik.compagnon.components.ui.screens.home.LoadMoreEffect
import app.n_zik.compagnon.components.ui.screens.home.PagedStatus
import app.n_zik.compagnon.components.ui.screens.home.rememberCollectionSongs
import app.n_zik.compagnon.core.coil.ImageCacheFactory
import app.n_zik.compagnon.core.network.ArtworkKey
import app.n_zik.compagnon.generated.resources.Res
import app.n_zik.compagnon.generated.resources.library_empty
import app.n_zik.compagnon.generated.resources.songs
import app.n_zik.compagnon.utils.align
import app.n_zik.compagnon.utils.center
import app.n_zik.compagnon.utils.color
import app.n_zik.compagnon.utils.fadingEdge
import app.n_zik.compagnon.utils.formatAsTime
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

/** The 4:3 cover of a detail screen fills the width: the largest size the contract allows. */
const val DETAIL_COVER_SIZE_PX = 1200

/**
 * Port of `AlbumDetails` (phone's `app/n_zik/android/components/ui/screens/album/AlbumScreen.kt` 420-821).
 *
 * Kept: `DynamicOrientationLayout`; the header item (full-width 4:3 cover, faded at the top by
 * `fadeSpacingTop` and at the bottom by `fadeSpacingBottom`, the `AutoResizeText` title 32–38 sp centred
 * over its bottom, in portrait only like the phone), "N Songs - duration" (`xs` medium), the centred row of
 * actions (`TabToolBar` at 80 % of the width), the "Songs" section title (`m` semi-bold, 16 dp sides,
 * 24 dp above, 8 dp below) and `SongItem` without thumbnail, numbered `index + 1` (`s` semi-bold
 * `textDisabled`, centred on 54 dp).
 * Actions kept from the phone's toolbar: shuffle, locator, play next, enqueue (whole album). The bookmark
 * button then 15 dp before the toolbar (`AlbumScreen.kt` 676-678: `bookmark` in accent when the album is
 * bookmarked, contract 1.3 `isBookmarked`, `bookmark_outline` in text otherwise; no contract route, no
 * action) and the share icon at the top end of the cover (no action). While the first page is loading the
 * centred [Loader] replaces the list (`AlbumScreen.kt` 589-603).
 * Dropped (contract v1 or PC): download all / delete downloads, radio, multi-selection,
 * edit title / authors / cover, add to playlist, the MusicBrainz "Info and community" block with
 * translation, the alternative versions, swipe actions, the floating shuffle icon.
 * Added by the Compagnon: the paging row (loading, error + "Retry"), "Nothing here." for an empty album.
 * The duration shows once all tracks are loaded (the contract gives no total duration).
 */
@Composable
fun AlbumDetails(
    header: CollectionHeader.OfAlbum,
    library: LibraryRepository,
    actions: LibraryActions,
    live: Boolean,
    onMessage: (String) -> Unit,
    onBack: () -> Unit,
) {
    val album = header.album
    val lazyListState = rememberLazyListState()
    val menuState = LocalMenuState.current
    val scope = rememberCoroutineScope()
    val list = rememberCollectionSongs(library, header.ref, onBack)
    val state by list.state.collectAsState()
    val items = state.items
    LoadMoreEffect(list, state, { lazyListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 })

    val thumbnailSizeDp = Dimensions.thumbnails.song
    val cover = if (album.hasArtwork) ArtworkKey.album(album.id, DETAIL_COVER_SIZE_PX) else null
    val collection = actions.collectionActions(header.ref, live)
    val playbackEnabled = live && collection != null

    val shuffle = SongShuffler(enabled = playbackEnabled) { collection?.onShuffle?.invoke() }
    val locator = Locator(lazyListState, { list.state.value.items }, indexOffset = ITEMS_BEFORE_SONGS) { id ->
        scope.launch { onMessage(getString(id)) }
    }
    val playNext = PlayNext(enabled = playbackEnabled) { collection?.onPlayNext?.invoke() }
    val enqueue = Enqueue(enabled = playbackEnabled) { collection?.onEnqueue?.invoke() }
    val bookmark = InertButton(
        iconId = if (album.isBookmarked) Res.drawable.bookmark else Res.drawable.bookmark_outline,
        tint = if (album.isBookmarked) colorPalette().accent else colorPalette().text,
    )
    val toolbar = buildList<Button> {
        if (collection != null) add(shuffle)
        add(locator)
        if (collection != null) {
            add(playNext)
            add(enqueue)
        }
    }

    val sectionTextModifier = Modifier
        .padding(horizontal = 16.dp)
        .padding(top = 24.dp, bottom = 8.dp)

    Box(Modifier.fillMaxSize()) {
        DynamicOrientationLayout(cover) { isLandscape ->
            Box(
                Modifier.fillMaxSize()
                    .background(colorPalette().background0),
                contentAlignment = Alignment.Center,
            ) {
              if (items.isEmpty() && !state.endReached && state.error == null) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Loader()
                }
              } else {
                LazyColumn(
                    state = lazyListState,
                    contentPadding = PaddingValues(bottom = Dimensions.bottomSpacer),
                    modifier = Modifier.fillMaxSize()
                        .background(colorPalette().background0),
                ) {
                    item("header") {
                        Box(Modifier.fillMaxWidth()) {
                            if (!isLandscape) {
                                ImageCacheFactory.Thumbnail(
                                    key = cover,
                                    contentDescription = null,
                                    contentScale = ContentScale.FillWidth,
                                    modifier = Modifier.aspectRatio(4f / 3)
                                        .fillMaxWidth()
                                        .align(Alignment.Center)
                                        .fadingEdge(
                                            top = Dimensions.fadeSpacingTop,
                                            bottom = Dimensions.fadeSpacingBottom,
                                        ),
                                )
                            }

                            AutoResizeText(
                                text = album.title.ifBlank { "..." },
                                style = typography().l.semiBold,
                                fontSizeRange = FontSizeRange(32.sp, 38.sp),
                                fontWeight = typography().l.semiBold.fontWeight,
                                fontFamily = typography().l.semiBold.fontFamily,
                                color = typography().l.semiBold.color,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.align(Alignment.BottomCenter)
                                    .padding(horizontal = 30.dp)
                                    .basicMarquee(iterations = Int.MAX_VALUE),
                            )

                            HeaderIconButton(
                                icon = Res.drawable.share_social,
                                color = colorPalette().text,
                                iconSize = 24.dp,
                                modifier = Modifier.align(Alignment.TopEnd)
                                    .padding(top = 5.dp, end = 5.dp),
                                onClick = {},
                            )
                        }
                    }

                    item("album_details") {
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            val songCount = "${state.total ?: items.size} ${stringResource(Res.string.songs)}"
                            val totalDuration = if (state.endReached) formatAsTime(items.sumOf { it.durationMs ?: 0L }) else "…"

                            BasicText(
                                text = "$songCount - $totalDuration",
                                style = typography().xs.medium,
                                maxLines = 1,
                            )
                        }
                    }

                    item("action_buttons") {
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            bookmark.ToolBarButton()

                            Spacer(Modifier.width(15.dp))

                            TabToolBar.Buttons(toolbar, modifier = Modifier.fillMaxWidth(.8f))
                        }
                    }

                    item("songsTitle") {
                        BasicText(
                            text = stringResource(Res.string.songs),
                            style = typography().m.semiBold.align(TextAlign.Start),
                            modifier = sectionTextModifier.fillMaxWidth(),
                        )
                    }

                    itemsIndexed(
                        items = items,
                        key = { index, song -> "$index:${song.id}" },
                    ) { index, song ->
                        val menu = actions.trackActions({ list.state.value.items }, index, song.id, live)
                        SongItem(
                            song = song,
                            showThumbnail = false,
                            modifier = Modifier,
                            onLongClick = menu?.let { { menuState.display { SongItemMenu(song, it).MenuComponent() } } },
                            thumbnailOverlay = {
                                BasicText(
                                    text = "${index + 1}",
                                    style = typography().s
                                        .semiBold
                                        .center
                                        .color(
                                            colorPalette().textDisabled,
                                        ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .width(thumbnailSizeDp)
                                        .align(Alignment.Center),
                                )
                            },
                            onClick = {
                                if (live && actions.available) actions.playFrom(list.state.value.items, index, song.id)
                            },
                        )
                    }

                    item("status") {
                        if (state.endReached && items.isEmpty()) {
                            BasicText(
                                text = stringResource(Res.string.library_empty),
                                style = typography().xs.semiBold.copy(color = colorPalette().textSecondary, textAlign = TextAlign.Center),
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                            )
                        }
                        PagedStatus(state, onRetry = list::retry)
                    }
                }
              }
            }
        }
    }
}

/** Header, details, actions and section title come before the first track. */
private const val ITEMS_BEFORE_SONGS = 4

/** Port of the album route (`AlbumScreen`): here only its `AlbumDetails`. */
@Composable
fun AlbumScreen(
    header: CollectionHeader.OfAlbum,
    library: LibraryRepository,
    actions: LibraryActions,
    live: Boolean,
    onMessage: (String) -> Unit,
    onBack: () -> Unit,
) = AlbumDetails(header, library, actions, live, onMessage, onBack)
