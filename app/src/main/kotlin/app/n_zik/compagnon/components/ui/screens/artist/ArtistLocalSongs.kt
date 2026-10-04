package app.n_zik.compagnon.components.ui.screens.artist

import app.n_zik.compagnon.generated.resources.share_social
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicText
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
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.n_zik.compagnon.bridge.library.Artist
import app.n_zik.compagnon.bridge.library.ArtistFollow
import app.n_zik.compagnon.bridge.library.DislikeMode
import app.n_zik.compagnon.bridge.library.LibraryRepository
import app.n_zik.compagnon.bridge.library.PagedState
import app.n_zik.compagnon.bridge.library.nextRotation
import app.n_zik.compagnon.bridge.library.nextToggle
import app.n_zik.compagnon.components.LocalMenuState
import app.n_zik.compagnon.components.SongItem
import app.n_zik.compagnon.components.menu.song.SongItemMenu
import app.n_zik.compagnon.components.navigation.header.TabToolBar
import app.n_zik.compagnon.components.styling.Dimensions
import app.n_zik.compagnon.colorPalette
import app.n_zik.compagnon.utils.LocalPreferences
import app.n_zik.compagnon.utils.UserSettings
import app.n_zik.compagnon.utils.Toaster
import app.n_zik.compagnon.utils.semiBold
import app.n_zik.compagnon.typography
import app.n_zik.compagnon.uiRoundnessShape
import app.n_zik.compagnon.components.tab.ShuffleOkFlash
import app.n_zik.compagnon.components.themed.AutoResizeText
import app.n_zik.compagnon.components.themed.FontSizeRange
import app.n_zik.compagnon.components.themed.HeaderIconButton
import app.n_zik.compagnon.components.ui.screens.album.DETAIL_COVER_SIZE_PX
import app.n_zik.compagnon.components.ui.screens.home.CollectionHeader
import app.n_zik.compagnon.components.ui.screens.home.LibraryActions
import app.n_zik.compagnon.components.ui.screens.home.LoadMoreEffect
import app.n_zik.compagnon.components.ui.screens.home.PagedStatus
import app.n_zik.compagnon.components.ui.screens.home.rememberCollectionSongs
import app.n_zik.compagnon.core.coil.ImageCacheFactory
import app.n_zik.compagnon.core.network.ArtworkKey
import app.n_zik.compagnon.generated.resources.Res
import app.n_zik.compagnon.generated.resources.added_to_dislikes
import app.n_zik.compagnon.generated.resources.added_to_favorites
import app.n_zik.compagnon.generated.resources.artist_songs_count_duration
import app.n_zik.compagnon.generated.resources.disliked
import app.n_zik.compagnon.generated.resources.download
import app.n_zik.compagnon.generated.resources.downloaded
import app.n_zik.compagnon.generated.resources.enqueue
import app.n_zik.compagnon.generated.resources.follow
import app.n_zik.compagnon.generated.resources.following
import app.n_zik.compagnon.generated.resources.info_download_all_songs
import app.n_zik.compagnon.generated.resources.info_no_songs_yet
import app.n_zik.compagnon.generated.resources.info_remove_all_downloaded_songs
import app.n_zik.compagnon.generated.resources.removed_from_favorites
import app.n_zik.compagnon.generated.resources.shuffle
import app.n_zik.compagnon.generated.resources.shuffle_ok
import app.n_zik.compagnon.utils.fadingEdge
import app.n_zik.compagnon.utils.formatText
import kotlinx.coroutines.flow.MutableStateFlow
import org.jetbrains.compose.resources.stringResource

