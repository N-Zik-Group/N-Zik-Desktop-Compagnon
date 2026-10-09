package app.n_zik.compagnon.components.ui.header

import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.n_zik.compagnon.colorPalette
import app.n_zik.compagnon.generated.AppVersion
import app.n_zik.compagnon.generated.resources.*
import app.n_zik.compagnon.typography
import app.n_zik.compagnon.uiRoundnessShape
import app.n_zik.compagnon.utils.LocalPreferences
import app.n_zik.compagnon.utils.UserSettings
import app.n_zik.compagnon.utils.bold
import kotlinx.coroutines.flow.MutableStateFlow
import org.jetbrains.compose.resources.stringResource

/**
 * Reference label for the header channel badge: the rendered width of this title at the badge
 * style caps the badge's width (spec `spec-updater` AD-6, port of the phone's
 * `HEADER_BADGE_REF_TITLE` in `HeaderVersionBadge.kt` L38) — no badge may be wider than the
 * "DEBUG" badge; a longer label scrolls in a finite marquee instead.
 */
internal const val HEADER_BADGE_REF_TITLE = "DEBUG"

private const val HEADER_BADGE_H_PADDING = 4
private const val HEADER_BADGE_V_PADDING = 1

/**
 * Finite marquee for the header badge: a few full passes, then the scroll stops and the label
 * settles back at its start position — the badge must not spin forever in the action bar (port
 * of the phone's `HEADER_BADGE_MARQUEE_ITERATIONS`, the platform default: 3 iterations).
 */
private const val HEADER_BADGE_MARQUEE_ITERATIONS = 3

/**
 * The badge style: 14sp bold (port of the phone's `headerBadgeStyle`, the "Rescue Center" burger
 * item size — the maximum text size for header badges).
 */
@Composable
private fun headerBadgeStyle(): TextStyle = typography().xs.bold

/**
 * Rendered width of [reference] at [style] — a hard width cap for the label: any wider label is
 * clamped to it and scrolls in a marquee. The header badge uses the "DEBUG" badge title as
 * reference (port of the phone's `clampedLabelMaxWidth`); measuring at the same style keeps the
 * cap exact for whatever font is loaded.
 */
@Composable
internal fun clampedLabelMaxWidth(reference: String, style: TextStyle): Dp {
    val textMeasurer = rememberTextMeasurer()
    return with(LocalDensity.current) {
        textMeasurer.measure(reference, style = style).size.width.toDp()
    }
}

/**
 * The channel badge of the app header (spec `spec-updater` AD-6: "Beta" / "Dev" / "Git" /
 * "Debug", none for stable).
 *
 * Same container and width cap as the phone's version badge (`HeaderBadgeBox`): a label longer
 * than "DEBUG" scrolls in a finite marquee (honoring the global "disable scrolling text"
 * setting, which falls back to ellipsis) instead of widening the header.
 *
 * Dropped: the phone's interactive debug-badge tap (the desktop badge is non-interactive) and
 * its 32-bit / FOSS suffixes (desktop channels have none).
 */
@Composable
fun HeaderChannelBadge(
    modifier: Modifier = Modifier,
) {
    // No badge for the stable channel (the phone's `versionSuffix.isEmpty()` early return)
    if (AppVersion.channel == "stable") return

    val badgeText = when (AppVersion.channel) {
        "beta" -> stringResource(Res.string.beta_title)
        "dev" -> stringResource(Res.string.dev_title)
        "git" -> stringResource(Res.string.git_title)
        else -> stringResource(Res.string.debug_title)
    }

    val preferences = LocalPreferences.current
    val settings by (preferences?.settings ?: remember { MutableStateFlow(UserSettings()) }).collectAsState()

    val style = headerBadgeStyle().copy(color = colorPalette().accent)
    val maxWidth = clampedLabelMaxWidth(HEADER_BADGE_REF_TITLE, style)

    Box(
        modifier = modifier
            .background(
                color = colorPalette().accent.copy(alpha = 0.2f),
                shape = uiRoundnessShape(),
            )
            .clip(uiRoundnessShape())
            .padding(
                horizontal = HEADER_BADGE_H_PADDING.dp,
                vertical = HEADER_BADGE_V_PADDING.dp,
            ),
    ) {
        // Uppercase per the phone's build-type badges ("BETA" / "DEV" / "MINIFIED" — the phone's
        // build-type strings are uppercase; the desktop's *_title strings are uppercase in
        // values/strings.xml, but the values-* locale copies stay mixed-case until the next
        // Crowdin resync, so the case is applied at render — round-4 audit MINOR-1, consistent
        // with the About card badge and the update flow)
        BasicText(
            text = badgeText.uppercase(),
            style = style,
            overflow = TextOverflow.Ellipsis,
            maxLines = 1,
            modifier = Modifier
                .widthIn(max = maxWidth)
                // Finite marquee: scrolls a few full passes, then stops on the start position
                // (BasicMarquee snaps the offset back to zero when done) — dropped when the
                // global "Disable scrolling text" setting is on (the phone's
                // `disableScrollingTextKey`, its `HeaderBadgeBox` L90, L119)
                .then(if (settings.disableScrollingText) Modifier else Modifier.basicMarquee(iterations = HEADER_BADGE_MARQUEE_ITERATIONS)),
        )
    }
}
