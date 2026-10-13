package app.n_zik.compagnon.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import app.n_zik.compagnon.bridge.library.AlbumSort
import app.n_zik.compagnon.bridge.library.ArtistSort
import app.n_zik.compagnon.bridge.library.PlaylistSort
import app.n_zik.compagnon.bridge.library.PlaylistSongSort
import app.n_zik.compagnon.bridge.library.SongSort
import app.n_zik.compagnon.components.menu.ListMenu
import app.n_zik.compagnon.components.navigation.header.TabToolBar
import app.n_zik.compagnon.components.tab.toolbar.MenuIcon
import app.n_zik.compagnon.colorPalette
import app.n_zik.compagnon.uiRoundnessShape
import app.n_zik.compagnon.generated.resources.*
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * An entry of a sort menu: the label and the icon of one value of the phone's sort enum, and the
 * value it selects — or `null` when the option has no contract route (it is shown like on the phone,
 * but a click does nothing).
 */
data class SortOption<T>(
    val labelId: StringResource,
    val iconId: DrawableResource,
    val value: T?,
)

/** All 12 options of the phone's `SongSortBy`, all wired (a phone with `library.sort`, since 1.6). */
val songSortOptions = listOf(
    SortOption(Res.string.sort_title, Res.drawable.text, SongSort.Title),
    SortOption(Res.string.sort_artist, Res.drawable.artist, SongSort.Artist),
    SortOption(Res.string.sort_album, Res.drawable.album, SongSort.Album),
    SortOption(Res.string.sort_duration, Res.drawable.time, SongSort.Duration),
    SortOption(Res.string.sort_play_count, Res.drawable.play, SongSort.PlayCount),
    SortOption(Res.string.sort_listening_time, Res.drawable.trending, SongSort.PlayTime),
    SortOption(Res.string.relative_listening_time, Res.drawable.stats_chart, SongSort.RelativePlayTime),
    SortOption(Res.string.sort_date_added, Res.drawable.time, SongSort.DateAdded),
    SortOption(Res.string.sort_date_played, Res.drawable.calendar, SongSort.DatePlayed),
    SortOption(Res.string.sort_date_liked, Res.drawable.heart, SongSort.DateLiked),
    SortOption(Res.string.sort_downloaded, Res.drawable.downloaded, SongSort.Downloaded),
    SortOption(Res.string.sort_custom_order, Res.drawable.position, SongSort.Custom),
)

/**
 * The songs options on a phone without `library.sort` (contract < 1.6): the same 12 entries, but only
 * Title, Artist and PlayTime have a contract route — the rest are shown like on the phone, without effect.
 */
val legacySongSortOptions: List<SortOption<SongSort>> = listOf(
    SortOption(Res.string.sort_title, Res.drawable.text, SongSort.Title),
    SortOption(Res.string.sort_artist, Res.drawable.artist, SongSort.Artist),
    SortOption(Res.string.sort_album, Res.drawable.album, null),
    SortOption(Res.string.sort_duration, Res.drawable.time, null),
    SortOption(Res.string.sort_play_count, Res.drawable.play, null),
    SortOption(Res.string.sort_listening_time, Res.drawable.trending, SongSort.PlayTime),
    SortOption(Res.string.relative_listening_time, Res.drawable.stats_chart, null),
    SortOption(Res.string.sort_date_added, Res.drawable.time, null),
    SortOption(Res.string.sort_date_played, Res.drawable.calendar, null),
    SortOption(Res.string.sort_date_liked, Res.drawable.heart, null),
    SortOption(Res.string.sort_downloaded, Res.drawable.downloaded, null),
    SortOption(Res.string.sort_custom_order, Res.drawable.position, null),
)

