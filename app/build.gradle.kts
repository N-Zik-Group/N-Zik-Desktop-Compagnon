import org.gradle.api.tasks.bundling.Zip
import org.gradle.api.tasks.testing.Test
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.compose.desktop.application.tasks.AbstractJPackageTask
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.MessageDigest

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.jetbrains.compose)
}

kotlin {
    jvmToolchain(21)
}

// The app version (version catalog, N-Zik Android convention): it feeds the portable distributable
// and the Windows installer alike. `nzikVersionName` is the jpackage app version, `nzikVersionCode`
// stays in the catalog for release discipline only (not read by the build). Bumped manually on each
// release (this repo has no version-bump machinery).
version = libs.versions.nzikVersionName.get()

// The embedded VLC runtime is built for Windows from the official VideoLAN zip, and for the Linux
// AppImage from the pinned Linux tarball (produced one-shot by scripts/build-vlc-linux-tarball.sh,
// spec `spec-linux-appimage`). The other Linux install paths (.deb/.rpm/AUR/portable) play through
// the system libvlc (spec `spec-linux-system-libvlc`): no runtime is downloaded for them.
val isWindowsHost = System.getProperty("os.name", "").startsWith("Windows", ignoreCase = true)
val isLinuxHost = System.getProperty("os.name", "").startsWith("Linux", ignoreCase = true)

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(libs.compose.components.resources)
    implementation(libs.compose.material3)

    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cio)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.websockets)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.zxing.core)
    implementation(libs.jna.platform)
    implementation(libs.androidx.graphics.shapes)
    // vlcj-natives comes transitively and is never overridden. It brings JNA as the `-jpms` artifacts
    // (5.16.0): the same classes as the catalog's `jna` 5.17.0 under another name, so they are excluded to
    // keep a single JNA on the classpath.
    implementation(libs.vlcj) {
        exclude(group = "net.java.dev.jna", module = "jna-jpms")
        exclude(group = "net.java.dev.jna", module = "jna-platform-jpms")
    }

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.ktor.server.websockets)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.kotlinx.coroutines.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
    // The frozen installation identity (version catalog `nzikUpgradeUuid` / `nzikPerUser`), so
    // `UpgradeIdentityTest` fails if the upgrade UUID or the per-user flag is ever changed.
    systemProperty("install.upgradeUuid", libs.versions.nzikUpgradeUuid.get())
    systemProperty("install.perUser", libs.versions.nzikPerUser.get().toBoolean().toString())
    // Packaging trial of story 12 (opt-in): `gradlew test -PvlcTrial=<directory of audio samples>`.
    providers.gradleProperty("vlcTrial").orNull?.let { media ->
        check(isWindowsHost) { "The VLC packaging trial (-PvlcTrial) is Windows-only: the embedded runtime is not built elsewhere" }
        dependsOn("extractVlc")
        systemProperty("vlc.trial.media", media)
        val runtime = providers.gradleProperty("vlcTrialRuntime").orNull
            ?: layout.buildDirectory.dir("vlc-runtime/windows-x64").get().asFile.absolutePath
        systemProperty("compose.application.resources.dir", runtime)
        testLogging.showStandardStreams = true
    }
}

tasks.compileTestKotlin {
    compilerOptions.optIn.add("kotlinx.coroutines.ExperimentalCoroutinesApi")
}

compose.resources {
    packageOfResClass = "app.n_zik.compagnon.generated.resources"
    publicResClass = false
    generateResClass = always
}

// ---- Embedded VLC runtime (libvlc 3.0.24, audio only) -----------------------------------------------
//
// The official VideoLAN zip is downloaded once into the Gradle user home (it survives `clean`), checked
// against its pinned SHA-256, and only the audio part is extracted under build/vlc-runtime. No VLC binary
// is ever committed, and no system VLC is needed.

val vlcVersion = "3.0.24"
val vlcZipUrl = "https://download.videolan.org/pub/videolan/vlc/$vlcVersion/win64/vlc-$vlcVersion-win64.zip"

// From the official vlc-3.0.24-win64.zip.sha256.
val vlcZipSha256 = "fcf30850371ad10c9373cc4f0f4501e7dee49e3e9ae9f20c72fb2661a1ca6323"

val vlcZipFile = gradle.gradleUserHomeDir.resolve("caches/n-zik-compagnon/vlc/vlc-$vlcVersion-win64.zip")

// Frozen by the packaging trial (story 12, `-vvv` logs): HTTP and file access (plain `http://` is served,
// seekable, by the `access` module of libhttps_plugin; the legacy `http` module is not seekable here), the demuxers and decoders of
// webm/opus, m4a/aac (fragmented or not), mp3, flac and ogg/vorbis/opus, the WASAPI/DirectSound outputs and
// the audio filters VLC chains for rate and format conversion.
val vlcRuntimeFiles = listOf(
    "COPYING.txt",
    "libvlc.dll",
    "libvlccore.dll",
    "plugins/access/libfilesystem_plugin.dll",
    "plugins/access/libhttps_plugin.dll",
    "plugins/audio_filter/libaudio_format_plugin.dll",
    "plugins/audio_filter/libsamplerate_plugin.dll",
    "plugins/audio_filter/libscaletempo_plugin.dll",
    "plugins/audio_filter/libtrivial_channel_mixer_plugin.dll",
    "plugins/audio_filter/libugly_resampler_plugin.dll",
    "plugins/audio_mixer/libfloat_mixer_plugin.dll",
    "plugins/audio_mixer/libinteger_mixer_plugin.dll",
    "plugins/audio_output/libdirectsound_plugin.dll",
    "plugins/audio_output/libmmdevice_plugin.dll",
    "plugins/audio_output/libwasapi_plugin.dll",
    "plugins/codec/libfaad_plugin.dll",
    "plugins/codec/libflac_plugin.dll",
    "plugins/codec/libmpg123_plugin.dll",
    "plugins/codec/libopus_plugin.dll",
    "plugins/codec/libvorbis_plugin.dll",
    "plugins/control/libdummy_plugin.dll",
    "plugins/demux/libes_plugin.dll",
    "plugins/demux/libflacsys_plugin.dll",
    "plugins/demux/libmkv_plugin.dll",
    "plugins/demux/libmp4_plugin.dll",
    "plugins/demux/libogg_plugin.dll",
    "plugins/packetizer/libpacketizer_flac_plugin.dll",
    "plugins/packetizer/libpacketizer_mpeg4audio_plugin.dll",
    "plugins/packetizer/libpacketizer_mpegaudio_plugin.dll",
    "plugins/stream_filter/libcache_read_plugin.dll",
    "plugins/stream_filter/libprefetch_plugin.dll",
    "plugins/stream_filter/libskiptags_plugin.dll",
)

