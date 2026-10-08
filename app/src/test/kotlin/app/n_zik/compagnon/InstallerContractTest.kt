package app.n_zik.compagnon

import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The Windows installer contract is pure NSIS text + a PowerShell one-liner: a makensis script
 * that changes the registry identity, drops an exit code, or stops relaying the elevated child's
 * code would desync the in-app updater helper (which interprets exactly those codes) with a green
 * build — and no other test path reads the .nsi. This test parses the script (and the helper's
 * source) directly, the same idiom as [LinuxPackagePinTest] (the tests' working dir is the `app/`
 * module dir, so the files are read relative to it).
 *
 * Contract (the NSIS header documents it): the registry product-info key is
 * `Software\N-Zik\DesktopCompagnon\${CHANNEL}` (per-user under HKCU, global under HKLM — the
 * identity is per channel x per scope); the emitted exit codes are
 *   0 = success; 1 = user cancelled / not done yet (incl. the running-app guard); 2 = remove
 *   chosen (the install is gone by design); 3 = install failure; 4 = elevated child vanished
 *   (death detected via the PID file + OpenProcess + GetExitCodeProcess, or no PID file within
 *   the grace); 1223 = UAC declined.
 * The helper in `UpdateDownloadManager.windowsInstallScript` names 0 / 1 / 2 / 1223 explicitly
 * and writes the failure marker for ANY other code — so the pins are: the emitted set is exactly
 * the documented set, every code the helper names is emittable, and the only unnamed emitted
 * codes are the documented install-failure (3) and vanished-child (4) codes.
 */
class InstallerContractTest {

    private val nsi = File("packaging/windows/installer.nsi")
    private val helperSource = File("src/main/kotlin/app/n_zik/compagnon/updater/services/UpdateDownloadManager.kt")

    private val nsiText: String by lazy { nsi.readText() }
    private val helperText: String by lazy { helperSource.readText() }

    /** The value of a `!define NAME "value"` line (the value may itself contain ${...} macros). */
    private fun defineValue(name: String): String =
        nsiText.lineSequence()
            .firstOrNull { it.trimStart().startsWith("!define $name ") }
            ?.substringAfter('"')
            ?.substringBefore('"')
            ?: error("$name is not defined in the NSIS script")

    /** The codes the installer exits with DIRECTLY: literal `SetErrorLevel <n>` (any scope). */
    private val literalExitCodes: Set<Int> by lazy {
        Regex("SetErrorLevel (\\d+)").findAll(nsiText).map { it.groupValues[1].toInt() }.toSet()
    }

    /**
     * The codes the ELEVATED CHILD writes to the propagation files (`$EXITCODE_OUT` for the
     * installer + the uninstaller's direct `FileWrite`). The non-elevated parent relays them
     * (`SetErrorLevel $R2` after `FileRead`ing the file — the relay is pinned in the exit-code
     * test), so these are emitted exit codes too.
     */
    private val propagationWrites: Set<Int> by lazy {
        (Regex("StrCpy \\\$EXITCODE_OUT \"(\\d+)\"").findAll(nsiText) +
            Regex("FileWrite \\\$R1 \"(\\d+)\"").findAll(nsiText))
            .map { it.groupValues[1].toInt() }
            .toSet()
    }

    private val emittedCodes: Set<Int> by lazy { literalExitCodes + propagationWrites }

    /** The codes the helper NAMES in its script (`-eq 0` + the `-ne 1 -ne 2 -ne 1223` list). */
    private val helperNamedCodes: Set<Int> by lazy {
        (Regex("""\\\${'$'}code -eq (\d+)""").findAll(helperText) +
            Regex("""\\\${'$'}code -ne (\d+)""").findAll(helperText))
            .map { it.groupValues[1].toInt() }
            .toSet()
    }

    @Test
    fun `the registry identity is per channel under the N-Zik key`() {
        assertEquals(
            "Software\\N-Zik\\DesktopCompagnon\\${'$'}{CHANNEL}",
            defineValue("PRODUCT_KEY"),
            "the product-info key is the channel subkey under Software\\N-Zik\\DesktopCompagnon (HKCU per-user / HKLM global)"
        )
        assertEquals(
            "Software\\Microsoft\\Windows\\CurrentVersion\\Uninstall\\${'$'}{PRODUCT_NAME}",
            defineValue("UNINST_KEY"),
            "the Windows uninstall entry keeps the per-product Uninstall key"
        )
    }

    @Test
    fun `every emitted exit code is a contract code and the helper names every no-marker one`() {
        val emitted = emittedCodes
        val named = helperNamedCodes
        assertEquals(setOf(0, 1, 2, 3, 4, 1223), emitted,
            "the installer's emitted exit codes are the documented contract set (the NSIS header)")
        assertEquals(setOf(0, 1, 2, 1223), named,
            "the helper names 0 (relaunch) / 1 (cancel) / 2 (remove) / 1223 (UAC declined)")
        assertTrue(named.all { it in emitted },
            "every code the helper treats as 'no marker' must be emittable by the installer: named=$named emitted=$emitted")
        assertEquals(setOf(3, 4), emitted - named,
            "the only emitted codes the helper does not name are 3 (install failure) and 4 (elevated child vanished -> both the failure marker)")
        assertTrue(
            nsiText.contains("SetErrorLevel \$R2"),
            "the non-elevated parent must relay the elevated child's exit code (SetErrorLevel \$R2 after reading the propagation file)"
        )
    }

    @Test
    fun `the helper writes the failure marker for any code it does not name`() {
        assertTrue(
            helperText.contains("Set-Content -Path '\$marker'"),
            "the helper's unexpected-code branch must write the failure marker"
        )
        assertTrue(
            helperText.contains("install failed with exit code \\\$code"),
            "the failure marker must record the code (the diagnostics)"
        )
    }
}
