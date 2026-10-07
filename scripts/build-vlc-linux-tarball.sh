#!/usr/bin/env bash
#
# build-vlc-linux-tarball.sh — one-shot producer of the pinned Linux VLC runtime tarball
# (spec `spec-linux-appimage`).
#
# VideoLAN publishes no prebuilt Linux runtime (source tarball + deb/rpm packages only), so the
# AppImage's embedded runtime is built here, once, from the official VideoLAN source. The source
# URL + SHA-256 are pinned below — this committed script IS the source of the pinned binary
# (transparency): rebuilding it reproduces the same audio subset, and the tarball's own SHA-256
# is what the build pins in `gradle/libs.versions.toml` (`vlc-linux-tarball-sha256`).
#
# What it produces (the same audio subset that `app/build.gradle.kts` extracts into the build —
# the two lists must stay in sync):
#   vlc-3.0.24-linux-x64/
#   ├── COPYING
#   ├── libvlc.so            (unversioned name JNA looks for — VlcRuntime's platform libvlc)
#   ├── libvlccore.so.9      (libvlc.so's DT_NEEDED soname; resolved via the AppImage's AppRun
#   │                          exporting the runtime dir on LD_LIBRARY_PATH — the build-time rpath
#   │                          `make install` bakes in is stripped (step 4b), so no lib carries an
#   │                          active rpath to the build host)
#   ├── libvlc_pulse.so.0    (the PulseAudio output's wrapper lib — libpulse_plugin.so's DT_NEEDED)
#   └── plugins/…            (access / audio_filter / audio_mixer / audio_output / codec /
#                             control / demux / packetizer / stream_filter — audio only)
#
# After the script finishes:
#   1. pin the printed SHA-256 in `gradle/libs.versions.toml` (`vlc-linux-tarball-sha256`);
#   2. upload the tarball to the GitHub release as the asset `vlc-3.0.24-linux-x64.tar.gz`
#      (chore, tracked in `assets/notes/TODO.txt`) — until then the download task in the build
#      is cache-first: the tarball already sits in the Gradle user home from step (3);
#   3. the tarball was copied to the Gradle user home cache
#      (`$GRADLE_USER_HOME/caches/n-zik-compagnon/vlc/`) so `downloadVlcLinux` finds it without
#      network access.
#
# Usage (WSL Ubuntu/Debian — or any Debian/Ubuntu-family Linux):
#   bash scripts/build-vlc-linux-tarball.sh
#
# Build dependencies (install once; run the script as root, or a user with passwordless sudo):
#   apt-get install -y build-essential libtool pkg-config gettext patchelf \
#     libogg-dev libvorbis-dev libopus-dev libmpg123-dev libflac-dev libfaad-dev \
#     libasound2-dev libpulse-dev libgnutls28-dev libssl-dev zlib1g-dev \
#     libsamplerate0-dev libebml-dev libmatroska-dev
#   (the gnutls dev package is `libgnutls-dev` on Debian; `libasound2t64-dev` on the t64
#   transition releases — the script tries both spellings)

set -euo pipefail

# Always run from the repository root, no matter where the script is called from.
cd "$(dirname "$0")/.."

VLC_VERSION=3.0.24
TARBALL_NAME="vlc-$VLC_VERSION-linux-x64.tar.gz"
SRC_URL="https://download.videolan.org/pub/videolan/vlc/$VLC_VERSION/vlc-$VLC_VERSION.tar.xz"
# From the official vlc-3.0.24.tar.xz.sha256 on download.videolan.org.
SRC_SHA256="e7cab503d1d7d5849b89d2cf0e1ee60d0ef6d012407791b644b9cfc0cc225fdf"

WORK="${WORK:-$HOME/nzik-vlc-linux}"
SRC="$WORK/vlc-$VLC_VERSION"
PREFIX="$WORK/prefix"
GRADLE_USER_HOME="${GRADLE_USER_HOME:-$HOME/.gradle}"
CACHE_DIR="$GRADLE_USER_HOME/caches/n-zik-compagnon/vlc"

# --- apt build dependencies (skipped when already present) ----------------------------------------
if ! pkg-config --exists ogg vorbis opus flac mpg123 faad pulse gnutls zlib \
    samplerate libebml libmatroska 2>/dev/null \
    || ! command -v libtool >/dev/null || ! command -v msgfmt >/dev/null \
    || ! command -v patchelf >/dev/null; then
  echo "== installing build dependencies (needs root or sudo) =="
  SUDO=""
  if [ "$(id -u)" -ne 0 ]; then SUDO="sudo"; fi
  export DEBIAN_FRONTEND=noninteractive
  $SUDO apt-get update -qq
  $SUDO apt-get install -y -qq \
    build-essential libtool pkg-config gettext patchelf \
    libogg-dev libvorbis-dev libopus-dev libmpg123-dev libflac-dev libfaad-dev \
    libssl-dev zlib1g-dev libsamplerate0-dev libebml-dev libmatroska-dev
  $SUDO apt-get install -y -qq libasound2-dev 2>/dev/null \
    || $SUDO apt-get install -y -qq libasound2t64-dev
  $SUDO apt-get install -y -qq libpulse-dev
  $SUDO apt-get install -y -qq libgnutls28-dev 2>/dev/null \
    || $SUDO apt-get install -y -qq libgnutls-dev
