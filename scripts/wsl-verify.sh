#!/usr/bin/env bash
# scripts/wsl-verify.sh — the automated WSL verification of the Linux side of the release batch.
#
# Scope (spec `spec-updater` AD-8 + the Linux packaging specs):
#   1. the full JVM test suite on a Linux host (after clean — the build dir is shared with the
#      Windows host under /mnt/c: the Kotlin incremental cache is NOT cross-OS readable — the
#      Windows build fails on a Linux-written cache ("\mnt\c\…") and the Linux build fails on a
#      Windows-written cache ("C:\…", "Expected absolute path but found relative path").
#      Whichever OS builds second must run clean.)
#   2. the per-channel Linux artifact naming contract — stable + dev (the dev keeps its OWN
#      package base `n-zik-desktop-compagnon-dev` + the dated `-dev-<yyyyMMdd>` suffix; stable
#      names stay byte-identical to the no-channel convention, so the AUR release entry stays valid)
#   3. the .deb builds (Ubuntu host) — stable + dev; the .rpm is built when rpmbuild is present
#   4. the stable portable-zip launch smoke test (needs a display — WSLg; skipped headless)
#
# Out of scope: the Windows/NSIS installer + the in-app updater install flow (Windows-only —
# verified on the Windows host; see the TODO.txt verification gate).
#
# Usage (from the Windows side, the repo checked out under the Windows tree):
#   wsl -e bash -c "cd /mnt/c/<path>/N-Zik-Desktop-Compagnon && bash scripts/wsl-verify.sh"
# or inside WSL, from the repo root:
#   bash scripts/wsl-verify.sh
#
# Exit code: 0 = every executed check passed (skips are allowed), 1 = at least one FAIL.
#
# NOTE: jpackage wipes its output dir on every run — each channel's artifact is therefore
# verified IMMEDIATELY after its own build (a later channel build deletes the earlier one).
set -uo pipefail
cd "$(dirname "$0")/.."

PASS=0 FAIL=0 SKIP=0
ok()   { echo "  PASS  $1"; PASS=$((PASS+1)); }
bad()  { echo "  FAIL  $1"; FAIL=$((FAIL+1)); }
skip() { echo "  SKIP  $1"; SKIP=$((SKIP+1)); }

# locate an exact file name under the compose output tree (the layout varies by task:
# the zips land in binaries/, the .deb/.rpm in binaries/main/<type>/)
find_artifact() {
  find app/build/compose/binaries -type f -name "$1" 2>/dev/null | head -1
}

baseVersion=$(grep -oP 'nzikVersionName\s*=\s*"\K[^"]+' gradle/libs.versions.toml)
today=$(date +%Y%m%d)
pkgStable=n-zik-desktop-compagnon
pkgDev=n-zik-desktop-compagnon-dev

echo "== [1/4] full test suite (Linux, after clean) =="
if ./gradlew clean build --console=plain -q; then ok "gradlew clean build (full suite, Linux host)"; else bad "gradlew clean build failed"; fi

echo "== [2/4] channel artifact naming — portable zip (stable + dev) =="
if ./gradlew :app:packageLinuxPortable -Pchannel=stable --console=plain -q; then
  if [ -n "$(find_artifact "${pkgStable}-${baseVersion}-linux-portable.zip")" ]; then
    ok "stable zip: ${pkgStable}-${baseVersion}-linux-portable.zip"
  else
    bad "missing ${pkgStable}-${baseVersion}-linux-portable.zip under app/build/compose/binaries"
  fi
else
  bad "packageLinuxPortable (stable) failed"
fi

if ./gradlew :app:packageLinuxPortable -Pchannel=dev --console=plain -q; then
  if [ -n "$(find_artifact "${pkgDev}-${baseVersion}-dev-${today}-linux-portable.zip")" ]; then
    ok "dev zip: ${pkgDev}-${baseVersion}-dev-${today}-linux-portable.zip"
  else
    bad "missing ${pkgDev}-${baseVersion}-dev-${today}-linux-portable.zip under app/build/compose/binaries (today=$today)"
  fi
