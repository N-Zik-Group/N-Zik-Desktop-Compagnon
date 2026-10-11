package app.n_zik.compagnon.tray

import app.n_zik.compagnon.components.ui.screens.home.LibraryTab
import app.n_zik.compagnon.generated.resources.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource

/**
 * The phone's launcher app shortcuts (its `ShortcutIconSync.kt` + `AppShortcutsSettingsDialog.kt`),
 * on the PC in the tray menu: same ids, same labels / icons, same order + enabled rules (Rescue
 * locked on) — but no cap: the phone's 4-shortcut limit is its launcher's display limit, a tray menu
 * has room for all of them (user decision 2026-10-12). [destination] is where a functional shortcut takes the window;
 * the shortcuts without a PC destination (no search route in the contract, no profiles, no rescue
 * activity on the PC) stay listed and only open the window — shown, never faked (the house
 * wire-contract rule). The phone's per-profile shortcuts are not ported (the PC has no profiles).
 */
enum class AppShortcut(
    val id: String,
    val labelId: StringResource,
    val iconId: DrawableResource,
    val destination: ShortcutDestination?,
) {
    // PC additions (user decision 2026-10-12): the home itself, its Songs tab, and the phone's
    // Quick picks tab — inert, Quick picks is not in the contract
    Home("home", Res.string.home, Res.drawable.shortcut_home, ShortcutDestination.Home),
    QuickPicks("quick_picks", Res.string.quick_picks, Res.drawable.shortcut_quick_picks, null),
    Songs("songs", Res.string.songs, Res.drawable.shortcut_songs, ShortcutDestination.Tab(LibraryTab.Songs)),
    // The phone's launcher shortcuts
    Search("search", Res.string.search, Res.drawable.shortcut_search, null),
    Albums("albums", Res.string.albums, Res.drawable.shortcut_albums, ShortcutDestination.Tab(LibraryTab.Albums)),
    Artists("artists", Res.string.artists, Res.drawable.shortcut_artists, ShortcutDestination.Tab(LibraryTab.Artists)),
    Library("library", Res.string.playlists, Res.drawable.shortcut_library, ShortcutDestination.Tab(LibraryTab.Playlists)),
    Profiles("profiles", Res.string.profiles, Res.drawable.shortcut_profiles, null),
    Rescue("rescue", Res.string.rescue_center, Res.drawable.shortcut_rescue, null);

    val locked: Boolean get() = this == Rescue

    companion object {
        fun of(id: String): AppShortcut? = entries.firstOrNull { it.id == id }
    }
}

/** Where a functional shortcut takes the window: the home as it is, or one of its tabs. */
sealed interface ShortcutDestination {
    data object Home : ShortcutDestination

    data class Tab(val tab: LibraryTab) : ShortcutDestination
}

/** All shortcut ids: the PC additions, then the phone's default order (its `defaultShortcutsOrder`). */
val ALL_SHORTCUT_IDS: List<String> = AppShortcut.entries.map { it.id }

/** The phone's `DEFAULT_ACTIVE_SHORTCUT_IDS`. */
val DEFAULT_ACTIVE_SHORTCUT_IDS: List<String> =
    listOf(AppShortcut.Albums.id, AppShortcut.Artists.id, AppShortcut.Library.id, AppShortcut.Rescue.id)

/** The shortcut order and enabled set (the phone's `ShortcutConfig`). */
data class ShortcutConfig(val order: List<String>, val enabled: Set<String>)

/**
 * The phone's `parseShortcutConfig`, verbatim rules: [ShortcutConfig.order] holds every id once
 * (stored order first, missing ids appended, unknown / duplicate ids dropped); [ShortcutConfig.enabled]
 * always contains Rescue and falls back to [DEFAULT_ACTIVE_SHORTCUT_IDS] when nothing usable is stored.
 */
fun parseShortcutConfig(orderStr: String?, enabledStr: String?): ShortcutConfig {
    val storedOrder = orderStr.orEmpty().split(",").filter { it in ALL_SHORTCUT_IDS }.distinct()
    val order = storedOrder + ALL_SHORTCUT_IDS.filter { it !in storedOrder }
    val storedEnabled = enabledStr.orEmpty().split(",").filter { it in ALL_SHORTCUT_IDS }
    val enabled = (storedEnabled.ifEmpty { DEFAULT_ACTIVE_SHORTCUT_IDS } + AppShortcut.Rescue.id).toSet()
    return ShortcutConfig(order, enabled)
}

/** The phone's `resolveActiveShortcutIds` without its launcher cap: every enabled shortcut, in display order. */
fun resolveActiveShortcuts(orderStr: String?, enabledStr: String?): List<AppShortcut> {
    val (order, enabled) = parseShortcutConfig(orderStr, enabledStr)
    return order.filter { it in enabled }.mapNotNull { AppShortcut.of(it) }
}

/** The settings dialog's working state: every id in display order, with its checked flag. */
data class ShortcutRows(val order: List<String>, val checked: List<Boolean>) {
    fun enabledCsv(): String = order.filterIndexed { index, _ -> checked[index] }.joinToString(",")

    fun orderCsv(): String = order.joinToString(",")

    companion object {
        /** The dialog's opening state — the same resolution as the tray (the phone's `loadPrefs`). */
        fun load(orderStr: String?, enabledStr: String?): ShortcutRows {
            val order = parseShortcutConfig(orderStr, enabledStr).order
            val active = resolveActiveShortcuts(orderStr, enabledStr).map { it.id }.toSet()
            return ShortcutRows(order, order.map { it in active })
        }

        /** The phone's « Reset »: the default order and the default active set. */
        fun defaults(): ShortcutRows = ShortcutRows(ALL_SHORTCUT_IDS, ALL_SHORTCUT_IDS.map { it in DEFAULT_ACTIVE_SHORTCUT_IDS })
    }
}

/** The outcome of a checkbox tap in the settings dialog (the phone's `onCheckedChange` rules). */
sealed interface ShortcutToggle {
    data class Applied(val rows: ShortcutRows) : ShortcutToggle

    /** Rescue cannot be unchecked (the phone's `app_shortcuts_rescue_locked` toast). */
    data object Locked : ShortcutToggle
}

/** The phone's checkbox rule: Rescue stays checked (no cap on the PC). */
fun ShortcutRows.toggle(index: Int, value: Boolean): ShortcutToggle {
    val id = order.getOrNull(index) ?: return ShortcutToggle.Applied(this)
    if (!value && AppShortcut.of(id)?.locked == true) return ShortcutToggle.Locked
    return ShortcutToggle.Applied(copy(checked = checked.toMutableList().also { it[index] = value }))
}

/** Moves the row at [from] to [to] (the phone's drag reorder; the PC dialog moves one step per arrow). */
fun ShortcutRows.move(from: Int, to: Int): ShortcutRows {
    if (from !in order.indices || to !in order.indices || from == to) return this
    val newOrder = order.toMutableList().apply { add(to, removeAt(from)) }
    val newChecked = checked.toMutableList().apply { add(to, removeAt(from)) }
    return ShortcutRows(newOrder, newChecked)
}

/**
 * The tray → window hand-off of a picked shortcut (app-lifetime): the tray sets it, the window
 * (`MainActivity`) consumes it — back home, then the shortcut's tab. `null` = nothing pending.
 */
object AppShortcutRequests {
    val pending = MutableStateFlow<AppShortcut?>(null)
}
