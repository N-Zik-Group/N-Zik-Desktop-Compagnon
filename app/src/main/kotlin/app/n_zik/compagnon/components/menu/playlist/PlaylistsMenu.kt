package app.n_zik.compagnon.components.menu.playlist

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.unit.dp
import app.n_zik.compagnon.LocalLibraryActions
import app.n_zik.compagnon.bridge.library.Playlist
import app.n_zik.compagnon.bridge.library.PlaylistOrigin
import app.n_zik.compagnon.bridge.library.PagedState
import app.n_zik.compagnon.colorPalette
import app.n_zik.compagnon.components.LocalMenuState
import app.n_zik.compagnon.components.menu.ListMenu
import app.n_zik.compagnon.components.themed.IconButton
import app.n_zik.compagnon.generated.resources.*
import app.n_zik.compagnon.typography
import app.n_zik.compagnon.uiRoundnessShape
import app.n_zik.compagnon.utils.PINNED_PREFIX
import app.n_zik.compagnon.utils.semiBold
import kotlinx.coroutines.flow.MutableStateFlow
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Port of `PlaylistsMenu` (phone's `app/it/fast4x/rimusic/ui/components/themed/PlaylistsMenu.kt`
 * 130-258: the cards 130-162, the rendering 199-258, its header 199-228) — the phone's "Add to
 * playlist" sheet, opened by every `add_to_playlist` entry (spec `spec-fill-pc-ui-gaps-menus` #47).
 *
 * The phone adds the chosen song to the tapped playlist in its own database (its `onAdd`, 98-107):
 * contract §10.2 has no add-to-playlist route, so a playlist tap is inert here. The playlists are the
 * list the PC already reads (`playlists`), pinned first (the phone's `PINNED_PREFIX` section, its 182-184,
 * 230-242) then the rest — the phone's generated rewind and legacy `monthly:` playlists are never
 * add-to targets (its 185-190) — in the phone's card design (name, "N songs", its `ytmusic` badge on a
 * bookmarked playlist, its "open" button).
 *
 * Wired: only the header's back (the phone's 206-213) — [MenuState.pop]. Inert (spec #47, no contract
 * route): the search toggle (the phone's 214-221 — its client-side name filter is not ported, the list
 * is always the whole read list), "New playlist" (the phone's `NewPlaylistDialog` toolbar button, its
 * 196-197, 227) and every card's click and "open" button (the phone's 137-139, 151-159).
 *
 * The phone's grid variant (its `GridMenu`, off by default) is not ported: the PC menus are always in
 * the phone's default list style, as [ListMenu].
 */
@Composable
fun PlaylistsMenu() {
    val menuState = LocalMenuState.current
    val playlists by (LocalLibraryActions.current?.lists?.playlists?.state
        ?: remember { MutableStateFlow(PagedState<Playlist>()) }).collectAsState()
    val items = playlists.items

    // The phone's split (its 182-190), extracted as a pure function (spec
    // `spec-fill-pc-ui-gaps-menus`, matrix "Sous-menu Ajouter à une playlist"): pinned first, then
    // the rest minus the generated rewind and legacy `monthly:` playlists (never add-to targets)
    val (pinnedPlaylists, unpinnedPlaylists) = playlistsAddToSections(items)

    ListMenu.Menu(title = null, showDragHandle = false) {
        // The phone's header (its 199-228)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .fillMaxWidth(),
        ) {
            // The phone's back (its 206-213): back to the menu that opened this layer
            IconButton(
                onClick = { menuState.pop() },
                icon = Res.drawable.chevron_back,
                color = colorPalette().accent,
                modifier = Modifier
                    .padding(all = 4.dp)
                    .size(20.dp),
            )
            // Spec #47: the phone's search toggle (its 214-221): inert — the phone's client-side name
            // filter is not ported, a click does nothing
            IconButton(
                onClick = {},
                icon = Res.drawable.search_circle,
                color = colorPalette().accent,
                modifier = Modifier
                    .padding(all = 4.dp)
                    .size(24.dp),
            )
            BasicText(
                text = stringResource(Res.string.playlists),
                style = typography().m.semiBold,
                modifier = Modifier.weight(1f).padding(start = 8.dp),
            )
            // Spec #47: the phone's "New playlist" toolbar button (its `NewPlaylistDialog`, 196-197,
            // 227): inert — no playlist creation on the PC, no contract route
            IconButton(
                onClick = {},
                icon = Res.drawable.add_in_playlist,
                color = colorPalette().accent,
                modifier = Modifier
                    .padding(all = 4.dp)
                    .size(24.dp),
            )
        }
        if (pinnedPlaylists.isNotEmpty()) {
            // The phone's pinned section (its 230-242): its `pinned_playlists` title
            ListMenu.SectionTitle(stringResource(Res.string.pinned_playlists))
            pinnedPlaylists.forEach { PlaylistCard(it) }
        }
        if (unpinnedPlaylists.isNotEmpty()) {
            // The phone's playlists section (its 244-256)
            ListMenu.SectionTitle(stringResource(Res.string.playlists))
            unpinnedPlaylists.forEach { PlaylistCard(it) }
        }
    }
}

