package app.n_zik.compagnon.components.themed

import androidx.compose.foundation.background
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import app.n_zik.compagnon.utils.bold
import app.n_zik.compagnon.utils.center
import app.n_zik.compagnon.utils.secondary
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource
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
import app.n_zik.compagnon.generated.resources.*
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
 * The phone's `InputNumericDialog` confirm check (`Dialog.kt` 850-870): an empty field is refused, a value
 * under [valueMin] is refused, anything else is kept as typed — the phone has no maximum check, so neither
 * does the PC (`valueMax` is only shown by the caller). PC only: a non-number reads as empty instead of
 * crashing (the phone's `toInt()` throws).
 */
sealed interface NumericInput {
    data class Valid(val value: Int) : NumericInput
    data object Empty : NumericInput
    data object BelowMin : NumericInput

    companion object {
        fun check(text: String, valueMin: String): NumericInput {
            val number = text.trim().toIntOrNull() ?: return Empty
            val min = valueMin.trim().toIntOrNull() ?: Int.MIN_VALUE
            return if (number < min) BelowMin else Valid(number)
        }
    }
}

/**
 * Port of `InputNumericDialog` (phone's `Dialog.kt` 777-890), without its commented-out leading icon and
 * border; its confirm check is [NumericInput.check] (no maximum, as on the phone).
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
                        when (val checked = NumericInput.check(txtField.value, valueMin)) {
                            NumericInput.Empty -> txtFieldError.value = valueCannotEmpty
                            NumericInput.BelowMin -> txtFieldError.value = "$valueMustBeGreater $valueMin"
                            // The phone hands the field's text as typed (`setValue(txtField.value)`), trimmed here
                            is NumericInput.Valid -> setValue(txtField.value.trim())
                        }
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


/**
 * Port of `DefaultDialog` (phone's `Dialog.kt` 414-445): a column on `background1`, 90 % wide, at most
 * 600 dp high, scrolling. PC only: at most 560 dp wide, as [ConfirmationDialog].
 * Ported ahead for `spec-home-songs-toolbar-actions` (decision 5 exception agreed for that spec): no PC
 * screen opens it yet.
 */
@Composable
fun DefaultDialog(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    horizontalAlignment: Alignment.Horizontal = Alignment.CenterHorizontally,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize(),
        ) {
            Column(
                horizontalAlignment = horizontalAlignment,
                modifier = modifier
                    .fillMaxWidth(0.9f)
                    .widthIn(max = 560.dp)
                    .heightIn(max = 600.dp)
                    .padding(all = 10.dp)
                    .background(color = colorPalette().background1, shape = uiRoundnessShape())
                    .padding(horizontal = 24.dp, vertical = 16.dp)
                    .verticalScroll(rememberScrollState()),
                content = content,
            )
        }
    }
}

/**
 * Port of `TextFieldDialog` (phone's `Dialog.kt` 208-312): a centred single-line field with its hint, "Cancel"
 * and "Done" (primary), Done / Enter accepted only when [isTextInputValid]; the field takes the focus 300 ms
 * after opening, as on the phone.
 * Ported ahead for `spec-home-songs-toolbar-actions` (decision 5 exception agreed for that spec): no PC
 * screen opens it yet.
 */
@Composable
fun TextFieldDialog(
    hintText: String,
    onDismiss: () -> Unit,
    onDone: (String) -> Unit,
    modifier: Modifier = Modifier,
    cancelText: String = stringResource(Res.string.cancel),
    doneText: String = stringResource(Res.string.done),
    initialTextInput: String = "",
    singleLine: Boolean = true,
    maxLines: Int = 1,
    onCancel: () -> Unit = onDismiss,
    isTextInputValid: (String) -> Boolean = { it.isNotEmpty() },
) {
    val focusRequester = remember { FocusRequester() }
    var textFieldValue by androidx.compose.runtime.saveable.rememberSaveable(initialTextInput, stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(text = initialTextInput, selection = TextRange(initialTextInput.length)))
    }

    DefaultDialog(
        onDismiss = onDismiss,
        modifier = modifier,
    ) {
        BasicTextField(
            value = textFieldValue,
            onValueChange = { textFieldValue = it },
            textStyle = typography().xs.semiBold.center,
            singleLine = singleLine,
            maxLines = maxLines,
            keyboardOptions = KeyboardOptions(imeAction = if (singleLine) ImeAction.Done else ImeAction.None),
            keyboardActions = KeyboardActions(
                onDone = {
                    if (isTextInputValid(textFieldValue.text)) {
                        onDismiss()
                        onDone(textFieldValue.text)
                    }
                },
            ),
            cursorBrush = SolidColor(colorPalette().text),
            decorationBox = { innerTextField ->
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.weight(1f),
                ) {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = textFieldValue.text.isEmpty(),
                        enter = fadeIn(tween(100)),
                        exit = fadeOut(tween(100)),
                    ) {
                        BasicText(
                            text = hintText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = typography().xs.semiBold.secondary,
                        )
                    }
                    innerTextField()
                }
            },
            modifier = Modifier
                .padding(all = 16.dp)
                .weight(weight = 1f, fill = false)
                .focusRequester(focusRequester),
        )

        Row(
            horizontalArrangement = Arrangement.SpaceEvenly,
            modifier = Modifier.fillMaxWidth(),
        ) {
            DialogTextButton(
                text = cancelText,
                onClick = onCancel,
            )
            DialogTextButton(
                primary = true,
                text = doneText,
                onClick = {
                    if (isTextInputValid(textFieldValue.text)) {
                        onDismiss()
                        onDone(textFieldValue.text)
                    }
                },
            )
        }
    }

    LaunchedEffect(Unit) {
        delay(300)
        runCatching { focusRequester.requestFocus() }
    }
}

