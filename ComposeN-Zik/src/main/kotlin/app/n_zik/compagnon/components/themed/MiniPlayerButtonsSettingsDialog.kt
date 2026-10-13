package app.n_zik.compagnon.components.themed

import app.n_zik.compagnon.colorPalette
import app.n_zik.compagnon.generated.resources.*
import app.n_zik.compagnon.typography
import app.n_zik.compagnon.uiRoundnessShape
import app.n_zik.compagnon.utils.Preferences
import app.n_zik.compagnon.utils.Toaster
import app.n_zik.compagnon.utils.medium
import app.n_zik.compagnon.utils.semiBold
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** The phone's MPB max-checked constraint (its `MiniPlayerButtonsSettingsDialog.kt` 159). */
const val MPB_MAX_CHECKED = 4

/**
 * The working state of the 2 FUNCTIONAL rows of the ported MiniPlayer buttons dialog — Discover
 * (the PC key `showMiniPlayerDiscover`) and Visualizer (the PC key `showMiniPlayerVisualizer`). The
 * 15 other rows are inert (no PC key), so they are frozen at the phone defaults and out of state
 * (spec `spec-fill-pc-ui-gaps-settings` §D).
 */
data class MpbWiredState(
    val discover: Boolean,
    val visualizer: Boolean,
) {
    /** How many functional rows are checked — the input of the max-checked gate [mpbRowCanToggle]. */
    val functionalCheckedCount: Int
        get() = listOf(discover, visualizer).count { it }
}

/**
 * The phone's MPB max-checked gate (its `MiniPlayerButtonsSettingsDialog.kt` 159 + its
 * `ToggleListDialog.kt` 110): an unchecked row is not togglable once the checked count reaches
 * [maxChecked]. On the PC the count covers the functional rows only — the 15 inert rows never
 * bind, so they are excluded from the count (the 4 inert rows checked on the phone's defaults —
 * skip_back / play_pause / skip_forward / audio_output — would otherwise permanently block the 2
 * functional rows, breaking the acceptance criterion that Discover must be checkable).
 */
fun mpbRowCanToggle(isChecked: Boolean, functionalCheckedCount: Int, maxChecked: Int = MPB_MAX_CHECKED): Boolean =
    isChecked || functionalCheckedCount < maxChecked

/**
 * The phone's in-dialog Reset (its `MiniPlayerButtonsSettingsDialog.kt` 207-213): the working
 * state only — the 2 functional rows back to off, nothing persisted (the phone's order has no PC
 * key either — there is no drag-reorder to reset).
 */
fun mpbResetState(): MpbWiredState = MpbWiredState(discover = false, visualizer = false)

/**
 * A row of the ported dialog, in the phone's DEFAULT ORDER (its `MiniPlayerButtonsSettingsDialog.kt`
 * 45-67): [label] / [icon] from the phone, [defaultChecked] the phone's default (shown on the inert
 * rows), [functional] true for the 2 rows with a PC key (spec §D).
 */
private data class MpbRow(
    val id: String,
    val label: StringResource,
    val icon: DrawableResource,
    val defaultChecked: Boolean,
    val functional: Boolean,
)

private val MPB_ROWS: List<MpbRow> = listOf(
    MpbRow("skip_back", Res.string.miniplayer_button_skip_back, Res.drawable.play_skip_back, true, false),
    MpbRow("play_pause", Res.string.miniplayer_button_play_pause, Res.drawable.play, true, false),
    MpbRow("skip_forward", Res.string.miniplayer_button_skip_forward, Res.drawable.play_skip_forward, true, false),
    MpbRow("shuffle", Res.string.miniplayer_button_shuffle, Res.drawable.shuffle, false, false),
    MpbRow("repeat", Res.string.miniplayer_button_repeat, Res.drawable.repeat, false, false),
    MpbRow("queue", Res.string.miniplayer_button_queue, Res.drawable.reorder, false, false),
    MpbRow("audio_output", Res.string.miniplayer_button_audio_output, Res.drawable.devices, true, false),
    MpbRow("sleep_timer", Res.string.miniplayer_button_sleep_timer, Res.drawable.sleep, false, false),
    MpbRow("like", Res.string.miniplayer_button_like, Res.drawable.heart, false, false),
    MpbRow("add_to_playlist", Res.string.miniplayer_button_add_to_playlist, Res.drawable.add_in_playlist, false, false),
    MpbRow("download", Res.string.miniplayer_button_download, Res.drawable.download, false, false),
    MpbRow("share", Res.string.miniplayer_button_share, Res.drawable.share_social, false, false),
    MpbRow("radio", Res.string.miniplayer_button_radio, Res.drawable.radio, false, false),
    MpbRow("discover", Res.string.miniplayer_button_discover, Res.drawable.discover, false, true),
    MpbRow("lyrics", Res.string.miniplayer_button_lyrics, Res.drawable.song_lyrics, false, false),
    MpbRow("visualizer", Res.string.miniplayer_button_visualizer, Res.drawable.sound_effect, false, true),
    MpbRow("video", Res.string.miniplayer_button_video, Res.drawable.video, false, false),
)

