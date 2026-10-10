package app.n_zik.compagnon.utils

import androidx.compose.runtime.staticCompositionLocalOf
import app.n_zik.compagnon.bridge.library.AlbumSort
import app.n_zik.compagnon.bridge.library.ArtistSort
import app.n_zik.compagnon.bridge.library.PlaylistSongSort
import app.n_zik.compagnon.bridge.library.PlaylistSort
import app.n_zik.compagnon.bridge.library.SongSort
import app.n_zik.compagnon.bridge.library.TopPeriod
import app.n_zik.compagnon.bridge.pairing.CredentialStore
import app.n_zik.compagnon.enums.AudioQualityFormat
import app.n_zik.compagnon.enums.ExoPlayerDiskCacheMaxSize
import app.n_zik.compagnon.updater.models.CheckUpdateState
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.logging.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/*
 * The phone's preference keys (`app/it/fast4x/rimusic/utils/Preferences.kt`), same names, same defaults —
 * except the `chipSorts` / `itemSizes` maps, which are Compagnon-local: the phone keeps one preference per
 * tab (its `Preference.kt`), the PC bundles them in two maps.
 * The Compagnon keeps them in `settings.json`, next to `pairing.json` (contract §12: user settings only).
 */
const val exoPlayerDiskCacheMaxSizeKey = "exoPlayerDiskCacheMaxSize"
const val exoPlayerCustomCacheKey = "exoPlayerCustomCache"
const val audioQualityFormatKey = "audioQualityFormat"
const val playbackVolumeKey = "playbackVolume"
const val chipSortsKey = "chipSorts"
const val itemSizesKey = "itemSizes"
const val disableScrollingTextKey = "disableScrollingText"
const val languageKey = "language"
const val lastPhoneLanguageKey = "lastPhoneLanguage"
// The in-app updater (spec `spec-updater`, the phone's updater preference keys):
const val checkUpdateStateKey = "checkUpdateState"
const val updateCancelledKey = "updateCancelled"
const val lastUpdateCheckKey = "lastUpdateCheck"
const val changelogCacheKey = "cached_changelog"
const val changelogCacheVersionKey = "cached_changelog_version"
// The updater's changelog translation (the phone's updater translation preference keys, spec AD-9):
const val otherLanguageAppUpdateKey = "otherLanguageAppUpdate"
const val updateTranslationActiveKey = "updateTranslationActive"

/**
 * The sort state of one library chip (contract §10, since 1.6): as on the phone, every chip of a
 * page keeps its own sort and direction (the phone's per-tab sort preferences). The wire values
 * (`SongSort` / `TopPeriod`) are stored as strings, so an unknown one reads as the default.
 */
@Serializable
data class ChipSort(
    val sort: String = "title",
    val reverse: Boolean = false,
    /** The period of a Top chip (the phone's `StatisticsType`); `null` keeps the phone's own period. */
    val period: String? = null,
) {
    val songSort: SongSort get() = SongSort.entries.firstOrNull { it.wire == sort } ?: SongSort.Title
    val albumSort: AlbumSort get() = AlbumSort.entries.firstOrNull { it.wire == sort } ?: AlbumSort.Title
    val artistSort: ArtistSort get() = ArtistSort.entries.firstOrNull { it.wire == sort } ?: ArtistSort.Name
    val playlistSort: PlaylistSort get() = PlaylistSort.entries.firstOrNull { it.wire == sort } ?: PlaylistSort.Name

    /**
     * The local sort of a local playlist's tracks: the phone's `PlaylistSongSortBy` names (its 14 wire
     * names) plus the phone's rewind-only `RewindTop`, which the contract serves as the phone's position
     * order (`Custom` — the rewind playlists keep their top order in the database, so a live sort never
     * rewrites it).
     */
    val playlistSongSort: PlaylistSongSort
        get() = if (sort == "rewindTop") PlaylistSongSort.Custom
        else PlaylistSongSort.entries.firstOrNull { it.wire == sort } ?: PlaylistSongSort.Title
    val topPeriod: TopPeriod? get() = period?.let { value -> TopPeriod.entries.firstOrNull { it.wire == value } }
}

