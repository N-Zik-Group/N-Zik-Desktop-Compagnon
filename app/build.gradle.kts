import org.gradle.api.tasks.bundling.Zip
import org.gradle.api.tasks.testing.Test
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.compose.desktop.application.tasks.AbstractJPackageTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Paths
import java.security.MessageDigest
import java.util.Base64

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

// ---- Build channel (spec `spec-updater`, AD-1) -------------------------------------------------------
//
// The channel is a Gradle build property: `-Pchannel=debug|stable|beta|dev|git`. Absent → debug
// for `:app:run` / plain compilation (dev iteration: DEBUG badge, updater off). Every packaging
// task requires an explicit channel (fail-loud): a release artifact is never made without saying
// which channel it is — the mobile build-type discipline, as a build property. The channel suffix
// lives ONLY in the artifact file names, the generated in-app version and the GitHub tag
// (`v{base}`, `v{base}-beta`, `v{base}-dev-YYYYMMDD`): the jpackage version stays numeric, so the
// stable artifact names remain byte-identical to the no-channel convention (the AUR release entry
// stays valid).

val channel = providers.gradleProperty("channel").orNull ?: "debug"

// Which channel each packaging task accepts (AD-1): the seven packaging tasks take stable|beta|dev;
// `createDistributable` + the portable zip additionally take `git` (AUR-git / source builds — no
// release asset, updater off, badge "GIT").
val packagingTaskChannels = mapOf(
    "packageExe" to listOf("stable", "beta", "dev"),
    "packageDeb" to listOf("stable", "beta", "dev"),
    "packageRpm" to listOf("stable", "beta", "dev"),
    "packageLinuxPortable" to listOf("stable", "beta", "dev", "git"),
    "packageFlatpak" to listOf("stable", "beta", "dev"),
    "packageArch" to listOf("stable", "beta", "dev"),
    "packageInstaller" to listOf("stable", "beta", "dev"),
    "createDistributable" to listOf("stable", "beta", "dev", "git"),
)

check(channel in packagingTaskChannels.values.flatten() + "debug") {
    "Unknown -Pchannel '$channel' (allowed: debug, stable, beta, dev, git)"
}
for (taskName in gradle.startParameter.taskNames.map { it.substringAfterLast(':') }.toSet()) {
    packagingTaskChannels[taskName]?.let { allowed ->
        check(channel in allowed) {
            "$taskName requires an explicit -Pchannel: ${allowed.joinToString("|")} " +
                "(the channel decides the artifact names and the in-app version; run/compile default to debug)"
        }
    }
}

// The channel suffix (AD-2): empty for stable/debug, `-beta`, `-dev-<build date>` or
// `-git-<8-char commit hash>` (a git checkout is required — outside one the build fails loudly).
val baseVersion = libs.versions.nzikVersionName.get()
val channelSuffix = when (channel) {
    "stable", "debug" -> ""
    "beta" -> "-beta"
    "dev" -> "-dev-" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
    else -> "-git-" + gitShortHash8()
}
val appVersionName = baseVersion + channelSuffix

// The channel display name (spec `spec-updater`, AD-8): the per-channel product name — the
// jpackage app name (the Windows install directory name, the Linux launcher and the Flatpak
// `exec` all derive from it), the .desktop `Name=` and the in-app window title.
// Stable / debug / -git keep the plain catalog name; beta and dev carry the channel plain and
// uppercase (the in-app badge convention, "BETA" / "DEV"). The published .exe file name does
// NOT use it: the CI renames the jpackage output to the channel's Linux base.
val channelDisplayName = when (channel) {
    "beta" -> "${libs.versions.nzikPackageName.get()} BETA"
    "dev" -> "${libs.versions.nzikPackageName.get()} DEV"
    else -> libs.versions.nzikPackageName.get()
}

/**
 * `git rev-parse --short=8 HEAD` of this checkout: the in-app identity of a `-git` build.
 * Outside a git checkout the build fails with an explicit message (spec matrix CHANNEL_GIT).
 */
fun gitShortHash8(): String {
    val process = try {
        ProcessBuilder("git", "rev-parse", "--short=8", "HEAD")
            .directory(project.projectDir)
            .redirectErrorStream(true)
            .start()
    } catch (e: Exception) {
        throw GradleException(
            "-Pchannel=git requires a git checkout: the `git` executable could not be started " +
                "(${e::class.simpleName}: ${e.message}) — build from a git working copy " +
                "(the in-app version embeds the commit hash)",
            e,
        )
    }
    val output = process.inputStream.bufferedReader().readText().trim()
    val code = process.waitFor()
    if (code != 0 || output.isEmpty()) {
        throw GradleException(
            "-Pchannel=git requires a git checkout: `git rev-parse --short=8 HEAD` failed — " +
                "build from a git working copy (the in-app version embeds the commit hash)"
        )
    }
    return output
}

// ---- Generated in-app version (spec `spec-updater`, AD-2) -------------------------------------------
//
// The source tree carries no version: a task generates `AppVersion.kt` (build dir, never committed)
// from the catalog + the channel, wired into the main source set. The app reads it at runtime
// (version display, the channel badge, the updater gate, the update check).
val appVersionFile = layout.buildDirectory.file(
    "generated/source/appVersion/main/app/n_zik/compagnon/generated/AppVersion.kt",
)

val generateAppVersion = tasks.register("generateAppVersion") {
    val output = appVersionFile
    inputs.property("channel", channel)
    inputs.property("versionName", appVersionName)
    inputs.property("versionCode", libs.versions.nzikVersionCode.get())
    outputs.file(output)
    doLast {
        output.get().asFile.parentFile.mkdirs()
        output.get().asFile.writeText(
            """
            |package app.n_zik.compagnon.generated
            |
            |/**
            | * Generated by the `generateAppVersion` Gradle task — do not edit or commit.
            | *
            | * The in-app identity of this build (spec `spec-updater`, AD-2): the catalog's base
            | * version + the channel suffix. Only the artifact file names, this object and the
            | * GitHub tag carry the suffix — jpackage keeps the numeric base version.
            | */
            |object AppVersion {
            |    /** The displayed version: base + channel suffix (e.g. "0.0.1-beta", "0.0.1-dev-20261007"). */
            |    const val versionName: String = "$appVersionName"
            |    /** The catalog's `nzikVersionCode` (release discipline: the changelog file name). */
            |    const val versionCode: Int = ${libs.versions.nzikVersionCode.get()}
            |    /** The build channel: debug / stable / beta / dev / git. */
            |    const val channel: String = "$channel"
            |    /** The in-app updater is enabled on stable / beta / dev only: debug and -git are
            |     |  source builds (no check at startup, the settings entry is forced disabled — anti-downgrade). */
            |    const val updaterEnabled: Boolean = ${channel in listOf("stable", "beta", "dev")}
            |}
            """.trimMargin()
        )
    }
}

kotlin {
    sourceSets.main {
        kotlin.srcDir(layout.buildDirectory.dir("generated/source/appVersion/main"))
    }
}

tasks.named<KotlinCompile>("compileKotlin") {
    dependsOn(generateAppVersion)
}

// The channel artifact rename (spec `spec-updater`, AD-2 / AD-8, loop 2) — ONE helper, applied by
// every packaging task: the channel suffix is inserted right after the base version in the
// artifact name ("n-zik-desktop-compagnon-0.0.1-linux-portable.zip" →
// "n-zik-desktop-compagnon-0.0.1-beta-linux-portable.zip"). An empty suffix (stable, the debug
// default) returns the name unchanged — the stable artifact names stay byte-identical to the
// no-channel convention (the AUR release entry stays valid). Two loop-2 hardenings:
//   * the base version is matched on a BOUNDARY — the next character is `.`, `-`, `_` or the
//     end of the name — so a base version that is a prefix of a longer version number
//     ("0.0.1" in "0.0.11-…") is never hit in the middle of a number;
//   * an artifact that ALREADY carries the channel suffix right after the base version is
//     rejected (`canBeChannelRenamed` returns false): a stale dated dev file
//     ("…-0.0.2-dev-20261006…") must not be re-suffixed into a corrupted
//     ("…-0.0.2-dev-20261007dev-20261006…").
fun versionBoundaryIndex(fileName: String, base: String): Int {
    var from = 0
    while (true) {
        val index = fileName.indexOf(base, from)
        if (index < 0) return -1
        val next = fileName.getOrNull(index + base.length)
        if (next == null || next == '.' || next == '-' || next == '_') return index
        from = index + 1
    }
}

/** The marker of "already suffixed", right after the bounded base version ("" for stable/debug). */
val channelSuffixPrefix = when (channel) {
    "dev" -> "-dev-"
    "git" -> "-git-"
    else -> channelSuffix
}

fun canBeChannelRenamed(fileName: String): Boolean {
    if (channelSuffix.isEmpty()) return true
    val index = versionBoundaryIndex(fileName, baseVersion)
    if (index < 0) return false
    return channelSuffixPrefix.isEmpty() ||
        !fileName.startsWith(channelSuffixPrefix, index + baseVersion.length)
}

fun channelArtifactName(fileName: String): String {
    if (channelSuffix.isEmpty()) return fileName
    check(canBeChannelRenamed(fileName)) {
        "channel rename: '$fileName' is not a plain-base artifact to rename " +
            "(the bounded base version '$baseVersion' is missing, or the file already carries the channel suffix)"
    }
    val index = versionBoundaryIndex(fileName, baseVersion)
    return fileName.substring(0, index + baseVersion.length) + channelSuffix + fileName.substring(index + baseVersion.length)
}

