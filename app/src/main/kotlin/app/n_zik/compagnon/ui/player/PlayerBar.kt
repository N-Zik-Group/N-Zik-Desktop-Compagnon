package app.n_zik.compagnon.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.n_zik.compagnon.generated.resources.Res
import app.n_zik.compagnon.generated.resources.connection_connecting
import app.n_zik.compagnon.generated.resources.connection_live
import app.n_zik.compagnon.generated.resources.connection_offline
import app.n_zik.compagnon.generated.resources.connection_reconnecting
import app.n_zik.compagnon.generated.resources.pause
import app.n_zik.compagnon.generated.resources.play
import app.n_zik.compagnon.generated.resources.play_skip_back
import app.n_zik.compagnon.generated.resources.play_skip_forward
import app.n_zik.compagnon.generated.resources.player_next
import app.n_zik.compagnon.generated.resources.player_nothing_playing
import app.n_zik.compagnon.generated.resources.player_open_full
import app.n_zik.compagnon.generated.resources.player_pause
import app.n_zik.compagnon.generated.resources.player_play
import app.n_zik.compagnon.generated.resources.player_previous
import app.n_zik.compagnon.generated.resources.player_repeat_all
import app.n_zik.compagnon.generated.resources.player_repeat_off
import app.n_zik.compagnon.generated.resources.player_repeat_one
import app.n_zik.compagnon.generated.resources.player_shuffle_off
import app.n_zik.compagnon.generated.resources.player_shuffle_on
import app.n_zik.compagnon.generated.resources.player_speed
import app.n_zik.compagnon.generated.resources.player_unknown_artist
import app.n_zik.compagnon.generated.resources.player_untitled
import app.n_zik.compagnon.generated.resources.repeat
import app.n_zik.compagnon.generated.resources.repeat_one
import app.n_zik.compagnon.generated.resources.shuffle
import app.n_zik.compagnon.player.ConnectionState
import app.n_zik.compagnon.player.PlayerRepository
import app.n_zik.compagnon.player.PlayerState
import app.n_zik.compagnon.player.RepeatMode
import app.n_zik.compagnon.player.SessionContract
import app.n_zik.compagnon.player.Track
import app.n_zik.compagnon.ui.theme.colorPalette
import app.n_zik.compagnon.ui.theme.semiBold
import app.n_zik.compagnon.ui.theme.typography
import app.n_zik.compagnon.ui.theme.uiRoundnessShape
import org.jetbrains.compose.resources.stringResource

/** Runs a command of the repository (launched by the caller's scope). */
typealias CommandLauncher = (suspend PlayerRepository.() -> Unit) -> Unit

/**
 * Fixed bottom bar, after the phone's mini player: artwork, title and artists; previous / play-pause /
 * next; seek bar with elapsed time and duration; speed, repeat and shuffle; connection indicator.
 * A click on the bar (outside the controls) opens the full player.
 */
