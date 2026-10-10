package app.n_zik.compagnon.bridge.state

import app.n_zik.compagnon.bridge.pairing.BridgeJson
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * The PC's effective view of a `/ui/settings` wire read (spec `spec-remove-ui-sync`): only `topN`
 * is consumed — the wire's appearance fields never reach the PC's UI (its coded defaults stand),
 * the phone's full 21-field payload decodes with its 7 dropped fields ignored, an absent `topN`
 * decodes to the default 10, and a `null` `topN` (the phone's unlimited) stays `null`, never
 * coerced to the default.
 */
class UiSettingsPcEffectiveTest {

    @Test
    fun `a wire read keeps only topN, the appearance resets to the coded defaults`() {
        val wire = UiSettings(
            colorPaletteName = "PureBlack",
            colorPaletteMode = "Light",
            blurStrength = 50f,
            playerBackdrop = 0.5f,
            rotatingAlbumCover = true,
            showThumbnail = false,
            noBlur = false,
            iconLikeType = "Heart",
            playerInfoShowIcons = false,
            showSkipTimeButtons = false,
            textOutline = true,
            transitionEffect = "Scale",
            topN = 50,
            nowPlayingIndicator = "CrazyBars",
        )
        assertEquals(UiSettings(topN = 50), wire.toPcEffective())
    }

    @Test
    fun `an absent wire topN decodes to the default 10`() {
        // A payload without the `topN` key decodes with the model's default
        val wire = BridgeJson.decodeFromString(UiSettings.serializer(), """{"colorPaletteName":"PureBlack"}""")
        assertEquals(10, wire.topN)
        assertEquals("PureBlack", wire.colorPaletteName)
    }

    @Test
    fun `a wire read with an unlimited topN (null) stays unlimited`() {
        assertNull(UiSettings(topN = null).toPcEffective().topN)
    }

    @Test
    fun `the phone's full 21-field payload decodes, its dropped fields are ignored`() {
        // The phone's current full payload (its `UiSettingsDto`, contract §10.5): the 14 fields the
        // model keeps plus the 7 it no longer carries — the dropped ones are ignored at decode,
        // not a failure, and they never reach the PC's effective settings.
        val json = """
            {
              "colorPaletteName": "PureBlack",
              "colorPaletteMode": "PitchBlack",
              "playerBackgroundColors": "CoverColorGradient",
              "blurStrength": 40.0,
              "blurDarkenFactor": 0.3,
              "playerBackdrop": 0.5,
              "rotatingAlbumCover": true,
              "showThumbnail": false,
              "noBlur": false,
              "bottomGradient": true,
              "iconLikeType": "Gift",
              "playerInfoShowIcons": false,
              "showSkipTimeButtons": false,
              "textOutline": true,
              "transitionEffect": "SlideVertical",
              "maxTopPlaylistItems": "25",
              "topN": 50,
              "menuStyle": "Grid",
              "disableScrollingText": true,
              "navigationBarPosition": "Left",
              "nowPlayingIndicator": "CrazyBars"
            }
        """.trimIndent()

        val wire = BridgeJson.decodeFromString(UiSettings.serializer(), json)

        // The kept fields decode.
        assertEquals("PureBlack", wire.colorPaletteName)
        assertEquals("PitchBlack", wire.colorPaletteMode)
        assertEquals(40f, wire.blurStrength)
        assertEquals(0.5f, wire.playerBackdrop)
        assertEquals(true, wire.rotatingAlbumCover)
        assertEquals(false, wire.showThumbnail)
        assertEquals(false, wire.noBlur)
        assertEquals("Gift", wire.iconLikeType)
        assertEquals(false, wire.playerInfoShowIcons)
        assertEquals(false, wire.showSkipTimeButtons)
        assertEquals(true, wire.textOutline)
        assertEquals("SlideVertical", wire.transitionEffect)
        assertEquals(50, wire.topN)
        assertEquals("CrazyBars", wire.nowPlayingIndicator)
        assertEquals(TransitionEffect.SlideVertical, wire.transition)
        assertEquals(IconLikeType.Gift, wire.likeIcon)

        // And none of the wire's appearance reaches the PC's effective settings.
        assertEquals(UiSettings(topN = 50), wire.toPcEffective())
    }
}
