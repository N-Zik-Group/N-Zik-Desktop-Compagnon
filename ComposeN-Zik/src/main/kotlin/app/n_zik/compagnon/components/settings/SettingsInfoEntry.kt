package app.n_zik.compagnon.components.settings

import androidx.compose.foundation.background
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.n_zik.compagnon.colorPalette
import app.n_zik.compagnon.typography
import app.n_zik.compagnon.uiRoundnessShape
import app.n_zik.compagnon.utils.semiBold
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/**
 * Port of the phone's `OtherInfoSettingsEntry` (phone's
 * `app/it/fast4x/rimusic/ui/screens/settings/SettingsScreen.kt` 971-1036): the read-only settings row —
 * the same 32 dp accent badge with its 18 dp icon, the title (s.semiBold) and its text (xs,
 * `textSecondary`) as [OtherSettingsEntry], but with NO chevron and NO click: it only displays its
 * information (the network-status and maintenance-status rows of the PC settings).
 */
@Composable
fun SettingsInfoEntry(
    title: String,
    text: String,
    icon: DrawableResource,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(uiRoundnessShape()),
        color = Color.Transparent,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
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
            }
        }
    }
}
