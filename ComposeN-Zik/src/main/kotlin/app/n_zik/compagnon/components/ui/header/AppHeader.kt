package app.n_zik.compagnon.components.ui.header

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import app.n_zik.compagnon.colorPalette
import app.n_zik.compagnon.components.navigation.header.ActionBar
import app.n_zik.compagnon.components.theme.favoritesIcon
import app.n_zik.compagnon.components.themed.Button
import app.n_zik.compagnon.generated.resources.*
import app.n_zik.compagnon.uiRoundnessShape

/**
 * Port of `AppHeader.Draw` (phone's `app/it/fast4x/rimusic/ui/components/navigation/header/AppHeader.kt`
 * 75-181): 64 dp on `background0`, the start padding animated from 12 dp (home) to 4 dp (a page) in 200 ms,
 * 4 dp after the actions; the back button (48 dp box in the UI roundness, `chevron_back` 24 dp in
 * `favoritesIcon`) slides, fades and expands in over 220 ms when a page is open ([isHome] `false`) and out
 * over 180 ms, pushing the [CollapsingAppTitle] (which takes the remaining width, its title going home
 * through [onHome]); then the [ActionBar] (search, profile face and its menu: "PC server", "Settings").
 * The scroll-hide offset (`LocalTopBarOffset`) is applied by the window, which also lays out the content
 * under the moving header. Dropped: the voice-search overlay (no search route) and the system bar insets.
 */
@Composable
fun AppHeader(
    isHome: Boolean,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onPhone: () -> Unit,
    onSettings: () -> Unit,
) {
    val themeBackground = colorPalette().background0

    // Animate the start padding smoothly so the logo has nice spacing when home,
    // and the back button aligns correctly when present.
    val startPadding by animateDpAsState(
        targetValue = if (isHome) 12.dp else 4.dp,
        animationSpec = tween(200),
    )

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
            // Back button — animates in from the left, pushes title smoothly
            AnimatedVisibility(
                visible = !isHome,
                enter = fadeIn(animationSpec = tween(220)) +
                    slideInHorizontally(animationSpec = tween(220)) { -it } +
                    expandHorizontally(animationSpec = tween(220)),
                exit = fadeOut(animationSpec = tween(180)) +
                    slideOutHorizontally(animationSpec = tween(180)) { -it } +
                    shrinkHorizontally(animationSpec = tween(180)),
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(uiRoundnessShape())
                        .clickable { onBack() },
                    contentAlignment = Alignment.Center,
                ) {
                    Button(
                        Res.drawable.chevron_back,
                        colorPalette().favoritesIcon,
                        0.dp,
                        24.dp,
                    )
                }
            }

            // Logo + Title, taking the remaining width
            CollapsingAppTitle(onHome = onHome, modifier = Modifier.weight(1f))

            // Action icons
            ActionBar(onPhone, onSettings)
        }
    }
}
