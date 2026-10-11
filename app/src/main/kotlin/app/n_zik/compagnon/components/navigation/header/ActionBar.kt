package app.n_zik.compagnon.components.navigation.header

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import app.n_zik.compagnon.artistThumbnailShape
import app.n_zik.compagnon.colorPalette
import app.n_zik.compagnon.components.themed.DropdownMenu
import app.n_zik.compagnon.components.ui.screens.profiles.ProfileFaceAvatar
import app.n_zik.compagnon.generated.resources.*
import org.jetbrains.compose.resources.stringResource

/**
 * Port of `HamburgerMenu` (phone's `app/it/fast4x/rimusic/ui/components/navigation/header/ActionBar.kt`
 * 50-193): the `DropdownMenu` on `background0` at 90 %, with the entries in the phone's order
 * (its 78-169) — the phone's nine entries, ported inert (spec `spec-fill-pc-ui-gaps-menus` #30-#38:
 * history 78, statistics 85, rewind 95 — the phone shows it by default, its `rewindEnabled` true;
 * Listen Together 103, profiles 117, picture in picture 124 — default off on the phone, visible and
 * inert anyway; debug logs 141 — the phone's default is off, so the static "Enable debug logs" label,
 * maintenance 152, rescue center 163: phone features without a contract route, shown, without
 * action) — interleaved with the entries the PC can do: "PC server" (`devices`, the "Phone" panel,
 * [onPhone]) between Listen Together and profiles, the divider before "Settings" (the Compagnon's
 * settings, [onSettings]) between picture in picture and debug logs.
 */
@Composable
private fun HamburgerMenu(
    expanded: Boolean,
    onPhone: () -> Unit,
    onSettings: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    val menu = DropdownMenu(
        expanded = expanded,
        containerColor = colorPalette().background0.copy(0.90f),
        onDismissRequest = onDismissRequest,
    )
    // The phone's inert entries (spec `spec-fill-pc-ui-gaps-menus` #30-#38, its `ActionBar.kt`
    // 78-163): shown in the phone's order, without action — no contract route
    // History (its 78-83)
    menu.add(DropdownMenu.Item(Res.drawable.history, Res.string.history) {})
    // Statistics (its 85-92)
    menu.add(DropdownMenu.Item(Res.drawable.stats_chart, Res.string.statistics) {})
    // Rewind (its 95-101): the phone shows it by default (its `rewindEnabled`, default true)
    menu.add(DropdownMenu.Item(Res.drawable.ic_launcher_monochrome, Res.string.rewind) {})
    // Listen Together (its 103-108)
    menu.add(DropdownMenu.Item(Res.drawable.people, Res.string.listen_together) {})
    // PC server button
    menu.add(
        DropdownMenu.Item(
            Res.drawable.devices,
            Res.string.bridge_server,
        ) { onPhone() },
    )
    // Profiles (its 117-122)
    menu.add(DropdownMenu.Item(Res.drawable.person, Res.string.profiles) {})
    // Picture in picture (its 124-129, default off on the phone): visible and inert
    menu.add(DropdownMenu.Item(Res.drawable.images_sharp, Res.string.menu_go_to_picture_in_picture) {})
    menu.add { HorizontalDivider() }
    // Settings button
    menu.add(
        DropdownMenu.Item(
            Res.drawable.settings,
            Res.string.settings,
        ) { onSettings() },
    )
    // Debug logs (its 141-149): the phone's default is off — the static "Enable debug logs" label
    menu.add(DropdownMenu.Item(Res.drawable.bugs, Res.string.enable_log_debug) {})
    // Maintenance (its 152-158)
    menu.add(DropdownMenu.Item(Res.drawable.shield_checkmark, Res.string.maintenance) {})
    // Rescue center (its 163-169)
    menu.add(DropdownMenu.Item(Res.drawable.shortcut_rescue, Res.string.rescue_center) {})
    menu.Draw()
}

/**
 * Port of the header's `ActionBar` (phone's `app/it/fast4x/rimusic/ui/components/navigation/header/ActionBar.kt`
 * 196-263): the 48 dp search icon (no search route in contract v1: shown, no action), then the active
 * profile's face (32 dp, 10 dp end padding, artist roundness) which opens the [HamburgerMenu]. The PC has no
 * profile photo: the face is the phone's fallback, the initials of the default profile name ("N-Zik Fan").
 * The connection state stays in the connection banner and the "Phone" panel.
 */
@Composable
fun ActionBar(
    onPhone: () -> Unit,
    onSettings: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    // Search Icon
    HeaderIcon(Res.drawable.search, contentDescription = stringResource(Res.string.search)) {}

    Box {
        ProfileFaceAvatar(
            faceName = stringResource(Res.string.profile_base_name),
            size = 32.dp,
            modifier = Modifier
                .padding(end = 10.dp)
                .clip(artistThumbnailShape())
                .clickable { expanded = !expanded },
        )

        // Hamburger menu
        HamburgerMenu(
            expanded = expanded,
            onPhone = {
                expanded = false
                onPhone()
            },
            onSettings = {
                expanded = false
                onSettings()
            },
            onDismissRequest = { expanded = false },
        )
    }
}