// The embedded VLC runtime is built for Windows from the official VideoLAN zip, and for the Linux
// Flatpak from the pinned Linux tarball (produced one-shot by
// scripts/build-vlc-linux-tarball.sh, spec `spec-linux-flatpak`). The other Linux install paths
// (.deb/.rpm/AUR/portable) play through the system libvlc (spec `spec-linux-system-libvlc`): no
// runtime is downloaded for them.
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
    // In-app updater changelog translation (spec `spec-updater`, loop 2 — the single sanctioned
    // new dependency, the phone's same version): the `translator` jar ships no POM (transitive
    // deps undeclared), so its hard OkHttp runtime requirement is declared explicitly.
    implementation(libs.okhttp3.okhttp)
    implementation(libs.translator)
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

// ---- Windows NSIS installer (spec `spec-updater` — the user decision: NSIS replaces the
// jpackage self-extracting installer; the record is the spec's Change Log) ----
//
// `:app:packageExe` (jpackage) builds the APP-IMAGE at `compose/binaries/main/app/<product>/`
// (the launcher + app/ + runtime/ with the embedded VLC) — plus the jpackage self-extracting
// exe, which is now a build BYPRODUCT, no longer distributed. This task runs makensis on the
// committed installer source of truth (`packaging/windows/installer.nsi`) with the per-channel
// defines and writes the final installer at the SAME output path as the jpackage exe (it
// overwrites the byproduct): `<product>-<version>.exe` (e.g.
// "N-Zik Desktop Compagnon DEV-0.0.1-dev-20261007.exe").
//
// NSIS 3.x toolchain: on CI the official zip ships EMBEDDED in the repo (tools/nsis/ — the
// windows-latest runner migrated to Windows Server 2025, which dropped the NSIS that the
// Windows 2022 image preinstalled; the channel workflows set NSIS_DIR to the SHA-verified
// extracted copy — nsis-updater.yml opens the update bot PR when the official SourceForge
// project ships a newer version, spec `spec-nsis-updater-ci`); on a dev machine per-user
// under `%LOCALAPPDATA%\Programs\NSIS\nsis-3.10` (the official portable zip — the setup.exe
// manifests requireAdministrator, so it is extracted, not installed) or the machine defaults;
// the `NSIS_DIR` environment variable (the NSIS root) wins when set. Branding is the window
// icon only (MUI_ICON / MUI_UNI_ICON) — plain MUI, no header image.
fun findMakensis(): File {
    val localAppData = System.getenv("LOCALAPPDATA").orEmpty()
    val candidates = listOfNotNull(
        providers.environmentVariable("NSIS_DIR").orNull?.let { File(it, "makensis.exe") },
        File("C:/Program Files (x86)/NSIS/makensis.exe"),
        File("C:/Program Files/NSIS/makensis.exe"),
        File(localAppData, "Programs/NSIS/nsis-3.10/Bin/makensis.exe"),
        File(localAppData, "Programs/NSIS/makensis.exe"),
    )
    return candidates.firstOrNull { it.isFile } ?: error(
        "NSIS (makensis.exe) not found — install NSIS 3.x (the official portable zip, per-user — " +
            "see rules/BUILD.md) or set the NSIS_DIR environment variable to the NSIS root",
    )
}

tasks.register<Exec>("packageInstaller") {
    // Windows-only (makensis); an explicit -Pchannel is enforced by the packagingTaskChannels map
    onlyIf { isWindowsHost }
    dependsOn("packageExe")
    val appDir = layout.buildDirectory.dir("compose/binaries/main/app/$channelDisplayName").get().asFile
    val outDir = layout.buildDirectory.dir("compose/binaries/main/exe").get().asFile
    val outFile = File(outDir, "$channelDisplayName-$appVersionName.exe")
    // A non-numeric version catalog entry must fail with a clear message, not a raw
    // NumberFormatException deeper in the pipeline
    check(baseVersion.matches(Regex("\\d+(\\.\\d+)*"))) {
        "base version '$baseVersion' must be numeric dot-separated (e.g. 0.0.1)"
    }
    val versionParts = baseVersion.split(".").map { it.toInt() }
    // The numeric "build" of the registry version identity: the dev build date (channelSuffix
    // "-dev-<date>"), 0 for the other channels
    val versionBuild = if (channel == "dev") channelSuffix.removePrefix("-dev-") else "0"
    val nsisScript = layout.projectDirectory.file("packaging/windows/installer.nsi")
    inputs.file(nsisScript)
    inputs.dir(appDir)
    outputs.file(outFile)
    doFirst {
        check(appDir.isDirectory) {
            "the jpackage app-image is missing: $appDir — run :app:packageExe -Pchannel=$channel first"
        }
        logger.lifecycle("NSIS installer → $outFile")
        // makensis is resolved AT EXECUTION TIME (not at configuration time — a build on a
        // machine without NSIS must stay green); doFirst runs before the Exec task starts the
        // process, so the command line can be set here
        commandLine(
            findMakensis(),
            "/DPRODUCT_NAME=$channelDisplayName",
            "/DCHANNEL=$channel",
            "/DAPP_VERSION=$appVersionName",
            "/DVERSION_MAJOR=${versionParts[0]}",
            "/DVERSION_MINOR=${versionParts[1]}",
            "/DVERSION_PATCH=${versionParts.getOrNull(2) ?: 0}",
            "/DVERSION_BUILD=$versionBuild",
            "/DAPPIMAGE_DIR=${appDir.absolutePath}",
            "/DLAUNCHER_NAME=$channelDisplayName.exe",
            "/DOUT_FILE=${outFile.absolutePath}",
            "/DICON_FILE=${project.file("../assets/design/icon.ico").absolutePath}",
            "/DVENDOR=N-Zik Group",
            "/DDESCRIPTION=Desktop companion for N-Zik: control your phone's library and playback from a large screen, and listen on your computer.",
            // AppInfo.NAME — the app data dir name AND the Windows Credential Manager target
            // (NOT channel-suffixed: one shared data dir across channels — verified against
            // `CredentialStore.appDirectory()` / `WindowsCredentialSecretStore`)
            "/DDATA_DIR_NAME=${libs.versions.nzikPackageName.get()}",
            "/DCRED_TARGET=${libs.versions.nzikPackageName.get()}",
            nsisScript,
        )
    }
}

compose.resources {
    packageOfResClass = "app.n_zik.compagnon.generated.resources"
    publicResClass = false
    generateResClass = always
}

// ---- Unescaped strings (user report 2026-10-07: visible Android escaping in the UI) ---------------
//
// The Crowdin-managed `values*/strings.xml` files carry the phone's Android escaping convention
// (`\'` for an apostrophe, `\"` for a quote, `\\` for a backslash — 116 occurrences across 31
// locale files). aapt2 unescapes them at Android compile time, but the Compose desktop resource
// pipeline does NOT — the backslash renders in the UI (« c\'est »). Fix at BUILD TIME, in two
// tasks:
//
//  * `unescapeStringsXml` — generates unescaped COPIES of every `values*/strings.xml` into a
//    generated dir (asserted on by UnescapedStringsTest; the source files are NEVER modified —
//    `values/` and `values-*` are Crowdin-managed and a future import would re-escape them);
//  * `unescapeCompiledResources` — the actual FIX, applied AFTER the compose resource
//    processing: the plugin's FINAL values merge (assembleMainResources →
//    `assembledResources/.../values*/strings.main.cvr`, the compiled values the runtime reads)
//    keeps the ORIGINAL escaped values. Empirical merge-order check (2026-10-07): the
//    unescaped copies registered as an additional res dir (sourceSets srcDir, then the
//    `ResourcesExtension.customDirectory` API) do NOT win — the original values stay in the
//    compiled `.cvr`. So, per the spec, the fallback is used: the compiled values are
//    post-processed in place, before `processResources` copies them to `build/resources`
//    (from which :app:run, the tests, the jar and the packaged apps all take them).
val unescapedResourcesDir = layout.buildDirectory.dir("generated/composeResourcesUnescaped")

/**
 * The pure left-to-right pass of the Android string unescape: `\'` → `'`, `\"` → `"`,
 * `\\` → `\`; a backslash before any other character (or a trailing backslash) is kept as-is.
 * Single pass, so a source `\\'` becomes `\'` (an escaped backslash followed by a plain
 * quote — NOT a re-escaped quote). The JVM tests carry a semantic copy of this function
 * (build scripts cannot call test sources); UnescapedStringsTest pins the build's
 * implementation on the real generated files.
 */
fun unescapeAndroidStringEscapes(value: String): String {
    val out = StringBuilder(value.length)
    var i = 0
    while (i < value.length) {
        if (value[i] == '\\' && i + 1 < value.length) {
            when (value[i + 1]) {
                '\\' -> { out.append('\\'); i += 2 }
                '\'' -> { out.append('\''); i += 2 }
                '"' -> { out.append('"'); i += 2 }
                else -> { out.append(value[i]); i += 1 }
            }
        } else {
            out.append(value[i])
            i += 1
        }
    }
    return out.toString()
}

