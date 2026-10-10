package app.n_zik.compagnon.components.tab.toolbar

import androidx.compose.runtime.Composable
import app.n_zik.compagnon.components.navigation.header.TabToolBar
import app.n_zik.compagnon.generated.resources.*
import kotlin.random.Random
import org.jetbrains.compose.resources.painterResource

/**
 * Port of the phone's `Randomizer` (`app/it/fast4x/rimusic/ui/components/tab/toolbar/Randomizer.kt`): the dice
 * of the Albums / Artists toolbars — enabled while the list shows items, a click opens one of them at random
 * (client-side, on the items the list shows, as the phone picks among its displayed items). Not
 * `Descriptive` and not a `MenuIcon`, as on the phone (no help, never in the overflow menu).
 */
class Randomizer<T>(
    private val getItems: () -> List<T>,
    private val onClick: (T) -> Unit,
) : Button {

    @Composable
    override fun ToolBarButton() {
        val items = getItems()
        TabToolBar.Icon(
            icon = painterResource(Res.drawable.dice),
            enabled = items.isNotEmpty(),
        ) {
            val current = getItems()
            // The phone's `Random(System.currentTimeMillis())`, a fresh seed per click
            if (current.isNotEmpty()) onClick(current[Random(System.currentTimeMillis()).nextInt(current.size)])
        }
    }
}
