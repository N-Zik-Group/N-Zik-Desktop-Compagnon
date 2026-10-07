package app.n_zik.compagnon

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The Flatpak contract lives in build config + a committed manifest template that no other test
 * path touches: a changed app-id would desynchronize the bundle identity (the install name, the
 * upgrade identity and the icon name all derive from it — frozen by AD-1, irreversible once
 * published), a drifted base runtime or a shrunken finish-args list would ship a bundle that
 * cannot reach the phone (network), the keyring (org.freedesktop.secrets) or the data dir (home) — all
 * with a green build. The build exposes the effective values (`systemProperty` in
 * `app/build.gradle.kts`, read from the committed template like `linux.appImage.*`) so this test
 * pins them, and the committed template itself is checked (valid JSON, exactly its two
 * placeholders) so the build cannot substitute anything it does not declare.
 */
class FlatpakPinTest {

    private val manifestTemplate = File("../packaging/flatpak/manifest.json")

    private fun flatpakProp(name: String): String =
        System.getProperty(name) ?: error("$name is not exposed to the tests")

    @Test
    fun `the app id is frozen to the reverse-domain identity`() {
        assertEquals("com.nzik.desktop.compagnon", flatpakProp("flatpak.appId"),
            "the Flatpak app-id is the frozen install/upgrade/icon identity (AD-1 — irreversible once published)")
    }

    @Test
    fun `the base runtime is the frozen freedesktop platform`() {
        assertEquals("org.freedesktop.Platform", flatpakProp("flatpak.runtime"),
            "the base runtime is downloaded from the Flathub CDN (AD-2 — a download, not a Flathub submission)")
        assertEquals("24.08", flatpakProp("flatpak.runtimeVersion"),
            "the base runtime version is pinned (AD-2)")
    }

    @Test
    fun `the bundle file name is frozen to the package name and version`() {
        // The version is the catalog's nzikVersionName (shared with Windows) and the package
        // name the frozen Linux install identity — read them from the catalog / the exposed
        // contract so the test pins the derivation, not a copy of the string.
        val toml = File("../gradle/libs.versions.toml")
        val version = Regex("""nzikVersionName\s*=\s*"?([^"\n]+)"?""")
            .find(toml.readText())?.groupValues?.get(1)
            ?: error("the catalog's nzikVersionName line is missing")
        val pkgName = System.getProperty("linux.packageName") ?: error("linux.packageName is not exposed to the tests")
        val fileName = flatpakProp("flatpak.fileName")
        assertEquals("$pkgName-$version-x86_64.flatpak", fileName,
            "the Flatpak name is <package>-<version>-x86_64.flatpak (the same frozen derivation as the AppImage)")
    }

    @Test
    fun `the finish args keep the pairing keyring windowing and audio sandbox`() {
        // The sandbox contract: network = the pairing listener + the phone's LAN server,
        // x11/wayland = windowing, pulseaudio = audio, device:dri = GPU rendering, home = the
        // data dir (~/N-Zik Desktop Compagnon/), org.freedesktop.secrets = the keyring (the
        // current Secret Service spec bus name — the only D-Bus name the app may reach,
        // through the flatpak runtime's D-Bus proxy, no raw bus socket; the legacy
        // org.freedesktop.SecretService name is no longer registered by modern keyrings).
        // Exposed from the committed template, joined with the unit separator. Note: the socket
        // types are validated by flatpak at build time (`--socket=ipc` is rejected — ipc is a
        // `--share=` type; `dri` is a `--device=` type), so a wrong entry fails the build too.
        val args = flatpakProp("flatpak.finishArgs").split("\u001f")
        for (arg in listOf(
            "--share=network",
            "--socket=x11",
            "--socket=wayland",
            "--socket=pulseaudio",
            "--device=dri",
            "--filesystem=home",
            "--talk-name=org.freedesktop.secrets",
        )) {
            assertTrue(args.contains(arg), "the Flatpak sandbox needs $arg (a missing one breaks pairing, the keyring or the data dir)")
        }
    }

    @Test
    fun `the command is the wrapper launcher`() {
        // A Flatpak .desktop cannot carry Env= (flatpak ignores it) and the embedded libs carry
        // no rpath — so the entry point is the nzik wrapper, which exports the runtime dir on
        // LD_LIBRARY_PATH before exec-ing the jpackage launcher.
        assertEquals("nzik", flatpakProp("flatpak.command"),
            "the manifest command is the nzik wrapper (the .desktop Exec names the same wrapper)")
    }

