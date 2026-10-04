package app.n_zik.compagnon.components.menu

import androidx.compose.runtime.Composable
import app.n_zik.compagnon.components.tab.toolbar.MenuIcon
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * A menu entry of the phone that has no contract route (story 11c): shown in its section with the phone's
 * icon and label (plus [suffix], e.g. the artist of "More of"), clickable, without effect.
 */
class InertMenuItem(
    override val iconId: DrawableResource,
    private val title: StringResource,
    private val suffix: String = "",
) : MenuIcon {
    override val menuIconTitle: String
        @Composable
        get() = stringResource(title) + suffix

    override fun onShortClick() {}
}

/**
 * Port of `String?.splitArtistNames` (phone's `app/it/fast4x/rimusic/utils/Utils.kt` 202) without the
 * user's conjunction list (a phone setting): split on `&` and `,`.
 */
internal fun splitArtistNames(artists: String?): List<String> {
    if (artists.isNullOrBlank()) return emptyList()
    return artists.split(Regex("\\s*(&|,)\\s*")).map { it.trim() }.filter { it.isNotBlank() }
}
