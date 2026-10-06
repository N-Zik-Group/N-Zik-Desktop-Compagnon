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

// The embedded VLC runtime is Windows-only: on a non-Windows host the app plays through the system libvlc
// (spec `spec-linux-system-libvlc`), so the win64 zip is neither downloaded nor extracted.
val isWindowsHost = System.getProperty("os.name", "").startsWith("Windows", ignoreCase = true)

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
// No Linux package embeds VLC: the app plays through the system libvlc (see VlcRuntime), so the .deb,
// the .rpm and the AUR entries declare `vlc` as a dependency — the package manager installs it. The
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
}
