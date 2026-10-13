package app.n_zik.compagnon.components.themed

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.unit.dp
import app.n_zik.compagnon.colorPalette
import app.n_zik.compagnon.uiRoundnessShape
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/** Port of `PrimaryButton` (phone's `app/it/fast4x/rimusic/ui/components/themed/PrimaryButton.kt`). */
@Composable
fun PrimaryButton(
    onClick: () -> Unit,
    iconId: DrawableResource,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Box(
        modifier = modifier
            .clip(uiRoundnessShape())
            .clip(uiRoundnessShape()).clickable(enabled = enabled, onClick = onClick)
            .background(colorPalette().background2)
            .size(62.dp),
    ) {
        Image(
            painter = painterResource(iconId),
            contentDescription = null,
            colorFilter = ColorFilter.tint(colorPalette().text),
            modifier = Modifier
                .align(Alignment.Center)
                .size(20.dp),
        )
    }
}
