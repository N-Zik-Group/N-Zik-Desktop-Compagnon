package app.n_zik.compagnon.updater.ui

import androidx.compose.runtime.Composable
import app.n_zik.compagnon.generated.AppVersion
import app.n_zik.compagnon.generated.resources.*
import app.n_zik.compagnon.utils.hasLocalizedFormat
import java.util.Locale
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** The localized, uppercase build-channel label, or an empty string for an unknown channel. */
@Composable
fun channelLabel(channel: String = AppVersion.channel): String {
    val res = channelTitleRes(channel) ?: return ""
    return channelLabelOf(stringResource(res))
}

/** The localized update title shared by the settings screen and the update dialog. */
@Composable
internal fun updateAvailableTitle(channel: String = AppVersion.channel): String {
    val label = channelLabel(channel)
    return when {
        label.isEmpty() -> stringResource(Res.string.updates_available)
        hasLocalizedFormat(Res.string.ui_translation_available_updates_channel_available_format) ->
            stringResource(Res.string.updates_channel_available_format, label)
        else -> "$label ${stringResource(Res.string.updates_available)}"
    }
}

/** Uppercases channel labels consistently across locales, including GIT under a Turkish locale. */
internal fun channelLabelOf(title: String): String = title.trim().uppercase(Locale.ROOT)

/**
 * The channel → `*_title` string mapping of [channelLabel] (pure, non-composable — pinned by
 * `ChannelLabelTest`, matrix rows LABEL_SCREEN / LABEL_DIALOG of spec `spec-updater-parity-fixes`):
 * the 5-branch superset (beta / stable / dev / git / debug), `null` for an unknown channel
 * (the page renders no prefix, the dialog never renders one for a non-updater channel).
 */
internal fun channelTitleRes(channel: String): StringResource? = when (channel) {
    "beta" -> Res.string.updates_build_beta_label
    "stable" -> Res.string.stable_title
    "dev" -> Res.string.updates_build_dev_label
    "git" -> Res.string.git_title
    "debug" -> Res.string.updates_build_debug_label
    else -> null
}
