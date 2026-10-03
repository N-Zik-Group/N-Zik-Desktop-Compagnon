package app.n_zik.compagnon.components.themed

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.unit.Dp
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/**
 * Port of `Button.Draw()` (phone's `app/it/fast4x/rimusic/ui/components/themed/Button.kt` 27), the icon of a
 * navigation bar button. The ×0.8 scale of `LocalIsManyButtons` only applies above 5 buttons: the
 * Compagnon has 4 at most, so it is always 1.
 */
@Composable
fun Button(
    icon: DrawableResource,
    color: Color,
    padding: Dp,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    Image(
        painter = painterResource(icon),
        contentDescription = null,
        colorFilter = ColorFilter.tint(color),
        modifier = modifier.padding(all = padding)
            .size(height = size, width = size),
    )
}
