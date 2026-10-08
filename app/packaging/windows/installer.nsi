; ============================================================================
; N-Zik Desktop Compagnon - Windows installer (NSIS 3.x)
; SOURCE OF TRUTH for the installer behavior (the NSIS installer replaces the
; jpackage self-extracting exe; the record is the Change Log of
; `_bmad-output/implementation-artifacts/spec-updater.md`).
;
; BUILT BY GRADLE (the :app:packageInstaller task runs makensis on this script):
;   1. :app:packageExe (jpackage) builds the APP-IMAGE at
;      app/build/compose/binaries/main/app/<product>/ (the launcher + app/ +
;      runtime/ with the embedded VLC) - plus the jpackage self-extracting exe,
;      which is now a build BYPRODUCT (this NSIS installer overwrites it at the
;      same output path);
;   2. makensis packages the app-image directory into the final installer.
;
; SCOPE - one installer exe, two scopes (scope-choice page, default per-user):
;   per-user : no admin rights; installs under %LOCALAPPDATA%\<product>;
;              registry under HKCU;
;   global   : the installer relaunches ITSELF elevated (shell32 ShellExecuteW
;              runas - the UAC prompt appears at that moment, visible); installs
;              under %PROGRAMFILES%\<product>; registry under HKLM.
;   Cross-scope migration: an installation of the OTHER scope (same channel) is
;   detected from the registry; the user is informed; the old scope is removed
;   first (its uninstaller runs silently - no wipe - then registry keys +
;   leftover files) and the result is VERIFIED (the old dir + the old scope's
;   registry keys must be gone - a global old scope cannot be removed by a
;   non-elevated process, so the verification fails and the install aborts
;   with exit 1 + a clear message). The user data (%APPDATA%\<product> -
;   pairing, settings, audio cache) is NEVER touched by install or migration.
;
; RUNNING-APP GUARD - install AND uninstall first check that the app is not
; still running: the launcher exe is opened for write (a locked file cannot be
; opened). Locked -> a clear message + exit 1 (the existing cancel semantics -
; the updater helper treats it as "not done yet", no failure marker). The
; uninstall guard runs in un.onInit, BEFORE the elevation decision (no UAC
; prompt is consumed for an uninstall that cannot proceed); the elevated child
; re-runs the same check (the app may have been closed in between - it passes
; then) and reports exit 1 through the exit-code file so the non-elevated
; parent relays it immediately instead of waiting out the bound.
;
; UPGRADE - registry-based version identity (replaces the frozen per-channel MSI
; upgrade UUIDs - spec AD-8 SUPERSEDED): the product-info key
; (Software\N-Zik\DesktopCompagnon\<channel> - under HKCU per-user / HKLM global)
; stores the version + scope + install dir + install date. NSIS registry
; commands take the root key as a compile-time literal, so every read/write/
; delete branches on the scope (per-user -> HKCU, global -> HKLM).
;
; RRU PAGE (existing install + NOT /UPDATE): "version X is installed" with
; UPGRADE / REPAIR / REMOVE. REMOVE launches the existing uninstaller
; interactively, waits, then exits the installer with code 2. When the in-app
; updater launches the installer with /UPDATE, the RRU page is SKIPPED (its show
; function returns early -> the wizard auto-advances) and the scope defaults to
; the current install's scope - with one gate: when the installed version is
; >= the incoming one (a same-version or older re-install), the user is asked
; "reinstall anyway?" (MB_YESNO) - No = exit 1 (a normal decline). The compare
; is NUMERIC (major / minor / patch / build - the dev build date), never
; lexicographic.
;
; UNINSTALL - files + registry + shortcuts, plus an nsDialogs checkbox page
; "Also delete user data (pairing, settings, audio cache)" - UNCHECKED BY
; DEFAULT. Checked -> deletes %APPDATA%\<product> and the Windows Credential
; Manager entry (target <product> - the device token, cmdkey /delete). A
; global-scope uninstall run by a limited user relaunches the uninstaller
; elevated (the same temp-file exit-code propagation as the installer). The
; uninstall section first GUARDS AGAINST COEXISTENCE (the other scope must have
; no live install - the uninstaller removes only its own scope's registry keys,
; never all four hives) and then deletes the keys of ITS SCOPE only.
;
; BRANDING - plain MUI (no header image, no full-page background - both were
; tried and abandoned: MUI_HEADERIMAGE squashes the banner into the ~396x70
; header strip, and the BgImage plugin in this NSIS distro does not render
; behind custom nsDialogs pages - screenshot-proven). The only branding is the
; supercircle window icon (MUI_ICON / MUI_UNICON). This script is strictly
; ASCII: makensis reads it in the system codepage (cp1252 on this host), so
; any non-ASCII byte mojibakes (or can corrupt a string literal).
; Language: English (built-in MUI). Additional OS languages can be added later
; by extending MUI_LANGUAGE + LangString - a future option, not implemented.
;
; EXIT CODES (pinned by `InstallerContractTest` - the in-app updater helper
; consumes them):
;   0    = success;
;   1    = user cancelled (any page - the NSIS built-in abort code) OR the
;          running-app guard fired (the app is still running - "not done yet");
;   2    = REMOVE chosen on the RRU page (the existing install is removed; the
;          app is gone by design - no relaunch, no failure marker);
;   3    = install failure (the app-image copy failed);
;   4    = elevated child vanished (the non-elevated parent detected the
;          child's death via the PID file + OpenProcess + GetExitCodeProcess,
;          or the child never wrote its PID file - a crash / kill before
;          reporting);
;   1223 = UAC DECLINED (ERROR_CANCELLED - the runas relaunch was refused).
;   The helper writes the failure marker for any code other than 0/1/2/1223
;   (3 and 4 both map to it).
;
; ELEVATION EXIT-CODE PROPAGATION - the non-elevated process relaunches itself
; elevated and watches it with TWO channels (so a child dying without reporting
; is detected as a death - exit 4 - instead of being waited out):
;   1. EXIT-CODE FILE (primary): the elevated child writes its exit code to
;      %TEMP%\<channel>-install-exitcode.txt (0 on the finish-page leave, 3 on
;      install failure, 1 when the running-app guard fires in the child); the
;      non-elevated parent polls that file and relays the code;
;   2. PID FILE + PROCESS POLL: the elevated child writes its own process ID
;      (kernel32 GetCurrentProcessId - this distro has no native $PID variable)
;      to %TEMP%\<channel>-elevated-pid.txt on its welcome-page PRE
;      (before the wizard is shown); the non-elevated parent reads it (a short
;      grace - the UAC prompt already resolved before the ShellExecuteW launch
;      returned), opens the child (kernel32 OpenProcess,
;      PROCESS_QUERY_INFORMATION) and polls kernel32 GetExitCodeProcess: while
;      the call reports 259 the child is still ALIVE (probe-proven on this
;      distro); any other value is the child's exit code - it is GONE, and
;      with the exit file still absent its documented codes (0/1/2) are relayed
;      as-is while anything else maps to 4. No PID file within the grace -> the
;      child never reached its welcome page -> 4. When no process handle can be
;      obtained at all (OpenProcess denied - the UAC integrity boundary can
;      block it), the wait degrades to the exit-file-only behavior and a child
;      that never reports within the bound is treated as a user cancel (1) -
;      cancel and death are then indistinguishable (documented degradation).
;   A UAC decline is detected at LAUNCH time (ShellExecuteW returns
;   SE_ERR_ACCESSDENIED, or GetLastError() == 1223) - no waiting.
;
; ELEVATED WAIT BOUND - 1200 polls of 500 ms (~10 minutes) for the exit-code
; file: a legit install copies the ~250 MB app-image in minutes; beyond the
; bound a non-reporting child is a user cancel.
; ============================================================================

!include "MUI2.nsh"
!include "nsDialogs.nsh"
!include "LogicLib.nsh"

; --- Defines (all set by Gradle via /D - see the :app:packageInstaller task) ---
;   PRODUCT_NAME / CHANNEL / APP_VERSION / VERSION_MAJOR / VERSION_MINOR /
;   VERSION_PATCH / VERSION_BUILD / APPIMAGE_DIR / LAUNCHER_NAME / OUT_FILE /
;   ICON_FILE / VENDOR / DESCRIPTION / DATA_DIR_NAME / CRED_TARGET

Name "${PRODUCT_NAME}"
Caption "${PRODUCT_NAME}"
BrandingText "${VENDOR}"
InstallDir "$PROGRAMFILES\${PRODUCT_NAME}"
; The installer runs as the invoking (non-elevated) user; elevation is a
; per-scope self-relaunch (the UAC prompt appears only when global is chosen).
RequestExecutionLevel user

SetCompressor /SOLID lzma

!define MUI_ICON "${ICON_FILE}"
!define MUI_UNICON "${ICON_FILE}"
!define MUI_ABORTWARNING
!define MUI_UNABORTWARNING
!define MUI_DISABLE_INSERT_LANGUAGE_AFTER_PAGES_WARNING

