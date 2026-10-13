package app.n_zik.compagnon.components.themed

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.n_zik.compagnon.colorPalette
import app.n_zik.compagnon.components.styling.Dimensions

/**
 * Port of `Loader` (phone's `app/it/fast4x/rimusic/ui/components/themed/Loader.kt` 20-40): the Material 3
 * Expressive `LoadingIndicator` in `accent`, [size] 100 dp, centred, with the floating bar's bottom spacer
 * under it, as with the phone's `BottomFloating` navigation bar — the bar the PC always draws.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun Loader(
    size: Dp = 100.dp,
    modifier: Modifier = Modifier.fillMaxWidth(),
) {
    // The phone pads only with its `BottomFloating` bar; the PC always draws that bar (frozen on the phone's
    // defaults, NAV adaptation), so it always pads — the phone's own position would mismatch the PC's bar
    val bottomPadding = Dimensions.bottomSpacer

    Box(
        modifier = modifier.padding(bottom = bottomPadding),
    ) {
        LoadingIndicator(
            color = colorPalette().accent,
            modifier = Modifier
                .align(Alignment.Center)
                .size(size),
        )
    }
}
