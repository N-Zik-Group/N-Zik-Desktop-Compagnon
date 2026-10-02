package app.n_zik.compagnon.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.n_zik.compagnon.AppInfo
import app.n_zik.compagnon.generated.resources.Res
import app.n_zik.compagnon.generated.resources.close
import app.n_zik.compagnon.generated.resources.connection_kicked
import app.n_zik.compagnon.generated.resources.connection_other_active
import app.n_zik.compagnon.generated.resources.connection_reconnecting_in
import app.n_zik.compagnon.generated.resources.connection_replaced
import app.n_zik.compagnon.generated.resources.connection_server_stopped
import app.n_zik.compagnon.generated.resources.devices
import app.n_zik.compagnon.generated.resources.forget_phone
import app.n_zik.compagnon.generated.resources.forget_phone_text
import app.n_zik.compagnon.generated.resources.paired_as
import app.n_zik.compagnon.generated.resources.paired_other_active_unknown
import app.n_zik.compagnon.generated.resources.paired_with
import app.n_zik.compagnon.generated.resources.phone_panel_open
import app.n_zik.compagnon.generated.resources.phone_panel_title
import app.n_zik.compagnon.generated.resources.reconnect
import app.n_zik.compagnon.generated.resources.retry
import app.n_zik.compagnon.generated.resources.trash
import app.n_zik.compagnon.pairing.PairingRecord
import app.n_zik.compagnon.player.ConnectionState
import app.n_zik.compagnon.player.PlayerRepository
import app.n_zik.compagnon.ui.components.NZikButton
import app.n_zik.compagnon.ui.components.SectionCard
import app.n_zik.compagnon.ui.components.SettingsEntry
import app.n_zik.compagnon.ui.pairing.MessageBanner
import app.n_zik.compagnon.ui.player.CommandLauncher
import app.n_zik.compagnon.ui.player.ConnectionIndicator
import app.n_zik.compagnon.ui.player.FullPlayerScreen
import app.n_zik.compagnon.ui.player.PlayerBar
import app.n_zik.compagnon.ui.player.QueueScreen
import app.n_zik.compagnon.ui.player.noticeText
import app.n_zik.compagnon.ui.theme.colorPalette
import app.n_zik.compagnon.ui.theme.semiBold
import app.n_zik.compagnon.ui.theme.typography
import app.n_zik.compagnon.ui.theme.uiRoundnessShape
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Main window once paired: the queue in the middle (the library comes with story 11b), the fixed
 * player bar at the bottom, the full player over everything on a click on the bar, the "Phone" panel,
 * connection banners and command failures (snackbar).
 */
@Composable
fun MainWindow(repository: PlayerRepository, record: PairingRecord, onForget: () -> Unit) {
    val palette = colorPalette()
    val state by repository.state.collectAsState()
    val connection by repository.connection.collectAsState()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var fullPlayer by remember { mutableStateOf(false) }
    var phonePanel by remember { mutableStateOf(false) }
    val onCommand: CommandLauncher = { command -> scope.launch { repository.command() } }

    LaunchedEffect(repository) {
        repository.notices.collect { notice ->
            // The newest notice replaces the one on screen instead of queueing behind it
            snackbar.currentSnackbarData?.dismiss()
            launch { snackbar.showSnackbar(noticeText(notice)) }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(palette.background0)) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopBar(record, connection, onPhone = { phonePanel = true })
            ConnectionBanner(connection, onReconnect = repository::reconnect)
            QueueScreen(repository, state, onCommand, modifier = Modifier.weight(1f), live = connection == ConnectionState.Live)
            PlayerBar(repository, state, connection, onCommand, onOpenFull = { fullPlayer = true })
        }
        AnimatedVisibility(
            visible = fullPlayer,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
        ) {
            FullPlayerScreen(repository, state, connection, onCommand, onClose = { fullPlayer = false })
        }
        if (phonePanel) {
            PhonePanel(record, connection, onClose = { phonePanel = false }, onForget = onForget)
        }
        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 110.dp),
        ) { data ->
            Snackbar(snackbarData = data, containerColor = palette.background2, contentColor = palette.text)
        }
    }
}

@Composable
private fun TopBar(record: PairingRecord, connection: ConnectionState, onPhone: () -> Unit) {
    val palette = colorPalette()
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(AppInfo.NAME, style = typography().l.semiBold, color = palette.text, modifier = Modifier.weight(1f))
        Row(
            modifier = Modifier
                .clip(uiRoundnessShape())
                .background(palette.background1)
                .clickable(onClick = onPhone)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(painterResource(Res.drawable.devices), null, tint = palette.accent, modifier = Modifier.size(18.dp))
            Text(
                stringResource(Res.string.phone_panel_open, record.serverName),
                style = typography().xs,
                color = palette.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 220.dp),
            )
            ConnectionIndicator(connection)
        }
    }
}

/** Contract §6.4 states that need words, with "Reconnect" / "Retry" where the user must act. */
@Composable
private fun ConnectionBanner(connection: ConnectionState, onReconnect: () -> Unit) {
    val palette = colorPalette()
    val (text, button) = when (connection) {
        ConnectionState.Live, ConnectionState.Connecting, ConnectionState.Revoked -> return
        is ConnectionState.Reconnecting ->
            stringResource(Res.string.connection_reconnecting_in, ((connection.delayMs + 999) / 1_000).toInt()) to null
        is ConnectionState.ServerStopped -> stringResource(Res.string.connection_server_stopped) to Res.string.reconnect
        ConnectionState.Kicked -> stringResource(Res.string.connection_kicked) to Res.string.reconnect
        ConnectionState.Replaced -> stringResource(Res.string.connection_replaced) to Res.string.reconnect
        is ConnectionState.OtherActive -> stringResource(
            Res.string.connection_other_active,
            connection.deviceName ?: stringResource(Res.string.paired_other_active_unknown),
        ) to Res.string.retry
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MessageBanner(text, if (button == null) palette.accent else palette.red, modifier = Modifier.weight(1f))
        if (button != null) {
            TextButton(onClick = onReconnect) { Text(stringResource(button), style = typography().xs.semiBold, color = palette.accent) }
        }
    }
}

/** The "Paired" content of story 10, now a panel of the main window: phone name, "Forget this phone". */
@Composable
private fun PhonePanel(record: PairingRecord, connection: ConnectionState, onClose: () -> Unit, onForget: () -> Unit) {
    val palette = colorPalette()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background0.copy(alpha = 0.7f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .padding(24.dp)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionCard(title = stringResource(Res.string.phone_panel_title), icon = Res.drawable.devices) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(Res.string.paired_with, record.serverName), style = typography().l.semiBold, color = palette.text)
                    Text(stringResource(Res.string.paired_as, record.deviceName), style = typography().xs, color = palette.textSecondary)
                    ConnectionIndicator(connection)
                    SettingsEntry(
                        title = stringResource(Res.string.forget_phone),
                        text = stringResource(Res.string.forget_phone_text),
                        icon = Res.drawable.trash,
                        onClick = onForget,
                    )
                }
            }
            NZikButton(text = stringResource(Res.string.close), onClick = onClose, primary = false)
        }
    }
}
