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
 * The phone's preference keys (`app/it/fast4x/rimusic/utils/Preferences.kt`), same names, same defaults.
 * The Compagnon keeps them in `settings.json`, next to `pairing.json` (contract §12: user settings only).
 */
const val exoPlayerDiskCacheMaxSizeKey = "exoPlayerDiskCacheMaxSize"
const val exoPlayerCustomCacheKey = "exoPlayerCustomCache"
const val audioQualityFormatKey = "audioQualityFormat"
const val playbackVolumeKey = "playbackVolume"
const val chipSortsKey = "chipSorts"

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
) {
    /** Ceiling of the audio cache: `0` disabled, `null` unlimited. */
    val songCacheMaxBytes: Long? get() = exoPlayerDiskCacheMaxSize.cacheBytes(exoPlayerCustomCache)
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
