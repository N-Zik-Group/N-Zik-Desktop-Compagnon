# Error Recovery & Rollback Rules — N-Zik Desktop Compagnon

**Version:** 1.0.0 | **Last updated:** 2026-10-05

**HALT semantics (applies to every HALT in every rule file):** HALT = stop ALL autonomous work (no edits, no builds, no commits, no skills). Present the required report, then wait for an explicit user instruction before resuming. The only exception is when the HALT rule itself prescribes follow-up actions — run them, then HALT again.

## Build Failure Recovery

1. Read error messages carefully
2. Fix the first error (often cascading)
3. Rebuild
4. **HALT after 3 failed attempts** — report to user with full error log

```
BUILD FAILURE ESCALATION:
Attempt 1 → Fix obvious issue → Rebuild
Attempt 2 → Research error → Fix → Rebuild
Attempt 3 → HALT → Report to user with error log
```

## BMAD Skill Failure

If a BMAD skill fails or gets stuck:

1. **Skill not found** → Search the IDE-specific skills directory for the user's IDE (see `rules/BMAD-TOOLS.md` table — most IDEs use `{project-root}/.agents/skills/`). Remember: `{project-root}` is the parent of `N-Zik-Desktop-Compagnon/` where `_bmad/` and `.agents/` live.
2. If still not found → HALT, inform user, and ask how to re-run the BMAD installer (no installer command is defined in these rules — see rules/BMAD.md "Installation Location")
3. **SKILL.md malformed** → HALT, report error, suggest `bmad-module-builder` to rebuild
4. **Skill execution error** → HALT, report the error, then ask the user via question tool: (1) retry the skill, (2) switch to `bmad-build` (implementation tasks only — user's explicit choice), (3) stop. Never switch skills without the user's answer.
5. **Agent stuck in loop** → HALT after 5 iterations, ask user

## Code Changes Break Existing Features

1. Revert uncommitted changes: `git checkout -- <file>`
2. Revert uncommitted work: `git stash`
3. Identify what broke
4. Fix incrementally, testing after each change
5. If unable to fix → HALT, report to user with diagnosis

## Known Flaky Test

`PairingListenerTest` ("real listener renews the requestId…") is a known pre-existing flaky test in the full suite (passes alone — a race between the real listener thread and the test dispatcher, recorded in `assets/notes/TODO.txt`). A failure there on otherwise-green code → do NOT burn the 3-attempt build counter chasing it: re-run once (or run the class alone), and if it fails again → HALT and report as the known flaky case instead of treating it as a regression.

## Network / Dependency Errors

1. Check internet connection
2. Verify Maven/Gradle repositories are accessible
3. Try `gradlew.bat --refresh-dependencies`
4. If proxy issue → HALT, inform user
5. If repository down → HALT, suggest using cached dependencies

## VLC Download Failure

Symptom: `downloadVlc` fails — HTTP error from download.videolan.org, or `VLC zip SHA-256 mismatch: expected …, got …`.

1. If the download failed (network) → re-run `gradlew.bat :app:build` once the connection is back; the zip is cached in the Gradle user home, so a successful download is never repeated
2. If the **SHA-256 mismatched** → HALT immediately. Do NOT update `vlcZipSha256` to match what was downloaded, do NOT disable the check. Verify the hash against the official `vlc-3.0.24-win64.zip.sha256` (download.videolan.org); a mismatch means either the download was corrupted (delete the cached zip and re-download) or the official file changed (ask the user before touching the pin — AGENTS.md "Ask First")
3. If `extractVlc` reports "VLC runtime incomplete, missing: …" → delete `app/build/vlc-runtime` and re-run; if it persists with a known-good zip → HALT, report to user

## Gradle Wrapper Issues

If `gradlew.bat` fails or is corrupted:

1. Check `gradlew.bat` and `gradle/wrapper/gradle-wrapper.jar` exist
2. Try `gradlew.bat --version` to verify the wrapper works
3. If corrupted → HALT, suggest re-cloning or re-downloading the wrapper
4. Never modify `gradle-wrapper.properties` without explicit instruction

## Corrupted _bmad/ Directory

1. **NEVER** manually edit `_bmad/` internals
2. Before deleting: back up `_bmad/custom/` (human-authored overrides) to a temp folder, then delete `_bmad/`, ask the user for the re-installation procedure (no installer command is defined in these rules — see rules/BMAD.md "Installation Location"), and restore `_bmad/custom/` after
3. Verify with `bmad-bmb-setup` skill

## Loop Detection

If you notice yourself repeating the same action:

1. Stop immediately
2. Count iterations — HALT at 5
3. Report to user: "I'm stuck in a loop doing [X]. Please advise."
4. Wait for user input before continuing

## General Rollback

- `git log --oneline -5` — find safe rollback point
- `git reset --soft HEAD~1` — undo last commit but keep changes staged (only if not pushed)
- **NEVER** force push without explicit user instruction
- **NEVER** delete committed history
