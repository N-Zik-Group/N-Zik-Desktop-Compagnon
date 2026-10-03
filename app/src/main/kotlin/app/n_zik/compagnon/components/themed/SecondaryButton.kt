package app.n_zik.compagnon.components.themed

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.unit.dp
import app.n_zik.compagnon.colorPalette
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/** Port of `SecondaryButton` (phone's `app/it/fast4x/rimusic/ui/components/themed/SecondaryButton.kt` 48). */
@Composable
fun SecondaryButton(
    onClick: () -> Unit,
    iconId: DrawableResource,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Box(
        modifier = modifier
            .clickable(enabled = enabled, onClick = onClick)
            .size(36.dp),
    ) {
        Image(
            painter = painterResource(iconId),
            contentDescription = null,
            colorFilter = ColorFilter.tint(colorPalette().text),
            modifier = Modifier
                .align(Alignment.Center)
                .size(22.dp),
        )
    }
}
