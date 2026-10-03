package app.n_zik.compagnon.components.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.n_zik.compagnon.colorPalette
import app.n_zik.compagnon.components.theme.ModernBlackColorPalette
import app.n_zik.compagnon.components.theme.PureBlackColorPalette
import app.n_zik.compagnon.typography
import app.n_zik.compagnon.uiRoundnessShape
import app.n_zik.compagnon.utils.semiBold
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/**
 * Port of `SettingsSectionCard` (phone's `app/it/fast4x/rimusic/ui/screens/settings/SettingsScreen.kt` 749-841):
 * a card (16 dp from the sides, 4 dp shadow tinted with the accent, `background1`, or dark gray on the black
 * palettes) with its header (32 dp accent badge holding an 18 dp icon, 12 dp, the accent title in
 * xs.semiBold), the optional description (xxs, `textSecondary`), the content, then 16 dp of space; it
 * appears and disappears with its expand / fade animation ([visible]).
 * The phone's `ColorPaletteMode.PitchBlack` check is not needed: the Compagnon always uses `Dark`.
 */
@Composable
fun SettingsSectionCard(
    title: String,
    icon: DrawableResource,
    content: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    visible: Boolean = true,
) {
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically(animationSpec = tween(400)) + fadeIn(animationSpec = tween(400)),
        exit = shrinkVertically(animationSpec = tween(200)) + fadeOut(animationSpec = tween(200)),
    ) {
        Column(modifier = modifier.fillMaxWidth()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .shadow(
                        elevation = 4.dp,
                        shape = uiRoundnessShape(),
                        spotColor = colorPalette().accent.copy(alpha = 0.2f),
                    ),
                shape = uiRoundnessShape(),
                colors = CardDefaults.cardColors(
                    containerColor = if (colorPalette() === PureBlackColorPalette || colorPalette() === ModernBlackColorPalette) {
                        Color(0xFF1A1A1A) // Gray dark for pitch black themes
                    } else {
                        colorPalette().background1
                    },
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                ) {
                    // Section Header
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 12.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(
                                    color = colorPalette().accent.copy(alpha = 0.1f),
                                    shape = uiRoundnessShape(),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painter = painterResource(icon),
                                tint = colorPalette().accent,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        BasicText(
                            text = title,
                            style = typography().xs.semiBold.copy(
                                color = colorPalette().accent,
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    if (description != null) {
                        BasicText(
                            text = description,
                            style = typography().xxs.copy(
                                color = colorPalette().textSecondary,
                            ),
                            modifier = Modifier.padding(bottom = 16.dp),
                        )
                    }

                    // Content
                    content()
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
