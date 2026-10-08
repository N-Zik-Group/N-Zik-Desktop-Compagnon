package app.n_zik.compagnon.updater.services

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.n_zik.compagnon.components.dialog.updater.NewUpdateAvailableDialog
import app.n_zik.compagnon.generated.AppVersion
import app.n_zik.compagnon.generated.resources.*
import app.n_zik.compagnon.updater.models.ArtifactNames
import app.n_zik.compagnon.updater.models.CheckUpdateState
import app.n_zik.compagnon.updater.models.GithubRelease
import app.n_zik.compagnon.updater.models.UpdaterConstants
import app.n_zik.compagnon.updater.models.currentInstallMode
import app.n_zik.compagnon.updater.models.probePackageManager
import app.n_zik.compagnon.utils.Preferences
import app.n_zik.compagnon.utils.Toaster
import app.n_zik.compagnon.utils.UserSettings
import app.n_zik.compagnon.utils.coroutines.NzikDispatchers
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import java.net.UnknownHostException
import java.nio.file.NoSuchFileException
import java.util.logging.Logger
import kotlin.math.pow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.jetbrains.compose.resources.getString

/**
 * Port of the phone's `Updater` (phone's `app/n_zik/android/updater/services/Updater.kt` L51-509):
 * the check / filter / compare / changelog / state machine of the in-app update, Ktor CIO instead
 * of OkHttp. Deliberate deviations (spec `spec-updater`, AD-1 / AD-3 / AD-6):
 * - the channel is a build-time decision ([AppVersion.channel]): the check only ever sees the
 *   same-channel releases, and there is NO runtime beta toggle (the phone's `checkBetaUpdates`
 *   parameter is gone);
 * - the asset is not an APK: [ArtifactNames] picks the channel's artifact for the detected
 *   install mode (the phone's `extractBuild` APK-name matching is replaced by it);
 * - the settings live in `settings.json` ([Preferences]) instead of the phone's SharedPreferences
 *   (attached once from the composition root — [attach]).
 */
object Updater {

    private val log = Logger.getLogger("Updater")

    /** The release list / single-release JSON format (GitHub serves extra fields). */
    private val releaseJson = Json { ignoreUnknownKeys = true }

    var isCheckingForUpdate by mutableStateOf(false)
    var isFetchingChangelog by mutableStateOf(false)
    var latestChangelog: String? by mutableStateOf(null)
    var currentChangelog: String? by mutableStateOf(null)
    var latestVersionCode: Int? = null
    var githubRelease: GithubRelease? by mutableStateOf(null)
    // Loop 2: reactive — a re-check from the update page must refresh the dialog / the page
    // card without recomposing anything by hand (the phone's `build` / `tagName` were plain
    // fields because Android re-reads them on rotation; Compose Desktop needs the state).
    var build: GithubRelease.Build? by mutableStateOf(null)
    private var tagName: String? by mutableStateOf(null)

    /** The app's [Preferences] (`settings.json`), attached once before any check. */
    private var preferences: Preferences? = null

    /** Binds the app's [Preferences] — called once from the composition root (the phone's global `appContext().preferences`). */
    fun attach(preferences: Preferences) {
        this.preferences = preferences
    }

    private fun persist(transform: (UserSettings) -> UserSettings) {
        preferences?.update(transform)
    }

    /**
     * Writes the startup-check choice from the UI (the desktop's equivalent of the phone's
     * `CheckForUpdateDialog`'s direct `checkUpdateState = CheckUpdateState.Disabled` write —
     * its `rememberPreference` write, here through the attached [Preferences]): [CheckUpdateState.Off]
     * from the "Turn off" option of the "ask" startup dialog.
     */
    fun setCheckUpdateState(state: CheckUpdateState) {
        persist { it.copy(checkUpdateState = state.wire) }
    }

    /**
     * Extracts the version suffix from a version / tag string (port of the phone's
     * `extractVersionSuffix` L78-81): `v0.0.2-beta` → `beta`, `v0.0.2-dev-20260901` → `dev`.
     */
    fun extractVersionSuffix(versionStr: String): String {
        val parts = versionStr.removePrefix(UpdaterConstants.PREFIX_VERSION).split("-")
        return if (parts.size > 1) parts[1] else ""
    }