    @Test
    fun `the libsecret module keeps the keyring client pinned`() {
        // The freedesktop runtime does not ship libsecret-1.so: without the module the app
        // falls back to the in-memory session store inside the sandbox and the token would
        // not survive a restart. The module builds the pinned official GNOME source into the
        // bundle (found by JNA like the embedded libvlc), so its source must stay pinned —
        // a changed pin must be verified, like the VLC pins.
        // 0.21.x (not the older 0.20.x) speaks the current Secret Service spec
        // (org.freedesktop.secrets) that modern keyring daemons register — the 0.20 series
        // only targets the legacy org.freedesktop.SecretService name, which modern
        // gnome-keyring no longer provides.
        assertEquals("https://github.com/GNOME/libsecret/archive/refs/tags/0.21.8.2.tar.gz",
            flatpakProp("flatpak.libsecretUrl"),
            "the libsecret source is the pinned official GNOME release (0.21.x, current spec)")
        assertEquals("83b63bdf73124e5de7a24653aee518e861daee65f6ec8593d20fb87f3097bd98",
            flatpakProp("flatpak.libsecretSha256"),
            "the libsecret source SHA-256 is pinned")
    }

    @Test
    fun `the template manifest is valid json with exactly its two placeholders`() {
        val text = manifestTemplate.readText()
        val manifest = Json.parseToJsonElement(text).jsonObject
        // The placeholders are the only dynamic values: app-id (+ the icon derived from it) and
        // version — nothing else may be substituted by the build.
        val placeholders = Regex("""__[A-Z0-9_]+__""").findAll(text).map { it.value }.toSet()
        assertEquals(setOf("__APP_ID__", "__VERSION__"), placeholders,
            "the committed template keeps exactly its two placeholders (__APP_ID__, __VERSION__)")
        assertEquals("__APP_ID__", manifest["app-id"]?.jsonPrimitive?.content,
            "the app-id is the __APP_ID__ placeholder (substituted by the build, frozen by the test)")
        assertEquals("__APP_ID__", manifest["icon"]?.jsonPrimitive?.content,
            "the icon is the app-id (the same placeholder, so the two can never drift)")
        assertEquals("__VERSION__", manifest["version"]?.jsonPrimitive?.content,
            "the version is the __VERSION__ placeholder (the catalog's nzikVersionName)")
        val finishArgs = manifest["finish-args"] as? JsonArray
        // The finish-args are the frozen sandbox contract — an added permission (a broader
        // --filesystem=, a new --share=) is an intent change, not a template edit: pin the
        // exact set, not a subset.
        assertEquals(
            setOf(
                "--share=network",
                "--socket=x11",
                "--socket=wayland",
                "--socket=pulseaudio",
                "--device=dri",
                "--filesystem=home",
                "--talk-name=org.freedesktop.secrets",
            ),
            finishArgs?.mapNotNull { it.jsonPrimitive.content }?.toSet(),
            "the finish-args are exactly the frozen sandbox contract (an added permission must go through the spec, not the template)")
        // The module recipes are part of the contract too: a wrong libsecret recipe builds
        // green with every test passing but ships no libsecret-1.so (the keyring silently
        // falls back to the session store), and a cp that drops lib ships a bundle that
        // cannot launch (no JRE, no jars, no embedded VLC).
        val modules = manifest["modules"] as? JsonArray
        val moduleNames = modules?.mapNotNull { it.jsonObject["name"]?.jsonPrimitive?.content }
        assertTrue("libsecret" in moduleNames.orEmpty() && "app" in moduleNames.orEmpty(),
            "the template carries both modules (libsecret + app)")
        val libsecret = modules?.first { (it as? JsonObject)?.get("name")?.jsonPrimitive?.content == "libsecret" } as? JsonObject
        assertEquals("meson", libsecret?.get("buildsystem")?.jsonPrimitive?.content,
            "the libsecret module builds the pinned GNOME source with meson (a no-compiler recipe ships no libsecret-1.so)")
        assertEquals(
            listOf("-Dvapi=false", "-Dintrospection=false", "-Dgtk_doc=false", "-Dmanpage=false"),
            (libsecret?.get("config-opts") as? JsonArray)?.mapNotNull { it.jsonPrimitive.content },
            "the libsecret config-opts stay as committed (gtk_doc is the known gi-docgen build failure, manpage/vapi/introspection are not shipped)")
        val app = modules?.first { (it as? JsonObject)?.get("name")?.jsonPrimitive?.content == "app" } as? JsonObject
        assertEquals("simple", app?.get("buildsystem")?.jsonPrimitive?.content,
            "the app module is the no-compiler simple type (its build-commands copy the staged app-image)")
        assertTrue(
            ((app?.get("build-commands") as? JsonArray)?.mapNotNull { it.jsonPrimitive.content }.orEmpty())
                .any { it.startsWith("cp -a bin lib share /app/") },
            "the app module copies bin + lib + share into /app (dropping lib would ship an unlaunchable bundle)")
    }
}