@Composable
fun PlayerBar(
    repository: PlayerRepository,
    state: PlayerState?,
    connection: ConnectionState,
    onCommand: CommandLauncher,
    onOpenFull: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = colorPalette()
    val track = state?.currentTrack
    val playback = SessionContract.FEATURE_PLAYBACK in repository.features
    // Commands need a live session: otherwise every one would end in the "not confirmed" notice.
    val live = connection == ConnectionState.Live
    val position = rememberPositionMs(repository, state, live)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .shadow(elevation = 8.dp, shape = uiRoundnessShape(), spotColor = palette.accent.copy(alpha = 0.2f))
            .clip(uiRoundnessShape())
            .background(palette.background1)
            .clickable(onClickLabel = stringResource(Res.string.player_open_full), onClick = onOpenFull)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.weight(0.32f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Artwork(repository, track?.id, track?.hasArtwork == true, size = 52.dp)
            TrackTitle(track, titleStyleSize = 0, modifier = Modifier.weight(1f))
        }
        Column(
            modifier = Modifier.weight(0.42f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (playback) {
                TransportControls(state, onCommand, buttonSize = 40.dp, playSize = 44.dp, enabled = live)
            }
            SeekBar(
                positionMs = position,
                durationMs = track?.durationMs,
                onSeek = { target -> onCommand { seek(target) } },
                enabled = live && playback && track != null,
            )
        }
        Row(
            modifier = Modifier.weight(0.26f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
        ) {
            if (playback && state != null) ModeControls(state, onCommand, enabled = live)
            ConnectionIndicator(connection, modifier = Modifier.padding(start = 8.dp))
        }
    }
}

/** Title and artists, one line each. [titleStyleSize] 0 = bar, 1 = full player. */
@Composable
fun TrackTitle(track: Track?, titleStyleSize: Int, modifier: Modifier = Modifier, centered: Boolean = false) {
    val palette = colorPalette()
    val titleStyle = if (titleStyleSize == 0) typography().xs.semiBold else typography().xl.semiBold
    val artistStyle = if (titleStyleSize == 0) typography().xxs else typography().s
    Column(modifier = modifier, horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start) {
        Text(
            when {
                track == null -> stringResource(Res.string.player_nothing_playing)
                track.title.isBlank() -> stringResource(Res.string.player_untitled)
                else -> track.title
            },
            style = titleStyle,
            color = palette.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (track != null) {
            Text(
                track.artists?.takeIf { it.isNotBlank() } ?: stringResource(Res.string.player_unknown_artist),
                style = artistStyle,
                color = palette.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Previous / play-pause / next. The play-pause button is the phone's tinted square. */
@Composable
fun TransportControls(state: PlayerState?, onCommand: CommandLauncher, buttonSize: Dp, playSize: Dp, enabled: Boolean) {
    val palette = colorPalette()
    val hasTrack = enabled && state?.currentTrack != null
    val playing = state?.isPlaying == true
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        PlayerIconButton(
            Res.drawable.play_skip_back,
            stringResource(Res.string.player_previous),
            onClick = { onCommand { previous() } },
            size = buttonSize,
            iconSize = buttonSize * 0.5f,
            enabled = hasTrack,
        )
        PlayerIconButton(
            if (playing) Res.drawable.pause else Res.drawable.play,
            stringResource(if (playing) Res.string.player_pause else Res.string.player_play),
            onClick = { onCommand { if (playing) pause() else play() } },
            size = playSize,
            iconSize = playSize * 0.55f,
            background = palette.background2,
            enabled = hasTrack,
        )
        PlayerIconButton(
            Res.drawable.play_skip_forward,
            stringResource(Res.string.player_next),
            onClick = { onCommand { next() } },
            size = buttonSize,
            iconSize = buttonSize * 0.5f,
            enabled = hasTrack,
        )
    }
}

/** Speed, repeat (`off` → `all` → `one`) and shuffle; an active mode is tinted with the accent. */
@Composable
fun ModeControls(state: PlayerState, onCommand: CommandLauncher, enabled: Boolean) {
    val palette = colorPalette()
    SpeedButton(
        speed = state.speed,
        onSpeed = { speed -> onCommand { setSpeed(speed) } },
        contentDescription = stringResource(Res.string.player_speed),
        enabled = enabled,
    )
    val nextRepeat = when (state.repeatMode) {
        RepeatMode.Off -> RepeatMode.All
        RepeatMode.All -> RepeatMode.One
        RepeatMode.One -> RepeatMode.Off
    }
    PlayerIconButton(
        if (state.repeatMode == RepeatMode.One) Res.drawable.repeat_one else Res.drawable.repeat,
        stringResource(
            when (state.repeatMode) {
                RepeatMode.Off -> Res.string.player_repeat_off
                RepeatMode.All -> Res.string.player_repeat_all
                RepeatMode.One -> Res.string.player_repeat_one
            },
        ),
        onClick = { onCommand { setRepeat(nextRepeat) } },
        tint = if (state.repeatMode == RepeatMode.Off) palette.textSecondary else palette.accent,
        enabled = enabled,
    )
    PlayerIconButton(
        Res.drawable.shuffle,
        stringResource(if (state.shuffle) Res.string.player_shuffle_on else Res.string.player_shuffle_off),
        onClick = { onCommand { setShuffle(!state.shuffle) } },
        tint = if (state.shuffle) palette.accent else palette.textSecondary,
        enabled = enabled,
    )
}

/** Dot + short label: live, connecting / reconnecting, disconnected. */
@Composable
fun ConnectionIndicator(connection: ConnectionState, modifier: Modifier = Modifier) {
    val palette = colorPalette()
    val (color, label) = when (connection) {
        ConnectionState.Live -> Color(0xff4caf50) to stringResource(Res.string.connection_live)
        ConnectionState.Connecting -> palette.textSecondary to stringResource(Res.string.connection_connecting)
        is ConnectionState.Reconnecting -> Color(0xffe0a030) to stringResource(Res.string.connection_reconnecting)
        else -> palette.red to stringResource(Res.string.connection_offline)
    }
    Row(
        modifier = modifier.widthIn(max = 140.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Text(label, style = typography().xxs, color = palette.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
