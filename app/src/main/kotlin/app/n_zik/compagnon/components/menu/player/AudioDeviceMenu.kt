package app.n_zik.compagnon.components.menu.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.n_zik.compagnon.LocalCommandLauncher
import app.n_zik.compagnon.LocalPlayerRepository
import app.n_zik.compagnon.bridge.ConnectionState
import app.n_zik.compagnon.bridge.state.AudioOutput
import app.n_zik.compagnon.colorPalette
import app.n_zik.compagnon.components.menu.ListMenu
import app.n_zik.compagnon.components.ui.sliders.SliderControl
import app.n_zik.compagnon.enums.AudioQualityFormat
import app.n_zik.compagnon.generated.resources.Res
import app.n_zik.compagnon.generated.resources.audio_devices
import app.n_zik.compagnon.generated.resources.audio_output_title
import app.n_zik.compagnon.generated.resources.audio_quality
import app.n_zik.compagnon.generated.resources.audio_quality_automatic
import app.n_zik.compagnon.generated.resources.audio_quality_format
import app.n_zik.compagnon.generated.resources.audio_quality_format_high
import app.n_zik.compagnon.generated.resources.audio_quality_format_low
import app.n_zik.compagnon.generated.resources.computer
import app.n_zik.compagnon.generated.resources.controls_title_playback_volume
import app.n_zik.compagnon.generated.resources.local_playback_unavailable
import app.n_zik.compagnon.generated.resources.music_note
import app.n_zik.compagnon.generated.resources.phone_android
import app.n_zik.compagnon.generated.resources.this_pc
import app.n_zik.compagnon.generated.resources.volume
import app.n_zik.compagnon.playback.vlc.VlcRuntime
import app.n_zik.compagnon.typography
import app.n_zik.compagnon.uiRoundnessShape
import app.n_zik.compagnon.utils.LocalPreferences
import app.n_zik.compagnon.utils.semiBold
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** An output of the phone's playback as the PC sees it (contract §8.5). */
data class AudioDevice(
    val name: String,
    val type: AudioDeviceType,
    val isConnected: Boolean,
    val isActive: Boolean = false,
)

/** Compagnon: the two outputs of contract 1.2 (the phone's `AudioDeviceType` lists its hardware routes). */
enum class AudioDeviceType(val output: AudioOutput, val icon: DrawableResource) {
    PC(AudioOutput.Pc, Res.drawable.computer),
    PHONE(AudioOutput.Phone, Res.drawable.phone_android),
}

/**
 * Port of `AudioDeviceMenu` (phone's `app/n_zik/android/components/menu/player/AudioDeviceMenu.kt` 178-709), in
 * its default list style, opened by the mini-player's "audio output" button.
 *
 * - "Audio output": two entries, "This PC" and the phone ([phoneName], its `serverName`), the active one
 *   from `audioOutput`; a click sends `player/output` (contract §9) and the state follows the
 *   `outputChanged` delta. "This PC" is disabled, with a message, when libvlc could not be loaded.
 * - "Volume": the PC's playback volume only (local, persisted, contract §8.5), in the phone's `VolumeRow`.
 * - "Audio Quality": the quality asked when the PC forges an audio URL.
 * Dropped: the hardware device discovery (Bluetooth, car, broadcasts, permission), the loading / error
 * states that come with it, the device volume (it is Windows' volume) and the grid style.
 */
