# AGENTS.md — N-Zik Desktop Compagnon

**Version:** 1.0.0 | **Last updated:** 2026-10-05

**MANDATORY: Read this file + rules/*.md before any task.**

> **Project identity:** Windows desktop companion for [N-Zik](https://github.com/N-Zik-Group/N-Zik). The **phone is the single source of truth** — it runs a local server on the Wi-Fi network and this app only talks to it (pairing, REST + WebSocket, artwork, streamed audio). **No business logic and no streaming stack live on the PC.** **Transitional product** — it will be replaced by a standalone N-Zik desktop app (see README.md). Wire contract with the phone: spec `spec-n-zik-pc-bridge` (contract 1.x) in the workspace `_bmad-output/`.

## Session Startup

1. Read this file entirely
2. Read ALL `rules/*.md` files
3. Match the user's language and announce the critical rules (see rules/WORKFLOW.md "Session Startup Sequence" + "Announcement Template")
4. Ask user via question tool: "Bug, feature, or something else?"
5. If bug or feature → ASK which IDE/tool (ONE at a time) AND which skill to use before loading anything (see rules/WORKFLOW.md Step 3a)

---

## ✅ Always Do

- Code → `app.n_zik.compagnon.*` ONLY (single package root, single `:app` module — there is no legacy namespace in this repo)
- Log with `java.util.logging.Logger` tagged by class name (no `println`/`printStackTrace` — see rules/CODE.md "Logging")
- Dispatch coroutines on `NzikDispatchers` named dispatchers only (`UI`, `PLAYBACK`, `MEDIA`, `DATA` — same name/shape as the phone app); fire-and-forget / app-lifetime scopes via `NzikDispatchers.fireAndForget()` — the object is planned, see rules/CODE.md "Coroutines & Dispatchers" (transition note); never a bare `Job()`
- Use version catalog refs (`gradle/libs.versions.toml`)
- The wire contract gates everything: hide or disable UI/actions without their contract feature; buttons without a contract route are `InertButton`/`InertMenuItem` carrying the phone's own title — shown, never faked (see rules/CODE.md "Wire Contract")
- Verify build passes after changes (`gradlew.bat build`)
- New features/bug fixes include at least one test
- Show evidence (diffs, test output) — never just claim "done"
- Match user's language for communication
- Code comments & commits in English

## ⚠️ Ask First

- Adding new dependencies not in `gradle/libs.versions.toml`
- Any change to the embedded VLC runtime config in `app/build.gradle.kts` (`vlcVersion`, `vlcZipUrl`, `vlcZipSha256`, `vlcRuntimeFiles`)
- Committing code (NEVER without human testing + approval)
- Commit mode at end of workflow: (1) Done+commit = `assets/notes/Done.txt` only, (2) Wait = nothing — ASK before editing ANY file (see rules/WORKFLOW.md Step 8d). This repo has NO version-bump / fastlane / Updater machinery — do not invent one
- Which IDE/tool to use (ask ONE at a time — see rules/BMAD-TOOLS.md for preferred list)

## 🚫 Never Do

- Put business logic or a streaming stack on the PC (the phone stays the source of truth — see the project identity above)
- Write the device token anywhere except the Windows Credential Manager (never in `pairing.json`, logs, `Done.txt`, README, or code — see rules/SECURITY.md)
- Write code before completing full BMAD workflow
- Skip BMAD workflow steps
- Skip the Step 8b code-review gate, or edit `assets/notes/Done.txt` before the user chose a commit mode (Step 8d) — exception: doc-only edits (rules/WORKFLOW.md "Doc-Only Exception") follow their own commit-approval flow and are NOT subject to the Step 8d mode question
- Commit without human approval
- Use `GlobalScope`, or `runBlocking` in production code WITHOUT a justification comment (allowed only when a synchronous API forces it — every usage must carry a comment explaining why)
- Introduce new raw `Dispatchers.IO`/`Dispatchers.Default`/`Dispatchers.Main` usages in app code (use `NzikDispatchers`), use a bare `Job()` for fire-and-forget/app-lifetime scopes (use `SupervisorJob` via `NzikDispatchers.fireAndForget()`), or `shutdown()`/close `NzikDispatchers` executors (app-lifetime, daemon threads — see rules/CODE.md "Coroutines & Dispatchers")
- Use `!!` operator unless justified with comment explaining why
- Commit VLC binaries (the runtime is downloaded at build time under `build/`) or change the pinned VLC SHA-256 without verification
- Edit `_bmad/` internals manually
- Force push or delete committed history

---

## Skill Discovery

**`{project-root}`** = the directory containing both `_bmad/` and `.agents/` folders (if only one exists, prefer `_bmad/`). This is the **workspace root** — the directory containing `_bmad/` — NOT the `N-Zik-Desktop-Compagnon/` subdirectory where this AGENTS.md lives. Go **up one level** from `N-Zik-Desktop-Compagnon/` to find it.

> **OpenCode path resolution:** Scripts are at `{project-root}/_bmad/scripts/`. If you're running from `N-Zik-Desktop-Compagnon/`, use `../_bmad/scripts/` or resolve to workspace root first.

**Skills location** (depends on your IDE):

> This table lists only the 3 preferred tools plus common alternatives. It is NOT the authoritative full list — if the user's IDE isn't shown here, **rules/BMAD-TOOLS.md is the source of truth** for all supported tools and their skills/global/commands directories. Never assume a tool is unsupported just because it's absent from this shorter table.

| IDE                  | Skills Path                                            | How to Load                     |
| -------------------- | ------------------------------------------------------ | ------------------------------- |
| OpenCode ⭐           | `{project-root}/.agents/skills/{skill-name}/SKILL.md`  | `@skills/{skill-name}`          |
| GitHub Copilot ⭐     | `{project-root}/.agents/skills/{skill-name}/SKILL.md`  | `LOAD the FULL {path}/SKILL.md` |
| Google Antigravity ⭐ | `{project-root}/.agent/skills/{skill-name}/SKILL.md`   | Direct read                     |
| Claude Code          | `{project-root}/.claude/skills/{skill-name}/SKILL.md`  | Direct read                     |
| Cursor               | `{project-root}/.agents/skills/{skill-name}/SKILL.md`  | Direct read                     |
| Codex                | `{project-root}/.agents/skills/{skill-name}/SKILL.md`  | Direct read                     |
| Other tools          | See `rules/BMAD-TOOLS.md`                              | See `rules/BMAD-TOOLS.md`       |

**When to use which skill:**

| Situation        | Skill                      | Then                 |
| ---------------- | -------------------------- | -------------------- |
| Bug fix          | `bmad-cis-problem-solving` | → `bmad-code-review` |
| New feature      | `bmad-build`               | → `bmad-code-review` |
| Architecture     | `bmad-architecture`        |                      |
| PRD/Requirements | `bmad-prd`                 |                      |
| UX Design        | `bmad-ux`                  |                      |
| Code Review      | `bmad-code-review`         |                      |
| Sprint Planning  | `bmad-sprint-planning`     |                      |

---

## Project Structure

```
N-Zik-Desktop-Compagnon/   ← git repo root (run gradlew.bat/git from here)
├── app/
│   ├── src/main/kotlin/app/n_zik/compagnon/   ★ ALL code lives here
│   │   ├── bridge/          contract layer: BridgeSession.kt (the WS session), pairing/ (PairingController, PairingListener, PairingModels,
│   │   │                    WindowsCredentialSecretStore, CredentialStore, RevocationPolicy), state/ (StateReducer, PlayerState,
│   │   │                    PlayerRepository/RemotePlayerRepository, ServerClock, BridgeStateMessages),
│   │   │                    library/ (LibraryRepository/RemoteLibraryRepository, PagedList, ArtworkLoader, LibraryModels),
│   │   │                    command/ (PlayWindow)
│   │   ├── components/      UI ported from the phone: ui/screens/ (home, album, artist, localplaylist, player, bridge, settings),
│   │   │                    player/ (Player, MiniPlayer, queue, seek bars, controls), menu/ (per-domain item menus),
│   │   │                    tab/ (chips, toolbars, search, sort), items/ (AlbumItem, ArtistItem, PlaylistItem, ItemContainer),
│   │   │                    styling/ (Dimensions, HomeItemSize), theme/ (ColorPalette, Typography), themed/ + settings/ + navigation/
│   │   ├── core/            network/ (BridgeClient + LibraryApi/PlayerApi/AudioApi, CandidateAddresses), navigation/ (BackNavigation),
│   │   │                    palette/ (pure-Kotlin port of androidx Palette), coil/ (ImageCacheFactory)
│   │   ├── playback/        vlc/ (VlcAudioEngine, VlcRuntime — embedded VLC 3.0.24), cache/ (AudioCache), services/ (LocalPlayback)
│   │   ├── enums/ + utils/  AudioQualityFormat, PairingMode, QueueLoopType…; Preferences, Toaster (formatMessage/formatText), PhoneDensity…
│   │   └── Main.kt, MainActivity.kt, AppInfo.kt, GlobalVars.kt
│   ├── src/main/composeResources/   drawables/ + font/ (Roboto, Rubik) + values/strings.xml (English, the ONLY strings file)
│   └── src/test/kotlin/             Tests mirroring the main package structure
├── gradle/libs.versions.toml        Version catalog
├── assets/notes/                    Done.txt + Changelog_Template.txt + TODO.txt (repo-root working files — NOT an app resources dir)
└── (WORKSPACE ROOT, one level above N-Zik-Desktop-Compagnon/: `_bmad/` + `_bmad-output/` BMAD installation + specs (incl. `spec-n-zik-pc-bridge`) ·
    `.agents/` `.agent/` `.claude/` skills · `docs/` reference projects READ-ONLY · `N-Zik/` the phone app — separate repo, NOT part of this one)
```

| What                | Where                                                                    |
| ------------------- | ------------------------------------------------------------------------ |
| Main code           | `app/src/main/kotlin/app/n_zik/compagnon/`                               |
| Contract (bridge)   | `bridge/` (pairing, state, library, command) + `core/network/` (BridgeClient) |
| Playback            | `playback/` (VLC engine, audio cache, local playback)                     |
| UI                  | `components/` (screens in `components/ui/screens/`, player in `components/player/`) |
| Strings (English)   | `app/src/main/composeResources/values/strings.xml` — single file, no `values-*` |
| Pairing persistence | token → Windows Credential Manager ("N-Zik Desktop Compagnon"); non-secret fields → `%APPDATA%\N-Zik Desktop Compagnon\pairing.json`; PC-local settings → `settings.json`; audio cache → `%APPDATA%\N-Zik Desktop Compagnon\cache\audio\` |
| Database            | none on the PC — the phone's local DB is the only database; schema work belongs to the N-Zik (phone) repo |
| Tests               | `app/src/test/kotlin/`                                                   |

---

## Build Commands

```bash
gradlew.bat build                                       # Compile + all tests (primary)
gradlew.bat :app:test                                   # Tests only
gradlew.bat :app:test --tests "app.n_zik.compagnon.SomeTest"   # Single test
gradlew.bat :app:run                                    # Launch the app
gradlew.bat clean build                                 # Clean + build
```

> **Windows:** this app and its toolchain are Windows-only — always `gradlew.bat`, run from the repo root `N-Zik-Desktop-Compagnon/`. JDK 21 (Gradle toolchain).
>
> **First build:** downloads the official VLC 3.0.24 win64 zip (about 83 MB) from download.videolan.org, checked against its pinned SHA-256; the zip is kept in the Gradle user home so `clean` does not re-download it — see rules/BUILD.md "Embedded VLC runtime".

HALT after 3 failed build attempts → report with full error log.

---

## BMAD Workflow

- **AGENTS.md wins:** code quality, security, commits, logging, persistence, build
- **BMAD wins:** workflow ordering, templates, checkpoints
- **Conflict:** AGENTS.md wins

→ See `rules/WORKFLOW.md` for full workflow enforcement.
→ See `rules/BMAD.md` for installation, config resolution.

---

## Rules Files

| File                  | Purpose                                               |
| --------------------- | ----------------------------------------------------- |
| `rules/CODE.md`       | Code quality, Kotlin/Compose Desktop patterns, dispatchers, wire contract, file placement |
| `rules/SECURITY.md`   | Device token, secrets, input validation, license checks, VLC runtime |
| `rules/RECOVERY.md`   | Build failures, skill failures, rollback              |
| `rules/BUILD.md`      | Gradle commands, embedded VLC, commit convention, testing |
| `rules/WORKFLOW.md`   | BMAD workflow step-by-step enforcement                |
| `rules/BMAD.md`       | BMAD config, skill customization, scripts             |
| `rules/BMAD-TOOLS.md` | IDE skill directories reference                       |
