package app.n_zik.compagnon

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import app.n_zik.compagnon.pairing.BridgeClient
import app.n_zik.compagnon.pairing.CandidateAddresses
import app.n_zik.compagnon.pairing.CredentialStore
import app.n_zik.compagnon.pairing.PairingController
import app.n_zik.compagnon.pairing.PairingListener
import app.n_zik.compagnon.pairing.SystemNetworkInterfaceSource
import app.n_zik.compagnon.ui.pairing.PairingApp
import app.n_zik.compagnon.ui.theme.NZikTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withContext
import java.net.InetAddress

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = AppInfo.NAME,
        state = rememberWindowState(width = 720.dp, height = 860.dp),
    ) {
        App()
    }
}

@Composable
fun App() {
    val scope = remember { CoroutineScope(SupervisorJob() + Dispatchers.Main) }
    val client = remember { BridgeClient() }
    val controller = remember {
        PairingController(
            scope = scope,
            api = client,
            store = CredentialStore(),
            listenerFactory = { PairingListener(scope) },
            candidateProvider = { withContext(Dispatchers.IO) { CandidateAddresses.select(SystemNetworkInterfaceSource) } },
            defaultDeviceName = defaultDeviceName(),
        )
    }
    DisposableEffect(controller) {
        controller.start()
        onDispose {
            // Leaving the app always closes the pairing listener.
            controller.close()
            client.close()
            scope.cancel()
        }
    }
    NZikTheme {
        PairingApp(controller)
    }
}

/** The Windows computer name, editable by the user before pairing (contract §4.3). */
private fun defaultDeviceName(): String {
    val name = System.getenv("COMPUTERNAME")?.takeIf { it.isNotBlank() }
        ?: runCatching { InetAddress.getLocalHost().hostName }.getOrNull()?.takeIf { it.isNotBlank() }
        ?: "PC"
    return name.trim().take(64)
}
