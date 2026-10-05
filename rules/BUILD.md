# Build & Test Rules — N-Zik Desktop Compagnon

**Version:** 1.0.0 | **Last updated:** 2026-10-05

## Gradle Version Catalog

Use `gradle/libs.versions.toml` references. NEVER hardcode versions.

```kotlin
// GOOD
implementation(libs.ktor.client.cio)
implementation(libs.vlcj)

// BAD
implementation("io.ktor:ktor-client-cio:3.6.0")
```

If a needed library isn't in the catalog → HALT and ask user before adding to `libs.versions.toml`.

## Build Commands

```bash
gradlew.bat build                                          # Compile + all tests (primary)
gradlew.bat :app:test                                      # Tests only
gradlew.bat :app:test --tests "app.n_zik.compagnon.PagedListTest"   # Single test class
gradlew.bat :app:run                                       # Launch the app
gradlew.bat clean build                                    # Clean + build
```

> **Windows:** this project builds and runs on Windows only — use `gradlew.bat`, run from the repo root `N-Zik-Desktop-Compagnon/`. The workspace root (the parent of `N-Zik-Desktop-Compagnon/`, where `_bmad/` lives) is **not** a git/gradle project.
>
> **JDK 21** toolchain (set in `app/build.gradle.kts`). **First build** downloads the embedded VLC zip (about 83 MB, once — see "Embedded VLC runtime" below).

## Verification

ALWAYS verify changes compile before reporting. If build fails:

1. Read error messages
2. Fix the first error (often cascading)
3. Rebuild
4. **HALT after 3 failed attempts** — report to user with full error log (the 3-attempt counter is per autonomous cycle — it resets when the user gives a new explicit direction after a HALT)

```
BUILD FAILURE ESCALATION:
Attempt 1 → Fix obvious issue → Rebuild
Attempt 2 → Research error → Fix → Rebuild
Attempt 3 → HALT → Report to user with error log
```

### Done.txt Format

File: `assets/notes/Done.txt` — repo root (NOT an app resources dir); same folder holds `Changelog_Template.txt` and `TODO.txt`

When committing, update `Done.txt` using its own template (`Changelog_Template.txt` in the same folder):

```
<keyword>(<scope>): <short summary> (issue/commit ref if any)
  - Technical detail 1
  - Technical detail 2
```

Include full issue link (use `issue https://...` to avoid auto-closing) or the commit hash reference `` (`9f35d2c8`) ``.
Entries are grouped under the section headers defined by the template (`Hotfix:` / `Added:` / `Changed:` / `Improved:` / `Fixed:` / `Refactor:` / `Removed:` / `Deprecated:` / `Other:`) — place each entry under the matching section.
The entry keyword for Done.txt follows the template's sections — it is not automatically a commit type: e.g. `change(...)` is a valid Done.txt entry but NOT a valid commit message type; `improve(...)` is valid in both (see Commit Convention).

## Embedded VLC Runtime

Audio is played by vlcj 4.12.1 on an **embedded copy of VLC 3.0.24** — no VLC installation is needed, and **no VLC binary is ever committed to this repo**.

- `app/build.gradle.kts` pins `vlcVersion`, `vlcZipUrl` (the official VideoLAN zip) and `vlcZipSha256` (from the official `.sha256`); the `downloadVlc` task downloads once into the Gradle user home (`caches/n-zik-compagnon/vlc/`) and checks the hash, `extractVlc` keeps only the audio subset (libvlc, libvlccore, ~30 plugins, `COPYING.txt`) under `build/vlc-runtime/windows-x64/vlc`
- `prepareAppResources` depends on `extractVlc` — the runtime reaches the app both under `:app:run` and in `createDistributable` (via `appResourcesRootDir`)
- If the embedded VLC cannot load at runtime, the app degrades ("This PC" output disabled with a message) — that resilience is by design, do not remove it
- **NEVER change `vlcVersion`/`vlcZipUrl`/`vlcZipSha256`/`vlcRuntimeFiles` without the user's explicit approval** (AGENTS.md "Ask First") — a changed hash or plugin list breaks playback silently
- The pinned SHA-256 must match the official `vlc-3.0.24-win64.zip.sha256` — verify against download.videolan.org before touching it

