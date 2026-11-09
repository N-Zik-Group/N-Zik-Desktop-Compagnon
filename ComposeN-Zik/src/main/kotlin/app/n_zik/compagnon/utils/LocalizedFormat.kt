package app.n_zik.compagnon.utils

import androidx.compose.runtime.Composable
import java.util.Locale
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** Whether to use a new format in place of its previously localized fragments. */
@Composable
internal fun hasLocalizedFormat(availability: StringResource): Boolean =
    Locale.getDefault().language == "en" || stringResource(availability) == "1"
