package app.n_zik.compagnon.components.tab

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.n_zik.compagnon.components.tab.toolbar.MenuIcon
import app.n_zik.compagnon.colorPalette
import app.n_zik.compagnon.utils.semiBold
import app.n_zik.compagnon.typography
import app.n_zik.compagnon.uiRoundnessShape
import app.n_zik.compagnon.generated.resources.Res
import app.n_zik.compagnon.generated.resources.backspace_outline
import app.n_zik.compagnon.generated.resources.clear
import app.n_zik.compagnon.generated.resources.search
import app.n_zik.compagnon.generated.resources.search_circle
import app.n_zik.compagnon.utils.secondary
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Port of `Search` (phone's `app/n_zik/android/components/tab/Search.kt` 67): the toolbar button and the
 * search bar card. The text itself is owned by the caller ([inputText] / [onInput]: it lives in
 * `LibraryLists`, so it survives a change of tab, and is sent to the phone as `query`).
 */
class Search private constructor(
    private val inputText: String,
    private val onInput: (String) -> Unit,
    visibleState: MutableState<Boolean>,
    focusState: MutableState<Boolean>,
    private val lazyListState: LazyListState?,
    private val lazyGridState: LazyGridState?,
) : MenuIcon {

    companion object {
        @Composable
        operator fun invoke(inputText: String, onInput: (String) -> Unit, lazyListState: LazyListState? = null) = Search(
            inputText,
            onInput,
            remember { mutableStateOf(inputText.isNotEmpty()) },
            remember { mutableStateOf(false) },
            lazyListState,
            null,
        )

        /** The grid variant (the phone's `Search(lazyGridState)`, its `HomeLibrary.kt` 243). */
        @Composable
        operator fun invoke(inputText: String, onInput: (String) -> Unit, lazyGridState: LazyGridState? = null) = Search(
            inputText,
            onInput,
            remember { mutableStateOf(inputText.isNotEmpty()) },
            remember { mutableStateOf(false) },
            null,
            lazyGridState,
        )
    }

    val inputValue: String
        get() = inputText
    override val iconId: DrawableResource = Res.drawable.search_circle
    override val menuIconTitle: String
        @Composable
        get() = stringResource(Res.string.search)

    var isVisible: Boolean by visibleState
    var isFocused: Boolean by focusState

    override fun onShortClick() {
        isVisible = !isVisible
        isFocused = isVisible
    }

    /** Attempt to hide search bar if it's empty. */
    fun hideIfEmpty() {
        if (isVisible) {
            if (inputValue.isBlank()) isVisible = false else isFocused = false
        }
    }

    @Composable
    fun SearchBar() {
        val focusRequester = remember { FocusRequester() }
        val focusManager = LocalFocusManager.current
        LaunchedEffect(isVisible, isFocused) {
            if (!isVisible) return@LaunchedEffect

            if (isFocused) {
                // The field may not be composed yet in the first frame of its appearance
                runCatching { focusRequester.requestFocus() }
            } else {
                focusManager.clearFocus()
            }
        }
        val isFirstRun = remember { mutableStateOf(true) }
        LaunchedEffect(inputText) {
            if (isFirstRun.value) {
                isFirstRun.value = false
                return@LaunchedEffect
            }
            lazyListState?.scrollToItem(0, 0)
            lazyGridState?.scrollToItem(0, 0)
        }
        // [TextFieldValue] keeps the cursor where the user put it
        var input by remember { mutableStateOf(TextFieldValue(inputText, TextRange(inputText.length))) }
        if (input.text != inputText) input = TextFieldValue(inputText, TextRange(inputText.length))

        AnimatedVisibility(
            visible = isVisible,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
                .fillMaxWidth(),
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(
                        elevation = 8.dp,
                        shape = uiRoundnessShape(),
                        spotColor = colorPalette().accent.copy(alpha = 0.3f),
                    ),
                shape = uiRoundnessShape(),
                colors = CardDefaults.cardColors(
                    containerColor = colorPalette().background1,
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
                BasicTextField(
                    value = input,
                    onValueChange = {
                        input = it
                        onInput(it.text)
                    },
                    textStyle = typography().xs.semiBold,
                    singleLine = true,
                    maxLines = 1,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        isVisible = inputValue.isNotBlank()
                        isFocused = false
                    }),
                    cursorBrush = SolidColor(colorPalette().text),
                    decorationBox = { innerTextField ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    color = colorPalette().background1,
                                    shape = uiRoundnessShape(),
                                )
                                .padding(horizontal = 20.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // Leading Icon
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(
                                        color = colorPalette().accent.copy(alpha = 0.1f),
                                        shape = uiRoundnessShape(),
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    painter = painterResource(Res.drawable.search),
                                    contentDescription = stringResource(Res.string.search),
                                    tint = colorPalette().accent,
                                    modifier = Modifier.size(18.dp),
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            // Text Field
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(
                                        color = colorPalette().background2,
                                        shape = uiRoundnessShape(),
                                    )
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                contentAlignment = Alignment.CenterStart,
                            ) {
                                if (inputValue.isEmpty()) {
                                    BasicText(
                                        text = stringResource(Res.string.search),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        style = typography().xs.semiBold.secondary,
                                    )
                                }

                                innerTextField()
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            // Trailing Icon (always present to maintain size)
                            Box(
                                modifier = Modifier.width(40.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (inputValue.isNotBlank()) {
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .background(
                                                color = colorPalette().accent.copy(alpha = 0.1f),
                                                shape = uiRoundnessShape(),
                                            ),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        IconButton(
                                            onClick = {
                                                onInput("")
                                                isFocused = true
                                            },
                                        ) {
                                            Icon(
                                                painter = painterResource(Res.drawable.backspace_outline),
                                                contentDescription = stringResource(Res.string.clear),
                                                tint = colorPalette().accent,
                                                modifier = Modifier.size(18.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                )
            }
        }
    }
}
