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

compose.desktop {
    application {
        mainClass = "app.n_zik.compagnon.MainKt"
        nativeDistributions {
            // build/vlc-runtime/windows-x64/vlc → <compose.application.resources.dir>/vlc, under
            // `run` as in `createDistributable`.
            appResourcesRootDir.set(vlcRuntimeRoot)
        }
    }
}

tasks.matching { it.name == "prepareAppResources" }.configureEach { dependsOn(extractVlc) }
