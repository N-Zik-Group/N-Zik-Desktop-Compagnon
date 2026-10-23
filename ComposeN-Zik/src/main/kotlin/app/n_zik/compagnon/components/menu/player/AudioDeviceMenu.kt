package app.n_zik.compagnon.components.menu.player

import app.n_zik.compagnon.generated.resources.*
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color
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
import app.n_zik.compagnon.playback.vlc.VlcRuntime
import app.n_zik.compagnon.typography
import app.n_zik.compagnon.uiRoundnessShape
import app.n_zik.compagnon.utils.LocalPreferences
import app.n_zik.compagnon.utils.formatText
import app.n_zik.compagnon.utils.semiBold
import kotlin.math.round
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
 * A hardware output of the phone as the phone lists it (its `AudioDeviceMenu.kt` 161-170): the PC port
 * of that device-list entry, for the inert phone list (spec `spec-fill-pc-ui-gaps-menus` #28). The
 * contract carries no phone hardware device (1.12.0), so this list is never fed — the entries are
 * rendered only when the list is non-empty, i.e. never for now (TODO: the contract would have to carry
 * the phone's devices for this list to show).
 */
data class PhoneAudioDevice(
    val name: String,
    val type: PhoneAudioDeviceType,
    val isActive: Boolean = false,
    val batteryLevel: Int? = null,
)

/**
 * Port of the phone's hardware `AudioDeviceType` (its `AudioDeviceMenu.kt` 172-179). The PC's
 * [AudioDeviceType] above is the contract §8.5 output pair (PC / phone), a different thing, hence the
 * name.
 */
enum class PhoneAudioDeviceType {
    BLUETOOTH,
    WIRED_HEADPHONES,
    PHONE_SPEAKER,
    EXTERNAL_SPEAKER,
    USB_HEADSET,
    HDMI,
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
 * Ported inert (spec `spec-fill-pc-ui-gaps-menus` #28-#29): the phone's hardware device list (its
 * `AudioDeviceMenu.kt` 451-533 — [PhoneDeviceEntry], the [PhoneAudioDevice] model with
 * [PhoneAudioDeviceType], the icons and name heuristics of its `AudioDeviceUtils.kt`) and its
 * discovery error state (its 406-439 — [PhoneDiscoveryError]). Neither is rendered for now: the
 * contract carries no phone hardware device (1.12.0), so the list stays empty and the discovery
 * error can never happen (its message state is never set). Dropped: the discovery itself
 * (Bluetooth, car, broadcasts, permission), the loading state that comes with it, the device volume
 * (it is the host's volume; ported inert in `PlaybackSettingsMenu`, spec #21) and the grid style
 * (deferred, served by `ui.settings` since 1.10.0, not read).
 * Écart 2 (spec): no behaviour change — this menu's volume and the audio quality apply to the PC, not
 * to the phone; the phone keeps its own.
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
    // The message is platform-aware (VlcRuntime.unavailableMessageIsEmbedded, pure in os.name), not
    // a file-existence check: the embedded-VLC one on Windows (the build embeds VLC by design, so a

    // load failure there is an app problem, not a missing package), the system-install one on Linux
    // (its install hint was resolved with the load, so reading it here is a field access, not a file IO)
    val unavailableText = if (VlcRuntime.unavailableMessageIsEmbedded(System.getProperty("os.name").orEmpty())) {
        stringResource(Res.string.local_playback_unavailable)
    } else {
        val installHint = (VlcRuntime.availability as? VlcRuntime.Availability.Unavailable)?.installHint
            ?: VlcRuntime.GENERIC_VLC_INSTALL_HINT
        formatText(stringResource(Res.string.local_playback_unavailable_linux), installHint)
    }
    val pcSubtitle = stringResource(Res.string.bridge_audio_output_pc_subtitle)

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
                // The phone's PC subtitle (`AudioDeviceMenu.kt` 486), always shown; the reason when libvlc is missing
                subtitle = when {
                    dev.type != AudioDeviceType.PC -> null
                    !dev.isConnected -> unavailableText
                    else -> pcSubtitle
                },
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

        // Spec #28-#29: the phone's hardware device list (its `AudioDeviceMenu.kt` 451-533) and its
        // discovery error state (its 406-439). The contract carries no phone hardware device (1.12.0):

        // [phoneDevices] is empty (TODO: the contract would have to carry the phone's devices for this
        // list to show) and [phoneDiscoveryError] is never set (the phone's discovery never runs here

        // and cannot fail) — neither is rendered for now
        val phoneDevices = emptyList<PhoneAudioDevice>()
        var phoneDiscoveryError by remember { mutableStateOf<String?>(null) }
        // A local snapshot: the delegated property cannot be smart-cast
        val discoveryError = phoneDiscoveryError
        if (discoveryError != null) {
            PhoneDiscoveryError(discoveryError)
        }
        if (phoneDevices.isNotEmpty()) {
            phoneDevices.forEach { dev -> PhoneDeviceEntry(dev) }
        }

        SectionTitle(stringResource(Res.string.volume))
        VolumeRow(
            currentVolume = playbackVolume,
            maxVolume = 1f,
            enabled = preferences != null,
            onVolumeChange = { newVolume ->
                dragged = round(newVolume * 100f) / 100f
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

/**
 * The phone's discovery error state (its `AudioDeviceMenu.kt` 406-439): a 48 dp error icon in a
 * tone-keyed red, the message, a Retry button — ported with its condition (the message state, never
 * set here: the contract carries no phone devices, so the phone's discovery never runs and cannot
 * fail; spec `spec-fill-pc-ui-gaps-menus` #29). [Res.drawable.alert] is the phone's
 * `Icons.Outlined.Error` glyph; the Retry button is inert (`onClick = {}`) — nothing to re-discover.
 */
@Composable
private fun PhoneDiscoveryError(errorMessage: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            painter = painterResource(Res.drawable.alert),
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            // The phone's tone-keyed red (its `AudioDeviceMenu.kt` 415-420)
            tint = Color.hsl(0f, 0.8f, if (colorPalette().isDark) 0.65f else 0.35f),
        )
        Spacer(modifier = Modifier.height(16.dp))
        // Centered by the column (the phone's `TextAlign.Center`)
        BasicText(
            text = errorMessage,
            style = typography().s.copy(color = colorPalette().text),
        )
        Spacer(modifier = Modifier.height(16.dp))
        // The phone's Retry (its `AudioDeviceMenu.kt` 430-438): inert, nothing to re-discover
        Button(onClick = {}) {
            Text(text = stringResource(Res.string.retry))
        }
    }
}

/**
 * One phone hardware device of the phone's list (its `AudioDeviceMenu.kt` 451-533), radio style: the
 * icon box (32 dp, the phone's icon at 18 dp, [phoneDeviceIcon]), the name, the phone's battery
 * subtitle (its 486) for a device with a level, the trailing radio when active — inert
 * (`onClick = {}`), the contract has no route to select a phone device (spec
 * `spec-fill-pc-ui-gaps-menus` #28).
 */
@Composable
private fun PhoneDeviceEntry(dev: PhoneAudioDevice) {
    ListMenu.Entry(
        text = dev.name,
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
                    painter = painterResource(phoneDeviceIcon(dev.type, dev.name)),
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(18.dp),
                )
            }
        },
        modifier = if (dev.isActive) Modifier.background(colorPalette().accent.copy(alpha = 0.1f), uiRoundnessShape()) else Modifier,
        // The phone's subtitle (its `AudioDeviceMenu.kt` 486): the battery level, carried by the
        // Bluetooth devices
        subtitle = dev.batteryLevel?.let { "Battery: $it%" },
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
        onClick = {},
    )
}

/** Port of the phone's device-name heuristics (`AudioDeviceUtils.kt` 55-102): buds / speaker / car, matched on the lowercased name — used by [phoneDeviceIcon] for the inert phone device list (spec `spec-fill-pc-ui-gaps-menus` #28). */
private fun isBuds(name: String?): Boolean {
    if (name == null) return false
    val lowerName = name.lowercase()
    return lowerName.contains("buds") ||
        lowerName.contains("airpods") ||
        lowerName.contains("earpods") ||
        lowerName.contains("earphone") ||
        lowerName.contains("freebuds") ||
        lowerName.contains("pods") ||
        lowerName.contains("wf")
}

private fun isSpeaker(name: String?): Boolean {
    if (name == null) return false
    val lowerName = name.lowercase()
    return lowerName.contains("speaker") ||
        lowerName.contains("soundbar") ||
        lowerName.contains("homepod") ||
        lowerName.contains("echo") ||
        lowerName.contains("boombox") ||
        lowerName.contains("audio system") ||
        lowerName.contains("sound") ||
        lowerName.contains("audio") ||
        lowerName.contains("stereo") ||
        lowerName.contains("music") ||
        lowerName.contains("box") ||
        lowerName.contains("party") ||
        lowerName.contains("waves")
}

private fun isCar(name: String?): Boolean {
    if (name == null) return false
    val lowerName = name.lowercase()
    val carKeywords = listOf(
        "car", "bt_auto", "toyota", "vw", "audi", "ford", "honda", "hyundai", "nissan", "kia",
        "mazda", "renault", "peugeot", "citroen", "fiat", "bmw", "mercedes", "volvo", "subaru",
        "jeep", "dodge", "chevrolet", "buick", "cadillac", "gmc", "tesla", "porsche", "ferrari",
        "lamborghini", "maserati", "bentley", "rolls-royce", "lexus", "acura", "infiniti", "genesis",
        "bt-auto", "auto", "vehicle", "infotainment", "uconnect", "sync", "mycar", "blue&me", "hand-free",
    )
    return carKeywords.any { lowerName.contains(it) }
}

/**
 * The phone's bottom-sheet device icon (its `getBottomSheetDeviceIcon`, `AudioDeviceUtils.kt` 145-169),
 * ported onto the PC's drawables — material icons are not available on the PC, so: BT buds →
 * [Res.drawable.buds] (the phone's own drawable), BT speaker → [Res.drawable.speaker] (the phone's
 * `SpeakerGroup`), BT car → [Res.drawable.car] (the phone's own drawable), BT other →
 * [Res.drawable.bluetooth] (the phone's `Bluetooth`), WIRED_HEADPHONES → [Res.drawable.headphones]
 * (the phone's `Headphones`), USB_HEADSET → [Res.drawable.headphones] (the phone's `Usb`; its other
 * icon function, `AudioDeviceUtils.kt` 124-131, maps USB to the `headphones` drawable), HDMI →
 * [Res.drawable.devices] (the phone's `Tv`; its fallback for unmapped types, its l.138),
 * EXTERNAL_SPEAKER → [Res.drawable.speaker] (the phone's `Speaker`), PHONE_SPEAKER →
 * [Res.drawable.phone_android] (the phone's `PhoneAndroid`). A forced car keeps [Res.drawable.car]
 * (the phone's l.150).
 */
private fun phoneDeviceIcon(type: PhoneAudioDeviceType, name: String?, isCarForced: Boolean = false): DrawableResource {
    if (isCarForced) return Res.drawable.car
    return when (type) {
        PhoneAudioDeviceType.BLUETOOTH -> when {
            isBuds(name) -> Res.drawable.buds
            isSpeaker(name) -> Res.drawable.speaker
            isCar(name) -> Res.drawable.car
            else -> Res.drawable.bluetooth
        }
        PhoneAudioDeviceType.WIRED_HEADPHONES -> Res.drawable.headphones
        PhoneAudioDeviceType.USB_HEADSET -> Res.drawable.headphones
        PhoneAudioDeviceType.HDMI -> Res.drawable.devices
        PhoneAudioDeviceType.EXTERNAL_SPEAKER -> Res.drawable.speaker
        PhoneAudioDeviceType.PHONE_SPEAKER -> Res.drawable.phone_android
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
            text = stringResource(Res.string.volume),
            style = typography().s.copy(
                fontWeight = FontWeight.SemiBold,
                color = colorPalette().text,
            ),
        )

        BasicText(
            text = "${if (maxVolume > 0) round(((currentVolume / maxVolume) * 100)).toInt() else 0}%",
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
