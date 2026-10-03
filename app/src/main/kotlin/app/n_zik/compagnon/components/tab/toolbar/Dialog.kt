package app.n_zik.compagnon.components.tab.toolbar

import androidx.compose.runtime.Composable

/** Port of the phone's `app/it/fast4x/rimusic/ui/components/tab/toolbar/Dialog.kt`. */
interface Dialog {

    var isActive: Boolean

    @get:Composable
    val dialogTitle: String

    @Composable
    fun Render()

    /** By default, a tap on the icon opens the dialog. */
    fun onShortClick() { isActive = true }
}
