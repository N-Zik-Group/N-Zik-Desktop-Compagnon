package app.n_zik.compagnon.components.ui.screens.settings

import app.n_zik.compagnon.bridge.state.PlayerBackgroundColors
import app.n_zik.compagnon.colorPalette
import app.n_zik.compagnon.components.settings.OtherSettingsEntry
import app.n_zik.compagnon.components.settings.SettingsDescription
import app.n_zik.compagnon.components.settings.SettingsInfoEntry
import app.n_zik.compagnon.components.settings.SettingsSectionCard
import app.n_zik.compagnon.components.settings.ToggleSettingsEntry
import app.n_zik.compagnon.components.tab.Search
import app.n_zik.compagnon.components.theme.BoundedCornerSize
import app.n_zik.compagnon.components.themed.HeaderWithIcon
import app.n_zik.compagnon.components.themed.MiniPlayerButtonsSettingsDialog
import app.n_zik.compagnon.components.themed.ValueSelectorDialog
import app.n_zik.compagnon.components.ui.sliders.SliderControl
import app.n_zik.compagnon.enums.AnimatedGradient
import app.n_zik.compagnon.enums.PlayerSwipeSensitivity
import app.n_zik.compagnon.enums.PlayerTimelineType
import app.n_zik.compagnon.enums.ThumbnailCoverType
import app.n_zik.compagnon.generated.resources.*
import app.n_zik.compagnon.uiRoundnessShape
import app.n_zik.compagnon.utils.Preferences
import app.n_zik.compagnon.utils.Toaster
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.stringResource

/**
 * The inputs of the PC's Player-appearance visibility conditions — everything a live condition
 * evaluates, so [apVisibleEntries] stays pure and testable in both orientations (spec
 * `spec-fill-pc-ui-gaps-settings`, Design Notes §1-2).
 *
 * [isLandscape] is the window's orientation (`BoxWithConstraints`, recomputed on every resize —
 * the window can be resized taller than wide, user decision 2026-10-12).
 */
data class ApVisibilityInput(
    val isLandscape: Boolean,
    val playerBackgroundColors: PlayerBackgroundColors,
    val animatedGradient: AnimatedGradient,
    val showCoverThumbnailAnimation: Boolean,
    val playerTimelineType: PlayerTimelineType,
)

/**
 * The visibility of the Player-appearance tab's dynamic entries (the 4 `isLandscape`-conditioned
 * ones + the live-key ones, spec §C). The entries with statically true conditions
 * (`showthumbnail_effective`, `playerType == Essential`, …) are always rendered, so they have no
 * flag here.
 */
data class ApVisibility(
    /** "Appearance Presets" (AS:1130) — the phone's `!isLandscape`. */
    val appearancePresets: Boolean,
    /** "Show top actions bar" (AS:2056) — the phone's `!isLandscape`. */
    val showTopActionsBar: Boolean,
    /** "Thumbnail Size" landscape branch (AS:1446) — the phone's `isLandscape`. */
    val thumbnailSizeLandscape: Boolean,
    /** "Thumbnail Size" portrait branch (AS:1488) — the phone's `!isLandscape`. */
    val thumbnailSizePortrait: Boolean,
    /** "Show thumbnail" (AS:1287) — `playerBackgroundColors == BlurredCoverColor`. */
    val showThumbnail: Boolean,
    /** "Rotating album cover" (AS:1916) — `playerBackgroundColors == BlurredCoverColor`. */
    val rotatingAlbumCover: Boolean,
    /** "Black Gradient at Bottom" (AS:1935) — the background is one of the two color gradients. */
    val blackGradientAtBottom: Boolean,
    /** "Enable action bar gradient" (AS:1969) — `playerBackgroundColors == BlurredCoverColor`. */
    val enableActionBarGradient: Boolean,
    /** "Gradient Type" (AS:1886) — `playerBackgroundColors == AnimatedGradient`. */
    val gradientType: Boolean,
    /** "Theme Shake Sensitivity" (AS:1902) — the background is the animated gradient AND the gradient is an M3E one. */
    val themeShakeSensitivity: Boolean,
    /** "Cover animation type" (AS:1423) — `showCoverThumbnailAnimation` (its parent toggle). */
    val coverAnimationType: Boolean,
    /** The "Visualizer effect require mic permission" info row (AS:1759) — `playerTimelineType == VisualizerBar`. */
    val micPermissionInfo: Boolean,
)

/**
 * The phone's `isM3E` (its `AppearanceSettings.kt` 1897): the animated gradient is one of the two
 * Material-3 Expressive morphing styles.
 */
val AnimatedGradient.isM3E: Boolean
    get() = this == AnimatedGradient.M3EMorphingTheme || this == AnimatedGradient.M3EMorphingCover

/**
 * The pure visibility of the Player-appearance tab's dynamic entries — the phone's
 * `AppearanceSettings.kt` conditions (AS:1130 / 2056 / 1446 / 1488 / 1287 / 1916 / 1935 / 1969 /
 * 1886 / 1902 / 1423 / 1759), evaluated from [ApVisibilityInput] only, so the screen re-evaluates
 * them live on every resize / setting change without any side effect (spec
 * `spec-fill-pc-ui-gaps-settings` §C + Design Notes §2 — tested in both orientations in
 * `ApVisibleEntriesTest.kt`).
 */
fun apVisibleEntries(input: ApVisibilityInput): ApVisibility = ApVisibility(
    appearancePresets = !input.isLandscape,
    showTopActionsBar = !input.isLandscape,
    thumbnailSizeLandscape = input.isLandscape,
    thumbnailSizePortrait = !input.isLandscape,
    showThumbnail = input.playerBackgroundColors == PlayerBackgroundColors.BlurredCoverColor,
    rotatingAlbumCover = input.playerBackgroundColors == PlayerBackgroundColors.BlurredCoverColor,
    blackGradientAtBottom = input.playerBackgroundColors == PlayerBackgroundColors.CoverColorGradient ||
        input.playerBackgroundColors == PlayerBackgroundColors.ThemeColorGradient,
    enableActionBarGradient = input.playerBackgroundColors == PlayerBackgroundColors.BlurredCoverColor,
    gradientType = input.playerBackgroundColors == PlayerBackgroundColors.AnimatedGradient,
    themeShakeSensitivity = input.playerBackgroundColors == PlayerBackgroundColors.AnimatedGradient &&
        input.animatedGradient.isM3E,
    coverAnimationType = input.showCoverThumbnailAnimation,
    micPermissionInfo = input.playerTimelineType == PlayerTimelineType.VisualizerBar,
)

/**
 * The phone's `AppearanceSettings.kt` 639-718 slider-row shape of the roundness previews (the
 * `thumbnailRoundnessDp >= 48f` switch, ported verbatim with the entry's own phone default): at
 * 48 dp the preview is a circle, below it a bounded-corner square.
 */
private fun phoneRoundnessShape(roundnessDp: Float): Shape =
    if (roundnessDp >= 48f) CircleShape else RoundedCornerShape(BoundedCornerSize(roundnessDp.dp, 0.25f))

/**
 * The phone's `AppearanceSettings.kt` 1456 / 1498 thumbnail-size slider preview, ported with the
 * inert row's fixed value and the phone's default roundness (12 dp — `thumbnailRoundnessDpKey` is
 * inert on the PC): a 36 dp accent border around an inner accent box shrunk by the value.
 */
@Composable
private fun InertThumbnailSizePreview(value: Float, roundnessDp: Float) {
    val shape = phoneRoundnessShape(roundnessDp)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .border(width = 1.dp, color = colorPalette().accent, shape = shape),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .padding(((100f - value) * 0.15f).dp)
                    .fillMaxSize()
                    .background(color = colorPalette().accent.copy(alpha = 0.5f), shape = shape),
            )
        }
    }
}

