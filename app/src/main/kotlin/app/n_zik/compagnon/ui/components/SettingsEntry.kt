package app.n_zik.compagnon.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.n_zik.compagnon.generated.resources.Res
import app.n_zik.compagnon.generated.resources.chevron_forward
import app.n_zik.compagnon.ui.theme.colorPalette
import app.n_zik.compagnon.ui.theme.semiBold
import app.n_zik.compagnon.ui.theme.typography
import app.n_zik.compagnon.ui.theme.uiRoundnessShape
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/** Clickable settings row of the phone (`OtherSettingsEntry`): icon badge, title, text, chevron. */
@Composable
fun SettingsEntry(
    title: String,
    text: String,
    icon: DrawableResource,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    trailingContent: (@Composable () -> Unit)? = null,
) {
    val palette = colorPalette()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(uiRoundnessShape())
            .clickable(enabled = enabled, onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconBadge(icon)
        Column(modifier = Modifier.weight(1f)) {
            BasicText(text = title, style = typography().s.semiBold.copy(color = if (enabled) palette.text else palette.textDisabled))
            if (text.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                BasicText(text = text, style = typography().xs.copy(color = palette.textSecondary))
            }
        }
        trailingContent?.invoke()
        Icon(
            painter = painterResource(Res.drawable.chevron_forward),
            tint = palette.textSecondary,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
        )
    }
}

/** Full-width rounded button of the phone's pairing cards (`PairingButton`). */
@Composable
fun NZikButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = true,
    enabled: Boolean = true,
) {
    val palette = colorPalette()
    val containerColor: Color = if (primary) palette.accent else palette.background2
    val contentColor: Color = if (primary) palette.onAccent else palette.text
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = palette.background2,
            disabledContentColor = palette.textDisabled,
        ),
        shape = uiRoundnessShape(),
        modifier = modifier.fillMaxWidth().heightIn(min = 48.dp),
    ) {
        Text(text, style = typography().s.semiBold)
    }
}
