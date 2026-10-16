# Linux & Flatpak Development Rules — N-Zik Desktop Compagnon

**Informational.** The Linux/Flatpak development logic gained from the tray + media-surface
implementation — what works in the sandbox, why, and how to verify it. Every
entry below is live-verified on the dev machine (CachyOS, GNOME 51, dark session, the
`com.nzik.desktop.compagnon.dev` bundle) or in the unit tests named. When a rule and a
"cleverer" approach conflict, the rule wins — it encodes a live bug.

## The sandbox session bus (xdg-dbusproxy)

The Flatpak sees the session bus through a proxy (`unix:path=/run/flatpak/bus` **inside the
sandbox** — that path does not exist on the host; never try to open it from the host). The
proxy forwards only what the finish-args allow:

- **`--talk-name=`** — the app may *call* that name. Without `--talk-name=org.kde.StatusNotifierWatcher`
  the proxy filters the watcher, the SNI probe reads "no host" and the tray stays dead with a
  GREEN build. Same for the keyring
  (`--talk-name=org.freedesktop.secrets`).
- **`--own-name=`** — the app may *register* that name. Needed for `org.mpris.MediaPlayer2.nzik_compagnon`
  (MPRIS) and the SNI item name. A duplicate launch's `Raise` call to an `--own-name` name passes
  the proxy fine (sending needs no extra grant — live-verified double launch).
- **Static names only.** A dynamic pid-based name (`org.kde.StatusNotifierItem-<pid>-1`) **cannot**
  be granted to a sandbox — the finish-args are frozen at build time. The SNI item therefore owns
  the STATIC `org.kde.StatusNotifierItem.nzik_desktop_compagnon` (`SNI_BUS_NAME`,
  `StatusNotifierTray.kt`). Outside the sandbox (a `gradlew run` on a native Linux host) the same
  static name is correct — SNI hosts deduplicate by bus name anyway.
- **No raw bus socket** in this app: only the proxied bus, only granted names. Anything not
  granted fails with a bus error — catch it, log it, degrade (the tray pattern: every D-Bus
  error is a log line, never a crash).
- **Do NOT add `--talk-name=ca.desrt.dconf`** (it was there and removed): the host dconf daemon
  is useless inside the sandbox — the app reads the host dconf user DB as a FILE instead (below).

## jlink + D-Bus: the SASL module

dbus-java's SASL layer resolves the session-bus UID through `com.sun.security.auth.module.UnixSystem`
(module `jdk.security.auth`) at RUNTIME. The Compose plugin computes the jlinked JRE's module set
from the app classpath — it **never finds** `jdk.security.auth` (no app code references it). Without
the module, EVERY session-bus connection inside every packaged Linux artifact (Flatpak, .deb, .rpm,
AUR, portable tarball) fails with `NoClassDefFoundError`, and the tray + MPRIS silently stay off — with
a green build. `:ComposeN-Zik:run` is immune (host JDK, full module set) — the bug only shows in packaged
artifacts.

- The fix: `createRuntimeImage { modules.add("jdk.security.auth") }` in `ComposeN-Zik/build.gradle.kts`
  (in `project.afterEvaluate` — the Compose plugin creates the task in its OWN afterEvaluate, which
  runs first).
- The pin: `LinuxPackagePinTest` reads `linux.jlinkModules` (the build exposes the EFFECTIVE module
  set via `systemProperty` — same pattern as `flatpak.*`; pin the effective value, never a copy of
  the string). If a plugin upgrade drops the line, the suite breaks, not the build.

## Host config vs sandbox config (dconf)

Inside the sandbox:

| Env var | Value |
| --- | --- |
| `HOME` | the REAL home (`/home/<user>`) — unusual, do not assume the per-app dir |
| `XDG_CONFIG_HOME` | the PER-APP dir (`~/.var/app/<id>/config`) — carries NO dconf |

Consequences, live-verified:

- **Every in-sandbox probe answers the SCHEMA DEFAULTS, not the host's value** — `gsettings`,
  the dconf daemon's D-Bus API (a sandboxed talk-name would reach a dconf daemon that serves the
  app's own per-app DB), the DConf/Settings portals. The host's session settings are only
  reachable through the host's FILES.
- **The tray/menu icon tint** therefore reads the host's dconf user DB **directly as a file**
  (`HostDconf.kt` — a dconf ≥ 0.44 GVDB parser: 24-byte header, bloom-filtered root hash table,
  key-segment chains, value blobs). Candidate order: `$XDG_CONFIG_HOME/dconf/user` then
  `$HOME/.config/dconf/user` — in the sandbox the first does not exist and the second works
  (99 KB, `GVariant` magic); on a plain run the first does. The order is tested
  (`HostDconfTest`), including the sandbox case.
- `prefer-light` → dark icons `#241F1F`; anything else (dark, default, missing, failed read) →
  white. Resolved ONCE before the UI exists (`primeMenuTint()` from `Main.main()` — the startup
  thread may do the one small synchronous read; the composition must never touch the disk).
- **Why not a themed symbolic `IconName`?** It would adapt on its own — but the host-side shell
  cannot see a flatpak's `/app`, so a custom icon file never resolves from the sandbox. The icons
  must ship their pixels: the SNI `IconPixmap` (raw ARGB32) and the DBusMenu `icon-data` (PNG) are
  tinted to the menu/panel's text color.

## Tray + menu from the sandbox