; Version resource. The registry "Version" keeps the full value (e.g.
; 0.0.1-dev-20261007), but the Windows version resource requires each of its 4
; parts < 65536, so the resource build (the dev build date, e.g. 20261007) is
; reduced mod 65536 (compile-time).
!define /math RESBUILD ${VERSION_BUILD} % 65536
VIProductVersion "${VERSION_MAJOR}.${VERSION_MINOR}.${VERSION_PATCH}.${RESBUILD}"
VIAddVersionKey "ProductName" "${PRODUCT_NAME}"
VIAddVersionKey "FileDescription" "${DESCRIPTION}"
VIAddVersionKey "FileVersion" "${VERSION_MAJOR}.${VERSION_MINOR}.${VERSION_PATCH}.${RESBUILD}"
VIAddVersionKey "ProductVersion" "${APP_VERSION}"
VIAddVersionKey "CompanyName" "${VENDOR}"
VIAddVersionKey "LegalCopyright" "Copyright (c) ${VENDOR}"

; --- Registry identity (registry-based upgrade model - spec AD-8 superseded) ---
; The product-info key lives under HKCU (per-user scope) or HKLM (global scope);
; the channel is the subkey, so the identity is per channel x per scope.
!define PRODUCT_KEY "Software\N-Zik\DesktopCompagnon\${CHANNEL}"
!define UNINST_KEY  "Software\Microsoft\Windows\CurrentVersion\Uninstall\${PRODUCT_NAME}"
; The elevation propagation files (%TEMP% - same user on both sides): the
; elevated exit-code file (the primary channel) + the elevated child's PID
; file (the process-poll channel - see the header).
!define EXITCODE_NAME "${CHANNEL}-install-exitcode.txt"
!define UN_EXITCODE_NAME "${CHANNEL}-uninstall-exitcode.txt"
!define PID_NAME "${CHANNEL}-elevated-pid.txt"
; The elevated wait bound (polls of 500 ms ~ 10 minutes) - a legit install
; copies the ~250 MB app-image in minutes; beyond the bound a non-reporting
; child is treated as a user cancel.
!define ELEVATED_WAIT_POLLS 1200
; The short grace to wait for the elevated child's PID file (polls of 500 ms
; ~ 20 seconds): the child writes it on its welcome-page PRE, seconds after
; the launch (the UAC prompt already resolved before the ShellExecuteW
; returned); no file within the grace = the child never reached its welcome
; page = exit 4.
!define PID_WAIT_POLLS 40

Var ELEVATED        ; "1" when this process was relaunched with --elevated
Var IS_UPDATE       ; "1" when launched with /UPDATE (from the in-app updater)
Var CHOSEN_SCOPE    ; "peruser" | "global"
Var INST_SCOPE_OLD  ; "peruser" | "global" | "" - an existing install, either scope
Var INST_DIR_OLD    ; the existing install dir ("" when none)
Var INST_VER_OLD    ; the existing install's full version string ("" when none)
Var RRU_CHOICE      ; "upgrade" | "repair" | "remove" (the RRU page selection)
Var WipeData        ; "1" when the user checked the uninstall wipe checkbox
Var EXITCODE_OUT    ; the exit code to write to the propagation file
Var INSTALLDATE     ; yyyyMMdd (the install date, written to the registry)
Var UELEVATED       ; uninstaller: "1" when relaunched with --uninst-elevated
Var UN_SCOPE        ; uninstaller: "peruser" | "global" (this install's scope)
Var UN_NEED_ELEVATE ; uninstaller: "1" when the welcome PRE must relaunch elevated

; ============================================================================
; Helpers
; ============================================================================

; Substring search: $R0 = haystack, $R1 = needle, result in $R2 ("1"/"0")
Function CmdContains
  StrCpy $R2 "0"
  StrLen $R3 $R0
  StrLen $R4 $R1
  IntOp $R5 $R3 - $R4
  StrCpy $R6 "0"
CmdLoop:
  ${If} $R6 > $R5
    Return
  ${EndIf}
  StrCpy $R7 $R0 $R4 $R6
  ${If} $R7 == $R1
    StrCpy $R2 "1"
    Return
  ${EndIf}
  IntOp $R6 $R6 + 1
  Goto CmdLoop
FunctionEnd

; Version parsing (this NSIS distro has no built-in version split). This
; distro's StrCpy is COUNT-FIRST: `StrCpy $dest $src $A $B` takes A = number of
; chars, B = start offset (a negative A counts from the end) - probe-proven,
; the opposite of the standard docs.
; in:  $R0 = the full version string (e.g. 0.0.1-dev-20261007)
; out: $R1 = major, $R2 = minor, $R3 = patch, $R4 = build - decimal strings
;      (build = the dev build date; "0" when the version carries no
;      "-dev-<date>" suffix - release / beta builds). Clobbers $R5-$R9.
Function VerParse
  StrCpy $R9 $R0
  ; --- strip everything from the first '-' (the dev-<date> / beta / ... suffix)
  StrLen $R7 $R9
  StrCpy $R6 "0"
  StrCpy $R8 "-1"
VP_Suf:
  ${If} $R6 >= $R7
    Goto VP_SufDone
  ${EndIf}
  StrCpy $R5 $R9 1 $R6
  ${If} $R5 == "-"
    StrCpy $R8 "$R6"
    Goto VP_SufDone
  ${EndIf}
  IntOp $R6 $R6 + 1
  Goto VP_Suf
VP_SufDone:
  ${If} $R8 != "-1"
    StrCpy $R0 $R9 $R8 0
  ${EndIf}
  ; --- major (the part before the first '.')
  StrLen $R7 $R0
  StrCpy $R6 "0"
  StrCpy $R8 "-1"
VP_Maj:
  ${If} $R6 >= $R7
    Goto VP_MajDone
  ${EndIf}
  StrCpy $R5 $R0 1 $R6
  ${If} $R5 == "."
    StrCpy $R8 "$R6"
    Goto VP_MajDone
  ${EndIf}
  IntOp $R6 $R6 + 1
  Goto VP_Maj
VP_MajDone:
  ${If} $R8 != "-1"
    StrCpy $R1 $R0 $R8 0
    IntOp $R5 $R8 + 1
    StrCpy $R0 $R0 -1 $R5
  ${Else}
    StrCpy $R1 $R0
    StrCpy $R0 ""
  ${EndIf}
  ; --- minor
  StrLen $R7 $R0
  StrCpy $R6 "0"
  StrCpy $R8 "-1"
VP_Min:
  ${If} $R6 >= $R7
    Goto VP_MinDone
  ${EndIf}
  StrCpy $R5 $R0 1 $R6
  ${If} $R5 == "."
    StrCpy $R8 "$R6"
    Goto VP_MinDone
  ${EndIf}
  IntOp $R6 $R6 + 1
  Goto VP_Min
VP_MinDone:
  ${If} $R8 != "-1"
    StrCpy $R2 $R0 $R8 0
    IntOp $R5 $R8 + 1
    StrCpy $R0 $R0 -1 $R5
  ${Else}
    StrCpy $R2 $R0
    StrCpy $R0 ""
  ${EndIf}
  ; --- patch (the remainder - the format guarantees no further dots)
  StrCpy $R3 $R0
  ; --- build: the date after the SECOND '-' of the ORIGINAL string (the
  ;      "<kind>-<date>" suffix), else 0
  StrCpy $R4 "0"
  StrCpy $R6 "0"
  StrCpy $R8 "-1"
VP_Bld1:
  ${If} $R8 != "-1"
    Goto VP_Bld1Done
  ${EndIf}
  StrLen $R7 $R9
  ${If} $R6 >= $R7
    Goto VP_Bld1Done
  ${EndIf}
  StrCpy $R5 $R9 1 $R6
  ${If} $R5 == "-"
    StrCpy $R8 "$R6"
    Goto VP_Bld1Done
  ${EndIf}
  IntOp $R6 $R6 + 1
  Goto VP_Bld1
VP_Bld1Done:
  ${If} $R8 != "-1"
    ; the date is after the next '-' of the suffix (scan starts just past the
    ; first '-')
    IntOp $R6 $R8 + 1
    StrCpy $R7 "-1"
VP_Bld2:
    ${If} $R7 != "-1"
      Goto VP_Bld2Done
    ${EndIf}
    StrLen $R8 $R9
    ${If} $R6 >= $R8
      Goto VP_Bld2Done
    ${EndIf}
    StrCpy $R5 $R9 1 $R6
    ${If} $R5 == "-"
      StrCpy $R7 "$R6"
      Goto VP_Bld2Done
    ${EndIf}
    IntOp $R6 $R6 + 1
    Goto VP_Bld2
VP_Bld2Done:
    ${If} $R7 != "-1"
      IntOp $R5 $R7 + 1
      StrCpy $R4 $R9 -1 $R5
    ${EndIf}
  ${EndIf}
FunctionEnd

; Version compare (NUMERIC - a lexicographic compare would rank "0.0.10" below
; "0.0.2", so the parts are compared as numbers; LogicLib compares two numeric-
; looking strings as numbers on this distro - probe-proven):
; in:  $R0 = version A (the installed one), $R1 = version B (the incoming one)
; out: $R2 = "1" (A > B), "0" (A == B), "-1" (A < B)
; Clobbers $R0-$R9 + numeric $9 (the scratch that survives VerParse).
Function VerCompare
  StrCpy $9 $R1
  Call VerParse
  StrCpy $R5 $R1
  StrCpy $R6 $R2
  StrCpy $R7 $R3
  StrCpy $R8 $R4
  StrCpy $R0 $9
  Call VerParse
  StrCpy $R9 "0"
  ${If} $R1 > $R5
    StrCpy $R9 "1"
  ${ElseIf} $R1 < $R5
    StrCpy $R9 "-1"
  ${Else}
    ${If} $R2 > $R6
      StrCpy $R9 "1"
    ${ElseIf} $R2 < $R6
      StrCpy $R9 "-1"
    ${Else}
      ${If} $R3 > $R7
        StrCpy $R9 "1"
      ${ElseIf} $R3 < $R7
        StrCpy $R9 "-1"
      ${Else}
        ${If} $R4 > $R8
          StrCpy $R9 "1"
        ${ElseIf} $R4 < $R8
          StrCpy $R9 "-1"
        ${EndIf}
      ${EndIf}
    ${EndIf}
  ${EndIf}
  StrCpy $R2 "$R9"
FunctionEnd

; Write the propagation file (only in the elevated child)
Function WriteExitCode
  ${If} $ELEVATED == "1"
    FileOpen $R9 "$TEMP\${EXITCODE_NAME}" w
    FileWrite $R9 "$EXITCODE_OUT"
    FileClose $R9
  ${EndIf}
FunctionEnd

; Detect an existing install of either scope (registry product-info key). NSIS
; registry roots are compile-time literals, so the HKCU/HKLM lookups are inline.
Function DetectInstalled
  StrCpy $INST_SCOPE_OLD ""
  StrCpy $INST_DIR_OLD ""
  StrCpy $INST_VER_OLD ""
  ReadRegStr $INST_DIR_OLD HKCU "${PRODUCT_KEY}" "InstallDir"
  ${If} $INST_DIR_OLD != ""
    StrCpy $INST_SCOPE_OLD "peruser"
    ReadRegStr $INST_VER_OLD HKCU "${PRODUCT_KEY}" "Version"
  ${Else}
    ReadRegStr $INST_DIR_OLD HKLM "${PRODUCT_KEY}" "InstallDir"
    ${If} $INST_DIR_OLD != ""
      StrCpy $INST_SCOPE_OLD "global"
      ReadRegStr $INST_VER_OLD HKLM "${PRODUCT_KEY}" "Version"
    ${EndIf}
  ${EndIf}
FunctionEnd

; Elevate + wait (installer): relaunch $EXE runas with --elevated [/UPDATE],
; then watch the child: the exit-code file (primary) + the child's PID (the
; process poll - see the header for the contract).
; PROBE-PROVEN (probe6, 2026-10-08): on this distro the two-call
; ShellExecuteExW (SHELLEXECUTEINFO struct) approach ALWAYS returned FALSE
; (ret=0) - its temp strings are freed before the second System::Call
; dereferences the struct - while a single-call ShellExecuteW returns a valid
; HINSTANCE (ret=42) for BOTH "open" and "runas". The struct approach is
; therefore abandoned. Also probe-proven: GetLastError() after a System::Call is
; a RED HERRING here - it reads 80 on EVERY call, even the successful ones, so
; the launch result must be judged by the ShellExecuteW RETURN VALUE.
; PATH (probe8/probe9-proven): on this distro $EXEPATH is the FULL exe path
; (directory + filename), so the relaunch path is built as $EXEDIR\$EXEFILE -
; $EXEPATH\$EXEFILE double-appends the filename and yields SE_ERR_FNF (ret=2).
; System-plugin register model (marker-proven on this distro): 'i.rN' writes the
; result to the numbered register $N - the script must read $N (NOT $RN, a
; separate register set); register args are passed as 'p rN'/'i rN' (no $).
; ShellExecuteW "runas" returns an HINSTANCE > 32 on success; on a UAC DECLINE
; it returns SE_ERR_ACCESSDENIED (5) (GetLastError may also be 1223 /
; ERROR_CANCELLED) -> exit code 1223; any other launch failure is a clean abort
; (exit 1) - the installer never continues in-process with the global scope.
Function ScopeElevate
  StrCpy $R1 "--elevated"
  ${If} $IS_UPDATE == "1"
    StrCpy $R1 "$R1 /UPDATE"
  ${EndIf}
  ; Path + args are passed as REGISTER REFERENCES (t R3 / t R1): this distro's
  ; System plugin parses escape sequences inside quoted 't "..."' parameters
  ; (marker-proven) - register strings are passed raw, no parsing.
  ; CASE MATTERS (probe31-proven, 2026-10-08): System plugin register sources
  ; are r0-r9 = $0-$9 and R0-R9 = $R0-$R9 (System.html L374-380). The path is
  ; in $R3, so it must be passed as 't R3' (uppercase); 't r3' would pass the
  ; empty numbered register $3 and ShellExecuteW fails (ret=31, probe31:
  ; lstrlenW(t r3)=0 vs lstrlenW(t R3)=96).
  StrCpy $R3 "$EXEDIR\$EXEFILE"
  System::Call 'shell32::ShellExecuteW(p 0, t "runas", t R3, t R1, p 0, i 1)i.r2'
  System::Call 'kernel32::GetLastError()i.r7'
  ${If} $2 < 32
    ${If} $2 == 5
      SetErrorLevel 1223
      Quit
    ${ElseIf} $7 == 1223
      SetErrorLevel 1223
      Quit
    ${Else}
      MessageBox MB_ICONSTOP "Could not start the elevated installer (ShellExecuteW error $2, GetLastError $7)."
      SetErrorLevel 1
      Quit
    ${EndIf}
  ${EndIf}
  HideWindow
  Delete "$TEMP\${EXITCODE_NAME}"
  Delete "$TEMP\${PID_NAME}"
  ; --- Phase 1: the child's PID file (short grace - see the header). The
  ;     child writes its own $PID on its welcome-page PRE, seconds after the
  ;     launch (the UAC prompt already resolved before the ShellExecuteW above
  ;     returned).
  StrCpy $R9 "0"
  StrCpy $R8 ""
