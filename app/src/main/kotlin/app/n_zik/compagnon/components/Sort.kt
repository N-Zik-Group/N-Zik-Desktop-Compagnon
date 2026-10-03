package app.n_zik.compagnon.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import app.n_zik.compagnon.bridge.library.SongSort
import app.n_zik.compagnon.components.menu.ListMenu
import app.n_zik.compagnon.components.navigation.header.TabToolBar
import app.n_zik.compagnon.components.tab.toolbar.MenuIcon
import app.n_zik.compagnon.colorPalette
import app.n_zik.compagnon.uiRoundnessShape
import app.n_zik.compagnon.generated.resources.Res
import app.n_zik.compagnon.generated.resources.arrow_up
import app.n_zik.compagnon.generated.resources.artist
import app.n_zik.compagnon.generated.resources.sort_artist
import app.n_zik.compagnon.generated.resources.sort_listening_time
import app.n_zik.compagnon.generated.resources.sort_title
import app.n_zik.compagnon.generated.resources.sorting_order
import app.n_zik.compagnon.generated.resources.text
import app.n_zik.compagnon.generated.resources.trending
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** Label and icon of a sort, those of the phone's `SongSortBy` (`enums/SongSortBy.kt`). */
private val SongSort.textId: StringResource
    get() = when (this) {
        SongSort.Title -> Res.string.sort_title
        SongSort.Artist -> Res.string.sort_artist
        SongSort.PlayTime -> Res.string.sort_listening_time
    }

private val SongSort.iconId: DrawableResource
    get() = when (this) {
        SongSort.Title -> Res.drawable.text
        SongSort.Artist -> Res.drawable.artist
        SongSort.PlayTime -> Res.drawable.trending
    }

/**
 * The phone's `SortOrder.rotationZ`: contract §10.1 fixes the order of each sort (title and artist A→Z,
 * listening time descending), so the arrow shows it.
 */
private val SongSort.rotationZ: Float
    get() = if (this == SongSort.PlayTime) 180f else 0f

/**
 * Port of `Sort` (phone's `app/n_zik/android/components/Sort.kt` 52) for the Songs tab, reduced to the
 * sorts of contract v1 (`title`, `artist`, `playTime`; the others of `SongSortBy` do not exist there).
 * The order cannot be chosen (the contract fixes it): the phone's short click (toggle the order) opens
 * the sort menu like its long click.
 */
class Sort(
    private val menuState: MenuState,
    private val sortBy: SongSort,
    private val onSortBy: (SongSort) -> Unit,
) : MenuIcon {

    override val iconId: DrawableResource = Res.drawable.arrow_up
    override val menuIconTitle: String
        @Composable
        get() = stringResource(Res.string.sorting_order)

    override fun onShortClick() = openMenu()
    override fun onLongClick() = openMenu()

    private fun openMenu() = menuState.display { ListMenu() }

    @Composable
    fun ListMenu() {
        ListMenu.Menu(title = menuIconTitle) {
            SongSort.entries.forEach {
                val isSelected = it == sortBy
                ListMenu.Entry(
                    text = stringResource(it.textId),
                    icon = {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(
                                    color = if (isSelected) colorPalette().accent.copy(alpha = 0.2f) else colorPalette().accent.copy(alpha = 0.1f),
                                    shape = uiRoundnessShape(),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painter = painterResource(it.iconId),
                                contentDescription = it.name,
                                tint = colorPalette().accent,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    },
                    modifier = if (isSelected) Modifier.background(colorPalette().accent.copy(alpha = 0.1f), uiRoundnessShape()) else Modifier,
                    trailingContent = {
                        AnimatedVisibility(
                            visible = isSelected,
                            enter = fadeIn() + scaleIn(),
                            exit = fadeOut() + scaleOut(),
                        ) {
                            RadioButton(
                                selected = true,
                                onClick = null,
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = colorPalette().accent,
                                    unselectedColor = colorPalette().textSecondary,
                                ),
                            )
                        }
                    },
                    onClick = {
                        menuState.hide()
                        onSortBy(it)
                    },
                )
            }
        }
    }

    @Composable
    override fun ToolBarButton() {
        val animatedArrow by animateFloatAsState(
            targetValue = sortBy.rotationZ,
            animationSpec = tween(durationMillis = 400, easing = LinearEasing),
            label = "",
        )

        TabToolBar.Icon(
            painterResource(iconId),
            color,
            sizeDp,
            isEnabled,
            this.modifier.graphicsLayer { rotationZ = animatedArrow },
            this::onShortClick,
            this::onLongClick,
        )
    }
}