/** The phone's `Info` (`models/Info.kt`): an entry of [SelectorDialog]; a `null` [name] reads "Not selectable". */
data class Info(val id: String, val name: String?)

/**
 * Port of `SelectorDialog` (phone's `Dialog.kt` 576-650): a title, the distinct [values], "Cancel".
 * Ported ahead for `spec-home-songs-toolbar-actions` (decision 5 exception agreed for that spec): no PC
 * screen opens it yet.
 */
@Composable
fun SelectorDialog(
    onDismiss: () -> Unit,
    title: String,
    values: List<Info>?,
    onValueSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    showItemsIcon: Boolean = false,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = modifier
                .padding(all = 10.dp)
                .background(color = colorPalette().background1, shape = uiRoundnessShape())
                .padding(vertical = 16.dp),
        ) {
            BasicText(
                text = title,
                style = typography().s.semiBold,
                modifier = Modifier.padding(vertical = 8.dp, horizontal = 24.dp),
            )
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                values?.distinct()?.forEach { value ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier
                            .clip(uiRoundnessShape()).clickable(
                                onClick = {
                                    onDismiss()
                                    onValueSelected(value.id)
                                },
                            )
                            .padding(vertical = 12.dp, horizontal = 24.dp)
                            .fillMaxWidth(),
                    ) {
                        if (showItemsIcon) {
                            IconButton(
                                onClick = {},
                                icon = Res.drawable.playlist,
                                color = colorPalette().text,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        BasicText(
                            text = value.name ?: stringResource(Res.string.not_selectable),
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            style = typography().xs.medium,
                        )
                    }
                }
            }
            Box(
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(end = 24.dp),
            ) {
                DialogTextButton(
                    text = stringResource(Res.string.cancel),
                    onClick = onDismiss,
                    modifier = Modifier,
                )
            }
        }
    }
}

/**
 * Port of `StringListDialog` (phone's `Dialog.kt` 907-1091): a card with its title and "add" button, the
 * strings with their red trash, "No items" when empty, "Cancel"; adding opens an input, removing asks for
 * a confirmation, a duplicate shows the conflict. PC adaptation: the add input is a [TextFieldDialog] (the
 * phone's `SettingsInputDialog` belongs to its settings screens, not ported).
 * PC only: the card is at most 560 dp wide, as [ConfirmationDialog].
 * Ported ahead for `spec-home-songs-toolbar-actions` (decision 5 exception agreed for that spec): no PC
 * screen opens it yet.
 */