/**
 * The phone's `AppearanceSettings.kt` 1542 / 1584 roundness slider preview, ported with the entry's
 * own phone default: a 36 dp accent square (circle at 48 dp) with its accent border.
 */
@Composable
private fun InertRoundnessPreview(roundnessDp: Float) {
    val shape = phoneRoundnessShape(roundnessDp)
    Spacer(
        modifier = Modifier
            .size(36.dp)
            .background(color = colorPalette().accent.copy(alpha = 0.5f), shape = shape)
            .border(width = 1.dp, color = colorPalette().accent, shape = shape),
    )
}

/**
 * The phone's `AppearanceSettings.kt` 1625 UI-roundness slider preview, ported with the phone's
 * default (25 dp, the 0.4 fraction the phone's coded shape uses): a 36 dp accent square with its
 * accent border.
 */
@Composable
private fun InertUiRoundnessPreview(roundnessDp: Float) {
    val shape = RoundedCornerShape(BoundedCornerSize(roundnessDp.dp, 0.4f))
    Spacer(
        modifier = Modifier
            .size(36.dp)
            .background(color = colorPalette().accent.copy(alpha = 0.5f), shape = shape)
            .border(width = 1.dp, color = colorPalette().accent, shape = shape),
    )
}

/**
 * The phone's `SliderSettingsEntry` (its `SettingsScreen.kt` 639-718) + the quick-value button row
 * the phone's `AppearanceSettings.kt` 1459-1475 / 1501-1516 / 1545-1561 / 1587-1602 / 1629-1645
 * attach to its sliders, ported as one inert block: the row shows the title + the phone's default
 * value (the phone renders `" ($value)"`), a click has no effect (the phone opens its value dialog —
 * there is no PC key to set), the [SliderControl] is disabled at the phone's default (the PC's
 * inert-slider convention) and the quick-value buttons keep the phone's selected highlight, their
 * clicks no-ops. The whole block is at alpha 0.5 (the inert-entry convention).
 */
@Composable
private fun InertSliderSettingsEntry(
    title: String,
    icon: DrawableResource,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    defaultValue: Float,
    quickValues: List<Pair<Float, String>>,
    buttonsPadding: PaddingValues,
    trailingPreview: (@Composable () -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .alpha(0.5f),
    ) {
        OtherSettingsEntry(
            title = title,
            text = " (${value.toInt()})",
            icon = icon,
            onClick = {},
            trailingContent = trailingPreview,
        )
        SliderControl(
            state = value,
            range = range,
            stepSize = 0f,
            defaultValue = defaultValue,
            drawValuePoints = true,
            isEnabled = false,
            showValue = false,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(buttonsPadding),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            quickValues.forEach { (v, label) ->
                val isSelected = value.toInt() == v.toInt()
                TextButton(
                    onClick = {},
                    shape = uiRoundnessShape(),
                    colors = ButtonDefaults.textButtonColors(
                        containerColor = if (isSelected) colorPalette().accent.copy(alpha = 0.2f) else Color.Transparent,
                    ),
                ) {
                    Text(
                        text = label,
                        fontSize = 12.sp,
                        color = if (isSelected) colorPalette().accent else colorPalette().text,
                        fontWeight = if (isSelected) FontWeight.Bold else null,
                    )
                }
            }
        }
    }
}

/**
 * The Player appearance tab (the phone's `AppearanceSettings.kt` 589-2813 header / description /
 * search, spec `spec-fill-pc-ui-gaps-settings` §C): the tab-style header (the phone's
 * `player_appearance` label + `appearance_settings_description` + the tab's own `color_palette`
 * icon), the description, the search, then the phone's 8 rendered section cards in the phone's
 * order — **Presets & Layout** (AS:847), **Cover & Thumbnail** (AS:1275), **Timeline & Controls**
 * (AS:1694), **Colors & Background** (AS:1857), **Player Behavior & Visuals** (AS:2044), **Lyrics**
 * (AS:2341), **Player action bar** (AS:2487) and **Reset** (AS:2777) — each with the phone's
 * animation duration.
 *
 * `isLandscape` is evaluated **dynamically** from the window's size (a `BoxWithConstraints`,
 * `maxWidth > maxHeight`, recomputed on every resize — user decision 2026-10-12), and the live-key
 * conditions (the PC keys `playerBackgroundColors`, `animatedGradient` + `isM3E`,
 * `showCoverThumbnailAnimation`, `playerTimelineType`) are evaluated from `settings` at
 * composition, as on the phone — both through the pure [apVisibleEntries].
 *
 * The phone entries without a PC key are **inert** (the PC's inert-entry convention): their normal
 * appearance, the phone's default value / state shown, no effect on a click — a long click toasts
 * the phone's description string (the entries whose phone row shows a value instead of a
 * description have no toast). The 8 wired entries act on the PC's own settings:
 *
 * - "Background colors" (AS:1868, the PC key `playerBackgroundColors`, 7 values)
 * - "Gradient Type" (AS:1886, the PC key `animatedGradient`, 17 values, conditionally shown)
 * - "Rotating album cover" (AS:1916, the PC key `rotatingAlbumCover`, conditionally shown)
 * - "Timeline" (AS:1753, the PC key `playerTimelineType`, 8 values)
 * - "Show cover thumbnail animation" (AS:1411, the PC key `showCoverThumbnailAnimation`)
 * - "Cover animation type" (AS:1423, the PC key `coverThumbnailAnimation`, 3 values, conditionally
 *   shown)
 * - "Player Swipe Sensitivity" (AS:2163, the PC key `playerSwipeSensitivity`, 4 values)
 * - "Disable scrolling text" (AS:2137, the PC key `disableScrollingText`) — MOVED from the PC's
 *   former "Others" card (the old Autres tab)
 *
 * plus the "MiniPlayer buttons" action (AS:1724) opening the ported [MiniPlayerButtonsSettingsDialog].
 *
 * The phone entries whose condition is false on the phone's defaults, or Android-only (⛔), are not
 * rendered — a KDoc at the site names the phone entry (file:line) and the reason.
 *
 * This screen is the 3rd tab of [SettingsScreen]'s 7-tab bar (the 5 → 7 tab bar of the spec,
 * inserted after Général + the User Interface tab, the phone's order).
 */
