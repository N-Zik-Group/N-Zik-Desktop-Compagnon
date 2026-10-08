package app.n_zik.compagnon.components.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
// 1.13.0-alpha01: `BorderStroke` moved from `androidx.compose.ui.graphics` to `androidx.compose.foundation`
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.n_zik.compagnon.colorPalette
import app.n_zik.compagnon.components.themed.DropdownMenu
import app.n_zik.compagnon.components.themed.HeaderWithIcon
import app.n_zik.compagnon.components.themed.ValueSelectorDialog
import app.n_zik.compagnon.generated.AppVersion
import app.n_zik.compagnon.generated.resources.*
// `update_download_progress` stays in strings.xml for the startup dialog; the page shows the
// raw percentage instead (the phone's two-line progression)
import app.n_zik.compagnon.components.theme.ModernBlackColorPalette
import app.n_zik.compagnon.components.theme.PureBlackColorPalette
import app.n_zik.compagnon.typography
import app.n_zik.compagnon.uiRoundnessShape
import app.n_zik.compagnon.updater.models.ArtifactNames
import app.n_zik.compagnon.updater.models.CheckUpdateState
import app.n_zik.compagnon.updater.models.GithubRelease
import app.n_zik.compagnon.updater.models.InstallMode
import app.n_zik.compagnon.updater.models.PackageManager
import app.n_zik.compagnon.updater.models.UpdaterConstants
import app.n_zik.compagnon.updater.models.currentDistributionMarker
import app.n_zik.compagnon.updater.models.currentInstallMode
import app.n_zik.compagnon.updater.models.livePackageManager
import app.n_zik.compagnon.updater.services.UpdateDownloadManager
import app.n_zik.compagnon.updater.services.Updater
import app.n_zik.compagnon.updater.services.updaterHttpClient
import app.n_zik.compagnon.updater.services.ChangelogTranslator
import app.n_zik.compagnon.updater.ui.ChangelogCard
import app.n_zik.compagnon.updater.ui.InstallStep
import app.n_zik.compagnon.updater.ui.UpdateLanguage
import app.n_zik.compagnon.updater.ui.copyToClipboard
import app.n_zik.compagnon.updater.ui.installCommand
import app.n_zik.compagnon.updater.ui.openFileFolder
import app.n_zik.compagnon.utils.Toaster
import app.n_zik.compagnon.utils.Preferences
import app.n_zik.compagnon.utils.bold
import app.n_zik.compagnon.utils.coroutines.NzikDispatchers
import app.n_zik.compagnon.utils.formatShortFileSize
import app.n_zik.compagnon.utils.formatText
import app.n_zik.compagnon.utils.openInBrowser
import app.n_zik.compagnon.utils.semiBold
import dev.rebelonion.translator.Language
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * The update page (spec `spec-settings-navigation` NAV-4 + `spec-updater` AD-9 revised — a 1:1
 * port of the phone's `updater/ui/UpdateScreen.kt`, adapted to the desktop conventions — the
 * navigation sub-page of the settings page (the settings page stays open underneath, back lands
 * on its About tab), the back-enabled [HeaderWithIcon] (the window header's back arrow too —
 * both close the page, [onClose]), the themed [DropdownMenu] /
 * [ValueSelectorDialog], the per-channel [AppVersion.channel] instead of the phone's runtime
 * beta toggle, the shared [ChangelogCard] / [InstallStep]):
 *
 *  * the top card — update available / up to date / no release yet, the ⋮ menu (the
 *    three-state check choice, the changelog translation language, the re-download, the cache
 *    clear);
 *  * the "What's new" card — the changelog (release / current, cached) with the phone's
 *    section cards and the translate toggle (the sanctioned `translator` dependency through
 *    [ChangelogTranslator]);
 *  * the bottom action — the phone's bottomBar: the download progression (the wavy progress,
 *    the indeterminate byte counter) and the per-mode install gesture ([InstallStep], the
 *    Windows interactive install), the check-for-update action and the GitHub release link.
 *
 * The language choice + the translation toggle are persisted in `settings.json` (the phone's
 * `rememberPreference` keys — the loop-2 closure replaced the v1 in-memory `remember`): the
 * toggle's default is the phone's — active when the app language is not English.
 */
