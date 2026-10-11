package app.n_zik.compagnon.utils

import app.n_zik.compagnon.bridge.state.PlayerBackgroundColors
import app.n_zik.compagnon.enums.AlbumSwipeAction
import app.n_zik.compagnon.enums.AnimatedGradient
import app.n_zik.compagnon.enums.HomeScreenTabs
import app.n_zik.compagnon.enums.NavigationBarPosition
import app.n_zik.compagnon.enums.NavigationBarType
import app.n_zik.compagnon.enums.PlaylistSwipeAction
import app.n_zik.compagnon.enums.PlayerSwipeSensitivity
import app.n_zik.compagnon.enums.PlayerTimelineType
import app.n_zik.compagnon.enums.QueueSwipeAction
import app.n_zik.compagnon.enums.SwipeAnimationNoThumbnail
import app.n_zik.compagnon.enums.ThumbnailCoverType
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * The PC's "App language" setting (spec `spec-remove-ui-sync`) through the real [Preferences] file
 * I/O: a settings file without the `language` key decodes with the [AppLanguage.SYSTEM] default,
 * and the one-time migration rewrites the old phone-mirroring sentinels (`auto_pc`, `auto_tel`) to
 * `system`, preserving the pre-existing values.
 */
class PreferencesTest {

    @TempDir
    lateinit var dir: Path

    private fun file() = dir.resolve("settings.json")

    /** A settings file written before the spec: the old keys only, no `language`. */
    private fun writeWithoutLanguage() {
        Files.writeString(
            file(),
            """{"exoPlayerCustomCache":48,"playbackVolume":0.7,"disableScrollingText":true}""",
        )
    }

    @Test
    fun `a settings file without the language key decodes with the system default`() {
        writeWithoutLanguage()

        val settings = Preferences(file()).settings.value
        assertEquals(AppLanguage.SYSTEM, settings.language)
        // The pre-existing values are preserved.
        assertPreserved(settings)
    }

    @Test
    fun `the migration rewrites the old auto_pc sentinel to system`() {
        migrateFrom("auto_pc")

        val reloaded = Preferences(file()).settings.value
        assertEquals(AppLanguage.SYSTEM, reloaded.language)
        assertPreserved(reloaded)
    }

    @Test
    fun `the migration rewrites the old auto_tel sentinel to system`() {
        migrateFrom("auto_tel")

        val reloaded = Preferences(file()).settings.value
        assertEquals(AppLanguage.SYSTEM, reloaded.language)
        assertPreserved(reloaded)
    }

    @Test
    fun `the migration is a no-op for a fresh or already-migrated file`() {
        writeWithoutLanguage()
        val preferences = Preferences(file()) // runs the migration on init (inert here)
        val before = Files.readString(file())
        // Nothing was written by the construction.
        assertEquals(before, Files.readString(file()))
        // A manual code is never touched.
        preferences.update { it.copy(language = "fr") }
        val reloaded = Preferences(file()).settings.value
        assertEquals("fr", reloaded.language)
        assertPreserved(reloaded)
    }

    @Test
    fun `the migration is a no-op for an already-migrated file`() {
        // A pre-spec file whose `language` already is the migrated `system` sentinel
        Files.writeString(
            file(),
            """{"exoPlayerCustomCache":48,"playbackVolume":0.7,"disableScrollingText":true,"language":"system"}""",
        )
        val before = Files.readString(file())
        val preferences = Preferences(file()) // runs the migration on init (inert here)
        assertEquals(before, Files.readString(file())) // nothing was rewritten
        assertEquals(AppLanguage.SYSTEM, preferences.settings.value.language)
        assertPreserved(preferences.settings.value)
    }

