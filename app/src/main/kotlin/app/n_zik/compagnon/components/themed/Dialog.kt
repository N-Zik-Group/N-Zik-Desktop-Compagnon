package app.n_zik.compagnon.components.themed

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.n_zik.compagnon.colorPalette
import app.n_zik.compagnon.generated.resources.Res
import app.n_zik.compagnon.generated.resources.cancel
import app.n_zik.compagnon.generated.resources.confirm
import app.n_zik.compagnon.typography
import app.n_zik.compagnon.uiRoundnessShape
import app.n_zik.compagnon.utils.medium
import org.jetbrains.compose.resources.stringResource

/**
 * Port of `ConfirmationDialog` (phone's `app/it/fast4x/rimusic/ui/components/themed/Dialog.kt` 314). PC only:
 * the card is at most 560 dp wide (a window is much wider than a phone). Dropped: the unused
 * `cancelBackgroundPrimary` / `confirmBackgroundPrimary` parameters.
 */
@Composable
fun ConfirmationDialog(
    text: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    cancelText: String = stringResource(Res.string.cancel),
    confirmText: String = stringResource(Res.string.confirm),
    onCancel: () -> Unit = onDismiss,
) {
    val colorPalette = colorPalette()
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize(),
        ) {
            Card(
                modifier = modifier
                    .fillMaxWidth(0.9f)
                    // PC: a window is much wider than a phone; the card keeps a phone-like width
                    .widthIn(max = 560.dp)
                    .padding(16.dp),
                shape = uiRoundnessShape(),
                colors = CardDefaults.cardColors(
                    containerColor = colorPalette.background1,
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // Message text
                    BasicText(
                        text = text,
                        style = typography().s.copy(
                            color = colorPalette.text,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 24.dp),
                    )

                    // Action buttons — Row with weight, no conflicting fillMaxWidth
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(colorPalette.background2, uiRoundnessShape())
                                .clip(uiRoundnessShape())
                                .clickable(onClick = onCancel)
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            BasicText(
                                text = cancelText,
                                style = typography().xs
                                    .medium
                                    .copy(
                                        color = colorPalette.text,
                                        textAlign = TextAlign.Center,
                                    ),
                            )
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(colorPalette.accent, uiRoundnessShape())
                                .clip(uiRoundnessShape())
                                .clickable {
                                    onConfirm()
                                    onDismiss()
                                }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            BasicText(
                                text = confirmText,
                                style = typography().xs
                                    .medium
                                    .copy(
                                        color = colorPalette.onAccent,
                                        textAlign = TextAlign.Center,
                                    ),
                            )
                        }
                    }
                }
            }
        }
    }
}
