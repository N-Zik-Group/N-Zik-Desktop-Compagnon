package app.n_zik.compagnon.components.tab.toolbar

import app.n_zik.compagnon.colorPalette
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import app.n_zik.compagnon.generated.resources.Res
import app.n_zik.compagnon.generated.resources.add_in_playlist
import app.n_zik.compagnon.generated.resources.arrow_up
import app.n_zik.compagnon.generated.resources.dice
import app.n_zik.compagnon.generated.resources.enqueue
import app.n_zik.compagnon.generated.resources.export_outline
import app.n_zik.compagnon.generated.resources.import_outline
import app.n_zik.compagnon.generated.resources.play_skip_forward
import app.n_zik.compagnon.generated.resources.resize
import app.n_zik.compagnon.generated.resources.search_circle
import app.n_zik.compagnon.generated.resources.shuffle
import app.n_zik.compagnon.generated.resources.trash
import app.n_zik.compagnon.generated.resources.unchecked_outline
import org.jetbrains.compose.resources.DrawableResource

/**
 * A toolbar button of the phone that has no contract route (story 11c): drawn exactly like the phone's
 * (same icon, size, colour, ripple), clickable, and its click has no effect.
 */
class InertButton(
    override val iconId: DrawableResource,
    override val modifier: Modifier = Modifier,
    private val tint: Color? = null,
) : Icon {
    override val color: Color
        @Composable
        get() = tint ?: colorPalette().text

    override fun onShortClick() {}
}

/**
 * The phone's default home-tab toolbars (`HomeAlbumsToolbarSettingsDialog`, `HomeArtistsToolbarSettingsDialog`,
 * `HomeLibraryToolbarSettingsDialog` `allButtonIds`), with the buttons the default preferences hide left out
 * (position lock: the sort is not `Custom`; sync: YouTube sync off). Their icons are the phone's: the sort
 * arrow (`arrow_up`, ascending: no rotation), `search_circle`, `dice`, `shuffle`, `unchecked_outline` (item
 * selector), `play_skip_forward`, `enqueue`, `add_in_playlist`, `import_outline`, `export_outline`, `trash`,
 * `resize` (item size).
 */
object HomeToolbars {

    /** Ascending sort: the phone's `SortOrder.Ascending.rotationZ`. */
    private fun sort() = InertButton(Res.drawable.arrow_up, Modifier.graphicsLayer { rotationZ = 0f })

    /** Albums and artists: sort, search, randomizer, shuffle, item selector, play next, enqueue, add to playlist, export, item size. */
    fun albumsOrArtists(): List<Button> = listOf(
        sort(),
        InertButton(Res.drawable.search_circle),
        InertButton(Res.drawable.dice),
        InertButton(Res.drawable.shuffle),
        InertButton(Res.drawable.unchecked_outline),
        InertButton(Res.drawable.play_skip_forward),
        InertButton(Res.drawable.enqueue),
        InertButton(Res.drawable.add_in_playlist),
        InertButton(Res.drawable.export_outline),
        InertButton(Res.drawable.resize),
    )

    /** Playlists: sort, search, shuffle, item selector, play next, enqueue, add to playlist, import, export, delete, item size. */
    fun playlists(): List<Button> = listOf(
        sort(),
        InertButton(Res.drawable.search_circle),
        InertButton(Res.drawable.shuffle),
        InertButton(Res.drawable.unchecked_outline),
        InertButton(Res.drawable.play_skip_forward),
        InertButton(Res.drawable.enqueue),
        InertButton(Res.drawable.add_in_playlist),
        InertButton(Res.drawable.import_outline),
        InertButton(Res.drawable.export_outline),
        InertButton(Res.drawable.trash),
        InertButton(Res.drawable.resize),
    )
}