    @Test
    fun `a settings file without the player keys decodes with the phone defaults`() {
        Files.writeString(file(), """{"exoPlayerCustomCache":48}""")

        val settings = Preferences(file()).settings.value
        assertEquals(false, settings.rotatingAlbumCover)
        assertEquals(false, settings.showCoverThumbnailAnimation)
        assertEquals(ThumbnailCoverType.Vinyl.name, settings.coverThumbnailAnimation)
        assertEquals(PlayerBackgroundColors.AnimatedGradient.name, settings.playerBackgroundColors)
        assertEquals(AnimatedGradient.M3EMorphingCover.name, settings.animatedGradient)
        assertEquals(PlayerTimelineType.Wavy.name, settings.playerTimelineType)
        assertEquals(SwipeAnimationNoThumbnail.Sliding.name, settings.swipeAnimationsNoThumbnail)
        assertEquals(PlayerSwipeSensitivity.Medium.name, settings.playerSwipeSensitivity)
        assertEquals(true, settings.showMiniPlayerDiscover)
        assertEquals(true, settings.showMiniPlayerVisualizer)
        assertEquals(QueueSwipeAction.RemoveFromQueue.name, settings.queueSwipeLeftAction)
        assertEquals(QueueSwipeAction.PlayNext.name, settings.queueSwipeRightAction)
        assertEquals(true, settings.isSwipeToActionEnabled)
    }

    @Test
    fun `the player keys round-trip through encode and decode`() {
        Preferences(file()).update {
            it.copy(
                rotatingAlbumCover = true,
                showCoverThumbnailAnimation = true,
                coverThumbnailAnimation = ThumbnailCoverType.CD.name,
                playerBackgroundColors = PlayerBackgroundColors.CoverColor.name,
                animatedGradient = AnimatedGradient.FluidThemeColorGradient.name,
                playerTimelineType = PlayerTimelineType.AudioWaves.name,
                swipeAnimationsNoThumbnail = SwipeAnimationNoThumbnail.Fade.name,
                playerSwipeSensitivity = PlayerSwipeSensitivity.High.name,
                showMiniPlayerDiscover = false,
                showMiniPlayerVisualizer = false,
                queueSwipeLeftAction = QueueSwipeAction.Enqueue.name,
                queueSwipeRightAction = QueueSwipeAction.Favourite.name,
                isSwipeToActionEnabled = false,
            )
        }

        val reloaded = Preferences(file()).settings.value
        assertEquals(true, reloaded.rotatingAlbumCover)
        assertEquals(true, reloaded.showCoverThumbnailAnimation)
        assertEquals(ThumbnailCoverType.CD.name, reloaded.coverThumbnailAnimation)
        assertEquals(ThumbnailCoverType.CD, reloaded.coverThumbnailAnimationValue)
        assertEquals(PlayerBackgroundColors.CoverColor.name, reloaded.playerBackgroundColors)
        assertEquals(PlayerBackgroundColors.CoverColor, reloaded.playerBackgroundColorsValue)
        assertEquals(AnimatedGradient.FluidThemeColorGradient.name, reloaded.animatedGradient)
        assertEquals(AnimatedGradient.FluidThemeColorGradient, reloaded.animatedGradientValue)
        assertEquals(PlayerTimelineType.AudioWaves.name, reloaded.playerTimelineType)
        assertEquals(PlayerTimelineType.AudioWaves, reloaded.playerTimelineTypeValue)
        assertEquals(SwipeAnimationNoThumbnail.Fade.name, reloaded.swipeAnimationsNoThumbnail)
        assertEquals(SwipeAnimationNoThumbnail.Fade, reloaded.swipeAnimationsNoThumbnailValue)
        assertEquals(PlayerSwipeSensitivity.High.name, reloaded.playerSwipeSensitivity)
        assertEquals(PlayerSwipeSensitivity.High, reloaded.playerSwipeSensitivityValue)
        assertEquals(false, reloaded.showMiniPlayerDiscover)
        assertEquals(false, reloaded.showMiniPlayerVisualizer)
        assertEquals(QueueSwipeAction.Enqueue.name, reloaded.queueSwipeLeftAction)
        assertEquals(QueueSwipeAction.Enqueue, reloaded.queueSwipeLeftActionValue)
        assertEquals(QueueSwipeAction.Favourite.name, reloaded.queueSwipeRightAction)
        assertEquals(QueueSwipeAction.Favourite, reloaded.queueSwipeRightActionValue)
        assertEquals(false, reloaded.isSwipeToActionEnabled)
    }

