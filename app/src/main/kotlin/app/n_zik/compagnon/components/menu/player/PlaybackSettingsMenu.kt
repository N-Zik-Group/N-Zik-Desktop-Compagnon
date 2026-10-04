package app.n_zik.compagnon.components.menu.player

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.n_zik.compagnon.LocalPlayerRepository
import app.n_zik.compagnon.bridge.ConnectionState
import app.n_zik.compagnon.bridge.state.SessionContract
import app.n_zik.compagnon.colorPalette
import app.n_zik.compagnon.components.menu.ListMenu
import app.n_zik.compagnon.components.ui.sliders.SliderControl
import app.n_zik.compagnon.generated.resources.Res
import app.n_zik.compagnon.generated.resources.controls_header_customize
import app.n_zik.compagnon.generated.resources.controls_title_playback_volume
import app.n_zik.compagnon.generated.resources.volume
import app.n_zik.compagnon.generated.resources.volume_up
import app.n_zik.compagnon.utils.LocalPreferences
import app.n_zik.compagnon.utils.UserSettings
import kotlinx.coroutines.flow.MutableStateFlow
import app.n_zik.compagnon.generated.resources.controls_title_playback_speed
import app.n_zik.compagnon.generated.resources.playback
import app.n_zik.compagnon.generated.resources.slow_motion
import app.n_zik.compagnon.typography
import app.n_zik.compagnon.uiRoundnessShape
import app.n_zik.compagnon.utils.semiBold
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Port of `PlaybackSettingsMenu` (phone's `app/n_zik/android/components/menu/player/PlaybackSettingsMenu.kt`),
 * opened by a long press (a right click on the PC) on the player's play button, in its default list style.
 *
 * Kept: the "Customize" list menu, the "Playback" section and its "Playback speed" slider (`player/speed`),
 * the "Volume" section with its "Playback volume" slider (story 12: the PC's own player, local and persisted,
 * contract §8.5).
 * Dropped (contract v1 has no such command, or nothing to apply it to on the PC): pitch, medley duration,
 * the device volume (Windows' volume), blur, bass boost, loudness and their sections, and the grid style.
 * The speed slider has the phone's range (0.1–10, story 11c); the command stays bounded to the contract's
 * 0.25–4.0 (§9), so a value outside is sent at the nearest bound. PC only: the speed is sent when the slider
 * is released (the phone applies each step to its own player); while dragging, the slider shows the dragged
 * value, then follows the phone again.
 */
class PlaybackSettingsMenu private constructor(
    private val onSpeed: (Float) -> Unit,
) {

    companion object {
        fun create(onSpeed: (Float) -> Unit): PlaybackSettingsMenu = PlaybackSettingsMenu(onSpeed)
    }

    @Composable
    fun ListMenu() {
        val repository = LocalPlayerRepository.current
        val state = repository?.state?.collectAsState()?.value
        val enabled = repository?.connection?.collectAsState()?.value == ConnectionState.Live
        var dragged by remember { mutableStateOf<Float?>(null) }
        val playbackSpeed = dragged ?: state?.speed ?: 1f
        val preferences = LocalPreferences.current
        val settings = preferences?.settings?.collectAsState()?.value
        var draggedVolume by remember { mutableStateOf<Float?>(null) }
        val playbackVolume = draggedVolume ?: settings?.playbackVolume ?: 1f

        ListMenu.Menu(title = stringResource(Res.string.controls_header_customize)) {
            // Section: Playback
            SectionTitle(stringResource(Res.string.playback))

            // Speed
            ListSliderMenuItem(
                icon = Res.drawable.slow_motion,
                title = stringResource(Res.string.controls_title_playback_speed),
                value = playbackSpeed,
                onValueChange = {
                    val rounded = kotlin.math.round(it * 10f) / 10f
                    dragged = rounded
                },
                onSlideComplete = {
                    dragged?.let { value ->
                        onSpeed(value.coerceIn(SessionContract.SPEED_MIN, SessionContract.SPEED_MAX))
                    }
                    dragged = null
                },
                valueRange = PHONE_SPEED_MIN..PHONE_SPEED_MAX,
                displayValue = { "%.1fx".format(it).replace(",", ".") },
                stepSize = 0f,
                defaultValue = 1f,
                drawValuePoints = true,
                isEnabled = enabled,
                onReset = { onSpeed(1f) },
            )

            // Section: Volume
            SectionTitle(stringResource(Res.string.volume))

            // Playback Volume (written when the slider is released)
            ListSliderMenuItem(
                icon = Res.drawable.volume_up,
                title = stringResource(Res.string.controls_title_playback_volume),
                value = playbackVolume,
                onValueChange = {
                    val rounded = kotlin.math.round(it * 100f) / 100f
                    draggedVolume = rounded
                },
                onSlideComplete = {
                    draggedVolume?.let { value -> preferences?.update { it.copy(playbackVolume = value.coerceIn(0f, 1f)) } }
                    draggedVolume = null
                },
                valueRange = 0f..1f,
                displayValue = { "${(it * 100).toInt()}%" },
                stepSize = 0f,
                defaultValue = 1f,
                drawValuePoints = true,
                isEnabled = preferences != null,
                onReset = { preferences?.update { it.copy(playbackVolume = 1f) } },
            )
        }
    }

    @Composable
    fun MenuComponent() = ListMenu()

    @Composable
    private fun SectionTitle(title: String) {
        BasicText(
            text = title,
            style = typography().xxs.semiBold.copy(
                color = colorPalette().accent,
                textAlign = TextAlign.Start,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 4.dp),
        )
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    private fun ListSliderMenuItem(
        icon: DrawableResource,
        title: String,
        value: Float,
        onValueChange: (Float) -> Unit,
        onSlideComplete: () -> Unit = {},
        valueRange: ClosedFloatingPointRange<Float>,
        displayValue: @Composable (Float) -> String,
        onReset: () -> Unit,
        isEnabled: Boolean = true,
        stepSize: Float = 0.1f,
        defaultValue: Float? = null,
        drawValuePoints: Boolean = false,
    ) {
        // The PC's "Disable scrolling text" (the phone's `disableScrollingTextKey`, its
        // `PlaybackSettingsMenu.kt` 645, 764): the title's marquee is dropped when set
        val preferences = LocalPreferences.current
        val settings by (preferences?.settings ?: remember { MutableStateFlow(UserSettings()) }).collectAsState()

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp)
                .let { if (!isEnabled) it.then(Modifier.alpha(0.5f)) else it },
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(
                        color = colorPalette().accent.copy(alpha = 0.1f),
                        shape = uiRoundnessShape(),
                    )
                    .clip(uiRoundnessShape())
                    .combinedClickable(
                        enabled = isEnabled,
                        onLongClick = onReset,
                        onClick = {},
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(icon),
                    tint = colorPalette().accent,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            }

            BasicText(
                text = title,
                maxLines = 1,
                style = typography().s.semiBold.copy(color = colorPalette().text),
                // The phone's `disableScrollingTextKey` (its `PlaybackSettingsMenu.kt` 645, 764):
                // the marquee is dropped when set
                modifier = Modifier
                    .weight(1f, fill = false)
                    .padding(end = 8.dp)
                    .then(if (settings.disableScrollingText) Modifier else Modifier.basicMarquee(iterations = Int.MAX_VALUE)),
            )

            BasicText(
                text = displayValue(value),
                style = typography().xxs.semiBold.copy(color = colorPalette().accent),
                modifier = Modifier.padding(end = 8.dp),
            )

            SliderControl(
                state = value,
                onSlide = { if (isEnabled) onValueChange(it) },
                onSlideComplete = onSlideComplete,
                toDisplay = displayValue,
                range = valueRange,
                stepSize = stepSize,
                defaultValue = defaultValue,
                drawValuePoints = drawValuePoints,
                showValue = false,
                modifier = Modifier.weight(1.5f),
            )
        }
    }
}

/** The phone's playback-speed slider range (`PlaybackSettingsMenu.kt`: 0.1–10). */
internal const val PHONE_SPEED_MIN = 0.1f
internal const val PHONE_SPEED_MAX = 10f
