# N-Zik Desktop Compagnon

A lightweight Windows companion for [N-Zik](https://github.com/N-Zik-Group/N-Zik): control your phone's library and playback from a large screen, and listen on your PC.

The phone stays the single source of truth. It runs a local server on your Wi-Fi network, and this app only talks to it: no business logic and no streaming stack live on the PC. Pair once (QR code or manual code), then browse your library, manage the queue and play tracks streamed from your phone.

> **Transitional product.** This companion bridges the gap until a standalone N-Zik desktop app exists. It will be replaced by it.

## Status

Pairing is available: scan the QR code shown by the app from N-Zik on your phone (or type the phone's IP address, port and code by hand), and the pairing is remembered across restarts.

Once paired, the main window shows your phone's library, as in the app: Songs (search, filters, sorts), Artists, Albums and Playlists, switched from the floating bar at the bottom. Open an album, an artist or a playlist to see its tracks; click a track to play the list from it, or use Play, Shuffle, Play next and Add to queue; a right click on a track or a collection opens its menu (the phone's long press). Everything plays on the phone. A mini player above the navigation bar (previous, play/pause, next) opens the full player (seek, speed, repeat, shuffle), whose colours follow the current cover; the queue opens from the player or with a right click on the mini player (jump to a track, move it, remove it, clear the queue). The app reconnects by itself after a network loss; when the phone's server is stopped, or when you disconnect this PC from the phone, use "Reconnect".

The device token is stored in the Windows Credential Manager (generic credential "N-Zik Desktop Compagnon"); the other pairing details live in `%APPDATA%\N-Zik Desktop Compagnon\pairing.json`. "Forget this phone", or revoking this PC from the phone, removes both.

## Sound on the PC

The mini player's "audio output" button opens the audio devices: choose **This PC** to hear the music on your computer, or your phone to hear it there. Both keep controlling the same playback; only one of them sounds. While the PC plays, the phone keeps playing silently, and the PC follows it (pause, seek, speed, next track). If this PC's session ends (app closed, disconnected from the phone, Wi-Fi lost), the phone pauses and takes the sound back. The button needs N-Zik with contract 1.2; with an older N-Zik it is hidden.

Tracks already played in full are kept in a local cache (`%APPDATA%\N-Zik Desktop Compagnon\cache\audio\`) and replayed without asking the phone again. Its size is set in the app's settings (header icon), with the phone's "Song cache max size" values (2 GB by default; "Turn off" downloads nothing). The playback volume (local to this PC) and the audio quality asked of the phone are in the same places as on the phone.

### Embedded VLC

Audio is played by [vlcj](https://github.com/caprica/vlcj) 4.12.1 on an embedded copy of [VLC](https://www.videolan.org/) 3.0.24: no VLC installation is needed. The build downloads the official VideoLAN zip (`vlc-3.0.24-win64.zip`, checked against its pinned SHA-256), keeps it in the Gradle user home so `clean` does not download it again, and extracts only the audio part (libvlc, libvlccore, about 30 plugins, `COPYING.txt`: about 10 MB) under `app/build/vlc-runtime`. No VLC binary is stored in this repository. VLC is © the VideoLAN team and contributors; libvlc is licensed under the LGPL 2.1 or later and some plugins under the GPL 2 or later (see `COPYING.txt` next to the runtime). vlcj is licensed under the GPL 3.0.

If the embedded VLC cannot be loaded, the app still works: "This PC" is disabled with a message.

## Pairing, Windows Firewall and manual mode

For QR pairing, the phone connects back to a temporary listener opened by this app on your local network. Windows may ask whether to allow N-Zik Desktop Compagnon (Java) through the firewall: allow it on **private** networks. If your Wi-Fi is set to **Public**, Windows blocks the phone; switch the network to Private, or use manual pairing.

If the phone has not reached the PC within 60 seconds, or if no local network address is found, the app switches to manual pairing: enter the IP address, port and 6-character code shown on the phone's "Manage server" screen. The QR code stays available.

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

## License

N-Zik Desktop Compagnon is free software, released under the [GNU General Public License v3.0](LICENSE).

### Bundled fonts

- **Roboto** Regular and Medium (`app/src/main/composeResources/font/roboto_w400.ttf`, `roboto_w500.ttf`), from [googlefonts/roboto](https://github.com/googlefonts/roboto) v2.138: the font Android gives the Material components, used for the same components here so they look like the phone's. Roboto is © Google and licensed under the [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0).
- **Rubik** (`rubik_w*.ttf`), the phone's app font.

## Acknowledgements

This project may reuse code from [Cubic Music Desktop](https://github.com/cybruGhost/DESKTOP-CUBIC-MUSIC) by cybruGhost, under the GPL-3.0. Its author granted explicit permission, as recorded in the Cubic Music Desktop notice:

> Copyright © 2026 cybruGhost and contributors.
>
> Cubic Music Desktop is open source under the GNU GPL v3.0. This notice records the author's explicit acknowledgement that N-Zik Group / N-Zik and NEVARLeVrai may reuse, modify and redistribute Cubic Music code under GPL-3.0, while preserving the applicable copyright, license and third-party notices.

Any file derived from Cubic Music Desktop keeps its original copyright and license notices.