/** The phone's `PlaylistSongSortBy` (phone's `app/it/fast4x/rimusic/enums/PlaylistSongSortBy.kt`), in the phone's order. */
val playlistSongSortOptions = listOf(
    SortOption(Res.string.sort_title, Res.drawable.text, PlaylistSongSort.Title),
    SortOption(Res.string.sort_artist, Res.drawable.artist, PlaylistSongSort.Artist),
    SortOption(Res.string.sort_album, Res.drawable.album, PlaylistSongSort.Album),
    SortOption(Res.string.sort_artist_and_album, Res.drawable.artist, PlaylistSongSort.ArtistAndAlbum),
    SortOption(Res.string.sort_duration, Res.drawable.time, PlaylistSongSort.Duration),
    SortOption(Res.string.sort_play_count, Res.drawable.play, PlaylistSongSort.PlayCount),
    SortOption(Res.string.sort_listening_time, Res.drawable.trending, PlaylistSongSort.PlayTime),
    SortOption(Res.string.relative_listening_time, Res.drawable.stats_chart, PlaylistSongSort.RelativePlayTime),
    SortOption(Res.string.sort_date_added, Res.drawable.time, PlaylistSongSort.DateAdded),
    SortOption(Res.string.sort_date_played, Res.drawable.up_right_arrow, PlaylistSongSort.DatePlayed),
    SortOption(Res.string.sort_date_liked, Res.drawable.heart, PlaylistSongSort.DateLiked),
    SortOption(Res.string.sort_album_year, Res.drawable.calendar, PlaylistSongSort.AlbumYear),
    SortOption(Res.string.sort_downloaded, Res.drawable.downloaded, PlaylistSongSort.Downloaded),
    SortOption(Res.string.sort_custom_order, Res.drawable.position, PlaylistSongSort.Custom),
)

/** The phone's `AlbumSortBy` (phone's `app/it/fast4x/rimusic/enums/AlbumSortBy.kt`), in the phone's order. */
val albumSortOptions = listOf(
    SortOption(Res.string.sort_name, Res.drawable.text, AlbumSort.Title),
    SortOption(Res.string.sort_artist, Res.drawable.artist, AlbumSort.Artist),
    SortOption(Res.string.sort_songs_number, Res.drawable.medical, AlbumSort.Songs),
    SortOption(Res.string.sort_duration, Res.drawable.time, AlbumSort.Duration),
    SortOption(Res.string.sort_play_count, Res.drawable.play, AlbumSort.PlayCount),
    SortOption(Res.string.sort_listening_time, Res.drawable.trending, AlbumSort.ListeningTime),
    SortOption(Res.string.sort_date_added, Res.drawable.time, AlbumSort.DateAdded),
    SortOption(Res.string.sort_album_year, Res.drawable.calendar, AlbumSort.Year),
    SortOption(Res.string.sort_custom_order, Res.drawable.position, AlbumSort.Custom),
)

/** The phone's `ArtistSortBy` (phone's `app/it/fast4x/rimusic/enums/ArtistSortBy.kt`), in the phone's order. */
val artistSortOptions = listOf(
    SortOption(Res.string.sort_name, Res.drawable.text, ArtistSort.Name),
    SortOption(Res.string.sort_play_count, Res.drawable.play, ArtistSort.PlayCount),
    SortOption(Res.string.sort_listening_time, Res.drawable.trending, ArtistSort.ListeningTime),
    SortOption(Res.string.sort_date_added, Res.drawable.time, ArtistSort.DateAdded),
    SortOption(Res.string.sort_custom_order, Res.drawable.position, ArtistSort.Custom),
)

/** The phone's `PlaylistSortBy` (phone's `app/it/fast4x/rimusic/enums/PlaylistSortBy.kt`), in the phone's order. */
val playlistSortOptions = listOf(
    SortOption(Res.string.sort_name, Res.drawable.text, PlaylistSort.Name),
    SortOption(Res.string.sort_songs_number, Res.drawable.medical, PlaylistSort.SongCount),
    SortOption(Res.string.sort_listening_time, Res.drawable.trending, PlaylistSort.ListeningTime),
    SortOption(Res.string.sort_play_count, Res.drawable.play, PlaylistSort.PlayCount),
    SortOption(Res.string.sort_date_added, Res.drawable.calendar, PlaylistSort.DateAdded),
    SortOption(Res.string.sort_custom_order, Res.drawable.position, PlaylistSort.Custom),
)

/**
 * The base rotation of the song arrow on a phone without `library.sort`: its contract fixes the order
 * it sends for songs (title and artist A→Z, listening time descending), so PlayTime is already
 * descending before the display's direction. A phone with `library.sort` always starts ascending
 * (rotation 0), whatever the sort.
 */
