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
./build.sh package          (Linux/WSL host)               # Linux .deb + .rpm + portable zip + AppImage + Flatpak (see "Linux packaging" below)
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

> **Host-gated:** `downloadVlc`/`extractVlc` (the official win64 zip) are disabled on a non-Windows host (`isWindowsHost` in `app/build.gradle.kts`) and the win64 runtime never enters the Linux distributable (tar.gz via `createDistributable`). On Linux, local playback uses the **system libvlc** (e.g. `sudo apt install vlc`); `VlcRuntime` skips the embedded gate and loads the system `libvlc` through vlcj's `NativeDiscovery`, and the "This PC" unavailable message carries the distro's install command (spec `spec-linux-system-libvlc`). The **AppImage is the one Linux exception** (spec `spec-linux-appimage`): it embeds the Linux runtime — a separate pinned tarball (`downloadVlcLinux`/`extractVlcLinux`, Linux host only) injected into the AppDir's `resources/vlc`, never into the shared app-image. `VlcRuntime` applies one unified rule everywhere: the app's resources dir contains the platform's libvlc (`libvlc.dll` on Windows, `libvlc.so` on Linux) → embedded; otherwise system discovery.

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
- **User data lives outside the package:** `~/N-Zik Desktop Compagnon/` + the keyring (libsecret) — an in-place upgrade (same package name, monotone version) and an uninstall leave it alone. The frozen upgrade UUID stays exclusive to the Windows installer.
- **Unsigned by design (deferred):** no GPG signature and no self-hosted apt/rpm repo — the packages are distributed as GitHub release files, so `apt install ./n-zik…deb` shows an "unauthenticated" warning (accepted; the source is public and any build can be rebuilt and compared against this one — not byte-reproducible, but a rebuild ships the same files and behavior). Signing + a self-hosted repo are deferred (`deferred-work.md`). The AppImage and the Flatpak are likewise unsigned (same deferral — the Flatpak by design, its SHA-256 published instead).

### jpackage tooling (WSL)

jpackage builds the `.deb` with `dpkg-deb` + `fakeroot` and the `.rpm` with `rpmbuild`. On WSL (Debian/Ubuntu-based) install them **system-wide**:

```bash
sudo apt install -y fakeroot rpm
```

`dpkg-deb` ships with `dpkg` (already present). A user-prefix install of `rpm`/`fakeroot` does **not** work — `rpmbuild` has a compiled-in `/usr/lib/rpm/rpmrc` path and the Debian `fakeroot` wrapper hardcodes a `/usr/lib` LD_PRELOAD path, so both need the system location.

**WSL build environment (2026-10-06):** the host's distros are `Ubuntu` (26.04), `FedoraLinux-44` and `archlinux` (`wsl -d <name>`; `-u root` works passwordless). The repo lives on the Windows D: drive → `/mnt/d/Autres/Projet Android/NZik-Folder/N-Zik-Desktop-Compagnon` (a 9P mount — slower than ext4, and real-time timing tests flake more readily on it). Non-root Gradle runs use the default user `nevar`, whose JDK 21 is at `/home/nevar/jdk21`: non-interactive bash does **not** source `~/.bashrc`, so export `JAVA_HOME` / `PATH` explicitly; and Windows PowerShell → WSL strips inner double quotes from inline commands, so for anything beyond a one-liner write a script file (e.g. under `C:\Users\Danie\AppData\Local\Temp\opencode\`, reachable as `/mnt/c/...`) and run `wsl -d Ubuntu -u nevar -- bash <that path>`.

### AUR entries (Arch / Manjaro / CachyOS)

Two entries under `packaging/`, pinned by `AurPkgbuildTest`:

