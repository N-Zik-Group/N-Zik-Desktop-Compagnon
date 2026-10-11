package app.n_zik.compagnon.utils

import androidx.compose.runtime.staticCompositionLocalOf
import app.n_zik.compagnon.bridge.library.AlbumSort
import app.n_zik.compagnon.bridge.library.ArtistSort
import app.n_zik.compagnon.bridge.library.PlaylistSongSort
import app.n_zik.compagnon.bridge.library.PlaylistSort
import app.n_zik.compagnon.bridge.library.SongSort
import app.n_zik.compagnon.bridge.library.TopPeriod
import app.n_zik.compagnon.bridge.pairing.CredentialStore
import app.n_zik.compagnon.bridge.state.PlayerBackgroundColors
import app.n_zik.compagnon.enums.AlbumSwipeAction
import app.n_zik.compagnon.enums.AnimatedGradient
import app.n_zik.compagnon.enums.AudioQualityFormat
import app.n_zik.compagnon.enums.CloseBehavior
import app.n_zik.compagnon.enums.ExoPlayerDiskCacheMaxSize
import app.n_zik.compagnon.enums.HomeScreenTabs
import app.n_zik.compagnon.enums.NavigationBarPosition
import app.n_zik.compagnon.enums.NavigationBarType
import app.n_zik.compagnon.enums.PlayerSwipeSensitivity
import app.n_zik.compagnon.enums.PlayerTimelineType
import app.n_zik.compagnon.enums.PlaylistSwipeAction
import app.n_zik.compagnon.enums.QueueSwipeAction
import app.n_zik.compagnon.enums.SwipeAnimationNoThumbnail
import app.n_zik.compagnon.enums.ThumbnailCoverType
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
const val playlistindicatorKey = "playlistindicator"
// The in-app updater (spec `spec-updater`, the phone's updater preference keys):
const val checkUpdateStateKey = "checkUpdateState"
const val updateCancelledKey = "updateCancelled"
const val lastUpdateCheckKey = "lastUpdateCheck"
const val changelogCacheKey = "cached_changelog"
const val changelogCacheVersionKey = "cached_changelog_version"
// The updater's changelog translation (the phone's updater translation preference keys, spec AD-9):
const val otherLanguageAppUpdateKey = "otherLanguageAppUpdate"
const val updateTranslationActiveKey = "updateTranslationActive"
// The player's appearance (spec `spec-fill-pc-ui-gaps-player`): the phone's preference keys, same names and defaults
const val rotatingAlbumCoverKey = "rotatingAlbumCover"
const val showCoverThumbnailAnimationKey = "showCoverThumbnailAnimation"
const val coverThumbnailAnimationKey = "coverThumbnailAnimation"
const val playerBackgroundColorsKey = "playerBackgroundColors"
const val animatedGradientKey = "animatedGradient"
const val playerTimelineTypeKey = "playerTimelineType"
const val swipeAnimationsNoThumbnailKey = "swipeAnimationsNoThumbnail"
const val playerSwipeSensitivityKey = "playerSwipeSensitivity"
const val showMiniPlayerDiscoverKey = "showMiniPlayerDiscover"
const val showMiniPlayerVisualizerKey = "showMiniPlayerVisualizer"
const val queueSwipeLeftActionKey = "queueSwipeLeftAction"
const val queueSwipeRightActionKey = "queueSwipeRightAction"
const val isSwipeToActionEnabledKey = "isSwipeToActionEnabled"
// The home screens (spec `spec-fill-pc-ui-gaps-home`): the phone's preference keys, same names and defaults
const val navigationBarPositionKey = "navigationBarPosition"
const val navigationBarTypeKey = "navigationBarType"
const val indexNavigationTabKey = "indexNavigationTab"
const val homeScreenTabIndexKey = "homeScreenTabIndex"
const val playlistSwipeLeftActionKey = "playlistSwipeLeftAction"
const val playlistSwipeRightActionKey = "playlistSwipeRightAction"
const val albumSwipeLeftActionKey = "albumSwipeLeftAction"
const val albumSwipeRightActionKey = "albumSwipeRightAction"
const val showFloatingIconKey = "showFloatingIcon"
const val multiFloatActionIconOffsetXKey = "multiFloatActionIconOffsetX"
const val multiFloatActionIconOffsetYKey = "multiFloatActionIconOffsetY"
// The window's close behavior (spec `spec-tray-taskbar-media-controls`):
const val closeBehaviorKey = "closeBehavior"
// The app shortcuts in the tray menu (the phone's `ShortcutIconSync.kt` keys, same CSV values):
const val appShortcutsOrderKey = "appShortcutsOrder"
const val appShortcutsEnabledKey = "appShortcutsEnabled"

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
    /** The PC's own "Disable scrolling text" (the phone's `disableScrollingTextKey` name is kept): stops the marquees of every text surface. */
    @SerialName(disableScrollingTextKey) val disableScrollingText: Boolean = false,
    /**
     * The PC's own "App language": the sentinel [AppLanguage.SYSTEM] (the PC's OS locale — the
     * default) or a BCP-47 code of the phone's list; an unknown code falls back to the PC's
     * `values/` (English), never a crash.
     */
    @SerialName(languageKey) val language: String = AppLanguage.SYSTEM,
    /**
     * The phone's "Show playlist indicator" (its `playlistindicatorKey`): the song rows' "in a playlist"
     * badge (contract 1.12.0 `Track.inPlaylist`). Shown by default on the PC — the phone's default is off
     * (decision 1 of the PC UI gaps plan: the items behind an off phone setting are visible until the PC
     * has its own setting entry).
     */
    @SerialName(playlistindicatorKey) val playlistIndicator: Boolean = true,
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
    /** The phone's "Rotating album cover" (its `rotatingAlbumCoverKey`): the blurred cover background turns in 30 s. */
    @SerialName(rotatingAlbumCoverKey) val rotatingAlbumCover: Boolean = false,
    /** The phone's "Show cover thumbnail animation" (its `showCoverThumbnailAnimationKey`): the central cover turns while it plays. */
    @SerialName(showCoverThumbnailAnimationKey) val showCoverThumbnailAnimation: Boolean = false,
    /** The phone's cover style name (its `coverThumbnailAnimationKey`); an unknown name reads as [ThumbnailCoverType.Vinyl]. */
    @SerialName(coverThumbnailAnimationKey) val coverThumbnailAnimation: String = ThumbnailCoverType.Vinyl.name,
    /** The phone's player background name (its `playerBackgroundColorsKey`); an unknown name reads as [PlayerBackgroundColors.AnimatedGradient]. */
    @SerialName(playerBackgroundColorsKey) val playerBackgroundColors: String = PlayerBackgroundColors.AnimatedGradient.name,
    /** The phone's animated gradient name (its `animatedGradientKey`); an unknown name reads as [AnimatedGradient.M3EMorphingCover]. */
    @SerialName(animatedGradientKey) val animatedGradient: String = AnimatedGradient.M3EMorphingCover.name,
    /** The phone's timeline name (its `playerTimelineTypeKey`); an unknown name reads as [PlayerTimelineType.Wavy]. */
    @SerialName(playerTimelineTypeKey) val playerTimelineType: String = PlayerTimelineType.Wavy.name,
    /** The phone's cover transition name (its `swipeAnimationsNoThumbnailKey`); an unknown name reads as [SwipeAnimationNoThumbnail.Sliding]. */
    @SerialName(swipeAnimationsNoThumbnailKey) val swipeAnimationsNoThumbnail: String = SwipeAnimationNoThumbnail.Sliding.name,
    /** The phone's cover swipe sensitivity name (its `playerSwipeSensitivityKey`); an unknown name reads as [PlayerSwipeSensitivity.Medium]. */
    @SerialName(playerSwipeSensitivityKey) val playerSwipeSensitivity: String = PlayerSwipeSensitivity.Medium.name,
    /**
     * The phone's mini-player "Discover" button (its `showMiniPlayerDiscoverKey`, off by default on the
     * phone; off on the PC too — user decision 2026-10-11: the default mini-player row is the transport
     * controls plus the audio output only).
     */
    @SerialName(showMiniPlayerDiscoverKey) val showMiniPlayerDiscover: Boolean = false,
    /** The phone's mini-player "Visualizer" button (its `showMiniPlayerVisualizerKey`, off by default on the phone; off on the PC too — user decision 2026-10-11). */
    @SerialName(showMiniPlayerVisualizerKey) val showMiniPlayerVisualizer: Boolean = false,
    /** The queue rows' left swipe action (the phone's `queueSwipeLeftActionKey`); an unknown name reads as [QueueSwipeAction.RemoveFromQueue]. */
    @SerialName(queueSwipeLeftActionKey) val queueSwipeLeftAction: String = QueueSwipeAction.RemoveFromQueue.name,
    /** The queue rows' right swipe action (the phone's `queueSwipeRightActionKey`); an unknown name reads as [QueueSwipeAction.PlayNext]. */
    @SerialName(queueSwipeRightActionKey) val queueSwipeRightAction: String = QueueSwipeAction.PlayNext.name,
    /** The phone's swipe actions toggle (its `isSwipeToActionEnabledKey`); off makes the queue rows non-swipeable. */
    @SerialName(isSwipeToActionEnabledKey) val isSwipeToActionEnabled: Boolean = true,
    /** The home's navigation bar position name (the phone's `navigationBarPositionKey`); an unknown name reads as [NavigationBarPosition.BottomFloating]. */
    @SerialName(navigationBarPositionKey) val navigationBarPosition: String = NavigationBarPosition.BottomFloating.name,
    /** The home's navigation bar style name (the phone's `navigationBarTypeKey`); an unknown name reads as [NavigationBarType.IconOnly]. */
    @SerialName(navigationBarTypeKey) val navigationBarType: String = NavigationBarType.IconOnly.name,
    /**
     * The home's start tab name (the phone's `indexNavigationTabKey`): [HomeScreenTabs.Default] or one of
     * the four visible tabs; an unknown name reads as [HomeScreenTabs.Default]. Applies on the next
     * launch, until [homeScreenTabIndex] is written (the phone's `rememberPreference` semantics).
     */
    @SerialName(indexNavigationTabKey) val indexNavigationTab: String = HomeScreenTabs.Default.name,
    /**
     * The last home tab used, persisted on tab change (the phone's `homeScreenTabIndexKey`): `null` =
     * never written, so the [indexNavigationTab] start tab applies; any value (including 0) then takes
     * precedence over it, as on the phone. `null` — not `0` — is the default: a persisted 0 is a valid
     * tab index and would otherwise hide the start tab on the first launch.
     */
    @SerialName(homeScreenTabIndexKey) val homeScreenTabIndex: Int? = null,
    /** The Songs rows' left swipe action name (the phone's `playlistSwipeLeftActionKey`); an unknown name reads as [PlaylistSwipeAction.Favourite]. */
    @SerialName(playlistSwipeLeftActionKey) val playlistSwipeLeftAction: String = PlaylistSwipeAction.Favourite.name,
    /** The Songs rows' right swipe action name (the phone's `playlistSwipeRightActionKey`); an unknown name reads as [PlaylistSwipeAction.PlayNext]. */
    @SerialName(playlistSwipeRightActionKey) val playlistSwipeRightAction: String = PlaylistSwipeAction.PlayNext.name,
    /** The Albums cards' left swipe action name (the phone's `albumSwipeLeftActionKey`); an unknown name reads as [AlbumSwipeAction.PlayNext]. */
    @SerialName(albumSwipeLeftActionKey) val albumSwipeLeftAction: String = AlbumSwipeAction.PlayNext.name,
    /** The Albums cards' right swipe action name (the phone's `albumSwipeRightActionKey`); an unknown name reads as [AlbumSwipeAction.Bookmark]. */
    @SerialName(albumSwipeRightActionKey) val albumSwipeRightAction: String = AlbumSwipeAction.Bookmark.name,
    /**
     * The home's floating actions container toggle (the phone's `showFloatingIconKey`, off by default on
     * the phone; off on the PC too — user decision 2026-10-11).
     */
    @SerialName(showFloatingIconKey) val showFloatingIcon: Boolean = false,
    /** The FAB's drag-repositioning x offset in px, persisted on long-press drag (the phone's `multiFloatActionIconOffsetXKey`). */
    @SerialName(multiFloatActionIconOffsetXKey) val multiFloatActionIconOffsetX: Float = 0f,
    /** The FAB's drag-repositioning y offset in px, persisted on long-press drag (the phone's `multiFloatActionIconOffsetYKey`). */
    @SerialName(multiFloatActionIconOffsetYKey) val multiFloatActionIconOffsetY: Float = 0f,
    /**
     * The window's close button (X) behavior (spec `spec-tray-taskbar-media-controls`): the [CloseBehavior]
     * wire string — "minimize_to_tray" (the default) hides the window to the system tray, "quit" closes
     * the app; an unknown value reads as the default. Only meaningful where a tray exists (Wayland hides
     * the setting — there the X quits regardless).
     */
    @SerialName(closeBehaviorKey) val closeBehavior: String = CloseBehavior.MinimizeToTray.wire,
    /** The app shortcuts' order, the phone's comma-separated ids (`null` = the default order). */
    @SerialName(appShortcutsOrderKey) val appShortcutsOrder: String? = null,
    /** The enabled app shortcuts, the phone's comma-separated ids (`null` = the phone's defaults). */
    @SerialName(appShortcutsEnabledKey) val appShortcutsEnabled: String? = null,
) {
    /** Ceiling of the audio cache: `0` disabled, `null` unlimited. */
    val songCacheMaxBytes: Long? get() = exoPlayerDiskCacheMaxSize.cacheBytes(exoPlayerCustomCache)

    /** The [checkUpdateState] wire value decoded to the typed choice (unknown → [CheckUpdateState.On]). */
    val checkUpdateStateValue: CheckUpdateState get() = CheckUpdateState.fromWire(checkUpdateState)

    /** The [coverThumbnailAnimation] name decoded to its style (unknown → [ThumbnailCoverType.Vinyl]). */
    val coverThumbnailAnimationValue: ThumbnailCoverType
        get() = ThumbnailCoverType.entries.firstOrNull { it.name == coverThumbnailAnimation } ?: ThumbnailCoverType.Vinyl

    /** The [playerBackgroundColors] name decoded to its background (unknown → [PlayerBackgroundColors.AnimatedGradient]). */
    val playerBackgroundColorsValue: PlayerBackgroundColors
        get() = PlayerBackgroundColors.entries.firstOrNull { it.name == playerBackgroundColors }
            ?: PlayerBackgroundColors.AnimatedGradient

    /** The [animatedGradient] name decoded to its style (unknown → [AnimatedGradient.M3EMorphingCover]). */
    val animatedGradientValue: AnimatedGradient
        get() = AnimatedGradient.entries.firstOrNull { it.name == animatedGradient } ?: AnimatedGradient.M3EMorphingCover

    /** The [playerTimelineType] name decoded to its timeline (unknown → [PlayerTimelineType.Wavy]). */
    val playerTimelineTypeValue: PlayerTimelineType
        get() = PlayerTimelineType.entries.firstOrNull { it.name == playerTimelineType } ?: PlayerTimelineType.Wavy

    /** The [swipeAnimationsNoThumbnail] name decoded to its transition (unknown → [SwipeAnimationNoThumbnail.Sliding]). */
    val swipeAnimationsNoThumbnailValue: SwipeAnimationNoThumbnail
        get() = SwipeAnimationNoThumbnail.entries.firstOrNull { it.name == swipeAnimationsNoThumbnail }
            ?: SwipeAnimationNoThumbnail.Sliding

    /** The [playerSwipeSensitivity] name decoded to its sensitivity (unknown → [PlayerSwipeSensitivity.Medium]). */
    val playerSwipeSensitivityValue: PlayerSwipeSensitivity
        get() = PlayerSwipeSensitivity.entries.firstOrNull { it.name == playerSwipeSensitivity }
            ?: PlayerSwipeSensitivity.Medium

    /** The [queueSwipeLeftAction] name decoded to its action (unknown → [QueueSwipeAction.RemoveFromQueue]). */
    val queueSwipeLeftActionValue: QueueSwipeAction
        get() = QueueSwipeAction.entries.firstOrNull { it.name == queueSwipeLeftAction }
            ?: QueueSwipeAction.RemoveFromQueue

    /** The [queueSwipeRightAction] name decoded to its action (unknown → [QueueSwipeAction.PlayNext]). */
    val queueSwipeRightActionValue: QueueSwipeAction
        get() = QueueSwipeAction.entries.firstOrNull { it.name == queueSwipeRightAction }
            ?: QueueSwipeAction.PlayNext

    /** The [navigationBarPosition] name decoded to its position (unknown → [NavigationBarPosition.BottomFloating]). */
    val navigationBarPositionValue: NavigationBarPosition
        get() = NavigationBarPosition.entries.firstOrNull { it.name == navigationBarPosition }
            ?: NavigationBarPosition.BottomFloating

    /** The [navigationBarType] name decoded to its style (unknown → [NavigationBarType.IconOnly]). */
    val navigationBarTypeValue: NavigationBarType
        get() = NavigationBarType.entries.firstOrNull { it.name == navigationBarType }
            ?: NavigationBarType.IconOnly

    /** The [indexNavigationTab] name decoded to its tab (unknown → [HomeScreenTabs.Default]). */
    val indexNavigationTabValue: HomeScreenTabs
        get() = HomeScreenTabs.entries.firstOrNull { it.name == indexNavigationTab }
            ?: HomeScreenTabs.Default

    /** The [playlistSwipeLeftAction] name decoded to its action (unknown → [PlaylistSwipeAction.Favourite]). */
    val playlistSwipeLeftActionValue: PlaylistSwipeAction
        get() = PlaylistSwipeAction.entries.firstOrNull { it.name == playlistSwipeLeftAction }
            ?: PlaylistSwipeAction.Favourite

    /** The [playlistSwipeRightAction] name decoded to its action (unknown → [PlaylistSwipeAction.PlayNext]). */
    val playlistSwipeRightActionValue: PlaylistSwipeAction
        get() = PlaylistSwipeAction.entries.firstOrNull { it.name == playlistSwipeRightAction }
            ?: PlaylistSwipeAction.PlayNext

    /** The [albumSwipeLeftAction] name decoded to its action (unknown → [AlbumSwipeAction.PlayNext]). */
    val albumSwipeLeftActionValue: AlbumSwipeAction
        get() = AlbumSwipeAction.entries.firstOrNull { it.name == albumSwipeLeftAction }
            ?: AlbumSwipeAction.PlayNext

    /** The [albumSwipeRightAction] name decoded to its action (unknown → [AlbumSwipeAction.Bookmark]). */
    val albumSwipeRightActionValue: AlbumSwipeAction
        get() = AlbumSwipeAction.entries.firstOrNull { it.name == albumSwipeRightAction }
            ?: AlbumSwipeAction.Bookmark
    /** The [closeBehavior] wire value decoded to its choice (unknown → [CloseBehavior.MinimizeToTray]). */
    val closeBehaviorValue: CloseBehavior get() = CloseBehavior.fromWire(closeBehavior)
}

/**
 * `settings.json` in the app folder ([CredentialStore.appDirectory]). Read once; every change is written
 * at once, atomically (temporary file then rename). A corrupt file reads as the defaults.
 */
class Preferences(private val file: Path = CredentialStore.appDirectory().resolve(FILE_NAME)) {
    private val log = Logger.getLogger("Preferences")

    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<UserSettings> = _settings.asStateFlow()

    init {
        // One-time migration (spec `spec-remove-ui-sync`): the old phone-mirroring sentinels are
        // gone — `auto_pc` and `auto_tel` both resolve to the PC's OS locale, now the `system`
        // sentinel. Idempotent: a fresh install or an already-migrated file decodes `system`
        // directly, and nothing is written. A pre-spec file's orphaned `lastPhoneLanguage` key is
        // ignored at decode (`ignoreUnknownKeys`) and dropped on the next write.
        if (_settings.value.language in setOf("auto_pc", "auto_tel")) { // the persisted old values; their constants were deleted with the sentinels
            update { it.copy(language = AppLanguage.SYSTEM) }
        }
    }

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
