package app.n_zik.compagnon.updater

import java.io.File
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The "Go to download" wiring (the new-update dialog's 1:1 phone port, parity round 2): the
 * dialog button dismisses the dialog and raises the one-shot
 * [NewUpdateAvailableDialog.GoToDownloadRequest] flag, and `MainActivity` consumes it (resets
 * the flag, opens the update page over the current content). The dialog sits above the
 * navigation, so the wiring is a flag + a snapshotFlow — no behavior test observes it without
 * the full navigation, so the source pin (the `UpdateDialogsPairingGateTest` idiom: the tests'
 * working dir is the `ComposeN-Zik/` module dir) keeps BOTH halves: dropping the flag set or
 * the consumer would leave the dialog dismissing WITHOUT opening the update page (the user
 * stuck with "no download, no page"), with every behavior test green.
 */
class GoToDownloadWiringTest {

    private fun sourceOf(relativePath: String): String {
        val sourceFile = File(relativePath)
        if (!sourceFile.isFile) error("the source pin needs $sourceFile — run the tests with the ComposeN-Zik module dir as the working directory (cwd=${File(".").absolutePath})")
        return sourceFile.readText()
    }

    @Test
    fun `the dialog button raises the one-shot go-to-download flag`() {
        val source = sourceOf("src/main/kotlin/app/n_zik/compagnon/components/dialog/updater/NewUpdateAvailableDialog.kt")
        assertTrue(
            source.contains("GoToDownloadRequest.requested = true"),
            "the 'Go to download' button must set the one-shot flag (after dismissing the dialog)",
        )
    }

    @Test
    fun `main consumes the flag and opens the update page`() {
        val source = sourceOf("src/main/kotlin/app/n_zik/compagnon/MainActivity.kt")
        assertTrue(
            source.contains("snapshotFlow { NewUpdateAvailableDialog.GoToDownloadRequest.requested }"),
            "MainActivity must consume the one-shot flag through the snapshotFlow",
        )
        assertTrue(
            source.contains("NewUpdateAvailableDialog.GoToDownloadRequest.requested = false"),
            "the flag must be reset (one-shot: a later open of the dialog must not re-fire)",
        )
        assertTrue(
            source.contains("pages = pages.openUpdate()"),
            "consuming the flag must open the update page",
        )
    }
}