    @Test
    fun `unknown player enum names keep the raw string and decode to the phone defaults`() {
        Files.writeString(
            file(),
            """{"coverThumbnailAnimation":"bogus","playerBackgroundColors":"bogus","animatedGradient":"bogus",
                "playerTimelineType":"bogus","swipeAnimationsNoThumbnail":"bogus","playerSwipeSensitivity":"bogus",
                "queueSwipeLeftAction":"bogus","queueSwipeRightAction":"bogus"}""",
        )

        val settings = Preferences(file()).settings.value
        // The raw names are kept exactly as written.
        assertEquals("bogus", settings.coverThumbnailAnimation)
        assertEquals("bogus", settings.playerBackgroundColors)
        assertEquals("bogus", settings.animatedGradient)
        assertEquals("bogus", settings.playerTimelineType)
        assertEquals("bogus", settings.swipeAnimationsNoThumbnail)
        assertEquals("bogus", settings.playerSwipeSensitivity)
        assertEquals("bogus", settings.queueSwipeLeftAction)
        assertEquals("bogus", settings.queueSwipeRightAction)
        // The decode getters fall back to the phone defaults.
        assertEquals(ThumbnailCoverType.Vinyl, settings.coverThumbnailAnimationValue)
        assertEquals(PlayerBackgroundColors.AnimatedGradient, settings.playerBackgroundColorsValue)
        assertEquals(AnimatedGradient.M3EMorphingCover, settings.animatedGradientValue)
        assertEquals(PlayerTimelineType.Wavy, settings.playerTimelineTypeValue)
        assertEquals(SwipeAnimationNoThumbnail.Sliding, settings.swipeAnimationsNoThumbnailValue)
        assertEquals(PlayerSwipeSensitivity.Medium, settings.playerSwipeSensitivityValue)
        assertEquals(QueueSwipeAction.RemoveFromQueue, settings.queueSwipeLeftActionValue)
        assertEquals(QueueSwipeAction.PlayNext, settings.queueSwipeRightActionValue)
    }

    @Test
    fun `the home keys decode with the phone defaults`() {
        Files.writeString(file(), """{"exoPlayerCustomCache":48}""")

        val settings = Preferences(file()).settings.value
        assertEquals(NavigationBarPosition.BottomFloating.name, settings.navigationBarPosition)
        assertEquals(NavigationBarType.IconOnly.name, settings.navigationBarType)
        assertEquals(HomeScreenTabs.Default.name, settings.indexNavigationTab)
        // Never written: the start tab applies, until the home tab change writes it
        assertEquals(null, settings.homeScreenTabIndex)
        assertEquals(PlaylistSwipeAction.Favourite.name, settings.playlistSwipeLeftAction)
        assertEquals(PlaylistSwipeAction.PlayNext.name, settings.playlistSwipeRightAction)
        assertEquals(AlbumSwipeAction.PlayNext.name, settings.albumSwipeLeftAction)
        assertEquals(AlbumSwipeAction.Bookmark.name, settings.albumSwipeRightAction)
        // On by default on the PC (off on the phone)
        assertEquals(true, settings.showFloatingIcon)
        assertEquals(0f, settings.multiFloatActionIconOffsetX)
        assertEquals(0f, settings.multiFloatActionIconOffsetY)
    }