@Composable
fun AppearanceSettingsScreen(preferences: Preferences, query: String, onQuery: (String) -> Unit) {
    val settings by preferences.settings.collectAsState()
    var showBackgroundColorsDialog by remember { mutableStateOf(false) }
    var showGradientTypeDialog by remember { mutableStateOf(false) }
    var showTimelineDialog by remember { mutableStateOf(false) }
    var showCoverAnimationTypeDialog by remember { mutableStateOf(false) }
    var showSwipeSensitivityDialog by remember { mutableStateOf(false) }
    var showMpbDialog by remember { mutableStateOf(false) }

    BoxWithConstraints {
        // The phone's `isLandscape`, DYNAMIC (user decision 2026-10-12): the window can be resized
        // taller than wide (min 1000×750 is a minimum, not a ratio) — recomputed on every resize,
        // never static
        val isLandscape = maxWidth > maxHeight
        // The live conditions, pure (spec §C + Design Notes §1-2, tested in ApVisibleEntriesTest)
        val vis = apVisibleEntries(
            ApVisibilityInput(
                isLandscape = isLandscape,
                playerBackgroundColors = settings.playerBackgroundColorsValue,
                animatedGradient = settings.animatedGradientValue,
                showCoverThumbnailAnimation = settings.showCoverThumbnailAnimation,
                playerTimelineType = settings.playerTimelineTypeValue,
            ),
        )
        val bg = settings.playerBackgroundColorsValue
        // The phone's sub-entry indent (its AS:1186 / 1315 / 1334 / … `if (bg == BlurredCoverColor)
        // 25.dp else 0.dp`), ported verbatim
        val subIndent = if (bg == PlayerBackgroundColors.BlurredCoverColor) 25.dp else 0.dp

        val search = Search(query, onQuery, lazyListState = null)
        // The cards' search ctx (spec §C): the card's title + its rendered entries' titles (the
        // phone's search quirks — e.g. the literal "artist ui roundness", AS:1565 / 1570 — are NOT
        // ported)
        val presetsLayoutCtx = settingsSearchCtx(
            search.inputValue,
            stringResource(Res.string.player_presets_and_layout),
            stringResource(Res.string.appearancepresets),
            stringResource(Res.string.playertype),
            stringResource(Res.string.queuetype),
            stringResource(Res.string.thumbnailtype),
            stringResource(Res.string.pinfo_type),
            stringResource(Res.string.pinfo_album_and_artist_name),
            stringResource(Res.string.pinfo_show_icons),
            stringResource(Res.string.pcontrols_type),
            stringResource(Res.string.icon_like_button),
        )
        val coverThumbnailCtx = settingsSearchCtx(
            search.inputValue,
            stringResource(Res.string.cover_and_thumbnail),
            stringResource(Res.string.show_thumbnail),
            stringResource(Res.string.thumbnailpause),
            stringResource(Res.string.crop_video_thumbnails),
            stringResource(Res.string.show_cover_thumbnail_animation),
            stringResource(Res.string.cover_thumbnail_animation_type),
            stringResource(Res.string.player_thumbnail_size),
            stringResource(Res.string.thumbnail_roundness),
            stringResource(Res.string.artist_roundness),
            stringResource(Res.string.ui_roundness),
        )
        val timelineCtx = settingsSearchCtx(
            search.inputValue,
            stringResource(Res.string.timeline_and_controls),
            stringResource(Res.string.timelinesize),
            stringResource(Res.string.miniplayer_buttons_config),
            stringResource(Res.string.player_swap_controls_with_timeline),
            stringResource(Res.string.timeline),
            stringResource(Res.string.transparentbar),
            stringResource(Res.string.player_controls_colors),
            stringResource(Res.string.play_button),
            stringResource(Res.string.buttonzoomout),
            stringResource(Res.string.icon_like_button),
        )
        val colorsCtx = settingsSearchCtx(
            search.inputValue,
            stringResource(Res.string.colors_and_background),
            stringResource(Res.string.background_colors),
            stringResource(Res.string.gradienttype),
            stringResource(Res.string.shake_sensitivity_theme),
            stringResource(Res.string.rotating_cover_title),
            stringResource(Res.string.blackgradient),
            stringResource(Res.string.bottomgradient),
            stringResource(Res.string.textoutline),
            stringResource(Res.string.show_total_time_of_queue),
            stringResource(Res.string.show_remaining_song_time),
            stringResource(Res.string.skip_time_buttons),
        )
        val behaviorCtx = settingsSearchCtx(
            search.inputValue,
            stringResource(Res.string.player_behavior_and_visuals),
            stringResource(Res.string.show_player_top_actions_bar),
            stringResource(Res.string.show_next_songs_in_player),
            stringResource(Res.string.disable_scrolling_text),
            stringResource(Res.string.disable_horizontal_swipe),
            stringResource(Res.string.player_swipe_sensitivity),
            stringResource(Res.string.player_rotating_buttons),
            stringResource(Res.string.background_progress_bar),
            stringResource(Res.string.visualizer),
            stringResource(Res.string.require_mic_permission),
        )
        val lyricsCtx = settingsSearchCtx(
            search.inputValue,
            stringResource(Res.string.txt_lyrics),
            stringResource(Res.string.show_lyrics_thumbnail),
            stringResource(Res.string.toggle_lyrics),
            stringResource(Res.string.click_lyrics_text),
            stringResource(Res.string.lyricscolor),
            stringResource(Res.string.save_lyrics_state),
            stringResource(Res.string.show_background_in_lyrics),
            stringResource(Res.string.player_enable_lyrics_popup_message),
        )
        val actionBarCtx = settingsSearchCtx(
            search.inputValue,
            stringResource(Res.string.player_action_bar),
            stringResource(Res.string.action_bar_transparent_background),
            stringResource(Res.string.actionspacedevenly),
            stringResource(Res.string.tapqueue),
            stringResource(Res.string.swipe_up_to_open_the_queue),
            stringResource(Res.string.player_action_bar_buttons),
        )
        val settingsResetCtx = settingsSearchCtx(search.inputValue, stringResource(Res.string.settings_reset))

        Column(
            modifier = Modifier
                .background(colorPalette().background0)
                .fillMaxHeight()
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            HeaderWithIcon(
                title = stringResource(Res.string.player_appearance),
                iconId = Res.drawable.color_palette,
                enabled = false,
                showIcon = true,
                modifier = Modifier,
                onClick = {},
            )

            SettingsDescription(
                text = stringResource(Res.string.appearance_settings_description),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )

            // Search Section
            search.ToolBarButton()
            search.SearchBar()

            // Presets & Layout Section (the phone's AS:847, spec §C)
            AnimatedVisibility(
                visible = presetsLayoutCtx,
                enter = fadeIn(animationSpec = tween(600)) + scaleIn(animationSpec = tween(600), initialScale = 0.9f),
            ) {
                SettingsSectionCard(
                    title = stringResource(Res.string.player_presets_and_layout),
                    icon = Res.drawable.ui,
                    content = {
                        // "Appearance Presets" (AS:1130, action → the phone's `AppearancePresetDialog`)
                        // — INERT, shown only in PORTRAIT (the phone's `!isLandscape`, DYNAMIC): the
                        // phone's 6 preset bundles write ~30 phone keys with no PC equivalent — a
                        // click has no effect, a long click toasts the phone's description
                        if (vis.appearancePresets) {
                            OtherSettingsEntry(
                                title = stringResource(Res.string.appearancepresets),
                                text = stringResource(Res.string.appearancepresetssecondary),
                                icon = Res.drawable.ui,
                                onClick = {},
                                onLongClick = { Toaster.i(Res.string.appearancepresetssecondary) },
                            )
                        }

                        // "Player type" (AS:1146, the phone's `playerTypeKey`, Essential) — INERT:
                        // the PC's player is coded Essential (the phone's default); the phone's
                        // default is shown, no dialog, a click has no effect; no description → no
                        // toast
                        OtherSettingsEntry(
                            title = stringResource(Res.string.playertype),
                            text = stringResource(Res.string.pcontrols_essential),
                            icon = Res.drawable.play,
                            onClick = {},
                        )

                        // "Queue type" (AS:1163, the phone's `queueTypeKey`, Modern) — INERT: the
                        // phone's default (Modern) shown, no dialog, a click has no effect; no
                        // description → no toast
                        OtherSettingsEntry(
                            title = stringResource(Res.string.queuetype),
                            text = stringResource(Res.string.pcontrols_modern),
                            icon = Res.drawable.sort_grid,
                            onClick = {},
                        )

                        // "Thumbnail type" (AS:1180, the phone's `thumbnailTypeKey`, Modern) —
                        // INERT: the phone's default (Modern) shown, no dialog, a click has no
                        // effect; no description → no toast
                        OtherSettingsEntry(
                            title = stringResource(Res.string.thumbnailtype),
                            text = stringResource(Res.string.pcontrols_modern),
                            icon = Res.drawable.cd,
                            onClick = {},
                            modifier = Modifier.padding(start = subIndent),
                        )

                        // "Header style" (AS:1198, the phone's `playerInfoTypeKey`, Modern) — INERT:
                        // the phone's default (Modern) shown, no dialog, a click has no effect; no
                        // description → no toast
                        OtherSettingsEntry(
                            title = stringResource(Res.string.pinfo_type),
                            text = stringResource(Res.string.pcontrols_modern),
                            icon = Res.drawable.information,
                            onClick = {},
                        )

                        // "Album and Artist header" (AS:1206, the phone's bare `SettingsDescription`)
                        // — read-only info row, as on the phone
                        SettingsInfoEntry(
                            title = stringResource(Res.string.pinfo_album_and_artist_name),
                            text = "",
                            icon = Res.drawable.information,
                        )

                        // "Show info with icons" (AS:1218, the phone's `playerInfoShowIconsKey`, on)
                        // — INERT: the phone's condition (`playerInfoType == Modern`) is met (the
                        // PC's header is coded Modern) — the default switch state, a click has no
                        // effect; the phone's row shows no description → no toast
                        ToggleSettingsEntry(
                            title = stringResource(Res.string.pinfo_show_icons),
                            text = "",
                            icon = Res.drawable.information,
                            isChecked = true,
                            enabled = false,
                            onCheckedChange = {},
                            modifier = Modifier.padding(start = 25.dp),
                        )

                        // "Control style" (AS:1239, the phone's `playerControlsTypeKey`, Essential)
                        // — INERT: the phone's default (Essential) shown, no dialog, a click has no
                        // effect; no description → no toast
                        OtherSettingsEntry(
                            title = stringResource(Res.string.pcontrols_type),
                            text = stringResource(Res.string.pcontrols_essential),
                            icon = Res.drawable.burger,
                            onClick = {},
                        )

                        // "Favorite button" (AS:1256, the phone's `iconLikeTypeKey`, Essential, 7
                        // values) — INERT: the phone's default (Essential) shown, no dialog, a
                        // click has no effect; no description → no toast
                        OtherSettingsEntry(
                            title = stringResource(Res.string.icon_like_button),
                            text = stringResource(Res.string.pcontrols_essential),
                            icon = Res.drawable.heart,
                            onClick = {},
                        )
                    },
                )
            }

            // Cover & Thumbnail Section (the phone's AS:1275, spec §C)
            AnimatedVisibility(
                visible = coverThumbnailCtx,
                enter = fadeIn(animationSpec = tween(600)) + scaleIn(animationSpec = tween(600), initialScale = 0.9f),
            ) {
                SettingsSectionCard(
                    title = stringResource(Res.string.cover_and_thumbnail),
                    icon = Res.drawable.album,
                    content = {
                        // "Show thumbnail" (AS:1287, the phone's `showthumbnailKey`) — INERT, shown
                        // only while the background is the blurred cover (LIVE from the PC key):
                        // the phone's effective value is always on (its composition effects
                        // AS:817-825 + its default), so the switch shows on, non-actionnable; the
                        // phone's row shows no description → no toast
                        if (vis.showThumbnail) {
                            ToggleSettingsEntry(
                                title = stringResource(Res.string.show_thumbnail),
                                text = "",
                                icon = Res.drawable.image,
                                isChecked = true,
                                enabled = false,
                                onCheckedChange = {},
                            )
                        }

                        /**
                         * NOT RENDERED (static, spec §C): the phone's "Swipe Animation" (AS:1303) —
                         * only shown when `!showthumbnail && playerType == Modern && !isLandscape`;
                         * the effective thumbnail is always on and the PC's player is coded
                         * Essential, so the condition is statically false. The PC key
                         * `swipeAnimationsNoThumbnail` exists (used by the player, `Thumbnail.kt`)
                         * but has no entry here.
                         */

                        /**
                         * NOT RENDERED (static, spec §C): the phone's "Fading edge" (AS:1330) —
                         * only shown when `showthumbnail && playerType == Modern`; the PC's player
                         * is coded Essential.
                         */

                        /**
                         * NOT RENDERED (static, spec §C): the phone's "Carousel view" (AS:1348) and
                         * "Thumbnail Size in Expanded Mode" (AS:1364) — only shown when
                         * `showthumbnail && playerType == Modern && !isLandscape &&
                         * (expandedPlayerToggle || expandedPlayer)`; the PC's player is coded
                         * Essential.
                         */

                        // "Shrink thumbnail when paused" (AS:1382, the phone's `thumbnailpauseKey`,
                        // on) — INERT: the phone's condition (`showthumbnail && playerType ==
                        // Essential`) is met (the PC's player is coded Essential) — the default
                        // switch state, a click has no effect; the phone's row shows no description
                        // → no toast
                        ToggleSettingsEntry(
                            title = stringResource(Res.string.thumbnailpause),
                            text = "",
                            icon = Res.drawable.pause,
                            isChecked = true,
                            enabled = false,
                            onCheckedChange = {},
                            modifier = Modifier.padding(start = subIndent),
                        )

                        // "Crop video thumbnails" (AS:1395, on) — INERT: the default switch state, a
                        // click has no effect; the phone's row shows no description → no toast
                        ToggleSettingsEntry(
                            title = stringResource(Res.string.crop_video_thumbnails),
                            text = "",
                            icon = Res.drawable.images_sharp,
                            isChecked = true,
                            enabled = false,
                            onCheckedChange = {},
                            modifier = Modifier.padding(start = subIndent),
                        )

                        // "Show cover thumbnail animation" (AS:1411, the PC key
                        // `showCoverThumbnailAnimation`, off) — WIRED
                        ToggleSettingsEntry(
                            title = stringResource(Res.string.show_cover_thumbnail_animation),
                            text = "",
                            icon = Res.drawable.images_sharp,
                            isChecked = settings.showCoverThumbnailAnimation,
                            onCheckedChange = {
                                preferences.update { s -> s.copy(showCoverThumbnailAnimation = it) }
                            },
                            modifier = Modifier.padding(start = subIndent),
                        )

                        // "Cover animation type" (AS:1423, the PC key `coverThumbnailAnimation`, 3
                        // values) — WIRED, shown only while "Show cover thumbnail animation" is on
                        // (LIVE from the PC key)
                        if (vis.coverAnimationType) {
                            OtherSettingsEntry(
                                title = stringResource(Res.string.cover_thumbnail_animation_type),
                                text = stringResource(settings.coverThumbnailAnimationValue.textId),
                                icon = Res.drawable.images_sharp,
                                onClick = { showCoverAnimationTypeDialog = true },
                                modifier = Modifier.padding(start = if (bg == PlayerBackgroundColors.BlurredCoverColor) 50.dp else 25.dp),
                            )
                        }

                        // "Thumbnail Size" (AS:1446 landscape / AS:1488 portrait, DYNAMIC from the
                        // window's orientation) — INERT: the phone's slider ported with its
                        // quick-value buttons, disabled at the phone's default (landscape 0 /
                        // portrait 90); no PC key, a click has no effect; the phone's row shows no
                        // description → no toast
                        if (vis.thumbnailSizeLandscape) {
                            InertSliderSettingsEntry(
                                title = stringResource(Res.string.player_thumbnail_size),
                                icon = Res.drawable.resize,
                                value = 0f,
                                range = 0f..100f,
                                defaultValue = 100f,
                                quickValues = listOf(0f to "Min", 25f to "25%", 50f to "50%", 75f to "75%", 100f to "Max"),
                                buttonsPadding = PaddingValues(horizontal = 32.dp, vertical = 4.dp),
                                trailingPreview = { InertThumbnailSizePreview(value = 0f, roundnessDp = 12f) },
                                modifier = Modifier.padding(start = subIndent),
                            )
                        } else if (vis.thumbnailSizePortrait) {
                            InertSliderSettingsEntry(
                                title = stringResource(Res.string.player_thumbnail_size),
                                icon = Res.drawable.resize,
                                value = 90f,
                                range = 0f..100f,
                                defaultValue = 90f,
                                quickValues = listOf(0f to "Min", 25f to "25%", 50f to "50%", 75f to "75%", 100f to "Max"),
                                buttonsPadding = PaddingValues(horizontal = 32.dp, vertical = 4.dp),
                                trailingPreview = { InertThumbnailSizePreview(value = 90f, roundnessDp = 12f) },
                                modifier = Modifier.padding(start = subIndent),
                            )
                        }

                        // "Thumbnail roundness" (AS:1532, the phone's `thumbnailRoundnessDpKey`,
                        // 12f) — INERT: the phone's slider ported with its quick-value buttons,
                        // disabled at the phone's default (12); no PC key, a click has no effect;
                        // the phone's row shows no description → no toast
                        InertSliderSettingsEntry(
                            title = stringResource(Res.string.thumbnail_roundness),
                            icon = Res.drawable.image,
                            value = 12f,
                            range = 0f..48f,
                            defaultValue = 12f,
                            quickValues = listOf(0f to "0", 12f to "12", 24f to "24", 36f to "36", 48f to "Max"),
                            buttonsPadding = PaddingValues(horizontal = 32.dp, vertical = 4.dp),
                            trailingPreview = { InertRoundnessPreview(roundnessDp = 12f) },
                            modifier = Modifier.padding(start = subIndent),
                        )

                        // "Artist roundness" (AS:1574, the phone's `artistThumbnailRoundnessDpKey`,
                        // 48f) — INERT: the phone's slider ported with its quick-value buttons,
                        // disabled at the phone's default (48 — the preview is a circle); no PC key,
                        // a click has no effect; the phone's row shows no description → no toast
                        // (the phone's search quirk, the literal "artist ui roundness", AS:1565 /
                        // 1570, is NOT ported — the ctx uses the entry's real label)
                        InertSliderSettingsEntry(
                            title = stringResource(Res.string.artist_roundness),
                            icon = Res.drawable.image,
                            value = 48f,
                            range = 0f..48f,
                            defaultValue = 48f,
                            quickValues = listOf(0f to "0", 12f to "12", 24f to "24", 36f to "36", 48f to "Max"),
                            buttonsPadding = PaddingValues(horizontal = 32.dp, vertical = 4.dp),
                            trailingPreview = { InertRoundnessPreview(roundnessDp = 48f) },
                            modifier = Modifier.padding(start = subIndent),
                        )

                        // "UI Roundness" (AS:1615, the phone's `uiRoundnessDpKey`, 25f) — INERT:
                        // the phone's slider ported with its quick-value buttons, disabled at the
                        // phone's default (25); no PC key, a click has no effect; the phone's row
                        // shows no description → no toast
                        InertSliderSettingsEntry(
                            title = stringResource(Res.string.ui_roundness),
                            icon = Res.drawable.ui,
                            value = 25f,
                            range = 0f..40f,
                            defaultValue = 25f,
                            quickValues = listOf(0f to "0", 10f to "10", 20f to "20", 30f to "30", 40f to "Max"),
                            buttonsPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                            trailingPreview = { InertUiRoundnessPreview(roundnessDp = 25f) },
                            modifier = Modifier.padding(start = subIndent),
                        )

                        /**
                         * NOT RENDERED (static, spec §C): the phone's "Remove background blur when
                         * lyrics is disabled" (AS:1659, `noblurKey`) — only shown when
                         * `!showthumbnail`; the effective thumbnail is always on.
                         */

                        /**
                         * NOT RENDERED (static, spec §C): the phone's "Stats for Nerds" (AS:1678,
                         * `statsfornerdsKey`) — only shown when `!(showthumbnail && playerType ==
                         * Essential)`; both halves of the condition are met on the PC.
                         */
                    },
                )
            }

            // Timeline & Controls Section (the phone's AS:1694, spec §C)
            AnimatedVisibility(
                visible = timelineCtx,
                enter = fadeIn(animationSpec = tween(600)) + scaleIn(animationSpec = tween(600), initialScale = 0.9f),
            ) {
                SettingsSectionCard(
                    title = stringResource(Res.string.timeline_and_controls),
                    icon = Res.drawable.horizontal_straight_line,
                    content = {
                        // "Player Timeline Size" (AS:1705, the phone's `playerTimelineSizeKey`,
                        // Biggest, 5 values) — INERT: the phone's default (Biggest) shown, no
                        // dialog, a click has no effect; no description → no toast
                        OtherSettingsEntry(
                            title = stringResource(Res.string.timelinesize),
                            text = stringResource(Res.string.biggest),
                            icon = Res.drawable.resize,
                            onClick = {},
                        )

                        // "MiniPlayer buttons" (AS:1724, action → the ported dialog, spec §D) —
                        // WIRED: its 2 functional rows (Discover / Visualizer) act on their PC keys
                        OtherSettingsEntry(
                            title = stringResource(Res.string.miniplayer_buttons_config),
                            text = stringResource(Res.string.miniplayer_buttons_config_description),
                            icon = Res.drawable.ui,
                            onClick = { showMpbDialog = true },
                        )

                        // "Swap the controls with the timeline" (AS:1738, off) — INERT: the default
                        // switch state, a click has no effect; the phone's row shows no
                        // description → no toast
                        ToggleSettingsEntry(
                            title = stringResource(Res.string.player_swap_controls_with_timeline),
                            text = "",
                            icon = Res.drawable.sync,
                            isChecked = false,
                            enabled = false,
                            onCheckedChange = {},
                        )

                        // "Timeline" (AS:1753, the PC key `playerTimelineType`, 8 values) — WIRED
                        OtherSettingsEntry(
                            title = stringResource(Res.string.timeline),
                            text = stringResource(settings.playerTimelineTypeValue.textId),
                            icon = Res.drawable.playing_indicator,
                            onClick = { showTimelineDialog = true },
                        )

                        // (info) "Visualizer effect require mic permission" (AS:1759) — shown only
                        // while the timeline is the visualizer bar (LIVE from the PC key), as the
                        // phone's `ImportantSettingsDescription`
                        if (vis.micPermissionInfo) {
                            SettingsDescription(
                                text = stringResource(Res.string.require_mic_permission),
                                important = true,
                            )
                        }

                        // "Transparent Timeline Bar" (AS:1771, on) — INERT: the default switch
                        // state, a click has no effect; the phone's row shows no description → no
                        // toast
                        ToggleSettingsEntry(
                            title = stringResource(Res.string.transparentbar),
                            text = "",
                            icon = Res.drawable.eye_off,
                            isChecked = true,
                            enabled = false,
                            onCheckedChange = {},
                        )

                        // "Player controls colors" (AS:1788, Monochrome, 3 values) — INERT: the
                        // phone's default (Monochrome) shown, no dialog, a click has no effect; no
                        // description → no toast
                        OtherSettingsEntry(
                            title = stringResource(Res.string.player_controls_colors),
                            text = stringResource(Res.string.monochrome),
                            icon = Res.drawable.color_palette,
                            onClick = {},
                        )

                        // "Play button" (AS:1806, the phone's `playerPlayButtonTypeKey`,
                        // CircularRibbed, 5 values) — INERT: the phone's default shown (the phone's
                        // "Wavy circle" label), no dialog, a click has no effect; no description →
                        // no toast
                        OtherSettingsEntry(
                            title = stringResource(Res.string.play_button),
                            text = stringResource(Res.string.circular_ribbed),
                            icon = Res.drawable.play,
                            onClick = {},
                        )

                        // "Button zoom-out animation" (AS:1824, on) — INERT: the default switch
                        // state, a click has no effect; the phone's row shows no description → no
                        // toast
                        ToggleSettingsEntry(
                            title = stringResource(Res.string.buttonzoomout),
                            text = "",
                            icon = Res.drawable.resize,
                            isChecked = true,
                            enabled = false,
                            onCheckedChange = {},
                        )

                        // "Favorite button" (the phone's DUPLICATE row, AS:1840 — ported as-is,
                        // spec §C: the phone's search conditions are inconsistent (AS:1832 /
                        // 1837), so the phone renders the entry twice) — INERT, as the AS:1256 row
                        OtherSettingsEntry(
                            title = stringResource(Res.string.icon_like_button),
                            text = stringResource(Res.string.pcontrols_essential),
                            icon = Res.drawable.play,
                            onClick = {},
                        )
                    },
                )
            }

            // Colors & Background Section (the phone's AS:1857, spec §C)
            AnimatedVisibility(
                visible = colorsCtx,
                enter = fadeIn(animationSpec = tween(600)) + scaleIn(animationSpec = tween(600), initialScale = 0.9f),
            ) {
                SettingsSectionCard(
                    title = stringResource(Res.string.colors_and_background),
                    icon = Res.drawable.color_palette,
                    content = {
                        // "Background colors" (AS:1868, the PC key `playerBackgroundColors`, 7
                        // values) — WIRED
                        OtherSettingsEntry(
                            title = stringResource(Res.string.background_colors),
                            text = stringResource(settings.playerBackgroundColorsValue.textId),
                            icon = Res.drawable.droplet,
                            onClick = { showBackgroundColorsDialog = true },
                        )

                        // "Gradient Type" (AS:1886, the PC key `animatedGradient`, 17 values) —
                        // WIRED, shown only while the background is the animated gradient (LIVE
                        // from the PC key, true by default)
                        if (vis.gradientType) {
                            OtherSettingsEntry(
                                title = stringResource(Res.string.gradienttype),
                                text = stringResource(settings.animatedGradientValue.textId),
                                icon = Res.drawable.color_palette,
                                onClick = { showGradientTypeDialog = true },
                                modifier = Modifier.padding(start = 25.dp),
                            )
                        }

                        // "Theme Shake Sensitivity" (AS:1902, the phone's `ShakeSensitivityTheme`,
                        // Disabled, 5 values) — INERT, shown only while the background is the
                        // animated gradient AND the gradient is an M3E one (LIVE, both keys PC,
                        // true by default): the phone's default (Disabled) is shown, no dialog (the
                        // enum is phone-only), a click has no effect; no description → no toast
                        if (vis.themeShakeSensitivity) {
                            OtherSettingsEntry(
                                title = stringResource(Res.string.shake_sensitivity_theme),
                                text = stringResource(Res.string.shake_sensitivity_theme_disabled),
                                icon = Res.drawable.shake_gesture,
                                onClick = {},
                                modifier = Modifier.padding(start = 25.dp),
                            )
                        }

                        // "Rotating album cover" (AS:1916, the PC key `rotatingAlbumCover`, off) —
                        // WIRED, shown only while the background is the blurred cover (LIVE from
                        // the PC key)
                        if (vis.rotatingAlbumCover) {
                            ToggleSettingsEntry(
                                title = stringResource(Res.string.rotating_cover_title),
                                text = "",
                                icon = Res.drawable.sync,
                                isChecked = settings.rotatingAlbumCover,
                                onCheckedChange = {
                                    preferences.update { s -> s.copy(rotatingAlbumCover = it) }
                                },
                                modifier = Modifier.padding(start = 25.dp),
                            )
                        }

                        // "Black Gradient at Bottom" (AS:1935, the phone's `blackgradientKey`,
                        // off) — INERT, shown only while the background is one of the two color
                        // gradients (LIVE from the PC key): the default switch state, a click has
                        // no effect; the phone's row shows no description → no toast
                        if (vis.blackGradientAtBottom) {
                            ToggleSettingsEntry(
                                title = stringResource(Res.string.blackgradient),
                                text = "",
                                icon = Res.drawable.drop_half_fill,
                                isChecked = false,
                                enabled = false,
                                onCheckedChange = {},
                            )
                        }

                        /**
                         * NOT RENDERED (static, spec §C): the phone's "Background Rotation
                         * Animation" (AS:1951, `albumCoverRotationKey`) — only shown when
                         * `bg == BlurredCoverColor && playerType == Modern`; the PC's player is
                         * coded Essential.
                         */

                        // "Enable action bar gradient" (AS:1969, the phone's `bottomgradientKey`,
                        // off) — INERT, shown only while the background is the blurred cover (LIVE
                        // from the PC key): the default switch state, a click has no effect; the
                        // phone's row shows no description → no toast
                        if (vis.enableActionBarGradient) {
                            ToggleSettingsEntry(
                                title = stringResource(Res.string.bottomgradient),
                                text = "",
                                icon = Res.drawable.droplet,
                                isChecked = false,
                                enabled = false,
                                onCheckedChange = {},
                            )
                        }

                        // "Enable Text Outline" (AS:1983, off) — INERT: the default switch state,
                        // a click has no effect; the phone's row shows no description → no toast
                        ToggleSettingsEntry(
                            title = stringResource(Res.string.textoutline),
                            text = "",
                            icon = Res.drawable.text,
                            isChecked = false,
                            enabled = false,
                            onCheckedChange = {},
                        )

                        // "Show total queue duration" (AS:1998, on) — INERT: the default switch
                        // state, a click has no effect; the phone's row shows no description → no
                        // toast
                        ToggleSettingsEntry(
                            title = stringResource(Res.string.show_total_time_of_queue),
                            text = "",
                            icon = Res.drawable.time,
                            isChecked = true,
                            enabled = false,
                            onCheckedChange = {},
                        )

                        // "Show remaining song duration" (AS:2013, on) — INERT: the default switch
                        // state, a click has no effect; the phone's row shows no description → no
                        // toast
                        ToggleSettingsEntry(
                            title = stringResource(Res.string.show_remaining_song_time),
                            text = "",
                            icon = Res.drawable.time,
                            isChecked = true,
                            enabled = false,
                            onCheckedChange = {},
                        )

                        // "Skip time buttons" (AS:2027, on) — INERT: the default switch state, a
                        // click has no effect; the phone's row shows no description → no toast
                        ToggleSettingsEntry(
                            title = stringResource(Res.string.skip_time_buttons),
                            text = "",
                            icon = Res.drawable.play_forward,
                            isChecked = true,
                            enabled = false,
                            onCheckedChange = {},
                        )
                    },
                )
            }

            // Player Behavior & Visuals Section (the phone's AS:2044, spec §C)
            AnimatedVisibility(
                visible = behaviorCtx,
                enter = fadeIn(animationSpec = tween(900)) + scaleIn(animationSpec = tween(900), initialScale = 0.9f),
            ) {
                SettingsSectionCard(
                    title = stringResource(Res.string.player_behavior_and_visuals),
                    icon = Res.drawable.equalizer,
                    content = {
                        // "Show top actions bar" (AS:2056, the phone's `showTopActionsBarKey`, on)
                        // — INERT, shown only in PORTRAIT (the phone's `!isLandscape`, DYNAMIC):
                        // the default switch state, a click has no effect; the phone's row shows
                        // no description → no toast
                        if (vis.showTopActionsBar) {
                            ToggleSettingsEntry(
                                title = stringResource(Res.string.show_player_top_actions_bar),
                                text = "",
                                icon = Res.drawable.enqueue,
                                isChecked = true,
                                enabled = false,
                                onCheckedChange = {},
                            )
                        }

                        /**
                         * NOT RENDERED (static, spec §C): the phone's "Blank Space at Top"
                         * (AS:2072, `topPaddingKey`) — only shown when `!isLandscape &&
                         * !showTopActionsBar`; `showTopActionsBar` is on by default on the phone,
                         * so the condition is statically false.
                         */

                        // "Show next song queue" (AS:2089, off) — INERT: the default switch state,
                        // a click has no effect; the phone's row shows no description → no toast
                        ToggleSettingsEntry(
                            title = stringResource(Res.string.show_next_songs_in_player),
                            text = "",
                            icon = Res.drawable.arrow_forward,
                            isChecked = false,
                            enabled = false,
                            onCheckedChange = {},
                        )

                        /**
                         * NOT RENDERED (static, spec §C): the phone's "Songs displayed" (AS:2100,
                         * `showsongsKey`) and "Show album cover" (AS:2119, `showalbumcoverKey`) —
                         * only shown when `showNextSongsInPlayer`; off by default on the phone.
                         */

                        // "Disable scrolling text" (AS:2137, the PC key `disableScrollingText`) —
                        // WIRED: MOVED from the PC's former "Others" card (the old Autres tab),
                        // re-ported with the phone's description as its text
                        ToggleSettingsEntry(
                            title = stringResource(Res.string.disable_scrolling_text),
                            text = stringResource(Res.string.scrolling_text_is_used_for_long_texts),
                            icon = Res.drawable.text,
                            isChecked = settings.disableScrollingText,
                            onCheckedChange = {
                                preferences.update { s -> s.copy(disableScrollingText = it) }
                            },
                        )

                        // "Disable song switching via swipe" (AS:2152, the phone's
                        // `disablePlayerHorizontalSwipeKey`, off) — INERT: `playerType` is
                        // statically Essential, so it is never the phone's vertical-swipe variant —
                        // the default switch state, a click has no effect, a long click toasts the
                        // phone's description
                        ToggleSettingsEntry(
                            title = stringResource(Res.string.disable_horizontal_swipe),
                            text = stringResource(Res.string.disable_song_switching_via_swipe),
                            icon = Res.drawable.sync,
                            isChecked = false,
                            enabled = false,
                            onCheckedChange = {},
                            onLongClick = { Toaster.i(Res.string.disable_song_switching_via_swipe) },
                        )

                        // "Player Swipe Sensitivity" (AS:2163, the PC key
                        // `playerSwipeSensitivity`, 4 values) — WIRED: the phone's condition
                        // (`!disablePlayerHorizontalSwipe`) is met (its key is off by default)
                        OtherSettingsEntry(
                            title = stringResource(Res.string.player_swipe_sensitivity),
                            text = stringResource(settings.playerSwipeSensitivityValue.textId),
                            icon = Res.drawable.gesture,
                            onClick = { showSwipeSensitivityDialog = true },
                            modifier = Modifier.padding(start = 25.dp),
                        )

                        // "Rotating buttons" (AS:2180, off) — INERT: the default switch state, a
                        // click has no effect, a long click toasts the phone's description
                        ToggleSettingsEntry(
                            title = stringResource(Res.string.player_rotating_buttons),
                            text = stringResource(Res.string.player_enable_rotation_buttons),
                            icon = Res.drawable.refresh,
                            isChecked = false,
                            enabled = false,
                            onCheckedChange = {},
                            onLongClick = { Toaster.i(Res.string.player_enable_rotation_buttons) },
                        )

                        // "Background progress bar" (AS:2197, the phone's `backgroundProgressKey`,
                        // MiniPlayer, 4 values) — INERT: the phone's default (the phone's
                        // "Minimized player" label) shown, no dialog, a click has no effect; no
                        // description → no toast
                        OtherSettingsEntry(
                            title = stringResource(Res.string.background_progress_bar),
                            text = stringResource(Res.string.minimized_player),
                            icon = Res.drawable.horizontal_straight_line,
                            onClick = {},
                        )

                        // "Visualizer" (AS:2214, the phone's `visualizerEnabledKey`, off) — INERT
                        // (no PC key): the default switch state, a click has no effect; the
                        // phone's row shows no description → no toast
                        ToggleSettingsEntry(
                            title = stringResource(Res.string.visualizer),
                            text = "",
                            icon = Res.drawable.sound_effect,
                            isChecked = false,
                            enabled = false,
                            onCheckedChange = {},
                        )

                        /**
                         * NOT RENDERED (static, spec §C): the phone's 7 visualizer sub-entries —
                         * "Save visualizer state" (AS:2227), "Visualizers Color" (AS:2237),
                         * "Visualizer Line Thickness" (AS:2249), "Custom Color" (AS:2268, the color
                         * picker is not ported), "Show thumbnail for visualizer" (AS:2295), "Dark
                         * background for visualizer" (AS:2306) and "Auto-hide visualizer buttons"
                         * (AS:2317) — all only shown when `visualizerEnabled`, off by default on
                         * the phone.
                         */

                        // (info) "Visualizer effect require mic permission" (AS:2328) — always
                        // shown, end of section, as the phone's `ImportantSettingsDescription`
                        SettingsDescription(
                            text = stringResource(Res.string.require_mic_permission),
                            important = true,
                        )
                    },
                )
            }

            // Lyrics Section (the phone's AS:2341, spec §C)
            AnimatedVisibility(
                visible = lyricsCtx,
                enter = fadeIn(animationSpec = tween(700)) + scaleIn(animationSpec = tween(700), initialScale = 0.9f),
            ) {
                SettingsSectionCard(
                    title = stringResource(Res.string.txt_lyrics),
                    icon = Res.drawable.song_lyrics,
                    content = {
                        // "Show thumbnail for lyrics" (AS:2352, the phone's
                        // `showlyricsthumbnailKey`, on) — INERT: the phone only forces it off for
                        // the Modern player type / no thumbnail → never on the PC — the default
                        // switch state, a click has no effect; the phone's row shows no
                        // description → no toast
                        ToggleSettingsEntry(
                            title = stringResource(Res.string.show_lyrics_thumbnail),
                            text = "",
                            icon = Res.drawable.image,
                            isChecked = true,
                            enabled = false,
                            onCheckedChange = {},
                        )

                        // "Toggle lyrics" (AS:2367, on) — INERT: the default switch state, a click
                        // has no effect, a long click toasts the phone's description
                        ToggleSettingsEntry(
                            title = stringResource(Res.string.toggle_lyrics),
                            text = stringResource(Res.string.by_tapping_on_the_thumbnail),
                            icon = Res.drawable.song_lyrics,
                            isChecked = true,
                            enabled = false,
                            onCheckedChange = {},
                            onLongClick = { Toaster.i(Res.string.by_tapping_on_the_thumbnail) },
                        )

                        // "Jump to lyrics by pressing the line" (AS:2382, on) — INERT: the default
                        // switch state, a click has no effect; the phone's row shows no
                        // description → no toast
                        ToggleSettingsEntry(
                            title = stringResource(Res.string.click_lyrics_text),
                            text = "",
                            icon = Res.drawable.arrow_down,
                            isChecked = true,
                            enabled = false,
                            onCheckedChange = {},
                        )

                        // "Lyrics Color" (AS:2391, the phone's `LyricsColor`, White, 4 values) —
                        // INERT: the phone's default (White) shown, no dialog (the color picker is
                        // not ported), a click has no effect; no description → no toast
                        OtherSettingsEntry(
                            title = stringResource(Res.string.lyricscolor),
                            text = stringResource(Res.string.color_white),
                            icon = Res.drawable.color_palette,
                            onClick = {},
                        )

                        /**
                         * NOT RENDERED (static, spec §C): the phone's "Custom Color" (AS:2404) —
                         * only shown when `lyricsColor == Custom`; White is the phone's default
                         * and the color picker is not ported.
                         */

                        // "Save lyrics state" (AS:2436, off) — INERT: the default switch state, a
                        // click has no effect, a long click toasts the phone's description
                        ToggleSettingsEntry(
                            title = stringResource(Res.string.save_lyrics_state),
                            text = stringResource(Res.string.save_lyrics_state_description),
                            icon = Res.drawable.bookmark,
                            isChecked = false,
                            enabled = false,
                            onCheckedChange = {},
                            onLongClick = { Toaster.i(Res.string.save_lyrics_state_description) },
                        )

                        // "Match Lyrics background color with Player" (AS:2452, the phone's
                        // `showBackgroundLyricsKey`, off) — INERT: the phone's condition
                        // (`showlyricsthumbnail`) is met (on by default) — the default switch
                        // state, a click has no effect; the phone's row shows no description → no
                        // toast
                        ToggleSettingsEntry(
                            title = stringResource(Res.string.show_background_in_lyrics),
                            text = "",
                            icon = Res.drawable.image,
                            isChecked = false,
                            enabled = false,
                            onCheckedChange = {},
                        )

                        // "Enable lyrics popup message" (AS:2467, on) — INERT: the default switch
                        // state, a click has no effect; the phone's row shows no description → no
                        // toast
                        ToggleSettingsEntry(
                            title = stringResource(Res.string.player_enable_lyrics_popup_message),
                            text = "",
                            icon = Res.drawable.alert,
                            isChecked = true,
                            enabled = false,
                            onCheckedChange = {},
                        )
                    },
                )
            }

            // Player action bar Section (the phone's AS:2487, spec §C)
            AnimatedVisibility(
                visible = actionBarCtx,
                enter = fadeIn(animationSpec = tween(700)) + scaleIn(animationSpec = tween(700), initialScale = 0.9f),
            ) {
                SettingsSectionCard(
                    title = stringResource(Res.string.player_action_bar),
                    icon = Res.drawable.ellipsis_vertical,
                    content = {
                        // "Transparent background" (AS:2499, on) — INERT: the default switch
                        // state, a click has no effect; the phone's row shows no description → no
                        // toast
                        ToggleSettingsEntry(
                            title = stringResource(Res.string.action_bar_transparent_background),
                            text = "",
                            icon = Res.drawable.eye,
                            isChecked = true,
                            enabled = false,
                            onCheckedChange = {},
                        )

                        // "Spaced evenly" (AS:2514, on) — INERT: the default switch state, a click
                        // has no effect; the phone's row shows no description → no toast
                        ToggleSettingsEntry(
                            title = stringResource(Res.string.actionspacedevenly),
                            text = "",
                            icon = Res.drawable.horizontal_bold_line,
                            isChecked = true,
                            enabled = false,
                            onCheckedChange = {},
                        )

                        // "Tap to open queue" (AS:2529, on) — INERT: the default switch state, a
                        // click has no effect; the phone's row shows no description → no toast
                        ToggleSettingsEntry(
                            title = stringResource(Res.string.tapqueue),
                            text = "",
                            icon = Res.drawable.gesture,
                            isChecked = true,
                            enabled = false,
                            onCheckedChange = {},
                        )

                        // "Swipe up to open queue" (AS:2544, on) — INERT: the default switch
                        // state, a click has no effect; the phone's row shows no description → no
                        // toast
                        ToggleSettingsEntry(
                            title = stringResource(Res.string.swipe_up_to_open_the_queue),
                            text = "",
                            icon = Res.drawable.gesture,
                            isChecked = true,
                            enabled = false,
                            onCheckedChange = {},
                        )

                        // "Button visibility" (AS:2559, action → the phone's
                        // `PlayerActionBarSettingsDialog`) — INERT: the dialog is 100 %
                        // phone-side (it reorders / shows / hides the phone's action-bar buttons);
                        // on the PC a click has no effect, a long click toasts the phone's
                        // description
                        OtherSettingsEntry(
                            title = stringResource(Res.string.player_action_bar_buttons),
                            text = stringResource(Res.string.player_action_bar_buttons_description),
                            icon = Res.drawable.ellipsis_vertical,
                            onClick = {},
                            onLongClick = { Toaster.i(Res.string.player_action_bar_buttons_description) },
                        )
                    },
                )
            }

            /**
             * NOT RENDERED (static, spec §C): the phone's "Fullscreen lyrics components" section
             * (AS:2580, 7 entries: AS:2593 / 2609 / 2624 / 2639 / 2656 / 2686 / 2704) — only shown
             * when `!showlyricsthumbnail`; on by default on the phone.
             */

            /**
             * NOT RENDERED ⛔ (spec §C): the phone's "Notification Player" section (AS:2729) — its
             * "First button" (AS:2741) and "Second button" (AS:2753) select the Android
             * notification's buttons; the PC has no notification player (and the phone's
             * service-restart side effect has no equivalent).
             */

            // Reset Section (the phone's AS:2777, spec §C)
            AnimatedVisibility(
                visible = settingsResetCtx,
                enter = fadeIn(animationSpec = tween(1100)) + scaleIn(animationSpec = tween(1100), initialScale = 0.9f),
            ) {
                SettingsSectionCard(
                    title = stringResource(Res.string.settings_reset),
                    icon = Res.drawable.refresh,
                    content = {
                        // "Reset" (AS:2784) — INERT: the phone resets ITS appearance settings here
                        // (its `DefaultAppearanceSettings` + the phone-side `reset()` of the MPB /
                        // action-bar dialogs); on the PC a click has no effect — the 2 MPB keys are
                        // NOT reset (there is no PC default bundle) — a long click toasts the
                        // phone's description
                        OtherSettingsEntry(
                            title = stringResource(Res.string.settings_reset),
                            text = stringResource(Res.string.settings_restore_default_settings),
                            icon = Res.drawable.refresh,
                            onClick = {},
                            onLongClick = { Toaster.i(Res.string.settings_restore_default_settings) },
                        )
                    },
                )
            }

            // ===== The value-selector dialogs of the wired entries (the phone's dialogs, ported) =====

            if (showBackgroundColorsDialog) {
                ValueSelectorDialog(
                    title = stringResource(Res.string.background_colors),
                    values = PlayerBackgroundColors.entries.toList(),
                    selectedValue = settings.playerBackgroundColorsValue,
                    onValueSelected = {
                        preferences.update { s -> s.copy(playerBackgroundColors = it.name) }
                        showBackgroundColorsDialog = false
                    },
                    onDismiss = { showBackgroundColorsDialog = false },
                    valueText = { stringResource(it.textId) },
                )
            }

            if (showGradientTypeDialog) {
                ValueSelectorDialog(
                    title = stringResource(Res.string.gradienttype),
                    values = AnimatedGradient.entries.toList(),
                    selectedValue = settings.animatedGradientValue,
                    onValueSelected = {
                        preferences.update { s -> s.copy(animatedGradient = it.name) }
                        showGradientTypeDialog = false
                    },
                    onDismiss = { showGradientTypeDialog = false },
                    valueText = { stringResource(it.textId) },
                )
            }

            if (showTimelineDialog) {
                ValueSelectorDialog(
                    title = stringResource(Res.string.timeline),
                    values = PlayerTimelineType.entries.toList(),
                    selectedValue = settings.playerTimelineTypeValue,
                    onValueSelected = {
                        preferences.update { s -> s.copy(playerTimelineType = it.name) }
                        showTimelineDialog = false
                    },
                    onDismiss = { showTimelineDialog = false },
                    valueText = { stringResource(it.textId) },
                )
            }

            if (showCoverAnimationTypeDialog) {
                ValueSelectorDialog(
                    title = stringResource(Res.string.cover_thumbnail_animation_type),
                    values = ThumbnailCoverType.entries.toList(),
                    selectedValue = settings.coverThumbnailAnimationValue,
                    onValueSelected = {
                        preferences.update { s -> s.copy(coverThumbnailAnimation = it.name) }
                        showCoverAnimationTypeDialog = false
                    },
                    onDismiss = { showCoverAnimationTypeDialog = false },
                    valueText = { stringResource(it.textId) },
                )
            }

            if (showSwipeSensitivityDialog) {
                ValueSelectorDialog(
                    title = stringResource(Res.string.player_swipe_sensitivity),
                    values = PlayerSwipeSensitivity.entries.toList(),
                    selectedValue = settings.playerSwipeSensitivityValue,
                    onValueSelected = {
                        preferences.update { s -> s.copy(playerSwipeSensitivity = it.name) }
                        showSwipeSensitivityDialog = false
                    },
                    onDismiss = { showSwipeSensitivityDialog = false },
                    valueText = { stringResource(it.textId) },
                )
            }

            // "MiniPlayer buttons" (AS:1724, spec §D) — the ported dialog, opened from the card
            if (showMpbDialog) {
                MiniPlayerButtonsSettingsDialog(
                    preferences = preferences,
                    onDismiss = { showMpbDialog = false },
                )
            }
        }
    }
}
