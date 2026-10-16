package app.n_zik.compagnon.updater

import app.n_zik.compagnon.updater.models.ArtifactNames
import app.n_zik.compagnon.updater.models.InstallMode
import app.n_zik.compagnon.updater.models.PackageManager
import app.n_zik.compagnon.generated.AppVersion
import app.n_zik.compagnon.updater.models.RELEASE_MARKER_CONTENT
import app.n_zik.compagnon.updater.models.appRootOfResolvedPath
import app.n_zik.compagnon.updater.models.currentDistributionMarker
import app.n_zik.compagnon.updater.models.currentInstallMode
import app.n_zik.compagnon.updater.models.currentIsAurBlocked
import app.n_zik.compagnon.updater.models.detectInstallMode
import app.n_zik.compagnon.updater.models.distributionMarkerAt
import app.n_zik.compagnon.updater.models.isAurBlocked
import app.n_zik.compagnon.updater.models.isNsisWindowsInstall
import app.n_zik.compagnon.updater.models.liveExecutablePath
import app.n_zik.compagnon.updater.models.preferMatchingInstallDir
import app.n_zik.compagnon.updater.models.probePackageManager
import app.n_zik.compagnon.updater.models.updaterEffectivelyEnabled
import app.n_zik.compagnon.updater.models.windowsInstallDirFromRegistry
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import org.junit.jupiter.api.io.TempDir

/**
 * Pins the install-mode detection and the per-mode asset names (spec `spec-updater`, AD-4): the pure
 * [detectInstallMode] (Windows: the NSIS uninstaller marker discriminates the install from the
 * portable extract — spec `spec-windows-portable-zip` D1: the product-named launcher folder, or a
 * custom folder whose `InstallDir` the installer recorded in the channel registry key; then the
 * Flatpak environment, then the package-managed `/opt` path, else portable) and
 * [ArtifactNames.forMode] (the channel-suffixed asset names of the release, `null` for the
 * pacman / no-probe installs that carry no binary asset).
 */
class InstallModeDetectionTest {

    private val env = emptyMap<String, String>()
    private val LINUX = "Linux"
    private val WINDOWS = "Windows 11"

    @Test
    fun `a windows launch without the NSIS marker is portable windows`() {
        // spec `spec-windows-portable-zip` D1: the portable extract never carries the uninstaller —
        // the env / scope inputs are irrelevant, only the marker (plus the product-named folder)
        // decides; the launcher sits at the install root (no bin/ level on Windows)
        assertEquals(
            InstallMode.PORTABLE_WINDOWS,
            detectInstallMode(WINDOWS, mapOf("FLATPAK_ID" to "app.com"),
                "C:\\Users\\me\\Downloads\\N-Zik Desktop Compagnon\\N-Zik Desktop Compagnon.exe",
            ),
        )
        // A launch whose path has no parent folder (empty / unresolvable) is portable too
        assertEquals(
            InstallMode.PORTABLE_WINDOWS,
            detectInstallMode(WINDOWS, env, "", ArtifactNames.productName("stable")),
        )
    }

    @Test
    fun `the product-named folder with the NSIS uninstaller is windows`(@TempDir root: File) {
        // The installer contract (installer.nsi): the app-image is copied as-is into $INSTDIR —
        // the launcher at the install root, the uninstaller ALWAYS written next to it. The marker
        // is the portable/installed discriminator, not the scope location
        val product = "N-Zik Desktop Compagnon"
        val installDir = File(root, product)
        installDir.mkdirs()
        val launcher = File(installDir, "$product.exe")
        launcher.writeText("launcher")
        assertEquals(
            InstallMode.PORTABLE_WINDOWS,
            detectInstallMode(WINDOWS, env, launcher.absolutePath, product),
            "without the uninstaller the launch is portable, wherever the folder sits",
        )
        File(installDir, "Uninstall $product.exe").writeText("uninstaller")
        assertEquals(
            InstallMode.WINDOWS,
            detectInstallMode(WINDOWS, env, launcher.absolutePath, product),
            "the NSIS marker in the product-named folder is the install (per-user scope shape)",
        )
    }

