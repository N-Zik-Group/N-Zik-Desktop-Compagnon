package app.n_zik.compagnon.components.dialog.common

import java.io.File
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Pins the shared dialog framework (spec `spec-dialog-framework-port`, issue #896): every PC
 * dialog surface renders through the single shared [DialogFrame] (one card chrome — the pitch
 * black branch, the 8 dp elevation, the 560 / 420 dp width cap + the 80 % height cap — with no
 * per-dialog card re-declaration) and the shared [ButtonRow] (one pill language, the phone's
 * roles); the gen-1 `DialogTextButton` is gone; the two updater dialogs no longer re-declare
 * their off-pattern 420 dp / elevation-4 chrome.
 *
 * The source-text idiom is [app.n_zik.compagnon.updater.ui.ChannelLabelSharedSourceTest]'s (the
 * tests' working dir is the `ComposeN-Zik/` module dir, so the files are read relative to it).
 * A green build alone cannot prove the parity: a dialog that re-declares its own card would
 * drift from the frame (the elevation, the pitch black branch, the width) with every behavior
 * test green.
 */
class DialogFrameworkSharedSourceTest {

    /** The 6 migrated dialog surfaces (spec `spec-dialog-framework-port`). */
    private val dialogFiles = listOf(
        File("src/main/kotlin/app/n_zik/compagnon/components/themed/Dialog.kt"),
        File("src/main/kotlin/app/n_zik/compagnon/components/dialog/updater/NewUpdateAvailableDialog.kt"),
        File("src/main/kotlin/app/n_zik/compagnon/components/dialog/updater/CheckForUpdateDialog.kt"),
        File("src/main/kotlin/app/n_zik/compagnon/components/dialog/common/CheckboxDialog.kt"),
        File("src/main/kotlin/app/n_zik/compagnon/components/ui/screens/settings/AppShortcutsDialog.kt"),
        File("src/main/kotlin/app/n_zik/compagnon/components/themed/MiniPlayerButtonsSettingsDialog.kt"),
    )

    @Test
    fun `every dialog surface renders through the shared dialog frame`() {
        for (file in dialogFiles) {
            assertTrue(
                file.readText().contains("DialogFrame("),
                "$file must render through the shared DialogFrame (the single source of the dialog card chrome)",
            )
        }
    }

    @Test
    fun `no dialog file re-declares its own frame card`() {
        for (file in dialogFiles) {
            val text = file.readText()
            assertFalse(
                text.contains("import androidx.compose.material3.Card"),
                "$file imports material3 Card — the frame is the single card chrome",
            )
            assertFalse(
                text.contains("CardDefaults.cardElevation"),
                "$file re-declares its own Card elevation — the frame is the single card chrome",
            )
        }
    }

    @Test
    fun `no dialog file declares its own raw dialog window`() {
        for (file in dialogFiles) {
            val text = file.readText()
            assertFalse(
                text.contains("import androidx.compose.ui.window.Dialog"),
                "$file opens its own raw Dialog window — the shared DialogFrame owns the window",
            )
            assertFalse(
                text.contains("import androidx.compose.ui.window.DialogProperties"),
                "$file imports DialogProperties for its own window — the shared DialogFrame owns the window",
            )
        }
    }

    @Test
    fun `the button row carries the continuous marquee contract`() {
        val buttonRow = File("src/main/kotlin/app/n_zik/compagnon/components/dialog/common/ButtonRow.kt")
        assertTrue(
            buttonRow.readText().contains(".basicMarquee(iterations = Int.MAX_VALUE)"),
            "ButtonRow.kt must keep the #896 contract: a pill label wider than its cell stays on one line and scrolls (continuous marquee)",
        )
    }

    @Test
    fun `the gen-1 dialog text button is gone`() {
        assertFalse(
            File("src/main/kotlin/app/n_zik/compagnon/components/themed/DialogTextButton.kt").exists(),
            "DialogTextButton.kt must be deleted — ButtonRow is the single source of every dialog button",
        )
    }

    @Test
    fun `the updater dialogs no longer re-declare the off-pattern chrome`() {
        val updaterFiles = listOf(
            File("src/main/kotlin/app/n_zik/compagnon/components/dialog/updater/NewUpdateAvailableDialog.kt"),
            File("src/main/kotlin/app/n_zik/compagnon/components/dialog/updater/CheckForUpdateDialog.kt"),
        )
        for (file in updaterFiles) {
            val text = file.readText()
            assertFalse(
                text.contains("widthIn(max = 4"),
                "$file re-declares the 420 dp chrome — the frame carries the width cap (maxWidth = 420.dp)",
            )
            assertFalse(
                text.contains("defaultElevation = 4"),
                "$file re-declares the elevation-4 cards — the frame carries the card chrome (8 dp elevation)",
            )
        }
    }
}