val SongSort.baseRotation: Float
    get() = if (this == SongSort.PlayTime) 180f else 0f

/**
 * Port of `Sort` (phone's `app/n_zik/android/components/Sort.kt` 52), generic over the tab's sort enum.
 * As on the phone, a short click toggles the direction ([reverse]) and a long click (a right click)
 * opens the sort menu. With a phone that has `library.sort` (contract 1.6) [reverse] is sent to the
 * phone, which re-sorts. On an older phone, for songs the contract fixes the order the phone sends,
 * so the PC shows it reversed client-side ([app.n_zik.compagnon.components.ui.screens.home.LibraryLists]:
 * the arrow flips, the list re-sorts, nothing is sent); for the collections it is inert. Options whose
 * [SortOption.value] is `null` are shown like on the phone, but a click does nothing.
 */
class Sort<T>(
    private val menuState: MenuState,
    private val options: List<SortOption<T>>,
    private val selected: T,
    private val reverse: Boolean,
    private val onSortBy: (T) -> Unit,
    private val onSortDirection: (Boolean) -> Unit,
    private val baseRotation: Float = 0f,
    /**
     * The current sort's label, drawn next to the arrow (the phone's `PlaylistSongsSort` does the
     * same in its `ToolBarButton` override: the selected sort's name, semi-bold, ellipsised). Like
     * the phone's label, a click on it opens the sort menu (the phone's arrow long-click is a no-op;
     * the PC keeps the arrow's right-click as a convenience).
     */
    private val currentLabel: (@Composable () -> Unit)? = null,
) : MenuIcon {

    override val iconId: DrawableResource = Res.drawable.arrow_up
    override val menuIconTitle: String
        @Composable
        get() = stringResource(Res.string.sorting_order)

    override fun onShortClick() = onSortDirection(!reverse)
    override fun onLongClick() = openMenu()

    private fun openMenu() = menuState.display { ListMenu() }

    @Composable
    fun ListMenu() {
        ListMenu.Menu(title = menuIconTitle) {
            options.forEach {
                val isSelected = it.value == selected
                ListMenu.Entry(
                    text = stringResource(it.labelId),
                    icon = {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(
                                    color = if (isSelected) colorPalette().accent.copy(alpha = 0.2f) else colorPalette().accent.copy(alpha = 0.1f),
                                    shape = uiRoundnessShape(),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painter = painterResource(it.iconId),
                                contentDescription = stringResource(it.labelId),
                                tint = colorPalette().accent,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    },
                    modifier = if (isSelected) Modifier.background(colorPalette().accent.copy(alpha = 0.1f), uiRoundnessShape()) else Modifier,
                    trailingContent = {
                        AnimatedVisibility(
                            visible = isSelected,
                            enter = fadeIn() + scaleIn(),
                            exit = fadeOut() + scaleOut(),
                        ) {
                            RadioButton(
                                selected = true,
                                onClick = null,
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = colorPalette().accent,
                                    unselectedColor = colorPalette().textSecondary,
                                ),
                            )
                        }
                    },
                    onClick = {
                        menuState.hide()
                        it.value?.let(onSortBy)
                    },
                )
            }
        }
    }

    @Composable
    override fun ToolBarButton() {
        val animatedArrow by animateFloatAsState(
            targetValue = baseRotation + if (reverse) 180f else 0f,
            animationSpec = tween(durationMillis = 400, easing = LinearEasing),
            label = "",
        )

        TabToolBar.Icon(
            painterResource(iconId),
            color,
            sizeDp,
            isEnabled,
            this.modifier.graphicsLayer { rotationZ = animatedArrow },
            this::onShortClick,
            this::onLongClick,
        )
        // The phone's label opens the sort menu on a click (its `PlaylistSongsSort` label is
        // `.clickable { super.onLongClick() }`)
        currentLabel?.let { label ->
            Box(
                modifier = Modifier
                    .clip(uiRoundnessShape())
                    .clickable { openMenu() },
            ) {
                label()
            }
        }
    }
}
