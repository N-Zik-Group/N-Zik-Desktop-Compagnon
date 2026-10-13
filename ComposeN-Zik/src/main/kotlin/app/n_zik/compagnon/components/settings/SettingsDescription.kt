package app.n_zik.compagnon.components.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.n_zik.compagnon.colorPalette
import app.n_zik.compagnon.typography
import app.n_zik.compagnon.utils.color
import app.n_zik.compagnon.utils.secondary
import app.n_zik.compagnon.utils.semiBold

/**
 * Port of `SettingsDescription` (phone's `app/it/fast4x/rimusic/ui/screens/settings/SettingsScreen.kt` 456): a
 * page or section description in xxs `textSecondary` (semi-bold red when [important]), 16 dp from the sides
 * and 8 dp above what follows.
 */
@Composable
fun SettingsDescription(
    text: String,
    modifier: Modifier = Modifier,
    important: Boolean = false,
    textAlign: TextAlign? = null,
) {
    if (textAlign != null) {
        Text(
            text = text,
            style = if (important) typography().xxs.semiBold.color(colorPalette().red)
            else typography().xxs.secondary,
            textAlign = textAlign,
            modifier = modifier
                .padding(horizontal = 16.dp)
                .padding(bottom = 8.dp),
        )
    } else {
        BasicText(
            text = text,
            style = if (important) typography().xxs.semiBold.color(colorPalette().red)
            else typography().xxs.secondary,
            modifier = modifier
                .padding(horizontal = 16.dp)
                .padding(bottom = 8.dp),
        )
    }
}
