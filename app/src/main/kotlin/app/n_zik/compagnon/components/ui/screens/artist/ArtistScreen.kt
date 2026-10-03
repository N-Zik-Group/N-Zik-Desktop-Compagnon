package app.n_zik.compagnon.components.ui.screens.artist

import androidx.compose.runtime.Composable
import app.n_zik.compagnon.bridge.library.LibraryRepository
import app.n_zik.compagnon.components.ui.screens.home.CollectionHeader
import app.n_zik.compagnon.components.ui.screens.home.LibraryActions

/**
 * Port of `ArtistScreen` (phone's `app/n_zik/android/components/ui/screens/artist/ArtistScreen.kt` 280-318):
 * its "Library" tab, `ArtistLocalSongs`. Dropped: the "Overview" tab (the artist's online page, outside
 * contract v1); with a single tab the phone's navigation bar draws nothing, so none is shown.
 */
@Composable
fun ArtistScreen(
    header: CollectionHeader.OfArtist,
    library: LibraryRepository,
    actions: LibraryActions,
    live: Boolean,
    onMessage: (String) -> Unit,
    onBack: () -> Unit,
) = ArtistLocalSongs(header, library, actions, live, onMessage, onBack)