else
  bad "packageLinuxPortable (dev) failed"
fi

echo "== [3/4] .deb builds (stable + dev) / .rpm (when rpmbuild is present) =="
if command -v dpkg >/dev/null 2>&1; then
  if ./gradlew :app:packageDeb -Pchannel=stable --console=plain -q; then
    if [ -n "$(find_artifact "${pkgStable}_${baseVersion}-1_amd64.deb")" ]; then
      ok "stable deb: ${pkgStable}_${baseVersion}-1_amd64.deb"
    else
      bad "missing ${pkgStable}_${baseVersion}-1_amd64.deb under app/build/compose/binaries"
    fi
  else
    bad "packageDeb (stable) failed"
  fi
  if ./gradlew :app:packageDeb -Pchannel=dev --console=plain -q; then
    if [ -n "$(find_artifact "${pkgDev}_${baseVersion}-dev-${today}-1_amd64.deb")" ]; then
      ok "dev deb: ${pkgDev}_${baseVersion}-dev-${today}-1_amd64.deb"
    else
      bad "missing ${pkgDev}_${baseVersion}-dev-${today}-1_amd64.deb under app/build/compose/binaries (today=$today)"
    fi
  else
    bad "packageDeb (dev) failed"
  fi
else
  skip "dpkg not present — .deb builds skipped"
fi
if command -v rpmbuild >/dev/null 2>&1; then
  if ./gradlew :app:packageRpm -Pchannel=stable --console=plain -q; then
    if [ -n "$(find_artifact "${pkgStable}-${baseVersion}-1.x86_64.rpm")" ]; then
      ok "stable rpm: ${pkgStable}-${baseVersion}-1.x86_64.rpm"
    else
      bad "missing ${pkgStable}-${baseVersion}-1.x86_64.rpm under app/build/compose/binaries"
    fi
  else
    bad "packageRpm (stable) failed"
  fi
else
  skip "rpmbuild not present — .rpm build skipped"
fi

echo "== [4/4] stable portable-zip launch smoke test (display required) =="
stableZip=$(find_artifact "${pkgStable}-${baseVersion}-linux-portable.zip")
if [ -z "${WAYLAND_DISPLAY:-}${DISPLAY:-}" ]; then
  skip "no display (headless WSL session) — launch smoke test skipped"
elif [ -z "$stableZip" ]; then
  skip "stable zip not present — launch smoke test skipped"
elif ! command -v unzip >/dev/null 2>&1 && ! command -v python3 >/dev/null 2>&1; then
  skip "neither unzip nor python3 present — launch smoke test skipped"
else
  tmp=$(mktemp -d)
  if command -v unzip >/dev/null 2>&1; then
    unzip -q "$stableZip" -d "$tmp"
  else
    python3 -c "import zipfile,sys; zipfile.ZipFile(sys.argv[1]).extractall(sys.argv[2])" "$stableZip" "$tmp"
  fi
  imageDir=$(ls -d "$tmp"/*/ | head -1)
  # python's zipfile does not restore the Unix exec bits (the zip entries are 0755 — a real
  # `unzip` restores them); chmod so the smoke test can exec the launcher either way
  chmod +x "$imageDir"/bin/* 2>/dev/null || true
  launcher=$(ls "$imageDir"bin/ | head -1)
  "$imageDir/bin/$launcher" >/dev/null 2>&1 &
  pid=$!
  sleep 8
  if kill -0 "$pid" 2>/dev/null; then
    ok "stable launcher alive after 8s (pairing screen up)"
    kill "$pid" 2>/dev/null
  else
    bad "launcher died within 8s"
  fi
  rm -rf "$tmp"
fi

echo
echo "WSL-VERIFY: PASS=$PASS FAIL=$FAIL SKIP=$SKIP"
if [ "$FAIL" -eq 0 ]; then
  echo "WSL-VERIFY: GREEN"
else
  echo "WSL-VERIFY: RED"
  exit 1
fi