    @Test
    fun `the scope location is secondary, the marker is the discriminator`(@TempDir root: File) {
        // A per-user install under a REDIRECTED %LOCALAPPDATA% (a product-named folder outside the
        // usual scope dirs) still detects as WINDOWS with the marker — the literal scope-root check
        // is NOT a requirement (review loopback 1 — BH-4)
        val product = "N-Zik Desktop Compagnon"
        val redirected = File(root, "redirected-profile/AppData Local/$product")
        redirected.mkdirs()
        File(redirected, "$product.exe").writeText("launcher")
        File(redirected, "Uninstall $product.exe").writeText("uninstaller")
        assertEquals(
            InstallMode.WINDOWS,
            detectInstallMode(WINDOWS, env, File(redirected, "$product.exe").absolutePath, product),
            "a redirected-profile NSIS install carries the marker in its product-named folder",
        )
        // ...and a portable extract placed INTO a scope dir is NOT an install without the marker
        // (review loopback 1 — ECH-1: the beta product folder shape, the scope dir alone must not
        // classify it)
        val beta = "N-Zik Desktop Compagnon BETA"
        val scopeExtract = File(root, "AppData/Local/$beta")
        scopeExtract.mkdirs()
        val betaLauncher = File(scopeExtract, "$beta.exe")
        betaLauncher.writeText("launcher")
        assertEquals(
            InstallMode.PORTABLE_WINDOWS,
            detectInstallMode(WINDOWS, env, betaLauncher.absolutePath, ArtifactNames.productName("beta")),
            "a portable extract inside a scope dir has no uninstaller marker",
        )
        // The marker of the WRONG channel product is not this build's marker (each channel is its
        // own installation, AD-8)
        File(scopeExtract, "Uninstall $beta.exe").writeText("uninstaller")
        assertEquals(
            InstallMode.PORTABLE_WINDOWS,
            detectInstallMode(WINDOWS, env, betaLauncher.absolutePath, ArtifactNames.productName("stable")),
            "the stable build does not claim the beta uninstaller marker",
        )
        assertEquals(
            InstallMode.WINDOWS,
            detectInstallMode(WINDOWS, env, betaLauncher.absolutePath, ArtifactNames.productName("beta")),
        )
    }

    // ---- spec `spec-windows-portable-zip` D1, review loopback 2: the registry cross-check
    // (the installer's directory page is editable — an install can sit in a custom folder) ----

    @Test
    fun `a custom-dir NSIS install is windows when the installer recorded its dir`(@TempDir root: File) {
        // The installer's directory page is EDITABLE: an install in a folder NOT named after the
        // product still carries the marker — the channel registry key's InstallDir (written by
        // the installer at install time) completes the detection. The registry read is injected
        // (no real registry in the test)
        val product = "N-Zik Desktop Compagnon"
        val custom = File(root, "Logiciels/MyApps")
        custom.mkdirs()
        val launcher = File(custom, "$product.exe")
        launcher.writeText("launcher")
        File(custom, "Uninstall $product.exe").writeText("uninstaller")
        assertEquals(
            InstallMode.WINDOWS,
            detectInstallMode(WINDOWS, env, launcher.absolutePath, product,
                registryInstallDir = { custom.absolutePath }),
            "marker + the installer's recorded InstallDir detect the custom-dir install",
        )
    }

    @Test
    fun `a custom-dir launch stays portable without a matching recorded dir`(@TempDir root: File) {
        val product = "N-Zik Desktop Compagnon"
        val custom = File(root, "Logiciels/MyApps")
        custom.mkdirs()
        val launcher = File(custom, "$product.exe")
        launcher.writeText("launcher")
        File(custom, "Uninstall $product.exe").writeText("uninstaller")
        // No recorded dir (a manually cleaned key / never installed): the folder-name rule applies
        assertEquals(
            InstallMode.PORTABLE_WINDOWS,
            detectInstallMode(WINDOWS, env, launcher.absolutePath, product, registryInstallDir = { null }),
            "without the recorded InstallDir the custom-dir launch is portable",
        )
        // A recorded dir that is NOT this folder does not claim the launch either
        assertEquals(
            InstallMode.PORTABLE_WINDOWS,
            detectInstallMode(WINDOWS, env, launcher.absolutePath, product,
                registryInstallDir = { File(root, "other").absolutePath }),
        )
    }

    @Test
    fun `the recorded dir comparison is case and trailing-slash insensitive`(@TempDir root: File) {
        val product = "N-Zik Desktop Compagnon"
        val custom = File(root, "Logiciels/MyApps")
        custom.mkdirs()
        val launcher = File(custom, "$product.exe")
        launcher.writeText("launcher")
        File(custom, "Uninstall $product.exe").writeText("uninstaller")
        assertEquals(
            InstallMode.WINDOWS,
            detectInstallMode(WINDOWS, env, launcher.absolutePath, product,
                registryInstallDir = { custom.absolutePath.lowercase() + "\\" }),
            "Windows paths compare case-insensitively, and a trailing separator is not a different dir",
        )
    }

