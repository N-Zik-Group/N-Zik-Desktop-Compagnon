package app.n_zik.compagnon.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.n_zik.compagnon.ui.theme.colorPalette
import app.n_zik.compagnon.ui.theme.semiBold
import app.n_zik.compagnon.ui.theme.typography
import app.n_zik.compagnon.ui.theme.uiRoundnessShape
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/** Settings-style section card of the phone (`SettingsSectionCard`): tinted icon, accent title, content. */
@Composable
fun SectionCard(
    title: String,
    icon: DrawableResource,
    modifier: Modifier = Modifier,
    description: String? = null,
    content: @Composable () -> Unit,
) {
    val palette = colorPalette()
    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(elevation = 4.dp, shape = uiRoundnessShape(), spotColor = palette.accent.copy(alpha = 0.2f)),
        shape = uiRoundnessShape(),
        colors = CardDefaults.cardColors(containerColor = palette.background1),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 12.dp)) {
                IconBadge(icon)
                Spacer(modifier = Modifier.width(12.dp))
                BasicText(
                    text = title,
                    style = typography().xs.semiBold.copy(color = palette.accent),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (description != null) {
                BasicText(
                    text = description,
                    style = typography().xxs.copy(color = palette.textSecondary),
                    modifier = Modifier.padding(bottom = 16.dp),
                )
            }
            content()
        }
    }
}

/** 32 dp accent-tinted square holding an 18 dp icon, as on the phone's settings. */
@Composable
fun IconBadge(icon: DrawableResource, modifier: Modifier = Modifier) {
    val palette = colorPalette()
    Box(
        modifier = modifier
            .size(32.dp)
            .background(color = palette.accent.copy(alpha = 0.1f), shape = uiRoundnessShape()),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            tint = palette.accent,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
    }
}
