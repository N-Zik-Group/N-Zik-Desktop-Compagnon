<!-- Thanks for the contribution! 🙌
     Fill in what applies to your PR and tick the matching boxes — leave the rest unchecked (don't delete them).
     Hard rules: AGENTS.md + rules/*.md at the repo root. -->

## 📋 What are you changing?
<!-- The problem, your solution, context and decisions. If similar work already exists, link it and explain how this PR differs. -->

## 🔗 Related issues
<!-- Avoid words that auto-close the issue ("fixes", "closes") — use the `issue https://...` format. -->
- Issue: `issue https://github.com/N-Zik-Group/N-Zik-Desktop-Compagnon/issues/…`

## 🚀 Type of change
<!-- Pick the type(s) matching the commit types in rules/BUILD.md -->
- [ ] ✨ New feature (`feat`)
- [ ] 🐛 Bug fix (`fix`)
- [ ] 📈 Incremental improvement of existing behavior (`improve`)
- [ ] ⚡ Performance (`perf`)
- [ ] 🧹 Refactor, no behavior change (`refactor`)
- [ ] 🧪 Tests only (`test`)
- [ ] 📖 Docs only (`docs`)
- [ ] 🛠️ Build / tooling / deps (`chore`)

## 🤖 Made with AI?
<!-- Tells us whether the 🤖 BMAD section of the checklist applies to this PR. -->
- [ ] 🤖 Yes — an AI agent helped produce this change (→ fill the 🤖 BMAD section below)
- [ ] 👤 No — I wrote it myself, no AI assistance

## 📸 Screenshots / video
<!-- Before/after for visual changes (image, GIF or MP4). Skip if not applicable. -->
| Before | After |
| ------ | ----- |
|        |       |

## ✅ How can we verify it?
<!-- Steps for a reviewer + concrete evidence: diffs, build/test output, screenshots. "It works" alone doesn't cut it 🙂 -->
1.
2.

---

## 🛠️ Checklist
> Tick every box that applies to your PR; leave the rest unchecked.

### ✅ Always (every PR)
- [ ] I tested this change myself (launched the app with `gradlew.bat :app:run`) before opening the PR
- [ ] No force push, no rewritten history
- [ ] Branch named `feat/…`, `fix/…` or `chore/…`
- [ ] Commits follow the repo convention — e.g. `feat(pairing): pair with the phone by QR or manual code`
  <!-- `type(scope): description` · English · imperative · under 72 chars · no final period · issue links as `issue https://...` -->

### 🤖 BMAD (only if "Made with AI" is ticked)
- [ ] I completed the full BMAD workflow — all 8 steps, including the final code review
- [ ] This PR is doc-only: it changes only Markdown/text prose — no code, no build files (even a code comment counts as code)
- [ ] `_bmad/` was not edited by hand

### 📝 Changelogs
- [ ] I added a `Done.txt` entry under the right section header — summary line **plus technical sub-bullets** (template: `assets/notes/Changelog_Template.txt`)

### 🏗️ Code
- [ ] New code lives in `app.n_zik.compagnon.*` (single `:app` module — there is no legacy namespace in this repo)
- [ ] No business logic or streaming stack added on the PC (the phone stays the source of truth)
- [ ] New UI/actions are gated on their contract feature — buttons without a contract route are `InertButton`/`InertMenuItem` with the phone's own title (shown, never faked)
- [ ] New strings go in `values/strings.xml` only (English) — I didn't touch any `values-*/` file (Crowdin-managed)
- [ ] This is a Crowdin sync PR — I said so in the description (exempt from BMAD + review)
- [ ] Coroutines run on `NzikDispatchers` only (`fireAndForget()` for fire-and-forget) — no `GlobalScope`, no raw `Dispatchers.*`, no `runBlocking` without a "why" comment
- [ ] Compose: `collectAsStateWithLifecycle()`, atomic `_state.update { }`, `key` + `contentType` on lazy lists
- [ ] I didn't add `!!` (any existing one has a comment explaining why)
- [ ] I log with a `java.util.logging.Logger` tagged by class name only — no `println` / `printStackTrace`
- [ ] Risky operations are wrapped in `runCatching`, and failures are logged
- [ ] I didn't touch the embedded VLC runtime config (`vlcVersion` / `vlcZipUrl` / `vlcZipSha256` / `vlcRuntimeFiles` in `app/build.gradle.kts`) without approval
- [ ] New dependencies go through `libs.versions.toml` (I asked before adding)
- [ ] No secrets in the diff — the device token never leaves the Windows Credential Manager (never in `pairing.json`, logs, `Done.txt`, README or code)
- [ ] External code is MIT/Apache-licensed, with the source cited

### 🧪 Build & tests
- [ ] `gradlew.bat build` is green (compile + all tests)
- [ ] New feature or bug fix → at least one new test, and `gradlew.bat :app:test` passes

## 🗒️ Anything else?
-
