package app.n_zik.compagnon.components.ui.screens.bridge

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.n_zik.compagnon.bridge.ConnectionState
import app.n_zik.compagnon.colorPalette
import app.n_zik.compagnon.generated.resources.*
import app.n_zik.compagnon.typography
import app.n_zik.compagnon.utils.semiBold
import org.jetbrains.compose.resources.stringResource

/**
 * The connection to the phone, in the style of the phone's server status (`BridgeStatusCard`,
 * `app/n_zik/android/components/ui/screens/bridge/BridgeServerScreen.kt` 173-248): a 10 dp dot, 10 dp, the
 * label in s.semiBold, both in the state's colour — `accent` when live (the phone's "running"), `accent` at
 * 60 % while connecting or reconnecting (its "starting" / "stopping"), `textDisabled` when the server
 * stopped (its "stopped"), `red` otherwise (its "failed"). PC only (the phone has no client connection).
 */
@Composable
fun ConnectionIndicator(connection: ConnectionState, modifier: Modifier = Modifier) {
    val palette = colorPalette()
    val (label, color) = when (connection) {
        ConnectionState.Live -> stringResource(Res.string.connection_live) to palette.accent
        ConnectionState.Connecting -> stringResource(Res.string.connection_connecting) to palette.accent.copy(alpha = 0.6f)
        is ConnectionState.Reconnecting -> stringResource(Res.string.connection_reconnecting) to palette.accent.copy(alpha = 0.6f)
        is ConnectionState.ServerStopped -> stringResource(Res.string.connection_offline) to palette.textDisabled
        else -> stringResource(Res.string.connection_offline) to palette.red
    }
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color),
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(text = label, style = typography().s.semiBold, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
