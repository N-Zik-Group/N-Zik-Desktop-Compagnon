package app.n_zik.compagnon.components.tab.toolbar

import androidx.compose.runtime.Composable
import app.n_zik.compagnon.components.themed.ConfirmationDialog

/** Port of the phone's `app/it/fast4x/rimusic/ui/components/tab/toolbar/ConfirmDialog.kt`. */
interface ConfirmDialog : Dialog {

    /** What happens when the user hits "Confirm". */
    fun onConfirm()

    /** A click outside the dialog (or Escape) closes it. */
    fun onDismiss() { isActive = false }

    @Composable
    override fun Render() {
        if (!isActive) return

        ConfirmationDialog(
            text = dialogTitle,
            onDismiss = ::onDismiss,
            onConfirm = ::onConfirm,
        )
    }
}