    /**
     * The display version of the pending update (port of the phone's `getDisplayVersion`
     * L116-121): keeps the release tag's "v" prefix, and appends the running build's channel
     * suffix when the tag lacks it ("v4.1.3" + beta build → "v4.1.3-beta"). The running
     * version name is returned when no release has been fetched yet (the phone's
     * `BuildConfig.VERSION_NAME` fallback).
     */
    fun getDisplayVersion(): String {
        val tag = githubRelease?.tagName ?: return AppVersion.versionName
        val suffix = extractVersionSuffix(AppVersion.versionName)
        // Stable (no suffix) and tags that already carry the channel suffix are shown as-is —
        // no duplication (the phone's `endsWith` guard, here suffix-aware for the full
        // desktop suffix names)
        return if (suffix.isEmpty() || extractVersionSuffix(tag).startsWith(suffix)) tag else "$tag-$suffix"
    }

    /**
     * Compares two version strings and returns true if version1 is newer than version2.
     * Ported UNCHANGED from the phone (`Updater.isVersionNewer` L171-198): the numeric base
     * first, then the dev date suffix ("v7.3.2-dev-20260806" vs "7.3.2-dev-20260806"). Pinned
     * by `VersionCompareTest`.
     */
    fun isVersionNewer(version1: String, version2: String): Boolean {
        val v1 = version1.removePrefix(UpdaterConstants.PREFIX_VERSION).substringBefore("-")
        val v2 = version2.removePrefix(UpdaterConstants.PREFIX_VERSION).substringBefore("-")

        val v1Parts = v1.split(".").map { it.toIntOrNull() ?: 0 }
        val v2Parts = v2.split(".").map { it.toIntOrNull() ?: 0 }

        val maxLength = maxOf(v1Parts.size, v2Parts.size)

        for (i in 0 until maxLength) {
            val v1Part = v1Parts.getOrNull(i) ?: 0
            val v2Part = v2Parts.getOrNull(i) ?: 0

            when {
                v1Part > v2Part -> return true
                v1Part < v2Part -> return false
            }
        }

        // Base versions are equal — compare date suffix for dev builds
        // Dev tag format: "v7.3.2-dev-20260806", dev versionName: "7.3.2-dev-20260806"
        val date1 = version1.removePrefix(UpdaterConstants.PREFIX_VERSION)
            .split("-").getOrNull(2)?.toIntOrNull() ?: 0
        val date2 = version2.removePrefix(UpdaterConstants.PREFIX_VERSION)
            .split("-").getOrNull(2)?.toIntOrNull() ?: 0

        return date1 > date2
    }

    /**
     * Same-channel eligibility (spec AD-1 / AD-3, port of the phone's channel filter in
     * `findBestRelease` L342-366 minus its runtime beta toggle): a check never crosses channels —
     * stable sees only the plain tags, beta only `-beta`, dev only `-dev-*`; debug / -git never
     * check (their [AppVersion.updaterEnabled] is false). Pure, pinned by `ChannelFilterTest`.
     */
    internal fun isSameChannel(releaseTag: String, channel: String): Boolean = when (channel) {
        UpdaterConstants.TYPE_STABLE -> extractVersionSuffix(releaseTag).isEmpty()
        UpdaterConstants.TYPE_BETA -> extractVersionSuffix(releaseTag).startsWith(UpdaterConstants.SUFFIX_CHAR_BETA)
        UpdaterConstants.TYPE_DEV -> extractVersionSuffix(releaseTag).startsWith(UpdaterConstants.SUFFIX_CHAR_DEV)
        else -> false
    }

    /**
     * Finds the best release for a channel (port of the phone's `findBestRelease` L342-405:
     * same base-score normalization + dev date bonus, same null when nothing is eligible).
     */
    internal fun findBestRelease(releases: List<GithubRelease>, channel: String): GithubRelease? {
        val eligibleReleases = releases.filter { release -> isSameChannel(release.tagName, channel) }
        if (eligibleReleases.isEmpty()) return null

        // Find the maximum number of version parts to normalize all versions
        val maxParts = eligibleReleases.maxOf { release ->
            val version = release.tagName.removePrefix(UpdaterConstants.PREFIX_VERSION).substringBefore("-")
            version.split(".").size
        }

        return eligibleReleases.maxByOrNull { release ->
            val version = release.tagName.removePrefix(UpdaterConstants.PREFIX_VERSION).substringBefore("-")
            val parts = version.split(".").map { it.toIntOrNull() ?: 0 }

            // Pad the parts array to have the same length as maxParts
            val normalizedParts = parts.toMutableList()
            while (normalizedParts.size < maxParts) {
                normalizedParts.add(0)
            }

            // Base comparable version number (e.g., 1.2.3 -> 1002003)
            val baseScore = normalizedParts.foldIndexed(0L) { index, acc, part ->
                acc + (part * (1000.0.pow(maxParts - 1 - index)).toLong())
            }

            // For dev releases, add the date as a fractional bonus
            // Dev tag format: "v7.3.2-dev-20260806"
            val dateBonus = if (channel == UpdaterConstants.TYPE_DEV) {
                val dateStr = release.tagName.split("-").lastOrNull()?.toLongOrNull() ?: 0L
                dateStr
            } else {
                0L
            }

            baseScore * 100_000_000L + dateBonus
        }
    }

