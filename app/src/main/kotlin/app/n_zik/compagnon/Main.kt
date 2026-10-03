package app.n_zik.compagnon

import java.net.InetAddress
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withContext
import app.n_zik.compagnon.bridge.BridgeSession
import app.n_zik.compagnon.bridge.library.RemoteLibraryRepository
import app.n_zik.compagnon.bridge.pairing.CredentialStore
import app.n_zik.compagnon.bridge.pairing.PairingController
import app.n_zik.compagnon.bridge.pairing.PairingListener
import app.n_zik.compagnon.bridge.state.RemotePlayerRepository
import app.n_zik.compagnon.components.theme.AnimatedAppearance
import app.n_zik.compagnon.components.theme.ColorPalette
import app.n_zik.compagnon.components.theme.rubikFontFamily
import app.n_zik.compagnon.components.ui.screens.bridge.BridgeScreen
import app.n_zik.compagnon.core.network.BridgeClient
import app.n_zik.compagnon.core.network.CandidateAddresses
import app.n_zik.compagnon.core.network.SystemNetworkInterfaceSource
import app.n_zik.compagnon.playback.cache.AudioCache
import app.n_zik.compagnon.playback.services.LocalPlayback
import app.n_zik.compagnon.utils.LocalPreferences
import app.n_zik.compagnon.utils.Preferences
import androidx.compose.runtime.CompositionLocalProvider

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = AppInfo.NAME,
        state = rememberWindowState(width = 1100.dp, height = 800.dp),
    ) {
        App()
    }
}

@Composable
fun App() {
    val scope = remember { CoroutineScope(SupervisorJob() + Dispatchers.Main) }
    val client = remember { BridgeClient() }
    val wsClient = remember { BridgeSession.httpClient() }
    // Story 12: user settings (`settings.json`) and the local audio cache, both next to `pairing.json`
    val preferences = remember { Preferences() }
    val audioCache = remember { AudioCache(maxBytes = { preferences.settings.value.songCacheMaxBytes }) }
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
            wsClient.close()
            scope.cancel()
        }
    }
    // The phone's `MainActivity.setContent` root: its appearance, faded by `AnimatedAppearance`
    val fontFamily = rubikFontFamily()
    val appearanceState = remember(fontFamily) { AppearanceState(computeAppearance(fontFamily)) }
    AnimatedAppearance(
        target = appearanceState.appearance,
        fadeFrom = appearanceState.fadeFromAppearance,
        onFadeComplete = { appearanceState.fadeFromAppearance = null },
    ) { appearance ->
        MaterialTheme(colorScheme = materialColorSchemeOf(appearance.colorPalette)) {
            val rootBackgroundColor by animateColorAsState(
                targetValue = appearanceState.appearance.colorPalette.background0,
                animationSpec = tween(350, easing = FastOutSlowInEasing),
                label = "rootBackground",
            )
            Box(modifier = Modifier.fillMaxSize().background(rootBackgroundColor)) {
                CompositionLocalProvider(LocalPreferences provides preferences) {
                    BridgeScreen(
                        controller = controller,
                        playerFactory = { active -> RemotePlayerRepository.create(active, client, wsClient, controller.revocation, scope) },
                        libraryFactory = { active -> RemoteLibraryRepository.create(active, client, controller.revocation) },
                        localPlaybackFactory = { active, repository, engine, playbackScope ->
                            LocalPlayback(
                                repository = repository,
                                engine = engine,
                                audio = client,
                                address = active.address,
                                deviceToken = active.pairing.deviceToken,
                                cache = audioCache,
                                settings = preferences.settings,
                                revocation = controller.revocation,
                                scope = playbackScope,
                            )
                        },
                        audioCache = audioCache,
                        appearanceState = appearanceState,
                    )
                }
            }
        }
    }
}

/**
 * Compagnon only: the Material 3 colours of the few Material components the ported screens use (cards,
 * text fields, progress indicators), mirrored from the N-Zik palette.
 */
private fun materialColorSchemeOf(palette: ColorPalette) = darkColorScheme(
    primary = palette.accent,
    onPrimary = palette.onAccent,
    secondary = palette.accent,
    onSecondary = palette.onAccent,
    background = palette.background0,
    onBackground = palette.text,
    surface = palette.background1,
    onSurface = palette.text,
    surfaceVariant = palette.background2,
    onSurfaceVariant = palette.textSecondary,
    outline = palette.background3,
    error = palette.red,
)

/** The Windows computer name, editable by the user before pairing (contract §4.3). */
private fun defaultDeviceName(): String {
    val name = System.getenv("COMPUTERNAME")?.takeIf { it.isNotBlank() }
        ?: runCatching { InetAddress.getLocalHost().hostName }.getOrNull()?.takeIf { it.isNotBlank() }
        ?: "PC"
    return name.trim().take(64)
}