/**
 * Port of `ArtistLocalSongs` (phone's `app/it/fast4x/rimusic/ui/screens/artist/ArtistLocalSongs.kt` 127-397),
 * the artist's "Library" tab, with its `ArtistHeader` (703).
 *
 * Kept: the header (full-width 4:3 cover with faded edges in portrait only, `AutoResizeText` name
 * 32–38 sp), "N Songs • duration" (`bodyLarge`), the `SongItem` list and "No songs available".
 * Action row: the phone's Library-tab row (`ArtistLocalSongs.kt` 209-273) in a SpaceEvenly row (12/12/12/8 dp
 * of padding): the follow pill (100 × 32 dp, `FollowButton.kt` 139-181 — "Following" in accent / onAccent
 * when followed, "Disliked" in red / onAccent when disliked, "Follow" in background2 / text otherwise;
 * since 1.7 a tap rotates the state — neutral → followed → disliked → neutral — and writes the phone's
 * target (`library.write`), toasting the phone's `added_to_favorites` / `added_to_dislikes` /
 * `removed_from_favorites` (its `FollowButton.kt` 97-115), while without the feature it stays inert) and,
 * in the phone's order, the
 * download-all and remove-downloads placeholders (their toasts are the phone's long-press hints, on click
 * here) and the enqueue and shuffle buttons wired to the contract (greyed without the `queue` feature or
 * while the list is empty; the app-wide `shuffle_ok` flash, issue #866).
 * Dropped (contract v1 or PC): the "Overview" tab and the subscribers line (the artist's online page),
 * share, swipe actions, the phone's confirmation dialogs (wire 1.7).
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
    val list = rememberCollectionSongs(library, header.ref, onBack)
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
    // The phone's Library-tab action row (`ArtistLocalSongs.kt` 209-273): the follow pill rotates and
    // writes its target state (since 1.7), download all / remove downloads are placeholders (their toasts
    // are the phone's long-press hints, on click here), enqueue / shuffle are wired to the contract
    val playbackEnabled = live && collection != null && songs.isNotEmpty()

    // The follow pill's state, since 1.7: the phone reads its artist live from the DB (`FollowButton.kt`
    // 140-143); here it is seeded from the header and re-synced from the Artists list (the confirmed
    // §10.2 writes patch it), so it tracks the phone's state across the write
    var followState by remember(header.artist.id) {
        mutableStateOf(
            when {
                header.artist.isBookmarked -> ArtistFollow.Followed
                header.artist.isDisliked -> ArtistFollow.Disliked
                else -> ArtistFollow.Neutral
            },
        )
    }
    val artistsState by (actions.lists?.artists?.state ?: remember { MutableStateFlow(PagedState<Artist>()) })
        .collectAsState()
    val listedArtist = artistsState.items.firstOrNull { it.id == header.artist.id }
    LaunchedEffect(listedArtist) {
        listedArtist?.let {
            followState = when {
                it.isBookmarked -> ArtistFollow.Followed
                it.isDisliked -> ArtistFollow.Disliked
                else -> ArtistFollow.Neutral
            }
        }
    }
    val writeEnabled = live && actions.canWrite
    // Since 1.7.2 (feature `library.dislikeMode`): the phone's "disliked" mode off makes the follow a
    // binary toggle (the phone's `toggleBookmark`, its `FollowButton.kt` 74-81); `null` keeps the
    // rotation (the phone's default)
    val dislikeMode by (actions.lists?.dislikeMode ?: remember { MutableStateFlow<DislikeMode?>(null) })
        .collectAsState()
    val followRotationEnabled = dislikeMode?.artists != false

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
                item(key = "action_buttons") {
                    Row(
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 8.dp),
                    ) {
                        // The phone's `FollowButton.ToolBarButton()` (`FollowButton.kt` 139-181): the 100 × 32 dp
                        // pill, "Following" (accent / onAccent) when followed, "Disliked" (red / onAccent) when
                        // disliked, "Follow" (background2 / text) otherwise. A tap rotates the state (neutral →
                        // followed → disliked → neutral, the phone's `rotateLikeState`) or, with the phone's
                        // "disliked" mode off (since 1.7.2), toggles it (the phone's `toggleBookmark`) and
                        // writes the phone's target state (`library.write`, since 1.7); without the feature it
                        // stays inert
                        Box(
                            modifier = Modifier
                                .requiredSize(100.dp, TabToolBar.TOOLBAR_ICON_SIZE)
                                .clip(uiRoundnessShape())
                                .background(
                                    when (followState) {
                                        ArtistFollow.Followed -> colorPalette().accent
                                        ArtistFollow.Disliked -> colorPalette().red
                                        ArtistFollow.Neutral -> colorPalette().background2
                                    },
                                )
                                .clickable(enabled = writeEnabled) {
                                    val target = if (followRotationEnabled) followState.nextRotation() else followState.nextToggle()
                                    followState = target
                                    actions.followArtist(header.artist.id, target)
                                    // The phone's toast (its `FollowButton.kt` 97-115)
                                    followToast(target, header.artist.name)
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            BasicText(
                                text = stringResource(
                                    when (followState) {
                                        ArtistFollow.Followed -> Res.string.following
                                        ArtistFollow.Disliked -> Res.string.disliked
                                        ArtistFollow.Neutral -> Res.string.follow
                                    },
                                ),
                                style = typography().s.copy(
                                    color = when (followState) {
                                        ArtistFollow.Neutral -> colorPalette().text
                                        else -> colorPalette().onAccent
                                    },
                                ),
                            )
                        }

                        // No contract route yet (wire 1.7): the phone's long-press hints, on click here
                        HeaderIconButton(
                            icon = Res.drawable.download,
                            color = colorPalette().text,
                            iconSize = 24.dp,
                            onClick = { Toaster.i(Res.string.info_download_all_songs) },
                        )
                        HeaderIconButton(
                            icon = Res.drawable.downloaded,
                            color = colorPalette().text,
                            iconSize = 24.dp,
                            onClick = { Toaster.i(Res.string.info_remove_all_downloaded_songs) },
                        )
                        HeaderIconButton(
                            icon = Res.drawable.enqueue,
                            color = if (playbackEnabled) colorPalette().text else colorPalette().textDisabled,
                            iconSize = 24.dp,
                            enabled = playbackEnabled,
                            onClick = { collection?.onEnqueue?.invoke() },
                        )
                        HeaderIconButton(
                            // Issue #866: the app-wide shuffle confirmation flash
                            icon = if (ShuffleOkFlash.active) Res.drawable.shuffle_ok else Res.drawable.shuffle,
                            color = if (playbackEnabled) colorPalette().text else colorPalette().textDisabled,
                            iconSize = 24.dp,
                            enabled = playbackEnabled,
                            onClick = {
                                ShuffleOkFlash.trigger()
                                collection?.onShuffle?.invoke()
                            },
                        )
                    }
                }
                item {
                    if (songCount > 0) {
                        Text(
                            text = formatText(stringResource(Res.string.artist_songs_count_duration), songCount, totalDurationText),
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
                            val menu = actions.trackActions(list.state, index, song.id, live)
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
    }
}

/** Port of `ArtistHeader` (`ArtistLocalSongs.kt` 703). */
@Composable
fun ArtistHeader(artist: Artist, isLandscape: Boolean) {
    // The PC's "Disable scrolling text" (the phone's `disableScrollingTextKey`, its
    // `ArtistLocalSongs.kt` 709, 741): the name's marquee is dropped when set
    val preferences = LocalPreferences.current
    val settings by (preferences?.settings ?: remember { MutableStateFlow(UserSettings()) }).collectAsState()

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
                    .then(if (settings.disableScrollingText) Modifier else Modifier.basicMarquee(iterations = Int.MAX_VALUE))
                    .align(Alignment.CenterHorizontally),
            )
            // `artistPage?.subscribers`: no artist page for a local artist (phone's 744-748)
            BasicText(
                text = "",
                style = typography().s.copy(colorPalette().textSecondary),
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }

        HeaderIconButton(
            icon = Res.drawable.share_social,
            color = colorPalette().text,
            iconSize = 24.dp,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 5.dp, end = 5.dp),
            onClick = {},
        )
    }
}

/**
 * The phone's toast of a follow/like state change (its `FollowButton.kt` 97-115, the same messages as
 * the artist menu's bookmark, its `LocalArtistItemMenu.kt` 274-292): the artist's name, when it has
 * one, in the phone's quoted-argument form.
 */
internal fun followToast(target: ArtistFollow, name: String) {
    val messageId = when (target) {
        ArtistFollow.Followed -> Res.string.added_to_favorites
        ArtistFollow.Disliked -> Res.string.added_to_dislikes
        ArtistFollow.Neutral -> Res.string.removed_from_favorites
    }
    if (name.isNotBlank()) Toaster.s(messageId, "\"$name\"") else Toaster.s(messageId)
}