    @Test
    fun `the home keys round-trip through encode and decode`() {
        Preferences(file()).update {
            it.copy(
                navigationBarPosition = NavigationBarPosition.Left.name,
                navigationBarType = NavigationBarType.IconAndText.name,
                indexNavigationTab = HomeScreenTabs.Playlists.name,
                homeScreenTabIndex = 2,
                playlistSwipeLeftAction = PlaylistSwipeAction.Enqueue.name,
                playlistSwipeRightAction = PlaylistSwipeAction.Download.name,
                albumSwipeLeftAction = AlbumSwipeAction.Enqueue.name,
                albumSwipeRightAction = AlbumSwipeAction.NoAction.name,
                showFloatingIcon = false,
                multiFloatActionIconOffsetX = 12.5f,
                multiFloatActionIconOffsetY = -3f,
            )
        }

        val reloaded = Preferences(file()).settings.value
        assertEquals(NavigationBarPosition.Left.name, reloaded.navigationBarPosition)
        assertEquals(NavigationBarPosition.Left, reloaded.navigationBarPositionValue)
        assertEquals(NavigationBarType.IconAndText.name, reloaded.navigationBarType)
        assertEquals(NavigationBarType.IconAndText, reloaded.navigationBarTypeValue)
        assertEquals(HomeScreenTabs.Playlists.name, reloaded.indexNavigationTab)
        assertEquals(HomeScreenTabs.Playlists, reloaded.indexNavigationTabValue)
        assertEquals(2, reloaded.homeScreenTabIndex)
        assertEquals(PlaylistSwipeAction.Enqueue.name, reloaded.playlistSwipeLeftAction)
        assertEquals(PlaylistSwipeAction.Enqueue, reloaded.playlistSwipeLeftActionValue)
        assertEquals(PlaylistSwipeAction.Download.name, reloaded.playlistSwipeRightAction)
        assertEquals(PlaylistSwipeAction.Download, reloaded.playlistSwipeRightActionValue)
        assertEquals(AlbumSwipeAction.Enqueue.name, reloaded.albumSwipeLeftAction)
        assertEquals(AlbumSwipeAction.Enqueue, reloaded.albumSwipeLeftActionValue)
        assertEquals(AlbumSwipeAction.NoAction.name, reloaded.albumSwipeRightAction)
        assertEquals(AlbumSwipeAction.NoAction, reloaded.albumSwipeRightActionValue)
        assertEquals(false, reloaded.showFloatingIcon)
        assertEquals(12.5f, reloaded.multiFloatActionIconOffsetX)
        assertEquals(-3f, reloaded.multiFloatActionIconOffsetY)
    }

    @Test
    fun `unknown home enum names keep the raw string and decode to the phone defaults`() {
        Files.writeString(
            file(),
            """{"navigationBarPosition":"bogus","navigationBarType":"bogus","indexNavigationTab":"bogus",
                "playlistSwipeLeftAction":"bogus","playlistSwipeRightAction":"bogus",
                "albumSwipeLeftAction":"bogus","albumSwipeRightAction":"bogus"}""",
        )

        val settings = Preferences(file()).settings.value
        // The raw names are kept exactly as written.
        assertEquals("bogus", settings.navigationBarPosition)
        assertEquals("bogus", settings.navigationBarType)
        assertEquals("bogus", settings.indexNavigationTab)
        assertEquals("bogus", settings.playlistSwipeLeftAction)
        assertEquals("bogus", settings.playlistSwipeRightAction)
        assertEquals("bogus", settings.albumSwipeLeftAction)
        assertEquals("bogus", settings.albumSwipeRightAction)
        // The decode getters fall back to the phone defaults.
        assertEquals(NavigationBarPosition.BottomFloating, settings.navigationBarPositionValue)
        assertEquals(NavigationBarType.IconOnly, settings.navigationBarTypeValue)
        assertEquals(HomeScreenTabs.Default, settings.indexNavigationTabValue)
        assertEquals(PlaylistSwipeAction.Favourite, settings.playlistSwipeLeftActionValue)
        assertEquals(PlaylistSwipeAction.PlayNext, settings.playlistSwipeRightActionValue)
        assertEquals(AlbumSwipeAction.PlayNext, settings.albumSwipeLeftActionValue)
        assertEquals(AlbumSwipeAction.Bookmark, settings.albumSwipeRightActionValue)
    }

    /** A pre-spec `settings.json`: the old keys plus the given `language` value. */
    private fun migrateFrom(language: String) {
        Files.writeString(
            file(),
            """{"exoPlayerCustomCache":48,"playbackVolume":0.7,"disableScrollingText":true,"language":"$language"}""",
        )
        Preferences(file()) // runs the migration on init
    }

    private fun assertPreserved(settings: UserSettings) {
        assertEquals(48, settings.exoPlayerCustomCache)
        assertEquals(0.7f, settings.playbackVolume)
        assertEquals(true, settings.disableScrollingText)
    }
}