/**
 * The phone's MPB row (its `ToggleListDialog.kt` 212-307), ported: the 22 dp icon (accent while the
 * row is togglable, `textDisabled` otherwise), the label (xs semi-bold, marquee as on the phone,
 * `textDisabled` while the row is not togglable), the 20 dp check (the phone's
 * `checked_filled` / `unchecked_outline`, accent while checked and togglable). The phone's
 * drag-reorder handle is NOT ported (there is no PC order key — the 15 inert rows have no effect
 * on the PC's rendering).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MpbDialogRow(
    row: MpbRow,
    isChecked: Boolean,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    val colorPalette = colorPalette()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 35.dp)
            .clip(uiRoundnessShape())
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { if (enabled) onToggle(!isChecked) },
            )
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            painter = painterResource(row.icon),
            contentDescription = null,
            tint = if (enabled) colorPalette.accent else colorPalette.textDisabled,
            modifier = Modifier.size(22.dp),
        )
        BasicText(
            text = stringResource(row.label),
            maxLines = 1,
            style = typography().xs.semiBold.copy(
                color = if (enabled) colorPalette.text else colorPalette.textDisabled,
            ),
            modifier = Modifier
                .weight(1f)
                .basicMarquee(),
        )
        Icon(
            painter = painterResource(if (isChecked) Res.drawable.checked_filled else Res.drawable.unchecked_outline),
            contentDescription = null,
            tint = if (enabled && isChecked) colorPalette.accent else colorPalette.textDisabled,
            modifier = Modifier.size(20.dp),
        )
    }
}

/**
 * Port of the phone's `MiniPlayerButtonsSettingsDialog` (its
 * `app/n_zik/android/components/dialog/settings/MiniPlayerButtonsSettingsDialog.kt`, spec
 * `spec-fill-pc-ui-gaps-settings` §D): the "MiniPlayer buttons" dialog opened from the
 * Player-appearance tab's "Timeline & Controls" card (AS:1724) — the title
 * `miniplayer_buttons_config` + its description, the 17 rows in the phone's default order
 * (MPB:45-67) with the phone's icons / labels, and its Reset / Cancel / Confirm row (the phone's
 * `ToggleListDialog` button styles; the confirm label uses the PC's `confirm` string, as every
 * other PC dialog).
 *
 * **Wired** (2 rows): Discover + Visualizer — the PC keys `showMiniPlayerDiscover` /
 * `showMiniPlayerVisualizer` (both off by default). **Inert** (15 rows): the switch shows the
 * phone's default, a click has no effect — and there is **no toast** (the phone's rows have no
 * description, so the PC's inert-entry convention toasts nothing).
 *
 * Ported verbatim: the max-4 constraint (the phone's MPB:159, counting the functional rows only —
 * see [mpbRowCanToggle]). **Reset** = the phone's MPB:207-213 semantics — the 2 functional rows
 * back to off in the working state only, nothing persisted. **Confirm** = the phone's
 * MPB:217-225 — persists the 2 keys, toasts `toast_preference_saved`, closes.
 *
 * NOT ported (KDoc per spec §D): the phone's drag-reordering (there is no PC order key) and the
 * phone's side effect "checking the visualizer turns on the visualizer" (MPB:147-149 — there is no
 * PC `visualizerEnabled` key).
 */