@Composable
fun AudioDeviceMenu(onDismiss: () -> Unit, phoneName: String) {
    val repository = LocalPlayerRepository.current ?: return
    val onCommand = LocalCommandLauncher.current
    val preferences = LocalPreferences.current
    val state by repository.state.collectAsState()
    val connection by repository.connection.collectAsState()
    val live = connection == ConnectionState.Live
    val output = state?.audioOutput ?: AudioOutput.Phone
    val pcAvailable = VlcRuntime.isAvailable

    val audioDevices = listOf(
        AudioDevice(stringResource(Res.string.this_pc), AudioDeviceType.PC, isConnected = pcAvailable, isActive = output == AudioOutput.Pc),
        AudioDevice(phoneName, AudioDeviceType.PHONE, isConnected = true, isActive = output == AudioOutput.Phone),
    )

    val settings = preferences?.settings?.collectAsState()?.value
    val audioQualityFormat = settings?.audioQualityFormat ?: AudioQualityFormat.Auto
    var dragged by remember { mutableStateOf<Float?>(null) }
    val playbackVolume = dragged ?: settings?.playbackVolume ?: 1f
    val qualityOptions = listOf(
        AudioQualityFormat.Auto to stringResource(Res.string.audio_quality_automatic),
        AudioQualityFormat.High to stringResource(Res.string.audio_quality_format_high),
        AudioQualityFormat.Low to stringResource(Res.string.audio_quality_format_low),
    )
    val unavailableText = stringResource(Res.string.local_playback_unavailable)

    ListMenu.Menu(title = stringResource(Res.string.audio_devices), showDragHandle = true) {
        SectionTitle(stringResource(Res.string.audio_output_title))
        audioDevices.forEach { dev ->
            ListMenu.Entry(
                text = dev.name,
                enabled = live && dev.isConnected,
                icon = {
                    val iconColor = if (dev.isActive) colorPalette().accent else colorPalette().text
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(
                                color = if (dev.isActive) colorPalette().accent.copy(alpha = 0.2f) else colorPalette().accent.copy(alpha = 0.1f),
                                shape = uiRoundnessShape(),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(dev.type.icon),
                            contentDescription = null,
                            tint = iconColor,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                },
                modifier = if (dev.isActive) Modifier.background(colorPalette().accent.copy(alpha = 0.1f), uiRoundnessShape()) else Modifier,
                subtitle = if (!dev.isConnected) unavailableText else null,
                trailingContent = {
                    AnimatedVisibility(
                        visible = dev.isActive,
                        enter = fadeIn() + scaleIn(),
                        exit = fadeOut() + scaleOut(),
                    ) {
                        RadioButton(
                            selected = true,
                            onClick = null,
                            colors = RadioButtonDefaults.colors(
                                selectedColor = colorPalette().accent,
                                unselectedColor = colorPalette().textSecondary,
                            ),
                        )
                    }
                },
                onClick = {
                    if (!dev.isActive) onCommand { setAudioOutput(dev.type.output) }
                },
            )
        }

        SectionTitle(stringResource(Res.string.volume))
        VolumeRow(
            currentVolume = playbackVolume,
            maxVolume = 1f,
            enabled = preferences != null,
            onVolumeChange = { newVolume ->
                dragged = kotlin.math.round(newVolume * 100f) / 100f
            },
            onVolumeChangeComplete = {
                dragged?.let { value -> preferences?.update { it.copy(playbackVolume = value.coerceIn(0f, 1f)) } }
                dragged = null
            },
        )

        SectionTitle(stringResource(Res.string.audio_quality_format))
        qualityOptions.forEach { (format, label) ->
            val isSelected = audioQualityFormat == format
            ListMenu.Entry(
                text = label,
                icon = {
                    val iconColor = if (isSelected) colorPalette().accent else colorPalette().text
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(
                                color = if (isSelected) colorPalette().accent.copy(alpha = 0.2f) else colorPalette().accent.copy(alpha = 0.1f),
                                shape = uiRoundnessShape(),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(Res.drawable.audio_quality),
                            contentDescription = null,
                            tint = iconColor,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                },
                modifier = if (isSelected) Modifier.background(colorPalette().accent.copy(alpha = 0.1f), uiRoundnessShape()) else Modifier,
                trailingContent = {
                    if (isSelected) {
                        RadioButton(
                            selected = isSelected,
                            onClick = null,
                            colors = RadioButtonDefaults.colors(
                                selectedColor = colorPalette().accent,
                                unselectedColor = colorPalette().textSecondary,
                            ),
                        )
                    }
                },
                onClick = { preferences?.update { it.copy(audioQualityFormat = format) } },
            )
        }
        Spacer(modifier = Modifier.height(32.dp))
    }
}

/** Port of the phone's `VolumeRow` (`AudioDeviceMenu.kt` 711-772); here the PC's playback volume (0–1). */
@Composable
private fun VolumeRow(
    currentVolume: Float,
    maxVolume: Float,
    enabled: Boolean = true,
    onVolumeChange: (Float) -> Unit,
    onVolumeChangeComplete: () -> Unit = {},
) {
    val alpha = if (enabled) 1f else 0.5f
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp)
            .graphicsLayer(alpha = alpha),
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(
                    color = colorPalette().accent.copy(alpha = 0.1f),
                    shape = uiRoundnessShape(),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(Res.drawable.music_note),
                contentDescription = null,
                tint = colorPalette().accent,
                modifier = Modifier.size(18.dp),
            )
        }

        BasicText(
            text = stringResource(Res.string.controls_title_playback_volume),
            style = typography().s.copy(
                fontWeight = FontWeight.SemiBold,
                color = colorPalette().text,
            ),
        )

        BasicText(
            text = "${if (maxVolume > 0) kotlin.math.round(((currentVolume / maxVolume) * 100)).toInt() else 0}%",
            style = typography().xxs.copy(
                fontWeight = FontWeight.SemiBold,
                color = colorPalette().accent,
            ),
        )

        SliderControl(
            state = currentVolume,
            range = 0f..maxVolume,
            stepSize = 0f,
            drawValuePoints = false,
            showValue = false,
            onSlide = if (enabled) onVolumeChange else ({}),
            onSlideComplete = if (enabled) onVolumeChangeComplete else ({}),
            modifier = Modifier.weight(1f),
        )
    }
}

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
