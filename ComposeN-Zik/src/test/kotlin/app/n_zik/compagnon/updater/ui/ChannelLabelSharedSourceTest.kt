package app.n_zik.compagnon.updater.ui

import java.io.File
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

/**
 * Pins the single shared source of the channel label (spec `spec-updater-parity-fixes` — the
 * drift this spec fixes): both call sites render it through the shared `channelLabel` in
 * `ChannelLabel.kt`, so neither may declare its own local copy (a `fun` / `val` named
 * `channelLabel`) — re-adding one would drift the dialog's label from the page's (the missing
 * `debug` branch, again) with every behavior test green. The source-text idiom is
 * `InstallerContractTest`'s (the tests' working dir is the `ComposeN-Zik/` module dir, so the
 * files are read relative to it).
 */
class ChannelLabelSharedSourceTest {

    private val callSites = listOf(
        File("src/main/kotlin/app/n_zik/compagnon/components/ui/screens/settings/UpdateScreen.kt"),
        File("src/main/kotlin/app/n_zik/compagnon/components/dialog/updater/NewUpdateAvailableDialog.kt"),
    )

    @Test
    fun `the call sites declare no local channel label copy`() {
        // A local declaration is a `fun` / `val` named channelLabel (with any modifier); a call
        // to the shared source is a bare `channelLabel(...)` reference and must NOT match
        for (file in callSites) {
            val localDeclaration = file.readText().lineSequence()
                .map { it.trimStart() }
                .filterNot { it.startsWith("//") || it.startsWith("/*") || it.startsWith("*") }
                .any { it.matches(Regex("(?:private |internal )?(?:fun|val) channelLabel\\b")) }
            assertFalse(
                localDeclaration,
                "$file declares a local channelLabel copy — the shared source is ChannelLabel.kt (spec `spec-updater-parity-fixes`)",
            )
        }
    }
}
