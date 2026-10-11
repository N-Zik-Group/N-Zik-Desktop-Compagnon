package app.n_zik.compagnon.components.styling

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.n_zik.compagnon.LocalPlayerRepository
import app.n_zik.compagnon.enums.NavigationBarType
import app.n_zik.compagnon.utils.LocalPreferences
import app.n_zik.compagnon.utils.UserSettings

/**
 * Port of the phone's `app/it/fast4x/rimusic/ui/styling/Dimensions.kt`. Only the dimensions the
 * Compagnon uses are kept; [bottomSpacer] is position- and type-aware as on the phone (its `LocalPlayerServiceBinder`
 * mini-player check reads the remote player state here).
 */
@Suppress("ClassName")
object Dimensions {
    val itemsVerticalPadding = 8.dp

    val headerHeight = 140.dp

    /** The phone's `navigationRailWidth` (the vertical bar's width). */
    val navigationRailWidth = 50.dp

    /** The phone's `navigationRailIconOffset` (the vertical bar's dot size is ×3 of it). */
    val navigationRailIconOffset = 6.dp

    /** The phone's `standardNavBarHeight`: the mini-player's bottom padding with the fixed bottom bar. */
    val standardNavBarHeight = 64.dp

    /** The phone's `navigationBarHeight` (the fixed bar's height before its 10 dp cut). */
    val navigationBarHeight = 64.dp

    /** The phone's `floatingNavBarHeight` (the floating bar with labels). */
    val floatingNavBarHeight = 84.dp

    val floatingNavBarIconOnlyHeight = 64.dp

    /**
     * `navBarBottomPadding(isFloating = true)`: `(systemBottom + 5.dp).coerceAtLeast(25.dp)`. A desktop
     * window has no system bar (`systemBottom` = 0), hence 25 dp. The non-floating bar's padding is
     * `systemBottom` itself — 0 here.
     */
    val navBarBottomPadding: Dp = 25.dp

    /** The phone's `additionalVerticalSpaceForFloatingAction` (the FAB's extra space over a bottom bar). */
    val additionalVerticalSpaceForFloatingAction = 40.dp

    /** `BottomFloating`: 72 dp. */
    val miniPlayerHeight: Dp = 72.dp

    /** `BottomFloating`: 72 dp. */
    val collapsedPlayer: Dp = 72.dp

    /**
     * The phone's `bottomSpacer` ([bottomSpacerDp]): with the floating bar,
     * `barHeight + barBottom + visualGap`, plus the mini-player while a track is current; with a
     * fixed bar, a flat 100 dp (all positions).
     */
    val bottomSpacer: Dp
        @Composable
        get() {
            val settings = LocalPreferences.current?.settings?.collectAsState()?.value ?: UserSettings()
            val isFloating = settings.navigationBarPositionValue.isFloating
            val isIconOnly = settings.navigationBarTypeValue == NavigationBarType.IconOnly
            val isMiniPlayerActive = LocalPlayerRepository.current?.state?.collectAsState()?.value?.currentTrack != null
            return bottomSpacerDp(isFloating, isIconOnly, isMiniPlayerActive)
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

/**
 * The phone's `bottomSpacer` formula (the [Dimensions.bottomSpacer] branch table, without its
 * state reads): with the floating bar, `barHeight + barBottom + visualGap` — the icon-only bar
 * height or the one with labels — plus the mini-player's 72 dp while a track is current; with a
 * fixed bar (any position), a flat 100 dp.
 */
internal fun bottomSpacerDp(isFloating: Boolean, isIconOnly: Boolean, miniPlayerActive: Boolean): Dp {
    return if (isFloating) {
        val barHeight = if (isIconOnly) Dimensions.floatingNavBarIconOnlyHeight else Dimensions.floatingNavBarHeight
        val barBottom = Dimensions.navBarBottomPadding
        val visualGap = 10.dp
        if (miniPlayerActive) {
            barHeight + barBottom + Dimensions.miniPlayerHeight + visualGap
        } else {
            barHeight + barBottom + visualGap
        }
    } else 100.dp
}

/** `HomeItemSize.SMALL` (the phone's default grid cell, `enums/HomeItemSize.kt`). */
val HOME_ITEM_SIZE_SMALL: Dp = 100.dp
