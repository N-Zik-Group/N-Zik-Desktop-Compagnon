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
import app.n_zik.compagnon.components.tab.toolbar.MenuIcon
import app.n_zik.compagnon.colorPalette
import app.n_zik.compagnon.uiRoundnessShape
import app.n_zik.compagnon.generated.resources.Res
import app.n_zik.compagnon.generated.resources._1_month
import app.n_zik.compagnon.generated.resources._1_week
import app.n_zik.compagnon.generated.resources._1_year
import app.n_zik.compagnon.generated.resources._3_month
import app.n_zik.compagnon.generated.resources._6_month
import app.n_zik.compagnon.generated.resources.all
import app.n_zik.compagnon.generated.resources.calendar_clear
import app.n_zik.compagnon.generated.resources.header_view_top_of
import app.n_zik.compagnon.generated.resources.stat_3months
import app.n_zik.compagnon.generated.resources.stat_6months
import app.n_zik.compagnon.generated.resources.stat_month
import app.n_zik.compagnon.generated.resources.stat_today
import app.n_zik.compagnon.generated.resources.stat_week
import app.n_zik.compagnon.generated.resources.stat_year
import app.n_zik.compagnon.generated.resources.today
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
 * 1.6: the chosen period travels as `period` of `/library/songs?filter=top`), its "Top N of ." title
 * (the phone's `header_view_top_of`, with the phone's default max items — its setting is phone-side
 * only, the PC cannot read it) and no selected highlight, as on the phone. [period] `null` keeps the
 * phone's own period (its default is `All`) and shows the `calendar_clear` icon. A click opens the menu
 * (the phone's selector has no direction arrow). Since 1.7.3 (feature `library.sortMenu`),
 * [options] follows the phone's Top tab menu — its periods in its order, its hidden ones dropped;
 * the native order stands in without it.
 */
class PeriodSelector(
    private val menuState: MenuState,
    private val period: TopPeriod?,
    /** Since 1.7.3 (feature `library.sortMenu`): the phone's Top tab menu — its periods, its order. */
    private val options: List<TopPeriod> = TopPeriod.entries,
    private val onPeriodSelected: (TopPeriod) -> Unit,
) : MenuIcon {

    override val iconId: DrawableResource = period?.iconId ?: Res.drawable.calendar_clear

    override val menuIconTitle: String
        @Composable
        get() = formatText(stringResource(Res.string.header_view_top_of), "10")

    override fun onShortClick() = openMenu()

    private fun openMenu() = menuState.display { ListMenu() }

    @Composable
    fun ListMenu() {
        ListMenu.Menu(title = menuIconTitle) {
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
