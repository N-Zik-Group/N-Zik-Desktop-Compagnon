# Build & Test Rules — N-Zik Desktop Compagnon

**Version:** 1.0.0 | **Last updated:** 2026-10-06

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
gradlew.bat :app:packageExe                                # Windows installer (see "Windows Installer" below)
./build.sh package          (Linux/WSL host)               # Linux .deb + .rpm + portable zip (see "Linux packaging" below)
```

> **Windows:** `gradlew.bat`, run from the repo root `N-Zik-Desktop-Compagnon/`. **Linux:** `./gradlew` (or `build.sh`) — local playback uses the **system libvlc** (VLC must be installed, e.g. `sudo apt install vlc`; the app shows the right package command per distro via `/etc/os-release`). The workspace root (the parent of `N-Zik-Desktop-Compagnon/`, where `_bmad/` lives) is **not** a git/gradle project.
>
> **JDK 21** toolchain (set in `app/build.gradle.kts`). **First build (Windows host)** downloads the embedded VLC zip (about 83 MB, once — see "Embedded VLC runtime" below); on a Linux host the download/extract tasks are disabled.

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

> **Windows host only:** `downloadVlc`/`extractVlc` are disabled on a non-Windows host (`isWindowsHost` in `app/build.gradle.kts`) and the win64 runtime never enters the Linux distributable (tar.gz via `createDistributable`). On Linux, local playback uses the **system libvlc** (e.g. `sudo apt install vlc`); `VlcRuntime` skips the embedded gate and loads the system `libvlc` through vlcj's `NativeDiscovery`, and the "This PC" unavailable message carries the distro's install command (spec `spec-linux-system-libvlc`).

- `app/build.gradle.kts` pins `vlcVersion`, `vlcZipUrl` (the official VideoLAN zip) and `vlcZipSha256` (from the official `.sha256`); the `downloadVlc` task downloads once into the Gradle user home (`caches/n-zik-compagnon/vlc/`) and checks the hash, `extractVlc` keeps only the audio subset (libvlc, libvlccore, ~30 plugins, `COPYING.txt`) under `build/vlc-runtime/windows-x64/vlc`
- `prepareAppResources` depends on `extractVlc` — the runtime reaches the app both under `:app:run` and in `createDistributable` (via `appResourcesRootDir`)
- If the embedded VLC cannot load at runtime, the app degrades ("This PC" output disabled with a message) — that resilience is by design, do not remove it
- **NEVER change `vlcVersion`/`vlcZipUrl`/`vlcZipSha256`/`vlcRuntimeFiles` without the user's explicit approval** (AGENTS.md "Ask First") — a changed hash or plugin list breaks playback silently
- The pinned SHA-256 must match the official `vlc-3.0.24-win64.zip.sha256` — verify against download.videolan.org before touching it

### Packaging trial (opt-in)

`gradlew.bat test -PvlcTrial=<directory of audio samples>` runs `VlcRuntimeTrialTest`: it plays every sample from a file and over HTTP with the embedded runtime and prints the libvlc modules used. Use it when touching the VLC plugin list or the audio path; it is OFF by default (the task only activates with `-PvlcTrial`).

## Windows Installer

`gradlew.bat :app:packageExe` builds the Windows installer (spec `spec-windows-installer`) into `app/build/compose/binaries/` (gitignored — never committed).

- **What it is:** jpackage `--type exe` — a self-extracting `.exe` wrapping the embedded MSI. The embedded VLC runtime goes in exactly as in the portable build (same jpackage image, same `compose.application.resources.dir` layout).
- **No tool to install on the build machine:** the Compose plugin downloads the WiX 3.11 toolset itself (`downloadWix`/`unzipWix` on the root project; the zip is kept in the Gradle user home). Set the `WIX_PATH` environment variable to a local WiX directory to use one instead.
- **Windows host only:** on a non-Windows host the task is disabled by the plugin (OS incompatible) and `build` never builds the installer — the build stays green with no packaging tool at all.
- **Per-user, no UAC by default** (`perUserInstall` + `dirChooser` in `app/build.gradle.kts`): installs to `%LOCALAPPDATA%\Programs\N-Zik Desktop Compagnon` without an elevation prompt. (With `dirChooser`, a user who deliberately picks a protected folder — e.g. `C:\Program Files` — still gets a UAC prompt; the default location never does.) This matches the data model (`%APPDATA%\N-Zik Desktop Compagnon\` + Windows Credential Manager, both per-user) and a future in-app updater will need no admin rights.
- **Shortcuts + uninstall entry:** Start menu (group `N-Zik`) and desktop shortcut; the Control-Panel entry is the catalog's `nzikPackageName` with the version `nzikVersionName`.
- **Upgrade:** `nzikUpgradeUuid` in `gradle/libs.versions.toml` is **FROZEN — never change this GUID** (pinned by `UpgradeIdentityTest`). It is the identity of the installation: every future installer (and the deferred in-app updater, which re-launches the installer silently) upgrades an existing install in place, and user data in `%APPDATA%\N-Zik Desktop Compagnon\` survives upgrade and uninstall. **The version must always strictly increase** (`nzikVersionName`, e.g. `0.0.1` → `0.0.2`): an MSI upgrade only applies when the incoming version is greater than the installed one, so a lower or equal version is refused.
- **Version / name:** the app's identity lives in the version catalog (`gradle/libs.versions.toml`, N-Zik Android convention): `nzikPackageName` (display name) and `nzikVersionName` (the jpackage app version, read by `version` in `app/build.gradle.kts`) — the single source in the build, portable distributable and installer alike. `nzikVersionCode` stays in the catalog for release discipline only; the build never reads it. `nzikUpgradeUuid` / `nzikPerUser` are the frozen installation identity (never bumped; see **Upgrade** above). Bumped manually on each release — there is no version-bump machinery in this repo.
- **Icon:** `assets/design/icon.ico` (committed binary, like `ic_banner2.png`) — referenced by `windows.iconFile`, rides in the installer, the app exe and the shortcuts.

## Linux packaging

`./build.sh package` (or `./gradlew :app:packageDeb :app:packageRpm :app:packageLinuxPortable`) builds the Linux packages from the same jpackage app-image as the portable build (spec `spec-linux-packaging`) into `app/build/compose/binaries/` (gitignored — never committed):

- **`.deb`** — `binaries/main/deb/n-zik-desktop-compagnon_<version>-1_amd64.deb` — Debian / Ubuntu / Mint: `apt install ./<file>.deb`
- **`.rpm`** — `binaries/main/rpm/n-zik-desktop-compagnon-<version>-1.x86_64.rpm` — Fedora / openSUSE / Rocky: `dnf install ./<file>.rpm` (architecture `x86_64`, never `noarch` — the bundled JRE is architecture-specific)
- **portable zip** — `binaries/n-zik-desktop-compagnon-<version>-linux-portable.zip`: a zip of the app-image directory (the app-image folder is the zip root) — the only Linux binary the AUR entry consumes; the Linux "portable" is the app-image directory, never a tar.gz
- **Portable-zip launcher exec bit:** the `packageLinuxPortable.doLast` patches the launcher's central-directory entry in the zip (the Unix mode lives at byte 38 of each central header — set to 0o100755) and verifies by re-reading it. Why: this repo lives on the 9P/drvfs mount (`/mnt/d` on WSL), where the zip writer loses the launcher's exec bit (observed 0777 on disk but 0644 in the entry) and Java's zip API has no external-attributes setter. On a non-9P host the entry already carries the bit, so the patch is a no-op. Without it, the launcher would unzip non-executable (the AUR release entry consumes this zip).

> **Linux/WSL host only:** on a Windows host the plugin disables the Deb/Rpm tasks (the format is not compatible with the current OS) and `gradlew.bat build` stays green with identical Windows outputs. Build the packages on WSL (or native Linux).

- **VLC is never bundled on Linux:** the app plays through the system libvlc (the existing `VlcRuntime` path), so the `.deb`/`.rpm`/AUR declare `vlc` as a dependency — the package manager installs it. The pinned VLC config (`vlcVersion`/`vlcZipUrl`/`vlcZipSha256`) and the `downloadVlc`/`extractVlc` tasks are unchanged; `appResourcesRootDir` is now conditional on the Windows host, so no runtime reaches the Linux packages.
- **The `vlc` dependency is injected through `freeArgs`** (`--linux-package-deps vlc`) on `packageDeb`/`packageRpm` — the Compose plugin's `linux { }` DSL exposes no `depends` and never passes jpackage's `--linux-package-deps`, so `freeArgs` (prepended to the jpackage command line) is the hook; `vlc` lands in the deb `Depends` and the rpm `Requires` with no repackaging. The same hook injects **`--linux-menu-group Audio;`** — the jpackage flag that fills the `.desktop` `Categories=` field (jpackage's default is `Unknown`; `linux.appCategory` only sets the deb `Section` and the rpm `Group`) — so the menu entry sorts under Audio, matching the AUR entries' own `.desktop`.
- **What an install gives (the "raccourci"):** the app in `/opt/n-zik-desktop-compagnon/` (the jpackage package name — the same `/opt` location on the `.deb`, the `.rpm` and both AUR entries), a `.desktop` menu entry + icon (jpackage `shortcut = true` — the DSL default is off), a clean uninstall via the package manager. The jpackage layout keeps the `.desktop` inside the app dir (`/opt/<pkg>/lib/<pkg>-<app>.desktop`) and the install/uninstall maintainer scripts register/remove it with `xdg-desktop-menu` (run as root → system mode, for all users; that is why `xdg-utils` is a package dependency); the icon is bundled at `/opt/<pkg>/lib/<app>.png` and referenced by absolute path in the `.desktop`. No desktop icon by default (GNOME requires user trust).
- **User data lives outside the package:** `~/N-Zik Desktop Compagnon/` + the keyring (libsecret) — an in-place upgrade (same package name, monotone version) and an uninstall leave it alone. The frozen upgrade UUID stays exclusive to the Windows installer.
- **Unsigned by design (deferred):** no GPG signature and no self-hosted apt/rpm repo — the packages are distributed as GitHub release files, so `apt install ./n-zik…deb` shows an "unauthenticated" warning (accepted; the source is public and every build is reproducible). Signing + a self-hosted repo are deferred (`deferred-work.md`).

### jpackage tooling (WSL)

jpackage builds the `.deb` with `dpkg-deb` + `fakeroot` and the `.rpm` with `rpmbuild`. On WSL (Debian/Ubuntu-based) install them **system-wide**:

```bash
sudo apt install -y fakeroot rpm
```

`dpkg-deb` ships with `dpkg` (already present). A user-prefix install of `rpm`/`fakeroot` does **not** work — `rpmbuild` has a compiled-in `/usr/lib/rpm/rpmrc` path and the Debian `fakeroot` wrapper hardcodes a `/usr/lib` LD_PRELOAD path, so both need the system location.

### AUR entries (Arch / Manjaro / CachyOS)

Two entries under `packaging/`, pinned by `AurPkgbuildTest`:

- **`packaging/aur/PKGBUILD`** — `n-zik-desktop-compagnon` (release-zip): downloads the portable zip + the icon, both pinned to the release tag `v<pkgver>`, and installs them to `/opt/n-zik-desktop-compagnon/` + the `/usr/bin` symlink + `.desktop` + icon. `depends=(vlc)`, `makedepends=(unzip)`.
- **`packaging/aur-git/PKGBUILD`** — `n-zik-desktop-compagnon-git`: clones `main` and builds the app-image with `./gradlew :app:createDistributable`. `makedepends=(jdk21-openjdk)` — JDK 21 on the user's machine (the Arch package name; the spec's `openjdk-21` is the Debian/Fedora name — refined here). `depends=(vlc)`. The first build downloads the Gradle wrapper + dependencies (~1 GB cache, a few minutes).
- **The `-git` entry's live-build requirements (makepkg ≥ 7.0, verified 2026-10-06 on WSL Arch, makepkg 7.1.0):** `pkgver()` runs before the source is downloaded (there is no local clone to inspect yet — the version is the 7-char short hash of the `main` tip, read with `git ls-remote`), and makepkg lints the `pkgver` variable before it runs the function (hence the `pkgver=0` placeholder); the VCS source uses the `name::url` syntax (the custom clone-dir name comes before the `::` — makepkg's `get_url` strips the prefix before the first `::`) with `sha256sums=('SKIP')`; and `package()` runs under fakeroot (as root), so `GRADLE_USER_HOME` is pinned to the build dir (otherwise the Gradle wrapper writes to `/root/.gradle`, which is not writable by the build user). A function-only `pkgver()` with no placeholder dies at lint with "pkgver is not allowed to be empty".

The jpackage app-image puts its launcher in `<app-image>/bin/` (named after the display name, which contains spaces) and bundles its own JRE under `lib/runtime`. The app-image folder is named after the display name, but **all four Linux install paths use the same `/opt` location — `/opt/n-zik-desktop-compagnon/`**: the `.deb`/`.rpm` get it from jpackage (`--linux-package-name`), and both AUR entries install the app-image under the lowercase package name (unzip + rename). Each entry also creates a **space-free `/usr/bin/<pkgname>` symlink** to the `bin/` launcher (the `.desktop` `Exec` points at the symlink, not the raw space-containing `/opt` path) and **fails loudly if the app-image layout ever changes**. The jpackage-generated `.desktop` files are self-consistent with their own (lowercase) `/opt` layout: `Exec="/opt/n-zik-desktop-compagnon/bin/N-Zik Desktop Compagnon"` (the space-containing path quoted), `Icon=` the bundled PNG.

**Release chore (release-zip entry only):** on every GitHub release, bump `pkgver` and recompute **all three** `sha256sums` (the portable zip, the icon and the LICENSE text — all are pinned to the tag `v<pkgver>`, which must exist for the `releases/download` URL to resolve; the current zip is `9e9f555a135c54b297ba30a53b5c9d451078deed4e88b99e2b762482d73394eb`, the icon is `831edcc324a716691b501f976b8a8b8254c4615a69f82ba088bec2bf775797e4`, and the LICENSE sum is filled from the same tag). Publish the `.deb`/`.rpm`/zip with their **SHA-256 checksums** alongside the release (the README's integrity note covers the Linux assets, not just the Windows installer). The `-git` entry tracks `main` with a dynamic `pkgver()` and needs no chore.

### Live-install matrix (WSL 2 / WSLg, 2026-10-06)

All four install paths were live-tested on WSL: Ubuntu 26.04 (`.deb`, including the 0.0.1→0.0.2 in-place upgrade with `~/N-Zik Desktop Compagnon/` preserved), Fedora 44 (`.rpm`), and Arch (`makepkg` for the release-zip entry and the `-git` entry — the `-git` `pkgver()` came out as the `main` tip's short hash). Every install, layout (`/opt/n-zik-desktop-compagnon/`, the `.desktop` + icon, the `/usr/bin` symlink), and uninstall was verified; **every launch was verified past "process alive"** — the window is mapped on the WSLg display (1090×770) and the pairing screen renders (QR code, IP/port, device-name input — confirmed by screenshot). WSLg mirrors the user's target environment (Wayland-only desktops): the app's windowing there is X11 against WSLg's X server (it does not use the `WAYLAND_DISPLAY` WSLg provides), and Skiko falls back to software rendering (no GL context under WSLg). If a window ever fails to open or freezes under Wayland, record it as a Wayland signal — the fallback is an explicit Compose `renderApi` selection (one line, no package impact).

Environment findings (not package bugs — the spec scopes the package dependency to `vlc`):

- A minimal Arch cannot launch the app until the X11 client libs (`libx11 libxext libxrender libxi libxcursor libxfixes libxrandr libxres libxss libxinerama libxft libxtst fontconfig libglvnd`) and a font (`ttf-dejavu`) are present — an `UnsatisfiedLinkError` on `libXtst.so.6` and "Could not load font" without them. Ubuntu ships these by default (which is why the `.deb` launched out of the box).
- On a minimal Ubuntu the deb/rpm postinst's `xdg-desktop-menu` step needs `/usr/share/desktop-directories`; without it, apt leaves the package half-configured (the install itself succeeds) — `mkdir -p /usr/share/desktop-directories` fixes it.
- The rpm's `Requires` is minimal (vlc, xdg-utils, `/bin/sh` + rpmlib — the JRE is bundled, never a system dependency); the deb carries `share/doc/copyright`, the rpm does not; the deb embeds build timestamps (non-reproducible across rebuilds).

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
