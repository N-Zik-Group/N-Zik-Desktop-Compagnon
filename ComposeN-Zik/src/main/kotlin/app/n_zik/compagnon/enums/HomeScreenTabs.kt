package app.n_zik.compagnon.enums

import app.n_zik.compagnon.generated.resources.*
import org.jetbrains.compose.resources.StringResource

/**
 * Port of the phone's `app/it/fast4x/rimusic/enums/HomeScreenTabs.kt` (the home tabs, in the
 * phone's declaration order and [index]es). [Default] and [QuickPics] share the phone's index 0
 * (the phone's Quick Picks tab, absent on the PC — both resolve to the first visible tab). The
 * Compagnon offers [Default] plus the four visible tabs in its start-tab setting ([Default]'s
 * label is the phone's `default_page`, its setting label, since the tab itself is not rendered
 * on the PC); [QuickPics]' label is kept with the phone's value even though it is never shown.
 */
enum class HomeScreenTabs(
    val index: Int,
    val textId: StringResource,
) {
    Default(0, Res.string.settings_home_default_page_label),

    QuickPics(0, Res.string.recommendations_title),

    Songs(1, Res.string.library_songs_title),

    Artists(2, Res.string.library_artists_title),

    Albums(3, Res.string.library_albums_title),

    Playlists(4, Res.string.library_playlists_title),

    Search(5, Res.string.search_title),
}
