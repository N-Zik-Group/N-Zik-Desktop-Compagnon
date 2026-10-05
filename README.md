<div align="center">
  <img alt="project's banner" src="./assets/design/ic_banner2.png" width="1080" />

  <h3>🌐 <a href="https://n-zik.vercel.app/">Official Website</a></h3>

  <p>
    <b>N-Zik Desktop Compagnon</b> is the Windows companion for
    <a href="https://github.com/N-Zik-Group/N-Zik">N-Zik</a>:
    control your phone's library and playback from a large screen, and listen on your PC.
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
</div>

  <br>

<div align="center">
  <img src="assets/stats/download-card.svg" alt="N-Zik Compagnon Stats" />
  <img src="assets/stats/chart.svg" alt="Download Growth" />

  <br><br>

  [![Launched on DevGlobe](https://devglobe.app/badges/launched-on-devglobe-dark.svg)](https://devglobe.app/projects/n-zik?utm_source=badge&utm_medium=embed)
  
  [![Localization Progress](https://badges.crowdin.net/N-Zik/localized.svg)](https://crowdin.com/project/N-Zik) [![License: GPL v3](https://img.shields.io/github/license/N-Zik-Group/n-zik-desktop-compagnon?color=blue)](https://www.gnu.org/licenses/gpl-3.0)
  [![CodeFactor](https://www.codefactor.io/repository/github/n-zik-group/n-zik-desktop-compagnon/badge)](https://www.codefactor.io/repository/github/n-zik-group/n-zik-desktop-compagnon)


  [![Crowdin](https://badges.crowdin.net/N-Zik/localized.svg)](https://crowdin.com/project/N-Zik)

  <br><br>

  [![GitHub](https://github.com/N-Zik-Group/N-Zik/blob/main/assets/get-it-on/GitHub.png?raw=true)](https://github.com/N-Zik-Group/n-zik-desktop-compagnon/releases/latest)
  [![BetaVersions](https://github.com/N-Zik-Group/N-Zik/blob/main/assets/get-it-on/GitHubBeta.png?raw=true)](https://github.com/N-Zik-Group/n-zik-desktop-compagnon/releases?q=&type=prerelease)
</div>

# 🎧 Features

- 🔗 **QR or Manual Pairing** – Pair once with the QR code shown by the app (or the phone's IP, port and code by hand); the pairing is remembered across restarts, and the device token lives in the Windows Credential Manager.
- 📚 **The Phone's Library** – Songs (search, filters, sorts), Artists, Albums and Playlists, switched from the floating bar at the bottom, with live refresh of the phone's lists.
- ⏯️ **Unified Player** – A mini player above the navigation bar opens the full player: rotating cover animation, colours that follow the current cover, seek, speed, repeat and shuffle.
- 📋 **Queue** – Opens from the player or the mini player: jump to a track, move it, remove it, clear the queue.
- 🎮 **Remote Control** – Everything plays on the phone: play / pause / previous / next, seek, speed, repeat, shuffle, and per-track menus (a right click is the phone's long press).
- 🖥️ **Sound on the PC** – Stream the audio to your PC instead of the phone: pick the output device (this PC or the phone) and the PC follows the phone's playback; when this PC's session ends, the phone takes the sound back.
- 💾 **Local Audio Cache** – Tracks already played in full are kept in a local cache and replayed without asking the phone again, with a configurable size (2 GB by default).
- 🔊 **Embedded VLC** – Audio is played on an embedded copy of VLC: no VLC installation is needed on your PC.
- 🎨 **The Phone's Look** – The UI is ported from N-Zik: same screens, same theme, dynamic palette from the current cover.
- 🔁 **Auto-Reconnect** – The app reconnects by itself after a network loss; when the phone's server is stopped, or when you disconnect this PC from the phone, use "Reconnect".

# 🔒 Pairing, Windows Firewall and manual mode

For QR pairing, the phone connects back to a temporary listener opened by this app on your local network. Windows may ask whether to allow N-Zik Desktop Compagnon (Java) through the firewall: allow it on **private** networks. If your Wi-Fi is set to **Public**, Windows blocks the phone; switch the network to Private, or use manual pairing.

If the phone has not reached the PC within 60 seconds, or if no local network address is found, the app switches to manual pairing: enter the IP address, port and 6-character code shown on the phone's "Manage server" screen. The QR code stays available.

The device token is stored in the Windows Credential Manager (generic credential "N-Zik Desktop Compagnon"); the other pairing details live in `%APPDATA%\N-Zik Desktop Compagnon\pairing.json`. "Forget this phone", or revoking this PC from the phone, removes both.

# 🔊 Sound on the PC

The mini player's "audio output" button opens the audio devices: choose **This PC** to hear the music on your computer, or your phone to hear it there. Both keep controlling the same playback; only one of them sounds. While the PC plays, the phone keeps playing silently, and the PC follows it (pause, seek, speed, next track). If this PC's session ends (app closed, disconnected from the phone, Wi-Fi lost), the phone pauses and takes the sound back. The button needs N-Zik with contract 1.2; with an older N-Zik it is hidden.

### Embedded VLC

Audio is played by [vlcj](https://github.com/caprica/vlcj) 4.12.1 on an embedded copy of [VLC](https://www.videolan.org/) 3.0.24: no VLC installation is needed. The build downloads the official VideoLAN zip (`vlc-3.0.24-win64.zip`, checked against its pinned SHA-256), keeps it in the Gradle user home so `clean` does not download it again, and extracts only the audio part (libvlc, libvlccore, about 30 plugins, `COPYING.txt`: about 10 MB) under `app/build/vlc-runtime`. No VLC binary is stored in this repository. VLC is © the VideoLAN team and contributors; libvlc is licensed under the LGPL 2.1 or later and some plugins under the GPL 2 or later (see `COPYING.txt` next to the runtime). vlcj is licensed under the GPL 3.0.

If the embedded VLC cannot be loaded, the app still works: "This PC" is disabled with a message.

# 🌐 Help Translate

Want to:

- Translate into a new language?
- Improve an existing translation?
- Fix typos or inconsistencies?

Join us on Crowdin!

[![Translated with Crowdin](https://badges.crowdin.net/badge/light/crowdin-on-dark.png)](https://crowdin.com/project/n-zik-desktop)

# 🛠️ Requirements & Building

## Requirements

- Windows 10 or 11
- JDK 21 (used by the Gradle toolchain to build and run)
- N-Zik on an Android phone connected to the same Wi-Fi network

## Build and run

Compile and run the tests:

```bat
gradlew.bat build
```

The first build downloads VLC 3.0.24 from download.videolan.org (about 83 MB, once).

Launch the app:

```bat
gradlew.bat :app:run
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

## 🔄 Automation & Maintenance

[![Update Project Stats and Chart](https://github.com/N-Zik-Group/N-Zik-Desktop-Compagnon/actions/workflows/metrics.yml/badge.svg)](https://github.com/N-Zik-Group/N-Zik-Desktop-Compagnon/actions/workflows/metrics.yml)
[![Fetch, create, and update repo's contributors](https://github.com/N-Zik-Group/N-Zik-Desktop-Compagnon/actions/workflows/weekly-update-contributors.yaml/badge.svg)](https://github.com/N-Zik-Group/N-Zik-Desktop-Compagnon/actions/workflows/weekly-update-contributors.yaml)

# ⚠️ Disclaimer

This project is a companion for [N-Zik](https://github.com/N-Zik-Group/N-Zik).

Its contents are not affiliated with, funded, authorized, endorsed by, or in any way associated with YouTube, Google LLC, or any of its affiliates or subsidiaries.

Any trademarks, service marks, trade names, or other intellectual property rights used in this project remain the property of their respective owners.

Made with ❤️ by [NEVARLeVrai](https://github.com/NEVARLeVrai)
Licensed under GPLv3 - see [LICENSE](LICENSE)