@Composable
fun MiniPlayerButtonsSettingsDialog(
    preferences: Preferences,
    onDismiss: () -> Unit,
) {
    val settings by preferences.settings.collectAsState()
    // The working state — the 2 functional rows only (the phone's MPB:163-165, ported); opened from
    // the current PC values
    var working by remember {
        mutableStateOf(MpbWiredState(settings.showMiniPlayerDiscover, settings.showMiniPlayerVisualizer))
    }

    val colorPalette = colorPalette()
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize(),
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    // PC: a window is much wider than a phone; the card keeps a phone-like width
                    .widthIn(max = 560.dp)
                    .padding(16.dp),
                shape = uiRoundnessShape(),
                colors = CardDefaults.cardColors(
                    containerColor = colorPalette.background1,
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                ) {
                    // Header
                    BasicText(
                        text = stringResource(Res.string.miniplayer_buttons_config),
                        style = typography().l.semiBold.copy(
                            color = colorPalette.text,
                        ),
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    BasicText(
                        text = stringResource(Res.string.miniplayer_buttons_config_description),
                        style = typography().s.copy(
                            color = colorPalette.textSecondary,
                        ),
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // The 17 rows, in the phone's default order (MPB:45-67)
                    Column(
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                            .heightIn(max = 480.dp),
                    ) {
                        MPB_ROWS.forEach { row ->
                            val isChecked = when (row.id) {
                                "discover" -> working.discover
                                "visualizer" -> working.visualizer
                                else -> row.defaultChecked
                            }
                            // The 15 inert rows are frozen at the phone defaults (never togglable);
                            // the 2 functional rows follow the max-checked gate (functional count
                            // only — see [mpbRowCanToggle]). The `when (row.id)` / else mapping below
                            // targets exactly those 2 functional rows (discover / visualizer) —
                            // extend it if a third functional row is ever added
                            val enabled = row.functional && mpbRowCanToggle(isChecked, working.functionalCheckedCount)
                            MpbDialogRow(
                                row = row,
                                isChecked = isChecked,
                                enabled = enabled,
                                onToggle = {
                                    if (row.id == "discover") {
                                        working = working.copy(discover = it)
                                    } else {
                                        working = working.copy(visualizer = it)
                                    }
                                },
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Reset / Cancel / Confirm (the phone's `ToggleListDialog` 152-204 button styles,
                    // ported; the confirm label uses the PC's `confirm` string, as every other PC
                    // dialog)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        // Reset: the working state only (the phone's MPB:207-213 — no persist, no
                        // toast)
                        BasicText(
                            text = stringResource(Res.string.reset),
                            style = typography().xs.medium.copy(
                                color = colorPalette.textDisabled,
                                textAlign = TextAlign.Center,
                            ),
                            modifier = Modifier
                                .clip(uiRoundnessShape())
                                .clickable { working = mpbResetState() }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        BasicText(
                            text = stringResource(Res.string.cancel),
                            style = typography().xs.medium.copy(
                                color = colorPalette.red.copy(alpha = 0.3f),
                                textAlign = TextAlign.Center,
                            ),
                            modifier = Modifier
                                .clip(uiRoundnessShape())
                                .clickable(onClick = onDismiss)
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        // Confirm: persists the 2 functional keys (the phone's MPB:217-225, without
                        // the order / side effect) + toasts + closes
                        BasicText(
                            text = stringResource(Res.string.confirm),
                            style = typography().xs.semiBold.copy(
                                color = colorPalette.onAccent,
                                textAlign = TextAlign.Center,
                            ),
                            modifier = Modifier
                                .clip(uiRoundnessShape())
                                .background(colorPalette.accent)
                                .clickable {
                                    preferences.update { s ->
                                        s.copy(
                                            showMiniPlayerDiscover = working.discover,
                                            showMiniPlayerVisualizer = working.visualizer,
                                        )
                                    }
                                    Toaster.s(Res.string.toast_preference_saved)
                                    onDismiss()
                                }
                                .padding(horizontal = 20.dp, vertical = 10.dp),
                        )
                    }
                }
            }
        }
    }
}
