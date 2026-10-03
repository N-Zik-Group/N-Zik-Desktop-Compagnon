package app.n_zik.compagnon.components.styling

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.n_zik.compagnon.LocalPlayerRepository

/**
 * Port of the phone's `app/it/fast4x/rimusic/ui/styling/Dimensions.kt`, with the values of its default
 * preferences (navigation bar `BottomFloating`, `IconOnly`). Only the dimensions the Compagnon uses are kept.
 */
@Suppress("ClassName")
object Dimensions {
    val itemsVerticalPadding = 8.dp

    val headerHeight = 140.dp

    val floatingNavBarIconOnlyHeight = 64.dp

    /**
     * `navBarBottomPadding(isFloating = true)`: `(systemBottom + 5.dp).coerceAtLeast(25.dp)`. A desktop
     * window has no system bar (`systemBottom` = 0), hence 25 dp.
     */
    val navBarBottomPadding: Dp = 25.dp

    /** `BottomFloating`: 72 dp. */
    val miniPlayerHeight: Dp = 72.dp

    /** `BottomFloating`: 72 dp. */
    val collapsedPlayer: Dp = 72.dp

    /**
     * `bottomSpacer` for the floating bar: `barHeight + barBottom + visualGap`, plus the mini-player while a
     * track is current (the phone's `binder.player.currentMediaItem != null`).
     */
    val bottomSpacer: Dp
        @Composable
        get() {
            val isMiniPlayerActive = LocalPlayerRepository.current?.state?.collectAsState()?.value?.currentTrack != null
            val barHeight = floatingNavBarIconOnlyHeight
            val barBottom = navBarBottomPadding
            val visualGap = 10.dp
            return if (isMiniPlayerActive) {
                barHeight + barBottom + miniPlayerHeight + visualGap
            } else {
                barHeight + barBottom + visualGap
            }
        }

    val fadeSpacingTop = 30.dp
    val fadeSpacingBottom = 65.dp

    object thumbnails {
        val album = 128.dp
        val artist = 128.dp
        val song = 54.dp
        val playlist = album
    }
}

/** `HomeItemSize.SMALL` (the phone's default grid cell, `enums/HomeItemSize.kt`). */
val HOME_ITEM_SIZE_SMALL: Dp = 100.dp
