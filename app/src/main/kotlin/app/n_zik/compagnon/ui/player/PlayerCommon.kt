package app.n_zik.compagnon.ui.player

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.n_zik.compagnon.generated.resources.Res
import app.n_zik.compagnon.generated.resources.command_clear
import app.n_zik.compagnon.generated.resources.command_jump
import app.n_zik.compagnon.generated.resources.command_move
import app.n_zik.compagnon.generated.resources.command_next
import app.n_zik.compagnon.generated.resources.command_pause
import app.n_zik.compagnon.generated.resources.command_play
import app.n_zik.compagnon.generated.resources.command_previous
import app.n_zik.compagnon.generated.resources.command_remove
import app.n_zik.compagnon.generated.resources.command_repeat
import app.n_zik.compagnon.generated.resources.command_seek
import app.n_zik.compagnon.generated.resources.command_shuffle
import app.n_zik.compagnon.generated.resources.command_speed
import app.n_zik.compagnon.generated.resources.command_unknown
import app.n_zik.compagnon.generated.resources.error_unknown_code
import app.n_zik.compagnon.generated.resources.musical_notes
import app.n_zik.compagnon.generated.resources.notice_failed
import app.n_zik.compagnon.generated.resources.notice_late_error
import app.n_zik.compagnon.generated.resources.notice_no_delta
import app.n_zik.compagnon.generated.resources.notice_not_found
import app.n_zik.compagnon.generated.resources.notice_other_active
import app.n_zik.compagnon.generated.resources.notice_queue_mismatch
import app.n_zik.compagnon.generated.resources.notice_rejected
import app.n_zik.compagnon.generated.resources.notice_server_stopping
import app.n_zik.compagnon.generated.resources.notice_unavailable
import app.n_zik.compagnon.generated.resources.notice_unreachable
import app.n_zik.compagnon.generated.resources.paired_other_active_unknown
import app.n_zik.compagnon.generated.resources.player_speed_value
import app.n_zik.compagnon.player.CommandKind
import app.n_zik.compagnon.player.PlayerNotice
import app.n_zik.compagnon.player.PlayerRepository
import app.n_zik.compagnon.player.PlayerState
import app.n_zik.compagnon.player.SessionContract
import app.n_zik.compagnon.ui.theme.colorPalette
import app.n_zik.compagnon.ui.theme.typography
import app.n_zik.compagnon.ui.theme.uiRoundnessShape
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** Speeds offered by the speed menu (contract §9 bounds: 0.25–4.0). */
val SPEED_CHOICES: List<Float> = listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f, 3f, 4f)

/** `m:ss`, or `h:mm:ss` from one hour. */
fun formatDuration(ms: Long): String {
    val totalSeconds = (ms.coerceAtLeast(0) / 1_000)
    val hours = totalSeconds / 3_600
    val minutes = (totalSeconds % 3_600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
}

/** `1x`, `1.25x`, `0.5x`. */
fun formatSpeedNumber(speed: Float): String =
    if (speed == speed.toInt().toFloat()) speed.toInt().toString() else "%.2f".format(java.util.Locale.ROOT, speed).trimEnd('0').trimEnd('.')

@Composable
fun speedLabel(speed: Float): String = stringResource(Res.string.player_speed_value, formatSpeedNumber(speed))

/**
 * Displayed position, extrapolated locally from the last WS anchor (contract §7): recomputed a few
 * times per second while playing and the session is live, never sent anywhere.
 */
@Composable
fun rememberPositionMs(repository: PlayerRepository, state: PlayerState?, live: Boolean): Long {
    var now by remember { mutableLongStateOf(repository.serverNowMs()) }
    LaunchedEffect(state, live) {
        // Without a live session the phone's state is unknown: the position freezes where it was.
        if (!live) return@LaunchedEffect
        now = repository.serverNowMs()
        while (state?.isPlaying == true) {
            delay(POSITION_TICK_MS)
            now = repository.serverNowMs()
        }
    }
    return state?.extrapolatedPositionMs(now) ?: 0L
}

private const val POSITION_TICK_MS = 250L

/** Track artwork through the phone (memory cache only); a music-note placeholder otherwise. */
@Composable
fun Artwork(
    repository: PlayerRepository,
    trackId: String?,
    hasArtwork: Boolean,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val palette = colorPalette()
    val enabled = SessionContract.FEATURE_ARTWORK in repository.features && hasArtwork && trackId != null
    var image by remember(trackId) { mutableStateOf<ImageBitmap?>(trackId?.let(repository::cachedArtwork)) }
    if (enabled) {
        LaunchedEffect(trackId) { image = repository.artwork(trackId) }
    }
    Box(
        modifier = modifier.size(size).clip(uiRoundnessShape()).background(palette.background2),
        contentAlignment = Alignment.Center,
    ) {
        val bitmap = image
        if (enabled && bitmap != null) {
            Image(bitmap = bitmap, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(size))
        } else {
            Icon(
                painterResource(Res.drawable.musical_notes),
                contentDescription = null,
                tint = palette.textSecondary,
                modifier = Modifier.size(size * 0.4f),
            )
        }
    }
}

/** Square, rounded icon button of the phone's player controls. */
@Composable
fun PlayerIconButton(
    icon: DrawableResource,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    iconSize: Dp = 20.dp,
    tint: Color = colorPalette().text,
    background: Color = Color.Transparent,
    enabled: Boolean = true,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(uiRoundnessShape())
            .background(background)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(icon),
            contentDescription = contentDescription,
            tint = if (enabled) tint else colorPalette().textDisabled,
            modifier = Modifier.size(iconSize),
        )
    }
}

