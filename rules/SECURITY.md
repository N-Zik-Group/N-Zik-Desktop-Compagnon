# Security Rules — N-Zik Desktop Compagnon

**Version:** 1.0.0 | **Last updated:** 2026-10-05

## The Device Token (primary secret of this app)

- The paired device token is the ONLY secret this app handles — it authenticates every bridge call (REST Bearer + WS upgrade header)
- **Storage: Windows Credential Manager ONLY** (generic credential "N-Zik Desktop Compagnon", via `WindowsCredentialSecretStore`'s JNA mapping of `CredWriteW`/`CredReadW`/`CredDeleteW`)
- `pairing.json` (in `%APPDATA%\N-Zik Desktop Compagnon\`) holds the NON-SECRET fields only (`serverIps`, `serverPort`, `serverName`, `deviceId`, `deviceName`) — the token must NEVER appear there; `CredentialStore` is built so a missing token or a corrupt file reads as "not paired"
- **NEVER log the token** — not at any level, not masked, not in an exception message
- **NEVER write the token** into `Done.txt`, `TODO.txt`, README, comments, tests, or any file in this repo
- "Forget this phone" and every revocation path (`RevocationPolicy`) must remove BOTH the Credential Manager entry and `pairing.json`
- If a token (or a Credential Manager entry) appears in a diff, commit, log output, or a test artifact → HALT immediately, treat as a leaked secret (same escalation as "Secrets found in code" below)

## Secrets & API Keys

- NEVER commit secrets, API keys, or tokens
- This repo has **no secrets mechanism at all** (no `local.properties`, no `BuildConfig`, no API keys) — the bridge authenticates with the paired token only. If a task seems to require introducing a secret or a new API key → HALT and ask the user first; inventing a secrets mechanism is a design decision, not an implementation detail
- NEVER log sensitive data (tokens, user data, pairing details beyond the non-secret fields)

## Input Validation

- Validate all user input before processing
- **Pairing inputs:** the manual form normalizes the code (upper case, spaces and dashes removed, 6 characters); the port is prevalidated against the phone's default (`42420`) and range; the computer name is 1–64 chars — keep these rules when touching `PairingController`/the manual form
- **LAN candidates:** `CandidateAddresses` serves RFC 1918 addresses only, virtual adapters excluded, default-gateway interface first — never serve a public IP into a QR payload
- Reject empty/whitespace-only responses for required fields; trim and normalize text inputs
- Validate URLs before opening (this app opens no browser by default — the pairing listener binds an ephemeral local port and serves only `POST /nzik-pair/v1/offer`)

## Question Tool Input Validation

- All user input arrives via question tool responses
- Validate URLs before opening (never auto-open)
- Validate file paths (prevent path traversal)
- Reject empty/whitespace-only responses for required fields
- Trim and normalize text inputs

## Sensitive Data Storage

- Use the Windows Credential Manager for the device token (the mechanism above) — no plain-text credential storage anywhere
- `%APPDATA%\N-Zik Desktop Compagnon\` holds `pairing.json` (non-secret), `settings.json` (PC-local settings) and the audio cache — nothing sensitive
- Clear sensitive data when the user forgets the phone / the phone revokes this PC (Credential Manager entry + `pairing.json`, both)
- All bridge network communication goes to the phone's **local** server — see the network exception below

> **Deliberate exception — cleartext on the LAN:** the phone's local server speaks plain HTTP/WebSocket on the Wi-Fi network (contract design; the token authenticates every call). Do NOT "fix" it to HTTPS/TLS, do NOT add certificate pinning to the bridge — it is intentional. The pairing listener likewise binds a local ephemeral port over plain HTTP.

## Third-Party Runtime & Licenses

- **VLC:** the embedded runtime is downloaded at build time against its pinned SHA-256 (`app/build.gradle.kts`) and extracted under `build/` (gitignored) — **no VLC binary is ever committed**. Do NOT change `vlcVersion`/`vlcZipUrl`/`vlcZipSha256`/`vlcRuntimeFiles` without explicit user approval (AGENTS.md "Ask First")
- **This repo is GPL-3.0** (LICENSE) — the project itself is copyleft:
  1. Any code added must be GPL-3.0-compatible; open-source (MIT, Apache) = acceptable; copyleft (GPL, AGPL, LGPL) = acceptable, verify the terms; closed-source/proprietary = NEVER acceptable
  2. **Cubic Music Desktop reuse** is documented in the README (the author granted explicit permission, GPL-3.0) — any file derived from it keeps its original copyright and license notices; do not strip them
  3. **Fonts:** Roboto (Apache License 2.0) and Rubik (SIL Open Font License) live in `composeResources/font/` — keep the README's font notices; the Rubik OFL attribution is still pending (tracked in `assets/notes/TODO.txt`)
  4. **VLC:** libvlc is LGPL 2.1+, some plugins GPL 2+, vlcj GPL 3.0 — the runtime ships with its `COPYING.txt` (kept under `build/vlc-runtime/.../vlc/COPYING.txt`); do not remove it from the extraction list
  5. Always cite source and license in a comment when porting or pasting code

## HALT IMMEDIATELY IF:

| Scenario                                | Action                                                                      |
| --------------------------------------- | --------------------------------------------------------------------------- |
| Device token found outside Credential Manager (diff, log, file, test) | HALT, remove it, rotate the pairing (re-pair from the phone), report to user |
| Secrets found in code                   | HALT immediately, remove secrets, report to user                            |
| License violation detected              | HALT, remove code, report to user with violation details                    |
| Hardcoded credentials                   | HALT, remove credentials, route them through the Credential Manager mechanism |
| Insecure change to the bridge (token in a URL/query, token logged) | HALT, revert, use the Bearer header convention, report                     |
| VLC binary or runtime change in a diff  | HALT, verify it is not being committed; if the pinned hash changed, ask the user before anything |
| User data leak                          | HALT, identify leak source, report to user (fix only after user confirmation) |
