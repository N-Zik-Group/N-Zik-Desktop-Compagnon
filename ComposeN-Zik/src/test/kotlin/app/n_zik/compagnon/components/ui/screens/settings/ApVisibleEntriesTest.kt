package app.n_zik.compagnon.components.ui.screens.settings

import app.n_zik.compagnon.bridge.state.PlayerBackgroundColors
import app.n_zik.compagnon.components.themed.MPB_MAX_CHECKED
import app.n_zik.compagnon.components.themed.MpbWiredState
import app.n_zik.compagnon.components.themed.mpbResetState
import app.n_zik.compagnon.components.themed.mpbRowCanToggle
import app.n_zik.compagnon.enums.AnimatedGradient
import app.n_zik.compagnon.enums.PlayerTimelineType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The pure visibility of the Player-appearance tab's dynamic entries (spec
 * `spec-fill-pc-ui-gaps-settings` §C + Design Notes §2): the 4 `isLandscape`-conditioned entries
 * (Appearance Presets / Show top actions bar / Thumbnail Size landscape / portrait — AS:1130 /
 * 2056 / 1446 / 1488) + the live-key entries (the PC keys `playerBackgroundColors`,
 * `animatedGradient` + `isM3E`, `showCoverThumbnailAnimation`, `playerTimelineType`), tested in
 * both orientations, plus the ported MiniPlayer-buttons dialog's max-checked / reset logic (spec
 * §D).
 */
class ApVisibleEntriesTest {

    private fun input(
        isLandscape: Boolean = true,
        bg: PlayerBackgroundColors = PlayerBackgroundColors.AnimatedGradient,
        gradient: AnimatedGradient = AnimatedGradient.M3EMorphingCover,
        coverAnim: Boolean = false,
        timeline: PlayerTimelineType = PlayerTimelineType.Wavy,
    ) = ApVisibilityInput(
        isLandscape = isLandscape,
        playerBackgroundColors = bg,
        animatedGradient = gradient,
        showCoverThumbnailAnimation = coverAnim,
        playerTimelineType = timeline,
    )

    // Orientation: the 4 landscape-dynamic entries (phone defaults: AnimatedGradient + M3E)

    @Test
    fun `landscape shows only the landscape thumbnail size`() {
        val vis = apVisibleEntries(input(isLandscape = true))
        assertFalse(vis.appearancePresets, "Appearance Presets is portrait-only (the phone's !isLandscape)")
        assertFalse(vis.showTopActionsBar, "Show top actions bar is portrait-only (the phone's !isLandscape)")
        assertTrue(vis.thumbnailSizeLandscape, "the landscape thumbnail-size branch shows in landscape")
        assertFalse(vis.thumbnailSizePortrait, "the portrait thumbnail-size branch hides in landscape")
    }

    @Test
    fun `portrait shows the presets the top actions bar and the portrait thumbnail size`() {
        val vis = apVisibleEntries(input(isLandscape = false))
        assertTrue(vis.appearancePresets, "Appearance Presets shows in portrait")
        assertTrue(vis.showTopActionsBar, "Show top actions bar shows in portrait")
        assertFalse(vis.thumbnailSizeLandscape, "the landscape thumbnail-size branch hides in portrait")
        assertTrue(vis.thumbnailSizePortrait, "the portrait thumbnail-size branch shows in portrait")
    }

    // Live keys: the background color

    @Test
    fun `blurred cover background shows its three sub entries`() {
        val vis = apVisibleEntries(input(isLandscape = true, bg = PlayerBackgroundColors.BlurredCoverColor))
        assertTrue(vis.showThumbnail)
        assertTrue(vis.rotatingAlbumCover)
        assertTrue(vis.enableActionBarGradient)
        assertFalse(vis.gradientType, "the gradient type is only for the animated gradient")
        assertFalse(vis.themeShakeSensitivity, "the shake sensitivity is only for the animated gradient")
        assertFalse(vis.blackGradientAtBottom, "the black gradient is only for the two color gradients")
    }

