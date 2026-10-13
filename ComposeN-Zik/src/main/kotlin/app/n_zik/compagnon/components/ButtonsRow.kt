package app.n_zik.compagnon.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.n_zik.compagnon.colorPalette
import app.n_zik.compagnon.uiRoundnessShape

/** Port of `ButtonsRow` (phone's `app/it/fast4x/rimusic/ui/components/ButtonsRow.kt` 24). */
@Composable
fun <E> ButtonsRow(
    chips: List<Pair<E, String>>,
    currentValue: E,
    onValueUpdate: (E) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState()),
    ) {
        chips.forEach { (value, label) ->
            FilterChip(
                label = { Text(label) },
                selected = currentValue == value,
                shape = uiRoundnessShape(),
                colors = FilterChipDefaults
                    .filterChipColors(
                        containerColor = colorPalette().background1,
                        labelColor = colorPalette().text,
                        selectedContainerColor = colorPalette().accent,
                        selectedLabelColor = colorPalette().onAccent,
                    ),
                onClick = { onValueUpdate(value) },
            )

            Spacer(Modifier.width(8.dp))
        }
    }
}