fi

# --- 1. Download the official source tarball, verified against its pinned SHA-256 -----------------
mkdir -p "$WORK"
if [ ! -f "$WORK/$VLC_VERSION.tar.xz" ]; then
  echo "== downloading $SRC_URL =="
  curl -fSL --retry 3 -o "$WORK/$VLC_VERSION.tar.xz.part" "$SRC_URL"
  actual="$(sha256sum "$WORK/$VLC_VERSION.tar.xz.part" | cut -d' ' -f1)"
  if [ "$actual" != "$SRC_SHA256" ]; then
    rm -f "$WORK/$VLC_VERSION.tar.xz.part"
    echo "ERROR: source tarball SHA-256 mismatch: expected $SRC_SHA256, got $actual" >&2
    exit 1
  fi
  mv "$WORK/$VLC_VERSION.tar.xz.part" "$WORK/$VLC_VERSION.tar.xz"
fi
echo "== source tarball OK ($(du -h "$WORK/$VLC_VERSION.tar.xz" | cut -f1)) =="

# --- 2. Extract + configure (audio-only: no GUI, no X11, no Qt, no video) -------------------------
rm -rf "$SRC"
echo "== extracting =="
tar -xf "$WORK/$VLC_VERSION.tar.xz" -C "$WORK"

echo "== configure (audio-only, no GUI/X11/Qt) =="
cd "$SRC"
rm -rf "$PREFIX"
mkdir -p "$PREFIX"
./configure \
  --prefix="$PREFIX" \
  --disable-qt \
  --disable-xcb \
  --disable-xvideo \
  --disable-dbus \
  --disable-lua \
  --disable-vcd \
  --disable-jack \
  --disable-sndio \
  --disable-v4l2 \
  --disable-decklink \
  --disable-mpc \
  --disable-mod \
  --disable-taglib \
  --disable-libcddb \
  --disable-avcodec \
  --disable-ncurses \
  --disable-skins2 \
  --disable-sout \
  --disable-vlm \
  --disable-libgcrypt \
  --disable-swscale \
  --disable-freetype \
  --disable-chromecast \
  --disable-dvdread \
  --disable-dvdnav \
  --disable-bluray

# --- 3. Build + install (the console `vlc` binary is built too — it is not shipped) ---------------
echo "== make -j$(nproc) =="
make -j"$(nproc)"
make install

# --- 4. Assemble the audio subset (the staging dir is the tarball root) ---------------------------
STAGE="$WORK/$TARBALL_NAME"
STAGE="${STAGE%.tar.gz}"   # vlc-3.0.24-linux-x64
rm -rf "$STAGE"
mkdir -p "$STAGE"

# The license file (the source's COPYING — the AppImage carries the license next to the runtime).
cp "$SRC/COPYING" "$STAGE/COPYING"

# The runtime libraries:
#   libvlc.so          — the unversioned name JNA looks for (a real file, not a symlink, so the
#                        tarball stays self-contained wherever it is extracted);
#   libvlccore.so.9    — libvlc.so's DT_NEEDED soname (a real file, resolved through the runtime
#                        dir on LD_LIBRARY_PATH — see the AppImage's AppRun);
#   libvlc_pulse.so.0  — the PulseAudio output wrapper library the build produces next to the
#                        plugins (installed under $PREFIX/lib/vlc/): `libpulse_plugin.so` has it in
#                        its DT_NEEDED, so the tarball must carry it like any other runtime lib.
cp "$(readlink -f "$PREFIX/lib/libvlc.so.5")" "$STAGE/libvlc.so"
cp "$(readlink -f "$PREFIX/lib/libvlccore.so.9")" "$STAGE/libvlccore.so.9"
cp "$(readlink -f "$PREFIX/lib/vlc/libvlc_pulse.so.0")" "$STAGE/libvlc_pulse.so.0"