@Composable
fun StringListDialog(
    title: String,
    addTitle: String,
    addPlaceholder: String,
    removeTitle: String,
    conflictTitle: String,
    list: List<String>,
    add: (String) -> Unit,
    remove: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showStringAddDialog by remember { mutableStateOf(false) }
    var showStringRemoveDialog by remember { mutableStateOf(false) }
    var removingItem by remember { mutableStateOf("") }
    var errorDialog by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .widthIn(max = 560.dp)
                .padding(16.dp),
            shape = uiRoundnessShape(),
            colors = CardDefaults.cardColors(containerColor = colorPalette().background1),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    BasicText(
                        text = title,
                        style = typography().l.semiBold.copy(color = colorPalette().text),
                    )
                    Button(
                        onClick = { showStringAddDialog = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colorPalette().accent,
                            contentColor = colorPalette().textSecondary,
                        ),
                        shape = uiRoundnessShape(),
                    ) {
                        Text(text = addTitle, style = typography().s.semiBold)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (list.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                            .weight(1f, false),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        list.forEach { item ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(uiRoundnessShape())
                                    .clickable { }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                            ) {
                                BasicText(
                                    text = item,
                                    style = typography().s.copy(color = colorPalette().text),
                                    modifier = Modifier.weight(1f),
                                )
                                androidx.compose.material3.IconButton(
                                    onClick = {
                                        removingItem = item
                                        showStringRemoveDialog = true
                                    },
                                    modifier = Modifier.size(32.dp),
                                ) {
                                    Icon(
                                        painter = painterResource(Res.drawable.trash),
                                        contentDescription = stringResource(Res.string.delete),
                                        tint = colorPalette().red,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        BasicText(
                            text = stringResource(Res.string.no_items),
                            style = typography().s.copy(color = colorPalette().textSecondary),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colorPalette().background2,
                        contentColor = colorPalette().text,
                    ),
                    shape = uiRoundnessShape(),
                ) {
                    Text(text = stringResource(Res.string.cancel), style = typography().s.semiBold)
                }
            }
        }
    }

    if (showStringAddDialog) {
        TextFieldDialog(
            hintText = addPlaceholder,
            onDismiss = { showStringAddDialog = false },
            onDone = {
                if (it !in list) add(it) else errorDialog = true
            },
        )
    }
    if (showStringRemoveDialog) {
        ConfirmationDialog(
            text = removeTitle,
            onDismiss = { showStringRemoveDialog = false },
            onConfirm = { remove(removingItem) },
        )
    }
    if (errorDialog) {
        ConfirmationDialog(
            text = conflictTitle,
            onDismiss = { errorDialog = false },
            onConfirm = { errorDialog = false },
            confirmText = stringResource(Res.string.confirm),
        )
    }
}

/**
 * Port of `GenericDialog` (phone's `Dialog.kt` 1094-1130): a bold title, [content], one text button.
 * Ported ahead for `spec-home-songs-toolbar-actions` (decision 5 exception agreed for that spec): no PC
 * screen opens it yet.
 */
@Composable
fun GenericDialog(
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit,
    title: String,
    textButton: String = stringResource(Res.string.cancel),
    content: @Composable () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = modifier
                .padding(all = 48.dp)
                .background(color = colorPalette().background1, shape = uiRoundnessShape())
                .padding(vertical = 16.dp),
        ) {
            BasicText(
                text = title,
                style = typography().s.bold,
                modifier = Modifier.padding(vertical = 8.dp, horizontal = 24.dp),
            )
            content()
            Box(
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(end = 24.dp),
            ) {
                DialogTextButton(text = textButton, onClick = onDismiss, modifier = Modifier)
            }
        }
    }
}

/**
 * Port of `InProgressDialog` (phone's `Dialog.kt` 1537-1611): [text] in `l` bold, a 48 dp wavy progress
 * (determinate when [total] > 0, with "done / total"), a "Cancel" button when [onDismiss] is set.
 * [isLandscape] narrows the card to 30 % (the phone's landscape; the PC passes its window's).
 * Ported ahead for `spec-home-songs-toolbar-actions` (decision 5 exception agreed for that spec): no PC
 * screen opens it yet.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun InProgressDialog(
    total: Int,
    done: Int,
    text: String,
    onDismiss: (() -> Unit)? = null,
    isLandscape: Boolean = false,
) {
    val colorPalette = colorPalette()
    DefaultDialog(
        onDismiss = { if (onDismiss != null) onDismiss() },
        modifier = Modifier.fillMaxWidth(if (isLandscape) 0.3f else 0.8f),
    ) {
        BasicText(
            text = text,
            style = TextStyle(
                textAlign = TextAlign.Center,
                fontSize = typography().l.bold.fontSize,
                fontWeight = typography().l.bold.fontWeight,
                color = colorPalette.text,
            ),
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(16.dp))
        val progress = inProgressFraction(total, done)
        if (total > 0) {
            CircularWavyProgressIndicator(
                progress = { progress },
                modifier = Modifier.size(48.dp).align(Alignment.CenterHorizontally),
                color = colorPalette.accent,
                trackColor = colorPalette.background2,
            )
        } else {
            CircularWavyProgressIndicator(
                modifier = Modifier.size(48.dp).align(Alignment.CenterHorizontally),
                color = colorPalette.accent,
                trackColor = colorPalette.background2,
            )
        }
        if (total > 0) {
            Spacer(modifier = Modifier.height(8.dp))
            BasicText(
                text = "$done / $total",
                style = TextStyle(
                    textAlign = TextAlign.Center,
                    fontStyle = typography().xs.semiBold.fontStyle,
                    color = colorPalette.text,
                ),
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (onDismiss != null) {
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colorPalette.background2,
                    contentColor = colorPalette.text,
                ),
                shape = uiRoundnessShape(),
                modifier = Modifier.fillMaxWidth(0.8f).align(Alignment.CenterHorizontally),
            ) {
                BasicText(
                    text = stringResource(Res.string.cancel),
                    style = TextStyle(
                        textAlign = TextAlign.Center,
                        fontStyle = typography().s.semiBold.fontStyle,
                        color = colorPalette.text,
                    ),
                )
            }
        }
    }
}

/** The phone's `InProgressDialog` progress: `done / total`, `0` without a total. */
fun inProgressFraction(total: Int, done: Int): Float = if (total > 0) (done.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f
