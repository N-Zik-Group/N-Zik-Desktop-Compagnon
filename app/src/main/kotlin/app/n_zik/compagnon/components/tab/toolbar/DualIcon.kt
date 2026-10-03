package app.n_zik.compagnon.components.tab.toolbar

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/**
 * Port of the phone's `app/it/fast4x/rimusic/ui/components/tab/toolbar/DualIcon.kt`: an icon showing
 * [iconId] or [secondIconId] depending on [isFirstIcon].
 */
interface DualIcon : Icon {

    val secondIconId: DrawableResource

    /** When `true`, [iconId] is shown, otherwise [secondIconId]. */
    var isFirstIcon: Boolean

    override val icon: Painter
        @Composable
        get() = if (isFirstIcon) super.icon else painterResource(secondIconId)
}