    @Test
    fun `the folder-name rule short-circuits before any registry read`(@TempDir root: File) {
        // The production registry read is a fallback: a product-named folder with the marker must
        // detect as an install WITHOUT consulting the registry (a failing registry — e.g. the
        // key manually deleted — must not degrade a regular install)
        val product = "N-Zik Desktop Compagnon"
        val installDir = File(root, product)
        installDir.mkdirs()
        val launcher = File(installDir, "$product.exe")
        launcher.writeText("launcher")
        File(installDir, "Uninstall $product.exe").writeText("uninstaller")
        assertEquals(
            InstallMode.WINDOWS,
            detectInstallMode(WINDOWS, env, launcher.absolutePath, product,
                registryInstallDir = { error("the registry must not be read when the folder name matches") }),
            "the product-named folder + marker is sufficient — the registry is only a fallback",
        )
        // And the registry read is keyed by this build's channel (a custom-dir launch that reaches it)
        val custom = File(root, "Custom")
        custom.mkdirs()
        val customLauncher = File(custom, "$product.exe")
        customLauncher.writeText("launcher")
        File(custom, "Uninstall $product.exe").writeText("uninstaller")
        var channelSeen: String? = null
        detectInstallMode(WINDOWS, env, customLauncher.absolutePath, product,
            registryInstallDir = { channelSeen = it; custom.absolutePath })
        assertEquals(AppVersion.channel, channelSeen, "the registry read is keyed by the build's channel")
    }

    // ---- spec `spec-updater-parity-fixes`: the cross-scope reader (a per-user AND a global
    // install of the same channel can coexist — the hive matching the instance dir wins) ----

    @Test
    fun `the matching hive wins over the hkcu-first degradation`() {
        // preferMatchingInstallDir (pure, testable off-Windows): the running instance's
        // directory decides which hive's record is returned — HKCU when it matches, HKLM when
        // it does, HKCU-then-HKLM when neither does (the pre-audit reader order, now an
        // explicit fallback)
        val x = "C:\\Program Files\\N-Zik Desktop Compagnon"
        val y = "C:\\Users\\me\\AppData\\Local\\N-Zik Desktop Compagnon"
        val z = "C:\\Program Files\\N-Zik Desktop Compagnon BETA"
        // CROSS_SCOPE_HKCU_MATCH — the per-user record matches the instance (unchanged)
        assertEquals(x, preferMatchingInstallDir(hkcu = x, hklm = y, instanceDir = x))
        // CROSS_SCOPE_HKLM_MATCH — the global instance, the per-user also installed: the
        // matching HKLM record is returned, not the HKCU one (the pre-audit misread)
        assertEquals(x, preferMatchingInstallDir(hkcu = y, hklm = x, instanceDir = x))
        // CROSS_SCOPE_NO_MATCH — the explicit HKCU-first degradation (today's behavior)
        assertEquals(y, preferMatchingInstallDir(hkcu = y, hklm = z, instanceDir = "C:\\nowhere"))
        // CROSS_SCOPE_ONLY_HKLM — no regression
        assertEquals(x, preferMatchingInstallDir(hkcu = null, hklm = x, instanceDir = x))
        // Both hives absent
        assertNull(preferMatchingInstallDir(hkcu = null, hklm = null, instanceDir = x))
        // The match is case + trailing-slash insensitive (sameWindowsDir) — the returned
        // record is the recorded value verbatim
        val recordedLower = x.lowercase() + "\\"
        assertEquals(
            recordedLower,
            preferMatchingInstallDir(hkcu = null, hklm = recordedLower, instanceDir = x.uppercase()),
        )
        // Both hives matching: HKCU first (the per-user scope is the default)
        assertEquals(x, preferMatchingInstallDir(hkcu = x, hklm = x, instanceDir = x))
    }

