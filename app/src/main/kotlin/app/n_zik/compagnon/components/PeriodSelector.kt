package app.n_zik.compagnon.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.n_zik.compagnon.bridge.library.TopPeriod
import app.n_zik.compagnon.components.menu.ListMenu
import app.n_zik.compagnon.bridge.state.LocalUiSettings
import app.n_zik.compagnon.components.tab.toolbar.Descriptive
import app.n_zik.compagnon.components.tab.toolbar.MenuIcon
import app.n_zik.compagnon.colorPalette
import app.n_zik.compagnon.uiRoundnessShape
import app.n_zik.compagnon.generated.resources.*
import app.n_zik.compagnon.utils.formatText
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** The icon of one `TopPeriod` (the phone's `StatisticsType` icons). */
val TopPeriod.iconId: DrawableResource
    get() = when (this) {
        TopPeriod.Today -> Res.drawable.stat_today
        TopPeriod.Week -> Res.drawable.stat_week
        TopPeriod.Month -> Res.drawable.stat_month
        TopPeriod.ThreeMonths -> Res.drawable.stat_3months
        TopPeriod.SixMonths -> Res.drawable.stat_6months
        TopPeriod.Year -> Res.drawable.stat_year
        TopPeriod.AllTime -> Res.drawable.calendar_clear
    }

/** The label of one `TopPeriod` (the phone's `StatisticsType` texts). */
val TopPeriod.labelId: StringResource
    get() = when (this) {
        TopPeriod.Today -> Res.string.today
        TopPeriod.Week -> Res.string._1_week
        TopPeriod.Month -> Res.string._1_month
        TopPeriod.ThreeMonths -> Res.string._3_month
        TopPeriod.SixMonths -> Res.string._6_month
        TopPeriod.Year -> Res.string._1_year
        TopPeriod.AllTime -> Res.string.all
    }

/**
 * Port of the phone's `PeriodSelector` (`app/n_zik/android/components/song/PeriodSelector.kt`): the sort
 * slot of the Songs "Top" chip — a menu of the phone's `StatisticsType` periods (contract §10, since
 * 1.6: the chosen period travels as `period` of `/library/songs?filter=top`), the button titled
 * `statistics` (its `Descriptive` message, shown on a right click), the menu titled "Top N of …" (the
 * phone's `header_view_top_of` with its `MaxTopPlaylistItems`, read through `ui.settings` since 1.10.0)
 * and no selected highlight, as on the phone. [period] `null` keeps the
 * phone's own period (its default is `All`) and shows the `calendar_clear` icon. A click opens the menu
 * (the phone's selector has no direction arrow). [options] is the phone's static periods in their
 * native order (the wire's `sortMenu` is no longer consumed, since the "remove UI sync" spec).
 */
class PeriodSelector(
    private val menuState: MenuState,
    private val period: TopPeriod?,
    /** The phone's Top tab periods, in their native order (static since the "remove UI sync" spec). */
    private val options: List<TopPeriod> = TopPeriod.entries,
    private val onPeriodSelected: (TopPeriod) -> Unit,
) : MenuIcon, Descriptive {

    override val iconId: DrawableResource = period?.iconId ?: Res.drawable.calendar_clear

    /** The phone's button title and `Descriptive` message (`PeriodSelector.kt` 72): `statistics`. */
    override val messageId: StringResource = Res.string.statistics
    override val menuIconTitle: String
        @Composable
        get() = stringResource(messageId)

    /**
     * The menu title, the phone's "Top N of …" (`PeriodSelector.kt` 169-176) with its `MaxTopPlaylistItems`
     * setting — read from the phone since contract 1.10.0 (`ui.settings`, `topN`, `null` = unlimited); the
     * phone's default (10) without it.
     */
    @Composable
    private fun menuTitle(): String {
        val topN = LocalUiSettings.current.topN
        val label = topN?.toString() ?: stringResource(Res.string.max_items_unlimited)
        return formatText(stringResource(Res.string.header_view_top_of), label)
    }

    override fun onShortClick() = openMenu()

    private fun openMenu() = menuState.display { ListMenu() }

    @Composable
    fun ListMenu() {
        ListMenu.Menu(title = menuTitle()) {
            options.forEach { entry ->
                ListMenu.Entry(
                    text = stringResource(entry.labelId),
                    icon = {
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
                                painter = painterResource(entry.iconId),
                                contentDescription = stringResource(entry.labelId),
                                tint = colorPalette().accent,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    },
                    onClick = {
                        menuState.hide()
                        onPeriodSelected(entry)
                    },
                )
            }
        }
    }
}
