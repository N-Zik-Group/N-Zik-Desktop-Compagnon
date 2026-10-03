package app.n_zik.compagnon.components.tab

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.n_zik.compagnon.utils.bold
import app.n_zik.compagnon.colorPalette
import app.n_zik.compagnon.typography

/*
 * Port of the phone's `app/it/fast4x/rimusic/ui/components/tab/TabHeader.kt` in its default
 * `UiType.RiMusic` layout: title on the left (`xl` bold), additional content after it.
 */

@Composable
private fun Title(title: String) {
    val fontStyle = typography().xl.bold

    Text(
        text = title,
        style = TextStyle(
            fontSize = fontStyle.fontSize,
            fontWeight = fontStyle.fontWeight,
            color = colorPalette().text,
            textAlign = TextAlign.Start,
        ),
        modifier = Modifier.padding(start = Dp.Hairline, end = 12.dp),
    )
}

@Composable
fun TabHeader(
    title: String,
    additionalContent: @Composable () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(top = 10.dp, bottom = 4.dp),
    ) {
        Title(title)
        additionalContent()
    }
}