fun sha256Of(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(1 shl 16)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

val downloadVlc = tasks.register("downloadVlc") {
    group = "vlc"
    description = "Downloads the official VLC $vlcVersion win64 zip and checks its pinned SHA-256."
    // Windows host only — see isWindowsHost.
    enabled = isWindowsHost
    if (!isWindowsHost) logger.lifecycle("downloadVlc: skipped on this non-Windows host — the embedded runtime is Windows-only (the app uses the system libvlc)")
    val zip = vlcZipFile
    val url = vlcZipUrl
    val expected = vlcZipSha256
    inputs.property("url", url)
    inputs.property("sha256", expected)
    outputs.file(zip)
    // The zip lives outside build/: an existing file with the right hash is never downloaded again.
    outputs.upToDateWhen { zip.isFile && sha256Of(zip) == expected }
    doLast {
        if (zip.isFile && sha256Of(zip) == expected) return@doLast
        zip.parentFile.mkdirs()
        val part = File(zip.parentFile, "${zip.name}.part")
        logger.lifecycle("Downloading $url")
        val client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build()
        val response = client.send(HttpRequest.newBuilder(URI(url)).GET().build(), HttpResponse.BodyHandlers.ofFile(part.toPath()))
        if (response.statusCode() != 200) {
            part.delete()
            throw GradleException("VLC download failed: HTTP ${response.statusCode()}")
        }
        val actual = sha256Of(part)
        if (actual != expected) {
            part.delete()
            throw GradleException("VLC zip SHA-256 mismatch: expected $expected, got $actual")
        }
        if (zip.exists()) zip.delete()
        if (!part.renameTo(zip)) throw GradleException("Could not move ${part.name} to ${zip.name}")
    }
}

val vlcRuntimeRoot = layout.buildDirectory.dir("vlc-runtime")

val extractVlc = tasks.register<Sync>("extractVlc") {
    group = "vlc"
    description = "Extracts the audio subset of VLC $vlcVersion into build/vlc-runtime/windows-x64/vlc."
    dependsOn(downloadVlc)
    // Windows host only — see isWindowsHost.
    enabled = isWindowsHost
    val prefix = "vlc-$vlcVersion/"
    val wanted = vlcRuntimeFiles.map { prefix + it }.toSet()
    from(zipTree(vlcZipFile)) {
        include { it.isDirectory || it.relativePath.pathString in wanted }
        eachFile { relativePath = RelativePath(true, *relativePath.segments.drop(1).toTypedArray()) }
    }
    includeEmptyDirs = false
    into(vlcRuntimeRoot.map { it.dir("windows-x64/vlc") })
    doLast {
        val root = vlcRuntimeRoot.get().dir("windows-x64/vlc").asFile
        val missing = vlcRuntimeFiles.filterNot { File(root, it).isFile }
        if (missing.isNotEmpty()) throw GradleException("VLC runtime incomplete, missing: $missing")
    }
}

// ---- Embedded VLC runtime for the Linux AppImage (libvlc 3.0.24, audio only) ----------------------
//
// VideoLAN publishes no prebuilt Linux runtime (source tarball + deb/rpm only), so the AppImage's
// embedded runtime is produced one-shot by scripts/build-vlc-linux-tarball.sh from the official
// VideoLAN source (its own URL + SHA-256 are pinned in that script). The resulting tarball is
// pinned URL + SHA-256 in the version catalog (SEPARATE from the Windows pins, which are never
// touched), then handled exactly like the Windows zip: downloaded once into the Gradle user home
// (it survives `clean`), checked against the pinned SHA-256, and only the audio part extracted
// under build/vlc-runtime. No VLC binary is ever committed.
//
// The AppImage is the ONLY Linux artifact that embeds VLC: the runtime is injected into the AppDir
// at assembly time (packageAppImage), never into the shared app-image — the .deb/.rpm/AUR/portable
// keep their system-vlc contract (`vlc` declared as a dependency, no resources/vlc).
//
// linuxdeploy (the AppImage builder, itself an AppImage) is pinned the same way and run at build
// time with `--appimage-extract-and-run`: no FUSE is needed on the build host (WSL included).

// Gradle 9 exposes hyphenated catalog keys as nested accessors: vlc-linux-tarball-url is
// libs.versions.vlc.linux.tarball.url, linuxdeploy-url is libs.versions.linuxdeploy.url.
val vlcLinuxTarballName = "vlc-$vlcVersion-linux-x64.tar.gz"
val vlcLinuxTarballUrl = libs.versions.vlc.linux.tarball.url.get()
// The SHA-256 of the tarball produced by scripts/build-vlc-linux-tarball.sh (pinned in the version
// catalog, published with the release asset; the download task is cache-first until the upload
// chore lands, so the local build works before the release).
val vlcLinuxTarballSha256 = libs.versions.vlc.linux.tarball.sha256.get()
val vlcLinuxTarballFile = gradle.gradleUserHomeDir.resolve("caches/n-zik-compagnon/vlc/$vlcLinuxTarballName")

// The Linux audio subset: the same frozen behavior as vlcRuntimeFiles (Windows) with the Linux
// names — the WASAPI/DirectSound/mmdevice outputs become the ALSA and PulseAudio outputs, and
// libvlccore ships under its soname (libvlc.so's DT_NEEDED, resolved through the LD_LIBRARY_PATH
// that the AppImage's AppRun exports on the runtime dir). `libvlc.so` is the unversioned name JNA
// looks for (the platform libvlc in VlcRuntime).
val vlcLinuxRuntimeFiles = listOf(
    "COPYING",
    "libvlc.so",
    "libvlccore.so.9",
    // The PulseAudio output's wrapper library (libpulse_plugin.so's DT_NEEDED — built by the same
    // configure as the plugins, installed under the prefix's lib/vlc/ like a runtime lib).
    "libvlc_pulse.so.0",
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
)

val downloadVlcLinux = tasks.register("downloadVlcLinux") {
    group = "vlc"
    description = "Downloads the pinned Linux VLC $vlcVersion runtime tarball and checks its SHA-256."
    // Linux host only — the embedded runtime feeds the AppImage (see packageAppImage).
    enabled = isLinuxHost
    if (!isLinuxHost) logger.lifecycle("downloadVlcLinux: skipped on this non-Linux host — the embedded Linux runtime feeds the AppImage only (the other Linux paths use the system libvlc)")
    val tarball = vlcLinuxTarballFile
    val url = vlcLinuxTarballUrl
    val expected = vlcLinuxTarballSha256
    inputs.property("url", url)
    inputs.property("sha256", expected)
    outputs.file(tarball)
    // The tarball lives outside build/: an existing file with the right hash is never downloaded again.
    outputs.upToDateWhen { tarball.isFile && sha256Of(tarball) == expected }
    doLast {
        if (tarball.isFile && sha256Of(tarball) == expected) return@doLast
        tarball.parentFile.mkdirs()
        val part = File(tarball.parentFile, "${tarball.name}.part")
        logger.lifecycle("Downloading $url")
        val client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build()
        val response = client.send(HttpRequest.newBuilder(URI(url)).GET().build(), HttpResponse.BodyHandlers.ofFile(part.toPath()))
        if (response.statusCode() != 200) {
            part.delete()
            throw GradleException(
                "VLC Linux tarball download failed: HTTP ${response.statusCode()} for $url. The tarball is " +
                    "published with the release; until then run scripts/build-vlc-linux-tarball.sh (it seeds the " +
                    "local Gradle user home cache) or seed the cache manually at ${tarball.absolutePath}"
            )
        }
        val actual = sha256Of(part)
        if (actual != expected) {
            part.delete()
            throw GradleException("VLC Linux tarball SHA-256 mismatch: expected $expected, got $actual")
        }
        if (tarball.exists()) tarball.delete()
        if (!part.renameTo(tarball)) throw GradleException("Could not move ${part.name} to ${tarball.name}")
    }
}

val vlcLinuxRuntimeRoot = layout.buildDirectory.dir("vlc-runtime/linux-x64")

val extractVlcLinux = tasks.register<Sync>("extractVlcLinux") {
    group = "vlc"
    description = "Extracts the audio subset of the Linux VLC $vlcVersion runtime into build/vlc-runtime/linux-x64/vlc."
    dependsOn(downloadVlcLinux)
    // Linux host only — see downloadVlcLinux.
    enabled = isLinuxHost
    val prefix = "vlc-$vlcVersion-linux-x64/"
    val wanted = vlcLinuxRuntimeFiles.map { prefix + it }.toSet()
    from(tarTree(vlcLinuxTarballFile)) {
        include { it.isDirectory || it.relativePath.pathString in wanted }
        eachFile { relativePath = RelativePath(true, *relativePath.segments.drop(1).toTypedArray()) }
    }
    includeEmptyDirs = false
    into(vlcLinuxRuntimeRoot.map { it.dir("vlc") })
    doLast {
        val root = vlcLinuxRuntimeRoot.get().dir("vlc").asFile
        val missing = vlcLinuxRuntimeFiles.filterNot { File(root, it).isFile }
        if (missing.isNotEmpty()) throw GradleException("VLC Linux runtime incomplete, missing: $missing")
    }
}

// The AppImage builder: the pinned linuxdeploy release asset (downloaded into the Gradle user home,
// checked, like the runtime tarball). x86_64 is the only architecture the app targets.
val linuxdeployUrl = libs.versions.linuxdeploy.url.get()
val linuxdeploySha256 = libs.versions.linuxdeploy.sha256.get()
val linuxdeployFile = gradle.gradleUserHomeDir.resolve("caches/n-zik-compagnon/linuxdeploy/linuxdeploy-x86_64.AppImage")

val downloadLinuxdeploy = tasks.register("downloadLinuxdeploy") {
    group = "n-zik"
    description = "Downloads the pinned linuxdeploy (Linux AppImage builder) and checks its SHA-256."
    // Linux host only — it is only used by packageAppImage.
    enabled = isLinuxHost
    if (!isLinuxHost) logger.lifecycle("downloadLinuxdeploy: skipped on this non-Linux host — the AppImage is built on a Linux/WSL host")
    val appImage = linuxdeployFile
    val url = linuxdeployUrl
    val expected = linuxdeploySha256
    inputs.property("url", url)
    inputs.property("sha256", expected)
    outputs.file(appImage)
    outputs.upToDateWhen { appImage.isFile && sha256Of(appImage) == expected }
    doLast {
        if (appImage.isFile && sha256Of(appImage) == expected) return@doLast
        appImage.parentFile.mkdirs()
        val part = File(appImage.parentFile, "${appImage.name}.part")
        logger.lifecycle("Downloading $url")
        val client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build()
        val response = client.send(HttpRequest.newBuilder(URI(url)).GET().build(), HttpResponse.BodyHandlers.ofFile(part.toPath()))
        if (response.statusCode() != 200) {
            part.delete()
            throw GradleException("linuxdeploy download failed: HTTP ${response.statusCode()}")
        }
        val actual = sha256Of(part)
        if (actual != expected) {
            part.delete()
            throw GradleException("linuxdeploy SHA-256 mismatch: expected $expected, got $actual")
        }
        if (appImage.exists()) appImage.delete()
        if (!part.renameTo(appImage)) throw GradleException("Could not move ${part.name} to ${appImage.name}")
        // Java's file writers create 0644: on a Linux host the AppImage needs its exec bit to run.
        appImage.setExecutable(true, false)
    }
}

// The app icon (committed binary, like `ic_banner2.png`): it rides in the installer, the app exe
// and the shortcuts. Pinned to the repository root so the path stays valid if the module moves.
val installerIcon = rootProject.file("assets/design/icon.ico")
// The Linux icon: jpackage needs a PNG for the .deb/.rpm (an .ico is not accepted). It is a one-shot
// conversion of icon.ico to a 256x256 RGBA PNG, committed like the others, and rides in the AUR packages.
val linuxIcon = rootProject.file("assets/design/icon-linux.png")

// The Linux package name (jpackage --linux-package-name): the deb/rpm file base name, the /opt
// install dir, the portable-zip base name and the AUR install identity, all derived from the single
// app identity (nzikPackageName) so none of them can drift from each other. Pinned by
// LinuxPackagePinTest (exposed to the JVM tests as `linux.packageName`).
val linuxPackageName = libs.versions.nzikPackageName.get().lowercase().replace(' ', '-')

// The AppImage release file name (spec spec-linux-appimage): the frozen x86_64 asset name, derived
// from the same package name + version as every other Linux artifact. Pinned by LinuxPackagePinTest.
val appImageFileName = "${linuxPackageName}-${libs.versions.nzikVersionName.get()}-x86_64.AppImage"

compose.desktop {
    application {
        mainClass = "app.n_zik.compagnon.MainKt"
        nativeDistributions {
            // Windows installer: `gradlew.bat :app:packageExe` — jpackage `--type exe`, a
            // self-extracting installer wrapping the embedded MSI, compiled by the WiX toolset that
            // the Compose plugin downloads itself (`downloadWix`/`unzipWix` on the root project).
            // The portable `createDistributable` image is produced separately and is not affected.
            // Deb/Rpm are the Linux packages: on a Windows host the plugin disables them (the format
            // is not compatible with the current OS), so the Windows outputs stay identical.
            targetFormats(TargetFormat.Exe, TargetFormat.Deb, TargetFormat.Rpm)

            // Display identity of the app (version catalog, N-Zik Android convention): installer
            // name, Start menu group and the Control-Panel (uninstall) entry.
            packageName = libs.versions.nzikPackageName.get()
            description = "Desktop companion for N-Zik: control your phone's library and playback from a large screen, and listen on your computer."
            vendor = "N-Zik Group"
            copyright = "Copyright (C) 2026 N-Zik Group"

            windows {
                // Per-user install without an elevation prompt: the app data live in %APPDATA% and
                // the Windows Credential Manager (both per-user), so a future in-app updater never
                // needs admin rights.
                perUserInstall = libs.versions.nzikPerUser.get().toBoolean()
                dirChooser = true
                menu = true
                menuGroup = "N-Zik"
                shortcut = true
                // FROZEN upgrade identity of the installation (version catalog `nzikUpgradeUuid`) —
                // NEVER change that GUID. jpackage passes it (`--win-upgrade-uuid`) to the installer
                // build, so every future installer (and the deferred in-app updater, which re-launches
                // the installer silently) recognizes this installation and upgrades it in place.
                // Pinned by `UpgradeIdentityTest`.
                upgradeUuid = libs.versions.nzikUpgradeUuid.get()
                iconFile.set(installerIcon)
            }

            // Linux .deb/.rpm identity (jpackage --linux-*): the package name is the deb/rpm file
            // base name and the /opt install dir, derived from the single app identity (nzikPackageName)
            // so it cannot drift from the rest of the installation. The app itself (window title, data
            // directory, launcher) keeps the full display name — only the package name is lowercased.
            linux {
                packageName = linuxPackageName
                // Monotone per-version release number (the rpm/deb "release"); the version itself
                // comes from nzikVersionName (a single source of truth, shared with Windows).
                appRelease = "1"
                // The package category metadata: the deb `Section` and the rpm `Group`. The .desktop
                // `Categories=` field is a separate jpackage flag (`--linux-menu-group`, injected via
                // freeArgs below) — without it jpackage leaves that field "Unknown".
                appCategory = "Audio"
                // jpackage only emits the .desktop menu entry + icon when the shortcut is on (the DSL
                // default is off) — this is the app's "raccourci".
                shortcut = true
                rpmLicenseType = "GPL-3.0-only"
                iconFile.set(linuxIcon)
            }

            // build/vlc-runtime/windows-x64/vlc → <compose.application.resources.dir>/vlc, under
            // `run`, `createDistributable` and the installer (same jpackage image). Windows host only:
            // on Linux the app plays through the system libvlc, so no runtime is embedded and the
            // packages declare `vlc` as a dependency instead (see the freeArgs below).
            if (isWindowsHost) {
                appResourcesRootDir.set(vlcRuntimeRoot)
            }
        }
    }
}

tasks.matching { it.name == "prepareAppResources" }.configureEach { dependsOn(extractVlc) }

// ---- Linux package dependencies + portable build ---------------------------------------------------
//
// The Compose plugin registers its jpackage tasks in its own afterEvaluate, so these references are
// deferred to project.afterEvaluate (which runs after the plugin's, once the tasks exist).
//
// None of the four Linux package paths (.deb/.rpm/AUR/portable) embeds VLC — the AppImage is the
// deliberate exception (spec spec-linux-appimage, the 5th release asset): those four play through
// the system libvlc (see VlcRuntime), so the .deb, the .rpm and the AUR entries declare `vlc` as
// a dependency — the package manager installs it. The
// plugin exposes no `depends` in the linux { } DSL and never passes jpackage's `--linux-package-deps`,
// so we inject it through the plugin's `freeArgs` extension point (those args are prepended to the
// jpackage command line): `vlc` lands in the deb `Depends` and the rpm `Requires` with no repackaging.
// The same hook injects `--linux-menu-group`: the jpackage flag that fills the .desktop `Categories=`
// field (`linux.appCategory` only sets the deb `Section` / the rpm `Group`; the .desktop default is
// "Unknown"), so the menu entry sorts under Audio like the AUR entries' own .desktop. The tasks run
// only on a compatible (Linux/WSL) host; on Windows they are disabled by the plugin.
project.afterEvaluate {
    tasks.named<AbstractJPackageTask>("packageDeb") { freeArgs.addAll(listOf("--linux-package-deps", "vlc", "--linux-menu-group", "Audio;")) }
    tasks.named<AbstractJPackageTask>("packageRpm") { freeArgs.addAll(listOf("--linux-package-deps", "vlc", "--linux-menu-group", "Audio;")) }

    // Expose the effective Linux packaging contract to the JVM tests (same pattern as
    // `install.upgradeUuid`): LinuxPackagePinTest fails if a plugin upgrade stops forwarding these
    // freeArgs (the `vlc` dependency + the Audio menu group are the core of the Linux packages) or
    // if the package name drifts from the portable zip's file name. Read eagerly here: this
    // afterEvaluate block runs after the plugin's (the tasks exist and the freeArgs are injected
    // above), and Test.systemProperty stores the value as-is (no Provider resolution).
    tasks.named<Test>("test") {
        systemProperty("linux.packageName", linuxPackageName)
        systemProperty("linux.freeArgs.deb", tasks.named<AbstractJPackageTask>("packageDeb").get().freeArgs.get().joinToString("\u001f"))
        systemProperty("linux.freeArgs.rpm", tasks.named<AbstractJPackageTask>("packageRpm").get().freeArgs.get().joinToString("\u001f"))
        systemProperty("linux.portable.zipName", tasks.named<Zip>("packageLinuxPortable").get().archiveFileName.get())
        // The AppImage contract (spec spec-linux-appimage): the pinned tarball (URL + name + SHA-256),
        // the pinned linuxdeploy (URL + SHA-256), the frozen AppImage file name and the resources-dir
        // injection point (the AppImage is the only Linux artifact that embeds the runtime — the other
        // four paths must never get a resources/vlc).
        systemProperty("linux.appImage.tarballUrl", vlcLinuxTarballUrl)
        systemProperty("linux.appImage.tarballName", vlcLinuxTarballName)
        systemProperty("linux.appImage.tarballSha256", vlcLinuxTarballSha256)
        systemProperty("linux.appImage.linuxdeployUrl", linuxdeployUrl)
        systemProperty("linux.appImage.linuxdeploySha256", linuxdeploySha256)
        systemProperty("linux.appImage.fileName", appImageFileName)
        systemProperty("linux.appImage.resourcesInjection", "usr/lib/app/resources/vlc")
    }

    // The portable Linux build is the jpackage app-image — the only Linux binary the AUR entry consumes.
    // `createDistributableImpl` is the plugin's hidden jpackage task (TargetFormat.AppImage) that writes
    // the app-image to <outputBaseDir>/<appDirName>/app/<package name>/. We zip that output folder so
    // the app folder is the zip root. Linux host only: on a Windows host the app-image is the Windows
    // image, so the task is disabled there.
    val createDistributableImpl = tasks.named<AbstractJPackageTask>("createDistributableImpl")

    // The app-image launcher must keep its execute bit inside the portable zip (users unzip it as-is
    // and the AUR release entry consumes it): this repo lives on the 9P/drvfs mount (/mnt/d on WSL),
    // where Gradle's zip writer does not preserve the launcher's exec bit (observed: 0777 on disk but
    // 0644 in the zip entry, reproduced by a forced re-zip). Java's zip API has no external-attributes
    // setter, so the zip task patches the launcher's central-directory header bytes directly (the Unix
    // mode lives at byte 38 of each central header; the launcher is the only entry that needs an exec
    // bit — jars and .so files do not). On a regular (non-9P) host the entry already carries the bit,
    // so the patch is a no-op.
    // jpackage lays the app-image out as <destinationDir>/<AppName>/bin/<AppName>; the launcher
    // shares the app-image folder name (located dynamically — no hard-coded display name).
    val launcherFile = createDistributableImpl.flatMap { it.destinationDir }.map { dir ->
        val base = dir.asFile
        // `listFiles` returns null when the destination is missing or not a directory (e.g. a disabled
        // or cleaned app-image build): fail with a clear message instead of a bare NPE.
        val appImages = base.listFiles { f, _ -> f.isDirectory }
            ?.filter { it.resolve("bin").isDirectory }
            ?: error("app-image output dir is missing or not a directory: $base")
        require(appImages.size == 1) {
            "expected exactly one app-image directory (with a bin/) in $base, found ${appImages.size}"
        }
        val appImage = appImages.first()
        // Fail-loud boundary (spec spec-linux-appimage): the shared app-image must never carry the
        // embedded VLC runtime — only the AppDir does (packageAppImage step 3, after this boundary).
        // A runtime leaking here would ride in the .deb/.rpm/AUR/portable under a green build.
        if (appImage.resolve("lib/app/resources/vlc").exists()) {
            throw GradleException(
                "the shared app-image carries lib/app/resources/vlc ($appImage) — only the AppImage may embed the VLC runtime"
            )
        }
        val launcher = appImage.resolve("bin").resolve(appImage.name)
        if (!launcher.isFile) error("app-image launcher not found: $launcher")
        launcher
    }

    tasks.register<Zip>("packageLinuxPortable") {
        group = "n-zik"
        description = "Zips the Linux app-image into n-zik-desktop-compagnon-<version>-linux-portable.zip."
        enabled = !isWindowsHost
        if (isWindowsHost) {
            logger.lifecycle("packageLinuxPortable: skipped on this Windows host — build it on a Linux/WSL host")
        }
        dependsOn(createDistributableImpl)
        from(createDistributableImpl.flatMap { it.destinationDir })
        archiveFileName.set("${linuxPackageName}-${libs.versions.nzikVersionName.get()}-linux-portable.zip")
        destinationDirectory.set(layout.buildDirectory.dir("compose/binaries"))
        // Patch the launcher entry's Unix mode to 0755 in the central directory (see above).
        doLast {
            val zipFile = archiveFile.get().asFile
            val baseDir = createDistributableImpl.get().destinationDir.get().asFile
            val entryName = launcherFile.get().path.removePrefix(baseDir.path.trimEnd('/').plus("/"))
            fun readBytes(f: File): ByteArray {
                val buf = ByteArray(65536)
                val out = ByteArrayOutputStream()
                val input = BufferedInputStream(f.inputStream())
                try {
                    var n = input.read(buf)
                    while (n >= 0) {
                        out.write(buf, 0, n)
                        n = input.read(buf)
                    }
                } finally {
                    input.close()
                }
                return out.toByteArray()
            }
            fun writeBytes(f: File, b: ByteArray) {
                val output = FileOutputStream(f)
                try {
                    output.write(b)
                } finally {
                    output.close()
                }
            }
            // Offset of the central-directory header holding [name] (-1 when absent).
            fun scanHeader(b: ByteArray, name: String): Int {
                var offset = 0
                while (offset + 46 <= b.size) {
                    // Central-directory file header signature is PK\x01\x02 = 50 4B 01 02 in file order.
                    val isHeader =
                        b[offset] == 0x50.toByte() && b[offset + 1] == 0x4B.toByte() &&
                            b[offset + 2] == 0x01.toByte() && b[offset + 3] == 0x02.toByte()
                    if (isHeader) {
                        val nameLen = (b[offset + 28].toInt() and 0xFF) or ((b[offset + 29].toInt() and 0xFF) shl 8)
                        if (offset + 46 + nameLen <= b.size && String(b, offset + 46, nameLen) == name) {
                            return offset
                        }
                        offset += 46 + nameLen
                    } else {
                        offset += 1
                    }
                }
                return -1
            }
            val bytes = readBytes(zipFile)
            val header = scanHeader(bytes, entryName)
            require(header >= 0) { "portable zip launcher entry not found in central directory: $entryName" }
            // Little-endian 0x81ED = 0o100755 (regular file + rwxr-xr-x).
            bytes[header + 38] = 0x00
            bytes[header + 39] = 0x00
            bytes[header + 40] = 0xED.toByte()
            bytes[header + 41] = 0x81.toByte()
            writeBytes(zipFile, bytes)
            // Verify by re-reading: the mode bytes must now be 0o100755.
            val reRead = readBytes(zipFile)
            val reHeader = scanHeader(reRead, entryName)
            require(reHeader >= 0 && reRead[reHeader + 38] == 0x00.toByte() &&
                reRead[reHeader + 39] == 0x00.toByte() &&
                reRead[reHeader + 40] == 0xED.toByte() && reRead[reHeader + 41] == 0x81.toByte()
            ) { "portable zip launcher entry is not 0755 after the central-directory patch: $zipFile" }
        }
    }

    // The AppImage (spec spec-linux-appimage) — the 5th Linux artifact: the app-image (the same
    // artifact the portable zip ships, kept free of the VLC runtime) wrapped by the pinned
    // linuxdeploy, with the embedded Linux VLC runtime injected into the AppDir's resources dir.
    // The Compose plugin's `TargetFormat.AppImage` only produces the app-image (no linuxdeploy, no
    // AppRun, no .desktop — verified empirically on 1.13.0-alpha01), so this task runs linuxdeploy
    // itself. Linux host only; on Windows it is skipped like packageLinuxPortable.
    tasks.register("packageAppImage") {
        group = "n-zik"
        description = "Builds the x86_64 AppImage (app + JRE + embedded VLC) from the app-image via linuxdeploy."
        enabled = isLinuxHost
        if (!isLinuxHost) {
            logger.lifecycle("packageAppImage: skipped on this non-Linux host — build it on a Linux/WSL host")
        }
        dependsOn(createDistributableImpl, extractVlcLinux, downloadLinuxdeploy)
        doLast {
            val binaries = layout.buildDirectory.dir("compose/binaries").get().asFile
            // The AppDir + the linuxdeploy out-dir live under the Gradle user home (local disk)
            // instead of the repo build dir: on the WSL build host the repo is on the 9P mount,
            // where the long-lived daemon can serve a stale (empty) listing of a dir another
            // process just populated (a produced AppImage was lost that way once — step 7 then
            // failed loudly), and linuxdeploy does thousands of small file ops there. Only the
            // finished AppImage is transferred back to the repo (step 7).
            val workDir = File(gradle.gradleUserHomeDir, "caches/n-zik-compagnon/appimage")
            val appDir = File(workDir, "AppDir")

            // 1. Locate the app-image (the same dynamic detection as the portable zip's launcher:
            //    the single subdirectory of the jpackage output dir that carries a bin/).
            val base = createDistributableImpl.get().destinationDir.get().asFile
            val appImages = base.listFiles { f, _ -> f.isDirectory }
                ?.filter { it.resolve("bin").isDirectory }
                ?: throw GradleException("app-image output dir is missing or not a directory: $base")
            require(appImages.size == 1) { "expected exactly one app-image directory (with a bin/) in $base, found ${appImages.size}" }
            val image = appImages.first()
            // Fail-loud boundary (spec spec-linux-appimage): the shared app-image must be free of
            // the embedded runtime — this task injects it into the AppDir only (step 3, after this
            // point), so a pre-existing resources/vlc here means a leak into the .deb/.rpm/AUR/
            // portable artifacts, and the build fails instead of shipping it.
            if (File(image, "lib/app/resources/vlc").exists()) {
                throw GradleException(
                    "the shared app-image carries lib/app/resources/vlc ($image) — only the AppImage may embed the VLC runtime"
                )
            }

            // 2. AppDir layout (AppImage convention): the app-image maps 1:1 under usr/ —
            //    <image>/bin/* → AppDir/usr/bin/ and <image>/lib → AppDir/usr/lib (the cfg, the
            //    jars, the JRE, libapplauncher.so). The launcher keeps its display name as-is: it
            //    looks up its own <name>.cfg next to it, and its $APPDIR (lib/app) derives from its
            //    location, so the classpath, the resources dir and the skiko path all keep resolving.
            delete(appDir)
            File(appDir, "usr/bin").mkdirs()
            File(appDir, "usr/lib").mkdirs()
            copy { from(File(image, "bin")); into(File(appDir, "usr/bin")) }
            copy { from(File(image, "lib")); into(File(appDir, "usr/lib")) }

            // 3. Inject the embedded VLC runtime into the app-image's resources dir — the one
            //    VlcRuntime reads (the cfg sets -Dcompose.application.resources.dir=$APPDIR/resources,
            //    i.e. <app-image>/lib/app/resources). Only the AppDir gets it: the shared app-image
            //    (and therefore the .deb/.rpm/AUR/portable) stays free of resources/vlc.
            val resourcesVlc = File(appDir, "usr/lib/app/resources/vlc")
            copy { from(vlcLinuxRuntimeRoot.get().dir("vlc")); into(resourcesVlc) }

            // 4. AppRun: exec the launcher with the runtime dir on LD_LIBRARY_PATH — the built
            //    libraries carry no rpath, so libvlc.so's DT_NEEDED (the libvlccore soname) and the
            //    plugins' own DT_NEEDED resolve through the runtime dir.
            File(appDir, "AppRun").apply {
                writeText(
                    "#!/bin/sh\n" +
                    "export LD_LIBRARY_PATH=\"${'$'}APPDIR/usr/lib/app/resources/vlc${'$'}{LD_LIBRARY_PATH:+:${'$'}LD_LIBRARY_PATH}\"\n" +
                    "exec \"${'$'}APPDIR/usr/bin/${image.name}\" \"${'$'}@\"\n"
                )
                setExecutable(true, false)
            }

            // 5. The .desktop entry + the hicolor icon. Exec names the usr/bin launcher (the AppImage
            //    runtime resolves a bare command against AppDir/usr/bin). No Version= key: per the
            //    desktop-entry spec it is the SPEC version (1.5), not the app version — the produced
            //    AppImage's file name is pinned explicitly in step 6 (L-D-A-I output env var), not
            //    derived from the .desktop. Categories needs the AudioVideo main category alongside
            //    Audio (the menu-spec pair appimagetool's validator enforces).
            File(appDir, "usr/share/applications").mkdirs()
            File(appDir, "usr/share/applications/$linuxPackageName.desktop").writeText(
                "[Desktop Entry]\n" +
                "Type=Application\n" +
                "Name=${libs.versions.nzikPackageName.get()}\n" +
                "Comment=Desktop companion for N-Zik: control your phone's library and playback from a large screen, and listen on your computer.\n" +
                "Exec=\"${image.name}\"\n" +
                "Icon=$linuxPackageName\n" +
                "Terminal=false\n" +
                "Categories=Audio;AudioVideo;\n"
            )
            File(appDir, "usr/share/icons/hicolor/256x256/apps").mkdirs()
            linuxIcon.copyTo(File(appDir, "usr/share/icons/hicolor/256x256/apps/$linuxPackageName.png"), overwrite = true)

            // 6. Run the pinned linuxdeploy (extract-and-run: no FUSE needed on the build host).
            //    The output dir sits next to the AppDir on local disk (step 7 transfers the AppImage
            //    to binaries/).
            val outDir = File(workDir, "out")
            outDir.deleteRecursively()
            outDir.mkdirs()
            // The cached linuxdeploy may predate the exec-bit fix in downloadLinuxdeploy (or have been
            // copied without it): make sure it is runnable before launching.
            if (!linuxdeployFile.canExecute()) {
                if (!linuxdeployFile.setExecutable(true, false)) {
                    throw GradleException("Could not make linuxdeploy executable: ${linuxdeployFile.absolutePath}")
                }
            }
            // The pinned linuxdeploy (1-alpha) selects the AppImage bundle through its output plugin
            // (`-o appimage`, not the old `--file-type`) and writes the produced AppImage to the
            // process's working directory (there is no output-dir flag) — so run it from outDir,
            // which step 7 then scans for the single .AppImage.
            //
            // `--exclude-library` keeps the app's OWN libs (the JRE under usr/lib/runtime, the
            // embedded VLC runtime under usr/lib/app/resources/vlc) out of the top-level usr/lib/
            // where linuxdeploy "deploys" the libraries its scanner resolves: the scanner walks
            // every ELF in the AppDir — JRE included — and flattens their inter-references there.
            // That flattening breaks the app: the jpackage launcher dlopens libjvm.so, and the
            // $ORIGIN/../lib RUNPATH linuxdeploy sets on the launcher resolves the flattened
            // usr/lib/libjvm.so copy before the real JRE at usr/lib/runtime — the launcher then
            // derives a bogus JRE root and dies with `could not open <AppDir>/usr/lib/jvm.cfg`.
            // Excluding the app's own libs leaves only the true system deps deployed.
            // NOTE: linuxdeploy matches the pattern against the file NAME only
            // (core/appdir.cpp: isInExcludelist(path.filename(), ...)) — never the full path — so
            // pass the bare names of every JRE lib (enumerated, not hard-coded: the jlink output
            // can change between JDKs) + the 3 unversioned/sonamed VLC runtime libs.
            val excludedLibNames = File(appDir, "usr/lib/runtime").walkTopDown()
                .filter { it.isFile && it.name.contains(".so") }
                .map { it.name }
                .toSet() +
                vlcLinuxRuntimeFiles.filter { it.contains(".so") && !it.contains("/") }
            val excludeLibrary = excludedLibNames.flatMap { listOf("--exclude-library", it) }
            val builder = ProcessBuilder(
                listOf(linuxdeployFile.absolutePath, "--appimage-extract-and-run",
                    "--appdir", appDir.absolutePath) + excludeLibrary + listOf("--output", "appimage"),
            ).directory(outDir).redirectErrorStream(true)
            // linuxdeploy's build-time dependency scanner resolves each bundled ELF's NEEDED entries
            // and must NOT re-deploy the JRE or the VLC runtime from the system: the JRE's libs
            // reference each other across subdirs (lib/awt → lib/server/libjvm.so), and the rpath
            // stripped from the pinned VLC tarball means the plugins' libvlccore.so.9 resolves only
            // through the runtime dir the AppRun exports at run time. Mirror both on the scanner's
            // LD_LIBRARY_PATH so it sees them as already present; the exclusions above then keep
            // them out of the deployed (top-level) set. Only the true system deps land in usr/lib/
            // (the codec libs — note: alsa is blacklisted by linuxdeploy by design, the host's
            // audio stack stays a host dependency, like WASAPI on Windows).
            val runtimeLib = File(appDir, "usr/lib/runtime/lib")
            val scannerDirs = listOf(
                File(appDir, "usr/lib/app/resources/vlc"),
                runtimeLib,
                File(runtimeLib, "server"),
            ).joinToString(":")
            val existing = builder.environment()["LD_LIBRARY_PATH"]
            builder.environment()["LD_LIBRARY_PATH"] =
                if (existing.isNullOrBlank()) scannerDirs else "$scannerDirs:$existing"
            // Pin the produced file name (the appimage output plugin env var; "LDAI" = the plugin's
            // prefix) so the frozen release name does not depend on the .desktop contents.
            builder.environment()["LDAI_OUTPUT"] = appImageFileName
            val process = builder.start()
            val output = process.inputStream.bufferedReader().readText()
            val code = process.waitFor()
            if (code != 0) {
                logger.error("linuxdeploy failed (exit $code):\n$output")
                throw GradleException("linuxdeploy failed (exit $code)")
            }
            // Guard against the JRE flattening (see the --exclude-library comment above): if a JRE
            // lib ends up in the top-level usr/lib, the launcher dlopens it instead of the real JRE
            // at usr/lib/runtime and dies with `could not open <AppDir>/usr/lib/jvm.cfg`. Fail the
            // build instead of shipping an AppImage that will not start.
            if (File(appDir, "usr/lib/libjvm.so").isFile) {
                throw GradleException(
                    "linuxdeploy flattened the JRE into the top-level usr/lib (usr/lib/libjvm.so) — " +
                        "the AppImage would not start; check the --exclude-library arguments (step 6)"
                )
            }

            // 7. Move the produced AppImage to binaries/. Its name is already the frozen release
            //    name (pinned via the output plugin's env var in step 6), so check that exact file
            //    rather than scanning the dir. The move is cross-device (local outDir → the repo
            //    build dir), so renameTo is tried first with a copy fallback.
            val produced = File(outDir, appImageFileName)
            if (!produced.isFile) {
                val found = outDir.listFiles()?.joinToString { it.name } ?: "(unreadable)"
                throw GradleException("linuxdeploy produced no AppImage: expected ${produced.absolutePath} (dir contents: $found)")
            }
            binaries.mkdirs()
            val target = File(binaries, appImageFileName)
            if (target.exists()) target.delete()
            if (!produced.renameTo(target)) {
                produced.copyTo(target)
                produced.delete()
            }
            logger.lifecycle("AppImage built: ${target.absolutePath}")
            // The release chore publishes the AppImage's SHA-256 next to its name: log it here so it
            // can be copied straight from the build output.
            logger.lifecycle("AppImage SHA-256: ${sha256Of(target)}")
        }
    }
}
