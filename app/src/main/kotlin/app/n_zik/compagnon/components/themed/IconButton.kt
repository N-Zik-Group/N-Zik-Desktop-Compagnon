package app.n_zik.compagnon.components.themed

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.Indication
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.n_zik.compagnon.uiRoundnessShape
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/** Port of `HeaderIconButton` (phone's `app/it/fast4x/rimusic/ui/components/themed/IconButton.kt` 28). */
@Composable
fun HeaderIconButton(
    onClick: () -> Unit,
    icon: DrawableResource,
    color: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    indication: Indication? = null,
    iconSize: Dp? = 20.dp,
    onLongClick: (() -> Unit)? = null,
) {
    IconButton(
        icon = icon,
        color = color,
        onClick = onClick,
        enabled = enabled,
        indication = indication,
        onLongClick = onLongClick,
        modifier = modifier
            .padding(all = 2.dp)
            .size(iconSize ?: 18.dp),
    )
}

/** Port of `IconButton` (phone's `app/it/fast4x/rimusic/ui/components/themed/IconButton.kt` 53). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun IconButton(
    onClick: () -> Unit,
    icon: DrawableResource,
    color: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    indication: Indication? = null,
    onLongClick: (() -> Unit)? = null,
) {
    Image(
        painter = painterResource(icon),
        contentDescription = null,
        colorFilter = ColorFilter.tint(color),
        modifier = modifier
            .clip(uiRoundnessShape())
            .combinedClickable(
                indication = indication ?: ripple(bounded = false),
                interactionSource = remember { MutableInteractionSource() },
                enabled = enabled,
                onClick = onClick,
                onLongClick = onLongClick,
            ),
    )
}
