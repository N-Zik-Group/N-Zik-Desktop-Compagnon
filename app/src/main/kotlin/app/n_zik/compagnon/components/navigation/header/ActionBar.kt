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
import app.n_zik.compagnon.generated.resources.Res
import app.n_zik.compagnon.generated.resources.bridge_server
import app.n_zik.compagnon.generated.resources.devices
import app.n_zik.compagnon.generated.resources.profile_base_name
import app.n_zik.compagnon.generated.resources.search
import app.n_zik.compagnon.generated.resources.settings
import org.jetbrains.compose.resources.stringResource

/**
 * Port of `HamburgerMenu` (phone's `app/it/fast4x/rimusic/ui/components/navigation/header/ActionBar.kt`
 * 50-193): the `DropdownMenu` on `background0` at 90 %, with the entries the PC can do: "PC server"
 * (`devices`, the "Phone" panel, [onPhone]), the divider, "Settings" (the Compagnon's settings,
 * [onSettings]). Dropped: history, statistics, rewind, Listen Together, profiles, picture in picture,
 * debug logs, maintenance and the rescue center (phone features without a contract route).
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
    // PC server button
    menu.add(
        DropdownMenu.Item(
            Res.drawable.devices,
            Res.string.bridge_server,
        ) { onPhone() },
    )
    menu.add { HorizontalDivider() }
    // Settings button
    menu.add(
        DropdownMenu.Item(
            Res.drawable.settings,
            Res.string.settings,
        ) { onSettings() },
    )
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
