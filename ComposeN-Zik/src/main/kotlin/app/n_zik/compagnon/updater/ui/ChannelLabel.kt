package app.n_zik.compagnon.updater.ui

import androidx.compose.runtime.Composable
import app.n_zik.compagnon.generated.AppVersion
import app.n_zik.compagnon.generated.resources.*
import java.util.Locale
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The channel label prefix of the update flow (spec `spec-updater-parity-fixes` — the parity
 * audit centralization): the uppercase channel label + a trailing space, rendered from the
 * SINGLE shared source both the update page and the update dialog call (their two private
 * copies had drifted: the dialog's lacked the `debug` branch — behaviorally unreachable, the
 * dialog renders only under [AppVersion.updaterEnabled], false for debug / -git, so the
 * 5-branch superset changes nothing visible).
 *
 * The `*_title` strings are uppercase in values/strings.xml, but the values-* locale copies
 * stay mixed-case until the next Crowdin resync — the .uppercase() is plain
 * rendering/normalization, not a safeguard (round-4 audit MINOR-1).
 *
 * @param channel the channel to label (this build's own channel by default — the page passes
 * its `AppVersion.channel` read, the dialog uses the default)
 */
@Composable
fun channelLabel(channel: String = AppVersion.channel): String {
    val res = channelTitleRes(channel) ?: return ""
    return channelLabelOf(stringResource(res))
}

/**
 * The title-string → label transformation of [channelLabel] (pure, pinned by `ChannelLabelTest`):
 * uppercase + the trailing space. `Locale.ROOT` — a default-locale `uppercase()` would render
 * the git channel prefix as "GİT" under a Turkish JVM default locale (the dotted capital I).
 */
internal fun channelLabelOf(title: String): String = title.uppercase(Locale.ROOT) + " "

/**
 * The channel → `*_title` string mapping of [channelLabel] (pure, non-composable — pinned by
 * `ChannelLabelTest`, matrix rows LABEL_SCREEN / LABEL_DIALOG of spec `spec-updater-parity-fixes`):
 * the 5-branch superset (beta / stable / dev / git / debug), `null` for an unknown channel
 * (the page renders no prefix, the dialog never renders one for a non-updater channel).
 */
internal fun channelTitleRes(channel: String): StringResource? = when (channel) {
    "beta" -> Res.string.beta_title
    "stable" -> Res.string.stable_title
    "dev" -> Res.string.dev_title
    "git" -> Res.string.git_title
    "debug" -> Res.string.debug_title
    else -> null
}
