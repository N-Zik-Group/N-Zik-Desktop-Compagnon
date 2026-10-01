package app.n_zik.compagnon.ui.pairing

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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.n_zik.compagnon.AppInfo
import app.n_zik.compagnon.generated.resources.Res
import app.n_zik.compagnon.generated.resources.app_subtitle
import app.n_zik.compagnon.generated.resources.starting
import app.n_zik.compagnon.generated.resources.validating
import app.n_zik.compagnon.pairing.PairedStatus
import app.n_zik.compagnon.pairing.PairingController
import app.n_zik.compagnon.pairing.PairingMode
import app.n_zik.compagnon.pairing.PairingState
import app.n_zik.compagnon.ui.theme.colorPalette
import app.n_zik.compagnon.ui.theme.semiBold
import app.n_zik.compagnon.ui.theme.typography
import org.jetbrains.compose.resources.stringResource

/** Pairing navigation: one screen per [PairingState]. */
@Composable
fun PairingApp(controller: PairingController) {
    val state by controller.state.collectAsState()
    val palette = colorPalette()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background0)
            .verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
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
            PairingMode.Qr -> QrPairingScreen(
                state = state,
                onDeviceNameChange = controller::setDeviceName,
                onManualEntry = controller::showManual,
                onDismissError = controller::dismissError,
            )
            PairingMode.Manual -> ManualPairingScreen(
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

@Composable
private fun Header() {
    val palette = colorPalette()
    Column {
        Text(AppInfo.NAME, style = typography().xxl.semiBold, color = palette.text)
        Text(stringResource(Res.string.app_subtitle), style = typography().xs, color = palette.textSecondary)
    }
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
