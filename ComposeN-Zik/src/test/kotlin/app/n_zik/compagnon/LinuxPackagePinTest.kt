package app.n_zik.compagnon

import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The Linux packaging contract lives in build config that no other test path touches: a plugin
 * upgrade that stops forwarding the jpackage `freeArgs` would ship a `.deb`/`.rpm` WITHOUT the
 * `vlc` dependency (the core contract — the `.deb`/`.rpm`/AUR/portable paths never bundle VLC;
 * only the Flatpak embeds the runtime, into its own staging), and a renamed `nzikPackageName`
 * would desynchronize the `/opt` dir from the portable tarball name, the AUR `pkgname`s and the
 * download URL — all with a green build.
 * The build exposes the effective values (`systemProperty` in `ComposeN-Zik/build.gradle.kts`, same pattern
 * as `windows.packageName`) so this test pins them. Channel-aware: since the 2026-10-15 naming
 * decision every channel shares the one package base (`n-zik-desktop-compagnon` — AD-8 as
 * amended) and every artifact name carries the channel suffix after the base version —
 * `build.sh package beta|dev` runs `:ComposeN-Zik:test -Pchannel=…`, so a channel-blind pin reds
 * those builds.
 */
class LinuxPackagePinTest {

    private val releasePkgbuild = File("../packaging/aur/PKGBUILD")
    private val gitPkgbuild = File("../packaging/aur-git/PKGBUILD")

    /** The package name the running build generates (shared by every channel — AD-8 as amended 2026-10-15). */
    private fun pkgName(): String =
        System.getProperty("linux.packageName") ?: error("linux.packageName is not exposed to the tests")

    @Test
    fun `both jpackage tasks carry the vlc dependency and the Audio menu group`() {
        // The plugin exposes no `depends` in the linux { } DSL and never passes jpackage's
        // --linux-package-deps itself: the args are injected through freeArgs, so pin the effective
        // args (joined with the unit separator, exposed that way by the build).
        for ((prop, label) in listOf("linux.freeArgs.deb" to ".deb", "linux.freeArgs.rpm" to ".rpm")) {
            val args = System.getProperty(prop) ?: error("$label: freeArgs are not exposed to the tests")
            assertTrue(
                args.contains("--linux-package-deps\u001fvlc"),
                "$label: jpackage must be passed --linux-package-deps vlc (the package declares the system vlc)"
            )
            assertTrue(
                args.contains("--linux-menu-group\u001fAudio;"),
                "$label: jpackage must be passed --linux-menu-group Audio; (the .desktop menu category)"
            )
        }
    }

    @Test
    fun `the jlinked runtime carries the dbus sasl module`() {
        // The compose plugin's classpath analysis never finds jdk.security.auth (the app code never
        // references com.sun.security.auth.module): without it in the jlinked JRE, dbus-java's SASL
        // layer cannot resolve the session-bus UID and EVERY session-bus connection inside the
        // packaged Linux artifacts fails with NoClassDefFoundError — the tray + MPRIS surfaces
        // silently stay off in all five Linux packaging paths, with a green build (the 2026-10-12
        // Flatpak field bug). Pinned against the EFFECTIVE task modules (exposed by the build),
        // not a copy of the string.
        val modules = (System.getProperty("linux.jlinkModules")
            ?: error("linux.jlinkModules is not exposed to the tests")).split("\u001f")
        assertTrue(
            modules.contains("jdk.security.auth"),
            "the jlinked JRE must carry jdk.security.auth (dbus-java's SASL — every session-bus connection in the packaged Linux artifacts)"
        )
    }

    @Test
    fun `the package name is frozen and matches the portable tarball base`() {
        // Every channel shares the ONE package base (AD-8 as amended 2026-10-15 — the dev's own
        // -dev base was removed): the frozen install identity is this base name
        assertEquals(
            "n-zik-desktop-compagnon",
            pkgName().removeSuffix("-dev"),
            "the catalog-derived linux.packageName is the shared frozen install identity (every channel)",
        )
        val tar = System.getProperty("linux.portable.tarName") ?: error("the portable tarball archiveFileName is not exposed to the tests")
        assertTrue(tar.startsWith("${pkgName()}-"), "the portable tarball base name must be the channel-aware package name: $tar")
        assertTrue(tar.endsWith("-linux-portable.tar.gz"), "the portable tarball keeps its -linux-portable.tar.gz suffix: $tar")
    }

