package app.n_zik.compagnon.components.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import app.n_zik.compagnon.colorPalette
import app.n_zik.compagnon.about.AboutLinks
import app.n_zik.compagnon.about.Contributors
import app.n_zik.compagnon.components.settings.ModernSettingsEntry
import app.n_zik.compagnon.components.settings.SettingsDescription
import app.n_zik.compagnon.components.settings.SettingsSectionCard
import app.n_zik.compagnon.components.themed.HeaderWithIcon
import app.n_zik.compagnon.components.theme.ModernBlackColorPalette
import app.n_zik.compagnon.components.theme.PureBlackColorPalette
import app.n_zik.compagnon.generated.AppVersion
import app.n_zik.compagnon.generated.resources.*
import app.n_zik.compagnon.typography
import app.n_zik.compagnon.uiRoundnessShape
import app.n_zik.compagnon.updater.models.ArtifactNames
import app.n_zik.compagnon.updater.models.UpdaterConstants
import app.n_zik.compagnon.updater.services.Updater
import app.n_zik.compagnon.utils.Preferences
import app.n_zik.compagnon.utils.Toaster
import app.n_zik.compagnon.utils.bold
import app.n_zik.compagnon.utils.formatText
import app.n_zik.compagnon.utils.openInBrowser
import app.n_zik.compagnon.utils.semiBold
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The About tab of the settings page (spec `spec-settings-navigation` NAV-3 — the phone's `About.kt`
 * as the last sub-tab, its `HeaderWithIcon` in tab style `enabled = false` like the phone's
 * `About.kt` 102-110): the header + description, the row of two cards (the app-info card — the
 * AD-8 per-channel product name, the version, the channel badge, "by N-Zik-Group" — and the update
 * card, which consumes the LIVE [Updater] state and opens the update sub-page), the troubleshooting
 * section (three repo links, the desktop's — [AboutLinks]) and the contributors section (the embedded
 * translators / developers, the phone's convention — no network fetch). Its content is the
 * 1:1 port of the phone's `About.kt` (spec `spec-updater` AD-10), unchanged; only the container
 * moved from the v1 overlay to the tab's own scrollable column (the phone's destination structure,
 * `About.kt` 87-94).
 *
 * The update card's "Check Update" forces the check WITHOUT the startup dialog
 * ([Updater.checkForUpdate] `showDialog = false`) and opens the update page instead (AD-9
 * revised / AD-10). On the source builds (debug / -git — `AppVersion.updaterEnabled` false) the
 * card is the phone's `!IS_AUTOUPDATE` branch: disabled variant, an explanation, no navigation.
 * The tab has no search (the phone's `About.kt` has none — NAV-12).
 */
@Composable
fun AboutScreen(
    preferences: Preferences,
    onOpenUpdate: () -> Unit,
) {
    val settings by preferences.settings.collectAsState()
    val updaterEnabled = AppVersion.updaterEnabled
    val newVersion = Updater.githubRelease?.tagName ?: ""
    val hasUpdate = Updater.githubRelease != null &&
        Updater.isVersionNewer(newVersion, AppVersion.versionName)

    // The tab's own scroll (the phone's destination structure, its `About.kt` 87-94).
    // Scroll-bounding audit (the intermittent "infinity maximum height" crash report): every
    // vertically scrollable component in this tree is bounded — this root [verticalScroll] is
    // measured with the settings page's own size (the `AnimatedContent(fillMaxSize())` inside the
    // page's `weight(1f)` box), and the only nested scrollables are the contributors'
    // [androidx.compose.foundation.lazy.LazyColumn]s, each inside its fixed 600 dp
    // [Contributors] box (a fixed-height container is safe under an unbounded offer). No
    // hardening is needed — the structure mirrors the phone's.
    Column(
        modifier = Modifier
            .background(colorPalette().background0)
            .fillMaxHeight()
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    ) {
        // Header in tab style (the phone's `About.kt` 102-110: `enabled = false`, no back)
        HeaderWithIcon(
            title = stringResource(Res.string.about),
            iconId = Res.drawable.information,
            enabled = false,
            showIcon = true,
            modifier = Modifier,
            onClick = {},
        )

        SettingsDescription(
            text = stringResource(Res.string.about_description),
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )

        // Header Cards Row - App Info & Update Check
        AnimatedVisibility(
            visible = true,
            enter = fadeIn(animationSpec = tween(600)) + scaleIn(
                animationSpec = tween(600),
                initialScale = 0.8f,
            ),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Max),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // App Info Card (the phone's accent-shadow card, its `About.kt` 140-144)
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .shadow(elevation = 8.dp, shape = uiRoundnessShape(), spotColor = colorPalette().accent.copy(alpha = 0.3f)),
                        shape = uiRoundnessShape(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (colorPalette() === PureBlackColorPalette || colorPalette() === ModernBlackColorPalette) {
                                Color(0xFF1A1A1A) // Gray dark for pitch black themes
                            } else {
                                colorPalette().background1
                            },
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxHeight()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Top,
                        ) {
                            // Top content
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                // App Icon
                                Box(
                                    modifier = Modifier
                                        .size(60.dp)
                                        .background(
                                            brush = Brush.radialGradient(
                                                colors = listOf(
                                                    colorPalette().accent.copy(alpha = 0.1f),
                                                    colorPalette().accent.copy(alpha = 0.05f),
                                                ),
                                            ),
                                            shape = CircleShape,
                                        ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Image(
                                        painter = painterResource(Res.drawable.app_icon),
                                        contentDescription = null,
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier.size(28.dp),
                                    )
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                // App Name — the AD-8 per-channel product name
                                BasicText(
                                    text = ArtifactNames.productName(AppVersion.channel),
                                    style = TextStyle(
                                        fontSize = typography().l.bold.fontSize,
                                        fontWeight = typography().l.bold.fontWeight,
                                        color = colorPalette().text,
                                        textAlign = TextAlign.Center,
                                    ),
                                    modifier = Modifier.fillMaxWidth(),
                                )

                                Spacer(modifier = Modifier.height(6.dp))

                                // Version and Badge (the phone's `basicMarquee(iterations = Int.MAX_VALUE)`
                                // on the row — a long version + badge scrolls instead of clipping)
                                Row(
                                    modifier = Modifier.fillMaxWidth().basicMarquee(iterations = Int.MAX_VALUE),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    BasicText(
                                        text = "v${AppVersion.versionName}",
                                        style = typography().xs.copy(
                                            color = colorPalette().textSecondary,
                                            textAlign = TextAlign.Center,
                                        ),
                                    )

                                    val currentSuffix = Updater.extractVersionSuffix(AppVersion.versionName)
                                    if (currentSuffix.isNotEmpty()) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Box(
                                            modifier = Modifier
                                                .background(
                                                    color = colorPalette().accent.copy(alpha = 0.2f),
                                                    shape = uiRoundnessShape(),
                                                )
                                                .padding(horizontal = 6.dp, vertical = 2.dp),
                                        ) {
                                            val buildTypeRes = when (currentSuffix) {
                                                UpdaterConstants.SUFFIX_CHAR_BETA -> Res.string.beta_title
                                                UpdaterConstants.SUFFIX_CHAR_DEV -> Res.string.dev_title
                                                UpdaterConstants.SUFFIX_CHAR_GIT -> Res.string.git_title
                                                else -> Res.string.stable_title
                                            }
                                            BasicText(
                                                text = stringResource(buildTypeRes).uppercase(),
                                                style = typography().xxs.bold.copy(color = colorPalette().accent),
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.weight(1f))

                            // Bottom content - Author
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(uiRoundnessShape())
                                        .clickable { openInBrowser(AboutLinks.REPO_OWNER_URL) },
                                ) {
                                    BasicText(
                                        text = stringResource(Res.string.by_string),
                                        style = typography().xs.copy(
                                            color = colorPalette().textSecondary,
                                            textAlign = TextAlign.Center,
                                        ),
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Icon(
                                        painter = painterResource(Res.drawable.github_icon),
                                        tint = colorPalette().accent,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    BasicText(
                                        text = "N-Zik-Group",
                                        style = typography().xs.copy(
                                            textDecoration = TextDecoration.Underline,
                                            color = colorPalette().accent,
                                            textAlign = TextAlign.Center,
                                        ),
                                    )
                                }
                            }
                        }
                    }

                    // Update Check Card (the phone's accent-shadow card, its `About.kt` 301-305):
                    // the [clip] before the clickable bounds the click ripple to the rounded
                    // card — the square-corner ripple leak of the v2 clickable-without-clip
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(uiRoundnessShape())
                            .then(if (updaterEnabled) Modifier.clickable { onOpenUpdate() } else Modifier)
                            .shadow(elevation = 8.dp, shape = uiRoundnessShape(), spotColor = colorPalette().accent.copy(alpha = 0.3f)),
                        shape = uiRoundnessShape(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (colorPalette() === PureBlackColorPalette || colorPalette() === ModernBlackColorPalette) {
                                Color(0xFF1A1A1A) // Gray dark for pitch black themes
                            } else {
                                colorPalette().background1
                            },
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxHeight()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Top,
                        ) {
                            if (updaterEnabled) {
                                // Top content
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    // Update Icon
                                    Box(
                                        modifier = Modifier
                                            .size(60.dp)
                                            .background(
                                                brush = Brush.radialGradient(
                                                    colors = listOf(
                                                        colorPalette().accent.copy(alpha = 0.1f),
                                                        colorPalette().accent.copy(alpha = 0.05f),
                                                    ),
                                                ),
                                                shape = CircleShape,
                                            ),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            painter = painterResource(Res.drawable.update),
                                            tint = colorPalette().accent,
                                            contentDescription = null,
                                            modifier = Modifier.size(28.dp),
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(12.dp))

                                    // Update Title (the phone's `l.bold`, its `About.kt` 357-358)
                                    BasicText(
                                        text = stringResource(Res.string.update),
                                        style = TextStyle(
                                            fontSize = typography().l.bold.fontSize,
                                            fontWeight = typography().l.bold.fontWeight,
                                            color = colorPalette().text,
                                            textAlign = TextAlign.Center,
                                        ),
                                        modifier = Modifier.fillMaxWidth(),
                                    )

                                    Spacer(modifier = Modifier.height(6.dp))

                                    if (Updater.githubRelease != null) {
                                        BasicText(
                                            text = stringResource(
                                                if (hasUpdate) Res.string.update_available else Res.string.up_to_date,
                                            ),
                                            style = typography().xs.copy(
                                                textAlign = TextAlign.Center,
                                                color = if (hasUpdate) colorPalette().accent else colorPalette().textSecondary,
                                            ),
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                    } else {
                                        BasicText(
                                            text = stringResource(Res.string.update_unknown),
                                            style = typography().xs.copy(
                                                textAlign = TextAlign.Center,
                                                color = colorPalette().textSecondary,
                                            ),
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                    }

                                    val lastCheckTime = settings.lastUpdateCheck
                                    val lastCheckStr =
                                        if (lastCheckTime > 0) SimpleDateFormat("dd MMM HH:mm", Locale.getDefault())
                                            .format(Date(lastCheckTime))
                                        else stringResource(Res.string.never_checked)
                                    BasicText(
                                        text = if (lastCheckTime > 0) {
                                            formatText(stringResource(Res.string.last_check), lastCheckStr)
                                        } else {
                                            stringResource(Res.string.never_checked)
                                        },
                                        style = typography().xxs.copy(
                                            textAlign = TextAlign.Center,
                                            color = colorPalette().textSecondary.copy(alpha = 0.7f),
                                        ),
                                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                    )
                                }

                                Spacer(modifier = Modifier.weight(1f))

                                // The "Check Update" action: the forced check WITHOUT the startup
                                // dialog, then the update page (AD-9 revised / AD-10)
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(36.dp)
                                        .clip(uiRoundnessShape())
                                        .clickable {
                                            Toaster.i(Res.string.checking_for_updates)
                                            Updater.checkForUpdate(isForced = true, showDialog = false)
                                            onOpenUpdate()
                                        },
                                    colors = CardDefaults.cardColors(containerColor = colorPalette().accent),
                                    shape = uiRoundnessShape(),
                                ) {
                                    // fillMaxSize (not fillMaxWidth) — the phone's About.kt uses
                                    // fillMaxSize so the label is centered VERTICALLY in the 36dp
                                    // card; fillMaxWidth left it top-aligned.
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        BasicText(
                                            text = stringResource(Res.string.check_update),
                                            style = typography().xs.semiBold.copy(color = Color.White),
                                        )
                                    }
                                }
                            } else {
                                // The source builds (debug / -git): the phone's !IS_AUTOUPDATE branch —
                                // the disabled variant, the explanation, no navigation
                                // fillMaxSize (not fillMaxWidth) per the phone's About.kt disabled
                                // branch — the card content must be vertically centered, not
                                // top-aligned (round-4 audit MINOR-2)
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(60.dp)
                                            .background(
                                                brush = Brush.radialGradient(
                                                    colors = listOf(
                                                        colorPalette().accent.copy(alpha = 0.1f),
                                                        colorPalette().accent.copy(alpha = 0.05f),
                                                    ),
                                                ),
                                                shape = CircleShape,
                                            ),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            painter = painterResource(Res.drawable.update),
                                            tint = colorPalette().textSecondary.copy(alpha = 0.5f),
                                            contentDescription = null,
                                            modifier = Modifier.size(28.dp),
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(12.dp))
                                    BasicText(
                                        text = stringResource(Res.string.update),
                                        style = TextStyle(
                                            fontSize = typography().l.bold.fontSize,
                                            fontWeight = typography().l.bold.fontWeight,
                                            color = colorPalette().textSecondary.copy(alpha = 0.5f),
                                            textAlign = TextAlign.Center,
                                        ),
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    BasicText(
                                        text = stringResource(Res.string.update_check_off_build),
                                        style = typography().xxs.copy(
                                            textAlign = TextAlign.Center,
                                            color = colorPalette().textSecondary.copy(alpha = 0.7f),
                                        ),
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Troubleshooting Section (the desktop repo's links — spec AD-10)
        AnimatedVisibility(
            visible = true,
            enter = fadeIn(animationSpec = tween(800)) + scaleIn(
                animationSpec = tween(800),
                initialScale = 0.9f,
            ),
        ) {
            SettingsSectionCard(
                title = stringResource(Res.string.troubleshooting),
                icon = Res.drawable.information,
                content = {
                    // The phone's `ModernSettingsEntry` rows (xs title / xxs description, no
                    // press scale — `OtherSettingsEntry` is s/xs with the 0.95 press scale)
                    ModernSettingsEntry(
                        title = stringResource(Res.string.view_the_source_code),
                        text = stringResource(Res.string.you_will_be_redirected_to_github),
                        icon = Res.drawable.github_icon,
                        onClick = { openInBrowser(AboutLinks.DESKTOP_REPO_URL) },
                    )

                    ModernSettingsEntry(
                        title = stringResource(Res.string.report_an_issue),
                        text = stringResource(Res.string.you_will_be_redirected_to_github),
                        icon = Res.drawable.trending,
                        onClick = { openInBrowser(AboutLinks.issueUrl("bug")) },
                    )

                    ModernSettingsEntry(
                        title = stringResource(Res.string.request_a_feature),
                        text = stringResource(Res.string.you_will_be_redirected_to_github),
                        icon = Res.drawable.star_brilliant,
                        onClick = { openInBrowser(AboutLinks.issueUrl("feature")) },
                    )
                },
            )
        }

        // Contributors Section (the embedded lists — the phone's convention, no network fetch)
        AnimatedVisibility(
            visible = true,
            enter = fadeIn(animationSpec = tween(1000)) + scaleIn(
                animationSpec = tween(1000),
                initialScale = 0.9f,
            ),
        ) {
            SettingsSectionCard(
                title = stringResource(Res.string.contributors),
                icon = Res.drawable.people,
                content = {
                    // Translators Section
                    var translatorsExpanded by remember { mutableStateOf(false) }
                    val translatorsRotation by animateFloatAsState(
                        targetValue = if (translatorsExpanded) 90f else 0f,
                        animationSpec = tween(300),
                        label = "translatorsRotation",
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(uiRoundnessShape())
                            .clickable { translatorsExpanded = !translatorsExpanded }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            painter = painterResource(Res.drawable.chevron_forward),
                            tint = colorPalette().accent,
                            contentDescription = null,
                            modifier = Modifier
                                .size(16.dp)
                                .graphicsLayer(rotationZ = translatorsRotation),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        BasicText(
                            text = "${Contributors.countTranslators()} " + stringResource(Res.string.translators),
                            style = typography().xs.semiBold.copy(color = colorPalette().textSecondary),
                        )
                    }

                    AnimatedVisibility(
                        visible = translatorsExpanded,
                        enter = fadeIn(animationSpec = tween(300)) + scaleIn(
                            animationSpec = tween(300),
                            initialScale = 0.95f,
                        ),
                        exit = fadeOut(animationSpec = tween(200)) + scaleOut(
                            animationSpec = tween(200),
                            targetScale = 0.95f,
                        ),
                    ) {
                        Column(
                            modifier = Modifier.padding(start = 24.dp, top = 8.dp),
                        ) {
                            SettingsDescription(text = stringResource(Res.string.in_alphabetical_order))
                            Contributors.ShowTranslators()
                        }
                    }

                    // Developers Section
                    var developersExpanded by remember { mutableStateOf(false) }
                    val developersRotation by animateFloatAsState(
                        targetValue = if (developersExpanded) 90f else 0f,
                        animationSpec = tween(300),
                        label = "developersRotation",
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(uiRoundnessShape())
                            .clickable { developersExpanded = !developersExpanded }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            painter = painterResource(Res.drawable.chevron_forward),
                            tint = colorPalette().accent,
                            contentDescription = null,
                            modifier = Modifier
                                .size(16.dp)
                                .graphicsLayer(rotationZ = developersRotation),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        BasicText(
                            text = "${Contributors.countDevelopers()} " + stringResource(Res.string.about_developers),
                            style = typography().xs.semiBold.copy(color = colorPalette().textSecondary),
                        )
                    }

                    AnimatedVisibility(
                        visible = developersExpanded,
                        enter = fadeIn(animationSpec = tween(300)) + scaleIn(
                            animationSpec = tween(300),
                            initialScale = 0.95f,
                        ),
                        exit = fadeOut(animationSpec = tween(200)) + scaleOut(
                            animationSpec = tween(200),
                            targetScale = 0.95f,
                        ),
                    ) {
                        Column(
                            modifier = Modifier.padding(start = 24.dp, top = 8.dp),
                        ) {
                            SettingsDescription(text = stringResource(Res.string.in_alphabetical_order))
                            Contributors.ShowDevelopers()
                        }
                    }
                },
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}
