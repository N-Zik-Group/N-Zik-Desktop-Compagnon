package app.n_zik.compagnon

import app.n_zik.compagnon.components.theme.robotoFontFamily
import app.n_zik.compagnon.components.theme.materialTypographyOf
import app.n_zik.compagnon.generated.resources.Res
import app.n_zik.compagnon.generated.resources.app_icon
import androidx.compose.material3.ripple
import androidx.compose.material3.RippleConfiguration
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.foundation.LocalIndication
import app.n_zik.compagnon.core.navigation.isBackKey
import app.n_zik.compagnon.core.navigation.LocalBackDispatcher
import app.n_zik.compagnon.core.navigation.BackDispatcher
import java.net.InetAddress
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import org.jetbrains.compose.resources.painterResource
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withContext
import app.n_zik.compagnon.bridge.BridgeSession
import app.n_zik.compagnon.bridge.library.RemoteLibraryRepository
import app.n_zik.compagnon.bridge.pairing.CredentialStore
import app.n_zik.compagnon.bridge.pairing.PairingController
import app.n_zik.compagnon.bridge.pairing.PairingListener
import app.n_zik.compagnon.bridge.state.RemotePlayerRepository
import app.n_zik.compagnon.components.theme.AnimatedAppearance
import app.n_zik.compagnon.components.theme.rubikFontFamily
import app.n_zik.compagnon.components.ui.screens.bridge.BridgeScreen
import app.n_zik.compagnon.core.network.BridgeClient
import app.n_zik.compagnon.core.network.CandidateAddresses
import app.n_zik.compagnon.core.network.SystemNetworkInterfaceSource
import app.n_zik.compagnon.playback.cache.AudioCache
import app.n_zik.compagnon.playback.services.LocalPlayback
import app.n_zik.compagnon.utils.AppLanguage
import app.n_zik.compagnon.utils.LocalPreferences
import app.n_zik.compagnon.utils.coroutines.NzikDispatchers
import app.n_zik.compagnon.utils.Preferences
import androidx.compose.runtime.CompositionLocalProvider

fun main() = application {
    // The window's Escape key is the phone's system back (story 11c), routed to the paired screen
    val backDispatcher = remember { BackDispatcher() }
    Window(
        onCloseRequest = ::exitApplication,
        title = AppInfo.NAME,
        icon = painterResource(Res.drawable.app_icon),
        state = rememberWindowState(width = 1100.dp, height = 800.dp),
        onPreviewKeyEvent = { event -> isBackKey(event) && backDispatcher.dispatch() },
    ) {
        CompositionLocalProvider(LocalBackDispatcher provides backDispatcher) {
            App()
        }
    }
}

@Composable
fun App() {
    // The app scope carries only non-UI work (pairing machine, pairing listener, the WS session):
    // it runs on the data dispatcher, never on the Swing EDT, and an unhandled failure is logged
    // by the shared fire-and-forget handler instead of dying uncaught on a pool thread.
    val scope = remember { NzikDispatchers.fireAndForget(NzikDispatchers.DATA) }
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
            candidateProvider = { withContext(NzikDispatchers.DATA) { CandidateAddresses.select(SystemNetworkInterfaceSource) } },
            defaultDeviceName = defaultDeviceName(),
            // Contract 1.9.0 (`ui.language`): the last persisted phone language, the `auto_tel`
            // fallback of the "App language" setting. Only a non-null phone language is filed —
            // a phone on `System` files its resolved locale tag; the PC's own OS locale is
            // never filed as a phone language.
            onPhoneLanguage = { fresh ->
                preferences.update {
                    if (it.lastPhoneLanguage == fresh) it else it.copy(lastPhoneLanguage = fresh)
                }
            },
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
    // Contract 1.9.0 (`ui.language`): the applied locale is the composition root's recomposition key
    // (`stringResource` is not locale-state-tracked on desktop, so [BridgeScreen] keys its content on
    // the applied tag for the visible switch). An absent or unparseable tag keeps the OS locale.
    val settings by preferences.settings.collectAsState()
    val phoneLanguage by controller.phoneLanguage.collectAsState()
    // Synchronous first apply during the root's first composition, before [BridgeScreen] reads the
    // tag: no startup null → tag re-key.
    remember {
        AppLanguage.applyTag(
            AppLanguage.resolveLanguageTag(settings.language, phoneLanguage, settings.lastPhoneLanguage),
        )
    }
    // Re-apply on any change of the setting, the last persisted phone language, or a fresh `meta`.
    LaunchedEffect(settings.language, settings.lastPhoneLanguage, phoneLanguage) {
        AppLanguage.applyTag(
            AppLanguage.resolveLanguageTag(settings.language, phoneLanguage, settings.lastPhoneLanguage),
        )
    }
    // The phone's `MainActivity.setContent` root: its appearance, faded by `AnimatedAppearance`
    val fontFamily = rubikFontFamily()
    val materialTypography = materialTypographyOf(robotoFontFamily())
    val appearanceState = remember(fontFamily) { AppearanceState(computeAppearance(fontFamily)) }
    AnimatedAppearance(
        target = appearanceState.appearance,
        fadeFrom = appearanceState.fadeFromAppearance,
        onFadeComplete = { appearanceState.fadeFromAppearance = null },
    ) { appearance ->
        // The phone draws its Material components without a `MaterialTheme`: the default light scheme, with
        // Android's Roboto (story 11c); the app ripple is bounded, in the palette's text colour
        // (phone's `MainActivity.kt` 1595-1598, 2072-2074)
        val rippleConfiguration = remember(appearance.colorPalette.text) {
            RippleConfiguration(color = appearance.colorPalette.text)
        }
        MaterialTheme(colorScheme = lightColorScheme(), typography = materialTypography) {
          CompositionLocalProvider(
            LocalIndication provides ripple(bounded = true),
            LocalRippleConfiguration provides rippleConfiguration,
          ) {
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
}

/** The Windows computer name, editable by the user before pairing (contract §4.3). */
private fun defaultDeviceName(): String {
    val name = System.getenv("COMPUTERNAME")?.takeIf { it.isNotBlank() }
        ?: runCatching { InetAddress.getLocalHost().hostName }.getOrNull()?.takeIf { it.isNotBlank() }
        ?: "PC"
    return name.trim().take(64)
}