- **SNI**: register the item on the STATIC `SNI_BUS_NAME` (above), `IconPixmap` as raw ARGB32
  big-endian bytes (NOT PNG — live-verified), `Menu` path to the DBusMenu object, the
  `StatusNotifierWatcher` registration in a retry loop (the host may own its name after the
  app's probe — the announceMenu workaround).
- **DBusMenu row ids must be STABLE across updates** (`TRAY_MENU_ID_*`): the host recycles its
  menu items by id — new ids on every update force a full menu rebuild in the shell. On a content
  change, emit `com.canonical.dbusmenu.LayoutUpdated` — and do it OFF the EDT (the tray contract:
  all D-Bus off the EDT; the LaunchedEffect hops to `NzikDispatchers.DATA` via `withContext`).
- **`GetLayout(parentId, recursionDepth, …)`**: every real host (appindicator GNOME, KDE) asks
  `(0, -1)` and the menu is flat — ignoring `parentId`/recursionDepth is deliberate, tested
  decision (review-verified).
- **Icon-data PNGs**: render each distinct row icon ONCE, in memory, tinted, with the SAME
  margin as the tray icon (render at `px * 0.8` centered on the `px` canvas — full-bleed glyphs
  look oversized in shell menus; user request "like Discord", live-verified: 6 icons, glyphs
  32–38 px on the 48×48 canvas). Only the SNI backend renders them — never on the AWT backend
  (wasted startup work on Windows).
- **Icon choice**: Linux trays (SNI + X11 AWT fallback) use the monochrome `ic_launcher_monochrome`
  glyph tinted from the host dconf; the **Windows AWT taskbar keeps the full-color untinted
  `app_icon`** (no dconf there — a white monochrome glyph is invisible on a light taskbar). The full-color `ic_launcher` tile stays with the desktop launchers.
- **Single instance (Linux)**: `SingleInstance.isDuplicate()` — `NameHasOwner(MPRIS_BUS_NAME)` on
  the session bus; a duplicate re-shows the first instance through its MPRIS `Raise` and exits.
  The WHOLE exchange is in `runCatching` (a bus error reads "the only instance" — never kill a
  legitimate launch). No guard exists on Windows (pre-existing, deferred — a named mutex would
  be a feature addition).

## The data dir

`~/.config/<storage name>/` (XDG — `$XDG_CONFIG_HOME` honoured, blank-safe) on Linux,
`%APPDATA%\<storage name>\` on Windows. The storage name is per-channel (`AppInfo.dataName`): the
base name
`N-Zik Desktop Compagnon` is SHARED by stable AND beta (the beta tests against the stable user's
real data), dev / debug / git carry `… DEV` / `… DEBUG` / `… GIT`; a beta built from the legacy
isolated `… BETA` dir merges its missing files back into the base once (`mergeLegacyChannelDirInto`
— the base wins, the `… BETA` dir survives). A SELF-CONTAINED PORTABLE (the `portable.txt` marker
at the app root, content exactly `portable`, written as an archive entry ONLY by the two portable
tasks) instead stores its data in `<portable root>/data/` (the base dir copied in once) — the
device token STILL lives in the OS secret store, never in the portable dir. The legacy bare-home
dir is migrated ONCE on first launch
(`CredentialStore.migrateLegacyAppDataDir`): whole-dir move when the target is absent (atomic →
plain → **copy** for a non-empty dir on another filesystem — the cross-mount rename fails with
`DirectoryNotEmptyException`, even plain `Files.move`), file-by-file merge when both exist,
symlinks recreated as links (NEVER followed — a dangling target aborts the walk; note the host
JDK has NO `StandardCopyOption.COPY_LINKS` — use `createSymbolicLink` + `readSymbolicLink`),
every failure only logs (no-crash). The sandbox contract: `--filesystem=xdg-config` +
`--filesystem=~/N-Zik Desktop Compagnon` (the one-time migration source — **remove the second
permission in a future release, with `FlatpakPinTest` in the same change**; see TODO.txt).

## Dev loop (the .dev bundle, in place)

```bash
./gradlew build                        # green first (all tests)
./gradlew :ComposeN-Zik:packageFlatpak -Pchannel=dev
# bundle: ComposeN-Zik/build/compose/binaries/n-zik-desktop-compagnon-0.0.1-dev-<YYYYMMDD>-x86_64.flatpak
pkexec flatpak install --system -y <ABSOLUTE bundle path>   # in-place update of the .dev
pkill -f "N-Zik[ ]Desktop[ ]Compagnon[ ]DEV"                # restart (bracket regex — see pitfalls)
nohup flatpak run com.nzik.desktop.compagnon.dev > /tmp/opencode/launch.log 2>&1 &
```

`pkexec flatpak` needs the ABSOLUTE bundle path. The .dev id (with the `-dev` suffix) is the
development identity — upgrades are in place, the pairing survives (same id, monotone version).

## Build host (WSL) — the Linux packaging tasks

All Linux packaging tasks are host-gated: on a non-Linux host they skip themselves with a
lifecycle message (`packageArch`, `packageLinuxPortable` — "skipped on this non-Linux host —
build it on a Linux/WSL host"; `.deb`/`.rpm` are disabled by the Compose plugin). A Windows-side
run produces NO Linux artifact at all — only the skip messages. The packaging build runs INSIDE
WSL (`./build.sh package <channel>`), never from the Windows side against the mount.

- **`zstd` is a host prerequisite of `:ComposeN-Zik:packageArch`** — the `tar -I zstd` compressor, a
  system apt tool, not a pinned artifact. A stock WSL Ubuntu does NOT have it. The task fails
  loud at its very start: `zstd not found on the PATH (or its --version failed) — install it
  with: sudo apt install zstd` (live-verified with a stripped `PATH` — the failure is
  fast, before any staging).
- **The false symptom — "the Arch build produced a zip instead of a `.pkg.tar.zst`".** That is
  the missing-`zstd` case: the sibling packaging tasks (`packageLinuxPortable`, `packageDeb`, …)
  run in the same Gradle invocation and can still complete, so `binaries/` ends up holding
  `…-linux-portable.tar.gz` WITHOUT the `.pkg.tar.zst`. The tarball is a DIFFERENT artifact (the
  portable app-image — extract + run `bin/`), not "the Arch package gone wrong". The tarball and
  the `.pkg.tar.zst` are both normal outputs of one packaging run, and both sit side by side on
  the GitHub release page — one is never the other one broken. First check for ANY "missing
  `.pkg.tar.zst`" report: `zstd --version` on the build host.
- **Staging stays on the local WSL disk.** The Arch/Flatpak tasks stage under the Gradle user
  home because the repo often lives on the 9P (Windows) mount, where thousands of small file ops
  are slow and the long-lived daemon can serve stale listings — the `packageArch` staging comment
  records the rationale, including a real lost-artifact incident.  `GRADLE_USER_HOME` at a
  Windows drive.
- **`binaries/` accumulates** across channels and dates (every artifact name carries the date for
  dev). The newest `.pkg.tar.zst` is the current Arch artifact — an older `…-linux-portable.tar.gz`
  next to it is not evidence of a regression.

## Version metadata per artifact (where the version lives)

Single source: the catalog's `nzikVersionName` (the base, e.g. `0.0.1`) + `nzikVersionCode`;
the channel suffix per channel (stable `""`, beta `-beta`, dev `-dev-YYYYMMDD`, git `-git`);
the `AppVersion` object generated at build time (never committed — Settings/About + the header
channel badge read it). Two registers by design (AD-2/AD-8):

1. **The channel-aware full version** (`<base> + channel suffix`, e.g. `0.0.1-dev-20261013`)
   lives in: the artifact FILE NAMES, the in-app `AppVersion`, the GitHub tags, the Windows
   installer metadata (NSIS `ProductVersion` + the registry `DisplayVersion` — visible in
   Windows' "installed apps"), and the Arch `pkgver` — dashes sanitized to dots
   (`0.0.1.dev.<date>-1`): libalpm accepts alphanumerics + dots and EXACTLY ONE dash in a version
   (the release separator; issue #29). The artifact FILE NAME keeps its dashes.
2. **The numeric base version** (`0.0.1`) is what jpackage records in the `.deb` control, the
   `.rpm` headers, the Flatpak manifest `version`, and the app-image PE. The channel rides in
   the PACKAGE/APP IDENTITY instead: the shared package name
   (`n-zik-desktop-compagnon` — every channel), the Flatpak app-id
   (`com.nzik.desktop.compagnon.dev` for dev), the
   Windows product name ("… DEV"). On Linux the channels replace each other in place (AD-8; Windows and Flatpak stay separate installations) — and
   the updater's InstallMode/provenance logic keys on that identity, not on the version field.

Consequences to know:

- `dpkg -l` / `rpm -q` / `flatpak info` show `0.0.1` for EVERY channel— the package NAME is shared, so the channel rides in the version field of the
  file name only (plus the Flatpak app-id and the Windows product name).
- The portables (Windows `.zip`, Linux `.tar.gz`) carry NO package metadata at all (they are not
  packages — only the in-app identity and the file name carry the version).
- The AUR entries carry their OWN `pkgver`/`pkgrel` (release-tarball: pinned per release in the
  PKGBUILD; `-git`: computed from the tracked branch) — independent of the release artifacts.
- Every line above is pinned by the suite: `LinuxPackagePinTest` (deb/rpm), `ArchPkgPinTest`
  (PKGINFO + the libalpm one-dash rule), `FlatpakPinTest` (manifest placeholders → effective
  app-id/version/launcher), `ArtifactNamesChannelTest` (file names),
  `InstallModeDetectionTest` (updater provenance).
- There is NO version-bump machinery in the repo (AGENTS.md forbids inventing one) — a release
  bump is a manual catalog edit + tag + the per-format chore (AUR `pkgver`/`sha256sums`,
  metainfo `releases` entry when the app ships on Flathub).

## Live-verification toolbox

- **In-sandbox probe**: `flatpak run --command=sh com.nzik.desktop.compagnon.dev -c '…'` — check
  env (`HOME`, `XDG_CONFIG_HOME`), the host dconf file (`ls -la $HOME/.config/dconf/user`, the
  `GVariant` magic, ~100 KB), any file the app should see.
- **Host-side D-Bus**: the SNI/MPRIS names are visible on the HOST session bus (the proxy
  exposes them). `dbus-send --session --print-reply --dest=org.kde.StatusNotifierItem.<id>
  /com/canonical/menu com.canonical.dbusmenu.GetLayout int32:0 int32:-1 array:string:` dumps the
  menu incl. the `icon-data` byte arrays (parse them as hex — `byte 0x89, 0x50, …`); decode a PNG
  and check the glyph's bounding box + transparent corners to verify the icon margin
  (`/tmp/opencode/check_menu_icon_padding.py`).
- **Name ownership**: `gdbus call --session --dest org.freedesktop.DBus --object-path
  /org/freedesktop/DBus --method org.freedesktop.DBus.NameHasOwner <name>` (note: gdbus treats a
  `-1` argument as an option — `dbus-send` above is the safe route for negative ints).
- **Double launch (single instance)**: launch a second instance while the first is alive; expect
  `INFOS: Single instance: another instance owns … — this launch exits` in the duplicate's log,
  NO "could not raise" warning, exit 0, and the first instance still owning both names.
  (An inconclusive first test usually means the "first" instance died on its own — check `pgrep`
  BEFORE launching the duplicate.)
- **Logs**: the app logs to the launching terminal — `nohup … > file 2>&1 &` then grep
  `SNI:` / `MPRIS:` / `Tray:` / `Single instance:` / `HostDconf:`. A missing "no dconf user DB"
  line = the tint file was found; its presence = the tint fell back to white.
- **Build gates**: `./gradlew build` (Linux host) / `gradlew.bat build` (Windows host) — the
  pin tests (FlatpakPinTest, LinuxPackagePinTest, ArchPkgPinTest) read EFFECTIVE build values
  exposed as `systemProperty`s; a build change that drifts the contract breaks the suite.

## Pitfalls (learned the hard way)

- **`pkill -f "flatpak run com.nzik"` matches ITSELF** — use the bracket regex
  `N-Zik[ ]Desktop[ ]Compagnon[ ]DEV` or explicit PIDs.
- **`timeout N flatpak run …` does not reliably kill the app** — an orphan survived a timeout
  kill. Kill by PID.
- **Duplicate JVMs** print `Cannot use file /tmp/hsperfdata_<user>/<pid>` and harmless
  `pure virtual method called` / `ProvideXdgActivationToken` / canberra-gtk-module lines at
  startup — not a failure.
- **The test source set does NOT see the generated `Res.drawable.*` accessors** — test code that
  needs a drawable reads the file, it does not reference the accessor.
- **`BridgeSessionTest` is known flaky under load** (real-time timeout vs a real loopback WebSocket — worse on the 9P mount; open, see `TODO.txt`) — re-run once before reporting. The old `PairingListenerTest` flake was FIXED 2026-10-07.
- **Compose 1.13.0-alpha01**: no `@Composable` inside `runCatching`/`try`-`catch`;
  `Painter.toAwtImage` returns a `PainterImage` that is NOT a `RenderedImage` — encode through a
  `BufferedImage` canvas (the tray's `padTrayImage` pattern) before `ImageIO`.
- **This host JDK (21.0.12) REMOVED `StandardCopyOption.COPY_LINKS`** (and `LinkOption.FOLLOW_LINKS`)
  — copy a symlink with `Files.createSymbolicLink(dest, Files.readSymbolicLink(src))`.
- **Kotlin gotchas already hit**: `ByteArray.take(n) == ByteArray` is always false (compare
  contents, not identity); unsigned bucket math is `(hash.toUInt() % nBuckets.toUInt()).toInt()`;
  `const val` only top-level/in objects; `ByteArray` has no `orEmpty()`.
- **Hex-encode bytes UNSIGNED** — `"%02x".format(byte)` sign-extends bytes ≥ 0x80 into 8–16
  digits; use `"%02x".format(it.toInt() and 0xFF)` (the MPRIS track id + artwork file names).
- **A Flatpak bundle is an ostree static delta, not a tar** — inspect by installing, not
  `tar tf` (rules/BUILD.md "Flatpak (the 4th Linux path)" — the "flatpak-builder 1.4.x notes" bullet).
