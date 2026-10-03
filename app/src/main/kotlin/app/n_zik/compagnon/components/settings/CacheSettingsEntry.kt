package app.n_zik.compagnon.components.settings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.n_zik.compagnon.colorPalette
import app.n_zik.compagnon.components.themed.HeaderIconButton
import app.n_zik.compagnon.generated.resources.Res
import app.n_zik.compagnon.generated.resources.chevron_forward
import app.n_zik.compagnon.generated.resources.trash
import app.n_zik.compagnon.typography
import app.n_zik.compagnon.uiRoundnessShape
import app.n_zik.compagnon.utils.semiBold
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/**
 * Port of `CacheSettingsEntry` (phone's `app/it/fast4x/rimusic/ui/screens/settings/SettingsScreen.kt`
 * 1041-1134): [OtherSettingsEntry] with a trash button ([onTrashClick]) before the chevron. Its press scale
 * is ported as on the phone, where nothing sets the pressed state.
 */
@Composable
fun CacheSettingsEntry(
    title: String,
    text: String,
    icon: DrawableResource,
    onClick: () -> Unit,
    onTrashClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = tween(150),
        label = "scale",
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(uiRoundnessShape())
            .clip(uiRoundnessShape()).clickable(onClick = onClick),
        color = Color.Transparent,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .scale(scale),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Icon
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(
                            color = colorPalette().accent.copy(alpha = 0.1f),
                            shape = uiRoundnessShape(),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(icon),
                        tint = colorPalette().accent,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                }

                // Content
                Column(
                    modifier = Modifier.weight(1f),
                ) {
                    BasicText(
                        text = title,
                        style = typography().s.semiBold.copy(
                            color = colorPalette().text,
                        ),
                    )
                    if (text.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        BasicText(
                            text = text,
                            style = typography().xs.copy(
                                color = colorPalette().textSecondary,
                            ),
                        )
                    }
                }

                // Trash button
                HeaderIconButton(
                    icon = Res.drawable.trash,
                    enabled = true,
                    color = colorPalette().text,
                    onClick = onTrashClick,
                )

                // Arrow indicator
                Icon(
                    painter = painterResource(Res.drawable.chevron_forward),
                    tint = colorPalette().textSecondary,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}
