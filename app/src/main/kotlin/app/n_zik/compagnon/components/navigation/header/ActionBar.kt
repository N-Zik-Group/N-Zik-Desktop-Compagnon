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
import org.jetbrains.compose.resources.stringResource

/**
 * Port of the header's `ActionBar` (phone's `app/it/fast4x/rimusic/ui/components/navigation/header/ActionBar.kt`
 * 207), on the PC: the connection to the phone ([ConnectionIndicator]) then the `devices` icon of the phone's
 * "PC server" menu entry, which opens the "Phone" panel ([onPhone]).
 * Dropped: search (no search route in contract v1) and the burger menu with the profile face (history,
 * statistics, rewind, Listen Together, profiles, settings…: phone features).
 */
@Composable
fun ActionBar(
    connection: ConnectionState,
    onPhone: () -> Unit,
) {
    ConnectionIndicator(connection, modifier = Modifier.padding(end = 8.dp))

    HeaderIcon(Res.drawable.devices, contentDescription = stringResource(Res.string.bridge_server), onClick = onPhone)
}