    @Test
    fun `a global instance in a custom dir is windows, not portable`(@TempDir root: File) {
        // The acceptance case: a per-user AND a global NSIS install of the same channel coexist;
        // the global instance runs from a custom dir (the HKLM.InstallDir) while HKCU carries
        // the per-user install's dir — the reader must return the matching HKLM record, so
        // detectInstallMode stays WINDOWS (the pre-audit HKCU-first read returned the per-user
        // dir and misdetected the global instance PORTABLE_WINDOWS — wrong asset + gesture).
        // The production reader is simulated through the injectable seam: the per-user record
        // does NOT match the instance dir, the global one does
        val product = "N-Zik Desktop Compagnon"
        val globalDir = File(root, "Program Files/MyApps")
        globalDir.mkdirs()
        val launcher = File(globalDir, "$product.exe")
        launcher.writeText("launcher")
        File(globalDir, "Uninstall $product.exe").writeText("uninstaller")
        val perUserDir = File(root, "AppData/Local/$product")
        assertEquals(
            InstallMode.WINDOWS,
            detectInstallMode(WINDOWS, env, launcher.absolutePath, product,
                registryInstallDir = {
                    preferMatchingInstallDir(
                        hkcu = perUserDir.absolutePath,
                        hklm = globalDir.absolutePath,
                        instanceDir = globalDir.absolutePath,
                    )
                }),
            "the hive matching the instance dir (HKLM) is returned — the global instance stays an install",
        )
        // Without the cross-scope fix (the HKCU-first read), the per-user record would not
        // match the instance dir and the launch would misread as portable
        assertEquals(
            InstallMode.PORTABLE_WINDOWS,
            detectInstallMode(WINDOWS, env, launcher.absolutePath, product,
                registryInstallDir = { perUserDir.absolutePath }),
        )
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    fun `the production registry reader round-trips the installer recorded dir`() {
        // The tests above inject the registry source; this exercises the PRODUCTION reader
        // (loopback 2, option B) against the real registry: the exact key path the installer
        // writes (`Software\N-Zik\DesktopCompagnon\{channel}` — InstallerContractTest pins the
        // write side), the two-pass REG_SZ read, and the absent-key -> null degradation. A
        // throwaway channel key (never collides with the app's channels), cleaned up afterwards
        val channel = "zz-roundtrip-${System.currentTimeMillis()}"
        val key = "HKCU\\Software\\N-Zik\\DesktopCompagnon\\$channel"
        val installDir = Files.createTempDirectory("nzik-registry-roundtrip")
        try {
            assertNull(
                windowsInstallDirFromRegistry(channel, installDir.toString()),
                "an absent key must read as null (the folder-name rule takes over)",
            )
            runReg("add", key, "/v", "InstallDir", "/t", "REG_SZ", "/d", installDir.toString(), "/f")
            assertEquals(
                installDir.toString(),
                windowsInstallDirFromRegistry(channel, installDir.toString()),
                "the InstallDir the installer records must be read back verbatim",
            )
        } finally {
            runCatching { runReg("delete", key, "/f") }
            Files.deleteIfExists(installDir)
        }
        assertNull(
            windowsInstallDirFromRegistry(channel, installDir.toString()),
            "a cleaned-up key must read as null again",
        )
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    fun `both hives coexisting returns the hive matching the instance dir`() {
        // The cross-scope coexistence against the REAL registry (spec `spec-updater-parity-fixes`):
        // a per-user AND a global record of the same channel — the reader returns the hive
        // matching the instance dir, not merely the HKCU-first one. The HKLM write needs
        // elevation; when the dev host is not elevated the coexistence cannot be staged, and
        // the test reports a SKIP (Assumptions — not a pass; the pure helper pins the
        // cross-scope rows off-Windows, the round-trip pins the production reader)
        val channel = "zz-cross-scope-${System.currentTimeMillis()}"
        val hkcuDir = Files.createTempDirectory("nzik-cross-hkcu")
        val hklmDir = Files.createTempDirectory("nzik-cross-hklm")
        val hkcuKey = "HKCU\\Software\\N-Zik\\DesktopCompagnon\\$channel"
        val hklmKey = "HKLM\\Software\\N-Zik\\DesktopCompagnon\\$channel"
        val hklmStaged = runCatching {
            runReg("add", hklmKey, "/v", "InstallDir", "/t", "REG_SZ", "/d", hklmDir.toString(), "/f")
        }.isSuccess
        try {
            Assumptions.assumeTrue(
                hklmStaged,
                "the HKLM record needs elevation to stage (non-elevated host — the cross-scope rows are pinned by the pure helper)",
            )
            runReg("add", hkcuKey, "/v", "InstallDir", "/t", "REG_SZ", "/d", hkcuDir.toString(), "/f")
            assertEquals(
                hklmDir.toString(),
                windowsInstallDirFromRegistry(channel, hklmDir.toString()),
                "the global instance (dir = the HKLM record) must not be claimed by the HKCU record",
            )
            assertEquals(
                hkcuDir.toString(),
                windowsInstallDirFromRegistry(channel, hkcuDir.toString()),
                "the per-user instance (dir = the HKCU record) is unchanged",
            )
            assertEquals(
                hkcuDir.toString(),
                windowsInstallDirFromRegistry(channel, "C:\\nowhere"),
                "neither hive matches: the HKCU-first degradation",
            )
        } finally {
            runCatching { runReg("delete", hklmKey, "/f") }
            runCatching { runReg("delete", hkcuKey, "/f") }
            Files.deleteIfExists(hkcuDir)
            Files.deleteIfExists(hklmDir)
        }
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    fun `the default registry seam derives the instance dir from the launcher path`() {
        // The production default `registryInstallDir` lambda (isNsisWindowsInstall — the same
        // expression detectInstallMode's default carries) derives the instance dir from the
        // executable path (spec `spec-updater-parity-fixes`): every other registry test
        // injects its own lambda, so a wrong derivation would silently degrade to the
        // HKCU-first bug with nothing failing. Stage a per-user AND a global record under a
        // throwaway channel key, place the launcher + marker in a custom (non-product-named)
        // dir that equals the HKLM record's InstallDir, and call isNsisWindowsInstall WITHOUT
        // the lambda argument — the default seam must derive the launcher folder as the
        // instance dir and pick the matching HKLM record (a HKCU-first read would claim the
        // per-user dir → the launch would misread as portable). A non-elevated host reports a
        // SKIP (Assumptions — the pure helper pins the cross-scope rows off-Windows)
        val product = "N-Zik Desktop Compagnon"
        val channel = "zz-default-seam-${System.currentTimeMillis()}"
        val customDir = Files.createTempDirectory("nzik-default-seam")
        val perUserDir = Files.createTempDirectory("nzik-default-seam-peruser")
        val hkcuKey = "HKCU\\Software\\N-Zik\\DesktopCompagnon\\$channel"
        val hklmKey = "HKLM\\Software\\N-Zik\\DesktopCompagnon\\$channel"
        val hklmStaged = runCatching {
            runReg("add", hklmKey, "/v", "InstallDir", "/t", "REG_SZ", "/d", customDir.toString(), "/f")
        }.isSuccess
        try {
            Assumptions.assumeTrue(
                hklmStaged,
                "the HKLM record needs elevation to stage (non-elevated host — the derivation is pinned by the pure helper)",
            )
            runReg("add", hkcuKey, "/v", "InstallDir", "/t", "REG_SZ", "/d", perUserDir.toString(), "/f")
            val launcher = File(customDir.toString(), "$product.exe")
            launcher.writeText("launcher")
            File(customDir.toString(), "Uninstall $product.exe").writeText("uninstaller")
            // The default seam (no lambda argument) + an explicit throwaway channel — the
            // production channel key (AppVersion.channel) is never touched
            assertTrue(
                isNsisWindowsInstall(launcher.absolutePath, product, channel),
                "the default seam must derive the launcher folder as the instance dir and pick the matching HKLM record",
            )
        } finally {
            runCatching { runReg("delete", hklmKey, "/f") }
            runCatching { runReg("delete", hkcuKey, "/f") }
            runCatching {
                File(customDir.toString(), "Uninstall $product.exe").delete()
                File(customDir.toString(), "$product.exe").delete()
                Files.deleteIfExists(customDir)
                Files.deleteIfExists(perUserDir)
            }
        }
    }

    private fun runReg(vararg args: String) {
        val process = ProcessBuilder(listOf("reg") + args).redirectErrorStream(true).start()
        val output = String(process.inputStream.readBytes())
        val exit = process.waitFor()
        check(exit == 0) { "reg ${args.firstOrNull()} failed (exit $exit): $output" }
    }

    @Test
    fun `the flatpak environment wins over every path`() {
        assertEquals(
            InstallMode.FLATPAK,
            detectInstallMode(LINUX, mapOf("FLATPAK_ID" to "com.nzik.desktop.compagnon"), "/usr/bin/something"),
        )
        assertEquals(
            InstallMode.FLATPAK,
            detectInstallMode(LINUX, mapOf("container" to "flatpak"), "/tmp/.mount_xyz/app"),
        )
        // Blank env values do not count
        assertEquals(
            InstallMode.PORTABLE,
            detectInstallMode(LINUX, mapOf("FLATPAK_ID" to "", "container" to " "), "/home/me/app/app"),
        )
    }

    @Test
    fun `a non-flatpak container is not flatpak`() {
        // Loop 2: the `container` marker must be EXACTLY "flatpak" — a docker / podman host is a
        // plain portable install, not a Flatpak
        assertEquals(
            InstallMode.PORTABLE,
            detectInstallMode(LINUX, mapOf("container" to "docker"), "/home/me/app/app"),
        )
        assertEquals(
            InstallMode.PORTABLE,
            detectInstallMode(LINUX, mapOf("container" to "Flatpak"), "/home/me/app/app"),
        )
    }

    @Test
    fun `the package managed path is package managed`() {
        // The .deb / .rpm / AUR entries all share the single /opt location
        assertEquals(
            InstallMode.PACKAGE_MANAGED,
            detectInstallMode(LINUX, env, "/opt/n-zik-desktop-compagnon/bin/N-Zik Desktop Compagnon"),
        )
        // The dev channel installs into the SHARED /opt base (spec `spec-updater`, AD-8 as
        // amended 2026-10-15 — its marker is the dated version suffix, not a separate path)
        assertEquals(
            InstallMode.PACKAGE_MANAGED,
            detectInstallMode(LINUX, env, "/opt/n-zik-desktop-compagnon/bin/N-Zik Desktop Compagnon DEV"),
        )
    }

    @Test
    fun `any other linux path is portable`() {
        assertEquals(
            InstallMode.PORTABLE,
            detectInstallMode(LINUX, env, "/home/me/downloads/n-zik/N-Zik Desktop Compagnon"),
        )
        assertEquals(
            InstallMode.PORTABLE,
            detectInstallMode(LINUX, env, ""),
        )
    }

    @Test
    fun `an appimage mount path falls through to portable`() {
        // The 2026-10-09 rework removed the APPIMAGE mode from detection (spec
        // `spec-channel-version-naming`): a legacy AppImage mount (`/tmp/.mount_*`) is no longer
        // recognized — it falls through to the portable/generic path, so a legacy AppImage install
        // gets the portable gesture (accepted consequence, the "pas besoin" family: the
        // releases no longer carry an AppImage asset)
        assertEquals(
            InstallMode.PORTABLE,
            detectInstallMode(LINUX, env, "/tmp/.mount_NZikDE/x86_64/N-Zik Desktop Compagnon"),
        )
    }

    @Test
    fun `the asset name per install mode follows the channel-suffixed convention`() {
        val version = "0.0.2-beta"
        // The 2026-10-09 naming rework / AD-8: the published .exe is the channel's Linux base
        // (the CI renames the jpackage output — the spaced product name never appears in the
        // asset name); the other assets carry the channel suffix after the base version
        assertEquals("n-zik-desktop-compagnon-$version.exe", ArtifactNames.forMode(InstallMode.WINDOWS, version, PackageManager.NONE))
        // The portable-Windows install (spec `spec-windows-portable-zip`) gets the Windows
        // portable zip on the same channel base
        assertEquals("n-zik-desktop-compagnon-$version-windows-portable.zip", ArtifactNames.forMode(InstallMode.PORTABLE_WINDOWS, version, PackageManager.NONE))
        assertEquals("n-zik-desktop-compagnon-$version-x86_64.flatpak", ArtifactNames.forMode(InstallMode.FLATPAK, version, PackageManager.NONE))
        assertEquals("n-zik-desktop-compagnon_$version-1_amd64.deb", ArtifactNames.forMode(InstallMode.PACKAGE_MANAGED, version, PackageManager.DEB))
        assertEquals("n-zik-desktop-compagnon-$version-1.x86_64.rpm", ArtifactNames.forMode(InstallMode.PACKAGE_MANAGED, version, PackageManager.RPM))
        assertEquals("n-zik-desktop-compagnon-$version-linux-portable.tar.gz", ArtifactNames.forMode(InstallMode.PORTABLE, version, PackageManager.NONE))
    }

    @Test
    fun `pacman and a failed probe carry no binary asset`() {
        // pacman builds from source: the dialog shows the AUR entry hint instead of a download
        assertNull(ArtifactNames.forMode(InstallMode.PACKAGE_MANAGED, "0.0.2-beta", PackageManager.AUR))
        assertNull(ArtifactNames.forMode(InstallMode.PACKAGE_MANAGED, "0.0.2-beta", PackageManager.NONE))
    }

    @Test
    fun `the package manager probe maps the answering binary`() {
        // The probe is injected (no real binaries in the test): the first answer wins, in the apt / dnf / pacman order
        assertEquals(PackageManager.DEB, probePackageManager(probe = { it == "apt" }))
        assertEquals(PackageManager.RPM, probePackageManager(probe = { it == "dnf" }))
        assertEquals(PackageManager.AUR, probePackageManager(probe = { it == "pacman" }))
        assertEquals(PackageManager.NONE, probePackageManager(probe = { false }))
        // dnf answers even when apt does not (the probe order is fixed)
        assertEquals(PackageManager.RPM, probePackageManager(probe = { it == "dnf" || it == "pacman" }))
    }

    // ---- spec `spec-arch-binary-package-release`: the release marker + the AUR block ----

    @Test
    fun `a marked pacman install gets the arch package asset`() {
        // The Arch package: the pacman binary package asset, offered only when the install
        // carries the release marker (a release-pkg install)
        assertEquals(
            "n-zik-desktop-compagnon-0.0.2-beta-1-x86_64.pkg.tar.zst",
            ArtifactNames.forMode(InstallMode.PACKAGE_MANAGED, "0.0.2-beta", PackageManager.AUR, isReleasePackage = true),
        )
        // The dev channel rides the shared package base (AD-8 as amended 2026-10-15) — its
        // marker is the dated version suffix only
        assertEquals(
            "n-zik-desktop-compagnon-0.0.2-dev-20261008-1-x86_64.pkg.tar.zst",
            ArtifactNames.forMode(InstallMode.PACKAGE_MANAGED, "0.0.2-dev-20261008", PackageManager.AUR, isReleasePackage = true),
        )
        // The archPkg helper itself (the default channel inference from the version suffix)
        assertEquals(
            "n-zik-desktop-compagnon-0.0.2-beta-1-x86_64.pkg.tar.zst",
            ArtifactNames.archPkg("0.0.2-beta"),
        )
        // An AUR install (no marker) has no binary asset — its updater is blocked before any
        // check, so no asset is ever selected for it
        assertNull(ArtifactNames.forMode(InstallMode.PACKAGE_MANAGED, "0.0.2-beta", PackageManager.AUR, isReleasePackage = false))
        // The other modes are untouched by the marker
        assertEquals(
            "n-zik-desktop-compagnon_0.0.2-beta-1_amd64.deb",
            ArtifactNames.forMode(InstallMode.PACKAGE_MANAGED, "0.0.2-beta", PackageManager.DEB, isReleasePackage = true),
        )
        assertEquals(
            "n-zik-desktop-compagnon-0.0.2-beta-linux-portable.tar.gz",
            ArtifactNames.forMode(InstallMode.PORTABLE, "0.0.2-beta", PackageManager.NONE, isReleasePackage = true),
        )
    }

    @Test
    fun `the aur block requires pacman and a package managed install without marker`() {
        // The decision matrix (spec `spec-arch-binary-package-release`): a package-managed install
        // on a pacman host WITHOUT the release marker is an AUR install — the updater is blocked.
        assertTrue(isAurBlocked(InstallMode.PACKAGE_MANAGED, null, { PackageManager.AUR }))
        assertFalse(isAurBlocked(InstallMode.PACKAGE_MANAGED, null, { PackageManager.DEB }))
        assertFalse(isAurBlocked(InstallMode.PACKAGE_MANAGED, null, { PackageManager.RPM }))
        assertFalse(isAurBlocked(InstallMode.PACKAGE_MANAGED, null, { PackageManager.NONE }))
        // A release-pkg install (the marker present) is NOT blocked — even on pacman
        assertFalse(isAurBlocked(InstallMode.PACKAGE_MANAGED, "github-release", { PackageManager.AUR }))
        // A non package-managed mode is never blocked, even on pacman
        assertFalse(isAurBlocked(InstallMode.PORTABLE, null, { PackageManager.AUR }))
        assertFalse(isAurBlocked(InstallMode.PORTABLE_WINDOWS, null, { PackageManager.AUR }))
        assertFalse(isAurBlocked(InstallMode.WINDOWS, null, { PackageManager.AUR }))
        assertFalse(isAurBlocked(InstallMode.FLATPAK, null, { PackageManager.AUR }))
    }

    @Test
    fun `the marker short-circuits the probe`() {
        // Loop 2 G13: the marker is checked BEFORE the probe — a marked install (release pkg) and
        // every non package-managed mode must never spawn the apt / dnf / pacman probes
        var probed = false
        val probe = { probed = true; PackageManager.AUR }
        isAurBlocked(InstallMode.PACKAGE_MANAGED, "github-release", probe)
        isAurBlocked(InstallMode.PORTABLE, null, probe)
        isAurBlocked(InstallMode.WINDOWS, null, probe)
        assertFalse(probed, "the probe must not spawn when the marker or the mode short-circuits")
    }

    @Test
    fun `the live composition is testable through the injectable defaults`() {
        // currentIsAurBlocked / updaterEffectivelyEnabled with injected parameters: the production
        // composition (currentInstallMode / currentDistributionMarker / probePackageManager) is the
        // same wiring the live gates use — an inversion of the marker negation would fail here
        assertTrue(currentIsAurBlocked(installMode = InstallMode.PACKAGE_MANAGED, marker = null, probe = { PackageManager.AUR }))
        assertFalse(currentIsAurBlocked(installMode = InstallMode.PACKAGE_MANAGED, marker = "github-release", probe = { PackageManager.AUR }))
        assertFalse(currentIsAurBlocked(installMode = InstallMode.PORTABLE, marker = null, probe = { PackageManager.AUR }))

        assertTrue(updaterEffectivelyEnabled(buildEnabled = true, aurBlocked = false))
        assertFalse(updaterEffectivelyEnabled(buildEnabled = true, aurBlocked = true))
        assertFalse(updaterEffectivelyEnabled(buildEnabled = false, aurBlocked = false))
        assertFalse(updaterEffectivelyEnabled(buildEnabled = false, aurBlocked = true))
    }

    // ---- the live executable-path resolution (loop 2 G9) ----

    @Test
    fun `the app root of a resolved launcher path is the opt app dir`() {
        // A package-managed launcher lives at <appRoot>/bin/<launcher> — the parent dir named
        // `bin` gives the app root, where the distribution.txt marker lives
        assertEquals("/opt/n-zik-desktop-compagnon", appRootOfResolvedPath("/opt/n-zik-desktop-compagnon/bin/N-Zik Desktop Compagnon"))
        assertEquals("/opt/n-zik-desktop-compagnon", appRootOfResolvedPath("/opt/n-zik-desktop-compagnon/bin/N-Zik Desktop Compagnon DEV"))
        // The UNRESOLVED launch path (/usr/bin/<pkg> — the symlink the .desktop Exec carries): the
        // parent is /usr, so no marker can be read from it — this is the loop-2 bug: the
        // production launch carries the SYMLINK path in its own cmdline
        assertEquals("/usr", appRootOfResolvedPath("/usr/bin/n-zik-desktop-compagnon"))
        // Paths whose parent is not a bin/ dir have no app root
        assertNull(appRootOfResolvedPath("/home/me/app/N-Zik Desktop Compagnon"))
        assertNull(appRootOfResolvedPath(""))
    }

    @Test
    fun `the marker reader is strict on content`(@TempDir root: File) {
        // The reader accepts EXACTLY the release marker content (spec
        // `spec-arch-binary-package-release`, review loop 3 — L3-EC2): a stray or forged
        // distribution.txt with any other content must read as NO marker (the write side is
        // equally strict — packageArch fails the build on any other content). No symlink needed
        // (the reader is pure over the path) — this runs on any host, developer mode or not.
        val optRoot = File(root, "opt/n-zik-desktop-compagnon")
        File(optRoot, "bin").mkdirs()
        val launcher = File(optRoot, "bin/N-Zik Desktop Compagnon")
        launcher.writeText("launcher")
        val marker = File(optRoot, "distribution.txt")

        marker.writeText(RELEASE_MARKER_CONTENT)
        assertEquals(RELEASE_MARKER_CONTENT, distributionMarkerAt(launcher.absolutePath))
        // The build side writes it with a trailing newline — the read trims
        marker.writeText("$RELEASE_MARKER_CONTENT\n")
        assertEquals(RELEASE_MARKER_CONTENT, distributionMarkerAt(launcher.absolutePath))
        // Any OTHER content is not the marker (a stray file must not unblock the updater)
        marker.writeText("aur")
        assertNull(distributionMarkerAt(launcher.absolutePath))
        // Blank is not the marker either (an AUR install carries the file not)
        marker.writeText("   ")
        assertNull(distributionMarkerAt(launcher.absolutePath))
        // Absent is not the marker
        marker.delete()
        assertNull(distributionMarkerAt(launcher.absolutePath))
    }

    @Test
    fun `the resolved path is what the detection runs on`() {
        // The composition the production gate uses: detectInstallMode over the RESOLVED path
        // (the loop-2 fix — over the unresolved /usr/bin path, an /opt install would be
        // misdetected PORTABLE)
        assertEquals(
            InstallMode.PACKAGE_MANAGED,
            detectInstallMode("Linux", emptyMap(), "/opt/n-zik-desktop-compagnon/bin/N-Zik Desktop Compagnon"),
        )
        // The unresolved launch path of that same install (before the loop-2 fix)
        assertEquals(
            InstallMode.PORTABLE,
            detectInstallMode("Linux", emptyMap(), "/usr/bin/n-zik-desktop-compagnon"),
        )
    }

    @Test
    fun `the live entry points are the pure helpers over the live data`() {
        // The live composition (spec `spec-arch-binary-package-release` G9, review loop 3 —
        // L3-VG2): the pure helpers are pinned above; this pins the wiring — that the live
        // kernel-resolved path actually feeds [detectInstallMode] / [distributionMarkerAt] (a
        // resolution regression would mis-detect a release-pkg install as PORTABLE with a null
        // marker, and nothing else would catch it).
        val resolved = liveExecutablePath()
        assertTrue(resolved.isNotBlank(), "liveExecutablePath must not be empty")
        assertEquals(
            detectInstallMode(System.getProperty("os.name", ""), System.getenv(), resolved),
            currentInstallMode(),
        )
        assertEquals(distributionMarkerAt(resolved), currentDistributionMarker())

        if (Files.isSymbolicLink(Paths.get("/proc/self/exe"))) {
            // Linux: the kernel-resolved path is NOT the launch symlink — toRealPath walked the
            // full chain (the production launch goes through the /usr/bin/<pkg> symlink)
            assertFalse(Files.isSymbolicLink(Paths.get(resolved)))
        } else if (!Files.exists(Paths.get("/proc/self/exe"))) {
            // Non-Linux hosts: the fallback is the command-line path itself
            // (ProcessHandle.command())
            assertEquals(ProcessHandle.current().info().command().orElse(""), resolved)
        }
    }
}