/** The user settings of the Compagnon. An unknown or missing value reads as the phone's default. */
@Serializable
data class UserSettings(
    @SerialName(exoPlayerDiskCacheMaxSizeKey) val exoPlayerDiskCacheMaxSize: ExoPlayerDiskCacheMaxSize = ExoPlayerDiskCacheMaxSize.`2GB`,
    @SerialName(exoPlayerCustomCacheKey) val exoPlayerCustomCache: Int = 32,
    @SerialName(audioQualityFormatKey) val audioQualityFormat: AudioQualityFormat = AudioQualityFormat.Auto,
    /** 0–1, local only (contract §8.5). */
    @SerialName(playbackVolumeKey) val playbackVolume: Float = 1f,
    /** The per-chip sort of the library pages, keyed `page:chip` (the phone keeps one sort per tab). */
    @SerialName(chipSortsKey) val chipSorts: Map<String, ChipSort> = emptyMap(),
    /**
     * The home grid item size per page, keyed by the page name (`albums`, `artists`, `playlists`), the
     * phone's `HomeItemSize` wire names (`small` / `medium` / `big`). A Compagnon-local setting: the
     * phone's per-tab sizes are not in the contract.
     */
    @SerialName(itemSizesKey) val itemSizes: Map<String, String> = emptyMap(),
    /** The phone's "Disable scrolling text" (its `disableScrollingTextKey`): since contract 1.10.0 (`ui.settings`) mirrored from the phone once read, the PC's own value for an older phone. */
    @SerialName(disableScrollingTextKey) val disableScrollingText: Boolean = false,
    /**
     * The PC's own "App language" (contract 1.9.0, `ui.language`): the sentinel `auto_pc` (the PC's
     * OS locale), `auto_tel` (the phone's language — the default) or a BCP-47 code of the phone's
     * list; an unknown code falls back to the PC's `values/` (English), never a crash.
     */
    @SerialName(languageKey) val language: String = AppLanguage.AUTO_TEL,
    /** The last phone language received in `meta` (contract 1.9.0): the `auto_tel` fallback. */
    @SerialName(lastPhoneLanguageKey) val lastPhoneLanguage: String? = null,
    /**
     * The in-app updater's three-state check choice (spec `spec-updater`, AD-9, loop 2 — the
     * phone's `checkUpdateState`, replacing the v1 boolean): the wire value "on" (automatic
     * check at startup), "ask" (ask the user at startup before checking) or "off" (no startup
     * check); an unknown value reads as "on" (the default, [CheckUpdateState.fromWire]). The
     * updater is forced off on debug / -git builds (`AppVersion.updaterEnabled`), where this
     * choice is ignored.
     */
    @SerialName(checkUpdateStateKey) val checkUpdateState: String = CheckUpdateState.On.wire,
    /** The update dialog was dismissed while an update was available (the phone's `updateCancelledKey`). */
    @SerialName(updateCancelledKey) val updateCancelled: Boolean = false,
    /** The last update check, millis (the phone's `lastUpdateCheckKey`). */
    @SerialName(lastUpdateCheckKey) val lastUpdateCheck: Long = 0L,
    /** The cached changelog of the running version (the phone's `cached_changelog`). */
    @SerialName(changelogCacheKey) val changelogCache: String? = null,
    /** The version code the [changelogCache] belongs to (the phone's `cached_changelog_version`; -1 = none). */
    @SerialName(changelogCacheVersionKey) val changelogCacheVersion: Int = -1,
    /**
     * The changelog's translation language (the phone's `otherLanguageAppUpdate`): an `UpdateLanguage`
     * BCP-47 code, "system" for the app language; an unknown code reads as "system" at the read site.
     */
    @SerialName(otherLanguageAppUpdateKey) val otherLanguageAppUpdate: String = "system",
    /**
     * The changelog translation toggle (the phone's `updateTranslationActive`); `null` = never set, and
     * its default is the phone's — active when the app language is not English (decided at the read site).
     */
    @SerialName(updateTranslationActiveKey) val updateTranslationActive: Boolean? = null,
) {
    /** Ceiling of the audio cache: `0` disabled, `null` unlimited. */
    val songCacheMaxBytes: Long? get() = exoPlayerDiskCacheMaxSize.cacheBytes(exoPlayerCustomCache)

    /** The [checkUpdateState] wire value decoded to the typed choice (unknown → [CheckUpdateState.On]). */
    val checkUpdateStateValue: CheckUpdateState get() = CheckUpdateState.fromWire(checkUpdateState)
}

/**
 * `settings.json` in the app folder ([CredentialStore.appDirectory]). Read once; every change is written
 * at once, atomically (temporary file then rename). A corrupt file reads as the defaults.
 */
class Preferences(private val file: Path = CredentialStore.appDirectory().resolve(FILE_NAME)) {
    private val log = Logger.getLogger("Preferences")

    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<UserSettings> = _settings.asStateFlow()

    fun update(transform: (UserSettings) -> UserSettings) {
        _settings.update(transform)
        runCatching { write(_settings.value) }
            .onFailure { log.warning("Could not write settings.json: ${it::class.simpleName}") }
    }

    private fun load(): UserSettings {
        if (!Files.exists(file)) return UserSettings()
        return runCatching { JSON.decodeFromString(UserSettings.serializer(), Files.readString(file, Charsets.UTF_8)) }
            .onFailure { log.warning("settings.json is unreadable: defaults used") }
            .getOrDefault(UserSettings())
    }

    @Synchronized
    private fun write(settings: UserSettings) {
        file.parent?.let { Files.createDirectories(it) }
        val tmp = file.resolveSibling("${file.fileName}.tmp")
        Files.writeString(tmp, JSON.encodeToString(UserSettings.serializer(), settings), Charsets.UTF_8)
        try {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    companion object {
        const val FILE_NAME = "settings.json"

        private val JSON = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
            encodeDefaults = true
        }
    }
}

/** The app's [Preferences], provided by the root composable (the phone's `rememberPreference` reads its own store). */
val LocalPreferences = staticCompositionLocalOf<Preferences?> { null }