/**
 * The phone's split (its `PlaylistsMenu.kt` 182-190): the pinned section (its 182-184 — the phone's
 * name-prefix check, carried on the wire as [Playlist.isPinned] since 1.7) first, then the rest
 * minus the generated rewind and legacy `monthly:` playlists, which are never add-to targets (its
 * 185-190) — extracted as a pure function so the sub-menu's list is testable (spec
 * `spec-fill-pc-ui-gaps-menus` #47, matrix row "Sous-menu Ajouter à une playlist").
 */
internal fun playlistsAddToSections(playlists: List<Playlist>): Pair<List<Playlist>, List<Playlist>> {
    val pinned = playlists.filter { it.isPinned }
    val rest = playlists.filter {
        !it.isPinned && it.origin !in REWIND_ORIGINS && it.origin != PlaylistOrigin.Monthly
    }
    return pinned to rest
}

/**
 * The phone's `PlaylistCard` (its 130-162): the name (the `pinned:` prefix stripped, its 134), the
 * phone's `SettingIcon` of `add_in_playlist`, "N songs", and trailing the phone's `ytmusic` badge on a
 * bookmarked playlist (its 141-150) and its "open" button (its 151-159) — the card's click and the
 * "open" button are inert (spec #47, no contract route).
 */
@Composable
private fun PlaylistCard(playlist: Playlist) {
    ListMenu.Entry(
        text = playlist.name.substringAfter(PINNED_PREFIX, playlist.name),
        icon = { PlaylistSettingIcon() },
        subtitle = "${playlist.trackCount} ${stringResource(Res.string.songs)}",
        // Spec #47: inert — the phone adds the song in its own database (its `onAdd`, 98-107), no
        // contract route
        onClick = {},
        trailingContent = {
            if (playlist.isBookmarked) {
                // The phone's `ytmusic` badge (its 141-150): red over white, 18 dp
                Image(
                    painter = painterResource(Res.drawable.ytmusic),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(
                        Color.Red.copy(alpha = 0.75f).compositeOver(Color.White),
                    ),
                    modifier = Modifier.size(18.dp),
                )
            }
            // The phone's "open" button (its 151-159): inert — the PC has no playlist page to open to
            IconButton(
                onClick = {},
                icon = Res.drawable.open,
                color = colorPalette().accent,
                modifier = Modifier.size(24.dp),
            )
        },
    )
}

/**
 * The phone's `SettingIcon` (its 110-127): the accent-tinted 18 dp icon in a 32 dp box on a 10 % accent
 * square (PC drawables, no material icons).
 */
@Composable
private fun PlaylistSettingIcon() {
    Box(
        modifier = Modifier
            .size(32.dp)
            .background(
                color = colorPalette().accent.copy(alpha = 0.1f),
                shape = uiRoundnessShape(),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(Res.drawable.add_in_playlist),
            tint = colorPalette().accent,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
    }
}

/**
 * The phone's generated `rewind-*` playlists (the phone's `RewindPlaylists.isRewind`, its 185-190):
 * since 1.7.1 the contract's [PlaylistOrigin] carries the phone's name-based check (monthly, yearly or
 * all-time, plus the legacy `Rewind` fallback of a phone before 1.7.1) — never add-to targets.
 */
private val REWIND_ORIGINS = setOf(
    PlaylistOrigin.Rewind,
    PlaylistOrigin.RewindMonthly,
    PlaylistOrigin.RewindYearly,
    PlaylistOrigin.RewindAlltime,
)
