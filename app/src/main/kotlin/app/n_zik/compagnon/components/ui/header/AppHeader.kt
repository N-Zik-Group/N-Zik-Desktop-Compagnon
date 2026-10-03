package app.n_zik.compagnon.components.ui.header

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.n_zik.compagnon.bridge.ConnectionState
import app.n_zik.compagnon.colorPalette
import app.n_zik.compagnon.components.navigation.header.ActionBar

/**
 * Port of `AppHeader.Draw` (phone's `app/it/fast4x/rimusic/ui/components/navigation/header/AppHeader.kt`) on
 * the home: 64 dp on `background0`, 12 dp before the logo and 4 dp after the actions, the
 * [CollapsingAppTitle] taking the remaining width, then the [ActionBar] (connection and "Phone").
 * Dropped: the back button (the opened playlist / album / artist has its own, ported with its screen), the
 * voice-search overlay and the scroll-hide offset (phone features), the system bar insets.
 */
@Composable
fun AppHeader(connection: ConnectionState, onPhone: () -> Unit) {
    val themeBackground = colorPalette().background0
    // isHome
    val startPadding = 12.dp

    Row(
        modifier = Modifier
            .background(themeBackground)
            .fillMaxWidth()
            .height(64.dp)
            .padding(start = startPadding, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Restore TopAppBar's default content coloring behavior for action buttons
        CompositionLocalProvider(
            LocalContentColor provides colorPalette().text,
        ) {
            // Logo + Title, taking the remaining width
            CollapsingAppTitle(Modifier.weight(1f))

            // Action icons
            ActionBar(connection, onPhone)
        }
    }
}