/** Unescape the value of every `<string>` element of a values XML (structure left untouched). */
fun unescapeAndroidStrings(xml: String): String {
    val stringElement = Regex("(<string name=\"[^\"]+\"[^>]*>)(.*?)(</string>)", RegexOption.DOT_MATCHES_ALL)
    return stringElement.replace(xml) { m ->
        m.groupValues[1] + unescapeAndroidStringEscapes(m.groupValues[2]) + m.groupValues[3]
    }
}

val unescapeStringsXml = tasks.register("unescapeStringsXml") {
    description = "Generates unescaped copies of composeResources/values*/strings.xml (the Android escaping convention the desktop pipeline does not unescape)."
    val sourceRoot = project.file("src/main/composeResources")
    inputs.files(project.fileTree(sourceRoot) { include("values*/strings.xml") })
    outputs.dir(unescapedResourcesDir)
    doLast {
        val outRoot = unescapedResourcesDir.get().asFile
        outRoot.deleteRecursively()
        var count = 0
        project.fileTree(sourceRoot)
            .matching { include("values*/strings.xml") }
            .files
            .forEach { source ->
                val target = outRoot.resolve(source.relativeTo(sourceRoot).path)
                target.parentFile.mkdirs()
                target.writeText(unescapeAndroidStrings(source.readText()))
                count++
            }
        logger.lifecycle("unescapeStringsXml: $count strings.xml files unescaped into ${outRoot.path}")
    }
}

// The FIX: unescape the COMPILED values (the plugin's final merge kept the escaped originals —
// see the block above). The .cvr format is plain text, one `string|<name>|<base64>` line per
// value; only the base64 payload of the `string|` lines is rewritten (UTF-8 decode → unescape →
// re-encode). In place on purpose: `assembledResources` is the source `processResources` copies
// from (→ build/resources → :app:run / tests / jar / packaging), so there is no other injection
// point. (The in-place rewrite makes the pipeline re-run its two tasks each build — deliberate,
// ~1 s, and keeps the generated files in sync with the sources.)
val unescapeCompiledResources = tasks.register("unescapeCompiledResources") {
    description = "Unescapes the Android escaping in the compiled compose values (.cvr) under build/generated/compose/resourceGenerator — the plugin's final values merge keeps the original escaped values (empirical, 2026-10-07)."
    // BOTH generated trees are rewritten, not only `assembledResources`:
    //  * `assembledResources` is what `processResources` ships (→ build/resources → run/tests/jar/packaging);
    //  * `preparedResources` is scanned by `generateResourceAccessorsFor*` to compute each
    //    ResourceItem's byte OFFSET + SIZE into the .cvr (empirical, 2026-10-07: after the
    //    rewrite, the regenerated accessors carry the unescaped offsets). Rewriting only the
    //    assembled tree would leave the accessors pointing at the escaped layout (garbled
    //    runtime strings — the runtime skip()s to the offset and read()s the size).
    val preparedTree = layout.buildDirectory.dir("generated/compose/resourceGenerator/preparedResources")
    val assembledTree = layout.buildDirectory.dir("generated/compose/resourceGenerator/assembledResources")
    // The task post-processes the WHOLE tree (Main/Dev/Test in both stages) — depend on every
    // assembler that writes into it (Gradle's implicit-dependency validation is strict)
    dependsOn(
        tasks.named("assembleMainResources"),
        tasks.named("assembleDevResources"),
        tasks.named("assembleTestResources"),
    )
    // Inputs only — the fix is applied in place (see the block above): declaring the modified
    // files as outputs of two tasks would be a duplicate-output conflict. The bases are the two
    // cvr trees, NOT resourceGenRoot as a whole: Gradle's implicit-dependency overlap check
    // looks at the base location, and resourceGenRoot would cover kotlin/commonResClass (the
    // generateComposeResClass output).
    inputs.files(
        project.fileTree(preparedTree) { include("**/*.cvr") },
        project.fileTree(assembledTree) { include("**/*.cvr") },
    )
    doLast {
        val encoder = Base64.getEncoder()
        val linePattern = Regex("(?m)^string\\|([^|]*)\\|([A-Za-z0-9+/=]+)\\r?\\n?")
        var files = 0
        var values = 0
        (project.fileTree(preparedTree) { include("**/*.cvr") } +
            project.fileTree(assembledTree) { include("**/*.cvr") })
            .files
            .forEach { file ->
                val text = file.readText()
                var changed = false
                val updated = linePattern.replace(text) { m ->
                    val payload = m.groupValues[2]
                    val decoded = runCatching {
                        String(Base64.getDecoder().decode(payload), Charsets.UTF_8)
                    }.getOrNull() ?: return@replace m.value
                    val unescaped = unescapeAndroidStringEscapes(decoded)
                    if (unescaped == decoded) {
                        m.value
                    } else {
                        changed = true
                        values++
                        "string|${m.groupValues[1]}|${encoder.encodeToString(unescaped.toByteArray(Charsets.UTF_8))}" +
                            (if (m.value.endsWith("\r\n")) "\r\n" else if (m.value.endsWith("\n")) "\n" else "")
                    }
                }
                if (changed) {
                    file.writeText(updated)
                    files++
                }
            }
        logger.lifecycle("unescapeCompiledResources: $files compiled values files unescaped ($values values)")
    }
}

// The compiled values must be unescaped in BOTH generated trees:
//  * `assembledResources` is what `processResources` ships (→ build/resources → run/tests/jar);
//  * `preparedResources` is consumed by the accessor pipeline (see `recomputeResourceItemOffsets`).
listOf(
    "generateResourceAccessorsForMain",
    "generateResourceAccessorsForTest",
    "processResources",
).forEach { taskName ->
    tasks.named(taskName) {
        dependsOn(unescapeCompiledResources)
    }
}