    private fun pinProp(name: String): String =
        System.getProperty(name) ?: error("$name is not exposed to the tests")

    private val sha256Regex = Regex("[0-9a-f]{64}")

    private fun assertRealSha256(prop: String, value: String) {
        assertTrue(sha256Regex.matches(value), "$prop: not a SHA-256 hex string: $value")
        assertTrue(value.toSet().size > 1, "$prop: still the placeholder (all-zeros) SHA-256 — pin the real hash")
    }

    @Test
    fun `the Linux VLC tarball is pinned by url name and real sha256`() {
        // The Flatpak is the only Linux artifact that embeds the runtime, so the tarball pin is
        // exposed under the shared `linux.tarball.*` names.
        val url = pinProp("linux.tarballUrl")
        val name = pinProp("linux.tarballName")
        val sha = pinProp("linux.tarballSha256")
        assertTrue(url.startsWith("https://github.com/N-Zik-Group/N-Zik-Desktop-Compagnon/releases/download/"),
            "the Linux tarball must be downloaded from the project's GitHub releases (the release asset it is published as): $url")
        assertTrue(url.endsWith("/$name"), "the tarball URL must point at the pinned file: $url vs $name")
        assertTrue(Regex("vlc-\\d+\\.\\d+\\.\\d+-linux-x64\\.tar\\.gz").matches(name),
            "the tarball name keeps the vlc-<version>-linux-x64.tar.gz shape: $name")
        assertRealSha256("linux.tarballSha256", sha)
    }

    @Test
    fun `the Linux VLC runtime file list pins the self-contained subset`() {
        // The extraction allow-list (ComposeN-Zik/build.gradle.kts vlcLinuxRuntimeFiles) must stay in
        // sync with the producer (scripts/build-vlc-linux-tarball.sh — 35 entries), and this
        // guard runs on every host that runs the test suite, including the ones where the
        // tarball is never extracted (the release CI does not run :ComposeN-Zik:test — the local
        // `gradlew build` gate and `build.sh package` are where it executes). The frozen set
        // is pinned EXACTLY (order-sensitive, like the libsecret config-opts pin): a
        // count-plus-spot check cannot catch a one-for-one swap (a dropped non-anchored entry
        // + a duplicate keeps the size 35 and the anchors intact, and the extraction silently
        // ships a dead subset — e.g. without the PulseAudio output, the sandbox's only audio
        // path), so an exact list equality is the only guard that fails the swap where the
        // tests run.
        val files = pinProp("linux.tarballRuntimeFiles").split("\u001f")
        assertEquals(
            listOf(
                "COPYING",
                "libvlc.so",
                "libvlccore.so.9",
                "libvlc_pulse.so.0",
                "libmatroska.so.7",
                "libebml.so.5",
                "libfaad.so.2",
                "plugins/access/libfilesystem_plugin.so",
                "plugins/access/libhttps_plugin.so",
                "plugins/audio_filter/libaudio_format_plugin.so",
                "plugins/audio_filter/libsamplerate_plugin.so",
                "plugins/audio_filter/libscaletempo_plugin.so",
                "plugins/audio_filter/libtrivial_channel_mixer_plugin.so",
                "plugins/audio_filter/libugly_resampler_plugin.so",
                "plugins/audio_mixer/libfloat_mixer_plugin.so",
                "plugins/audio_mixer/libinteger_mixer_plugin.so",
                "plugins/audio_output/libalsa_plugin.so",
                "plugins/audio_output/libpulse_plugin.so",
                "plugins/codec/libfaad_plugin.so",
                "plugins/codec/libflac_plugin.so",
                "plugins/codec/libmpg123_plugin.so",
                "plugins/codec/libopus_plugin.so",
                "plugins/codec/libvorbis_plugin.so",
                "plugins/control/libdummy_plugin.so",
                "plugins/demux/libes_plugin.so",
                "plugins/demux/libflacsys_plugin.so",
                "plugins/demux/libmkv_plugin.so",
                "plugins/demux/libmp4_plugin.so",
                "plugins/demux/libogg_plugin.so",
                "plugins/packetizer/libpacketizer_flac_plugin.so",
                "plugins/packetizer/libpacketizer_mpeg4audio_plugin.so",
                "plugins/packetizer/libpacketizer_mpegaudio_plugin.so",
                "plugins/stream_filter/libcache_read_plugin.so",
                "plugins/stream_filter/libprefetch_plugin.so",
                "plugins/stream_filter/libskiptags_plugin.so",
            ),
            files,
            "the runtime file list is the frozen 35-entry subset (the producer's 32 audio files + libmatroska.so.7 / libebml.so.5 / libfaad.so.2 — the 24.08 runtime's missing libs, measured in the sandbox 2026-10-11); a swap or duplicate must fail here, not on the Linux host at extraction time"
        )
    }

