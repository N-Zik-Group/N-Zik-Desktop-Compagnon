<div align="center">
  <img alt="project's banner" src="./assets/design/ic_banner2.png" width="1080" />

  <h3>🌐 <a href="https://n-zik.vercel.app/">Official Website</a></h3>

  <p>
    <b>N-Zik Desktop Compagnon</b> is the desktop companion for
    <a href="https://github.com/N-Zik-Group/N-Zik">N-Zik</a> (Windows and Linux):
    control your phone's library and playback from a large screen, and listen on your computer.
  </p>

  <p>
    The <strong>phone stays the single source of truth</strong>. It runs a local server on your Wi-Fi network,
    and this app only talks to it: no business logic and no streaming stack live on the PC.
    Pair once (QR code or manual code), then browse your library, manage the queue and play tracks streamed from your phone.
  </p>

  <p>
    <strong>Transitional product.</strong> This companion bridges the gap until a standalone N-Zik desktop app exists.
    It will be replaced by it.
  </p>

  <p>
    <strong>N-Zik</strong> is a side project I originally built for myself and friends, not chasing glory.
    It's grown a bit since then, which is cool.
  </p>

  <p>
    I use generative AI to assist with code, structured with the
    <a href="https://github.com/bmad-code-org/BMAD-METHOD">BMAD Method</a>,
    but everything gets reviewed and tested before it's pushed. I'm not shipping blind.
  </p>

  <p>
    If AI-assisted development isn't your thing, no hard feelings,
    there are plenty of great alternatives.
  </p>
</div>

<div align="center">
  <img src="assets/stats/download-card.svg" alt="N-Zik Compagnon Stats" />
  <img src="assets/stats/chart.svg" alt="Download Growth" />

  <br><br>