@Composable
fun UpdateScreen(
    preferences: Preferences,
    onClose: () -> Unit,
) {
    val settings by preferences.settings.collectAsState()
    val downloadState by UpdateDownloadManager.downloadState.collectAsState()

    // The translation language + the toggle: persisted in `settings.json` (the phone's
    // `rememberPreference` port, spec AD-9 — the loop-2 closure replaced the v1 in-memory
    // `remember`); an unknown persisted code reads as `System`, and the toggle's never-set
    // default is the phone's: active when the app language is not English
    val appLang = Locale.getDefault().language
    var otherLanguageApp by remember {
        mutableStateOf(UpdateLanguage.entries.firstOrNull { it.code == settings.otherLanguageAppUpdate } ?: UpdateLanguage.System)
    }
    var isTranslationActive by remember { mutableStateOf(settings.updateTranslationActive ?: appLang != "en") }
    var showLanguageDialog by remember { mutableStateOf(false) }
    // "System" resolves to the OS default language (the phone's rule, L127-131: the first
    // entry matching `Locale.getDefault().language`, falling back to English)
    val activeTranslateLang = remember(otherLanguageApp, appLang) {
        if (otherLanguageApp != UpdateLanguage.System) otherLanguageApp
        else UpdateLanguage.entries.firstOrNull { it.code == appLang } ?: UpdateLanguage.English
    }

    // The install gesture of this instance (detected once — the mode does not change at runtime)
    val installMode = remember { currentInstallMode() }
    // The package-manager probe (memoized — the blocking `--version` scan runs once per
    // process) — off the composition thread, `null` until it resolves (the shared [InstallStep]
    // waits on it)
    var packageManager by remember { mutableStateOf<PackageManager?>(null) }
    LaunchedEffect(installMode) {
        if (installMode == InstallMode.PACKAGE_MANAGED) {
            packageManager = withContext(NzikDispatchers.DATA) { livePackageManager() }
        }
    }

    val currentVersion = AppVersion.versionName
    // The display version of the pending update (the phone's `Updater.getDisplayVersion()` —
    // keeps the tag's "v" and appends the channel suffix when the tag lacks it)
    val newVersion = Updater.getDisplayVersion()
    val hasUpdate = Updater.githubRelease != null &&
        Updater.isVersionNewer(Updater.githubRelease?.tagName ?: "", currentVersion)
    val channel = AppVersion.channel
    val currentSuffix = Updater.extractVersionSuffix(currentVersion)
    val fileSize = try { Updater.build?.readableSize.orEmpty() } catch (_: Exception) { "" }
    // downloadingVersion carries the display version's "v" tag prefix (what startDownload was
    // passed); currentVersion does not — strip it, or the comparison never matches
    val isReinstalling = downloadState !is UpdateDownloadManager.DownloadState.Idle &&
        UpdateDownloadManager.downloadingVersion?.removePrefix(UpdaterConstants.PREFIX_VERSION) == currentVersion

    // The current changelog (the phone's trigger, L762-770): the cached changelog first, then
    // the current one over the network — only when NOT showing the new update, and re-run when
    // the reinstall/update flags change (the "What's new" card stays alive even with no update)
    LaunchedEffect(isReinstalling, hasUpdate) {
        if (isReinstalling || !hasUpdate) {
            Updater.loadCachedChangelog()
            Updater.fetchCurrentChangelog()
        }
    }

    // Cleanup state on exit — but NOT if the download is active or completed (the phone's
    // DisposableEffect)
    DisposableEffect(Unit) {
        onDispose {
            val currentState = UpdateDownloadManager.downloadState.value
            if (currentState is UpdateDownloadManager.DownloadState.Failed) {
                UpdateDownloadManager.resetState()
            }
        }
    }

    // The "BETA/DEV/…" title prefix: the uppercase channel label + a space — stable is labeled
    // too (the phone shows "STABLE" on the stable channel as well; the phone's strings are
    // uppercase in the update flow — `beta_title`/`dev_title`/`git_title`/`debug_title` stay
    // mixed-case here because the header channel badge uses them as-is)
    val channelLabel = when (channel) {
        "beta" -> stringResource(Res.string.beta_title).uppercase() + " "
        "stable" -> stringResource(Res.string.stable_title).uppercase() + " "
        "dev" -> stringResource(Res.string.dev_title).uppercase() + " "
        "git" -> stringResource(Res.string.git_title).uppercase() + " "
        "debug" -> stringResource(Res.string.debug_title).uppercase() + " "
        else -> ""
    }

    // The page structure (the phone's `updater/ui/UpdateScreen.kt` Scaffold: the fixed header, the
    // scrollable content, the bottom action riding the end of the scroll)
    Column(
        modifier = Modifier
            .background(colorPalette().background0)
            .fillMaxSize(),
    ) {
        // Header (back-enabled: its arrow closes the page, the settings page stays open on About)
        HeaderWithIcon(
            title = stringResource(Res.string.update),
            iconId = Res.drawable.update,
            enabled = true,
            showIcon = true,
            modifier = Modifier,
            onClick = onClose,
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            // Top Card (Update available / Up to date / no release yet)
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
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (colorPalette() === PureBlackColorPalette || colorPalette() === ModernBlackColorPalette) {
                                Color(0xFF1A1A1A) // Gray dark for pitch black themes
                            } else {
                                colorPalette().background1
                            },
                        ),
                        shape = uiRoundnessShape(),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    ) {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            // Settings Menu positioned at TopEnd (the phone's ⋮)
                            Box(modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                                var showMenu by remember { mutableStateOf(false) }
                                Box(
                                    modifier = Modifier
                                        .clip(uiRoundnessShape())
                                        .clickable { showMenu = true }
                                        .padding(12.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        painter = painterResource(Res.drawable.ellipsis_vertical),
                                        contentDescription = stringResource(Res.string.menu),
                                        tint = colorPalette().text,
                                        modifier = Modifier.size(24.dp),
                                    )
                                }

                                val menu = DropdownMenu(
                                    expanded = showMenu,
                                    containerColor = colorPalette().background0.copy(alpha = 0.9f),
                                    onDismissRequest = { showMenu = false },
                                )

                                val checkState = settings.checkUpdateStateValue
                                val stateStr = when (checkState) {
                                    CheckUpdateState.On -> stringResource(Res.string.on)
                                    CheckUpdateState.Ask -> stringResource(Res.string.ask)
                                    CheckUpdateState.Off -> stringResource(Res.string.off)
                                }
                                menu.add(
                                    DropdownMenu.Item(
                                        icon = Res.drawable.update,
                                        customText = "${stringResource(Res.string.enable_check_for_update)}: $stateStr",
                                    ) {
                                        preferences.update { s ->
                                            s.copy(
                                                checkUpdateState =
                                                    when (s.checkUpdateStateValue) {
                                                        CheckUpdateState.On -> CheckUpdateState.Ask
                                                        CheckUpdateState.Ask -> CheckUpdateState.Off
                                                        CheckUpdateState.Off -> CheckUpdateState.On
                                                    }.wire,
                                            )
                                        }
                                    }
                                )

                                menu.add(
                                    DropdownMenu.Item(
                                        icon = Res.drawable.translate,
                                        customText = "${stringResource(Res.string.info_translation)}: \n${otherLanguageApp.label}",
                                    ) {
                                        showMenu = false
                                        showLanguageDialog = true
                                    }
                                )

                                menu.add(
                                    DropdownMenu.Item(
                                        icon = Res.drawable.download,
                                        customText = "${stringResource(Res.string.redownload_update)} ($currentVersion)",
                                    ) {
                                        showMenu = false
                                        // The package-manager probe (when still pending) is a blocking
                                        // `--version` scan — the re-download runs off the composition
                                        // thread
                                        NzikDispatchers.fireAndForget(NzikDispatchers.DATA).launch {
                                            redownloadCurrentVersion(installMode, packageManager)
                                        }
                                    }
                                )

                                menu.add(
                                    DropdownMenu.Item(
                                        icon = Res.drawable.trash,
                                        customText = stringResource(Res.string.update_cache_cleared),
                                    ) {
                                        showMenu = false
                                        NzikDispatchers.fireAndForget(NzikDispatchers.DATA).launch {
                                            UpdateDownloadManager.clearUpdateCache()
                                            Toaster.s(Res.string.update_cache_cleared)
                                        }
                                    }
                                )
                                menu.Draw()
                            }

                            if (showLanguageDialog) {
                                ValueSelectorDialog(
                                    title = stringResource(Res.string.info_translation),
                                    selectedValue = otherLanguageApp,
                                    onValueSelected = {
                                        otherLanguageApp = it
                                        isTranslationActive = it.translator != Language.ENGLISH
                                        showLanguageDialog = false
                                        // Persist both (the phone's `rememberPreference` writes on each change)
                                        preferences.update { s ->
                                            s.copy(
                                                otherLanguageAppUpdate = it.code,
                                                updateTranslationActive = it.translator != Language.ENGLISH,
                                            )
                                        }
                                    },
                                    valueText = { it.label },
                                    values = UpdateLanguage.entries.toList(),
                                    onDismiss = { showLanguageDialog = false },
                                )
                            }

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 40.dp, bottom = 32.dp, start = 16.dp, end = 16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                if (hasUpdate || isReinstalling) {
                                    Box(
                                        modifier = Modifier
                                            .size(64.dp)
                                            .clip(uiRoundnessShape())
                                            .background(colorPalette().accent.copy(alpha = 0.1f)),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            painter = painterResource(
                                                if (isReinstalling) Res.drawable.download else Res.drawable.arrow_up,
                                            ),
                                            contentDescription = null,
                                            tint = colorPalette().accent,
                                            modifier = Modifier.size(32.dp),
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(16.dp))
                                    BasicText(
                                        text = if (isReinstalling) {
                                            stringResource(Res.string.reinstalling_update)
                                        } else {
                                            "$channelLabel${stringResource(Res.string.update_available)}"
                                        },
                                        modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE),
                                        maxLines = 1,
                                        style = typography().l.bold.copy(color = colorPalette().text, textAlign = TextAlign.Center),
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    BasicText(
                                        text = if (isReinstalling) currentVersion else newVersion,
                                        modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE),
                                        maxLines = 1,
                                        style = typography().s.copy(color = colorPalette().textSecondary, textAlign = TextAlign.Center),
                                    )
                                    if (fileSize.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(2.dp))
                                        BasicText(
                                            text = formatText(stringResource(Res.string.update_file_size), fileSize),
                                            style = typography().xs.copy(color = colorPalette().textSecondary),
                                        )
                                    }
                                } else if (Updater.githubRelease != null) {
                                    Box(
                                        modifier = Modifier
                                            .size(64.dp)
                                            .clip(uiRoundnessShape())
                                            .background(colorPalette().accent.copy(alpha = 0.1f)),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            painter = painterResource(Res.drawable.checkmark),
                                            contentDescription = null,
                                            tint = colorPalette().accent,
                                            modifier = Modifier.size(32.dp),
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(16.dp))
                                    BasicText(
                                        text = stringResource(Res.string.up_to_date),
                                        modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE),
                                        maxLines = 1,
                                        style = typography().l.bold.copy(color = colorPalette().text, textAlign = TextAlign.Center),
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    BasicText(
                                        text = "${UpdaterConstants.PREFIX_VERSION}$currentVersion",
                                        modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE),
                                        maxLines = 1,
                                        style = typography().s.copy(color = colorPalette().textSecondary, textAlign = TextAlign.Center),
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(64.dp)
                                            .clip(uiRoundnessShape())
                                            .background(colorPalette().accent.copy(alpha = 0.1f)),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            painter = painterResource(Res.drawable.information),
                                            contentDescription = null,
                                            tint = colorPalette().accent,
                                            modifier = Modifier.size(32.dp),
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(16.dp))
                                    BasicText(
                                        text = ArtifactNames.productName(channel),
                                        modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE),
                                        maxLines = 1,
                                        style = typography().l.bold.copy(color = colorPalette().text, textAlign = TextAlign.Center),
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    val stateStr = when (settings.checkUpdateStateValue) {
                                        CheckUpdateState.On -> stringResource(Res.string.auto_update_enabled)
                                        CheckUpdateState.Ask -> stringResource(Res.string.auto_update_ask)
                                        CheckUpdateState.Off -> stringResource(Res.string.auto_update_disabled)
                                    }
                                    BasicText(
                                        text = "${UpdaterConstants.PREFIX_VERSION}$currentVersion • $stateStr",
                                        modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE),
                                        maxLines = 1,
                                        style = typography().s.copy(color = colorPalette().textSecondary, textAlign = TextAlign.Center),
                                    )
                                }
                                val lastCheckTime = settings.lastUpdateCheck
                                val lastCheckStr = if (lastCheckTime > 0) {
                                    SimpleDateFormat("dd MMM HH:mm", Locale.getDefault())
                                        .format(Date(lastCheckTime))
                                } else {
                                    stringResource(Res.string.never_checked)
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                BasicText(
                                    text = if (lastCheckTime > 0) {
                                        formatText(stringResource(Res.string.last_check), lastCheckStr)
                                    } else {
                                        stringResource(Res.string.never_checked)
                                    },
                                    style = typography().xxs.copy(color = colorPalette().textSecondary.copy(alpha = 0.7f)),
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Changelog Section — the release's changelog when an update is out, else the
            // current version's (cached, then fetched — the phone's logic)
            // The phone's fallback chain (L775-782): the release's changelog, then its raw body,
            // then the current version's — the card never goes blank when the release changelog
            // was not fetched. (The phone's last-resort bundled `R.raw.release_notes` has no
            // desktop counterpart: no raw release-notes resource exists in this repo.)
            val changelogTextToDisplay = if (isReinstalling || !hasUpdate) {
                Updater.currentChangelog?.takeIf { it.isNotBlank() }
            } else {
                Updater.latestChangelog?.takeIf { it.isNotBlank() }
                    ?: Updater.githubRelease?.body?.takeIf { it.isNotBlank() }
                    ?: Updater.currentChangelog?.takeIf { it.isNotBlank() }
            }

            var translatedText by remember { mutableStateOf<String?>(null) }

            LaunchedEffect(changelogTextToDisplay, isTranslationActive, activeTranslateLang) {
                if (isTranslationActive && changelogTextToDisplay.isNullOrBlank().not()) {
                    val destLanguage = activeTranslateLang.translator
                    if (destLanguage != Language.ENGLISH) {
                        translatedText = ChangelogTranslator.translate(
                            changelogTextToDisplay,
                            destLanguage,
                            Language.ENGLISH,
                        )
                    } else {
                        translatedText = changelogTextToDisplay
                    }
                }
            }

            if (!changelogTextToDisplay.isNullOrBlank() || Updater.isFetchingChangelog) {
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(animationSpec = tween(800)) + scaleIn(
                        animationSpec = tween(800),
                        initialScale = 0.9f,
                    ),
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (colorPalette() === PureBlackColorPalette || colorPalette() === ModernBlackColorPalette) {
                                Color(0xFF1A1A1A) // Gray dark for pitch black themes
                            } else {
                                colorPalette().background1
                            },
                        ),
                        shape = uiRoundnessShape(),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(bottom = 16.dp),
                            ) {
                                Icon(
                                    painter = painterResource(Res.drawable.sparkles),
                                    contentDescription = null,
                                    tint = colorPalette().accent,
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                BasicText(
                                    text = formatText(
                                        stringResource(Res.string.whats_new_in),
                                        if (hasUpdate && !isReinstalling) newVersion else
                                            "${UpdaterConstants.PREFIX_VERSION}$currentVersion",
                                    ),
                                    modifier = Modifier.weight(1f).padding(end = 8.dp).basicMarquee(iterations = Int.MAX_VALUE),
                                    maxLines = 1,
                                    style = typography().m.bold.copy(color = colorPalette().text),
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(uiRoundnessShape())
                                        .combinedClickable(
                                            onClick = {
                                                val active = !isTranslationActive
                                                isTranslationActive = active
                                                // Persist (the phone's `rememberPreference` writes on each change)
                                                preferences.update { s -> s.copy(updateTranslationActive = active) }
                                            },
                                            onLongClick = { showLanguageDialog = true },
                                        )
                                        .padding(6.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        painter = painterResource(Res.drawable.translate),
                                        contentDescription = stringResource(Res.string.translate),
                                        tint = if (isTranslationActive) colorPalette().accent else colorPalette().textSecondary,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                            if (Updater.isFetchingChangelog) {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CircularWavyProgressIndicator(
                                        modifier = Modifier.size(32.dp),
                                        color = colorPalette().accent,
                                    )
                                }
                            } else {
                                ChangelogCard(
                                    rawText = changelogTextToDisplay.orEmpty(),
                                    translatedText = if (isTranslationActive) translatedText else null,
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // The bottom action (the phone's bottomBar, adapted into the scroll): the download
            // / progression / install gesture, the check-for-update, the GitHub release link
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colorPalette().background0.copy(alpha = 0.95f))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                when (val state = downloadState) {
                    is UpdateDownloadManager.DownloadState.Starting,
                    is UpdateDownloadManager.DownloadState.Downloading,
                    is UpdateDownloadManager.DownloadState.DownloadingIndeterminate,
                    is UpdateDownloadManager.DownloadState.Completed -> {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = colorPalette().background1),
                            shape = uiRoundnessShape(),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                            ) {
                                when (state) {
                                    is UpdateDownloadManager.DownloadState.Downloading -> {
                                        val downloadStr = if (hasUpdate) {
                                            stringResource(Res.string.downloading_update)
                                        } else {
                                            val extraInfo =
                                                if (fileSize.isNotEmpty()) "$channelLabel.trim() - $fileSize" else channelLabel.trim()
                                            formatText(
                                                stringResource(Res.string.downloading_actual_version),
                                                currentVersion,
                                                extraInfo,
                                            )
                                        }
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Box(modifier = Modifier.weight(1f)) {
                                                BasicText(
                                                    text = downloadStr,
                                                    modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE),
                                                    maxLines = 1,
                                                    style = typography().s.bold.copy(color = colorPalette().text),
                                                )
                                            }
                                            BasicText(
                                                text = "${(state.progress * 100).toInt()}%",
                                                modifier = Modifier.padding(start = 8.dp).width(40.dp),
                                                style = typography().s.copy(
                                                    color = colorPalette().accent,
                                                    textAlign = TextAlign.End,
                                                ),
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(12.dp))
                                        // The phone's LinearWavyProgressIndicator (the same
                                        // material3 wavy widget — spec AD-9, the ported component)
                                        LinearWavyProgressIndicator(
                                            progress = { state.progress },
                                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(uiRoundnessShape()),
                                            color = colorPalette().accent,
                                            trackColor = colorPalette().background2,
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.End,
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(uiRoundnessShape())
                                                    .clickable {
                                                        Toaster.i(Res.string.download_cancelled)
                                                        UpdateDownloadManager.cancelDownload()
                                                    }
                                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                            ) {
                                                BasicText(
                                                    text = stringResource(Res.string.cancel),
                                                    modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE),
                                                    maxLines = 1,
                                                    style = typography().s.semiBold.copy(color = colorPalette().red),
                                                )
                                            }
                                        }
                                    }

                                    is UpdateDownloadManager.DownloadState.DownloadingIndeterminate -> {
                                        // No Content-Length — the byte counter (loop 2)
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Box(modifier = Modifier.weight(1f)) {
                                                BasicText(
                                                    text = formatText(
                                                        stringResource(Res.string.update_download_indeterminate),
                                                        formatShortFileSize(state.bytesRead),
                                                    ),
                                                    modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE),
                                                    maxLines = 1,
                                                    style = typography().s.bold.copy(color = colorPalette().text),
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.End,
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(uiRoundnessShape())
                                                    .clickable {
                                                        Toaster.i(Res.string.download_cancelled)
                                                        UpdateDownloadManager.cancelDownload()
                                                    }
                                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                            ) {
                                                BasicText(
                                                    text = stringResource(Res.string.cancel),
                                                    modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE),
                                                    maxLines = 1,
                                                    style = typography().s.semiBold.copy(color = colorPalette().red),
                                                )
                                            }
                                        }
                                    }

                                    is UpdateDownloadManager.DownloadState.Starting -> {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Box(modifier = Modifier.weight(1f)) {
                                                BasicText(
                                                    text = stringResource(Res.string.starting),
                                                    modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE),
                                                    maxLines = 1,
                                                    style = typography().s.bold.copy(color = colorPalette().text),
                                                )
                                            }
                                            CircularWavyProgressIndicator(
                                                modifier = Modifier.padding(start = 8.dp).size(20.dp),
                                                color = colorPalette().accent,
                                            )
                                        }
                                    }

                                    is UpdateDownloadManager.DownloadState.Completed -> {
                                        // Hoisted into composable scope: the click lambda is not composable,
                                        // so `stringResource` cannot be called inside it (the dialog's
                                        // same fix)
                                        val windowsHelperError =
                                            stringResource(Res.string.error_windows_install_helper)
                                        // The exact command of this mode (`null` = the manual modes —
                                        // the accent button keeps the "Open folder" fallback instead
                                        // of a misleading "Install")
                                        val command = installCommand(installMode, packageManager, state.filePath)
                                        // The per-mode install gesture (the shared card + the
                                        // phone's accent "Install" action)
                                        InstallStep(
                                            filePath = state.filePath,
                                            installMode = installMode,
                                            packageManager = packageManager,
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Card(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(uiRoundnessShape())
                                                .clickable {
                                                    when {
                                                        installMode == InstallMode.WINDOWS -> {
                                                            if (UpdateDownloadManager.startWindowsInstall(state.filePath)) {
                                                                // Quit so the installer can replace the running
                                                                // files (the dialog's JVM-exit rationale)
                                                                System.exit(0)
                                                            } else {
                                                                Toaster.e(windowsHelperError)
                                                            }
                                                        }
                                                        // The command-managed modes (flatpak / apt /
                                                        // dnf / pacman): copy the exact command — the
                                                        // user runs it in their terminal (never
                                                        // auto-run, privilege)
                                                        command != null -> copyToClipboard(
                                                            command,
                                                            Res.string.command_copied,
                                                        )
                                                        else -> openFileFolder(state.filePath)
                                                    }
                                                },
                                            colors = CardDefaults.cardColors(containerColor = colorPalette().accent),
                                            shape = uiRoundnessShape(),
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(12.dp),
                                                horizontalArrangement = Arrangement.Center,
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                Icon(
                                                    painter = painterResource(Res.drawable.checkmark),
                                                    contentDescription = null,
                                                    tint = Color.White,
                                                    modifier = Modifier.size(24.dp),
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                BasicText(
                                                    text = when {
                                                        installMode == InstallMode.WINDOWS -> stringResource(Res.string.install)
                                                        command != null -> stringResource(Res.string.install_copy_command)
                                                        else -> stringResource(Res.string.install_open_folder)
                                                    },
                                                    modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE),
                                                    maxLines = 1,
                                                    style = typography().s.bold.copy(color = Color.White),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    is UpdateDownloadManager.DownloadState.Idle,
                    is UpdateDownloadManager.DownloadState.Failed -> {
                        if (state is UpdateDownloadManager.DownloadState.Failed) {
                            LaunchedEffect(state) {
                                Toaster.e(state.error)
                                // The phone's reset (L345-349): back to Idle so the button
                                // reads "Download" again, not "Retry"
                                UpdateDownloadManager.resetState()
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            if (hasUpdate) {
                                if (Updater.build == null) {
                                    // An update release exists but carries no downloadable build for
                                    // this installation (the release lacks this channel's asset) —
                                    // the card must not be a silent button: it shows the hint and
                                    // re-checks on click
                                    Card(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(uiRoundnessShape())
                                            .clickable {
                                                Toaster.i(Res.string.checking_for_updates)
                                                // The same forced check as the no-update card: the page re-renders
                                                // its state from the result
                                                Updater.checkForUpdate(isForced = true, showDialog = false)
                                            },
                                        colors = CardDefaults.cardColors(containerColor = colorPalette().background1),
                                        shape = uiRoundnessShape(),
                                        border = BorderStroke(1.dp, colorPalette().accent.copy(alpha = 0.5f)),
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(16.dp),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    painter = painterResource(Res.drawable.update),
                                                    contentDescription = null,
                                                    tint = colorPalette().accent,
                                                    modifier = Modifier.size(20.dp),
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                BasicText(
                                                    text = stringResource(Res.string.update_not_available_yet),
                                                    modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE),
                                                    maxLines = 1,
                                                    style = typography().s.semiBold.copy(color = colorPalette().accent),
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    Card(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(uiRoundnessShape())
                                            .clickable {
                                                val build = Updater.build
                                                if (build == null) {
                                                    // The asset disappeared in the meantime (a recheck replaced the
                                                    // release) — re-check instead of doing nothing
                                                    Toaster.i(Res.string.checking_for_updates)
                                                    Updater.checkForUpdate(isForced = true, showDialog = false)
                                                } else {
                                                    UpdateDownloadManager.startDownload(
                                                        downloadUrl = build.downloadUrl,
                                                        version = newVersion,
                                                        assetName = build.name,
                                                    )
                                                }
                                            },
                                        colors = CardDefaults.cardColors(containerColor = colorPalette().accent),
                                        shape = uiRoundnessShape(),
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(16.dp),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    painter = painterResource(Res.drawable.download),
                                                    contentDescription = null,
                                                    tint = Color.White,
                                                    modifier = Modifier.size(20.dp),
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                BasicText(
                                                    text = if (state is UpdateDownloadManager.DownloadState.Failed) {
                                                        stringResource(Res.string.retry)
                                                    } else {
                                                        stringResource(Res.string.download)
                                                    },
                                                    modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE),
                                                    maxLines = 1,
                                                    style = typography().s.bold.copy(color = Color.White),
                                                )
                                            }
                                        }
                                    }
                                }
                            } else {
                                Card(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(uiRoundnessShape())
                                        .clickable {
                                            Toaster.i(Res.string.checking_for_updates)
                                            // The phone's call (L402): the forced check WITHOUT
                                            // the startup dialog — the page re-renders its
                                            // "Update available" state instead of a dialog over it
                                            Updater.checkForUpdate(isForced = true, showDialog = false)
                                        },
                                    colors = CardDefaults.cardColors(containerColor = colorPalette().background1),
                                    shape = uiRoundnessShape(),
                                    border = BorderStroke(1.dp, colorPalette().accent.copy(alpha = 0.5f)),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                painter = painterResource(Res.drawable.update),
                                                contentDescription = null,
                                                tint = colorPalette().accent,
                                                modifier = Modifier.size(20.dp),
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            BasicText(
                                                text = stringResource(Res.string.check_update),
                                                modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE),
                                                maxLines = 1,
                                                style = typography().s.semiBold.copy(color = colorPalette().accent),
                                            )
                                        }
                                    }
                                }
                            }

                            Card(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(uiRoundnessShape())
                                    .clickable { openInBrowser(githubReleaseUrl(currentSuffix, currentVersion)) },
                                colors = CardDefaults.cardColors(containerColor = colorPalette().background1),
                                shape = uiRoundnessShape(),
                                border = BorderStroke(1.dp, colorPalette().textSecondary.copy(alpha = 0.5f)),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            painter = painterResource(Res.drawable.github_icon),
                                            contentDescription = null,
                                            tint = colorPalette().text,
                                            modifier = Modifier.size(20.dp),
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        BasicText(
                                            text = stringResource(Res.string.github),
                                            modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE),
                                            maxLines = 1,
                                            style = typography().s.semiBold.copy(color = colorPalette().text),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * The GitHub release URL of the shown version (the phone's tag logic, ported — with one fix:
 * a channel-suffixed version links to its OWN release tag, the phone's `substringBefore('-')`
 * dropped the dev suffix and pointed at the stable tag).
 */
internal fun githubReleaseUrl(suffix: String, version: String): String {
    val cleanVersion = version.removePrefix(UpdaterConstants.PREFIX_VERSION)
    val tag = if (suffix.isNotEmpty()) "v$cleanVersion" else "v${cleanVersion.substringBefore("-")}"
    return "${UpdaterConstants.REPO_URL}/releases/tag/$tag"
}

/**
 * The window back / Escape during an active download (the loop-2 closure, the phone's `BackHandler`
 * port, L142-148): the window's single [BackDispatcher] handler owns the back gesture, so the
 * decision + the cancel live here — when the update page is open AND a download is starting or
 * running, back toasts + cancels the download (the [UpdateDownloadManager.cancelDownload] — the
 * same as the page's own cancel action) and the page then closes as usual (the activity's back
 * handler runs this, then pops the page stack — `NavPageState.back`). Same behavior as the
 * phone: toast + cancel + leave.
 */
internal fun cancelDownloadOnBack() {
    val state = UpdateDownloadManager.downloadState.value
    if (state is UpdateDownloadManager.DownloadState.Starting ||
        state is UpdateDownloadManager.DownloadState.Downloading ||
        state is UpdateDownloadManager.DownloadState.DownloadingIndeterminate
    ) {
        Toaster.i(Res.string.download_cancelled)
        UpdateDownloadManager.cancelDownload()
    }
}

/** The release JSON format (GitHub serves extra fields — shared by the re-download's release read). */
private val releaseJson = Json { ignoreUnknownKeys = true }

/**
 * The update page's "Download again last update" gesture (the ⋮ menu's download item): re-fetches
 * the artifact of the RUNNING version — its own release tag's asset for the detected install
 * mode (the package-manager probe, when still pending, is the blocking `--version` scan, so the
 * call site runs this off the composition thread). The download itself rides the shared
 * [UpdateDownloadManager] state (the page's bottom action shows its progression).
 *
 * [isReleasePackage] adopts the release marker (spec `spec-arch-binary-package-release`,
 * loop 2 G14): a pacman install carries the 7th asset only when it is a release-pkg install —
 * without the marker this gesture is a no-op, like every other check on an AUR install. The
 * [client] and [changelogFetcher] are the test seams (the production defaults are
 * [updaterHttpClient] and the fire-and-forget [Updater.fetchCurrentChangelog]).
 */
internal suspend fun redownloadCurrentVersion(
    installMode: InstallMode,
    packageManager: PackageManager?,
    isReleasePackage: Boolean = currentDistributionMarker() != null,
    client: HttpClient = updaterHttpClient(),
    changelogFetcher: () -> Unit = { Updater.fetchCurrentChangelog() },
) {
    // The re-download re-fetches the RUNNING version's artifact — its changelog too, so the
    // "What's new" card shows the fresh (not cached) text of this version (spec AD-9; the
    // page entry already does this, but a re-download implies the cached artifact was stale).
    // The seam (review loop 3 — L3-BH10): the tests pass a no-op so the unit test does no
    // live network call
    changelogFetcher()
    val version = AppVersion.versionName
    val tag = "${UpdaterConstants.PREFIX_VERSION}${version.removePrefix(UpdaterConstants.PREFIX_VERSION)}"
    try {
        val response = client.get(
            "${UpdaterConstants.GITHUB_API}/repos/${UpdaterConstants.REPO}/releases/tags/$tag",
        )
        if (response.status != HttpStatusCode.OK) {
            // The phone's toast (L586-598): a warning that the artifact is not there yet
            Toaster.w(Res.string.update_not_available_yet)
            return
        }
        val release = releaseJson.decodeFromString<GithubRelease>(response.bodyAsText())
        val pm = packageManager ?: livePackageManager()
        val assetName = ArtifactNames.forMode(installMode, version, pm, isReleasePackage = isReleasePackage) ?: return
        val build = release.builds.firstOrNull { it.name == assetName } ?: return
        UpdateDownloadManager.startDownload(build.downloadUrl, version, build.name)
    } catch (e: Exception) {
        // The phone's toast (L586-598): a warning that the artifact is not there yet
        Toaster.w(Res.string.update_not_available_yet)
    } finally {
        client.close()
    }
}