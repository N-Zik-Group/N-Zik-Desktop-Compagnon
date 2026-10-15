package app.n_zik.compagnon.components.themed

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.n_zik.compagnon.colorPalette
import app.n_zik.compagnon.components.dialog.common.ButtonRow
import app.n_zik.compagnon.components.dialog.common.DialogButton
import app.n_zik.compagnon.components.dialog.common.DialogFrame
import app.n_zik.compagnon.components.theme.ColorPalette
import app.n_zik.compagnon.generated.resources.*
import app.n_zik.compagnon.typography
import app.n_zik.compagnon.uiRoundnessShape
import app.n_zik.compagnon.utils.bold
import app.n_zik.compagnon.utils.center
import app.n_zik.compagnon.utils.medium
import app.n_zik.compagnon.utils.secondary
import app.n_zik.compagnon.utils.semiBold
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Port of `ConfirmationDialog` (the phone's `app/n_zik/android/components/dialog/common/
 * ConfirmationDialog.kt`): a message + the Cancel (danger) / Confirm (primary) [ButtonRow] on the
 * shared [DialogFrame] (the phone's parity — the cancel pill is the red `danger`, the confirm the
 * accent `primary`). PC only: the frame's fixed max width (560 dp default — a window is much
 * wider than a phone). Dropped: the unused gen-1 `cancelBackgroundPrimary` /
 * `confirmBackgroundPrimary` parameters. Story 12 adds [ValueSelectorDialog] and
 * [InputNumericDialog] (with [textFieldColors]) below.
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
    DialogFrame(onDismiss = onDismiss, modifier = modifier) {
        // Message text
        BasicText(
            text = text,
            style = typography().s.copy(
                color = colorPalette().text,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
        )

        // Action buttons (the phone's parity — both pills via the shared [ButtonRow])
        ButtonRow(
            buttons = listOf(
                DialogButton(
                    text = cancelText,
                    onClick = onCancel,
                    danger = true,
                ),
                DialogButton(
                    text = confirmText,
                    onClick = {
                        onConfirm()
                        onDismiss()
                    },
                    primary = true,
                ),
            ),
        )
    }
}

/** Port of `textFieldColors` (the phone's `InputNumericDialog` field colors, inlined in its `components/dialog/common/InputNumericDialog.kt`). */
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
 * Port of `ValueSelectorDialog` (the phone's `app/n_zik/android/components/dialog/common/
 * ValueSelectorDialog.kt`): a title + secondary line, the radio list (its n_zik radio language)
 * and the Cancel [ButtonRow] on the shared [DialogFrame]. PC only: the phone's `lockedValues` /
 * `lockedLabel` parameters (the PC's screens do not lock rows) and the frame's fixed max width
 * (560 dp default — a window is much wider than a phone).
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
    DialogFrame(
        onDismiss = onDismiss,
        modifier = modifier,
        title = title,
    ) {
        titleSecondary?.let {
            Spacer(modifier = Modifier.height(4.dp))
            BasicText(
                text = it,
                style = typography().s.copy(
                    color = colorPalette.textSecondary,
                ),
            )
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
        ButtonRow(
            buttons = listOf(
                DialogButton(
                    text = stringResource(Res.string.cancel),
                    onClick = onDismiss,
                    danger = true,
                ),
            ),
        )
    }
}

/**
 * The phone's `InputNumericDialog` confirm check (inlined in its `components/dialog/common/
 * InputNumericDialog.kt`): an empty field is refused, a value under [valueMin] is refused,
 * anything else is kept as typed — the phone has no maximum check, so neither does the PC
 * (`valueMax` is only shown by the caller). PC only: a non-number reads as empty instead of
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
 * Port of `InputNumericDialog` (the phone's `app/n_zik/android/components/dialog/common/
 * InputNumericDialog.kt`), on the shared [DefaultDialog] and [ButtonRow] (Confirm secondary,
 * Cancel danger — the phone's roles), without its leading icon and border; its confirm check is
 * [NumericInput.check] (no maximum, as on the phone). It keeps the phone's own inner title
 * (`s.semiBold`, the phone's `InputNumericDialog.kt:55-60`) instead of the frame's `l` title —
 * parity, not a deviation.
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

    DefaultDialog(onDismiss = onDismiss, modifier = modifier) {
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

        ButtonRow(
            buttons = listOf(
                DialogButton(
                    text = stringResource(Res.string.confirm),
                    onClick = {
                        when (val checked = NumericInput.check(txtField.value, valueMin)) {
                            NumericInput.Empty -> txtFieldError.value = valueCannotEmpty
                            NumericInput.BelowMin -> txtFieldError.value = "$valueMustBeGreater $valueMin"
                            // The phone hands the field's text as typed (`setValue(txtField.value)`), trimmed here
                            is NumericInput.Valid -> setValue(txtField.value.trim())
                        }
                    },
                ),
                DialogButton(
                    text = stringResource(Res.string.cancel),
                    onClick = onDismiss,
                    danger = true,
                ),
            ),
        )
    }
}

/**
 * Port of `DefaultDialog` (the phone's `app/n_zik/android/components/dialog/common/
 * DefaultDialog.kt`): a vertically scrolling body on the shared [DialogFrame] (no title, no
 * built-in buttons — the content decides). The frame body itself stays non-scrollable; the
 * scrollable column keeps tall content inside the frame's 80 % height cap.
 * Ported ahead for `spec-home-songs-toolbar-actions` (decision 5 exception agreed for that spec):
 * no PC screen opens it yet.
 */
@Composable
fun DefaultDialog(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    horizontalAlignment: Alignment.Horizontal = Alignment.CenterHorizontally,
    content: @Composable ColumnScope.() -> Unit,
) {
    DialogFrame(
        onDismiss = onDismiss,
        modifier = modifier,
        horizontalAlignment = horizontalAlignment,
    ) {
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState()),
            content = content,
        )
    }
}

/**
 * Port of `TextFieldDialog` (the phone's `app/n_zik/android/components/dialog/common/
 * TextFieldDialog.kt`): a centred single-line field with its hint, "Cancel" (danger) and "Done"
 * (primary) [ButtonRow], Done / Enter accepted only when [isTextInputValid]; the field takes the
 * focus 300 ms after opening, as on the phone.
 * Ported ahead for `spec-home-songs-toolbar-actions` (decision 5 exception agreed for that spec):
 * no PC screen opens it yet.
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
    var textFieldValue by rememberSaveable(initialTextInput, stateSaver = TextFieldValue.Saver) {
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
                    // Fully qualified: the decoration box's ColumnScope receiver shadows the
                    // top-level AnimatedVisibility
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

        ButtonRow(
            buttons = listOf(
                DialogButton(
                    text = cancelText,
                    onClick = onCancel,
                    danger = true,
                ),
                DialogButton(
                    text = doneText,
                    onClick = {
                        if (isTextInputValid(textFieldValue.text)) {
                            onDismiss()
                            onDone(textFieldValue.text)
                        }
                    },
                    primary = true,
                ),
            ),
        )
    }

    LaunchedEffect(Unit) {
        delay(300)
        runCatching { focusRequester.requestFocus() }
    }
}

/**
 * The phone's `Info` (`models/Info.kt`): an entry of [SelectorDialog]; a `null` [name] reads
 * "Not selectable". Kept for `spec-home-songs-toolbar-actions` (decision 5 exception agreed for
 * that spec); no living phone source (the phone's `SelectorDialog` was deleted in the #896
 * centralization).
 */
data class Info(val id: String, val name: String?)

/**
 * A title, the distinct [values], "Cancel" — a port of the phone's gen-1 `SelectorDialog` (its
 * `components/themed/Dialog.kt` 576-650), migrated onto the shared [DialogFrame] and [ButtonRow].
 * Kept for `spec-home-songs-toolbar-actions` (decision 5 exception agreed for that spec); no
 * living phone source (deleted in the phone's #896 centralization). The title now renders in the
 * frame's `l` semi-bold title language (the phone's gen-1 `s.semiBold` inner title is dropped).
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
    DialogFrame(
        onDismiss = onDismiss,
        modifier = modifier,
        title = title,
    ) {
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState()),
        ) {
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
        ButtonRow(
            buttons = listOf(
                DialogButton(
                    text = stringResource(Res.string.cancel),
                    onClick = onDismiss,
                    danger = true,
                ),
            ),
        )
    }
}

/**
 * Port of `StringListDialog` (the phone's `app/n_zik/android/components/dialog/common/
 * StringListDialog.kt`): a header with its title and "add" button, the strings with their red
 * trash, "No items" when empty, and the Cancel [ButtonRow], on the shared [DialogFrame]. PC
 * adaptation: the add input is a [TextFieldDialog] (the phone's `SettingsInputDialog` belongs to
 * its settings screens, not ported).
 * Ported ahead for `spec-home-songs-toolbar-actions` (decision 5 exception agreed for that spec):
 * no PC screen opens it yet.
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
    val colorPalette = colorPalette()
    var showStringAddDialog by remember { mutableStateOf(false) }
    var showStringRemoveDialog by remember { mutableStateOf(false) }
    var removingItem by remember { mutableStateOf("") }
    var errorDialog by remember { mutableStateOf(false) }

    DialogFrame(onDismiss = onDismiss, modifier = modifier) {
        // Header (the title + the "add" action, as on the phone)
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            BasicText(
                text = title,
                style = typography().l.semiBold.copy(color = colorPalette.text),
            )
            Button(
                onClick = { showStringAddDialog = true },
                colors = ButtonDefaults.buttonColors(
                    containerColor = colorPalette.accent,
                    contentColor = colorPalette.textSecondary,
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
                            style = typography().s.copy(color = colorPalette.text),
                            modifier = Modifier.weight(1f),
                        )
                        // Fully qualified: absolute naming conflict with this package's `IconButton`
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
                                tint = colorPalette.red,
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
                    style = typography().s.copy(color = colorPalette.textSecondary),
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        ButtonRow(
            buttons = listOf(
                DialogButton(
                    text = stringResource(Res.string.cancel),
                    onClick = onDismiss,
                    danger = true,
                ),
            ),
        )
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
 * A title, [content], one text button — a port of the phone's gen-1 `GenericDialog` (its
 * `components/themed/Dialog.kt` 1094-1130), migrated onto the shared [DialogFrame] and
 * [ButtonRow]. Kept for `spec-home-songs-toolbar-actions` (decision 5 exception agreed for that
 * spec); no living phone source (deleted in the phone's #896 centralization). The title now
 * renders in the frame's `l` semi-bold title language (the phone's gen-1 `s.bold` inner title is
 * dropped).
 */
@Composable
fun GenericDialog(
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit,
    title: String,
    textButton: String = stringResource(Res.string.cancel),
    content: @Composable () -> Unit,
) {
    DialogFrame(
        onDismiss = onDismiss,
        modifier = modifier,
        title = title,
    ) {
        content()
        ButtonRow(
            buttons = listOf(
                DialogButton(
                    text = textButton,
                    onClick = onDismiss,
                    danger = true,
                ),
            ),
        )
    }
}

/**
 * Port of `InProgressDialog` (the phone's `app/n_zik/android/components/dialog/common/
 * InProgressDialog.kt`): [text] in `l` bold, a 48 dp wavy progress (determinate when [total] > 0,
 * with "done / total"), and the Cancel [ButtonRow] when [onDismiss] is set, on the shared
 * [DefaultDialog]. [isLandscape] narrows the card to 30 % (the phone's landscape; the PC passes
 * its window's).
 * Ported ahead for `spec-home-songs-toolbar-actions` (decision 5 exception agreed for that spec):
 * no PC screen opens it yet.
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
            ButtonRow(
                buttons = listOf(
                    DialogButton(
                        text = stringResource(Res.string.cancel),
                        onClick = onDismiss,
                        danger = true,
                    ),
                ),
            )
        }
    }
}

/** The phone's `InProgressDialog` progress (its `components/dialog/common/InProgressDialog.kt`): `done / total`, `0` without a total. */
fun inProgressFraction(total: Int, done: Int): Float = if (total > 0) (done.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f
