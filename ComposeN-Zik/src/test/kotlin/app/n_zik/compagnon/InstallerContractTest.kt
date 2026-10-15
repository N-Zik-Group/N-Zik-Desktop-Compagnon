package app.n_zik.compagnon

import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The Windows installer contract is pure NSIS text + a PowerShell one-liner: a makensis script
 * that changes the registry identity, drops an exit code, or stops relaying the elevated child's
 * code would desync the in-app updater helper (which interprets exactly those codes) with a green
 * build — and no other test path reads the .nsi. This test parses the script (and the helper's
 * source) directly, the same idiom as [LinuxPackagePinTest] (the tests' working dir is the
 * `ComposeN-Zik/` module dir, so the files are read relative to it).
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
 *
 * The in-app install detection (spec `spec-windows-portable-zip` D1) discriminates the NSIS
 * install from the portable extract on the uninstaller marker — so the marker write side is
 * contract too: the uninstaller is ALWAYS written at the exact path the detection reads
 * (`$INSTDIR\Uninstall <product>.exe`), the per-scope registry `InstallDir` is recorded, and
 * the scope defaults place the install in the product-named dir.
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
     * The body of a named NSIS function (between `Function <name>` and its `FunctionEnd`).
     * The word boundary after the name anchors the match to an EXACT name — a bare
     * indexOf("Function $name") would let a future `Function Shortcuts` resolve into
     * `Function ShortcutsShow`'s body (or vice versa).
     */
    private fun nsisFunction(name: String): String {
        val marker = Regex("Function " + Regex.escape(name) + """\b""").find(nsiText)
            ?: error("$name is not defined in the NSIS script")
        val end = nsiText.indexOf("FunctionEnd", marker.range.last)
        check(end > marker.range.last) { "$name is not defined in the NSIS script" }
        return nsiText.substring(marker.range.last + 1, end)
    }

    /** The body of a named NSIS section (from `Section "<name>"` to its `SectionEnd`). */
    private fun nsisSection(name: String): String {
        val start = nsiText.indexOf("Section \"$name\"")
        val end = nsiText.indexOf("SectionEnd", start)
        check(start >= 0 && end > start) { "Section \"$name\" is not found in the NSIS script" }
        return nsiText.substring(start, end)
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

    @Test
    fun `the uninstaller marker contract the in-app detection relies on is pinned`() {
        // spec `spec-windows-portable-zip` D1 (review 2 — VG2-1): the in-app detection
        // discriminates the NSIS install from the portable extract on the uninstaller marker —
        // if the installer stops writing it at the exact path the detection reads, every real
        // install would misdetect as portable with a green build. The write side is the
        // contract:
        assertTrue(
            nsiText.contains("WriteUninstaller \"${'$'}INSTDIR\\Uninstall ${'$'}{PRODUCT_NAME}.exe\""),
            "the uninstaller is ALWAYS written at the exact path the in-app detection reads (Uninstall <product>.exe in \$INSTDIR)"
        )
        assertTrue(
            nsiText.contains("WriteRegStr HKCU \"${'$'}{UNINST_KEY}\" \"UninstallString\" '\"${'$'}INSTDIR\\Uninstall ${'$'}{PRODUCT_NAME}.exe\"'"),
            "the per-user uninstall entry records the same marker path (UninstallString)"
        )
        assertTrue(
            nsiText.contains("WriteRegStr HKLM \"${'$'}{UNINST_KEY}\" \"UninstallString\" '\"${'$'}INSTDIR\\Uninstall ${'$'}{PRODUCT_NAME}.exe\"'"),
            "the global uninstall entry records the same marker path (UninstallString)"
        )
        // The installer records its install dir under the per-channel key (the D1 registry
        // cross-check source) in BOTH scopes:
        assertTrue(
            nsiText.contains("WriteRegStr HKCU \"${'$'}{PRODUCT_KEY}\" \"InstallDir\" \"${'$'}INSTDIR\""),
            "the per-user scope records InstallDir = \$INSTDIR under the channel key"
        )
        assertTrue(
            nsiText.contains("WriteRegStr HKLM \"${'$'}{PRODUCT_KEY}\" \"InstallDir\" \"${'$'}INSTDIR\""),
            "the global scope records InstallDir = \$INSTDIR under the channel key"
        )
        // The scope defaults place the install in the product-named dir (the D1 folder-name
        // rule holds out of the box; the directory page is editable, which is why the
        // detection carries the registry cross-check fallback):
        assertTrue(
            nsiText.contains("InstallDir \"${'$'}PROGRAMFILES\\${'$'}{PRODUCT_NAME}\""),
            "the global scope default installs into the product-named Program Files dir"
        )
        assertEquals(
            2,
            Regex("StrCpy \\\$INSTDIR \"\\\$LOCALAPPDATA\\\\\\$\\{PRODUCT_NAME\\}\"").findAll(nsiText).count(),
            "the per-user scope sets \$INSTDIR to the product-named LOCALAPPDATA dir (pre-branch default + the scope page)"
        )
        assertTrue(
            nsiText.contains("!insertmacro MUI_PAGE_DIRECTORY"),
            "the directory page is offered (editable) — the custom-dir installs the registry cross-check covers"
        )
    }

    /**
     * The shortcuts page (spec `spec-installer-shortcuts-launch` D1/D4/D5): ONE checkbox —
     * "Create shortcuts (Start Menu and Desktop)" — ON by default, between the directory page
     * and the install-files page; the install section creates the shortcuts only under the
     * `SHORTCUTS` gate, and the OFF branch DELETES the existing Desktop + Start Menu shortcuts
     * (the upgrade path: an install with shortcuts, upgraded with the box off, must not keep
     * the old `.lnk` files). The checkbox is read in the page's LEAVE callback (distro quirk —
     * the leave fires on the Next click while the dialog still exists), and the silent path
     * defaults `SHORTCUTS="1"` in `.onInit` (`/S` skips the pages).
     */
    @Test
    fun `the shortcuts page exists before the install-files page and gates the shortcut block`() {
        assertTrue(
            nsiText.contains("Page custom ShortcutsShow ShortcutsLeave"),
            "the shortcuts page must be a custom nsDialogs page (ShortcutsShow/ShortcutsLeave)"
        )
        val pagePos = nsiText.indexOf("Page custom ShortcutsShow ShortcutsLeave")
        val directoryPos = nsiText.indexOf("!insertmacro MUI_PAGE_DIRECTORY")
        val instfilesPos = nsiText.indexOf("!insertmacro MUI_PAGE_INSTFILES")
        assertTrue(
            directoryPos in 0 until pagePos && pagePos in 0 until instfilesPos,
            "the shortcuts page must be ordered between the directory page and the install-files page (directory -> shortcuts -> install)"
        )
        assertTrue(
            nsiText.contains("StrCpy \$SHORTCUTS \"1\""),
            "the silent install path keeps shortcuts ON by default (SHORTCUTS = \"1\" in .onInit — /S skips the pages)"
        )
        assertTrue(
            nsisFunction("ShortcutsShow").contains("\${NSD_SetState} \$R4 \${BST_CHECKED}"),
            "the shortcut checkbox is created CHECKED by default (NSD_SetState to BST_CHECKED in ShortcutsShow — the leave alone cannot prove the default)"
        )
        assertTrue(
            nsisFunction("ShortcutsLeave").contains("NSD_GetState"),
            "the shortcut checkbox must be read in the page's LEAVE callback (distro quirk — a GetState after the show reads a destroyed dialog)"
        )
        // The ON branch creates both shortcuts; the OFF branch deletes the existing ones
        // Pattern: \$\{If\} \$SHORTCUTS == "1"(.*?)\$\{EndIf\} — a raw string interpolates
        // $identifiers, so the literal \$ before the var name is written as \ + ${'$'}
        val block = Regex("""\$\{If\} \${'$'}SHORTCUTS == "1"(.*?)\$\{EndIf\}""", RegexOption.DOT_MATCHES_ALL)
            .find(nsiText)?.value
            ?: error("the SHORTCUTS gate block is not found in the NSIS script")
        // Plain-text pins on the .nsi path fragments (raw strings: ${'$'} is the only way to
        // write a literal $ that is not followed by an identifier; a backslash right before
        // the closing delimiter stays a literal content character)
        assertTrue(
            block.contains("""CreateDirectory "${'$'}SMPROGRAMS\N-Zik""") &&
                block.contains("""CreateShortcut "${'$'}SMPROGRAMS\N-Zik"""),
            "the ON branch creates the Start Menu group \"N-Zik\" + its shortcut"
        )
        assertTrue(
            block.contains("""CreateShortcut "${'$'}DESKTOP"""),
            "the ON branch creates the Desktop shortcut"
        )
        assertTrue(block.contains("\${Else}"), "the OFF branch must exist (the upgrade path)")
        val offBranch = block.substringAfter("\${Else}")
        assertTrue(
            offBranch.contains("Delete \"\$DESKTOP\\${'$'}{PRODUCT_NAME}.lnk\""),
            "the OFF branch must DELETE the existing Desktop shortcut (an upgrade with the box off must not keep the old .lnk)"
        )
        assertTrue(
            offBranch.contains("Delete \"\$SMPROGRAMS\\N-Zik\\${'$'}{PRODUCT_NAME}.lnk\""),
            "the OFF branch must DELETE the existing Start Menu shortcut (an upgrade with the box off must not keep the old .lnk)"
        )
        assertTrue(
            offBranch.contains("RMDir \"\$SMPROGRAMS\\N-Zik\""),
            "the OFF branch also removes the (now empty) Start Menu group directory (the same operations the uninstaller does)"
        )
        // The elevated child RE-SHOWS the page (D5 — consistent with the scope / directory
        // re-show, no state propagation): the show function must not branch on the elevation
        // flag to skip it (a skip here would silently drop the user's choice on a global install)
        assertTrue(
            !nsisFunction("ShortcutsShow").contains("ELEVATED"),
            "the shortcuts page must re-show in the elevated child (no ELEVATED branch that skips it — the user re-confirms after the UAC relaunch, same pattern as \$INSTDIR)"
        )
        // UPDATE_FLOW (spec edge-case matrix): the page is STILL OFFERED on /UPDATE — only the
        // finish page branches on IS_UPDATE, never the shortcuts page
        assertTrue(
            !nsisFunction("ShortcutsShow").contains("IS_UPDATE"),
            "the shortcuts page is still offered on /UPDATE (no IS_UPDATE branch that skips it — the updater flow keeps the user's shortcut choice)"
        )
        // The UNINSTALL row (unchanged by this feature — the pin guards the regression): the
        // uninstaller deletes both shortcuts if present (Delete is a no-op when absent). Scoped
        // to the uninstall section — a whole-file contains would be satisfied by the
        // install-side OFF-branch Deletes above
        val uninstallSection = nsisSection("Uninstall")
        assertTrue(
            uninstallSection.contains("Delete \"\$DESKTOP\\${'$'}{PRODUCT_NAME}.lnk\""),
            "the UNINSTALLER deletes the Desktop shortcut if present (no-op when absent)"
        )
        assertTrue(
            uninstallSection.contains("Delete \"\$SMPROGRAMS\\N-Zik\\${'$'}{PRODUCT_NAME}.lnk\""),
            "the UNINSTALLER deletes the Start Menu shortcut if present (no-op when absent)"
        )
    }

    /**
     * The finish page (spec `spec-installer-shortcuts-launch` D2/D3): a custom nsDialogs page
     * replacing the MUI finish page — the "Launch <product>" checkbox is ON by default and
     * offered OUTSIDE the `/UPDATE` flow only (the updater helper relaunches the app after
     * exit 0 — its own running-guard; a visible checkbox would add a redundant relaunch
     * race). The launch is an async `Exec` of `$INSTDIR\${LAUNCHER_NAME}` (NOT `ExecWait` —
     * the installer must return exit 0 to the helper immediately) and the exit-0 write to the
     * propagation file stays in the leave.
     */
    @Test
    fun `the finish page is custom and offers the launch checkbox only outside the updater flow`() {
        assertFalse(
            nsiText.contains("MUI_PAGE_FINISH"),
            "the MUI finish page is replaced by the custom page — MUI_FINISHPAGE_RUN's checkbox is a compile-time define and cannot be conditionally hidden at runtime for /UPDATE"
        )
        assertTrue(
            nsiText.contains("Page custom FinishShow FinishLeave"),
            "the finish page must be a custom nsDialogs page (FinishShow/FinishLeave — the checkbox is read in the leave)"
        )
        // It must be the LAST page — the wizard frame shows the Finish button only on the final
        // page (no button handling in the custom finish page)
        val finishPagePos = nsiText.indexOf("Page custom FinishShow FinishLeave")
        val instfilesPos = nsiText.indexOf("!insertmacro MUI_PAGE_INSTFILES")
        assertTrue(
            instfilesPos in 0 until finishPagePos,
            "the finish page must come AFTER the install-files page (it is the last page — the Finish button depends on it)"
        )
        val show = nsisFunction("FinishShow")
        // The /UPDATE branch: informational labels only, NO launch checkbox
        val updateBranch = show.substringAfter("\${If} \$IS_UPDATE == \"1\"").substringBefore("\${Else}")
        assertFalse(
            updateBranch.contains("NSD_CreateCheckBox"),
            "on /UPDATE the finish page must show NO launch checkbox (the updater helper relaunches the app after exit 0 — its own running-guard)"
        )
        assertTrue(
            updateBranch.contains("NSD_CreateLabel"),
            "on /UPDATE the finish page is an informational label only (the page still shows — it is the finish)"
        )
        // The non-/UPDATE branch: the launch checkbox, ON by default
        val offUpdateBranch = show.substringAfter("\${Else}")
        assertTrue(
            offUpdateBranch.contains("Launch \${PRODUCT_NAME}"),
            "outside /UPDATE the finish page offers the \"Launch <product>\" checkbox"
        )
        assertTrue(
            offUpdateBranch.contains("NSD_CreateCheckBox") &&
                offUpdateBranch.contains("\${NSD_SetState} \$R4 \${BST_CHECKED}"),
            "the launch checkbox is created and CHECKED by default (NSD_SetState to BST_CHECKED — not merely present)"
        )
        // The leave: the launch gated on the checkbox + the exit-0 write
        val leave = nsisFunction("FinishLeave")
        assertTrue(
            leave.contains("\${If} \$IS_UPDATE != \"1\""),
            "the finish leave never reads the launch checkbox on /UPDATE (the IS_UPDATE guard — paired with the show's LAUNCH_APP reset, the never-launch guarantee cannot be dropped by removing one side alone)"
        )
        val gate = "\${If} \$LAUNCH_APP == \"1\""
        assertTrue(
            leave.contains(gate),
            "the launch must run only when the launch checkbox was checked (the LAUNCH_APP gate)"
        )
        // The launch line as PLAIN TEXT (the same idiom as the registry pins above — a regex on
        // the \$-carrying path is escape-fragile): Exec '"$INSTDIR\${LAUNCHER_NAME}"'
        val execLine = "Exec '\"${'$'}INSTDIR\\${'$'}{LAUNCHER_NAME}\"'"
        assertTrue(
            leave.contains(execLine),
            "the finish leave must launch the app (an async Exec) targeting \$INSTDIR\\<launcher>"
        )
        assertTrue(
            leave.indexOf(gate) < leave.indexOf(execLine),
            "the launch Exec must sit INSIDE the LAUNCH_APP gate (no launch when unchecked)"
        )
        assertTrue(
            !leave.contains("ExecWait"),
            "the launch must be async (Exec) — the installer returns exit 0 to the updater helper immediately"
        )
        assertTrue(
            leave.contains("StrCpy \$EXITCODE_OUT \"0\"") && leave.contains("Call WriteExitCode"),
            "the exit-0 write to the propagation file stays in the finish leave (the updater helper consumes exit 0 — the elevated child's primary channel)"
        )
    }
}
