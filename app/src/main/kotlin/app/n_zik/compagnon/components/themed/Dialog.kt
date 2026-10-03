package app.n_zik.compagnon.components.themed

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
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.n_zik.compagnon.colorPalette
import app.n_zik.compagnon.components.theme.ColorPalette
import app.n_zik.compagnon.generated.resources.Res
import app.n_zik.compagnon.generated.resources.cancel
import app.n_zik.compagnon.generated.resources.confirm
import app.n_zik.compagnon.generated.resources.value_cannot_be_empty
import app.n_zik.compagnon.generated.resources.value_must_be_greater_than
import app.n_zik.compagnon.typography
import app.n_zik.compagnon.uiRoundnessShape
import app.n_zik.compagnon.utils.medium
import app.n_zik.compagnon.utils.semiBold
import org.jetbrains.compose.resources.stringResource

/**
 * Port of `ConfirmationDialog` (phone's `app/it/fast4x/rimusic/ui/components/themed/Dialog.kt` 314). PC only:
 * the card is at most 560 dp wide (a window is much wider than a phone). Dropped: the unused
 * `cancelBackgroundPrimary` / `confirmBackgroundPrimary` parameters.
 * Story 12 adds [ValueSelectorDialog] and [InputNumericDialog] (with [textFieldColors]) below.
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

/** Port of `textFieldColors` (phone's `Dialog.kt` 194-205). */
@Composable
fun textFieldColors(colorPalette: ColorPalette, errorText: String) =
    TextFieldDefaults.colors(
        focusedPlaceholderColor = colorPalette.textDisabled,
        unfocusedPlaceholderColor = colorPalette.textDisabled,
        cursorColor = colorPalette.text,
        focusedTextColor = colorPalette.text,
        unfocusedTextColor = colorPalette.text,
        focusedContainerColor = if (errorText.isEmpty()) colorPalette.background1 else colorPalette.red,
        unfocusedContainerColor = if (errorText.isEmpty()) colorPalette.background1 else colorPalette.red,
        focusedIndicatorColor = colorPalette.accent,
        unfocusedIndicatorColor = colorPalette.textDisabled,
    )

/**
 * Port of `ValueSelectorDialog` (phone's `Dialog.kt` 447-572). PC only: the card is at most 560 dp wide, as
 * [ConfirmationDialog].
 */
@Composable
fun <T> ValueSelectorDialog(
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit,
    title: String,
    titleSecondary: String? = null,
    selectedValue: T,
    values: List<T>,
    onValueSelected: (T) -> Unit,
    valueText: @Composable (T) -> String = { it.toString() },
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
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                        ) {
                            BasicText(
                                text = title,
                                style = typography().l.semiBold.copy(
                                    color = colorPalette.text,
                                ),
                            )
                            if (titleSecondary != null) {
                                Spacer(modifier = Modifier.height(4.dp))
                                BasicText(
                                    text = titleSecondary,
                                    style = typography().s.copy(
                                        color = colorPalette.textSecondary,
                                    ),
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Values list
                    Column(
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                            .weight(1f, false),
                    ) {
                        values.forEach { value ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(0.dp),
                                modifier = Modifier
                                    .clip(uiRoundnessShape()).clickable(
                                        onClick = {
                                            onDismiss()
                                            onValueSelected(value)
                                        },
                                    )
                                    .padding(vertical = 0.dp, horizontal = 16.dp)
                                    .fillMaxWidth()
                                    .clip(uiRoundnessShape()),
                            ) {
                                RadioButton(
                                    selected = selectedValue == value,
                                    onClick = {
                                        onDismiss()
                                        onValueSelected(value)
                                    },
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = colorPalette.accent,
                                        unselectedColor = colorPalette.textSecondary,
                                    ),
                                )

                                BasicText(
                                    text = valueText(value),
                                    style = typography().s.copy(
                                        color = colorPalette.text,
                                    ),
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Cancel button
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colorPalette.background2,
                            contentColor = colorPalette.text,
                        ),
                        shape = uiRoundnessShape(),
                    ) {
                        Text(
                            text = stringResource(Res.string.cancel),
                            style = typography().s.semiBold,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Port of `InputNumericDialog` (phone's `Dialog.kt` 777-890), without its commented-out leading icon and
 * border. PC only: a value over [valueMax] is bounded to it (the phone only checks the minimum), and a
 * non-number reads as empty instead of crashing.
 */
@Composable
fun InputNumericDialog(
    onDismiss: () -> Unit,
    title: String,
    value: String,
    valueMin: String,
    valueMax: String,
    placeholder: String,
    setValue: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val txtFieldError = remember { mutableStateOf("") }
    val txtField = remember { mutableStateOf(value) }
    val valueCannotEmpty = stringResource(Res.string.value_cannot_be_empty)
    val valueMustBeGreater = stringResource(Res.string.value_must_be_greater_than)

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = modifier
                .padding(all = 10.dp)
                .background(color = colorPalette().background1, shape = uiRoundnessShape())
                .padding(vertical = 16.dp)
                .requiredHeight(190.dp),
        ) {
            BasicText(
                text = title,
                style = typography().s.semiBold,
                modifier = Modifier
                    .padding(vertical = 8.dp, horizontal = 24.dp),
            )

            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier
                    .fillMaxWidth(),
            ) {
                TextField(
                    modifier = Modifier
                        .fillMaxWidth(0.7f),
                    colors = textFieldColors(colorPalette(), txtFieldError.value),
                    leadingIcon = {},
                    placeholder = { Text(text = placeholder) },
                    value = txtField.value,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    onValueChange = {
                        txtField.value = it.take(10)
                    },
                )
            }

            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier
                    .fillMaxWidth(),
            ) {
                BasicText(
                    text = if (txtFieldError.value.isNotEmpty()) txtFieldError.value else "---",
                    style = typography().xs.medium,
                    modifier = Modifier
                        .padding(vertical = 8.dp, horizontal = 24.dp),
                )
            }

            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier
                    .fillMaxWidth(),
            ) {
                DialogTextButton(
                    text = stringResource(Res.string.confirm),
                    onClick = {
                        val number = txtField.value.trim().toIntOrNull()
                        if (number == null) {
                            txtFieldError.value = valueCannotEmpty
                            return@DialogTextButton
                        }
                        if (number < valueMin.toInt()) {
                            txtFieldError.value = "$valueMustBeGreater $valueMin"
                            return@DialogTextButton
                        }
                        setValue(minOf(number, valueMax.toInt()).toString())
                    },
                )

                DialogTextButton(
                    text = stringResource(Res.string.cancel),
                    onClick = onDismiss,
                    modifier = Modifier,
                )
            }
        }
    }
}
