package app.n_zik.compagnon.components.navigation.header

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.n_zik.compagnon.bridge.ConnectionState
import app.n_zik.compagnon.components.ui.screens.bridge.ConnectionIndicator
import app.n_zik.compagnon.generated.resources.Res
import app.n_zik.compagnon.generated.resources.bridge_server
import app.n_zik.compagnon.generated.resources.devices
import app.n_zik.compagnon.generated.resources.settings
import org.jetbrains.compose.resources.stringResource

/**
 * Port of the header's `ActionBar` (phone's `app/it/fast4x/rimusic/ui/components/navigation/header/ActionBar.kt`
 * 207), on the PC: the connection to the phone ([ConnectionIndicator]), the `devices` icon of the phone's
 * "PC server" menu entry, which opens the "Phone" panel ([onPhone]), then the `settings` icon of the phone's
 * menu entry "Settings", which opens the Compagnon's settings ([onSettings], story 12).
 * Dropped: search (no search route in contract v1) and the burger menu with the profile face (history,
 * statistics, rewind, Listen Together, profiles…: phone features); its "Settings" entry is the icon here.
 */
@Composable
fun ActionBar(
    connection: ConnectionState,
    onPhone: () -> Unit,
    onSettings: () -> Unit,
) {
    ConnectionIndicator(connection, modifier = Modifier.padding(end = 8.dp))

    HeaderIcon(Res.drawable.devices, contentDescription = stringResource(Res.string.bridge_server), onClick = onPhone)

    HeaderIcon(Res.drawable.settings, contentDescription = stringResource(Res.string.settings), onClick = onSettings)
}