/** Speed button: shows the current speed, opens the list of speeds. */
@Composable
fun SpeedButton(
    speed: Float,
    onSpeed: (Float) -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val palette = colorPalette()
    var open by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .clip(uiRoundnessShape())
                .clickable(enabled = enabled, onClickLabel = contentDescription) { open = true }
                .background(if (speed != 1f) palette.accent.copy(alpha = 0.15f) else Color.Transparent)
                .size(width = 56.dp, height = 40.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                speedLabel(speed),
                style = typography().xs,
                color = when {
                    !enabled -> palette.textDisabled
                    speed != 1f -> palette.accent
                    else -> palette.text
                },
            )
        }
        DropdownMenu(expanded = open && enabled, onDismissRequest = { open = false }) {
            SPEED_CHOICES.forEach { choice ->
                DropdownMenuItem(
                    text = {
                        Text(
                            speedLabel(choice),
                            style = typography().xs,
                            color = if (choice == speed) palette.accent else palette.text,
                        )
                    },
                    onClick = {
                        open = false
                        if (choice != speed) onSpeed(choice)
                    },
                )
            }
        }
    }
}

suspend fun commandName(command: CommandKind?): String = getString(
    when (command) {
        CommandKind.Play -> Res.string.command_play
        CommandKind.Pause -> Res.string.command_pause
        CommandKind.Seek -> Res.string.command_seek
        CommandKind.Next -> Res.string.command_next
        CommandKind.Previous -> Res.string.command_previous
        CommandKind.Speed -> Res.string.command_speed
        CommandKind.Repeat -> Res.string.command_repeat
        CommandKind.Shuffle -> Res.string.command_shuffle
        CommandKind.Jump -> Res.string.command_jump
        CommandKind.Remove -> Res.string.command_remove
        CommandKind.Move -> Res.string.command_move
        CommandKind.Clear -> Res.string.command_clear
        null -> Res.string.command_unknown
    },
)

/** Readable text of a [PlayerNotice], decided from its kind (itself from the contract `code`). */
suspend fun noticeText(notice: PlayerNotice): String {
    val name = commandName(notice.command)
    return when (notice) {
        is PlayerNotice.NoDelta -> getString(Res.string.notice_no_delta, name)
        is PlayerNotice.QueueMismatch -> getString(Res.string.notice_queue_mismatch, name)
        is PlayerNotice.Rejected -> getString(Res.string.notice_rejected, name)
        is PlayerNotice.Unavailable -> getString(Res.string.notice_unavailable, name)
        is PlayerNotice.ServerStopping -> getString(Res.string.notice_server_stopping, name)
        is PlayerNotice.OtherActive ->
            getString(Res.string.notice_other_active, name, notice.deviceName ?: getString(Res.string.paired_other_active_unknown))
        is PlayerNotice.NotFound -> getString(Res.string.notice_not_found, name)
        is PlayerNotice.Unreachable -> getString(Res.string.notice_unreachable, name)
        is PlayerNotice.Failed ->
            getString(Res.string.notice_failed, name, notice.status, notice.code ?: getString(Res.string.error_unknown_code))
        is PlayerNotice.LateError ->
            getString(Res.string.notice_late_error, name, notice.code, notice.commandId?.take(8) ?: getString(Res.string.error_unknown_code))
    }
}