    @Test
    fun `the extraction allow-list stays in sync with the producer script`() {
        // The tarball's CONTENT is defined by the committed producer (scripts/build-vlc-linux-tarball.sh
        // — the source of the pinned binary; its two staged lists are the single source of truth). The
        // exact pin above freezes the gradle allow-list, but it cannot see a one-sided edit: if the
        // producer's list changes (a plugin swapped at a future VLC bump) and only the tarball is
        // re-pinned through the catalog PR, the diff is SHA-only — the file-list drift is invisible in
        // it. So this test parses the producer's staged lists (the four single-cp root files + the
        // RUNTIME_LIBS sonames + the AUDIO_SUBSET plugins) and compares them with the extraction
        // allow-list: a drift fails the suite on EVERY host, before the catalog PR. (Order is
        // irrelevant — both sides are file SETS, compared as sorted sets.)
        val script = File("../scripts/build-vlc-linux-tarball.sh").readText()
        val audioSubset = script
            .substringAfter("AUDIO_SUBSET=\"")
            .substringBefore("\n\"")
            .trim()
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toList()
        val runtimeLibs = script
            .substringAfter("RUNTIME_LIBS=\"")
            .substringBefore("\n\"")
            .trim()
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { it.substringBefore(' ') } // line format: "<soname> <staged needer>"
            .toList()
        val producerStaged = (listOf("COPYING", "libvlc.so", "libvlccore.so.9", "libvlc_pulse.so.0")
            + runtimeLibs + audioSubset).toSortedSet()
        assertEquals(
            producerStaged,
            pinProp("linux.tarballRuntimeFiles").split("\u001f").toSortedSet(),
            "the producer's staged file set (scripts/build-vlc-linux-tarball.sh) and the extraction allow-list must match exactly — a one-sided edit (producer or gradle list) must fail here, not surface as a bundle whose tarball and its allow-list disagree"
        )
    }

    @Test
    fun `the package name matches the AUR pkgnames and the download URL`() {
        // The committed AUR entries are STABLE-ONLY; since the 2026-10-15 naming decision every
        // channel shares the one package base (AD-8 as amended), so the package name IS the
        // base name on every channel.
        val baseName = pkgName().removeSuffix("-dev")
        // Release entry: pkgname is the package identity.
        val releaseName = releasePkgbuild.readText().lineSequence()
            .firstOrNull { it.startsWith("pkgname=") }?.removePrefix("pkgname=")
            ?: error("release entry: the pkgname= line is missing")
        assertEquals(baseName, releaseName, "the release AUR pkgname must be the jpackage package name")
        // Git entry: pkgname carries the -git suffix; the install identity is _pkgname.
        val gitName = gitPkgbuild.readText().lineSequence()
            .firstOrNull { it.startsWith("_pkgname=") }?.removePrefix("_pkgname=")
            ?: error("git entry: the _pkgname= line is missing")
        assertEquals(baseName, gitName, "the git AUR install identity (_pkgname) must be the jpackage package name")
        // The release entry downloads the tarball from the release; the URL's package base must be the same name.
        val urlBase = Regex("releases/download/v\\$\\{pkgver\\}/([a-z0-9-]+)-\\$\\{pkgver\\}-linux-portable\\.tar\\.gz")
            .find(releasePkgbuild.readText())?.groupValues?.get(1)
            ?: error("release entry: the portable-tarball download URL is missing")
        assertEquals(baseName, urlBase, "the release download URL must name the portable tarball after the package name")
    }
}
