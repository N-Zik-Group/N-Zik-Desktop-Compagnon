package app.n_zik.compagnon.components.ui.screens.bridge

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.n_zik.compagnon.AppInfo
import app.n_zik.compagnon.AppearanceState
import app.n_zik.compagnon.MainActivity
import app.n_zik.compagnon.bridge.library.LibraryRepository
import app.n_zik.compagnon.bridge.pairing.ActivePairing
import app.n_zik.compagnon.bridge.pairing.PairedStatus
import app.n_zik.compagnon.bridge.pairing.PairingController
import app.n_zik.compagnon.bridge.pairing.PairingState
import app.n_zik.compagnon.bridge.state.PlayerRepository
import app.n_zik.compagnon.playback.cache.AudioCache
import app.n_zik.compagnon.playback.services.LocalPlayback
import app.n_zik.compagnon.playback.vlc.AudioEngine
import app.n_zik.compagnon.playback.vlc.VlcAudioEngine
import app.n_zik.compagnon.playback.vlc.VlcRuntime
import app.n_zik.compagnon.utils.AppLanguage
import app.n_zik.compagnon.utils.coroutines.NzikDispatchers
import java.util.logging.Logger
import kotlinx.coroutines.CoroutineScope
import app.n_zik.compagnon.colorPalette
import app.n_zik.compagnon.utils.semiBold
import app.n_zik.compagnon.typography
import app.n_zik.compagnon.enums.PairingMode
import app.n_zik.compagnon.components.settings.SettingsDescription
import app.n_zik.compagnon.components.themed.HeaderWithIcon
import app.n_zik.compagnon.generated.resources.devices
import androidx.compose.ui.text.style.TextAlign
import app.n_zik.compagnon.generated.resources.Res
import app.n_zik.compagnon.generated.resources.app_subtitle
import app.n_zik.compagnon.generated.resources.starting
import app.n_zik.compagnon.generated.resources.validating
import org.jetbrains.compose.resources.stringResource

/**
 * Top-level navigation: one pairing screen per [PairingState]; `Paired(Ok)` opens the main window with
 * a player session built by [playerFactory] and a library reader built by [libraryFactory] from the
 * credential, the session closed as soon as that state is left
 * (revocation, "Forget this phone", a new check).
 *
 * Story 12: with the session comes the PC's own player ([localPlaybackFactory], on the embedded libvlc
 * when it loads, else without engine), started with it and released with it: the player stopped, then
 * the vlcj media player and factory released. The player's scope is a fire-and-forget one
 * ([NzikDispatchers.fireAndForget] on [NzikDispatchers.PLAYBACK]) and is never cancelled from here:
 * a composable leaving composition is not the owner of a scope that in-flight work (forge, probe,
 * revocation, download) must be allowed to finish on.
 */
@Composable
fun BridgeScreen(
    controller: PairingController,
    playerFactory: (ActivePairing) -> PlayerRepository,
    libraryFactory: (ActivePairing) -> LibraryRepository,
    localPlaybackFactory: (ActivePairing, PlayerRepository, AudioEngine?, CoroutineScope) -> LocalPlayback,
    audioCache: AudioCache?,
    appearanceState: AppearanceState,
) {
    val state by controller.state.collectAsState()
    val shown = state
    val active = controller.active
    // The applied language tag (app.n_zik.compagnon.utils.AppLanguage): a change re-keys the screens
    // below so strings recompose in the new locale, while the session state above survives the re-key.
    val appliedTag by AppLanguage.appliedTag.collectAsState()
    if (shown is PairingState.Paired && shown.status == PairedStatus.Ok && active != null) {
        val repository = remember(active) { playerFactory(active) }
        val library = remember(active) { libraryFactory(active) }
        val playbackScope = remember(repository) { NzikDispatchers.fireAndForget(NzikDispatchers.PLAYBACK) }
        val engine = remember(repository) { createEngine() }
        val localPlayback = remember(repository) { localPlaybackFactory(active, repository, engine, playbackScope) }
        DisposableEffect(repository) {
            repository.start()
            localPlayback.start()
            onDispose {
                // The playback scope is fire-and-forget: never cancelled, in-flight work survives.
                localPlayback.close()
                repository.close()
                engine?.release()
            }
        }
        key(appliedTag) {
            // Re-keyed on the applied language tag (strings recompose in the new locale); the
            // repository, playback and engine state above it survives the re-key.
            MainActivity(
                repository = repository,
                library = library,
                localPlayback = localPlayback,
                audioCache = audioCache,
                record = shown.record,
                appearanceState = appearanceState,
                onForget = {
                    // The session is closed first, then the pairing is erased and the QR comes back.
                    repository.close()
                    controller.forget()
                },
            )
        }
        return
    }
    key(appliedTag) {
        // The pairing page keeps its scroll and in-flight transition state: the re-key resets it
        // (acceptable on a language change). Only the session state is hoisted above the key.
        val palette = colorPalette()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(palette.background0)
                .verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.TopCenter,
        ) {
            // PC: a window is wider than a phone; the page keeps a phone-like width, centred
            Column(
                modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(vertical = 24.dp),
            ) {
                Header()
                AnimatedContent(
                    targetState = state,
                    contentKey = ::screenKey,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "pairingScreen",
                ) { shown -> Screen(shown, controller) }
            }
        }
    }
}

