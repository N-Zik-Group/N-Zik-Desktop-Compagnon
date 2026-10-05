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
#
# Requirements:
#   - JDK 21 on PATH, or JAVA_HOME pointing at a JDK 21
#     (the Gradle toolchain can also auto-provision it if it is missing)
#   - a Wi-Fi phone running N-Zik, if you want to run the app against it
#
# Note: the app also runs on Linux (`./gradlew :app:run`), pairing included
# (keyring, or in-memory for the session when no keyring daemon is running).
# The embedded VLC Linux runtime is not ported yet, so "Sound on the PC" is
# unavailable there.

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
  *)
    echo "Usage: ./build.sh [build|test|clean]" >&2
    exit 1
    ;;
esac
