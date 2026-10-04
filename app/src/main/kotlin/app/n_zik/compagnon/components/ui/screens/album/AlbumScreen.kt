package app.n_zik.compagnon.components.ui.screens.album

import app.n_zik.compagnon.components.tab.toolbar.InertButton
import app.n_zik.compagnon.components.tab.toolbar.MenuIcon
import app.n_zik.compagnon.generated.resources.bookmark_outline
import app.n_zik.compagnon.generated.resources.bookmark
import app.n_zik.compagnon.generated.resources.bookmark_slash
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.n_zik.compagnon.bridge.library.AlbumLike
import app.n_zik.compagnon.bridge.library.DislikeMode
import app.n_zik.compagnon.bridge.library.LibraryRepository
import app.n_zik.compagnon.bridge.library.nextRotation
import app.n_zik.compagnon.bridge.library.nextToggle
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
import app.n_zik.compagnon.utils.Toaster
import app.n_zik.compagnon.generated.resources.Res
import app.n_zik.compagnon.generated.resources.add_in_playlist
import app.n_zik.compagnon.generated.resources.added_to_dislikes
import app.n_zik.compagnon.generated.resources.added_to_favorites
import app.n_zik.compagnon.generated.resources.removed_from_favorites
import app.n_zik.compagnon.generated.resources.add_to_playlist
import app.n_zik.compagnon.generated.resources.artists_edit
import app.n_zik.compagnon.generated.resources.unchecked_outline
import app.n_zik.compagnon.generated.resources.cover_edit
import app.n_zik.compagnon.generated.resources.download
import app.n_zik.compagnon.generated.resources.downloaded
import app.n_zik.compagnon.generated.resources.enqueue
import app.n_zik.compagnon.generated.resources.info_bookmark_album
import app.n_zik.compagnon.generated.resources.info_remove_all_downloaded_songs
import app.n_zik.compagnon.generated.resources.info_shuffle
import app.n_zik.compagnon.generated.resources.item_select
import app.n_zik.compagnon.generated.resources.library_empty
import app.n_zik.compagnon.generated.resources.play_next
import app.n_zik.compagnon.generated.resources.play_skip_forward
import app.n_zik.compagnon.generated.resources.radio
import app.n_zik.compagnon.generated.resources.shuffle
import app.n_zik.compagnon.generated.resources.songs
import app.n_zik.compagnon.generated.resources.start_radio
import app.n_zik.compagnon.generated.resources.title_edit
import app.n_zik.compagnon.generated.resources.update_authors
import app.n_zik.compagnon.generated.resources.update_cover
import app.n_zik.compagnon.generated.resources.update_title
import app.n_zik.compagnon.utils.align
import app.n_zik.compagnon.utils.center
import app.n_zik.compagnon.utils.color
import app.n_zik.compagnon.utils.fadingEdge
import app.n_zik.compagnon.utils.formatAsTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.DrawableResource
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
 * Toolbar: the phone's full order — shuffle, locator, play next and enqueue (whole album) are wired to the
 * contract, the rest (download all / delete downloads, radio, item selector, edit title / authors / cover,
 * add to playlist) are placeholders without a contract route, a click does nothing. The bookmark button
 * then 15 dp before the toolbar (phone's `AlbumDetails.kt` 89-175): the phone's tri-state — `bookmark`
 * in accent, `bookmark_slash` in red for a disliked album, `bookmark_outline` in text — since 1.7.2
 * written with its `POST /library/albums/{id}/like`: the phone's "disliked" mode on (feature
 * `library.dislikeMode`) rotating (its `rotateLikeState`), off toggling (its `toggleBookmark`), toasting
 * the phone's `added_to_favorites` / `added_to_dislikes` / `removed_from_favorites` (its
 * `AlbumDetails.kt` 155-172); a phone before 1.7.2 keeps its 1.7 binary route; inert outside a `Live`
 * session, and the share icon at the top end of the cover (no action). While the first page is loading the
 * centred [Loader] replaces the list (`AlbumScreen.kt` 589-603).
 * Dropped (contract v1 or PC): the MusicBrainz "Info and community" block with
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
    // The phone's header bookmark (phone's `AlbumDetails.kt` 89-175): the phone's tri-state —
    // `bookmark` in accent, `bookmark_slash` in red for a disliked album, `bookmark_outline` in text —
    // since 1.7.2 written with its `POST /library/albums/{id}/like`, the phone's "disliked" mode on
    // (feature `library.dislikeMode`) rotating (its `rotateLikeState`), off toggling (its `toggleBookmark`);
    // a phone before 1.7.2 keeps its 1.7 binary route — labelled `info_bookmark_album`; inert outside
    // a `Live` session
    var likeState by remember(album.id) {
        mutableStateOf(
            when {
                album.isDisliked -> AlbumLike.Disliked
                album.isBookmarked -> AlbumLike.Bookmarked
                else -> AlbumLike.Neutral
            },
        )
    }
    LaunchedEffect(album.id, album.isBookmarked, album.isDisliked) {
        likeState = when {
            album.isDisliked -> AlbumLike.Disliked
            album.isBookmarked -> AlbumLike.Bookmarked
            else -> AlbumLike.Neutral
        }
    }
    val dislikeMode by (actions.lists?.dislikeMode ?: remember { MutableStateFlow<DislikeMode?>(null) })
        .collectAsState()
    val bookmark = object : MenuIcon {
        override val iconId: DrawableResource = when (likeState) {
            AlbumLike.Bookmarked -> Res.drawable.bookmark
            AlbumLike.Disliked -> Res.drawable.bookmark_slash
            AlbumLike.Neutral -> Res.drawable.bookmark_outline
        }
        override val color: Color
            @Composable
            get() = when (likeState) {
                AlbumLike.Bookmarked -> colorPalette().accent
                AlbumLike.Disliked -> colorPalette().red
                AlbumLike.Neutral -> colorPalette().text
            }
        override val menuIconTitle: String
            @Composable
            get() = stringResource(Res.string.info_bookmark_album)
        override fun onShortClick() {
            if (live && actions.canWrite) {
                // Since 1.7.2: the tri-state write, rotation or toggle per the phone's "disliked" mode;
                // a phone before 1.7.2 keeps its binary route
                val mode = dislikeMode
                val target = if (mode == null) {
                    likeState.nextToggle()
                } else if (mode.albums) {
                    likeState.nextRotation()
                } else {
                    likeState.nextToggle()
                }
                likeState = target
                if (mode == null) {
                    actions.bookmarkAlbum(album.id, target == AlbumLike.Bookmarked)
                } else {
                    actions.likeAlbum(album.id, target)
                }
                // The phone's toast (its `AlbumDetails.kt` 155-172)
                val messageId = when (target) {
                    AlbumLike.Bookmarked -> Res.string.added_to_favorites
                    AlbumLike.Disliked -> Res.string.added_to_dislikes
                    AlbumLike.Neutral -> Res.string.removed_from_favorites
                }
                if (album.title.isNotBlank()) Toaster.s(messageId, "\"${album.title}\"") else Toaster.s(messageId)
            }
        }
    }
    // The phone's toolbar order; shuffle, locator, play next and enqueue are wired to the contract,
    // the rest are placeholders without a contract route (no action)
    val toolbar = buildList<Button> {
        add(InertButton(Res.drawable.downloaded, Res.string.download))
        add(InertButton(Res.drawable.download, Res.string.info_remove_all_downloaded_songs))
        if (collection != null) add(shuffle) else add(InertButton(Res.drawable.shuffle, Res.string.info_shuffle))
        add(InertButton(Res.drawable.radio, Res.string.start_radio))
        add(locator)
        add(InertButton(Res.drawable.unchecked_outline, Res.string.item_select))
        add(InertButton(Res.drawable.title_edit, Res.string.update_title))
        add(InertButton(Res.drawable.artists_edit, Res.string.update_authors))
        add(InertButton(Res.drawable.cover_edit, Res.string.update_cover))
        if (collection != null) {
            add(playNext)
            add(enqueue)
        } else {
            add(InertButton(Res.drawable.play_skip_forward, Res.string.play_next))
            add(InertButton(Res.drawable.enqueue, Res.string.enqueue))
        }
        add(InertButton(Res.drawable.add_in_playlist, Res.string.add_to_playlist))
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
                        val menu = actions.trackActions(list.state, index, song.id, live)
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
