package app.n_zik.compagnon.components.themed

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.n_zik.compagnon.utils.bold
import app.n_zik.compagnon.colorPalette
import app.n_zik.compagnon.utils.semiBold
import app.n_zik.compagnon.typography
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/*
 * Port of the phone's `app/it/fast4x/rimusic/ui/components/themed/Header.kt`: `HeaderWithIcon` (155), in its
 * default `UiType.RiMusic` branch, and `HeaderInfo` (275).
 */

@Composable
fun HeaderWithIcon(
    title: String,
    modifier: Modifier,
    iconId: DrawableResource,
    showIcon: Boolean = true,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (showIcon) {
                SecondaryButton(
                    iconId = iconId,
                    enabled = enabled,
                    onClick = onClick,
                )
                Spacer(modifier = Modifier.width(8.dp))
            }

            BasicText(
                text = title,
                style = TextStyle(
                    fontSize = typography().xxl.bold.fontSize,
                    fontWeight = typography().xxl.bold.fontWeight,
                    color = colorPalette().text,
                    textAlign = TextAlign.Start,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun HeaderInfo(
    title: String,
    iconId: DrawableResource,
    spacer: Dp = 5.dp,
) {
    Image(
        painter = painterResource(iconId),
        contentDescription = null,
        colorFilter = ColorFilter.tint(colorPalette().textSecondary),
        modifier = Modifier.size(12.dp),
    )
    BasicText(
        text = title,
        style = TextStyle(
            color = colorPalette().textSecondary,
            fontStyle = typography().xxxs.semiBold.fontStyle,
            fontWeight = typography().xxxs.semiBold.fontWeight,
            fontSize = typography().xxxs.semiBold.fontSize,
        ),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(start = 4.dp),
    )

    Spacer(
        modifier = Modifier.width(spacer),
    )
}
