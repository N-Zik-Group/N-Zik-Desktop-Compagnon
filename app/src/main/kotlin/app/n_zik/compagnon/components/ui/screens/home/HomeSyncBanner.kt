package app.n_zik.compagnon.components.ui.screens.home

import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.n_zik.compagnon.colorPalette
import app.n_zik.compagnon.generated.resources.*
import app.n_zik.compagnon.typography
import app.n_zik.compagnon.utils.formatText
import app.n_zik.compagnon.utils.semiBold
import org.jetbrains.compose.resources.stringResource

/**
 * Spec `spec-fill-pc-ui-gaps-home` #65: the phone's sync banner (its `HomeAlbum.kt` 571-600,
 * `HomeArtist.kt` 553-578, `HomeLibrary.kt` 730-759, all reading its `HomeSyncState`): the
 * `syncing_item` name on a marquee, the `syncing_progress` ("index / total") beside the red
 * `syncing_failed` (shown when [failed] > 0) and, below, the Material3
 * [LinearWavyProgressIndicator] ([progress] 0..1) in the accent over the `background2` track — the
 * phone's 16 dp horizontal / 12 dp vertical padding and its `xxs` semi-bold `textSecondary`
 * labels. The sync state is not in the contract, so it is never shown on the PC — the component is
 * present, never displayed (G1 placeholders pattern).
 */
@Composable
fun HomeSyncBanner(
    currentName: String,
    currentIndex: Int,
    total: Int,
    failed: Int,
    progress: Float,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            BasicText(
                text = formatText(stringResource(Res.string.syncing_item), currentName),
                style = typography().xxs.semiBold.copy(color = colorPalette().textSecondary),
                maxLines = 1,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
                    .basicMarquee(iterations = Int.MAX_VALUE),
            )
            Row {
                BasicText(
                    text = formatText(stringResource(Res.string.syncing_progress), currentIndex, total),
                    style = typography().xxs.semiBold.copy(color = colorPalette().textSecondary),
                )
                if (failed > 0) {
                    BasicText(
                        text = " " + stringResource(Res.string.syncing_failed),
                        style = typography().xxs.semiBold.copy(color = colorPalette().red),
                    )
                }
            }
        }
        LinearWavyProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            color = colorPalette().accent,
            trackColor = colorPalette().background2,
        )
    }
}