- **`packaging/aur/PKGBUILD`** — `n-zik-desktop-compagnon` (release-zip): downloads the portable zip + the icon, both pinned to the release tag `v<pkgver>`, and installs them to `/opt/n-zik-desktop-compagnon/` + the `/usr/bin` symlink + `.desktop` + icon. `depends=(vlc)`, `makedepends=(unzip)`.
- **`packaging/aur-git/PKGBUILD`** — `n-zik-desktop-compagnon-git`: clones `main` and builds the app-image with `./gradlew :app:createDistributable`. `makedepends=(jdk21-openjdk)` — JDK 21 on the user's machine (the Arch package name; the spec's `openjdk-21` is the Debian/Fedora name — refined here). `depends=(vlc)`. The first build downloads the Gradle wrapper + dependencies (~1 GB cache, a few minutes).
- **The `-git` entry's live-build requirements (makepkg ≥ 7.0, verified 2026-10-06 on WSL Arch, makepkg 7.1.0):** `pkgver()` runs before the source is downloaded (there is no local clone to inspect yet — the version is the 7-char short hash of the `main` tip, read with `git ls-remote`), and makepkg lints the `pkgver` variable before it runs the function (hence the `pkgver=0` placeholder); the VCS source uses the `name::url` syntax (the custom clone-dir name comes before the `::` — makepkg's `get_url` strips the prefix before the first `::`) with `sha256sums=('SKIP')`; and `package()` runs under fakeroot (as root), so `GRADLE_USER_HOME` is pinned to the build dir (otherwise the Gradle wrapper writes to `/root/.gradle`, which is not writable by the build user). A function-only `pkgver()` with no placeholder dies at lint with "pkgver is not allowed to be empty".

The jpackage app-image puts its launcher in `<app-image>/bin/` (named after the display name, which contains spaces) and bundles its own JRE under `lib/runtime`. The app-image folder is named after the display name, but **all four Linux install paths use the same `/opt` location — `/opt/n-zik-desktop-compagnon/`**: the `.deb`/`.rpm` get it from jpackage (`--linux-package-name`), and both AUR entries install the app-image under the lowercase package name (unzip + rename). Each entry also creates a **space-free `/usr/bin/<pkgname>` symlink** to the `bin/` launcher (the `.desktop` `Exec` points at the symlink, not the raw space-containing `/opt` path) and **fails loudly if the app-image layout ever changes**. The jpackage-generated `.desktop` files are self-consistent with their own (lowercase) `/opt` layout: `Exec="/opt/n-zik-desktop-compagnon/bin/N-Zik Desktop Compagnon"` (the space-containing path quoted), `Icon=` the bundled PNG.

**Release chore (release-zip entry only):** on every GitHub release, bump `pkgver` and recompute **all three** `sha256sums` (the portable zip, the icon and the LICENSE text — all are pinned to the tag `v<pkgver>`, which must exist for the `releases/download` URL to resolve; the current zip is `9e9f555a135c54b297ba30a53b5c9d451078deed4e88b99e2b762482d73394eb`, the icon is `831edcc324a716691b501f976b8a8b8254c4615a69f82ba088bec2bf775797e4`, and the LICENSE sum is filled from the same tag). Publish the `.deb`/`.rpm`/zip/**AppImage**/**Flatpak** with their **SHA-256 checksums** alongside the release (the README's integrity note covers the Linux assets, not just the Windows installer). The AppImage additionally needs the pinned Linux VLC tarball published as a release asset — built once by `scripts/build-vlc-linux-tarball.sh` (official source, audio subset, rpath stripped); until that upload chore lands (tracked in `assets/notes/TODO.txt`) the build is cache-first in the Gradle user home and the pinned URL becomes authoritative once uploaded. The `-git` entry tracks `main` with a dynamic `pkgver()` and needs no chore.

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

## CI Expectations

- **This repo has no CI** — no `.github/` workflows, no pre-commit hooks. Tests are local-only: always run `gradlew.bat build` yourself before reporting
- Releases are **manual** GitHub releases (portable Windows EXE — spec `spec-n-zik-pc-bridge` story 13, tracked in `assets/notes/TODO.txt`); there is no signing/keystore machinery in this repo yet — if a release flow is added, the keystore handling must follow rules/SECURITY.md

## Code Formatting

- ktlint/detekt are **NOT configured** in this project — no lint task exists; do not search for one
- `kotlin.code.style=official` (gradle.properties)
- Follow existing file formatting patterns
- No trailing whitespace, newline at end of file