    @Test
    fun `color gradients show only the black gradient`() {
        assertTrue(
            apVisibleEntries(input(bg = PlayerBackgroundColors.CoverColorGradient)).blackGradientAtBottom,
        )
        assertTrue(
            apVisibleEntries(input(bg = PlayerBackgroundColors.ThemeColorGradient)).blackGradientAtBottom,
        )
        assertFalse(
            apVisibleEntries(input(bg = PlayerBackgroundColors.CoverColor)).blackGradientAtBottom,
        )
        assertFalse(
            apVisibleEntries(input(bg = PlayerBackgroundColors.ThemeColor)).blackGradientAtBottom,
        )
    }

    @Test
    fun `only the blurred cover background shows its three sub entries`() {
        PlayerBackgroundColors.entries.forEach { bg ->
            val vis = apVisibleEntries(input(bg = bg))
            if (bg == PlayerBackgroundColors.BlurredCoverColor) {
                assertTrue(vis.showThumbnail)
                assertTrue(vis.rotatingAlbumCover)
                assertTrue(vis.enableActionBarGradient)
            } else {
                assertFalse(vis.showThumbnail, "show thumbnail hides outside the blurred cover ($bg)")
                assertFalse(vis.rotatingAlbumCover, "rotating album cover hides outside the blurred cover ($bg)")
                assertFalse(
                    vis.enableActionBarGradient,
                    "the action bar gradient hides outside the blurred cover ($bg)",
                )
            }
        }
    }

    // Live keys: the animated gradient + isM3E

    @Test
    fun `the gradient type and the shake sensitivity follow the animated gradient background`() {
        val animated = apVisibleEntries(input(bg = PlayerBackgroundColors.AnimatedGradient))
        assertTrue(animated.gradientType)
        assertTrue(animated.themeShakeSensitivity, "M3EMorphingCover is an M3E gradient")

        val other = apVisibleEntries(input(bg = PlayerBackgroundColors.CoverColor))
        assertFalse(other.gradientType)
        assertFalse(other.themeShakeSensitivity)
    }

    @Test
    fun `the shake sensitivity needs an M3E gradient`() {
        assertTrue(
            apVisibleEntries(input(gradient = AnimatedGradient.M3EMorphingTheme)).themeShakeSensitivity,
        )
        assertFalse(
            apVisibleEntries(input(gradient = AnimatedGradient.Linear)).themeShakeSensitivity,
        )
        assertFalse(
            apVisibleEntries(input(gradient = AnimatedGradient.Random)).themeShakeSensitivity,
        )
    }

    // Live keys: the cover animation + the timeline

    @Test
    fun `the cover animation type follows its parent toggle`() {
        assertTrue(apVisibleEntries(input(coverAnim = true)).coverAnimationType)
        assertFalse(apVisibleEntries(input(coverAnim = false)).coverAnimationType)
    }

    @Test
    fun `the mic permission info row follows the timeline`() {
        assertTrue(apVisibleEntries(input(timeline = PlayerTimelineType.VisualizerBar)).micPermissionInfo)
        assertFalse(apVisibleEntries(input(timeline = PlayerTimelineType.Wavy)).micPermissionInfo)
    }

    // MiniPlayer buttons dialog logic (spec §D)

    @Test
    fun `an unchecked MPB row is blocked at max checked`() {
        assertFalse(mpbRowCanToggle(isChecked = false, functionalCheckedCount = 4, maxChecked = MPB_MAX_CHECKED))
        assertTrue(mpbRowCanToggle(isChecked = false, functionalCheckedCount = 3, maxChecked = MPB_MAX_CHECKED))
        // a checked row stays togglable (to uncheck it), even at max
        assertTrue(mpbRowCanToggle(isChecked = true, functionalCheckedCount = 4, maxChecked = MPB_MAX_CHECKED))
    }

    @Test
    fun `the functional checked count counts only the functional rows`() {
        assertEquals(0, MpbWiredState(discover = false, visualizer = false).functionalCheckedCount)
        assertEquals(1, MpbWiredState(discover = true, visualizer = false).functionalCheckedCount)
        assertEquals(2, MpbWiredState(discover = true, visualizer = true).functionalCheckedCount)
    }

    @Test
    fun `the MPB reset state turns the functional rows off`() {
        val reset = mpbResetState()
        assertFalse(reset.discover)
        assertFalse(reset.visualizer)
        assertEquals(0, reset.functionalCheckedCount)
    }
}