    /**
     * The version code linked in the release body (the phone's regex, kept — spec AD-3: the body
     * carries the raw changelog link `Updater/changelogs/<code>.txt`, written by the release chore).
     */
    internal fun changelogVersionCodeOf(releaseBody: String): String? =
        Regex("${UpdaterConstants.CHANGELOGS_PATH}/(\\d+)\\.txt").find(releaseBody)?.groupValues?.get(1)

    /**
     * The raw changelog URL of a release (port of the phone's `fetchUpdate` changelog branch
     * L248-277): `dev.txt` for dev tags, the linked `{code}.txt` otherwise; `null` when the
     * body links no changelog. Pinned by `ChangelogUrlTest`.
     */
    internal fun changelogUrlOf(release: GithubRelease): String? =
        if (release.tagName.contains(UpdaterConstants.SUFFIX_CHAR_DEV)) {
            "${UpdaterConstants.CHANGELOGS_URL}/dev.txt"
        } else {
            changelogVersionCodeOf(release.body)?.let { "${UpdaterConstants.CHANGELOGS_URL}/$it.txt" }
        }

    /** The raw changelog URL of the RUNNING version (its version code, or `dev.txt` for dev builds). */
    internal fun currentChangelogUrl(versionCode: Int, isDev: Boolean): String =
        if (isDev) "${UpdaterConstants.CHANGELOGS_URL}/dev.txt"
        else "${UpdaterConstants.CHANGELOGS_URL}/$versionCode.txt"

    /**
     * Sends out requests to GitHub for the latest release (port of the phone's `fetchUpdate`
     * L214-286 — OkHttp replaced by Ktor; the beta-toggle parameter replaced by the channel).
     * The releases are filtered by channel and saved to [githubRelease] / [build] / [tagName];
     * the release's changelog is fetched alongside. The [client] is injected so the logic is
     * testable against a Ktor `MockEngine`.
     *
     * > **NOTE**: this is a blocking process, it should never run on the UI thread.
     *
     * @throws NoSuchFileException when no same-channel release exists (the phone's mapping:
     *   "no update available").
     *
     * **Toasts**: only the forced (explicit user) checks toast — the startup/background check
     * is silent (a repo with no release would otherwise toast « No update available » on every
     * launch — user decision, 2026-10-07).
     */
    internal suspend fun fetchUpdate(
        client: HttpClient,
        channel: String,
        isForced: Boolean = false,
    ) = withContext(NzikDispatchers.DATA) {
        // Get all releases to find the best one
        val url = "${UpdaterConstants.GITHUB_API}/repos/${UpdaterConstants.REPO}/releases"
        val response = client.get(url)

        if (response.status != HttpStatusCode.OK) {
            if (isForced) {
                Toaster.e(getString(Res.string.error_update_check_failed, "HTTP ${response.status.value}"))
            }
            return@withContext
        }

        val resBody = response.bodyAsText()
        if (resBody.isBlank()) {
            if (isForced) {
                Toaster.i(Res.string.info_no_update_available)
            }
            return@withContext
        }

        val releases = releaseJson.decodeFromString<List<GithubRelease>>(resBody)

        // Find the best release for this channel
        val bestRelease = findBestRelease(releases, channel) ?: throw NoSuchFileException("")

        this@Updater.githubRelease = bestRelease
        this@Updater.tagName = bestRelease.tagName

        // The asset of this channel for the detected install mode (the phone's `extractBuild`
        // APK-name matching, replaced by the channel artifact names; null = no asset — the
        // pacman/AUR hint shows instead of a download).
        val releaseVersion = bestRelease.tagName.removePrefix(UpdaterConstants.PREFIX_VERSION)
        val assetName = ArtifactNames.forMode(
            currentInstallMode(),
            releaseVersion,
            probePackageManager(),
        )
        this@Updater.build = when (assetName) {
            null -> null
            else -> bestRelease.builds.firstOrNull { it.name == assetName } ?: throw NoSuchFileException("")
        }

        // The release's changelog (dev → dev.txt; stable/beta → the {code}.txt linked in the body)
        val changelogUrl = changelogUrlOf(bestRelease)
        if (changelogUrl != null) {
            try {
                isFetchingChangelog = true
                latestVersionCode = changelogVersionCodeOf(bestRelease.body)?.toIntOrNull()
                val txtRes = client.get(changelogUrl)
                if (txtRes.status == HttpStatusCode.OK) {
                    latestChangelog = txtRes.bodyAsText()
                }
            } catch (e: Exception) {
                log.warning("Error fetching changelog: ${e::class.simpleName} (${e.message})")
            } finally {
                isFetchingChangelog = false
            }
        }
    }