# The audio subset — keep in sync with `vlcLinuxRuntimeFiles` in `app/build.gradle.kts` (the
# WASAPI/DirectSound/mmdevice outputs of the Windows list are the ALSA/PulseAudio outputs here).
AUDIO_SUBSET="
plugins/access/libfilesystem_plugin.so
plugins/access/libhttps_plugin.so
plugins/audio_filter/libaudio_format_plugin.so
plugins/audio_filter/libsamplerate_plugin.so
plugins/audio_filter/libscaletempo_plugin.so
plugins/audio_filter/libtrivial_channel_mixer_plugin.so
plugins/audio_filter/libugly_resampler_plugin.so
plugins/audio_mixer/libfloat_mixer_plugin.so
plugins/audio_mixer/libinteger_mixer_plugin.so
plugins/audio_output/libalsa_plugin.so
plugins/audio_output/libpulse_plugin.so
plugins/codec/libfaad_plugin.so
plugins/codec/libflac_plugin.so
plugins/codec/libmpg123_plugin.so
plugins/codec/libopus_plugin.so
plugins/codec/libvorbis_plugin.so
plugins/control/libdummy_plugin.so
plugins/demux/libes_plugin.so
plugins/demux/libflacsys_plugin.so
plugins/demux/libmkv_plugin.so
plugins/demux/libmp4_plugin.so
plugins/demux/libogg_plugin.so
plugins/packetizer/libpacketizer_flac_plugin.so
plugins/packetizer/libpacketizer_mpeg4audio_plugin.so
plugins/packetizer/libpacketizer_mpegaudio_plugin.so
plugins/stream_filter/libcache_read_plugin.so
plugins/stream_filter/libprefetch_plugin.so
plugins/stream_filter/libskiptags_plugin.so
"
for plugin in $AUDIO_SUBSET; do
  src="$PREFIX/lib/vlc/$plugin"
  if [ ! -f "$src" ]; then
    echo "ERROR: expected plugin not built: $src" >&2
    exit 1
  fi
  mkdir -p "$STAGE/$(dirname "$plugin")"
  cp "$src" "$STAGE/$plugin"
done

# --- 4b. Strip the build-time rpath from every .so (a portable tarball) ---------------------------
# `make install` leaves each module's rpath set to the build prefix (e.g. /home/<user>/prefix/lib):
# machine-specific, so the pinned tarball must not carry an ACTIVE rpath to the build host. At
# runtime the AppImage's AppRun exports the runtime dir on LD_LIBRARY_PATH, so the rpath is not
# needed. patchelf removes DT_RPATH and DT_RUNPATH. (The removed string may linger as dead,
# unreferenced bytes in each lib's .dynstr — inert: no dynamic entry points to it, and it is
# removed from the load path entirely.)
echo "== stripping build rpath (patchelf) =="
while IFS= read -r -d '' so; do
  # Only touch libs that actually carry an rpath (patchelf warns on no-op removals).
  # Note: readelf prints DT_RPATH as `(RPATH)` and DT_RUNPATH as `(RUNPATH)` — match both.
  if readelf -d "$so" | grep -qE 'RPATH|RUNPATH'; then
    patchelf --remove-rpath "$so"
  fi
done < <(find "$STAGE" -name '*.so*' -type f -print0)
leftover=$(find "$STAGE" -name '*.so*' -type f -print0 \
  | xargs -0 readelf -d 2>/dev/null | grep -cE 'RPATH|RUNPATH' || true)
if [ "$leftover" -gt 0 ]; then
  echo "ERROR: rpath/runpath still present in $leftover staged lib(s)" >&2
  exit 1
fi

# --- 5. Tar it (reproducible: sorted entries, fixed owner/mtime, gzip -n) -------------------------
# The tar root is the staging dir's basename (relative, from $WORK) so the archive contains a single
# top-level `vlc-3.0.24-linux-x64/` dir — the layout `extractVlcLinux` in app/build.gradle.kts expects.
echo "== tarring =="
cd "$WORK"
tar --sort=name --owner=0 --group=0 --numeric-owner \
    --mtime='UTC 2026-01-01' --format=gnu \
    -cf "$STAGE.tar" "$(basename "$STAGE")"
gzip -n -9 -f "$STAGE.tar"

# --- 6. Publish the SHA-256 + drop the tarball in the Gradle user home cache -----------------------
TARBALL="$WORK/$TARBALL_NAME"
echo
echo "== DONE =="
echo "tarball : $TARBALL ($(du -h "$TARBALL" | cut -f1))"
echo "SHA-256 : $(sha256sum "$TARBALL" | cut -d' ' -f1)"
echo
echo "Next steps:"
echo "  1. pin the SHA-256 above in gradle/libs.versions.toml (vlc-linux-tarball-sha256)"
echo "  2. upload $TARBALL_NAME to the GitHub release (chore — assets/notes/TODO.txt)"
mkdir -p "$CACHE_DIR"
cp "$TARBALL" "$CACHE_DIR/$TARBALL_NAME"
echo "cached  : $CACHE_DIR/$TARBALL_NAME (downloadVlcLinux is cache-first)"

if [ "${KEEP_WORK:-0}" != "1" ]; then
  rm -rf "$SRC" "$PREFIX"
fi
