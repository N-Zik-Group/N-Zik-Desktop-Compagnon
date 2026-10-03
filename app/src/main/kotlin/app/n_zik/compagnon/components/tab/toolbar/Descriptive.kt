package app.n_zik.compagnon.components.tab.toolbar

import app.n_zik.compagnon.utils.Toaster
import org.jetbrains.compose.resources.StringResource

/**
 * Port of the phone's `app/it/fast4x/rimusic/ui/components/tab/toolbar/Descriptive.kt`: a long press (a right
 * click on the PC) tells what the button does.
 */
interface Descriptive : Icon {

    val messageId: StringResource

    override fun onLongClick() = Toaster.i(messageId)
}