    /**
     * The production path of [fetchUpdate]: the Ktor CIO client, this build's channel — and the
     * updater is OFF on debug and -git (source builds, anti-downgrade — spec AD-1). [isForced]
     * is forwarded so only the explicit user checks toast (see the core [fetchUpdate]).
     */
    suspend fun fetchUpdate(isForced: Boolean = false) {
        if (!AppVersion.updaterEnabled) return
        val client = updaterHttpClient()
        try {
            isCheckingForUpdate = true
            try {
                fetchUpdate(client, AppVersion.channel, isForced)
            } finally {
                isCheckingForUpdate = false
            }
        } finally {
            client.close()
        }
    }

    /**
     * Fetches the running version's changelog (port of the phone's `fetchCurrentChangelog`
     * L291-324): `{versionCode}.txt`, or `dev.txt` for dev builds; the result is cached in
     * `settings.json` (the phone's `cached_changelog` / `cached_changelog_version` keys).
     */
    fun fetchCurrentChangelog() = NzikDispatchers.fireAndForget(NzikDispatchers.DATA).launch {
        try {
            isFetchingChangelog = true
            val client = updaterHttpClient()
            try {
                val response = client.get(
                    currentChangelogUrl(AppVersion.versionCode, AppVersion.channel == UpdaterConstants.TYPE_DEV),
                )
                if (response.status == HttpStatusCode.OK) {
                    val fetchedChangelog = response.bodyAsText()
                    if (fetchedChangelog.isNotBlank()) {
                        currentChangelog = fetchedChangelog
                        // Cache the changelog locally
                        persist {
                            it.copy(
                                changelogCache = fetchedChangelog,
                                changelogCacheVersion = AppVersion.versionCode,
                            )
                        }
                    }
                }
            } finally {
                client.close()
            }
        } catch (e: Exception) {
            log.warning("Error fetching current changelog: ${e::class.simpleName} (${e.message})")
            // If network fails, try to load from cache
            if (currentChangelog.isNullOrBlank()) {
                loadCachedChangelog()
            }
        } finally {
            isFetchingChangelog = false
        }
    }

    /** Loads the changelog from the `settings.json` cache if it matches the running version code. */
    fun loadCachedChangelog() {
        val prefs = preferences ?: return
        val s = prefs.settings.value
        if (s.changelogCacheVersion == AppVersion.versionCode && !s.changelogCache.isNullOrBlank()) {
            currentChangelog = s.changelogCache
        }
    }

    /**
     * The pure skip / result decision of [checkForUpdate] (loop 2 — the skip and reset rules
     * extracted so `decideCheckTest` pins them without any network or dialog):
     *
     *  * [Skip] — the check does not run: the updater is off (debug / -git — anti-downgrade), or
     *    a NON-forced check while the dialog-cancellation flag is set (the user's "don't check"
     *    gesture — the phone's in-memory `NewUpdateAvailableDialog.isCancelled`, re-armed to its
     *    default on every launch);
     *  * [Result] — the check runs, with the outcome of the version comparison: [hasUpdate]
     *    true = the best same-channel release is newer than this build.
     *
     * [updateCancelled] is the IN-MEMORY dialog-cancellation flag only (the phone's `PH-UPD`
     * L417 skip rule): the persisted `updateCancelled` setting is WRITE-ONLY, like on the
     * phone — written on dialog dismissal and on a no-update result, but never read in the
     * skip decision, so a dismissal never suppresses the startup check across launches.
     */
    sealed interface CheckDecision {
        /** The check was skipped (updater off, or dialog-cancelled + non-forced). */
        data object Skip : CheckDecision

        /** The check runs: [hasUpdate] is the outcome of the version comparison. */
        data class Result(val hasUpdate: Boolean) : CheckDecision
    }

