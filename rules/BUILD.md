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

**The one sanctioned runtime dependency:** `com.github.rebelonion:translator` (catalog `libs.translator`, applied in `app/build.gradle.kts`) — the changelog translation of the update page ("What's new", the `translate` toggle) through `updater/services/ChangelogTranslator.kt` (spec `spec-updater` AD-9, the phone's own dependency, the same version as the phone). No other dependency may be added to the app runtime without a spec change + user approval.

## Build Commands

```bash
gradlew.bat build                                          # Compile + all tests (primary)
gradlew.bat :app:test                                      # Tests only
gradlew.bat :app:test --tests "app.n_zik.compagnon.PagedListTest"   # Single test class
gradlew.bat :app:run                                       # Launch the app (channel debug by default)
gradlew.bat clean build                                    # Clean + build
gradlew.bat :app:packageInstaller -Pchannel=stable         # Windows NSIS installer (see "Windows Installer" + "Build channels" below)
gradlew.bat :app:packageInstaller -Pchannel=beta           # Beta Windows installer, named "N-Zik Desktop Compagnon (Beta)-<base>-beta.exe"
./build.sh package stable     (Linux/WSL host)             # Linux .deb + .rpm + portable zip + AppImage + Flatpak for the channel (see "Linux packaging" + "Build channels" below)
```

## Build channels (spec `spec-updater`)

A **channel** is a build-time Gradle property: `-Pchannel=debug|stable|beta|dev|git`. It drives the generated `AppVersion` object (below) and the artifact file names — nothing else (the jpackage version stays the numeric base `nzikVersionName`; the channel suffix never enters the MSI/deb/rpm version).

| Channel | Artifact suffix | Assets published | Header badge | In-app updater |
|---------|-----------------|------------------|--------------|----------------|
| `stable` | (none — names byte-identical to the pre-channel convention) | the full set of 6 | none (the phone's rule) | on |
| `beta`   | `-beta` | the full set of 6 | Beta | on |
| `dev`    | `-dev-YYYYMMDD` (the build date) | the full set of 6 | Dev | on |
| `debug`  | (none) | **none** — run/source only | Debug | **off** (anti-downgrade) |
| `git`    | `-git-<hash8>` (`git rev-parse --short=8 HEAD`) | **none** — run/source only (the AUR `-git` entry) | Git | **off** (anti-downgrade) |

- **Absent property:** `debug` for `:app:run` / a plain compile (the dev iteration: badge Debug, updater off) — `gradlew.bat build` never packages and stays channel-agnostic. **Every packaging task** (`packageExe` / `packageInstaller` / `packageDeb` / `packageRpm` / `packageLinuxPortable` / `packageAppImage` / `packageFlatpak`) **fails loud** without `-Pchannel=stable|beta|dev`; `createDistributable` additionally accepts `git`. A release artifact is never built without an explicit channel (the phone's build-type discipline).
- **`-Pchannel=git` outside a git checkout fails the build** with an explicit message (the hash is `git rev-parse --short=8 HEAD`; there is no fallback).
- **`AppVersion.kt` (generated, never committed):** the `generateAppVersion` task writes `build/generated/source/appVersion/main/app/n_zik/compagnon/generated/AppVersion.kt` — `versionName` (base + suffix), `versionCode` (the catalog `nzikVersionCode`), `channel`, `updaterEnabled` (stable|beta|dev) — and the main source set compiles it in (`compileKotlin` depends on the task). The app reads only this object at runtime; no suffix logic lives in the app code.
- **Artifact names (the rename helper, one per packaging task):** the channel suffix sits right after the base version — `n-zik-desktop-compagnon[_-dev]_<base>[-beta|-dev-YYYYMMDD]-1_amd64.deb`, `n-zik-desktop-compagnon[_-dev]-<base>[-…]-1.x86_64.rpm`, `N-Zik Desktop Compagnon [(Beta)|(Dev)]-<base>[-…].exe` (the verified jpackage convention: base name + version, no architecture suffix), `n-zik-desktop-compagnon[_-dev]-<base>[-…]-linux-portable.zip`, `…-x86_64.AppImage`, `…-x86_64.flatpak`. The **stable names are byte-identical to the pre-channel convention** — the AUR release entry (`packaging/aur/PKGBUILD`, URLs pinned to the tag `v<pkgver>`) stays valid unchanged. The channel convention is pinned by the `updater` tests (`ArtifactNames`) and the effective names are exposed via `Test.systemProperty` (the `LinuxPackagePinTest` pattern).
- **AD-8 product identity (the channel rides in the product, not only the file names):** the jpackage app name is per-channel — `N-Zik Desktop Compagnon (Beta)` for beta, `N-Zik Desktop Compagnon (Dev)` for dev; stable / debug / -git keep the plain catalog name. It drives the Windows installer base name (`N-Zik Desktop Compagnon (Beta)-0.0.1-beta.exe`), the Linux launcher, the AppImage `AppRun`, the Flatpak `exec`/`Name=`, the window title and the in-app badge. There is NO frozen upgrade GUID anymore (the MSI-era UUIDs are gone with the NSIS installer — the record is the `installer.nsi` header + the `spec-updater` Change Log): on Windows every channel is its own installation, identified by its own registry key `Software\N-Zik\DesktopCompagnon\{channel}` (per-user scope → HKCU, global → HKLM — see "Windows Installer"), and upgraded in place by its own updater; on Linux the **beta channel is an in-place replacement of the stable one** (same package base + Flatpak app-id — `n-zik-desktop-compagnon` / `com.nzik.desktop.compagnon`), while the dev channel installs under its OWN package base `n-zik-desktop-compagnon-dev` (`/opt/n-zik-desktop-compagnon-dev/` + app-id `com.nzik.desktop.compagnon.dev`, beside the stable one — a parallel product, never a replacement). Pinned by `InstallerContractTest` (the registry identity + the exit-code contract), `LinuxPackagePinTest` / `FlatpakPinTest` (the package name + app-id) and the `updater` tests (`ArtifactNames` + `ChannelArtifactNameTest` — the per-channel / dev names).
- **The AUR `-git` entry** (`packaging/aur-git/PKGBUILD`) builds `:app:createDistributable -Pchannel=git` — a source build: updater off, Git badge, the anti-downgrade rule. Its dynamic `pkgver()` (the remote tip's 7-char hash) is the AUR identity; the in-app `-git-<hash8>` is derived from the local checkout at build time.
- **Cross-channel upgrades:** stable and beta of the same base share the jpackage version — an in-channel upgrade works (the base bumps), a cross-channel switch is a manual reinstall (accepted: the product is transitional).
- **Release chore:** see the "Release chore (channels)" note under "Linux packaging" — the tag, the release body's raw changelog link, the per-channel assets and the `Updater/changelogs/{code}.txt`.

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

> **Host-gated:** `downloadVlc`/`extractVlc` (the official win64 zip) are disabled on a non-Windows host (`isWindowsHost` in `app/build.gradle.kts`) and the win64 runtime never enters the Linux distributable (tar.gz via `createDistributable`). On Linux, local playback uses the **system libvlc** (e.g. `sudo apt install vlc`); `VlcRuntime` skips the embedded gate and loads the system `libvlc` through vlcj's `NativeDiscovery`, and the "This PC" unavailable message carries the distro's install command (spec `spec-linux-system-libvlc`). The **AppImage is the one Linux exception** (spec `spec-linux-appimage`): it embeds the Linux runtime — a separate pinned tarball (`downloadVlcLinux`/`extractVlcLinux`, Linux host only) injected into the AppDir's `resources/vlc`, never into the shared app-image. `VlcRuntime` applies one unified rule everywhere: the app's resources dir contains the platform's libvlc (`libvlc.dll` on Windows, `libvlc.so` on Linux) → embedded; otherwise system discovery.

- `app/build.gradle.kts` pins `vlcVersion`, `vlcZipUrl` (the official VideoLAN zip) and `vlcZipSha256` (from the official `.sha256`); the `downloadVlc` task downloads once into the Gradle user home (`caches/n-zik-compagnon/vlc/`) and checks the hash, `extractVlc` keeps only the audio subset (libvlc, libvlccore, ~30 plugins, `COPYING.txt`) under `build/vlc-runtime/windows-x64/vlc`
- `prepareAppResources` depends on `extractVlc` — the runtime reaches the app both under `:app:run` and in `createDistributable` (via `appResourcesRootDir`)
- If the embedded VLC cannot load at runtime, the app degrades ("This PC" output disabled with a message) — that resilience is by design, do not remove it
- **NEVER change `vlcVersion`/`vlcZipUrl`/`vlcZipSha256`/`vlcRuntimeFiles` without the user's explicit approval** (AGENTS.md "Ask First") — a changed hash or plugin list breaks playback silently
- The pinned SHA-256 must match the official `vlc-3.0.24-win64.zip.sha256` — verify against download.videolan.org before touching it

### Packaging trial (opt-in)

`gradlew.bat test -PvlcTrial=<directory of audio samples>` runs `VlcRuntimeTrialTest`: it plays every sample from a file and over HTTP with the embedded runtime and prints the libvlc modules used. Use it when touching the VLC plugin list or the audio path; it is OFF by default (the task only activates with `-PvlcTrial`).

## Windows Installer

`gradlew.bat :app:packageInstaller -Pchannel=stable|beta|dev` builds the Windows NSIS installer (spec `spec-windows-installer`; the channel requirement is spec `spec-updater`) into `app/build/compose/binaries/` (gitignored — never committed). The installer file name carries the channel suffix right after the base version, on the per-channel product name (AD-8): `N-Zik Desktop Compagnon (Beta)-0.0.2-beta.exe` for `-Pchannel=beta`, `N-Zik Desktop Compagnon (Dev)-0.0.2-dev-YYYYMMDD.exe` for `-Pchannel=dev`; stable keeps the plain name `N-Zik Desktop Compagnon-0.0.2.exe` (base name + version — verified against the real output). A bare `packageInstaller` fails loud with the channel message.

- **What it is:** an **NSIS 3.x** installer built from `app/packaging/windows/installer.nsi` (the SOURCE OF TRUTH for the installer behavior — its header is the full contract; the NSIS installer replaced the jpackage self-extracting exe — the record is the `spec-updater` Change Log). `:app:packageInstaller` runs makensis over the jpackage app-image (the embedded VLC runtime goes in exactly as in the portable build — same app-image, same `compose.application.resources.dir` layout); the jpackage self-extracting exe is now a build **BYPRODUCT** (built first, then overwritten at the same output path by the NSIS installer).
- **NSIS on the build machine:** makensis (NSIS 3.x) is required — preinstalled on the GitHub Actions Windows runners (`C:\Program Files (x86)\NSIS`); on a dev machine install the official NSIS portable zip per-user (`%LOCALAPPDATA%\Programs\NSIS\nsis-3.10`) or set the `NSIS_DIR` environment variable to the NSIS root. The task resolves `makensis.exe` at execution time and fails with the install hint when it is missing (the build does NOT download the toolchain).
- **Windows host only:** on a non-Windows host the task is disabled by the plugin (OS incompatible) and `build` never builds the installer — the build stays green with no packaging tool at all.
- **One installer, two scopes (scope-choice page, DEFAULT per-user — the user decision):** **per-user** — no admin rights, installs under `%LOCALAPPDATA%\<product>`, registry under HKCU; **global** — the installer relaunches ITSELF elevated (`shell32::ShellExecuteW runas` — the VISIBLE UAC prompt appears at that moment), installs under `%PROGRAMFILES%\<product>`, registry under HKLM. Cross-scope migration (an install of the OTHER scope, same channel): detected from the registry, the user is informed, the old scope is removed first (its uninstaller runs silently, then registry keys + leftover files) and the result is VERIFIED — a global old scope cannot be removed by a non-elevated process, so the verification fails and the install aborts (exit 1 + a clear message). The app DATA still live in `%APPDATA%\N-Zik Desktop Compagnon\` + the Windows Credential Manager (both per-user), regardless of scope or install location — never touched by install or migration.
- **Shortcuts + uninstall entry:** Start menu (group `N-Zik`) and desktop shortcut; the Control-Panel entry is the per-channel product name with the version `nzikVersionName`.
- **Upgrade (registry-based — NO frozen GUID):** the product-info key `Software\N-Zik\DesktopCompagnon\{channel}` (HKCU per-user / HKLM global) stores the version + scope + install dir + install date. Every future installer of the SAME channel (and the in-app updater) upgrades that install in place, and user data in `%APPDATA%\N-Zik Desktop Compagnon\` survives upgrade and uninstall. **The version must always strictly increase** (`nzikVersionName`, e.g. `0.0.1` → `0.0.2`): the compare is NUMERIC (major / minor / patch / build — the dev build date); when the in-app updater launches the installer with `/UPDATE`, a same-or-older installed version triggers a "reinstall anyway?" confirmation (declining exits 1) instead of an automatic advance.
- **The updater's install (spec `spec-updater`, AD-4 — INTERACTIVE since the 2026-10-07 user decision; the silent design is superseded, the spec Change Log is the record):** the update page/dialog's "Install now" starts a detached PowerShell helper (the app quits; the helper polls the app process with a capped 30-s wait until it has really exited — the deterministic path that releases the locked files — then launches the downloaded installer **interactively with the `/UPDATE` flag**: the RRU page is skipped, the scope defaults to the current install's scope, the user sees progress and can cancel; the installer's RUNNING-APP GUARD — install and uninstall first open the launcher exe for write, a locked file cannot be opened, locked → a clear message + exit 1 — protects the still-running case). Exit codes (pinned by `InstallerContractTest`): **0** → success (the helper relaunches the app from its current path, only when it is not already running and its path still exists); **1** (the user cancelled any page — or the running-app guard fired) / **2** (the REMOVE choice — the app is gone by design) / **1223** (the user declined the UAC) → a normal decline, nothing written; **3** (install failure) / **4** (the elevated child vanished — death detected through its PID file + `GetExitCodeProcess`, or the PID file never appeared) or anything else → the failure marker. A hang is visible to the user, so the helper carries no timeout and no auto-kill.
- **Version / name:** the app's identity lives in the version catalog (`gradle/libs.versions.toml`, N-Zik Android convention): `nzikPackageName` (display name) and `nzikVersionName` (the jpackage app version, read by `version` in `app/build.gradle.kts`) — the single source in the build, portable distributable and installer alike. `nzikVersionCode` is NOT a jpackage number: it is read by the build only through `generateAppVersion` (written into the generated `AppVersion` and read at runtime as `AppVersion.versionCode` — e.g. the updater's changelog URL), and it names the changelog file (`Updater/changelogs/{code}.txt`). Bumped manually on each release — there is no version-bump machinery in this repo.
- **Icon:** `assets/design/icon.ico` (committed binary, like `ic_banner2.png`) — referenced by `windows.iconFile`, rides in the installer, the app exe and the shortcuts.

## Linux packaging

`./build.sh package` (or `./gradlew :app:packageDeb :app:packageRpm :app:packageLinuxPortable :app:packageAppImage :app:packageFlatpak`) builds the Linux packages + the AppImage + the Flatpak from the same jpackage app-image as the portable build (specs `spec-linux-packaging`, `spec-linux-appimage`, `spec-linux-flatpak`) into `app/build/compose/binaries/` (gitignored — never committed):

- **`.deb`** — `binaries/main/deb/n-zik-desktop-compagnon_<version>-1_amd64.deb` — Debian / Ubuntu / Mint: `apt install ./<file>.deb`
- **`.rpm`** — `binaries/main/rpm/n-zik-desktop-compagnon-<version>-1.x86_64.rpm` — Fedora / openSUSE / Rocky: `dnf install ./<file>.rpm` (architecture `x86_64`, never `noarch` — the bundled JRE is architecture-specific)
- **portable zip** — `binaries/n-zik-desktop-compagnon-<version>-linux-portable.zip`: a zip of the app-image directory (the app-image folder is the zip root) — the only Linux binary the AUR entry consumes; the Linux "portable" is the app-image directory, never a tar.gz
- **AppImage** — `binaries/n-zik-desktop-compagnon-<version>-x86_64.AppImage` (x86_64 only): a single self-contained file (app + JRE + the official Linux VLC runtime), runs directly with no install — produced by `packageAppImage`, see "AppImage (the 5th Linux path)" below
- **Flatpak** — `binaries/n-zik-desktop-compagnon-<version>-x86_64.flatpak` (x86_64 only): a sandboxed bundle (app + JRE + the official Linux VLC runtime), installed with `flatpak install ./<file>.flatpak` — the 6th Linux path, produced by `packageFlatpak`, see "Flatpak (the 6th Linux path)" below
- **Portable-zip launcher exec bit:** the `packageLinuxPortable.doLast` patches the launcher's central-directory entry in the zip (the Unix mode lives at byte 38 of each central header — set to 0o100755) and verifies by re-reading it. Why: this repo lives on the 9P/drvfs mount (`/mnt/d` on WSL), where the zip writer loses the launcher's exec bit (observed 0777 on disk but 0644 in the entry) and Java's zip API has no external-attributes setter. On a non-9P host the entry already carries the bit, so the patch is a no-op. Without it, the launcher would unzip non-executable (the AUR release entry consumes this zip).

> **Linux/WSL host only:** on a Windows host the plugin disables the Deb/Rpm tasks (the format is not compatible with the current OS) and `gradlew.bat build` stays green with identical Windows outputs. Build the packages on WSL (or native Linux).

- **VLC is never bundled in the five Linux packages (the AppImage + the Flatpak are the deliberate exceptions, specs `spec-linux-appimage` + `spec-linux-flatpak`):** the app plays through the system libvlc (the `VlcRuntime` path), so the `.deb`/`.rpm`/AUR declare `vlc` as a dependency — the package manager installs it. The pinned win64 VLC config (`vlcVersion`/`vlcZipUrl`/`vlcZipSha256`) and the `downloadVlc`/`extractVlc` tasks are unchanged; `appResourcesRootDir` is conditional on the Windows host, so no win64 runtime reaches the Linux packages — and the AppImage's + the Flatpak's Linux runtime are injected only into each staging's `resources/vlc` (step 4 of `packageAppImage` / step 3 of `packageFlatpak`), never into the shared app-image the other five paths consume.
- **The `vlc` dependency is injected through `freeArgs`** (`--linux-package-deps vlc`) on `packageDeb`/`packageRpm` — the Compose plugin's `linux { }` DSL exposes no `depends` and never passes jpackage's `--linux-package-deps`, so `freeArgs` (prepended to the jpackage command line) is the hook; `vlc` lands in the deb `Depends` and the rpm `Requires` with no repackaging. The same hook injects **`--linux-menu-group Audio;`** — the jpackage flag that fills the `.desktop` `Categories=` field (jpackage's default is `Unknown`; `linux.appCategory` only sets the deb `Section` and the rpm `Group`) — so the menu entry sorts under Audio, matching the AUR entries' own `.desktop`.
- **What an install gives (the "raccourci"):** the app in `/opt/n-zik-desktop-compagnon/` (the jpackage package name — the same `/opt` location on the `.deb`, the `.rpm` and both AUR entries), a `.desktop` menu entry + icon (jpackage `shortcut = true` — the DSL default is off), a clean uninstall via the package manager. The jpackage layout keeps the `.desktop` inside the app dir (`/opt/<pkg>/lib/<pkg>-<app>.desktop`) and the install/uninstall maintainer scripts register/remove it with `xdg-desktop-menu` (run as root → system mode, for all users; that is why `xdg-utils` is a package dependency); the icon is bundled at `/opt/<pkg>/lib/<app>.png` and referenced by absolute path in the `.desktop`. No desktop icon by default (GNOME requires user trust).
- **User data lives outside the package:** `~/N-Zik Desktop Compagnon/` + the keyring (libsecret) — an in-place upgrade (same package name, monotone version) and an uninstall leave it alone. (The Windows installer likewise never touches the user data — see "Windows Installer".)
- **Unsigned by design (deferred):** no GPG signature and no self-hosted apt/rpm repo — the packages are distributed as GitHub release files, so `apt install ./n-zik…deb` shows an "unauthenticated" warning (accepted; the source is public and any build can be rebuilt and compared against this one — not byte-reproducible, but a rebuild ships the same files and behavior). Signing + a self-hosted repo are deferred (`deferred-work.md`). The AppImage and the Flatpak are likewise unsigned (same deferral — the Flatpak by design, its SHA-256 published instead).

### jpackage tooling (WSL)

jpackage builds the `.deb` with `dpkg-deb` + `fakeroot` and the `.rpm` with `rpmbuild`. On WSL (Debian/Ubuntu-based) install them **system-wide**:

```bash
sudo apt install -y fakeroot rpm
```

`dpkg-deb` ships with `dpkg` (already present). A user-prefix install of `rpm`/`fakeroot` does **not** work — `rpmbuild` has a compiled-in `/usr/lib/rpm/rpmrc` path and the Debian `fakeroot` wrapper hardcodes a `/usr/lib` LD_PRELOAD path, so both need the system location.

**WSL build environment (2026-10-06):** the host's distros are `Ubuntu` (26.04), `FedoraLinux-44` and `archlinux` (`wsl -d <name>`; `-u root` works passwordless). The repo lives on the Windows D: drive → `/mnt/d/Autres/Projet Android/NZik-Folder/N-Zik-Desktop-Compagnon` (a 9P mount — slower than ext4, and real-time timing tests flake more readily on it). Non-root Gradle runs use the default user `nevar`, whose JDK 21 is at `/home/nevar/jdk21`: non-interactive bash does **not** source `~/.bashrc`, so export `JAVA_HOME` / `PATH` explicitly; and Windows PowerShell → WSL strips inner double quotes from inline commands, so for anything beyond a one-liner write a script file (e.g. under `C:\Users\Danie\AppData\Local\Temp\opencode\`, reachable as `/mnt/c/...`) and run `wsl -d Ubuntu -u nevar -- bash <that path>`. **Cross-OS builds share `app/build/`:** the Kotlin incremental compile cache is NOT cross-OS readable — a Windows-written cache (its `C:\…` paths) fails the Linux `compileKotlin` with `Expected absolute path but found relative path`, and vice versa (a Linux-written cache's `\mnt\…` paths fail the Windows build). After switching OSes, run a clean build (`./gradlew clean build` on WSL — `scripts/wsl-verify.sh` does it; `gradlew.bat clean build` on Windows).

### AUR entries (Arch / Manjaro / CachyOS)

Two entries under `packaging/`, pinned by `AurPkgbuildTest`:

- **`packaging/aur/PKGBUILD`** — `n-zik-desktop-compagnon` (release-zip): downloads the portable zip + the icon, both pinned to the release tag `v<pkgver>`, and installs them to `/opt/n-zik-desktop-compagnon/` + the `/usr/bin` symlink + `.desktop` + icon. `depends=(vlc)`, `makedepends=(unzip)`.
- **`packaging/aur-git/PKGBUILD`** — `n-zik-desktop-compagnon-git`: clones `main` and builds the app-image with `./gradlew :app:createDistributable`. `makedepends=(jdk21-openjdk)` — JDK 21 on the user's machine (the Arch package name; the spec's `openjdk-21` is the Debian/Fedora name — refined here). `depends=(vlc)`. The first build downloads the Gradle wrapper + dependencies (~1 GB cache, a few minutes).
- **The `-git` entry's live-build requirements (makepkg ≥ 7.0, verified 2026-10-06 on WSL Arch, makepkg 7.1.0):** `pkgver()` runs before the source is downloaded (there is no local clone to inspect yet — the version is the 7-char short hash of the `main` tip, read with `git ls-remote`), and makepkg lints the `pkgver` variable before it runs the function (hence the `pkgver=0` placeholder); the VCS source uses the `name::url` syntax (the custom clone-dir name comes before the `::` — makepkg's `get_url` strips the prefix before the first `::`) with `sha256sums=('SKIP')`; and `package()` runs under fakeroot (as root), so `GRADLE_USER_HOME` is pinned to the build dir (otherwise the Gradle wrapper writes to `/root/.gradle`, which is not writable by the build user). A function-only `pkgver()` with no placeholder dies at lint with "pkgver is not allowed to be empty".

The jpackage app-image puts its launcher in `<app-image>/bin/` (named after the display name, which contains spaces) and bundles its own JRE under `lib/runtime`. The app-image folder is named after the display name, but **all four Linux install paths use the same `/opt` location — `/opt/n-zik-desktop-compagnon/`**: the `.deb`/`.rpm` get it from jpackage (`--linux-package-name`), and both AUR entries install the app-image under the lowercase package name (unzip + rename). Each entry also creates a **space-free `/usr/bin/<pkgname>` symlink** to the `bin/` launcher (the `.desktop` `Exec` points at the symlink, not the raw space-containing `/opt` path) and **fails loudly if the app-image layout ever changes**. The jpackage-generated `.desktop` files are self-consistent with their own (lowercase) `/opt` layout: `Exec="/opt/n-zik-desktop-compagnon/bin/N-Zik Desktop Compagnon"` (the space-containing path quoted), `Icon=` the bundled PNG.

**Release chore (release-zip entry only):** on every GitHub release, bump `pkgver` and recompute **all three** `sha256sums` (the portable zip, the icon and the LICENSE text — all are pinned to the tag `v<pkgver>`, which must exist for the `releases/download` URL to resolve; the current zip is `9e9f555a135c54b297ba30a53b5c9d451078deed4e88b99e2b762482d73394eb`, the icon is `831edcc324a716691b501f976b8a8b8254c4615a69f82ba088bec2bf775797e4`, and the LICENSE sum is filled from the same tag). Publish the `.deb`/`.rpm`/zip/**AppImage**/**Flatpak** with their **SHA-256 checksums** alongside the release (the README's integrity note covers the Linux assets, not just the Windows installer). The AppImage additionally needs the pinned Linux VLC tarball published as a release asset — built once by `scripts/build-vlc-linux-tarball.sh` (official source, audio subset, rpath stripped); until that upload chore lands (tracked in `assets/notes/TODO.txt`) the build is cache-first in the Gradle user home and the pinned URL becomes authoritative once uploaded. The `-git` entry tracks `main` with a dynamic `pkgver()` and needs no chore.

**Release chore — channels (spec `spec-updater`):** a release is a per-channel GitHub release; there is no version-bump machinery — the catalog is bumped **manually** (the phone's release discipline, unchanged):

1. **Bump the catalog** (`gradle/libs.versions.toml`): `nzikVersionName` strictly increases (e.g. `0.0.1` → `0.0.2`), `nzikVersionCode` increments by 1 (it names the changelog file and reaches the runtime as `AppVersion.versionCode` through `generateAppVersion` — never as an MSI/jpackage number, which stays `nzikVersionName`).
2. **Write the changelog** `Updater/changelogs/{nzikVersionCode}.txt` (repo root, English — the phone's convention; `dev.txt` stays the generic dev line, never version-specific).
3. **Build the channel's full set of 6 assets** (stable: the Windows host for the `.exe` + WSL for the five Linux artifacts; beta / dev: the same, each with its channel): `./build.sh package stable|beta|dev` and `gradlew.bat :app:packageInstaller -Pchannel=stable|beta|dev`. The artifact names carry the channel suffix on the per-channel product name (AD-8 — see "Build channels" above: `N-Zik Desktop Compagnon (Beta)-…-beta.exe`, the dev base `n-zik-desktop-compagnon-dev`) — stable stays byte-identical to the pre-channel convention.
4. **Tag + release per channel:** the GitHub tag is `v{base}` (stable), `v{base}-beta`, or `v{base}-dev-YYYYMMDD` (the dev build date). The **release body must carry the raw changelog link** `Updater/changelogs/{code}.txt` (the updater's phone regex `CHANGELOGS_PATH/(\d+).txt` derives the changelog URL from it — the dialog shows the changelog only when the body links it; a dev release fetches `dev.txt` regardless). Attach **only the channel's assets** (never a cross-channel mix) + their SHA-256 lines; beta is a pre-release.
5. **AUR release entry:** bump `pkgver` + recompute the three `sha256sums` (stable tag only — the entry is reserved to stable).
6. **Verification gate (Windows stable only):** empirically verify the in-app updater's INTERACTIVE install flow on a real Windows machine before the release ships (the NSIS contract — the `/UPDATE` launch, the same-or-older-version "reinstall anyway?" gate, the scope page per-user default / global + visible UAC, the exit-code handling and the running-app guard — is pinned by `InstallerContractTest`, but the live UAC flow is still NOT yet verified on a real machine; spec design note — tracked in `assets/notes/TODO.txt`): the check against the real GitHub (current release → "no update available"), then a manual test `-beta` release → check → download → interactive install → relaunch, token + settings intact.

### AppImage (the 5th Linux path)

`./gradlew :app:packageAppImage` — Linux/WSL host only; on a Windows host the task logs a lifecycle message and skips (the `packageLinuxPortable` pattern), so `gradlew.bat build` stays green (spec `spec-linux-appimage`):

- **What it produces:** `binaries/n-zik-desktop-compagnon-<version>-x86_64.AppImage` (x86_64 only) — the app + the jpackage JRE + the official Linux VLC runtime in `usr/lib/app/resources/vlc`, plus its true system dependencies bundled by linuxdeploy (the codec libs, the X/AWT libs, PulseAudio). The AppImage's **SHA-256 is published with the release** (the 5th release asset; no AUR entry, no updater, no signature — like the other assets).
- **The shared app-image stays clean:** the task reuses the exact app-image the portable zip packages (one source of truth) and copies it into an AppDir; the Linux runtime is injected only into the AppDir's `usr/lib/app/resources/vlc` (the cfg's `$APPDIR/resources`), so the `.deb`/`.rpm`/AUR/portable never carry it (pinned by `LinuxPackagePinTest`).
- **Pins (catalog, verified before use):** the Linux runtime tarball `vlc-3.0.24-linux-x64.tar.gz` (URL + SHA-256 — built once from the official VideoLAN source tarball `vlc-3.0.24.tar.xz` by `scripts/build-vlc-linux-tarball.sh`: the 32-file audio subset, build-time rpath stripped from every `.so`; no committed VLC binary) and linuxdeploy 1-alpha (URL + SHA-256). The AppImage file name is frozen by the same test.
- **Build-host notes:** linuxdeploy (itself an AppImage) runs via `--appimage-extract-and-run` — no FUSE needed to build. The AppDir + the linuxdeploy out-dir live under the Gradle user home (local disk, no space in the path): the repo sits on a 9P mount where the long-lived daemon can serve a stale (empty) dir listing, and linuxdeploy's `--exclude-library` matches the **file name only** — its scanner walks every ELF in the AppDir (JRE included) and would otherwise flatten the app's own libs into the top-level `usr/lib/`, where the jpackage launcher's `$ORIGIN/../lib` RUNPATH would load `usr/lib/libjvm.so` instead of the real JRE at `usr/lib/runtime` and die with `could not open <AppDir>/usr/lib/jvm.cfg`. The task therefore passes every JRE lib name (enumerated, not hard-coded) + the 3 runtime libs as exclusions, and **fails the build if `usr/lib/libjvm.so` reappears** (regression guard).
- **Host-side audio dep (accepted, standard AppImage convention):** linuxdeploy blacklists `libasound.so.2` (the host audio stack — the Linux equivalent of leaving WASAPI to Windows), so the bundled ALSA output uses the host's `alsa-lib` (present on virtually every Linux desktop, and under WSLg); the PulseAudio output is fully bundled.
- **Usage / FUSE:** the target needs `fuse3` to mount the AppImage; on a host without FUSE, run it with the same flag the build uses: `./<file>.AppImage --appimage-extract-and-run`.

### Flatpak (the 6th Linux path)

`./gradlew :app:packageFlatpak` — Linux/WSL host only; on a Windows host the task logs a lifecycle message and skips (the `packageLinuxPortable` pattern), so `gradlew.bat build` stays green (spec `spec-linux-flatpak`):

- **What it produces:** `binaries/n-zik-desktop-compagnon-<version>-x86_64.flatpak` (x86_64 only) — the app + the jpackage JRE + the official Linux VLC runtime in `/app/lib/app/resources/vlc`, inside a Flatpak bundle. The Flatpak's **SHA-256 is published with the release** (the 6th release asset; unsigned by design, like the other assets).
- **Manual build, no Flathub submission:** the bundle is built locally on the build host (WSL) and distributed as a GitHub release asset, like the five others — no Flathub app page, no Flathub CI, no review. The only Flathub involvement is downloading the base runtime `org.freedesktop.Platform` 24.08 (AD-2) from the Flathub CDN: at build time (once, into the flatpak install dir) and at the first install on the target machine (once). The app-id `com.nzik.desktop.compagnon` is FROZEN (AD-1): the install name, the upgrade identity and the icon name all derive from it — a changed app-id orphans every installed copy. A self-hosted runtime mirror stays the documented alternative on renegotation.
- **Host prerequisites (WSL, once):** `sudo apt install flatpak-builder` (pulls in `flatpak`; both are apt host tools — never pinned artifacts) + one flatpak remote, e.g. `flatpak remote-add --user flathub https://dl.flathub.org/repo/flathub.flatpakrepo` (user scope, no root needed). Missing tools or a missing remote fail the build with the exact command.
- **Same assembly as the AppImage:** the task reuses the exact app-image the portable zip + AppImage package (one source of truth), copies it into a staging dir under the Gradle user home (`caches/n-zik-compagnon/flatpak/app` — local disk, no 9P), checks the same fail-loud boundary (the shared app-image stays free of `lib/app/resources/vlc`), injects the same pinned Linux runtime into `lib/app/resources/vlc` (the one rule in `VlcRuntime`: the resources dir carries `libvlc.so` → embedded — the sandbox never sees a system libvlc), then `flatpak-builder` (the committed `packaging/flatpak/manifest.json` template — only its two placeholders, the app-id and the version, are substituted) + `flatpak build-bundle`.
- **The wrapper launcher:** the built libraries carry no rpath, and a Flatpak `.desktop` cannot carry `Env=` (flatpak ignores it) — so the bundle's entry point is the `/app/bin/nzik` wrapper: it exports `LD_LIBRARY_PATH=/app/lib/app/resources/vlc` and execs the jpackage launcher (the space-containing display name, quoted). `bin/` is mandatory, not cosmetic: the flatpak sandbox's `PATH` puts `/app/bin` first (the bare `Exec=nzik` / the manifest's `command: nzik` resolve there), and flatpak-builder's finish step hard-fails when the command binary is not in `<builddir>/files/bin/` (`Error: Command 'nzik' not found`) — a top-level `/app/nzik` is never resolved. The `.desktop` (Exec=nzik, Icon=app-id) + the hicolor icon ride under `/app/share`.
- **The finish-args (the sandbox contract, pinned by `FlatpakPinTest`):** `--share=network` (the pairing listener + the phone's LAN server), `--socket=x11` / `wayland` / `pulseaudio` (windowing + audio), `--device=dri` (GPU rendering), `--filesystem=home` (the data dir `~/N-Zik Desktop Compagnon/`), `--talk-name=org.freedesktop.secrets` (the keyring — the current Secret Service spec bus name, the only D-Bus name the app may reach, through the runtime's D-Bus proxy; no raw bus socket). The socket/device type names are validated by flatpak at build time (a wrong one fails the finish step — e.g. `ipc` is a `--share=` type, `dri` a `--device=` type). The keyring also needs its **client library** inside the sandbox: the freedesktop runtime does not ship `libsecret-1.so`, so the bundle carries a second module that builds `libsecret` from the pinned GNOME source (0.21.8.2 — the 0.21 series speaks the current Secret Service spec `org.freedesktop.secrets` that modern keyring daemons register; the 0.20 series only targets the legacy `org.freedesktop.SecretService` name. URL + SHA-256 pinned in the template and `FlatpakPinTest`) — without it the app silently falls back to the in-memory session store (the token would not survive a restart). Audio inside the sandbox goes through PulseAudio (`--socket=pulseaudio`): the sandbox has no `/dev/snd`, so an ALSA output cannot reach the host audio (unlike the AppImage, whose ALSA output uses the host's `alsa-lib` and whose PulseAudio output is bundled).
- **flatpak-builder 1.4.x notes** (verified on flatpak-builder 1.4.8 + flatpak 1.16.6, Ubuntu 26.04 WSL): the **app** module must be `buildsystem: simple` (flatpak's no-compiler type — the default autotools fails with `Can't find autogen, autogen.sh or bootstrap`); its build-commands run with the staging dir as CWD and copy the contents to `/app/` (the build sandbox's app prefix = the final `/app` of the bundle). The second module (`libsecret`) uses `buildsystem: meson` — it compiles the pinned GNOME source with the SDK's compiler (do not "fix" it to `simple`: the bundle would then ship no `libsecret-1.so` and the keyring would silently fall back to the session store). The `flatpak-builder` invocation must pass `--repo <builddir>/repo`: without it (or `--install`) 1.4.x only stages the app in the build dir and never commits it to a repo, leaving `build-bundle` with nothing to package (`build-export` auto-creates the repo). `flatpak build-bundle` takes `REPO FILENAME APP-ID` (with an explicit `--arch=x86_64`; the bundle is written to the process's CWD — the workdir). The build dir must be a **sub-dir** of the workdir and flatpak-builder must run with the workdir as CWD: `--force-clean` wipes the entire build dir (staging + generated manifest must not live in it) and refuses to delete the CWD, the state dir (`.flatpak-builder`, CWD-based) or their parents. The produced bundle is an ostree static delta, **not** a tar — inspect by installing it, not with `tar tf`. The staged icon must be a valid PNG (`build-export --update-appstream` validates it); the export's `No appstream data` line is informational (the app ships no AppStream metainfo), and the 24.08 end-of-life notice at install time is expected (the base runtime is deliberately pinned — AD-2). The committed template carries informational top-level `icon` + `version` keys for review transparency (the real icon ships under `/app/share/icons` as the app-id) — flatpak-builder logs an `Unknown property` **warning** for each; the build is unaffected, do not "fix" them by deleting the keys (the `__VERSION__` placeholder is part of the pinned contract). The base runtime + sdk are pre-installed into the flatpak install dir (user scope) before the build — `--install-deps-from` would install them system-wide (the documented remote is user-scoped, no root on the build host).

### Live-install matrix (WSL 2 / WSLg, 2026-10-06 + 2026-10-07)

All six Linux paths were live-tested on WSL: the four install paths — Ubuntu 26.04 (`.deb`, including the 0.0.1→0.0.2 in-place upgrade with `~/N-Zik Desktop Compagnon/` preserved), Fedora 44 (`.rpm`), and Arch (`makepkg` for the release-zip entry and the `-git` entry — the `-git` `pkgver()` came out as the `main` tip's short hash) — plus the AppImage (launched via `--appimage-extract-and-run` under WSLg, no FUSE needed: JVM start, pairing screen and the self-contained layout verified, the process alive well past launch) and the Flatpak (2026-10-07: `flatpak install --user` of the locally built bundle — re-installing a newer bundle with the same app-id updates the existing install in place, the updater's flatpak gesture; `flatpak run --user` — process alive past launch, the pairing listener bound (network + home OK), and the keyring reachable inside the sandbox: the bundled `libsecret` loads and the `org.freedesktop.secrets` D-Bus call reaches the running keyring daemon through the talk-name proxy, no session-store fallback; the window renders via the Skiko software fallback under WSLg; the full visual + phone pairing on a real desktop = the friend's CachyOS live install, pending — non-blocking). For the install paths, every install, layout (`/opt/n-zik-desktop-compagnon/`, the `.desktop` + icon, the `/usr/bin` symlink), and uninstall was verified; **every launch was verified past "process alive"** — the window is mapped on the WSLg display (1090×770) and the pairing screen renders (QR code, IP/port, device-name input — confirmed by screenshot). WSLg mirrors the user's target environment (Wayland-only desktops): the app's windowing there is X11 against WSLg's X server (it does not use the `WAYLAND_DISPLAY` WSLg provides), and Skiko falls back to software rendering (no GL context under WSLg). If a window ever fails to open or freezes under Wayland, record it as a Wayland signal — the fallback is an explicit Compose `renderApi` selection (one line, no package impact).

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

## GitHub CI (channel workflows)

Three GitHub Actions workflows under `.github/workflows/` — one per channel, each run = 1 channel = 1 release = the channel's full set of 6 artifacts, unsigned, no fastlane (port of the phone repo's channel workflows, spec `spec-github-ci-canals`):

| Workflow | Channel | Tag | Triggers | Release |
|----------|---------|-----|----------|---------|
| `build-stable.yml` | `stable` | `v{base}` | Sunday 00:00 (Europe/Paris) + manual dispatch | normal, marked latest |
| `build-beta.yml` | `beta` | `v{base}-beta` | manual dispatch only | pre-release, not latest |
| `build-dev.yml` | `dev` | `v{base}-dev-YYYYMMDD` (the build date) | daily 00:00 (Europe/Paris) + manual dispatch + push to `main` | pre-release |

Each run is a linear pipeline: the guards → the two build jobs in parallel (`build-linux` = the 5 Linux tasks in ONE gradle invocation; `build-windows` = `:app:packageInstaller`) → `upload-to-release` (softprops — the tag, the release, the 6 artifacts collected from their exact build output paths (a missing one fails loud), `SHA256SUMS.txt` as the 7th asset) → the channel's post jobs (`close-issues` stable; `comment-beta-issues` beta; `cleanup-old-dev-releases` keeping 2 + `comment-dev-issues` dev) → `notify-discord`. The version is read from the catalog (`versioning` greps `nzikVersionName` + `nzikVersionCode` from `gradle/libs.versions.toml`) — the CI never writes it. Concurrency groups `stable-deploy` / `beta-deploy` / `dev-deploy` with `cancel-in-progress: false` (a second run queues instead of cancelling; re-running the same base updates the release in place — never a 2nd release). Both build jobs pin the runner OS timezone to Europe/Paris **before** the gradle invocation (the dev build date is the JVM's `LocalDate.now()`).

- **Guards (skip conditions):** **stable** — a GitHub release already exists for the catalog version (version-equal), `Updater/changelogs/{code}.txt` is missing, or the same base already has an in-flight `v{base}-beta` / `v{base}-dev-*`; **beta** — the same two (version-equal + missing changelog); **dev** — the push must be from `NEVARLeVrai` (schedule / dispatch always build; a Crowdin / "update stats and chart" / "In-app contributors list updated" commit or a `github-actions[bot]` push skips) plus a changes check: a `v{base}-dev-*` release dated today already exists → skip, a scheduled run with no relevant `NEVARLeVrai` commit in the last 24 h → skip, a push / dispatch → build. A guard trip on **stable/beta fails the guard job** (`exit 1` — verbatim mobile: the run goes red, the build jobs cascade-skip, no build and no release); a **dev** skip exits 0 with the build jobs skipped via `if:` — the run stays green. The stable in-flight guards and the dev `dev.txt` check fail loud when the GitHub API or the file state cannot be verified (hardening beyond the mobile port).
- **What the CI publishes:** the tag, the release with the channel's 6 artifacts (`.exe`, `.deb`, `.rpm`, portable `.zip`, `.AppImage`, `.flatpak`), `SHA256SUMS.txt` as the 7th asset, the release body (banner, the 6 SHA-256 lines, install steps, available builds, update policy, the raw changelog link `Updater/changelogs/{code}.txt` the in-app updater's regex needs, download links and the release page) and the Discord notification (secret `DISCORD_WEBHOOK_URL`).
- **What the CI does NOT do:** no signing / fastlane / keystore (the "unsigned, SHA-256 published" story of "Release chore — channels" stays), no catalog bump, no changelog writing — bumping `gradle/libs.versions.toml` + writing `Updater/changelogs/{code}.txt` remain the manual release chore, and the changelog must be written **before** the bump (the guard reads it).
- **Prerequisites (fail-loud early, never mid-build):** the pinned Linux VLC tarball `vlc-3.0.24-linux-x64.tar.gz` **uploaded as a release asset** (the AppImage release chore — until then the Linux job fails loud, `TARBALL_404`); the repo secret `DISCORD_WEBHOOK_URL` set once (if missing, ONLY `notify-discord` fails — the release is still published); the Linux runner tools `fakeroot` / `rpm` / `flatpak` / `flatpak-builder` + the Flathub remote + the pre-installed `org.freedesktop.Platform//24.08` runtime — installed as explicit steps of `build-linux` (the same host prerequisites as "Flatpak (the 6th Linux path)" above).
- **Manual fallback:** `./build.sh package stable|beta|dev` + `gradlew.bat :app:packageInstaller -Pchannel=…` + the manual tag/upload stay valid — the CI collects exactly those artifact names (see "Release chore — channels" above).

## CI Expectations

- **CI = the 3 channel workflows** (`.github/workflows/build-{stable,beta,dev}.yml` — see "GitHub CI (channel workflows)" above). They build + publish the channel releases, they do **not** gate on the test suite. Tests are local-only: always run `gradlew.bat build` yourself before reporting
- Releases are published by the channel workflows (tag + the 6 assets + `SHA256SUMS.txt` + the raw changelog link + Discord), with the manual `build.sh` / `packageInstaller` + upload as the fallback; there is no signing/keystore/fastlane machinery in this repo — the artifacts stay unsigned (the SHA-256 is published instead), and if a signing flow is ever added, the keystore handling must follow rules/SECURITY.md

## Code Formatting

- ktlint/detekt are **NOT configured** in this project — no lint task exists; do not search for one
- `kotlin.code.style=official` (gradle.properties)
- Follow existing file formatting patterns
- No trailing whitespace, newline at end of file
