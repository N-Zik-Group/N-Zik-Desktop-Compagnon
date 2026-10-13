package app.n_zik.compagnon.components.menu.playlist

import app.n_zik.compagnon.bridge.library.Playlist
import app.n_zik.compagnon.bridge.library.PlaylistOrigin
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * The "Add to playlist" sub-menu's list (spec `spec-fill-pc-ui-gaps-menus` #47, matrix row
 * "Sous-menu Ajouter à une playlist"): the phone's split (its `PlaylistsMenu.kt` 182-190) — the
 * pinned section first (a pure pin check, even for a generated playlist), the rest minus the
 * generated rewind and legacy `monthly:` playlists, which are never add-to targets.
 */
class PlaylistsMenuTest {

    @Test
    fun `the pinned section lists the pinned playlists first, generated or not`() {
        val pinnedLocal = Playlist(id = "1", name = "pinned:My list", isPinned = true, origin = PlaylistOrigin.Local)
        val pinnedRewind = Playlist(id = "2", name = "pinned:Monthly", isPinned = true, origin = PlaylistOrigin.RewindMonthly)
        val unpinned = Playlist(id = "3", name = "Other list", origin = PlaylistOrigin.Local)

        val (pinned, rest) = playlistsAddToSections(listOf(unpinned, pinnedLocal, pinnedRewind))

        assertEquals(listOf(pinnedLocal, pinnedRewind), pinned)
        assertEquals(listOf(unpinned), rest)
    }

    @Test
    fun `the rest keeps the non pinned playlists minus the generated rewind and monthly ones`() {
        val local = Playlist(id = "1", name = "Local list", origin = PlaylistOrigin.Local)
        val ytmusic = Playlist(id = "2", name = "YT list", origin = PlaylistOrigin.Ytmusic)
        val spotify = Playlist(id = "3", name = "Spotify list", origin = PlaylistOrigin.Spotify)
        // Never add-to targets (the phone's `RewindPlaylists.isRewind` + `monthly:` prefix)
        val rewindMonthly = Playlist(id = "4", name = "rewind-monthly", origin = PlaylistOrigin.RewindMonthly)
        val rewindYearly = Playlist(id = "5", name = "rewind-yearly", origin = PlaylistOrigin.RewindYearly)
        val rewindAlltime = Playlist(id = "6", name = "rewind-alltime", origin = PlaylistOrigin.RewindAlltime)
        val rewindLegacy = Playlist(id = "7", name = "Rewind", origin = PlaylistOrigin.Rewind)
        val monthlyLegacy = Playlist(id = "8", name = "monthly:abc", origin = PlaylistOrigin.Monthly)

        val (pinned, rest) = playlistsAddToSections(
            listOf(rewindMonthly, local, monthlyLegacy, ytmusic, rewindLegacy, spotify, rewindAlltime),
        )

        assertEquals(emptyList<Playlist>(), pinned)
        assertEquals(listOf(local, ytmusic, spotify), rest)
    }

    @Test
    fun `an empty read list renders no section`() {
        val (pinned, rest) = playlistsAddToSections(emptyList())

        assertEquals(emptyList<Playlist>(), pinned)
        assertEquals(emptyList<Playlist>(), rest)
    }
}