    internal fun decideCheck(
        updaterEnabled: Boolean,
        updateCancelled: Boolean,
        isForced: Boolean,
        bestTagName: String?,
        currentVersion: String,
    ): CheckDecision = when {
        !updaterEnabled -> CheckDecision.Skip
        !isForced && updateCancelled -> CheckDecision.Skip
        else -> CheckDecision.Result(bestTagName?.let { isVersionNewer(it, currentVersion) } ?: false)
    }

    /**
     * Checks for an update of this channel (port of the phone's `checkForUpdate` L407-466, its
     * runtime beta toggle replaced by the channel; loop 2: the skip / reset rules moved to the
     * pure [decideCheck], the previous changelog is cleared at the start, a non-JSON 200 body
     * maps to a clean check-failed error, and only a forced check resets the persisted
     * cancelled flag). Updates `lastUpdateCheck`, fetches when needed, and sets the dialog
     * state + the toasts.
     *
     * @param isForced a forced check ignores the "update cancelled" flag (the explicit
     *   user actions: the settings page's check, the About card's check).
     * @param showDialog whether the startup [NewUpdateAvailableDialog] is raised on an update
     *   (AD-10: the About page's "Check update" forces the check with `showDialog = false`
     *   and opens the update PAGE instead — the dialog and the page never both show).
     */
    fun checkForUpdate(isForced: Boolean = false, showDialog: Boolean = true) = NzikDispatchers.fireAndForget(NzikDispatchers.DATA).launch {
        // The check starts from a clean slate (loop 2): the previous check's changelog must not
        // linger when this one finds nothing (the "What's new" card shows nothing, not stale text).
        latestChangelog = null
        // Update the last check timestamp at the beginning
        persist { it.copy(lastUpdateCheck = System.currentTimeMillis()) }

        if (
            decideCheck(
                updaterEnabled = AppVersion.updaterEnabled,
                // The in-memory dialog-cancellation flag only (the phone's skip rule, L417):
                // the persisted `updateCancelled` setting is write-only, like on the phone
                updateCancelled = NewUpdateAvailableDialog.isCancelled,
                isForced = isForced,
                bestTagName = tagName,
                currentVersion = AppVersion.versionName,
            ) is CheckDecision.Skip
        ) return@launch

        try {
            if (tagName == null || isForced) {
                fetchUpdate(isForced = isForced)
            }

            // Check if the new version is actually newer
            val hasUpdate = tagName?.let { isVersionNewer(it, AppVersion.versionName) } ?: false

            // The startup dialog is raised only when the caller wants it (the About page's
            // "Check update" opens the update page instead — AD-10).
            if (showDialog) {
                NewUpdateAvailableDialog.isActive = hasUpdate
            }

            if (hasUpdate) {
                if (isForced) {
                    Toaster.i(Res.string.update_available)
                }
                // If there's an update available, the in-memory cancel flag is cleared (the
                // persisted one stays — only a forced check may re-arm it).
                NewUpdateAvailableDialog.isCancelled = false
            } else {
                if (isForced) {
                    Toaster.i(Res.string.info_no_update_available)
                }
                NewUpdateAvailableDialog.isCancelled = true
                // Only a forced check may reset the persisted cancelled flag (finding #22): the
                // user's "don't check" gesture survives background startup checks; an explicit
                // check that found nothing clears it so future updates can be offered again.
                if (isForced) {
                    persist { it.copy(updateCancelled = false) }
                }
            }
        } catch (e: Exception) {
            val message = when (e) {
                is UnknownHostException -> getString(Res.string.error_no_internet)
                is NoSuchFileException -> getString(Res.string.info_no_update_available)
                // Loop 2: a 200 with a non-JSON body (GitHub rate-limit HTML, a proxy error
                // page) must map to the clean check-failed error, not a raw decode dump.
                is SerializationException -> getString(Res.string.error_update_check_failed, "invalid response")
                else -> e.message ?: getString(Res.string.error_update_check_failed, "unknown")
            }

            // Use appropriate toast type based on exception — forced checks only: the
            // startup/background check is silent (see the core [fetchUpdate] KDoc).
            if (isForced) {
                when (e) {
                    is NoSuchFileException -> Toaster.i(message)
                    else -> Toaster.e(message)
                }
            }

            NewUpdateAvailableDialog.isCancelled = true
        }
    }

    /** Persists the dialog's cancelled flag (the phone's `NewUpdateAvailableDialog.onDismiss` SharedPreferences write). */
    internal fun persistUpdateCancelled(cancelled: Boolean) {
        persist { it.copy(updateCancelled = cancelled) }
    }
}
