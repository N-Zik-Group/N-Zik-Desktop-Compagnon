# N-Zik Desktop Compagnon

A lightweight Windows companion for [N-Zik](https://github.com/N-Zik-Group/N-Zik): control your phone's library and playback from a large screen, and listen on your PC.

The phone stays the single source of truth. It runs a local server on your Wi-Fi network, and this app only talks to it: no business logic and no streaming stack live on the PC. Pair once (QR code or manual code), then browse your library, manage the queue and play tracks streamed from your phone.

> **Transitional product.** This companion bridges the gap until a standalone N-Zik desktop app exists. It will be replaced by it.

## Status

Early bootstrap. The app currently opens a window showing a "Not paired" placeholder; pairing, synced playback state and audio arrive in upcoming releases.

## Requirements

- Windows 10 or 11
- JDK 21 (used by the Gradle toolchain to build and run)
- N-Zik on an Android phone connected to the same Wi-Fi network (once pairing is available)

## Build and run

Compile and run the tests:

```bat
gradlew.bat build
```

Launch the app:

```bat
gradlew.bat :app:run
```

## License

N-Zik Desktop Compagnon is free software, released under the [GNU General Public License v3.0](LICENSE).

## Acknowledgements

This project may reuse code from [Cubic Music Desktop](https://github.com/cybruGhost/DESKTOP-CUBIC-MUSIC) by cybruGhost, under the GPL-3.0. Its author granted explicit permission, as recorded in the Cubic Music Desktop notice:

> Copyright © 2026 cybruGhost and contributors.
>
> Cubic Music Desktop is open source under the GNU GPL v3.0. This notice records the author's explicit acknowledgement that N-Zik Group / N-Zik and NEVARLeVrai may reuse, modify and redistribute Cubic Music code under GPL-3.0, while preserving the applicable copyright, license and third-party notices.

Any file derived from Cubic Music Desktop keeps its original copyright and license notices.