/** The vlcj engine on the loaded libvlc (embedded on Windows, system on Linux), or `null` when libvlc is not available (the app keeps working). */
private fun createEngine(): AudioEngine? {
    if (!VlcRuntime.isAvailable) return null
    return runCatching { VlcAudioEngine() }
        .onFailure { Logger.getLogger("BridgeScreen").warning("Audio engine unavailable: ${it::class.simpleName}") }
        .getOrNull()
}

/** Screens only animate when the screen itself changes, not on every field update. */
private fun screenKey(state: PairingState): String = when (state) {
    PairingState.Starting -> "starting"
    is PairingState.Unpaired -> "unpaired-${state.mode}"
    is PairingState.Validating -> "validating"
    is PairingState.Paired -> if (state.status == PairedStatus.Unreachable) "unreachable" else "paired"
    PairingState.Revoked -> "revoked"
}

@Composable
private fun Screen(state: PairingState, controller: PairingController) {
    when (state) {
        PairingState.Starting -> Progress(stringResource(Res.string.starting))
        is PairingState.Validating -> Progress(stringResource(Res.string.validating))
        is PairingState.Unpaired -> when (state.mode) {
            PairingMode.Qr -> PairPcCard(
                state = state,
                onDeviceNameChange = controller::setDeviceName,
                onManualEntry = controller::showManual,
                onDismissError = controller::dismissError,
            )
            PairingMode.Manual -> ManualPairingCard(
                state = state,
                onFormChange = controller::updateManual,
                onDeviceNameChange = controller::setDeviceName,
                onSubmit = controller::submitManual,
                onShowQr = controller::showQr,
                onDismissError = controller::dismissError,
            )
        }
        is PairingState.Paired -> if (state.status == PairedStatus.Unreachable) {
            UnreachableScreen(state, onRetry = controller::retry, onEditIp = controller::editIp, onForget = controller::forget)
        } else {
            PairedScreen(state, onRetry = controller::retry, onForget = controller::forget)
        }
        PairingState.Revoked -> RevokedScreen(onPairAgain = controller::pairAgain)
    }
}

/**
 * The page header of the phone's "PC server" screen (`BridgeServerScreen.kt` 124-137): `HeaderWithIcon` (the
 * app's name with the `devices` icon, disabled) then the centred `SettingsDescription`.
 */
@Composable
private fun Header() {
    HeaderWithIcon(
        title = AppInfo.NAME,
        iconId = Res.drawable.devices,
        enabled = false,
        showIcon = true,
        modifier = Modifier,
        onClick = {},
    )
    SettingsDescription(
        text = stringResource(Res.string.app_subtitle),
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun Progress(text: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CircularProgressIndicator(color = colorPalette().accent)
        Text(text, style = typography().s, color = colorPalette().textSecondary)
    }
}
