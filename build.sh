#!/usr/bin/env bash
#
# build.sh — build N-Zik Desktop Compagnon locally on Linux (also works on macOS).
#
# Windows users: use gradlew.bat directly from the repo root (no script needed).
#
# Usage:
#   ./build.sh            compile + run all tests
#   ./build.sh test       run the tests only
#   ./build.sh clean      clean, then compile + run all tests
#   ./build.sh package <channel>   run the tests, then build the Linux .deb + .rpm + portable zip +
#                                  Flatpak + Arch binary package (.pkg.tar.zst) for the
#                                  channel (Linux/WSL host only).
#                                  The channel is REQUIRED: stable | beta | dev (spec `spec-updater` —
#                                  a packaging build never runs without an explicit channel)
#
# Requirements:
#   - JDK 21 on PATH, or JAVA_HOME pointing at a JDK 21
#     (the Gradle toolchain can also auto-provision it if it is missing)
#   - a Wi-Fi phone running N-Zik, if you want to run the app against it
#   - for `package`: the jpackage tooling must be able to build a .deb and a .rpm on this host
#     (see rules/BUILD.md "Linux packaging")
#   - for `package`: the Flatpak task additionally needs the host's flatpak tools
#     (`sudo apt install flatpak-builder` + one flatpak remote, once — see
#     rules/BUILD.md "Flatpak (the 4th Linux path)")
#   - for `package`: the Arch binary package task additionally needs the host's `zstd`
#     (`sudo apt install zstd` — the .pkg.tar.zst compressor; the task fails loud without it)
#
# Note: the app also runs on Linux (`./gradlew :app:run`), pairing included
# (keyring, or in-memory for the session when no keyring daemon is running).
# "Sound on the PC" uses the system libvlc — install VLC first (e.g.
# `sudo apt install vlc`); the app shows the install command when it is missing.

set -euo pipefail

# Always run from the repository root, no matter where the script is called from.
cd "$(dirname "$0")"

# --- Java check (JDK 21) ---
if [ -n "${JAVA_HOME:-}" ]; then
  java_bin="$JAVA_HOME/bin/java"
else
  java_bin="$(command -v java || true)"
fi

if [ -z "$java_bin" ] || [ ! -x "$java_bin" ]; then
  echo "Error: Java not found. Install JDK 21, or set JAVA_HOME to a JDK 21 install." >&2
  exit 1
fi

echo "Using: $("$java_bin" -version 2>&1 | head -n 1)"

case "${1:-build}" in
  build)
    ./gradlew build
    ;;
  test)
    ./gradlew :app:test
    ;;
  clean)
    ./gradlew clean build
    ;;
  package)
    # The build channel (spec `spec-updater` AD-1): a packaging build never runs without an
    # explicit channel — the Gradle tasks fail loud anyway, the script fails it earlier with
    # the exact usage.
    channel="${2:-}"
    if [[ "$channel" != "stable" && "$channel" != "beta" && "$channel" != "dev" ]]; then
      echo "Usage: ./build.sh package stable|beta|dev  (the channel names the artifacts, e.g. ...-0.0.2-beta-...)" >&2
      exit 1
    fi
    # Linux artifacts only: on a non-Linux host the .deb/.rpm tasks are disabled by the Compose
    # plugin (incompatible OS) and the Flatpak/Arch tasks skip themselves (lifecycle
    # message), so this is a Linux/WSL-host command. The artifacts are shipped only after the
    # tests pass (the packaging config is pinned by the JVM test suite).
    ./gradlew :app:test :app:packageDeb :app:packageRpm :app:packageLinuxPortable :app:packageFlatpak :app:packageArch -Pchannel="$channel"
    ;;
  *)
    echo "Usage: ./build.sh [build|test|clean|package]" >&2
    exit 1
    ;;
esac
