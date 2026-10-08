package app.n_zik.compagnon.components.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import app.n_zik.compagnon.colorPalette
import app.n_zik.compagnon.generated.resources.*
import app.n_zik.compagnon.typography
import app.n_zik.compagnon.uiRoundnessShape
import app.n_zik.compagnon.utils.semiBold
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/**
 * Port of `ModernSettingsEntry` (phone's `app/it/fast4x/rimusic/ui/screens/settings/SettingsScreen.kt`
 * 1136-1198): a clickable row (4 dp vertical padding, 12 dp inner, UI roundness) on a plain
 * transparent row: the 32 dp accent badge with its 18 dp icon, the title (xs.semiBold, 2 dp
 * bottom gap) and its description (xxs, `textSecondary`), the 16 dp chevron. Unlike
 * [OtherSettingsEntry] there is NO press scale — the phone's `ModernSettingsEntry` is a bare
 * `Row`, and neither is the desktop's port (the About screen's troubleshooting rows use it).
 */
@Composable
fun ModernSettingsEntry(
    title: String,
    text: String,
    icon: DrawableResource,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(uiRoundnessShape())
            .clickable(onClick = onClick)
            .padding(12.dp),
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
                style = typography().xs.semiBold.copy(color = colorPalette().text),
                modifier = Modifier.padding(bottom = 2.dp),
            )
            if (text.isNotEmpty()) {
                BasicText(
                    text = text,
                    style = typography().xxs.copy(color = colorPalette().textSecondary),
                )
            }
        }

        // Arrow indicator
        Icon(
            painter = painterResource(Res.drawable.chevron_forward),
            tint = colorPalette().textSecondary,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
        )
    }
}