// ---- Accessor offset repair (safety net: the accessors must match the unescaped cvr layout) ----
//
// Every generated ResourceItem carries a byte OFFSET + SIZE into the .cvr (empirical, 2026-10-07 —
// e.g. `ResourceItem(..., 12803, 167)`; the runtime `skip`s to the offset and `read`s the size,
// GitHub JetBrains/compose-multiplatform#4938). The compose plugin computes those offsets from the
// PREPARED .cvrs — so `unescapeCompiledResources` above rewrites BOTH generated trees: when the
// accessors are regenerated, they pick up the unescaped offsets (empirical: regenerating after the
// rewrite emits the unescaped layout; rewriting only `assembledResources` left the accessors
// pointing at the escaped layout — the runtime then reads misaligned chunks, garbled/truncated
// strings). This task is the safety net that keeps the two consistent no matter what: it re-points
// every ResourceItem that does not match the unescaped .cvr layout (line start → line end,
// excluding the trailing newline — the plugin's own convention: size 28 for a 29-byte line). In
// the steady state it is a no-op ("nothing to repair").
val recomputeResourceItemOffsets = tasks.register("recomputeResourceItemOffsets") {
    description = "Safety net: re-points the generated accessors' ResourceItem offsets/sizes at the unescaped .cvr layout (see the block above)."
    val resourceGenRoot = layout.buildDirectory.dir("generated/compose/resourceGenerator")
    dependsOn(
        unescapeCompiledResources,
        tasks.named("generateResourceAccessorsForMain"),
        tasks.named("generateResourceAccessorsForTest"),
    )
    // Inputs only — the fix is applied in place on the generated accessor sources (the same
    // deliberate pattern as unescapeCompiledResources: the pipeline re-runs one cycle each build).
    // The base locations are the individual sub-trees, NOT resourceGenRoot as a whole: Gradle's
    // implicit-dependency overlap check looks at the base location, and resourceGenRoot would
    // cover kotlin/commonResClass (the generateComposeResClass output).
    inputs.files(
        project.fileTree(layout.buildDirectory.dir("generated/compose/resourceGenerator/assembledResources")) { include("**/*.cvr") },
        project.fileTree(layout.buildDirectory.dir("generated/compose/resourceGenerator/kotlin/mainResourceAccessors")) { include("**/*.kt") },
        project.fileTree(layout.buildDirectory.dir("generated/compose/resourceGenerator/kotlin/devResourceAccessors")) { include("**/*.kt") },
        project.fileTree(layout.buildDirectory.dir("generated/compose/resourceGenerator/kotlin/testResourceAccessors")) { include("**/*.kt") },
    )
    doLast {
        val resourceGen = resourceGenRoot.get().asFile
        val patchedFiles = mutableMapOf<String, Int>()
        listOf(
            "mainResourceAccessors" to "Main",
            "devResourceAccessors" to "Dev",
            "testResourceAccessors" to "Test",
        ).forEach { (accessorDirName, setDirName) ->
            val accessorDir = resourceGen.resolve("kotlin").resolve(accessorDirName)
            val valuesRoot = resourceGen.resolve("assembledResources").resolve(setDirName)
                .resolve("composeResources").resolve("app.n_zik.compagnon.generated.resources")
            if (!accessorDir.isDirectory || !valuesRoot.isDirectory) return@forEach
            // (cvr relative path) -> (string name) -> (byte offset, byte size) of the value line
            val offsetMaps = mutableMapOf<String, Map<String, Pair<Long, Long>>>()
            valuesRoot.walkTopDown()
                .filter { it.extension == "cvr" }
                .forEach { cvrFile ->
                    val text = String(cvrFile.readBytes(), Charsets.UTF_8)
                    val nameToRange = mutableMapOf<String, Pair<Long, Long>>()
                    var offset = 0L
                    for (line in text.split("\n")) {
                        if (line.startsWith("string|")) {
                            val nameEnd = line.indexOf('|', 7)
                            nameToRange[line.substring(7, nameEnd)] = offset to line.length.toLong()
                        }
                        offset += (line.length + 1).toLong() // LF (the plugin writes LF)
                    }
                    // The generated accessors use '/' separators (values-fr/strings.main.cvr)
                    offsetMaps[cvrFile.relativeTo(valuesRoot).path.replace('\\', '/')] = nameToRange
                }
            // Line shape: ResourceItem(setOf(LanguageQualifier("fr"), ), "${MD}values-fr/strings.main.cvr", 12859, 167),
            // groups: 1 = cvr path (with the ${MD} prefix), 2 = old offset, 3 = old size
            val itemPattern = Regex("ResourceItem\\(.*?, \"([^\"]+\\.cvr)\", (\\d+), (\\d+)\\)")
            val namePattern = Regex("StringResource\\(\"string:([^\"]+)\",")
            accessorDir.walkTopDown()
                .filter { it.extension == "kt" }
                .forEach { ktFile ->
                    val raw = ktFile.readText()
                    val lineEnding = if (raw.contains("\r\n")) "\r\n" else "\n"
                    var currentName: String? = null
                    var changed = false
                    var patched = 0
                    val updated = raw.split(lineEnding).joinToString(lineEnding) { line ->
                        namePattern.find(line)?.let { currentName = it.groupValues[1] }
                        val m = itemPattern.find(line) ?: return@joinToString line
                        val name = currentName ?: return@joinToString line
                        val cvrRel = m.groupValues[1].removePrefix("\${MD}")
                        val range = offsetMaps[cvrRel]?.get(name) ?: return@joinToString line
                        val oldOffsets = "${m.groupValues[2]}, ${m.groupValues[3]}"
                        val newOffsets = "${range.first}, ${range.second}"
                        if (oldOffsets != newOffsets) {
                            changed = true
                            patched++
                            return@joinToString line.replace(oldOffsets, newOffsets)
                        }
                        line
                    }
                    if (changed) {
                        ktFile.writeText(updated)
                        patchedFiles[ktFile.name] = patched
                    }
                }
        }
        if (patchedFiles.isNotEmpty()) {
            logger.lifecycle("recomputeResourceItemOffsets: " +
                patchedFiles.entries.joinToString(", ") { "${it.key} (${it.value} items)" })
        } else {
            logger.lifecycle("recomputeResourceItemOffsets: nothing to repair")
        }
    }
}
// The dev source set's accessor task is registered by the compose plugin only after evaluation —
// a plain tasks.named(...) fails at configuration time with "not found in project ':app'".
afterEvaluate {
    tasks.named("generateResourceAccessorsForDev") {
        dependsOn(unescapeCompiledResources)
    }
    recomputeResourceItemOffsets {
        dependsOn(tasks.named("generateResourceAccessorsForDev"))
    }
}
// The compiled main sources must be built from the REPAIRED accessors (the generator's output
// directory is a Kotlin source root — without this edge compileKotlin could read the stale
// offsets before the repair runs).
tasks.named<KotlinCompile>("compileKotlin") {
    dependsOn(recomputeResourceItemOffsets)
}
// The generated copies are asserted on by the JVM tests (UnescapedStringsTest).
tasks.named<Test>("test") {
    dependsOn(unescapeStringsXml)
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

// ---- Embedded VLC runtime for the Linux Flatpak (libvlc 3.0.24, audio only) -----------------------
//
// VideoLAN publishes no prebuilt Linux runtime (source tarball + deb/rpm only), so the Flatpak's
// embedded runtime is produced one-shot by scripts/build-vlc-linux-tarball.sh from the official
// VideoLAN source (its own URL + SHA-256 are pinned in that script). The resulting tarball is
// pinned URL + SHA-256 in the version catalog (SEPARATE from the Windows pins, which are never
// touched), then handled exactly like the Windows zip: downloaded once into the Gradle user home
// (it survives `clean`), checked against the pinned SHA-256, and only the audio part extracted
// under build/vlc-runtime. No VLC binary is ever committed.
//
// The Flatpak is the ONLY Linux artifact that embeds VLC: the runtime is injected into its own
// staging at assembly time (packageFlatpak), never into the shared app-image — the
// .deb/.rpm/AUR/portable keep their system-vlc contract (`vlc` declared as a dependency, no
// resources/vlc).

// Gradle 9 exposes hyphenated catalog keys as nested accessors: vlc-linux-tarball-url is
// libs.versions.vlc.linux.tarball.url.
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
// that the Flatpak wrapper launcher exports on the runtime dir). `libvlc.so` is the unversioned
// name JNA looks for (the platform libvlc in VlcRuntime).
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
    // Linux host only — the embedded runtime feeds the Flatpak (see packageFlatpak).
    enabled = isLinuxHost
    if (!isLinuxHost) logger.lifecycle("downloadVlcLinux: skipped on this non-Linux host — the embedded Linux runtime feeds the Flatpak only (the other Linux paths use the system libvlc)")
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

// The app icon (committed binary, like `ic_banner2.png`): it rides in the installer, the app exe
// and the shortcuts. Pinned to the repository root so the path stays valid if the module moves.
val installerIcon = rootProject.file("assets/design/icon.ico")
// The Linux icon: jpackage needs a PNG for the .deb/.rpm (an .ico is not accepted). It is a one-shot
// conversion of icon.ico to a 256x256 RGBA PNG, committed like the others, and rides in the AUR packages.
val linuxIcon = rootProject.file("assets/design/icon-linux.png")

// The Linux package name (jpackage --linux-package-name): the deb/rpm file base name, the /opt
// install dir, the portable-zip base name and the AUR install identity, all derived from the single
// app identity (nzikPackageName) so none of them can drift from each other. The dev channel keeps
// its OWN package (spec `spec-updater`, AD-8): `n-zik-desktop-compagnon-dev`, installing to
// `/opt/n-zik-desktop-compagnon-dev` beside the stable one — the dev build is a parallel product,
// never a replacement of the stable installation. Pinned by LinuxPackagePinTest (exposed to the
// JVM tests as `linux.packageName`).
val linuxPackageName =
    libs.versions.nzikPackageName.get().lowercase().replace(' ', '-') +
        if (channel == "dev") "-dev" else ""

// The Flatpak app-id (spec spec-linux-flatpak, AD-1): the frozen Flatpak identity — the bundle's
// install name, the upgrade identity and the icon name all derive from it, so it must never
// change after the first release (a changed app-id would orphan every installed copy). The dev
// channel keeps its OWN app-id (spec `spec-updater`, AD-8): `com.nzik.desktop.compagnon.dev` —
// like its package name, the dev bundle is a parallel product, never a replacement of the
// stable one. Pinned by FlatpakPinTest (exposed to the JVM tests as `flatpak.appId`).
val flatpakAppId = if (channel == "dev") "com.nzik.desktop.compagnon.dev" else "com.nzik.desktop.compagnon"

// The Flatpak release file name (spec spec-linux-flatpak): the frozen x86_64 asset name, derived
// from the same package name + version as every other Linux artifact. Pinned by FlatpakPinTest.
// The channel suffix rides after the base version through the single rename helper (AD-2).
val flatpakFileName = channelArtifactName("${linuxPackageName}-${libs.versions.nzikVersionName.get()}-x86_64.flatpak")

// The Arch binary package (spec spec-arch-binary-package-release): the 5th Linux artifact — a pacman
// `.pkg.tar.zst` assembled from the SAME shared app-image as the other paths (no bundled VLC: the
// package declares `vlc` as a dependency, exactly like the .deb/.rpm/AUR). Its layout is the release
// entry's `package()` in packaging/aur/PKGBUILD (byte-identical by contract, pinned by
// ArchPkgPinTest against that file) + the provenance marker `distribution.txt` that ONLY this task
// adds (the PKGBUILDs carry it not — the marker is the single signal distinguishing a release-pkg
// install from an AUR install for the in-app updater). The channel rides in the package name
// (`-dev`) and in the artifact file name through the single rename helper (AD-2), like the other
// four. Pinned by ArchPkgPinTest (exposed to the JVM tests as `arch.*`).
val archMarkerContent = "github-release"
// The PKGINFO v2 `pkgver`: the FULL version — `<base>[-channel suffix]-<pkgrel>` (the release number
// is INSIDE the `pkgver`, as in makepkg — there is no `pkgrel` keyword; the dash is mandatory,
// libalpm rejects a version without one). Pkgrel 1 = the jpackage `appRelease` single source.
val archPkgver = appVersionName + "-1"
val archFileName = channelArtifactName("${linuxPackageName}-${baseVersion}-1-x86_64.pkg.tar.zst")
// The /usr/bin launcher: an absolute symlink to the app's bin/ launcher (the PKGBUILD's
// `ln -sf "$launcher" ...` — the .desktop Exec resolves through it, and the kernel resolves the
// symlink at exec time, which is exactly what the updater's live path resolution relies on).
val archSymlinkTarget = "/opt/$linuxPackageName/bin/$channelDisplayName"
// The PKGINFO `pkgdesc`: verbatim from the PKGBUILD (the layout's source of truth).
val archPkgDesc = "Desktop companion for N-Zik: control your phone's library and playback from a large screen, and listen on your computer."
val archUrl = "https://github.com/N-Zik-Group/N-Zik-Desktop-Compagnon"
// The .desktop menu entry: the PKGBUILD's verbatim (Name carries the channel display name — the
// jpackage launcher is named after the app name).
val archDesktopContent = listOf(
    "[Desktop Entry]",
    "Type=Application",
    "Name=$channelDisplayName",
    "Comment=$archPkgDesc",
    "Exec=/usr/bin/$linuxPackageName",
    "Icon=$linuxPackageName",
    "Terminal=false",
    "Categories=Audio;",
).joinToString("\n") + "\n"
// The .PKGINFO (PKGINFO v2, pacman >= 6.1 — the exact schema makepkg produces; PKGINFO(5)): only the
// two runtime values (`builddate`, `size`) are substituted by the task through the
// `__BUILDDATE__`/`__SIZE__` placeholders; everything else is static at write time.
val archPkginfoContent = listOf(
    "pkgname = $linuxPackageName",
    "pkgbase = $linuxPackageName",
    "xdata = pkgtype=pkg",
    "pkgver = $archPkgver",
    "pkgdesc = $archPkgDesc",
    "arch = x86_64",
    "url = $archUrl",
    "license = GPL-3.0-only",
    "depend = vlc",
    "builddate = __BUILDDATE__",
    "packager = N-Zik Group CI",
    "size = __SIZE__",
).joinToString("\n") + "\n"

// The committed Flatpak manifest template (spec spec-linux-flatpak): the reviewable, committed
// build recipe (transparency of the manual build — the bundle is built with the host's
// flatpak-builder, never from generated-only config). The build substitutes its three
// placeholders (__APP_ID__, __VERSION__, __LAUNCHER__) and reads the effective contract
// (runtime, command, finish-args) from it, so the template stays the single source of truth.
// Read eagerly: a broken template fails the build on every host, not just the Linux one.
val flatpakManifestTemplate = rootProject.file("packaging/flatpak/manifest.json")
val flatpakManifestJson = groovy.json.JsonSlurper().parseText(flatpakManifestTemplate.readText()) as Map<String, Any>
val flatpakRuntimeName = flatpakManifestJson["runtime"] as String
val flatpakRuntimeVersion = flatpakManifestJson["runtime-version"] as String
val flatpakCommand = flatpakManifestJson["command"] as String
val flatpakFinishArgs = (flatpakManifestJson["finish-args"] as List<*>).joinToString("\u001f")
// The libsecret module pin (spec spec-linux-flatpak): the keyring client library. The freedesktop
// runtime does not ship libsecret-1.so, so the bundle builds it from the pinned GNOME source —
// without the module the app silently falls back to the in-memory session store inside the
// sandbox (the token would not survive a restart). Read from the template like the rest of the
// contract, so a dropped or drifted module fails the contract test, not the user's keyring.
val flatpakLibsecretModule = (flatpakManifestJson["modules"] as List<*>).first { (it as Map<*, *>)["name"] == "libsecret" } as Map<*, *>
val flatpakLibsecretSource = (flatpakLibsecretModule["sources"] as List<*>).first() as Map<*, *>
val flatpakLibsecretUrl = flatpakLibsecretSource["url"] as String
val flatpakLibsecretSha256 = flatpakLibsecretSource["sha256"] as String

// The Flatpak wrapper's channel-independent env exports (spec spec-linux-flatpak, AD-3 — the
// 2026-10-08 flatpak audio/keyring fix). LD_LIBRARY_PATH lets libvlc.so's DT_NEEDED (the
// libvlccore soname) and the plugins' own DT_NEEDED resolve from the embedded runtime.
// VLC_PLUGIN_PATH points libvlc at the embedded plugin dir: libvlc_new() loads its mandatory
// modules (audio, clock, access) at instance creation, and in VLC 3.0.24 the --plugin-path CLI
// option is gone (the env var is the only lever) — without it libvlc_new returns NULL and every
// local playback fails. jna.library.path makes the bundled libsecret (staged in /app/lib by the
// libsecret module) resolvable by JNA's explicit search, independent of the sandbox's
// host-dependent ld cache: the unversioned libsecret-1.so is installed by meson, but it is found
// only through JNA's explicit path or the host ld cache, and that coverage is not guaranteed
// across flatpak versions (the 2026-10-08 keyring regression).
val flatpakWrapperExports = listOf(
    "export LD_LIBRARY_PATH=\"/app/lib/app/resources/vlc\${LD_LIBRARY_PATH:+:\$LD_LIBRARY_PATH}\"",
    "export VLC_PLUGIN_PATH=\"/app/lib/app/resources/vlc/plugins\${VLC_PLUGIN_PATH:+:\$VLC_PLUGIN_PATH}\"",
    "export JAVA_TOOL_OPTIONS=\"-Djna.library.path=/app/lib\${JAVA_TOOL_OPTIONS:+ \$JAVA_TOOL_OPTIONS}\"",
)

compose.desktop {
    application {
        mainClass = "app.n_zik.compagnon.MainKt"
        nativeDistributions {
            // Windows: `gradlew.bat :app:packageExe` builds the jpackage APP-IMAGE
            // (`compose/binaries/main/app/`) + the jpackage self-extracting exe — the latter is
            // now a build BYPRODUCT, not the distributed artifact: the Windows installer is NSIS
            // (the user decision — `:app:packageInstaller` runs makensis on
            // `packaging/windows/installer.nsi` over the app-image and overwrites the jpackage
            // exe at the same output path; spec `spec-updater` Change Log).
            // The portable `createDistributable` image is produced separately and is not affected.
            // Deb/Rpm are the Linux packages: on a Windows host the plugin disables them (the format
            // is not compatible with the current OS), so the Windows outputs stay identical.
            targetFormats(TargetFormat.Exe, TargetFormat.Deb, TargetFormat.Rpm)

            // Display identity of the app (version catalog, N-Zik Android convention): installer
            // name, Start menu group and the Control-Panel (uninstall) entry. The per-channel
            // product name (spec `spec-updater`, AD-8): beta and dev builds install as
            // "N-Zik Desktop Compagnon BETA" / "… DEV" — the jpackage app name drives the install
            // directory name (the Windows installer base), the Linux launcher and the Flatpak
            // `exec`, so the channel rides in the product identity, not only the file names
            // (the published .exe file name is the channel's Linux base instead — the CI renames
            // the jpackage output before the release upload).
            packageName = channelDisplayName
            description = "Desktop companion for N-Zik: control your phone's library and playback from a large screen, and listen on your computer."
            vendor = "N-Zik Group"
            copyright = "Copyright (C) 2026 N-Zik Group"

            windows {
                // jpackage exe: build BYPRODUCT only (the user decision — the NSIS installer
                // `packaging/windows/installer.nsi` is the distributed artifact; see the
                // `packageInstaller` task + the Change Log of `spec-updater.md`). Kept
                // machine-level (perUserInstall=false) so the byproduct stays consistent with the
                // NSIS global scope. The MSI-era frozen upgrade UUIDs are gone with the
                // self-extracting exe — the NSIS installer uses a registry-based per-channel ×
                // per-scope version identity instead (spec AD-8 superseded).
                perUserInstall = false
                dirChooser = true
                menu = true
                menuGroup = "N-Zik"
                shortcut = true
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
// None of the four Linux package paths (.deb/.rpm/AUR/portable) embeds VLC — the Flatpak
// (spec spec-linux-flatpak) is the deliberate exception: those four play through the system
// libvlc (see VlcRuntime), so the .deb, the .rpm and the AUR entries declare `vlc` as a
// dependency — the package manager installs it.
// The
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

    // The channel rename of the jpackage-produced artifacts (spec `spec-updater`, AD-2 / loop 2):
    // jpackage derives the file name itself (the .exe / .deb / .rpm — all carrying the base
    // version, in a different position per format), so each task renames its own output right
    // after the build through the single `channelArtifactName` helper (a no-op for stable).
    // Filtered by extension (the .deb and the .rpm may share the output tree under
    // `createDistributable`, and a stale artifact of the other format must never be renamed) and
    // by `canBeChannelRenamed`: the base version must sit on a version boundary (`.`, `-`, `_` or
    // end of name) AND the file must not already carry the channel suffix (no `-beta-beta`, no
    // re-suffixed `-dev-<date>dev-<other date>`).
    for ((taskName, extension) in listOf("packageExe" to ".exe", "packageDeb" to ".deb", "packageRpm" to ".rpm")) {
        tasks.named<AbstractJPackageTask>(taskName) {
            doLast {
                val dir = destinationDir.get().asFile
                dir.listFiles()
                    ?.filter {
                        it.isFile && it.name.endsWith(extension) && canBeChannelRenamed(it.name)
                    }
                    ?.forEach { file ->
                        val target = channelArtifactName(file.name)
                        if (target != file.name) {
                            val renamed = File(dir, target)
                            if (renamed.exists()) renamed.delete()
                            check(file.renameTo(renamed)) {
                                "channel rename: could not rename ${file.name} → $target"
                            }
                            logger.lifecycle("channel rename: ${file.name} → $target")
                        }
                    }
            }
        }
    }

    // Expose the effective Linux packaging contract to the JVM tests (same pattern as
    // `windows.packageName`): LinuxPackagePinTest fails if a plugin upgrade stops forwarding these
    // freeArgs (the `vlc` dependency + the Audio menu group are the core of the Linux packages) or
    // if the package name drifts from the portable zip's file name. Read eagerly here: this
    // afterEvaluate block runs after the plugin's (the tasks exist and the freeArgs are injected
    // above), and Test.systemProperty stores the value as-is (no Provider resolution).
    tasks.named<Test>("test") {
        // The channel contract (spec `spec-updater`, AD-1/AD-2): the effective channel, the
        // generated in-app version and the updater gate (the artifact names below already carry
        // the channel suffix — with the default debug channel they stay byte-identical to the
        // no-channel convention, so the pin tests keep passing unmodified).
        systemProperty("channel", channel)
        systemProperty("appVersion.versionName", appVersionName)
        systemProperty("appVersion.versionCode", libs.versions.nzikVersionCode.get())
        systemProperty("appVersion.updaterEnabled", (channel in listOf("stable", "beta", "dev")).toString())
        // The per-channel product name (spec `spec-updater`, AD-8): the window title + the Windows
        // package identity (the MSI-era upgrade UUIDs are gone with the NSIS installer — AD-8
        // superseded).
        systemProperty("windows.packageName", channelDisplayName)
        systemProperty("linux.packageName", linuxPackageName)
        // The effective per-task artifact names AFTER the channel rename (spec `spec-updater`,
        // AD-2 / loop 2 — the doLast hook above renames the jpackage outputs through
        // `channelArtifactName`): the jpackage-derived base names (the conventions pinned in
        // InstallModeDetectionTest) through that helper (a no-op for stable/debug).
        // `ChannelArtifactNameTest` pins the derivation against these (test-side copy of the
        // helper — build-script symbols are unreachable from the JVM tests).
        systemProperty("artifacts.exe", channelArtifactName("${channelDisplayName}-$baseVersion.exe"))
        systemProperty("artifacts.deb", channelArtifactName("${linuxPackageName}_$baseVersion-1_amd64.deb"))
        systemProperty("artifacts.rpm", channelArtifactName("${linuxPackageName}-$baseVersion-1.x86_64.rpm"))
        systemProperty("linux.freeArgs.deb", tasks.named<AbstractJPackageTask>("packageDeb").get().freeArgs.get().joinToString("\u001f"))
        systemProperty("linux.freeArgs.rpm", tasks.named<AbstractJPackageTask>("packageRpm").get().freeArgs.get().joinToString("\u001f"))
        systemProperty("linux.portable.zipName", tasks.named<Zip>("packageLinuxPortable").get().archiveFileName.get())
        // The shared pinned Linux VLC tarball (spec spec-linux-flatpak): URL + name + SHA-256.
        // The Flatpak is the only Linux artifact that embeds the runtime (injected into its own
        // staging — the other paths must never get a resources/vlc), so the pin is exposed under
        // a shared name rather than a per-artifact one.
        systemProperty("linux.tarballUrl", vlcLinuxTarballUrl)
        systemProperty("linux.tarballName", vlcLinuxTarballName)
        systemProperty("linux.tarballSha256", vlcLinuxTarballSha256)
        // The Flatpak contract (spec spec-linux-flatpak): the frozen app-id (AD-1), the base
        // runtime + version (AD-2), the finish-args (the sandbox contract: network = pairing
        // listener, SecretService = keyring, home = the data dir, …), the frozen bundle name and
        // the wrapper command — all read from the committed template above, so a template change
        // fails the test.
        systemProperty("flatpak.appId", flatpakAppId)
        systemProperty("flatpak.runtime", flatpakRuntimeName)
        systemProperty("flatpak.runtimeVersion", flatpakRuntimeVersion)
        systemProperty("flatpak.finishArgs", flatpakFinishArgs)
        systemProperty("flatpak.fileName", flatpakFileName)
        systemProperty("flatpak.command", flatpakCommand)
        systemProperty("flatpak.libsecretUrl", flatpakLibsecretUrl)
        systemProperty("flatpak.libsecretSha256", flatpakLibsecretSha256)
        // The Arch package contract (spec spec-arch-binary-package-release): the frozen layout
        // (the PKGBUILD's package() as a package, pinned against it by ArchPkgPinTest), the full
        // PKGINFO v2 (with the channel suffix in the pkgver — channel-aware, so `build.sh
        // package beta|dev` stays green), the provenance marker (path + content) and the frozen
        // artifact name. The two multi-line contents carry quotes/newlines: base64 keeps them
        // command-line-safe (the pin test decodes).
        systemProperty("arch.fileName", archFileName)
        systemProperty("arch.pkgname", linuxPackageName)
        systemProperty("arch.pkgver", archPkgver)
        systemProperty("arch.pkgdesc", archPkgDesc)
        systemProperty("arch.url", archUrl)
        systemProperty("arch.optRoot", "opt/$linuxPackageName")
        systemProperty("arch.symlinkPath", "usr/bin/$linuxPackageName")
        systemProperty("arch.symlinkTarget", archSymlinkTarget)
        systemProperty("arch.desktopPath", "usr/share/applications/$linuxPackageName.desktop")
        systemProperty("arch.desktopContent", Base64.getEncoder().encodeToString(archDesktopContent.toByteArray()))
        systemProperty("arch.iconPath", "usr/share/icons/hicolor/256x256/apps/$linuxPackageName.png")
        systemProperty("arch.licensePath", "usr/share/licenses/$linuxPackageName/LICENSE")
        systemProperty("arch.pkginfoContent", Base64.getEncoder().encodeToString(archPkginfoContent.toByteArray()))
        systemProperty("arch.markerPath", "opt/$linuxPackageName/distribution.txt")
        systemProperty("arch.markerContent", archMarkerContent)
        systemProperty("flatpak.wrapperExports", Base64.getEncoder().encodeToString(flatpakWrapperExports.joinToString("\u001f").toByteArray()))
    }

    // The portable Linux build is the jpackage app-image — the only Linux binary the AUR entry consumes.
    // `createDistributableImpl` is the plugin's hidden jpackage task that writes
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
        // Fail-loud boundary (spec spec-linux-flatpak): the shared app-image must never carry the
        // embedded VLC runtime — only the Flatpak staging does (packageFlatpak step 3, after this
        // boundary). A runtime leaking here would ride in the .deb/.rpm/AUR/portable under a green
        // build.
        if (appImage.resolve("lib/app/resources/vlc").exists()) {
            throw GradleException(
                "the shared app-image carries lib/app/resources/vlc ($appImage) — only the Flatpak may embed the VLC runtime, and into its own staging (never the shared app-image, whose other consumers — .deb/.rpm/AUR/portable — must stay on the system libvlc)"
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
        // The channel suffix rides after the base version through the single rename helper (AD-2).
        archiveFileName.set(channelArtifactName("${linuxPackageName}-${libs.versions.nzikVersionName.get()}-linux-portable.zip"))
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

    // The Flatpak (spec spec-linux-flatpak) — the 4th Linux artifact: the same app-image the
    // portable zip packages (one source of truth, kept free of the VLC runtime) + the embedded
    // Linux VLC runtime in lib/app/resources/vlc + the nzik wrapper launcher in the app's bin/
    // dir (a Flatpak .desktop cannot carry Env= — the wrapper exports the runtime dir on
    // LD_LIBRARY_PATH, the compiled libs have no rpath; bin/ is where the flatpak sandbox PATH
    // and the builder's finish step resolve the bare command — step 4) + the .desktop + icon,
    // staged under the Gradle user home (local disk — on the WSL build host the repo sits on
    // the 9P mount, where the long-lived daemon can serve a stale (empty) listing) and built
    // with the HOST's flatpak-builder + flatpak (apt tools — never pinned artifacts). Manual
    // build, no Flathub submission: the only Flathub involvement is downloading the base
    // runtime from the Flathub CDN (AD-2) — the bundle is distributed as a GitHub release
    // asset, like the five others.
    // Linux host only; on Windows it is skipped (lifecycle message), so gradlew.bat build stays green.
    tasks.register("packageFlatpak") {
        group = "n-zik"
        description = "Builds the x86_64 Flatpak bundle (app + JRE + embedded VLC) from the app-image via flatpak-builder."
        enabled = isLinuxHost
        if (!isLinuxHost) {
            logger.lifecycle("packageFlatpak: skipped on this non-Linux host — build it on a Linux/WSL host")
        }
        dependsOn(createDistributableImpl, extractVlcLinux)
        doLast {
            val binaries = layout.buildDirectory.dir("compose/binaries").get().asFile
            // The staging + the build dir live under the Gradle user home (local disk): the repo
            // is on the 9P mount on the WSL build host, where the long-lived daemon can serve a
            // stale (empty) listing of a dir another process just populated (a produced bundle
            // was lost that way once), and flatpak-builder does thousands of small file
            // ops. Only the finished bundle is transferred back to the repo (step 7).
            val workDir = File(gradle.gradleUserHomeDir, "caches/n-zik-compagnon/flatpak")

            // 1. Fail-loud host prerequisites (the flatpak tools are apt host tools, not pinned
            //    artifacts; the base runtime is downloaded from a configured remote on first use).
            fun isOnPath(binary: String): Boolean =
                runCatching {
                    val process = ProcessBuilder(binary, "--version").redirectErrorStream(true).start()
                    process.inputStream.readBytes()
                    process.waitFor()
                }.isSuccess
            if (!isOnPath("flatpak") || !isOnPath("flatpak-builder")) {
                throw GradleException(
                    "flatpak / flatpak-builder not found on the PATH — install them with: sudo apt install flatpak-builder"
                )
            }
            val remotesProcess = ProcessBuilder("flatpak", "remotes", "--columns=name").redirectErrorStream(true).start()
            val remotes = remotesProcess.inputStream.bufferedReader().readText()
                .lineSequence().filter { it.isNotBlank() }.toList()
            if (remotesProcess.waitFor() != 0 || remotes.isEmpty()) {
                throw GradleException(
                    "no flatpak remote is configured (the base runtime is downloaded from one) — add Flathub with: " +
                        "flatpak remote-add --user flathub https://dl.flathub.org/repo/flathub.flatpakrepo"
                )
            }

            // 2. Locate the app-image (the same dynamic detection as the portable zip) + the
            //    fail-loud boundary: the shared app-image must be free of the embedded runtime —
            //    this task injects it into the staging dir only (step 3, after this point), so a
            //    pre-existing resources/vlc here means a leak into the .deb/.rpm/AUR/portable
            //    artifacts, and the build fails instead of shipping it.
            val base = createDistributableImpl.get().destinationDir.get().asFile
            val appImages = base.listFiles { f, _ -> f.isDirectory }
                ?.filter { it.resolve("bin").isDirectory }
                ?: throw GradleException("app-image output dir is missing or not a directory: $base")
            require(appImages.size == 1) { "expected exactly one app-image directory (with a bin/) in $base, found ${appImages.size}" }
            val image = appImages.first()
            if (File(image, "lib/app/resources/vlc").exists()) {
                throw GradleException(
                    "the shared app-image carries lib/app/resources/vlc ($image) — only the Flatpak may embed the VLC runtime, and into its own staging (never the shared app-image, whose other consumers — .deb/.rpm/AUR/portable — must stay on the system libvlc)"
                )
            }

            // 3. Stage the app-image contents + the embedded runtime + the wrapper + the .desktop
            //    + the icon under <workDir>/app/ (the manifest's dir source). The build dir is a
            //    sub-dir (<workDir>/build) so --force-clean never wipes the staging or the
            //    generated manifest.
            val staging = File(workDir, "app")
            delete(staging)
            copy { from(image); into(staging) }
            // Inject the embedded VLC runtime into the app-image's resources dir — the one
            // VlcRuntime reads (the cfg sets -Dcompose.application.resources.dir=$APPDIR/resources,
            // i.e. /app/lib/app/resources in the Flatpak). Only the staging gets it: the shared
            // app-image (and therefore the .deb/.rpm/AUR/portable) stays free of resources/vlc.
            copy { from(vlcLinuxRuntimeRoot.get().dir("vlc")); into(File(staging, "lib/app/resources/vlc")) }

            // 4. The wrapper launcher, in the app's bin/ dir: a Flatpak .desktop cannot carry
            //    Env= (flatpak ignores it) and the embedded libs carry no rpath — so the wrapper
            //    exports the runtime dir on LD_LIBRARY_PATH and execs the jpackage launcher (whose
            //    path contains spaces — quoted). bin/ is mandatory, not cosmetic: the flatpak
            //    sandbox PATH puts /app/bin first (the bare Exec + command resolve to
            //    /app/bin/nzik), and flatpak-builder's finish step hard-fails when the command
            //    binary is not in <builddir>/files/bin/ ("Command 'nzik' not found").
            // The per-channel product name (spec `spec-updater`, AD-8): the jpackage launcher is
            // named after the app name, so the wrapper and the .desktop ride the same channel
            // identity as every other artifact.
            val displayName = channelDisplayName
            File(staging, "bin/$flatpakCommand").apply {
                writeText(
                    "#!/bin/sh\n" +
                        flatpakWrapperExports.joinToString("\n") + "\n" +
                        "exec \"/app/bin/$displayName\" \"\$@\"\n"
                )
                setExecutable(true, false)
            }

            // 5. The .desktop entry + the hicolor icon (both under /app/share: flatpak resolves
            //    the bare Exec command and the app-id icon name against them). No Version= key:
            //    per the desktop-entry spec it is the SPEC version (1.5), not the app version.
            //    Categories carries no AudioVideo main-category pair (the Flatpak menu allows it).
            File(staging, "share/applications").mkdirs()
            File(staging, "share/applications/$flatpakAppId.desktop").writeText(
                "[Desktop Entry]\n" +
                    "Type=Application\n" +
                    "Name=$displayName\n" +
                    "Comment=Desktop companion for N-Zik: control your phone's library and playback from a large screen, and listen on your computer.\n" +
                    "Exec=$flatpakCommand\n" +
                    "Icon=$flatpakAppId\n" +
                    "Terminal=false\n" +
                    "Categories=Audio;\n"
            )
            File(staging, "share/icons/hicolor/256x256/apps").mkdirs()
            linuxIcon.copyTo(File(staging, "share/icons/hicolor/256x256/apps/$flatpakAppId.png"), overwrite = true)

            // 6. Generate the manifest in the workdir from the committed template: only the three
            //    placeholders are substituted (transparency of the manual build — the recipe is
            //    reviewable without Gradle). __LAUNCHER__ is the channel display name: the jpackage
            //    app-image names its bin/ launcher after the app name, which carries the channel
            //    ("N-Zik Desktop Compagnon DEV" on dev — a stable-only hardcoded name here broke
            //    the dev build: chmod on the missing file, flatpak-builder exit 1).
            val manifest = File(workDir, "manifest.json")
            manifest.writeText(
                flatpakManifestTemplate.readText()
                    .replace("__APP_ID__", flatpakAppId)
                    .replace("__VERSION__", libs.versions.nzikVersionName.get())
                    .replace("__LAUNCHER__", displayName)
            )

            // 7. Build + bundle. The base runtime + the sdk are installed into the flatpak install
            //    dir (user scope — the documented prerequisite is a user remote, no root needed)
            //    before the build: `flatpak install` is idempotent, so an install already present
            //    at that version is left alone (AD-2: a download from the Flathub CDN at build
            //    time, once — and again on the target machine at the first install).
            val remote = remotes.first()
            val install = ProcessBuilder(
                "flatpak", "install", "--user", "-y", remote,
                "$flatpakRuntimeName//$flatpakRuntimeVersion",
                "org.freedesktop.Sdk//$flatpakRuntimeVersion"
            ).redirectErrorStream(true)
            val installProcess = install.start()
            val installOutput = installProcess.inputStream.bufferedReader().readText()
            val installCode = installProcess.waitFor()
            if (installCode != 0) {
                logger.error("flatpak install failed (exit $installCode):\n$installOutput")
                throw GradleException(
                    "could not install the base runtime ($flatpakRuntimeName $flatpakRuntimeVersion) from the " +
                        "'$remote' remote — check the remote hosts it (the documented setup is: " +
                        "flatpak remote-add --user flathub https://dl.flathub.org/repo/flathub.flatpakrepo)"
                )
            }
            val buildDir = File(workDir, "build")
            // --repo <builddir>/repo is required: without it (or --install) flatpak-builder 1.4.x
            // only stages the app in the build dir and never commits it to a repo, so there would
            // be nothing for build-bundle to package. build-export auto-creates the repo.
            val builder = ProcessBuilder(
                "flatpak-builder", "--force-clean",
                "--repo", File(buildDir, "repo").absolutePath,
                buildDir.absolutePath, manifest.absolutePath
            ).directory(workDir).redirectErrorStream(true)
            val builderProcess = builder.start()
            val builderOutput = builderProcess.inputStream.bufferedReader().readText()
            val builderCode = builderProcess.waitFor()
            if (builderCode != 0) {
                logger.error("flatpak-builder failed (exit $builderCode):\n$builderOutput")
                throw GradleException("flatpak-builder failed (exit $builderCode)")
            }
            // build-bundle takes REPO FILENAME APP-ID (the arch is passed explicitly — the frozen
            // x86_64 asset). The bundle is written to the process's working directory (there is
            // no output-dir flag), which is the workdir — step 8 then moves it to binaries/.
            val packer = ProcessBuilder(
                "flatpak", "build-bundle", File(buildDir, "repo").absolutePath,
                flatpakFileName, flatpakAppId, "--arch=x86_64"
            ).directory(workDir).redirectErrorStream(true)
            val packerProcess = packer.start()
            val packerOutput = packerProcess.inputStream.bufferedReader().readText()
            val packerCode = packerProcess.waitFor()
            if (packerCode != 0) {
                logger.error("flatpak build-bundle failed (exit $packerCode):\n$packerOutput")
                throw GradleException("flatpak build-bundle failed (exit $packerCode)")
            }

            // 8. Move the produced bundle to binaries/. Its name is already the frozen release
            //    name (passed to build-bundle), so check that exact file rather than scanning.
            //    The move is cross-device (local workdir → the repo build dir), so renameTo is
            //    tried first with a copy fallback.
            val produced = File(workDir, flatpakFileName)
            if (!produced.isFile) {
                val found = workDir.listFiles()?.joinToString { it.name } ?: "(unreadable)"
                throw GradleException("flatpak build-bundle produced no bundle: expected ${produced.absolutePath} (dir contents: $found)")
            }
            binaries.mkdirs()
            val target = File(binaries, flatpakFileName)
            if (target.exists()) target.delete()
            if (!produced.renameTo(target)) {
                produced.copyTo(target)
                produced.delete()
            }
            logger.lifecycle("Flatpak built: ${target.absolutePath}")
            // The release chore publishes the Flatpak's SHA-256 next to its name: log it here so
            // it can be copied straight from the build output.
            logger.lifecycle("Flatpak SHA-256: ${sha256Of(target)}")
        }
    }

    // The Arch binary package (spec spec-arch-binary-package-release) — the 5th Linux artifact:
    // the same shared app-image (one source of truth, kept free of the VLC runtime — boundary
    // below) assembled into a pacman `.pkg.tar.zst` with EXACTLY the release AUR entry's
    // package() layout (/opt/<pkg>, the /usr/bin symlink, the .desktop, the icon, the LICENSE,
    // `depends = vlc`) PLUS the provenance marker `distribution.txt` (`github-release`) at the
    // app root — the single signal the in-app updater uses to tell a release-pkg install from an
    // AUR install (the PKGBUILDs carry it not). No bundled VLC: the package declares `vlc` as a
    // dependency, exactly like the .deb/.rpm/AUR. The naming rides the channel through
    // `linuxPackageName` (the `-dev` base) + `channelArtifactName` (the file-name suffix).
    // Linux host only; on Windows it is skipped like packageFlatpak, so
    // gradlew.bat build stays green.
    tasks.register("packageArch") {
        group = "n-zik"
        description = "Builds the x86_64 pacman binary package (.pkg.tar.zst) from the app-image: PKGBUILD layout + distribution.txt marker."
        enabled = isLinuxHost
        if (!isLinuxHost) {
            logger.lifecycle("packageArch: skipped on this non-Linux host — build it on a Linux/WSL host")
        }
        dependsOn(createDistributableImpl)
        doLast {
            val binaries = layout.buildDirectory.dir("compose/binaries").get().asFile
            // The staging dir lives under the Gradle user home (local disk): the repo is on the 9P
            // mount on the WSL build host, where thousands of small staging file ops + a ~160 MB
            // tar are slow and the daemon can serve stale listings (the Flatpak reasoning).
            // Only the finished package is transferred back to the repo (step 7).
            val workDir = File(gradle.gradleUserHomeDir, "caches/n-zik-compagnon/arch-pkg")

            // 1. Fail-loud host prerequisite: `zstd` is the compressor behind `tar -I zstd`
            //    (GNU tar + the system zstd binary — apt host tool, not a pinned artifact). The
            //    check requires exit 0 of `--version`: a broken binary must not pass.
            fun exitCodeOf(binary: String): Int =
                runCatching {
                    val process = ProcessBuilder(binary, "--version").redirectErrorStream(true).start()
                    process.inputStream.readBytes()
                    process.waitFor()
                }.getOrDefault(-1)
            if (exitCodeOf("zstd") != 0) {
                throw GradleException(
                    "zstd not found on the PATH (or its --version failed) — install it with: sudo apt install zstd"
                )
            }

            // 2. Locate the app-image (the same dynamic detection as the portable zip /
            //    packageFlatpak) + the fail-loud boundary: the shared app-image must be free of
            //    the embedded runtime — the Arch package plays through the system libvlc (the
            //    `depend = vlc` in the .PKGINFO), so a resources/vlc leak would bundle VLC into
            //    the package and contradict the frozen "no bundled VLC" contract.
            val base = createDistributableImpl.get().destinationDir.get().asFile
            val appImages = base.listFiles { f, _ -> f.isDirectory }
                ?.filter { it.resolve("bin").isDirectory }
                ?: throw GradleException("app-image output dir is missing or not a directory: $base")
            require(appImages.size == 1) { "expected exactly one app-image directory (with a bin/) in $base, found ${appImages.size}" }
            val image = appImages.first()
            if (File(image, "lib/app/resources/vlc").exists()) {
                throw GradleException(
                    "the shared app-image carries lib/app/resources/vlc ($image) — the Arch package (like the .deb/.rpm/AUR) must stay on the system libvlc: only the Flatpak may embed the VLC runtime, and into its own staging"
                )
            }

            // 3. Stage the PKGBUILD's package() layout under pkgRoot: the app-image renamed to
            //    the lowercase package name in /opt (single /opt install location across all the
            //    Linux packages) + the provenance marker + the /usr/bin symlink (ABSOLUTE target,
            //    like the PKGBUILD's `ln -sf "$launcher" ...` — the kernel resolves it at exec
            //    time, which the updater's live path resolution relies on) + the .desktop + the
            //    icon + the LICENSE (the repo root copy — the PKGBUILD downloads the same file).
            val pkgRoot = File(workDir, "pkgroot")
            delete(pkgRoot)
            val optRoot = File(pkgRoot, "opt/$linuxPackageName")
            optRoot.mkdirs()
            copy { from(image); into(optRoot) }
            File(optRoot, "distribution.txt").writeText(archMarkerContent)
            File(pkgRoot, "usr/bin").mkdirs()
            Files.createSymbolicLink(
                File(pkgRoot, "usr/bin/$linuxPackageName").toPath(),
                Paths.get(archSymlinkTarget)
            )
            File(pkgRoot, "usr/share/applications").mkdirs()
            File(pkgRoot, "usr/share/applications/$linuxPackageName.desktop").writeText(archDesktopContent)
            File(pkgRoot, "usr/share/icons/hicolor/256x256/apps").mkdirs()
            linuxIcon.copyTo(File(pkgRoot, "usr/share/icons/hicolor/256x256/apps/$linuxPackageName.png"), overwrite = true)
            File(pkgRoot, "usr/share/licenses/$linuxPackageName").mkdirs()
            rootProject.file("LICENSE").copyTo(File(pkgRoot, "usr/share/licenses/$linuxPackageName/LICENSE"), overwrite = true)

            // 4. Post-staging self-checks — fail-loud BEFORE the tar, so a regression of the
            //    marker (e.g. written into pkgRoot instead of the app root), of the symlink
            //    target or of the launcher layout fails the build instead of shipping a package
            //    whose install would read "no marker" / broken Exec for the release-pkg users.
            val launcher = File(optRoot, "bin/$channelDisplayName")
            if (!launcher.isFile || !launcher.canExecute()) {
                throw GradleException("launcher ${launcher.path} not found (or not executable) in the app-image — the .desktop Exec would be broken")
            }
            val marker = File(optRoot, "distribution.txt")
            if (marker.readText().trim() != archMarkerContent) {
                throw GradleException("distribution.txt marker is missing or wrong in the staged app root (${marker.path}): expected '$archMarkerContent'")
            }
            val symlink = File(pkgRoot, "usr/bin/$linuxPackageName").toPath()
            if (!Files.isSymbolicLink(symlink) || Files.readSymbolicLink(symlink) != Paths.get(archSymlinkTarget)) {
                throw GradleException("the staged /usr/bin symlink does not point at $archSymlinkTarget (expected the absolute app launcher path)")
            }

            // 5. The .PKGINFO (PKGINFO v2): substitute the two runtime values (builddate = unix
            //    seconds, size = the staged payload total) and verify no placeholder survives.
            //    The size is the payload measured BEFORE the .PKGINFO is written — the ~1 KB of
            //    the file itself is not counted (pacman does not validate `size`: display only).
            val size = pkgRoot.walkTopDown().filter { it.isFile }.sumOf { it.length() }
            val pkginfo = archPkginfoContent
                .replace("__BUILDDATE__", (System.currentTimeMillis() / 1000).toString())
                .replace("__SIZE__", size.toString())
            if (Regex("__[A-Z0-9_]+__").containsMatchIn(pkginfo)) {
                throw GradleException("a __…__ placeholder survived the substitution in the .PKGINFO")
            }
            File(pkgRoot, ".PKGINFO").writeText(pkginfo)

            // 6. Deterministic tar (top-level bare entries — no `./` prefix, verified against
            //    pacman 7.1.0): sorted by name, owner/group 0, numeric owner — reproducible
            //    ordering + properties for the same payload.
            val produced = File(workDir, archFileName)
            val tar = ProcessBuilder(
                "tar", "-I", "zstd",
                "--sort=name", "--owner=0", "--group=0", "--numeric-owner",
                "-cf", produced.absolutePath,
                "-C", pkgRoot.absolutePath,
                ".PKGINFO", "opt", "usr"
            ).redirectErrorStream(true)
            val tarProcess = tar.start()
            val tarOutput = tarProcess.inputStream.bufferedReader().readText()
            val tarCode = tarProcess.waitFor()
            if (tarCode != 0) {
                logger.error("tar failed (exit $tarCode):\n$tarOutput")
                throw GradleException("tar failed (exit $tarCode)")
            }

            // 7. Move the produced package to binaries/. The move is cross-device (local workdir
            //    → the repo build dir), so renameTo is tried first with a copy fallback.
            if (!produced.isFile) {
                val found = workDir.listFiles()?.joinToString { it.name } ?: "(unreadable)"
                throw GradleException("tar produced no package: expected ${produced.absolutePath} (dir contents: $found)")
            }
            binaries.mkdirs()
            val target = File(binaries, archFileName)
            if (target.exists()) target.delete()
            if (!produced.renameTo(target)) {
                produced.copyTo(target)
                produced.delete()
            }
            logger.lifecycle("Arch package built: ${target.absolutePath}")
            // The release chore publishes the package's SHA-256 next to its name: log it here so
            // it can be copied straight from the build output.
            logger.lifecycle("Arch package SHA-256: ${sha256Of(target)}")
        }
    }
}
