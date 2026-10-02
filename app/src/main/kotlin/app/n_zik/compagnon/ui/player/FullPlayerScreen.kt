package app.n_zik.compagnon.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.n_zik.compagnon.generated.resources.Res
import app.n_zik.compagnon.generated.resources.chevron_down
import app.n_zik.compagnon.generated.resources.player_close_full
import app.n_zik.compagnon.player.ConnectionState
import app.n_zik.compagnon.player.PlayerRepository
import app.n_zik.compagnon.player.PlayerState
import app.n_zik.compagnon.player.SessionContract
import app.n_zik.compagnon.ui.theme.colorPalette
import org.jetbrains.compose.resources.stringResource

/**
 * Full player, after the phone's `Player` + `Controls`: large artwork, title and artists, seek bar,
 * transport and modes. Closing it goes back to the queue.
 */
@Composable
fun FullPlayerScreen(
    repository: PlayerRepository,
    state: PlayerState?,
    connection: ConnectionState,
    onCommand: CommandLauncher,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = colorPalette()
    val track = state?.currentTrack
    val playback = SessionContract.FEATURE_PLAYBACK in repository.features
    val live = connection == ConnectionState.Live
    val position = rememberPositionMs(repository, state, live)
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(palette.background0)
            // Clicks on blank areas stop here and never reach the queue underneath.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            PlayerIconButton(Res.drawable.chevron_down, stringResource(Res.string.player_close_full), onClick = onClose)
            Spacer(Modifier.weight(1f))
            ConnectionIndicator(connection)
        }
        BoxWithConstraints(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(vertical = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            val side = minOf(maxWidth, maxHeight, 480.dp)
            Artwork(repository, track?.id, track?.hasArtwork == true, size = side)
        }
        Column(
            modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TrackTitle(track, titleStyleSize = 1, centered = true)
            SeekBar(
                positionMs = position,
                durationMs = track?.durationMs,
                onSeek = { target -> onCommand { seek(target) } },
                enabled = live && playback && track != null,
                barHeight = 4.dp,
            )
            if (playback) {
                TransportControls(state, onCommand, buttonSize = 52.dp, playSize = 68.dp, enabled = live)
                if (state != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ModeControls(state, onCommand, enabled = live)
                    }
                }
            }
            Box(Modifier.height(8.dp))
        }
    }
}
