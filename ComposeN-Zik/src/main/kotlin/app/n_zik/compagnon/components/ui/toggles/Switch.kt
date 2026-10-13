package app.n_zik.compagnon.components.ui.toggles

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.n_zik.compagnon.colorPalette
import app.n_zik.compagnon.exactUiRoundnessShape

/**
 * Port of the phone's themed switch (phone's `app/n_zik/android/components/ui/toggles/Switch.kt`): a
 * 52 × 32 dp track in `exactUiRoundnessShape` — `accent` over `onAccent` when on, `textSecondary`
 * (track at 30 % alpha) when off — with the thumb growing 16 → 24 dp on a 150 ms tween, instead of
 * material3's default switch.
 */
@Composable
fun Switch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    checkedThumbColor: Color = colorPalette().onAccent,
    checkedTrackColor: Color = colorPalette().accent,
    uncheckedThumbColor: Color = colorPalette().textSecondary,
    uncheckedTrackColor: Color = colorPalette().textSecondary.copy(alpha = 0.3f),
    enabled: Boolean = true,
) {
    val trackColor by animateColorAsState(if (checked) checkedTrackColor else uncheckedTrackColor)
    val thumbColor by animateColorAsState(if (checked) checkedThumbColor else uncheckedThumbColor)

    // The M3 switch track is 52 dp wide, 32 dp high
    val thumbSize by animateDpAsState(if (checked) 24.dp else 16.dp, animationSpec = tween(150))

    // If the thumb is 24 dp, its margin is 4 dp: the x offset ranges from 4 dp to 52-24-4 = 24 dp.
    // If it is 16 dp, its margin is 8 dp: the x offset ranges from 8 dp to 52-16-8 = 28 dp
    val thumbOffset by animateDpAsState(if (checked) 24.dp else 8.dp, animationSpec = tween(150))

    Box(
        modifier = modifier
            .size(width = 52.dp, height = 32.dp)
            .background(trackColor, shape = exactUiRoundnessShape())
            .clip(exactUiRoundnessShape())
            .then(
                if (onCheckedChange != null) {
                    Modifier.clickable(enabled = enabled) {
                        onCheckedChange.invoke(!checked)
                    }
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .size(thumbSize)
                .background(thumbColor, shape = exactUiRoundnessShape()),
        )
    }
}