[![Launched on DevGlobe](https://devglobe.app/badges/launched-on-devglobe-dark.svg)](https://devglobe.app/projects/n-zik-desktop-compagnon?utm_source=badge&utm_medium=embed)

  [![Localization Progress](https://badges.crowdin.net/N-Zik/localized.svg)](https://crowdin.com/project/N-Zik) [![License: GPL v3](https://img.shields.io/github/license/N-Zik-Group/n-zik-desktop-compagnon?color=blue)](https://www.gnu.org/licenses/gpl-3.0)
  [![CodeFactor](https://www.codefactor.io/repository/github/n-zik-group/n-zik-desktop-compagnon/badge)](https://www.codefactor.io/repository/github/n-zik-group/n-zik-desktop-compagnon)
</div>

# 📲 Installation

[![GitHub](https://github.com/N-Zik-Group/N-Zik/blob/main/assets/get-it-on/GitHub.png?raw=true)](https://github.com/N-Zik-Group/n-zik-desktop-compagnon/releases/latest)
[![BetaVersions](https://github.com/N-Zik-Group/N-Zik/blob/main/assets/get-it-on/GitHubBeta.png?raw=true)](https://github.com/N-Zik-Group/n-zik-desktop-compagnon/releases?q=&type=prerelease)

## 📦 Available Builds

- **Windows Installer** (`.exe`): an NSIS install with a scope-choice page (per-user by default, no prompt; the global scope asks for a visible UAC elevation) with Start menu + desktop shortcuts and an uninstall entry; a newer version of the same channel upgrades it in place.
- **Windows Portable**: The distributable app folder, no install needed (produced alongside the installer).
- **Debian package** (`.deb`): Debian / Ubuntu / Mint - `apt install ./<file>.deb`.
- **RPM package** (`.rpm`): Fedora / openSUSE / Rocky - `dnf` or `zypper` `install ./<file>.rpm`.
- **Linux Portable** (`.zip`): The app-image, unzip and run - the same bundle the AUR release entry consumes.
- **Linux AppImage** (`.AppImage`): A single self-contained file - app + JRE + the official VLC runtime, so it plays without any system VLC; runs directly, no install.
- **Linux Flatpak** (`.flatpak`, x86_64): The sandboxed install for Flatpak-based systems - app + JRE + the official VLC runtime, so it plays without any system VLC; the keyring client (libsecret) is built into the bundle from its pinned GNOME source; a manual build (no Flathub submission), installed with `flatpak install`.
- **AUR**: `n-zik-desktop-compagnon` (the latest release) and `n-zik-desktop-compagnon-git` (always the latest of `main` - needs JDK 21 to build).

> ℹ️ The builds are published as **GitHub release files only** - no self-hosted repository. On Linux, VLC is a **declared system dependency** of the packages (never bundled): the package manager pulls it in automatically and the app plays through the system libvlc - the **AppImage and the Flatpak are the exceptions**, they embed the official VLC runtime so they need no system VLC. Every install ships an application-menu entry (`.desktop` + icon) and uninstalls cleanly, leaving your data (pairing, settings, audio cache) alone.

### 🔄 Channels and auto-update

The app is released on **channels** (the phone's discipline, adapted): **stable** (the plain releases), **beta** (pre-releases, tagged `v{base}-beta`) and **dev** (dated pre-releases, tagged `v{base}-dev-YYYYMMDD`). The channel is fixed at build time (`-Pchannel` - never a runtime toggle) and only the **same channel** is ever offered as an update. Each channel is built and published by its own **GitHub Actions workflow** (`.github/workflows/build-{stable,beta,dev}.yml`): **stable** on Sundays (Europe/Paris) and on demand, **beta** on demand, **dev** daily, on demand and on pushes to `main`.

- **Channel workflows:** each channel's workflow builds the channel's full set of artifacts and publishes the release - the tag, the 6 assets + `SHA256SUMS.txt` (a SHA-256 table and the download links live in the release body, alongside the raw changelog link the updater reads), and a Discord notification. The version bump and the changelog file (`Updater/changelogs/{code}.txt`) stay manual - the CI never bumps the version, writes the changelog or signs the artifacts (see the unsigned notes above) - and the manual build + upload (`build.sh` / `packageInstaller`) remains the fallback.
- **In-app updater:** on the stable/beta/dev channels the app checks the [GitHub releases](https://github.com/N-Zik-Group/n-zik-desktop-compagnon/releases) at startup (settings → About → the update card → the update page: the startup check is a three-state choice — **on** (automatic check), **ask** (an info dialog first, the phone's own), or **off** (never) — and the check can also be run on demand from the update page itself), shows the release's changelog, downloads the artifact for your install with progression, and offers the native install gesture per install mode - **Windows**: in-place reinstall — the app quits, then the installer runs interactively with the `/UPDATE` flag (the scope defaults to the current install's scope — per-user or global, the global one shows a visible UAC prompt — and an "already installed — reinstall anyway?" confirmation shows first when the installed version is the same or older; cancellable — and the app relaunches itself on success); **Flatpak**: the exact `flatpak install <file>` command; **`.deb`/`.rpm`** (installed from a package manager): the exact `apt`/`dnf` install command, shown but never auto-run (no privilege escalation); **AppImage / portable**: the old location + the downloaded file, to replace by hand. The download cleans up its own file on cancel, and a failed download (a body shorter than its `Content-Length` is rejected by the HTTP client while receiving it) shows a clean error and can be re-run.
- **Channel badge:** the app header shows a small badge for non-stable builds - **Beta**, **Dev**, **Git** (source builds from `main`, the AUR `-git` entry) or **Debug** (a plain run/compile); the stable build shows none.
- **Per-channel product identity (AD-8):** the product name is `N-Zik Desktop Compagnon (Beta)` / `N-Zik Desktop Compagnon (Dev)` for beta / dev (the installer base name, the window title, the badges; stable keeps the plain name). On Windows every channel is its own installation (its own registry identity - no frozen upgrade identifier anymore), so a beta or dev install lives beside the stable one; on Linux the **beta channel replaces the stable one in place** (same package base + Flatpak app-id) while the **dev channel** installs as `n-zik-desktop-compagnon-dev` in `/opt/` with its own Flatpak app-id - a parallel product, never a replacement. The updater of each channel only ever offers its own channel.
- **Source builds:** a run from source or the AUR `-git` entry never checks for updates (anti-downgrade: a `main` build must not offer a release as an "update") - the settings entry is disabled with an explanation there.

---

<div align="center">

## 📚 Wiki

[![Ask DeepWiki](https://deepwiki.com/badge.svg)](https://deepwiki.com/N-Zik-Group/N-Zik-Desktop-Compagnon)

<br>

## 🌍 Community

Join the N-Zik Discord:

<a href="https://discord.gg/bneHC7QRje">
  <img src="https://discord.com/api/guilds/1345079801324634193/widget.png?style=banner2" alt="Discord Server">
</a>

<br>

</div>

# 🎧 Features

- 🔗 **QR or Manual Pairing**: Pair once with the QR code shown by the app (or the phone's IP, port and code by hand); the pairing is remembered across restarts, and the device token lives in the system secret store - the Windows Credential Manager, or the Linux keyring (in memory only, for the session, on a Linux machine without a keyring daemon).
- 📚 **The Phone's Library**: Songs (search, filters, sorts), Artists, Albums and Playlists, switched from the floating bar at the bottom, with live refresh of the phone's lists.
- ⏯️ **Unified Player**: A mini player above the navigation bar opens the full player: rotating cover animation, colours that follow the current cover, seek, speed, repeat and shuffle.
- 📋 **Queue**: Opens from the player or the mini player: jump to a track, move it, remove it, clear the queue.
- 🎮 **Remote Control**: Everything plays on the phone: play / pause / previous / next, seek, speed, repeat, shuffle, and per-track menus (a right click is the phone's long press).
- 🖥️ **Sound on the PC**: Stream the audio to your PC instead of the phone: pick the output device (this PC or the phone) and the PC follows the phone's playback; when this PC's session ends, the phone takes the sound back.
- 💾 **Local Audio Cache**: Tracks already played in full are kept in a local cache and replayed without asking the phone again, with a configurable size (2 GB by default).
- 🔊 **Embedded VLC**: Audio is played on an embedded copy of VLC: no VLC installation is needed on your PC.
- 🎨 **The Phone's Look**: The UI is ported from N-Zik: same screens, same theme, dynamic palette from the current cover.
- 🔁 **Auto-Reconnect**: The app reconnects by itself after a network loss; when the phone's server is stopped, or when you disconnect this PC from the phone, use "Reconnect".

# 🔒 Pairing, Windows Firewall and manual mode

For QR pairing, the phone connects back to a temporary listener opened by this app on your local network. Windows may ask whether to allow N-Zik Desktop Compagnon (Java) through the firewall: allow it on **private** networks. If your Wi-Fi is set to **Public**, Windows blocks the phone; switch the network to Private, or use manual pairing.

If the phone has not reached the PC within 60 seconds, or if no local network address is found, the app switches to manual pairing: enter the IP address, port and 6-character code shown on the phone's "Manage server" screen. The QR code stays available.

The device token is stored in the platform secret store: the Windows Credential Manager (generic credential "N-Zik Desktop Compagnon") on Windows, and the keyring on Linux (GNOME Secret Service: `libsecret` + a keyring daemon such as `gnome-keyring`). On a Linux machine without a keyring daemon, the token is kept in memory for the session only, with a visible note on the paired screen - pair again after a restart. The other pairing details live in `pairing.json` (`%APPDATA%\N-Zik Desktop Compagnon\` on Windows, `~/N-Zik Desktop Compagnon/` on Linux). "Forget this phone", or revoking this PC from the phone, removes both.

# 🔊 Sound on the PC

The mini player's "audio output" button opens the audio devices: choose **This PC** to hear the music on your computer, or your phone to hear it there. Both keep controlling the same playback; only one of them sounds. While the PC plays, the phone keeps playing silently, and the PC follows it (pause, seek, speed, next track). If this PC's session ends (app closed, disconnected from the phone, Wi-Fi lost), the phone pauses and takes the sound back. The button needs N-Zik with contract 1.2; with an older N-Zik it is hidden.

### 🎬 Embedded VLC

Audio is played by [vlcj](https://github.com/caprica/vlcj) 4.12.1 on an embedded copy of [VLC](https://www.videolan.org/) 3.0.24: no VLC installation is needed. The build downloads the official VideoLAN zip (`vlc-3.0.24-win64.zip`) for the Windows build and the pinned Linux tarball (`vlc-3.0.24-linux-x64.tar.gz`, produced by `scripts/build-vlc-linux-tarball.sh` from the official VideoLAN source and published as a release asset) for the AppImage, checked against their pinned SHA-256s, keeps both in the Gradle user home so `clean` does not download them again, and extracts only the audio part (libvlc, libvlccore, about 30 plugins, the license file: about 10 MB) under `app/build/vlc-runtime`. No VLC binary is stored in this repository. VLC bumps are produced by the `vlc-tarball-builder.yml` pipeline: bump the committed script on `main` and it builds the tarball, publishes the prerelease staging release `vlc-<v>-tarball`, and opens a catalog-only PR — reviewing and merging that PR activates the new pin (the bot never merges). The current 3.0.24 tarball is still the manual one-shot, published in the same namespace (the `vlc-3.0.24-tarball` prerelease). VLC is © the VideoLAN team and contributors; libvlc is licensed under the LGPL 2.1 or later and some plugins under the GPL 2 or later (see `COPYING.txt` next to the runtime). vlcj is licensed under the GPL 3.0.

If the embedded VLC cannot be loaded, the app still works: "This PC" is disabled with a message.

# 📷 Screenshots & Videos

# 🌐 Supported Languages

Thanks to all our amazing contributors!  
Here are the languages currently supported:

- 🇿🇦 **Afrikaans**: [HelloZebra1133](https://crowdin.com/profile/HelloZebra1133)
- 🇸🇦 **Arabic**: [ABS zarzis](https://crowdin.com/profile/abszar), [Ahmad Al Juwaisri](https://crowdin.com/profile/juwaisri)
- 🇦🇿 **Azerbaijani**: [Nizami Səmidov](https://crowdin.com/profile/nizamismidov4), [Notesuree](https://github.com/Notesuree)
- 🇧🇩 **Bangla**: [Ann Naser Nabil](https://github.com/AnnNaserNabil)
- 🇷🇺 **Bashkir**: [Shilave malay](https://crowdin.com/profile/Bash.boy)
- 🇪🇸 **Catalan**: [Adrià Martínez](https://crowdin.com/profile/marxally), [Aniol](https://crowdin.com/profile/aniol), [EMC_Translator](https://crowdin.com/profile/EMC_Translator)
- 🇨🇳 **Chinese (Simplified)**: [benhaotang](https://crowdin.com/profile/benhaotang), [SharkChan0622](https://github.com/SharkChan0622)
- 🇹🇼 **Chinese (Traditional)**: [YeeTW](https://github.com/yjcTW), [SharkChan0622](https://github.com/SharkChan0622)
- 🇨🇿 **Czech**: [ikanakova](https://github.com/ikanakova), [JZITNIK-github](https://github.com/JZITNIK-github)
- 🇩🇰 **Danish**: [cultcats](https://crowdin.com/profile/cultcats)
- 🇳🇱 **Dutch**: [BabyBenefactor](https://crowdin.com/profile/BabyBenefactor)
- 🇬🇧 **English**: [Alejandro Moctezuma](https://crowdin.com/profile/alejandromoc), [twistios](https://crowdin.com/profile/twistios), [Smk90](https://crowdin.com/profile/smk90), [CanIn](https://crowdin.com/profile/canin), [koliwan](https://crowdin.com/profile/koliwan), [Glich440](https://github.com/Glich440), [fast4x](https://github.com/fast4x)
- 🌍 **Esperanto**: [kefiiris](https://github.com/kefiiris)
- 🇪🇪 **Estonian**: [beez276](https://crowdin.com/profile/beez276)
- 🇵🇭 **Filipino**: [Clyde-Timonera](https://github.com/Clyde-Timonera)
- 🇫🇮 **Finnish**: [Smk90](https://crowdin.com/profile/smk90), [rikalaj](https://crowdin.com/profile/rikalaj)
- 🇫🇷 **French**: [Mickael81](https://crowdin.com/profile/mickael81), [esophagusdecency](https://crowdin.com/profile/esophagusdecency), [NEVARLeVrai](https://github.com/NEVARLeVrai)
- 🇪🇸 **Galician**: [zordor](https://crowdin.com/profile/zordor), [ninjum](https://crowdin.com/profile/ninjum)
- 🇩🇪 **German**: [twistqj](https://crowdin.com/profile/twistqj), [nitro4542](https://crowdin.com/profile/nitro4542), [twistios](https://crowdin.com/profile/twistios), [Eddisch](https://crowdin.com/profile/eddisch2010), and more...
- 🇬🇷 **Greek**: [Marinkas](https://github.com/Marinkas)
- 🇮🇱 **Hebrew**: [opcitgv](https://crowdin.com/profile/opcitgv), [TheCreeperDuck](https://crowdin.com/profile/thecreeperduck)
- 🇮🇳 **Hindi**: [NikunjKhangwal](https://crowdin.com/profile/nikunjkhangwal), [Sharunkumar](https://crowdin.com/profile/sharunkumar), [Th3-C0der](https://github.com/Th3-C0der)
- 🇭🇺 **Hungarian**: [Zan1456](https://crowdin.com/profile/Zan1456), [Ndvok](https://crowdin.com/profile/ndvok)
- 🇮🇹 **Italian**: [Fabio Iotti](https://crowdin.com/profile/bruce965), [CiccioDerole](https://crowdin.com/profile/CiccioDerole), [fast4x](https://github.com/fast4x)
- 🇮🇩 **Indonesian**: [luthfialfarabi](https://crowdin.com/profile/luthfialfarabi), [teddysulaimanGL](https://github.com/teddysulaimanGL)
- 🌐 **Interlingua**: [softinterlingua](https://github.com/softinterlingua)
- 🇯🇵 **Japanese**: [maboroshin](https://crowdin.com/profile/maboroshin), [Mid_Vur_Shaan](https://crowdin.com/profile/Mid_Vur_Shaan)
- 🇰🇷 **Korean**: [ZeroZero00](https://crowdin.com/profile/ZeroZero00), [TsyQax](https://crowdin.com/profile/TsyQax)
- 🇳🇴 **Norwegian**: [Xyrcon](https://crowdin.com/profile/xyrcon)
- 🇮🇷 **Persian**: [CUMOON](https://github.com/CUMOON)
- 🇵🇱 **Polish**: [Krzysztof](https://crowdin.com/profile/scrummybingus), [AntoniNowak](https://crowdin.com/profile/AntoniNowak), and more...
- 🇵🇹 **Portuguese (Portugal)**: [ManuelCoimbra](https://crowdin.com/profile/ManuelCoimbra)
- 🇧🇷 **Portuguese (Brazil)**: [vs-machado](https://crowdin.com/profile/vs-machado), [xSyntheticWave](https://crowdin.com/profile/xSyntheticWave), [NEVARLeVrai](https://github.com/NEVARLeVrai)
- 🇷🇴 **Romanian**: [OrangeZXZ](https://github.com/OrangeZxZ)
- 🇷🇺 **Russian**: [Eddisch](https://crowdin.com/profile/eddisch2010), [Alnoer](https://crowdin.com/profile/Alnoer), [siggi1984](https://github.com/siggi1984), and more...
- 🇷🇸 **Serbian (Cyrillic & Latin)**: [IvanMaksimovic77](https://github.com/IvanMaksimovic77)
- 🇪🇸 **Spanish**: [Alejandro Moctezuma](https://crowdin.com/profile/alejandromoc), [DanielSevillano](https://github.com/DanielSevillano), and more...
- 🇱🇰 **Sinhala**: [VINULA2007](https://crowdin.com/profile/VINULA2007)
- 🇸🇪 **Swedish**: [sebbe.ekman](https://crowdin.com/profile/sebbe.ekman)
- 🇹🇷 **Turkish**: [abfreeman](https://github.com/abfreeman), [mikropsoft](https://github.com/mikropsoft), and more...
- 🇺🇦 **Ukrainian**: [Avin](https://crowdin.com/profile/avinateachip), [Crayz310](https://github.com/Crayz310), and more...
- 🇻🇳 **Vietnamese**: [teaminh](https://crowdin.com/profile/teaminh)

## 🌍 Help Translate

Want to:

- Translate into a new language?
- Improve an existing translation?
- Fix typos or inconsistencies?

Join us on Crowdin!

> ❓ Don't see your language?

[![Translated with Crowdin](https://badges.crowdin.net/badge/light/crowdin-on-dark.png)](https://crowdin.com/project/n-zik)

# 🛠️ Requirements & Building

## 📋 Requirements

- Windows 10 or 11, or Linux
- JDK 21 (used by the Gradle toolchain to build and run)
- N-Zik on an Android phone connected to the same Wi-Fi network

## 🏃 Build and run

Compile and run the tests. On Windows: `gradlew.bat`. On Linux: the `build.sh` script.

```bat
gradlew.bat build
```

```bash
./build.sh
```

The first build on a **Windows host** downloads VLC 3.0.24 from download.videolan.org (about 83 MB, once); on a **Linux host** the download is skipped and the app uses the **system libvlc** instead.

Launch the app on Windows:

```bat
gradlew.bat :app:run
```

A plain run (or a plain compile) builds the **debug channel** - the Debug badge in the header, no auto-update (source-build discipline). A release artifact is never built without an explicit channel: `-Pchannel=stable|beta|dev` on the packaging tasks (`stable` is the only one whose artifact names stay byte-identical to the original convention).

The app also runs on Linux: `./gradlew :app:run`. Pairing works there too (keyring, or session-only without a keyring daemon); "Sound on the PC" uses the **system libvlc** - install VLC (e.g. `sudo apt install vlc`) and local playback works; when it is missing, the app shows the install command for your distribution.

## 🪟 Windows installer

Alongside the portable build, the app ships as an NSIS Windows installer (a scope-choice page: per-user by default, or global with a visible UAC prompt):

```bat
gradlew.bat :app:packageInstaller -Pchannel=stable
```

The channel is required (`stable`, `beta` or `dev` - see "Channels and auto-update" above): the beta/dev installers are named after it on the per-channel product name (AD-8: e.g. `N-Zik Desktop Compagnon (Beta)-0.0.2-beta.exe` — the jpackage name with the channel suffix right after the version), the stable one keeps its plain name.

The installer (`.exe`, written under `app\build\compose\binaries\`) is built from `app\packaging\windows\installer.nsi` (the header there is the full contract - the NSIS installer replaced the jpackage self-extracting exe). It offers a scope-choice page: **per-user by default** (no administrator prompt, installs to `%LOCALAPPDATA%\N-Zik Desktop Compagnon`) or **global** (a visible UAC elevation, installs to `%PROGRAMFILES%\N-Zik Desktop Compagnon`). It adds Start menu + desktop shortcuts and registers the uninstall entry under the per-channel product name (**N-Zik Desktop Compagnon**). Building it needs NSIS 3.x on the build machine (the official portable zip per-user, or the `NSIS_DIR` environment variable - the build resolves `makensis.exe` at execution time and does not download the toolchain).

Installing a newer version on top of an existing one (same channel) **upgrades it in place** - the installation's identity is its registry key (`Software\N-Zik\DesktopCompagnon\{channel}` - HKCU for per-user, HKLM for global), recorded by the installer itself; no frozen upgrade identifier anymore. The user data in `%APPDATA%\N-Zik Desktop Compagnon\` (pairing, settings, audio cache) is left untouched by an upgrade or an uninstall, and the portable build is produced separately and is not affected.

> The installer is **not code-signed yet** (signing is deferred, pending a code-signing certificate - see the release cadence): Windows SmartScreen shows its usual "publisher not verified" notice on the first run of each new version. Every GitHub release therefore publishes the installer's **SHA-256 checksum** so you can verify your download (PowerShell: `Get-FileHash -Algorithm SHA256 "N-Zik Desktop Compagnon-x.y.z.exe"`; the Linux release assets - `.deb`, `.rpm`, portable zip, AppImage and Flatpak - are published with their **SHA-256 checksums** as well), and the source stays public, so any build can be rebuilt and compared against this one (builds are not byte-reproducible, but a rebuild ships the same files and behavior).

## 🐧 Linux packages

On Linux the app ships as a `.deb` (Debian/Ubuntu/Mint), a `.rpm` (Fedora/openSUSE/Rocky), an **AppImage** (any distro, no install), a **Flatpak** (any Flatpak-based distro) and through the [AUR](https://aur.archlinux.org/) for Arch-based distros (Arch, Manjaro, CachyOS). Grab the asset from a [GitHub release](https://github.com/N-Zik-Group/n-zik-desktop-compagnon/releases/latest) and install it - the AppImage is run, not installed (the exact filenames are the release assets):

```bash
# Debian / Ubuntu / Mint
apt install ./n-zik-desktop-compagnon_<version>-1_amd64.deb

# Fedora / openSUSE / Rocky
dnf install ./n-zik-desktop-compagnon-<version>-1.x86_64.rpm      # Fedora / Rocky
zypper install ./n-zik-desktop-compagnon-<version>-1.x86_64.rpm   # openSUSE

# Arch-based (AUR)
yay -S n-zik-desktop-compagnon        # the latest release
yay -S n-zik-desktop-compagnon-git    # always the latest of main (needs JDK 21 to build)
```

`yay` is an [AUR helper](https://aur.archlinux.org/packages/yay/) (itself an AUR package): without one, build the entry from its PKGBUILD with `makepkg -si`.

**AppImage (any distro, no install):** the AppImage release asset is a single self-contained file - app + JRE + the official VLC runtime (the one Linux build that needs no system VLC). It runs directly from wherever you put it:

```bash
chmod +x n-zik-desktop-compagnon-<version>-x86_64.AppImage
./n-zik-desktop-compagnon-<version>-x86_64.AppImage
```

Most distros need `fuse3` for the AppImage mount; on a host without FUSE, run it the extract-and-run way instead: `./<file>.AppImage --appimage-extract-and-run`. Dropping the file in an applications directory (e.g. `~/Applications`) makes some desktop environments register its embedded `.desktop` menu entry automatically; others do not. Like the other Linux assets, the AppImage's **SHA-256** is published with the release. The only host-side audio library it relies on is the ALSA one (`alsa-lib`, present on virtually every Linux desktop): the PulseAudio output is bundled, the ALSA output uses the host's.

**Flatpak (any Flatpak-based distro, x86_64):** grab the `.flatpak` release asset and install it with `flatpak install` (Flatpak must be on the machine, e.g. `sudo apt install flatpak`, and a flatpak remote must be configured for the base runtime — Flathub, once: `flatpak remote-add --user flathub https://dl.flathub.org/repo/flathub.flatpakrepo`; several distros do not add one automatically):

```bash
flatpak install ./n-zik-desktop-compagnon-<version>-x86_64.flatpak
```

The first install downloads the base runtime (`org.freedesktop.Platform`) once from the Flathub CDN (through the configured remote); the bundle itself is self-contained - app + JRE + the official VLC runtime, so no system VLC is needed. It is built manually on a Linux host (no Flathub submission - no app page, no CI, no review). Your data - pairing, settings and audio cache in `~/N-Zik Desktop Compagnon/`, plus the keyring token (GNOME Secret Service) - is left alone by an upgrade or an uninstall. The Flatpak is unsigned by design, like the other assets: its **SHA-256** is published with the release.

**No VLC to install with the packages:** the packages declare `vlc` as a dependency, so the package manager pulls it in automatically, and the app plays through the system libvlc. Installing puts the app in `/opt/n-zik-desktop-compagnon/`, adds a `.desktop` entry to the application menu (with the N-Zik icon) and a clean uninstall through the package manager. Your data - pairing, settings and audio cache in `~/N-Zik Desktop Compagnon/`, plus the keyring token - is left alone by an upgrade or an uninstall.

> **Minimal Arch installs:** the app needs X11 client libraries and a font to launch - a bare Arch system does not ship them, so a first launch may need a few extra packages (the full list is in `rules/BUILD.md`, "Live-install matrix").

> The Linux packages are **not code-signed yet** (signing is deferred, pending a self-hosted repository - see the release cadence): `apt install ./x.deb` shows the usual "unauthenticated" warning for a package installed from a file. The source is public, so any build can be rebuilt and compared against this one - the packages are not byte-reproducible (the `.deb` embeds build timestamps), but a rebuild ships the same files and behavior.

---

# 🤝 Contributing

## 🛠️ Improve the App

Pull requests are welcome!
Feel free to fix bugs, enhance features, or suggest new ideas.

# 📜 Clone the repo

Use this command to clone the repo

```
git clone -b main --single-branch https://github.com/N-Zik-Group/N-Zik-Desktop-Compagnon.git
```

# 🫂 Acknowledgements

### 🎵 Based on / Permission:

This project may reuse code from [Cubic Music Desktop](https://github.com/cybruGhost/DESKTOP-CUBIC-MUSIC) by cybruGhost, under the GPL-3.0. Its author granted explicit permission, as recorded in the Cubic Music Desktop notice:

> Copyright © 2026 cybruGhost and contributors.
>
> Cubic Music Desktop is open source under the GNU GPL v3.0. This notice records the author's explicit acknowledgement that N-Zik Group / N-Zik and NEVARLeVrai may reuse, modify and redistribute Cubic Music code under GPL-3.0, while preserving the applicable copyright, license and third-party notices.

Any file derived from Cubic Music Desktop keeps its original copyright and license notices.

### 📚 Libraries & Dependencies:

- [**vlcj**](https://github.com/caprica/vlcj): JVM bindings to play audio with the embedded [**VLC**](https://www.videolan.org/).
- [**Kotlin**](https://kotlinlang.org/) / [**JetBrains Compose Multiplatform**](https://www.jetbrains.com/compose-multiplatform/): Language and UI framework.
- [**Ktor**](https://github.com/ktorio/ktor): HTTP client and WebSocket framework for the bridge.
- [**Coil**](https://github.com/coil-kt/coil): Image loading for covers and artwork.
- [**Crowdin**](https://crowdin.com/): Community translation platform.

### 🔤 Bundled fonts:

- **Roboto** Regular and Medium (`app/src/main/composeResources/font/roboto_w400.ttf`, `roboto_w500.ttf`), from [googlefonts/roboto](https://github.com/googlefonts/roboto) v2.138: the font Android gives the Material components, used for the same components here so they look like the phone's. Roboto is © Google and licensed under the [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0).
- **Rubik** (`rubik_w*.ttf`), the phone's app font.

# 👀 Status

## 🛠️ Build & Deployment

[![Automatic Cache Builder](https://github.com/N-Zik-Group/N-Zik-Desktop-Compagnon/actions/workflows/cache-builder.yaml/badge.svg)](https://github.com/N-Zik-Group/N-Zik-Desktop-Compagnon/actions/workflows/cache-builder.yaml)

## 🔄 Automation & Maintenance

[![Automatic Dependency Submission](https://github.com/N-Zik-Group/N-Zik-Desktop-Compagnon/actions/workflows/dependency-graph/auto-submission/badge.svg)](https://github.com/N-Zik-Group/N-Zik-Desktop-Compagnon/actions/workflows/dependency-graph/auto-submission)  
[![Dependabot Updates](https://github.com/N-Zik-Group/N-Zik-Desktop-Compagnon/actions/workflows/dependabot/dependabot-updates/badge.svg)](https://github.com/N-Zik-Group/N-Zik-Desktop-Compagnon/actions/workflows/dependabot/dependabot-updates)  
[![Chores](https://github.com/N-Zik-Group/N-Zik-Desktop-Compagnon/actions/workflows/house-keeper.yaml/badge.svg)](https://github.com/N-Zik-Group/N-Zik-Desktop-Compagnon/actions/workflows/house-keeper.yaml)  
[![Close stale tickets weekly](https://github.com/N-Zik-Group/N-Zik-Desktop-Compagnon/actions/workflows/close-stale-tickets.yaml/badge.svg)](https://github.com/N-Zik-Group/N-Zik-Desktop-Compagnon/actions/workflows/close-stale-tickets.yaml)  
[![Comment or close on label](https://github.com/N-Zik-Group/N-Zik-Desktop-Compagnon/actions/workflows/comment-on-label.yaml/badge.svg)](https://github.com/N-Zik-Group/N-Zik-Desktop-Compagnon/actions/workflows/comment-on-label.yaml)  
[![Auto-assign issues](https://github.com/N-Zik-Group/N-Zik-Desktop-Compagnon/actions/workflows/auto-assign-issues.yml/badge.svg)](https://github.com/N-Zik-Group/N-Zik-Desktop-Compagnon/actions/workflows/auto-assign-issues.yml)  
[![Update Project Stats and Chart](https://github.com/N-Zik-Group/N-Zik-Desktop-Compagnon/actions/workflows/metrics.yml/badge.svg)](https://github.com/N-Zik-Group/N-Zik-Desktop-Compagnon/actions/workflows/metrics.yml)

## 👥 Contributors

[![Fetch, create, and update repo's contributors](https://github.com/N-Zik-Group/N-Zik-Desktop-Compagnon/actions/workflows/weekly-update-contributors.yaml/badge.svg)](https://github.com/N-Zik-Group/N-Zik-Desktop-Compagnon/actions/workflows/weekly-update-contributors.yaml)

# ⚠️ Disclaimer

This project is a companion for [N-Zik](https://github.com/N-Zik-Group/N-Zik).

Its contents are not affiliated with, funded, authorized, endorsed by, or in any way associated with YouTube, Google LLC, or any of its affiliates or subsidiaries.

Any trademarks, service marks, trade names, or other intellectual property rights used in this project remain the property of their respective owners.

Made with ❤️ by [NEVARLeVrai](https://github.com/NEVARLeVrai)  
Licensed under GPLv3 - see [LICENSE](LICENSE)