ElevPid:
  ${If} $R9 >= ${PID_WAIT_POLLS}
    ; no PID file within the grace: the child never reached its welcome page
    ; (vanished before reporting) - exit 4 (the helper's failure marker)
    SetErrorLevel 4
    Quit
  ${EndIf}
  FileOpen $R5 "$TEMP\${PID_NAME}" r
  ${If} $R5 != -1
    FileRead $R5 $R8
    FileClose $R5
    ${If} $R8 != ""
      IntOp $8 $R8 + 0
      ${If} $8 > 0
        Goto ElevPidGot
      ${EndIf}
    ${EndIf}
  ${EndIf}
  IntOp $R9 $R9 + 1
  Sleep 500
  Goto ElevPid
ElevPidGot:
  ; --- Phase 2: watch the child until it reports its exit code (the file) or
  ;     dies (GetExitCodeProcess stops reporting 259 - probe-proven on this
  ;     distro: 259 = the child is still alive).
  StrCpy $R9 "0"
ElevWait:
  IfFileExists "$TEMP\${EXITCODE_NAME}" ElevGot
  ${If} $R0 > 0
    System::Call 'kernel32::GetExitCodeProcess(p r0)i.r1'
    ${If} $1 != 259
      Goto ElevChildGone
    ${EndIf}
  ${Else}
    ; no handle yet: open the child (retried - the PID is in numeric $8;
    ; PROCESS_QUERY_INFORMATION = 1024 = 0x0400)
    System::Call 'kernel32::OpenProcess(i 1024, i 0, i r8)i.r0'
  ${EndIf}
  IntOp $R9 $R9 + 1
  ${If} $R9 >= ${ELEVATED_WAIT_POLLS}
    ; the bound passed without a report. With a live-handle check in place a
    ; vanished child would have been caught (ElevChildGone) - reaching here
    ; means either the child is still alive (a user left the wizard open -
    ; treated as a cancel -> 1, no failure marker) or no handle could be
    ; obtained at all (OpenProcess denied by the integrity boundary - the
    ; documented degradation: cancel and death are indistinguishable -> 1)
    ${If} $R0 > 0
      System::Call 'kernel32::CloseHandle(i r0)i.r6'
    ${EndIf}
    SetErrorLevel 1
    Quit
  ${EndIf}
  Sleep 500
  Goto ElevWait
ElevChildGone:
  ; The child is GONE (GetExitCodeProcess reported its exit code in $1) and its
  ; exit file is absent. Relay its documented codes as-is (0 success / 1 user
  ; cancel / 2 remove - a cancel never writes the file), anything else is 4
  ; (the child vanished - crash or kill -> the helper's failure marker).
  ${If} $R0 > 0
    System::Call 'kernel32::CloseHandle(i r0)i.r6'
  ${EndIf}
  Delete "$TEMP\${PID_NAME}"
  ${If} $1 == 0
    StrCpy $R2 "0"
  ${ElseIf} $1 == 1
    StrCpy $R2 "1"
  ${ElseIf} $1 == 2
    StrCpy $R2 "2"
  ${Else}
    StrCpy $R2 "4"
  ${EndIf}
  SetErrorLevel $R2
  Quit
ElevGot:
  FileOpen $R8 "$TEMP\${EXITCODE_NAME}" r
  FileRead $R8 $R2
  FileClose $R8
  Delete "$TEMP\${EXITCODE_NAME}"
  Delete "$TEMP\${PID_NAME}"
  ${If} $R0 > 0
    System::Call 'kernel32::CloseHandle(i r0)i.r6'
  ${EndIf}
  IntOp $R2 $R2 + 0
  SetErrorLevel $R2
  Quit
FunctionEnd

; The REMOVE choice on the RRU page: launch the existing uninstaller
; interactively, wait, then exit the installer with code 2
Function RRUDoRemove
  StrCpy $R1 "$INST_DIR_OLD\Uninstall ${PRODUCT_NAME}.exe"
  IfFileExists "$R1" RRUDoRemoveRun
  Goto RRUDoRemoveEnd
RRUDoRemoveRun:
  ExecWait '"$R1"'
RRUDoRemoveEnd:
  SetErrorLevel 2
  Quit
FunctionEnd

; Welcome page PRE hook (installer): the ELEVATED CHILD records its own
; process ID in the temp PID file - the non-elevated parent reads it after its
; ShellExecuteW launch to watch the child (OpenProcess + GetExitCodeProcess -
; see ScopeElevate). The parent deleted any stale file just before launching,
; so the file written here is fresh. The hook fires before the welcome page is
; shown (MUI_PAGE_CUSTOMFUNCTION_PRE) - the earliest point of this process.
; QUIRK (compile-proven 2026-10-08): this distro has no native $PID variable
; (makensis warning 6000 on "$PID") - the process ID comes from kernel32
; GetCurrentProcessId (probe-proven: it lands in numeric $0).
Function InstallWelcomePre
  ${If} $ELEVATED == "1"
    System::Call 'kernel32::GetCurrentProcessId()i.r0'
    StrCpy $R2 "$0"
    FileOpen $R1 "$TEMP\${PID_NAME}" w
    ${If} $R1 != -1
      FileWrite $R1 "$R2"
      FileClose $R1
    ${EndIf}
  ${EndIf}
FunctionEnd

; Substring search (uninstaller copy): $R0 = haystack, $R1 = needle, result in
; $R2 ("1"/"0") - uninstaller code may only Call un.-prefixed functions
Function un.CmdContains
  StrCpy $R2 "0"
  StrLen $R3 $R0
  StrLen $R4 $R1
  IntOp $R5 $R3 - $R4
  StrCpy $R6 "0"
UnCmdLoop:
  ${If} $R6 > $R5
    Return
  ${EndIf}
  StrCpy $R7 $R0 $R4 $R6
  ${If} $R7 == $R1
    StrCpy $R2 "1"
    Return
  ${EndIf}
  IntOp $R6 $R6 + 1
  Goto UnCmdLoop
FunctionEnd

; Elevate + wait (uninstaller): relaunch $EXE runas with --uninst-elevated,
; then poll the uninstall propagation file for the child's exit code.
; PROBE-PROVEN (probe6/probe9, 2026-10-08): single-call ShellExecuteW (the
; two-call ShellExecuteExW struct returned FALSE on this distro) with the path
; built as $EXEDIR\$EXEFILE ($EXEPATH already includes the filename, so
; $EXEPATH\$EXEFILE double-appends it -> SE_ERR_FNF). Success is HINSTANCE > 32;
; a UAC DECLINE returns SE_ERR_ACCESSDENIED (5) (or GetLastError 1223) -> exit
; code 1223; any other failure is a clean abort (exit 1).
Function un.UnScopeElevate
  StrCpy $R1 "--uninst-elevated"
  ; Path + args as REGISTER REFERENCES (t R3 / t R1): this distro's System
  ; plugin parses escapes inside quoted 't "..."' parameters (marker-proven);
  ; register strings are passed raw. CASE MATTERS (probe31-proven): R3 = $R3
  ; (the path), r3 = $3 (empty) - see the ScopeElevate note.
  StrCpy $R3 "$EXEDIR\$EXEFILE"
  System::Call 'shell32::ShellExecuteW(p 0, t "runas", t R3, t R1, p 0, i 1)i.r2'
  System::Call 'kernel32::GetLastError()i.r7'
  ${If} $2 < 32
    ${If} $2 == 5
      SetErrorLevel 1223
      Quit
    ${ElseIf} $7 == 1223
      SetErrorLevel 1223
      Quit
    ${Else}
      MessageBox MB_ICONSTOP "Could not start the elevated uninstaller (ShellExecuteW error $2, GetLastError $7)."
      SetErrorLevel 1
      Quit
    ${EndIf}
  ${EndIf}
  HideWindow
  Delete "$TEMP\${UN_EXITCODE_NAME}"
  StrCpy $R1 "0"
UElevWait:
  IfFileExists "$TEMP\${UN_EXITCODE_NAME}" UElevGot
  IntOp $R1 $R1 + 1
  ${If} $R1 >= ${ELEVATED_WAIT_POLLS}
    SetErrorLevel 1
    Quit
  ${EndIf}
  Sleep 500
  Goto UElevWait
UElevGot:
  FileOpen $R8 "$TEMP\${UN_EXITCODE_NAME}" r
  FileRead $R8 $R2
  FileClose $R8
  Delete "$TEMP\${UN_EXITCODE_NAME}"
  IntOp $R2 $R2 + 0
  SetErrorLevel $R2
  Quit
FunctionEnd

; Running-app guard by PROCESS NAME (the PRIMARY uninstaller running-app
; check): "1" in $R0 when ANY process whose image file name equals
; ${LAUNCHER_NAME} is running - in ANY directory, not only $INSTDIR -
; "0" otherwise. WHY name-based (live bug 2026-10-08): the path-scoped
; FileOpen lock check in un.onInit only sees an instance living in $INSTDIR;
; a live instance from another location (a dev :app:run launch, another
; scope's install dir) still runs the same app image for this user and
; would make the file deletion fail mid-way - the silent half-uninstall
; risk the lock check exists for. The in-app updater flow never trips this
; guard: its helper waits for the app to exit before launching the
; installer.
; Implementation (probe-proven on this distro, 2026-10-08): Toolhelp32
; snapshot (kernel32 CreateToolhelp32Snapshot TH32CS_SNAPPROCESS +
; Process32FirstW / Process32NextW), walking PROCESSENTRY32W. This
; distro's makensis is a 32-BIT compiler (PE machine 0x014C, x86-unicode
; stubs only - the built uninstaller stubs are 32-bit too), so the struct
; is the x86 layout: 9 x DWORD (36 bytes) then szExeFile WCHAR[260] -
; dwSize = 556, total 556 bytes (the x64 layout - 560 - would be rejected
; by the 32-bit API: probe-observed ERROR_INVALID_DATA 80). Probe-proven
; quirks of this System plugin build: the struct pointer must live in a
; NUMERIC register ($9 - "p r9" passes $9, the $R9 string register is a
; different cell and yields a garbage pointer), and szExeFile is read with
; the struct-only "&w260" field type ("t" reads an empty string here). The
; walk skips the 9 DWORDs sequentially (the plugin has no offset
; addressing). A snapshot failure fails OPEN ("0"): Process32FirstW
; returns FALSE for the invalid handle, and the secondary FileOpen lock
; check in un.onInit still covers the $INSTDIR case. MUST run from a PAGE
; CALLBACK (un.UnWelcomePre) - System::Call in the un.onInit context HANGS
; in this distro (marker-proven).
Function un.UnAppRunning
  StrCpy $R0 "0"
  System::Alloc 556
  Pop $9
  System::Call "*$9(i 556)"
  System::Call "kernel32::CreateToolhelp32Snapshot(i 0x00000002, i 0)i.r8"
  System::Call "kernel32::Process32FirstW(i r8, p r9)i.r0"
  ${If} $0 = 0
    Goto UnAppDone
  ${EndIf}
UnAppNext:
  System::Call "*$9(i,i,i,i,i,i,i,i,i,&w260 .R3)"
  StrCmp $R3 "${LAUNCHER_NAME}" UnAppFound
  System::Call "kernel32::Process32NextW(i r8, p r9)i.r0"
  ${If} $0 = 1
    Goto UnAppNext
  ${EndIf}
  Goto UnAppDone
UnAppFound:
  StrCpy $R0 "1"
UnAppDone:
  System::Call "kernel32::CloseHandle(i r8)i.r6"
  System::Free $9
FunctionEnd

; ============================================================================
; .onInit (installer startup) + un.onInit (uninstaller startup)
; ============================================================================

Function .onInit
  StrCpy $ELEVATED "0"
  StrCpy $IS_UPDATE "0"
  StrCpy $CHOSEN_SCOPE ""
  StrCpy $RRU_CHOICE "upgrade"
  StrCpy $EXITCODE_OUT ""
  StrCpy $INST_SCOPE_OLD ""
  StrCpy $INST_DIR_OLD ""
  StrCpy $INST_VER_OLD ""
  StrCpy $INSTALLDATE ""
  ; Flags from the command line ($CMDLINE = the full command line)
  StrCpy $R0 "$CMDLINE"
  StrCpy $R1 "--elevated"
  Call CmdContains
  ${If} $R2 == "1"
    StrCpy $ELEVATED "1"
  ${EndIf}
  StrCpy $R0 "$CMDLINE"
  StrCpy $R1 "/UPDATE"
  Call CmdContains
  ${If} $R2 == "1"
    StrCpy $IS_UPDATE "1"
  ${EndIf}
  ; Silent-scope flag (/SCOPE=peruser|global). In interactive runs the scope
  ; page always (re)sets both the scope and $INSTDIR; the flag only matters
  ; when the pages are skipped (/S - the smoke test / future CI silent
  ; installs). It sets $INSTDIR too, because this NSIS distro does not apply
  ; the standard /D silent flag (probe: $INSTDIR ignored /D).
  StrCpy $R0 "$CMDLINE"
  StrCpy $R1 "/SCOPE=peruser"
  Call CmdContains
  ${If} $R2 == "1"
    StrCpy $CHOSEN_SCOPE "peruser"
    StrCpy $INSTDIR "$LOCALAPPDATA\${PRODUCT_NAME}"
  ${EndIf}
  StrCpy $R0 "$CMDLINE"
  StrCpy $R1 "/SCOPE=global"
  Call CmdContains
  ${If} $R2 == "1"
    StrCpy $CHOSEN_SCOPE "global"
    StrCpy $INSTDIR "$PROGRAMFILES\${PRODUCT_NAME}"
  ${EndIf}
FunctionEnd

; Uninstaller startup logic (scope detection + the running-app guard + the
; elevation-need flag). The documented uninstaller init callback is un.onInit
; (NSIS manual 4.7.2.2.2 - "called when the uninstaller is nearly finished
; initializing"); it runs before the first page. The actual elevation RELAUNCH
; happens on the welcome page PRE (un.UnWelcomePre) - System::Call in the
; un.onInit context HANGS in this distro (marker-proven), page callbacks are
; safe.
Function un.onInit
  StrCpy $UELEVATED "0"
  StrCpy $WipeData "0"
  StrCpy $UN_SCOPE "peruser"
  StrCpy $R0 "$CMDLINE"
  StrCpy $R1 "--uninst-elevated"
  Call un.CmdContains
  ${If} $R2 == "1"
    StrCpy $UELEVATED "1"
  ${EndIf}
  ; Scope detection + ACTUAL install-dir restore (live bug 2026-10-08): the
  ; uninstaller stub initializes $INSTDIR from the build-time default, NOT
  ; the directory the user actually installed to - the previous build never
  ; restored it, so a global uninstall deleted the registry keys but the
  ; rmdir targeted the wrong dir and orphaned the real global folder.
  ; InstallDir is written per scope at install time (HKLM for global, HKCU
  ; for per-user).
  ReadRegStr $R0 HKLM "${PRODUCT_KEY}" "InstallDir"
  StrCpy $R1 "$R0"
  ReadRegStr $R0 HKCU "${PRODUCT_KEY}" "InstallDir"
  ${If} $R1 != ""
    StrCpy $UN_SCOPE "global"
    StrCpy $INSTDIR "$R1"
  ${Else}
    ${If} $R0 != ""
      StrCpy $INSTDIR "$R0"
    ${EndIf}
  ${EndIf}
  ; --- Running-app guard (SECONDARY - path-scoped: it only sees an instance
  ;     in $INSTDIR; the PRIMARY process-name check, un.UnAppRunning, runs on
  ;     the welcome page PRE because System::Call hangs in this onInit context
  ;     in this distro), BEFORE the elevation decision (no UAC prompt is
  ;     consumed for an uninstall that cannot proceed): a locked launcher
  ;     means the app is still running - the deletion below would fail
  ;     mid-way (the exe locked, the registry removed - a silent half-uninstall).
  ;     Ask the user to close it and exit 1 (the existing cancel semantics -
  ;     "not done yet", no failure marker). A missing launcher (already gone,
  ;     or the stub default dir) is never locked - the guard is a no-op then.
  ;     The ELEVATED child re-runs this same check (the app may have been
  ;     closed between the parent's check and the child's launch - it passes
  ;     then; it is still open - the child shows the guard again, which is
  ;     correct) and reports exit 1 through the exit-code file so the
  ;     non-elevated parent relays it immediately instead of waiting out the
  ;     bound.
  IfFileExists "$INSTDIR\${LAUNCHER_NAME}" +2
  Goto UnLockGuardDone
  FileOpen $R9 "$INSTDIR\${LAUNCHER_NAME}" w
  ${If} $R9 = -1
    ${If} $UELEVATED == "1"
      FileOpen $R1 "$TEMP\${UN_EXITCODE_NAME}" w
      FileWrite $R1 "1"
      FileClose $R1
    ${EndIf}
    MessageBox MB_ICONSTOP "N-Zik Desktop Compagnon is running. Close it and click OK to retry."
    SetErrorLevel 1
    Quit
  ${Else}
    FileClose $R9
  ${EndIf}
UnLockGuardDone:
  ; A global-scope uninstall from a NON-elevated process must relaunch the
  ; uninstaller elevated with a VISIBLE UAC, unconditionally (same semantics as
  ; the installer's ScopeLeave, which has NO account-type gate). probe10-proven:
  ; this distro's UserInfo::GetAccountType returns "User", so an account-type
  ; gate would never fire from the folder launch while Control Panel launched
  ; the uninstaller already-elevated. The temp-file exit-code propagation
  ; (1223 on decline) is the installer's. The flag only: the relaunch itself
  ; is triggered by the welcome page PRE (un.UnWelcomePre) - System::Call
  ; hangs in this un.onInit context, and from the welcome PRE the parent's
  ; wizard window already exists, so the parent can HideWindow it right after
  ; launching the elevated child: one visible window at a moment.
  StrCpy $UN_NEED_ELEVATE "0"
  ${If} $UN_SCOPE == "global"
    ${If} $UELEVATED != "1"
      StrCpy $UN_NEED_ELEVATE "1"
    ${EndIf}
  ${EndIf}
FunctionEnd

; Welcome page PRE hook (uninstaller): probe12-proven to fire in this distro.
; It is the PRIMARY running-app guard point (un.UnAppRunning - BEFORE the
; elevation relaunch, so no UAC prompt is consumed for an uninstall that
; cannot proceed; the secondary path-scoped FileOpen lock check ran earlier
; in un.onInit but only sees the $INSTDIR instance) and the ELEVATION
; RELAUNCH POINT: the un.onInit context cannot call System::Call (hangs -
; marker-proven), the page callbacks can. On the parent (global scope,
; non-elevated) the relaunch below NEVER RETURNS - it Quits after the
; elevated child's exit code is propagated; the elevated child takes over
; (UELEVATED=1 -> UN_NEED_ELEVATE=0) and falls through this same hook -
; re-running the guard there (the app may have been closed between the
; parent's check and the child's launch - it passes then; it is still open -
; the child shows the guard again and reports exit 1 through the exit-code
; file so the non-elevated parent relays it immediately instead of waiting
; out the bound).
Function un.UnWelcomePre
  Call un.UnAppRunning
  ${If} $R0 == "1"
    ${If} $UELEVATED == "1"
      FileOpen $R1 "$TEMP\${UN_EXITCODE_NAME}" w
      FileWrite $R1 "1"
      FileClose $R1
    ${EndIf}
    MessageBox MB_ICONSTOP "N-Zik Desktop Compagnon is running. Close it and click OK to retry."
    SetErrorLevel 1
    Quit
  ${EndIf}
  ${If} $UN_NEED_ELEVATE == "1"
    Call un.UnScopeElevate
  ${EndIf}
FunctionEnd

; ============================================================================
; Install pages
; ============================================================================

!define MUI_PAGE_CUSTOMFUNCTION_PRE "InstallWelcomePre"
!insertmacro MUI_PAGE_WELCOME

Page custom RRUShow RRULeave

Page custom ScopeShow ScopeLeave

!insertmacro MUI_PAGE_DIRECTORY

!insertmacro MUI_PAGE_INSTFILES

!define MUI_PAGE_CUSTOMFUNCTION_LEAVE "FinishLeave"
!insertmacro MUI_PAGE_FINISH

; ============================================================================
; RRU page (existing install + NOT /UPDATE)
; ============================================================================

Function RRUShow
  Call DetectInstalled
  ; /UPDATE (the in-app updater path): the RRU page is SKIPPED - with one gate:
  ; when an install exists and its version is >= the incoming one (a same-
  ; version or older re-install - the updater downloaded an older build), the
  ; user is asked "reinstall anyway?" first - No = exit 1 (a normal decline,
  ; no failure marker). The compare is NUMERIC (VerCompare - major / minor /
  ; patch / build), never lexicographic.
  ${If} $IS_UPDATE == "1"
    ${If} $INST_SCOPE_OLD != ""
      StrCpy $R0 "$INST_VER_OLD"
      StrCpy $R1 "${APP_VERSION}"
      Call VerCompare
      ${If} $R2 != "-1"
        ; mode flags pipe-joined (this distro's MessageBox parser rejects the
        ; space-separated two-flag form - compile-proven 2026-10-08)
        MessageBox MB_YESNO|MB_ICONQUESTION "Version $INST_VER_OLD of ${PRODUCT_NAME} is already installed. Reinstall version ${APP_VERSION} anyway?"
        ; $R1 = the clicked ID (this distro has no IDYES/IDNO constants -
        ; compile-proven: makensis warning 6000 on ${IDYES}); the Win32 IDs
        ; are IDYES = 6, IDNO = 7
        ${If} $R1 != 6
          SetErrorLevel 1
          Quit
        ${EndIf}
      ${EndIf}
    ${EndIf}
    ; Skip: a show function that returns without showing a dialog skips the
    ; page (NSIS manual 4.5.3 + the "Skipping Pages" FAQ)
    Return
  ${EndIf}
  ${If} $INST_SCOPE_OLD == ""
    Return
  ${EndIf}
  nsDialogs::Create 1018
  Pop $R0
  ; nsDialogs::Create returns the string "error" (not 0) on failure - the
  ; official check per the plugin Readme; Abort in the show function skips
  ; the page
  ${If} $R0 == error
    Abort
  ${EndIf}
  ${NSD_CreateLabel} 5 5 250 16 "A previous installation was found"
  Pop $R1
  ; The version + product name are long: two single-line labels (multiline
  ; EDIT/MLText controls render empty in this NSIS distro - proven)
  ${NSD_CreateLabel} 5 23 260 14 "Version $INST_VER_OLD of"
  Pop $R2
  ${NSD_CreateLabel} 5 39 260 14 "${PRODUCT_NAME} is installed."
  Pop $R6
  ${NSD_CreateLabel} 5 61 260 16 "Choose what to do:"
  Pop $R6
  ${NSD_CreateFirstRadioButton} 15 79 240 16 "Upgrade to the new version"
  Pop $R3
  ${NSD_CreateAdditionalRadioButton} 15 97 240 16 "Repair (reinstall the same version)"
  Pop $R4
  ${NSD_CreateAdditionalRadioButton} 15 115 240 16 "Remove the existing installation"
  Pop $R5
  ${NSD_SetState} $R3 ${BST_CHECKED}
  nsDialogs::Show
  Pop $R0
FunctionEnd

Function RRULeave
  ; Read the radios NOW: in this distro the leave callback fires on the Next
  ; click while the show function is still blocked in nsDialogs::Show's
  ; message pump (marker-proven) - the show function's post-Show code runs
  ; AFTER the leave, so the selection must be resolved here, while the
  ; dialog still exists.
  StrCpy $RRU_CHOICE "upgrade"
  ${NSD_GetState} $R4 $R1
  ${If} $R1 == ${BST_CHECKED}
    StrCpy $RRU_CHOICE "repair"
  ${EndIf}
  ${NSD_GetState} $R5 $R1
  ${If} $R1 == ${BST_CHECKED}
    StrCpy $RRU_CHOICE "remove"
  ${EndIf}
  ${If} $RRU_CHOICE == "remove"
    Call RRUDoRemove
  ${EndIf}
  ; upgrade or repair -> proceed to the scope page; scope defaults to the existing
  StrCpy $CHOSEN_SCOPE "$INST_SCOPE_OLD"
FunctionEnd

; ============================================================================
; Scope choice page
; ============================================================================

Function ScopeShow
  Call DetectInstalled
  nsDialogs::Create 1018
  Pop $R0
  ; nsDialogs::Create returns the string "error" on failure (plugin Readme);
  ; Abort in the show function skips the page
  ${If} $R0 == error
    Abort
  ${EndIf}
  ${If} $ELEVATED == "1"
    ; Elevated child: scope is global, already elevated - informational only
    ; (single-line labels: multiline EDIT controls render empty here)
    ${NSD_CreateLabel} 5 5 250 16 "Installing for all users"
    Pop $R1
    ${NSD_CreateLabel} 5 23 260 14 "This installation is for all users (global scope),"
    Pop $R2
    ${NSD_CreateLabel} 5 39 260 14 "running with administrator rights."
    Pop $R6
    StrCpy $CHOSEN_SCOPE "global"
  ${Else}
    ${NSD_CreateLabel} 5 5 250 16 "Choose who can use this installation"
    Pop $R1
    ${NSD_CreateFirstRadioButton} 15 25 240 16 "Only me (default - no UAC prompt)"
    Pop $R2
    ; The long descriptions as two single-line labels each (auto-wrap, never
    ; clip - the multiline boxes rendered empty with stray scrollbars)
    ${NSD_CreateLabel} 27 43 235 14 "Installed in your user folder"
    Pop $R6
    ${NSD_CreateLabel} 27 59 235 14 "(%LOCALAPPDATA%). No admin rights needed."
    Pop $R6
    ${NSD_CreateAdditionalRadioButton} 15 77 240 16 "All users of this computer (global)"
    Pop $R4
    ${NSD_CreateLabel} 27 95 235 14 "Installed in Program Files, for all users."
    Pop $R5
    ${NSD_CreateLabel} 27 111 235 14 "A UAC prompt asks for admin rights."
    Pop $R6
    ; Preselect the existing scope when present, else per-user (the default)
    ${If} $INST_SCOPE_OLD == "global"
      ${NSD_SetState} $R4 ${BST_CHECKED}
    ${Else}
      ${NSD_SetState} $R2 ${BST_CHECKED}
    ${EndIf}
  ${EndIf}
  nsDialogs::Show
  Pop $R0
FunctionEnd

Function ScopeLeave
  ; Read the radios NOW: in this distro the leave callback fires on the Next
  ; click while the show function is still blocked in nsDialogs::Show's
  ; message pump (marker-proven: ScopeLeave ran between ScopeShow's
  ; create-result marker and its post-Show marker). The show function's
  ; post-Show code runs AFTER the leave, so the scope must be resolved here,
  ; while the dialog still exists - this is what makes $INSTDIR follow the
  ; scope (a stale "" fell into the Program Files else-branch before).
  ${If} $ELEVATED != "1"
    ${NSD_GetState} $R4 $R1
    ${If} $R1 == ${BST_CHECKED}
      StrCpy $CHOSEN_SCOPE "global"
    ${Else}
      StrCpy $CHOSEN_SCOPE "peruser"
    ${EndIf}
  ${Else}
    StrCpy $CHOSEN_SCOPE "global"
  ${EndIf}
  ; Cross-scope migration notice (the removal itself happens in the install section)
  ${If} $INST_SCOPE_OLD != ""
    ${If} $INST_SCOPE_OLD != $CHOSEN_SCOPE
      MessageBox MB_ICONINFORMATION "A $INST_SCOPE_OLD installation is already present. It will be removed first. Your user data (pairing, settings, audio cache) are kept."
    ${EndIf}
  ${EndIf}
  ; Elevation: global scope from a non-elevated process -> relaunch elevated
  ${If} $CHOSEN_SCOPE == "global"
    ${If} $ELEVATED != "1"
      Call ScopeElevate
    ${EndIf}
  ${EndIf}
  ; Default install dir per scope (the directory page still allows changing it)
  ${If} $CHOSEN_SCOPE == "peruser"
    StrCpy $INSTDIR "$LOCALAPPDATA\${PRODUCT_NAME}"
  ${Else}
    StrCpy $INSTDIR "$PROGRAMFILES\${PRODUCT_NAME}"
  ${EndIf}
FunctionEnd

; Exit code 0 on the finish-page leave (install already happened in the section)
Function FinishLeave
  StrCpy $EXITCODE_OUT "0"
  Call WriteExitCode
FunctionEnd

; ============================================================================
; Install section
; ============================================================================

Section "Install"
  ; --- Cross-scope migration: remove the OTHER scope first (its uninstaller,
  ;     silently - the wipe checkbox is OFF by default, so user data is kept -
  ;     then registry keys + leftover files). User data is NEVER touched.
  ${If} $INST_SCOPE_OLD != ""
    ${If} $INST_SCOPE_OLD != $CHOSEN_SCOPE
      StrCpy $R1 "$INST_DIR_OLD\Uninstall ${PRODUCT_NAME}.exe"
      IfFileExists "$R1" MigrateRun
      Goto MigrateSkip
MigrateRun:
      ExecWait '"$R1" /S'
MigrateSkip:
      ; Guarded: the other scope's dir may not exist (manual removal) - only
      ; rmdir when present. Uses 'cmd /c rmdir /s /q' (proven on this content
      ; in this distro where NSIS 'RMDir /r' leaves app\/runtime\ behind).
      ; Bare dir path - NO trailing backslash (probe: the trailing-backslash
      ; form returns NOT-FOUND on an existing dir in this distro).
      IfFileExists "$INST_DIR_OLD" +2
      Goto MigNoDir
      ExecWait 'cmd /c rmdir /s /q "$INST_DIR_OLD"'
MigNoDir:
      ; The OTHER scope's registry keys only (its scope's hive - NSIS roots
      ; are compile-time literals, so the branches are explicit)
      ${If} $INST_SCOPE_OLD == "peruser"
        DeleteRegKey HKCU "${UNINST_KEY}"
        DeleteRegKey HKCU "${PRODUCT_KEY}"
      ${Else}
        DeleteRegKey HKLM "${UNINST_KEY}"
        DeleteRegKey HKLM "${PRODUCT_KEY}"
      ${EndIf}
    ${EndIf}
  ${EndIf}

  ; --- Post-migration verification (the invariant: the removed scope is fully
  ;     gone - its install dir AND its registry keys). A global old scope
  ;     cannot be removed by a non-elevated process (the silent migration runs
  ;     the old uninstaller without its elevation relaunch), so the invariant
  ;     is checked and the install ABORTS with a clear message instead of
  ;     leaving two inconsistent scopes.
  ${If} $INST_SCOPE_OLD != ""
    ${If} $INST_SCOPE_OLD != $CHOSEN_SCOPE
      ${If} $INST_SCOPE_OLD == "peruser"
        ReadRegStr $R4 HKCU "${PRODUCT_KEY}" "InstallDir"
      ${Else}
        ReadRegStr $R4 HKLM "${PRODUCT_KEY}" "InstallDir"
      ${EndIf}
      ${If} $R4 != ""
        MessageBox MB_ICONSTOP "The previous installation (scope: $INST_SCOPE_OLD) could not be fully removed - its registry entry is still present. Re-run the installer."
        SetErrorLevel 1
        Quit
      ${EndIf}
      IfFileExists "$INST_DIR_OLD" E1DirStill
      Goto E1Ok
E1DirStill:
      MessageBox MB_ICONSTOP "The previous installation (scope: $INST_SCOPE_OLD) could not be fully removed - its directory is still present. Re-run the installer."
      SetErrorLevel 1
      Quit
E1Ok:
    ${EndIf}
  ${EndIf}

  ; --- Running-app guard: if a previous launcher is still present AND locked,
  ;     the app is running - the wipe below would fail mid-extraction. Ask the
  ;     user to close it and exit 1 (the updater helper treats that as "not
  ;     done yet"). A missing launcher (fresh install) is never locked.
  IfFileExists "$INSTDIR\${LAUNCHER_NAME}" +2
  Goto InstallGuardDone
  FileOpen $9 "$INSTDIR\${LAUNCHER_NAME}" w
  ${If} $9 = -1
    MessageBox MB_ICONSTOP "N-Zik Desktop Compagnon is running. Close it and click OK to retry."
    SetErrorLevel 1
    Quit
  ${Else}
    FileClose $9
  ${EndIf}
InstallGuardDone:

  ; --- Install / upgrade: replace the whole install dir (no ghost files survive
  ;     an upgrade). The app must be closed first (see the guard above) - the
  ;     in-app updater helper waits for the running instance to exit before
  ;     launching this installer, so the files are released.
  ; The wipe uses 'cmd /c rmdir /s /q' (proven on this content in this distro,
  ; where NSIS 'RMDir /r' leaves app\/runtime\ behind - user-proven) and only
  ; runs when the dir exists. Extraction success is verified with ground truth
  ; (the launcher on disk), not with the error flag (which is unreliable in
  ; this distro: it persists across successful commands).
  ; QUIRK (probe-proven 2026-10-08): IfFileExists "<dir>\\" (trailing backslash)
  ; returns NOT-FOUND on an EXISTING directory in this distro - so the guard
  ; must use the bare dir path, no trailing backslash (else the wipe/rmdir below
  ; is silently skipped and the install dir is orphaned).
  IfFileExists "$INSTDIR" DoWipe
  Goto DoExtract
DoWipe:
  ; No $LASTEXITCODE in this NSIS distro (makensis warning 6000 + binary scan:
  ; the symbol is absent from makensis and every stub) - cmd's output is
  ; captured in a transient file (empty output = success, otherwise the OS
  ; error text - the diagnostics); the capture is deleted right after.
  Delete "$TEMP\nzik-rmdir-out.txt"
  ExecWait 'cmd /c rmdir /s /q "$INSTDIR" > "$TEMP\nzik-rmdir-out.txt" 2>&1'
  Delete "$TEMP\nzik-rmdir-out.txt"
DoExtract:
  ; Extract (File auto-creates the output dir when the wipe removed it)
  SetOutPath "$INSTDIR"
  File /r "${APPIMAGE_DIR}\*.*"
  ; Ground-truth verification: the launcher is the critical shipped file -
  ; if it is not on disk, the extraction really failed.
  IfFileExists "$INSTDIR\${LAUNCHER_NAME}" ExtractedOk
  StrCpy $EXITCODE_OUT "3"
  Call WriteExitCode
  SetErrorLevel 3
  Quit
ExtractedOk:

  ; --- Shortcuts (parity with the jpackage windows{} config: the start menu
  ;     group "N-Zik" + the desktop shortcut)
  CreateDirectory "$SMPROGRAMS\N-Zik"
  CreateShortcut "$SMPROGRAMS\N-Zik\${PRODUCT_NAME}.lnk" "$INSTDIR\${LAUNCHER_NAME}" "" "$INSTDIR\${LAUNCHER_NAME}" 0
  CreateShortcut "$DESKTOP\${PRODUCT_NAME}.lnk" "$INSTDIR\${LAUNCHER_NAME}" "" "$INSTDIR\${LAUNCHER_NAME}" 0

  ; --- Install date (yyyyMMdd). This NSIS distro has no DateGet, so the local
  ;     date comes from kernel32 GetLocalTime (SYSTEMTIME). The System plugin
  ;     here does not parse the i2 (WORD) type (probe: empty registers), so the
  ;     16-byte struct is read as 32-bit dwords and split with bit ops:
  ;     dword0 = wYear | wMonth<<16, dword1 = wDayOfWeek | wDay<<16. Register
  ;     args use the doc form 'p r0' (no $); results land in numbered $N.
  System::Call '*(&i16)i.r0'
  System::Call 'kernel32::GetLocalTime(p r0)i.r1'
  System::Call '*$0(i .r2, i .r3, i .r4, i .r5)'
  IntOp $R2 $2 & 65535
  IntOp $R3 $2 / 65536
  IntOp $R4 $3 / 65536
  IntFmt $R5 "%04d" $R2
  IntFmt $R6 "%02d" $R3
  IntFmt $R7 "%02d" $R4
  StrCpy $INSTALLDATE "$R5$R6$R7"

  ; --- Registry: product-info key + uninstall entry. NSIS roots are literals,
  ;     so the per-user (HKCU) and global (HKLM) writes are separate branches.
  ${If} $CHOSEN_SCOPE == "peruser"
    WriteRegStr HKCU "${PRODUCT_KEY}" "DisplayName" "${PRODUCT_NAME}"
    WriteRegStr HKCU "${PRODUCT_KEY}" "Version" "${APP_VERSION}"
    WriteRegStr HKCU "${PRODUCT_KEY}" "Scope" "peruser"
    WriteRegStr HKCU "${PRODUCT_KEY}" "InstallDir" "$INSTDIR"
    WriteRegStr HKCU "${PRODUCT_KEY}" "InstallDate" "$INSTALLDATE"
    WriteRegStr HKCU "${PRODUCT_KEY}" "Publisher" "${VENDOR}"
    WriteRegStr HKCU "${UNINST_KEY}" "DisplayName" "${PRODUCT_NAME}"
    WriteRegStr HKCU "${UNINST_KEY}" "UninstallString" '"$INSTDIR\Uninstall ${PRODUCT_NAME}.exe"'
    WriteRegStr HKCU "${UNINST_KEY}" "DisplayIcon" '"$INSTDIR\${LAUNCHER_NAME}"'
    WriteRegStr HKCU "${UNINST_KEY}" "DisplayVersion" "${APP_VERSION}"
    WriteRegStr HKCU "${UNINST_KEY}" "Publisher" "${VENDOR}"
    WriteRegStr HKCU "${UNINST_KEY}" "InstallDate" "$INSTALLDATE"
    WriteRegDWORD HKCU "${UNINST_KEY}" "NoModify" 1
    WriteRegDWORD HKCU "${UNINST_KEY}" "NoRepair" 1
  ${Else}
    WriteRegStr HKLM "${PRODUCT_KEY}" "DisplayName" "${PRODUCT_NAME}"
    WriteRegStr HKLM "${PRODUCT_KEY}" "Version" "${APP_VERSION}"
    WriteRegStr HKLM "${PRODUCT_KEY}" "Scope" "global"
    WriteRegStr HKLM "${PRODUCT_KEY}" "InstallDir" "$INSTDIR"
    WriteRegStr HKLM "${PRODUCT_KEY}" "InstallDate" "$INSTALLDATE"
    WriteRegStr HKLM "${PRODUCT_KEY}" "Publisher" "${VENDOR}"
    WriteRegStr HKLM "${UNINST_KEY}" "DisplayName" "${PRODUCT_NAME}"
    WriteRegStr HKLM "${UNINST_KEY}" "UninstallString" '"$INSTDIR\Uninstall ${PRODUCT_NAME}.exe"'
    WriteRegStr HKLM "${UNINST_KEY}" "DisplayIcon" '"$INSTDIR\${LAUNCHER_NAME}"'
    WriteRegStr HKLM "${UNINST_KEY}" "DisplayVersion" "${APP_VERSION}"
    WriteRegStr HKLM "${UNINST_KEY}" "Publisher" "${VENDOR}"
    WriteRegStr HKLM "${UNINST_KEY}" "InstallDate" "$INSTALLDATE"
    WriteRegDWORD HKLM "${UNINST_KEY}" "NoModify" 1
    WriteRegDWORD HKLM "${UNINST_KEY}" "NoRepair" 1
  ${EndIf}

  WriteUninstaller "$INSTDIR\Uninstall ${PRODUCT_NAME}.exe"
SectionEnd

; ============================================================================
; Uninstall pages
; ============================================================================

; Uninstaller startup logic: scope detection + the running-app guard + the
; elevation-need flag in un.onInit; the actual elevation relaunch in the
; welcome page PRE hook (System::Call is unsafe in the un.onInit context in
; this distro).
!define MUI_PAGE_CUSTOMFUNCTION_PRE "un.UnWelcomePre"
!insertmacro MUI_UNPAGE_WELCOME

PageEx un.custom
  PageCallbacks un.UnWipeShow un.UnWipeLeave
PageExEnd

!insertmacro MUI_UNPAGE_CONFIRM

; The uninstall section runs when the uninstaller reaches this (final) page.
UninstPage instfiles

; --- Language: English (built-in MUI) - inserted after ALL the page macros
;     (install + uninstall). The per-language MUI chrome strings are supplied by
;     MUI itself; the custom page texts are English. Additional OS languages can
;     be added later by extending MUI_LANGUAGE + LangString - a future option,
;     not implemented. ---
!insertmacro MUI_LANGUAGE English

; ============================================================================
; Uninstall wipe-checkbox page (UNCHECKED BY DEFAULT)
; ============================================================================

Function un.UnWipeShow
  nsDialogs::Create 1018
  Pop $R0
  ; nsDialogs::Create returns the string "error" (not 0) on failure - the
  ; official check per the plugin Readme; Abort in the show function skips
  ; the page
  ${If} $R0 == error
    Abort
  ${EndIf}
  StrCpy $WipeData "0"
  ${NSD_CreateLabel} 5 5 250 16 "Uninstall ${PRODUCT_NAME}"
  Pop $R1
  ; The long description as two single-line labels (multiline EDIT/MLText
  ; controls render empty in this NSIS distro - proven)
  ${NSD_CreateLabel} 5 23 260 14 "Your user data (pairing, settings, audio cache)"
  Pop $R2
  ${NSD_CreateLabel} 5 39 260 14 "are kept by default."
  Pop $R6
  ${NSD_CreateLabel} 5 57 260 14 "Data folder:"
  Pop $R6
  ; The full data path as a single-line EDIT (auto-hscrolls, renders fine)
  ${NSD_CreateText} 5 73 260 16 "$APPDATA\${DATA_DIR_NAME}"
  Pop $R3
  ; UNCHECKED by default (the NSD default state)
  ${NSD_CreateCheckBox} 5 95 260 16 "Also delete user data"
  Pop $R4
  ${NSD_CreateLabel} 5 113 260 14 "(pairing, settings, audio cache - never touched"
  Pop $R5
  ${NSD_CreateLabel} 5 129 260 14 "by a normal uninstall)"
  Pop $R6
  nsDialogs::Show
  Pop $R0
FunctionEnd

; Read the checkbox in the leave callback: in this distro the leave fires on
; the Next click while the show function is still in the nsDialogs::Show
; message pump (marker-proven on the scope page) - by the time the show
; function's post-Show tail runs, the dialog (and the checkbox) is already
; destroyed, so a GetState there would always read unchecked.
Function un.UnWipeLeave
  StrCpy $WipeData "0"
  ${NSD_GetState} $R4 $R1
  ${If} $R1 == ${BST_CHECKED}
    StrCpy $WipeData "1"
  ${EndIf}
FunctionEnd

; ============================================================================
; Uninstall section
; ============================================================================

Section "Uninstall"
  ; Coexistence guard: the OTHER scope must have no live install - this
  ; uninstaller removes only the registry keys of ITS OWN scope (below), so
  ; removing this scope while the other is still installed would leave a
  ; broken state (the other scope's app files with this scope's keys gone).
  ; Abort with a clear message (the user removes the other scope first).
  ${If} $UN_SCOPE == "peruser"
    ReadRegStr $R4 HKLM "${PRODUCT_KEY}" "InstallDir"
  ${Else}
    ReadRegStr $R4 HKCU "${PRODUCT_KEY}" "InstallDir"
  ${EndIf}
  ${If} $R4 != ""
    MessageBox MB_ICONSTOP "Another installation of ${PRODUCT_NAME} (the other scope) is still present. Remove it first, then retry."
    Abort
  ${EndIf}

  Delete "$DESKTOP\${PRODUCT_NAME}.lnk"
  Delete "$SMPROGRAMS\N-Zik\${PRODUCT_NAME}.lnk"
  RMDir "$SMPROGRAMS\N-Zik"

  Delete "$INSTDIR\Uninstall ${PRODUCT_NAME}.exe"
  Delete "$INSTDIR\${LAUNCHER_NAME}"
  ; Guarded: the dir may be gone (manual removal). Uses 'cmd /c rmdir /s /q'
  ; (proven on this content in this distro where NSIS 'RMDir /r' leaves
  ; app\/runtime\ behind). Bare dir path - NO trailing backslash (probe: the
  ; trailing-backslash form returns NOT-FOUND on an existing dir in this
  ; distro, which silently skips this rmdir and orphans the install dir).
  ; No $LASTEXITCODE in this NSIS distro (makensis warning 6000 + binary scan:
  ; the symbol is absent from makensis and every stub) - cmd's output is
  ; captured in a transient file (empty output = success, otherwise the OS
  ; error text - the diagnostics); the capture is deleted right after.
  IfFileExists "$INSTDIR" +2
  Goto UnMigNoDir
  Delete "$TEMP\nzik-rmdir-out.txt"
  ExecWait 'cmd /c rmdir /s /q "$INSTDIR" > "$TEMP\nzik-rmdir-out.txt" 2>&1'
  Delete "$TEMP\nzik-rmdir-out.txt"
UnMigNoDir:

  ; Registry: the keys of THIS scope only (the coexistence guard above ensures
  ; the other scope has no install - its keys, if any, belong to it). A global
  ; uninstall runs elevated (the welcome-PRE relaunch), so the HKLM writes are
  ; possible here; a per-user uninstall needs no elevation for HKCU.
  ${If} $UN_SCOPE == "peruser"
    DeleteRegKey HKCU "${UNINST_KEY}"
    DeleteRegKey HKCU "${PRODUCT_KEY}"
  ${Else}
    DeleteRegKey HKLM "${UNINST_KEY}"
    DeleteRegKey HKLM "${PRODUCT_KEY}"
  ${EndIf}

  ${If} $WipeData == "1"
    ; User data: %APPDATA%\<product> (pairing.json, settings.json, cache\audio)
    ; Guarded: the data dir may not exist (the app was never run). Uses
    ; 'cmd /c rmdir /s /q' (proven on this content in this distro where NSIS
    ; 'RMDir /r' leaves files behind)
    IfFileExists "$APPDATA\${DATA_DIR_NAME}" +2
    Goto UnWipeNoDir
    ExecWait 'cmd /c rmdir /s /q "$APPDATA\${DATA_DIR_NAME}"'
UnWipeNoDir:
    ; The Windows Credential Manager entry (the device token - target name
    ; <product>, the same target the app writes with CredWrite)
    ExecWait 'cmd /c cmdkey /delete:"${CRED_TARGET}"'
  ${EndIf}

  ; The elevated child reports success to the non-elevated waiter
  ${If} $UELEVATED == "1"
    FileOpen $R1 "$TEMP\${UN_EXITCODE_NAME}" w
    FileWrite $R1 "0"
    FileClose $R1
  ${EndIf}
SectionEnd

OutFile "${OUT_FILE}"