### Packaging trial (opt-in)

`gradlew.bat test -PvlcTrial=<directory of audio samples>` runs `VlcRuntimeTrialTest`: it plays every sample from a file and over HTTP with the embedded runtime and prints the libvlc modules used. Use it when touching the VLC plugin list or the audio path; it is OFF by default (the task only activates with `-PvlcTrial`).

## Commit Convention

Format: `type(scope): short description`

| Type       | When to use                                |
| ---------- | ------------------------------------------ |
| `feat`     | New feature or capability                  |
| `fix`      | Bug fix                                    |
| `refactor` | Code restructuring without behavior change |
| `chore`    | Build, CI, dependency, or tooling changes  |
| `docs`     | Documentation changes only                 |
| `test`     | Adding or updating tests                   |
| `perf`     | Performance improvement                    |
| `improve`  | Incremental improvement/refinement of existing behavior (used widely in this repo's history) |

Examples:

```
feat(pairing): pair with the phone by QR or manual code
fix(player): tick the remaining-time label on the same second as the elapsed one
refactor(bridge): extract the WS close-code decision
chore(build): pin vlcj to 4.12.1
```

Rules:

- Under 72 characters
- Imperative mood ("add" not "added")
- Scope optional but recommended (this repo's history uses scopes like `pairing`, `player`, `library`, `queue`, `menu`, `ui`, `build`)
- No period at end
- Include GitHub issue URL when applicable — use `issue https://...` (avoid keywords that auto-close issues like "fixes" or "closes")

## Branching

- Work on `main` unless user specifies a branch
- Branch naming: `feat/<name>`, `fix/<name>`, `chore/<name>`
- If merge conflict → HALT, report to user

## Testing — JUnit 5 (Jupiter) + kotlinx-coroutines-test + Ktor test tools

Single framework: **JUnit 5 (Jupiter)** on the JUnit Platform (`useJUnitPlatform()`). There is no vintage engine, no Robolectric, no Compose UI test harness, and no mock framework in the catalog — fakes are the Ktor `MockEngine` (HTTP clients), a real CIO server on loopback (where a real socket is the subject), and `runTest` virtual time (timings). For a new test, mirror the framework of the test files it belongs to (check neighboring imports).

```kotlin
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PagedListTest {
    @Test
    fun `a failed page keeps the loaded ones and waits for retry`() = runTest {
        val list = PagedList(...)
        // ...
        assertEquals(100, list.loadedCount)
    }
}
```

Test files: `app/src/test/kotlin/` — mirror the source package structure exactly (e.g. `bridge/state/StateReducerTest.kt` tests `bridge/state/StateReducer.kt`).

New features/bug fixes should include at least one test. If the change is pure UI with no extractable logic, note it in the report (the codebase's convention is to extract the math — sheet snap, scrubber, duration indicator — into a pure function and test it).

## CI Expectations

- **This repo has no CI** — no `.github/` workflows, no pre-commit hooks. Tests are local-only: always run `gradlew.bat build` yourself before reporting
- Releases are **manual** GitHub releases (portable Windows EXE — spec `spec-n-zik-pc-bridge` story 13, tracked in `assets/notes/TODO.txt`); there is no signing/keystore machinery in this repo yet — if a release flow is added, the keystore handling must follow rules/SECURITY.md

## Code Formatting

- ktlint/detekt are **NOT configured** in this project — no lint task exists; do not search for one
- `kotlin.code.style=official` (gradle.properties)
- Follow existing file formatting patterns
- No trailing whitespace, newline at end of file
