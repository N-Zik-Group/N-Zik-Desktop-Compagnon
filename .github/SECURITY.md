# Security Policy

## Reporting a vulnerability

If you believe you have found a security vulnerability in **N-Zik Desktop
Compagnon**, please report it responsibly:

1. Open an issue with the **[SECURITY] vulnerability template**
   ([New issue → Security Vulnerability](https://github.com/N-Zik-Group/N-Zik-Desktop-Compagnon/issues/new?template=security_report.yaml)), or
2. Contact the developers directly on the [community Discord](https://discord.gg/bneHC7QRje).

**Never** post real secrets (tokens, passwords, cookies, API keys) in a public
issue — strip any personal data from your report before submitting it.

The report is reviewed by the maintainer, who will follow up on it. If the
vulnerability lives on the phone side, it is forwarded to the
[N-Zik phone repo](https://github.com/N-Zik-Group/N-Zik) — the phone stays
the source of truth, and its own security policy applies there.

## How the companion handles sensitive data

- **Device token (pairing):** stored only in the Windows Credential Manager
  (generic credential "N-Zik Desktop Compagnon"). It is never written to
  `pairing.json`, logs, crash reports, or anywhere else on disk.
- **`pairing.json` / `settings.json`** (in `%APPDATA%\N-Zik Desktop Compagnon\`):
  non-secret pairing details and PC-local settings only.
- **Local audio cache** (`%APPDATA%\N-Zik Desktop Compagnon\cache\audio\`):
  streamed audio kept locally for offline re-play; size is user-configurable.
- **Network:** the companion talks only to the phone's local server over your
  Wi-Fi network. It opens no accounts and stores no cloud credentials.

## Supported versions

Only the **latest stable release** is supported for security updates.
Beta (pre-release) builds may receive fixes at the maintainer's discretion.
Older releases are not supported.

## Out of scope

- The N-Zik phone app itself → report on the
  [N-Zik phone repo](https://github.com/N-Zik-Group/N-Zik).
- Upstream components used by the companion (VLC/libvlc, vlcj, Ktor, JNA,
  Kotlin/Compose) → report to the respective upstream project.
- YouTube, Google, or any other third-party service behavior — the companion
  streams audio from the phone and does not talk to those services itself.
