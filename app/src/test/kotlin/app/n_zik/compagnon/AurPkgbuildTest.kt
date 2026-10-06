package app.n_zik.compagnon

import java.io.File
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The two AUR packages (the release-zip entry and the `-git` entry) are the Arch/Manjaro/CachyOS
 * install path. They live in the repo (under `packaging/`) and are NOT built by Gradle — an AUR
 * build fetches them. This test pins the identity of both so a silent drift (a renamed package, a
 * dropped `vlc` dependency, a broken source URL) fails the build instead of shipping a broken AUR
 * entry. It also pins that the shared Linux icon is committed (jpackage needs it for the .deb/.rpm).
 */
class AurPkgbuildTest {

    private val releasePkgbuild = File("../packaging/aur/PKGBUILD")
    private val gitPkgbuild = File("../packaging/aur-git/PKGBUILD")
    private val linuxIcon = File("../assets/design/icon-linux.png")

    @Test
    fun `both AUR PKGBUILDs are present`() {
        assertTrue(releasePkgbuild.isFile, "packaging/aur/PKGBUILD is missing")
        assertTrue(gitPkgbuild.isFile, "packaging/aur-git/PKGBUILD is missing")
    }

    @Test
    fun `release entry package identity`() {
        val text = releasePkgbuild.readText()
        // The release entry is the plain package; the `-git` entry is a separate AUR package.
        assertTrue(text.contains("pkgname=n-zik-desktop-compagnon\n"), "release pkgname must be n-zik-desktop-compagnon")
        assertTrue(!text.contains("pkgname=n-zik-desktop-compagnon-git"), "release entry must not be the -git package")
    }

    @Test
    fun `git entry package identity`() {
        val text = gitPkgbuild.readText()
        assertTrue(text.contains("pkgname=n-zik-desktop-compagnon-git\n"), "git pkgname must be n-zik-desktop-compagnon-git")
    }

    @Test
    fun `both entries declare vlc as a dependency`() {
        // No Linux package bundles VLC: the app plays through the system libvlc, so the package must
        // declare `vlc` (installed by pacman alongside the app).
        for ((file, label) in listOf(releasePkgbuild to "release", gitPkgbuild to "git")) {
            val line = file.readText().lineSequence()
                .firstOrNull { it.trimStart().startsWith("depends=") }
                ?: error("$label entry: the depends= line is missing")
            assertTrue(line.contains("vlc"), "$label entry: vlc is missing from $line")
        }
    }

    @Test
    fun `release entry downloads the portable zip and the icon from the release`() {
        val text = releasePkgbuild.readText()
        assertTrue(
            text.contains("releases/download/v\${pkgver}/n-zik-desktop-compagnon-\${pkgver}-linux-portable.zip"),
            "release entry must download the portable zip from the GitHub release (v<pkgver>)"
        )
        assertTrue(
            text.contains("assets/design/icon-linux.png"),
            "release entry must fetch the shared icon-linux.png"
        )
    }

    @Test
    fun `git entry clones main and builds with Gradle on JDK 21`() {
        val text = gitPkgbuild.readText()
        // The makepkg source syntax is "name::url": the custom clone-dir name comes BEFORE the "::"
        // (makepkg's get_url() strips the prefix before the first "::"). It pins the lowercase clone
        // dir, while pkgver()/package() cd into ${_pkgname} (a case-sensitive FS would reject the
        // mixed-case GitHub repo basename otherwise).
        assertTrue(
            text.contains("source=(\"\${_pkgname}::git+\${url}#branch=main\")"),
            "git entry must clone the main branch into the pinned ${'$'}{_pkgname} dir (name::url order)"
        )
        assertTrue(text.contains(":app:createDistributable"), "git entry must build the app-image with Gradle")
        assertTrue(text.contains("jdk21-openjdk"), "git entry must require JDK 21 (jdk21-openjdk) as a makedep")
        // makepkg requires an integrity entry per source; the tracked branch moves, so a -git
        // source is checked with 'SKIP' (an empty sha256sums=() aborts the build).
        assertTrue(text.contains("sha256sums=('SKIP')"), "git entry must pin the git source checksum as SKIP")
        // package() runs under fakeroot (as root): the JVM user home would resolve to /root, so the
        // Gradle user home must be pinned to the build dir or the wrapper cannot write its cache.
        assertTrue(text.contains("GRADLE_USER_HOME"), "git entry must pin GRADLE_USER_HOME for the fakeroot build")
    }

    @Test
    fun `git entry derives pkgver from the main branch tip`() {
        val text = gitPkgbuild.readText()
        // makepkg (7.x) lints the pkgver variable before it runs pkgver(), so the function needs a
        // placeholder value or the build dies with "pkgver is not allowed to be empty".
        assertTrue(text.contains("pkgver="), "git entry needs a pkgver= placeholder next to pkgver()")
        // pkgver() runs before the source is downloaded, so there is no local clone to inspect:
        // the version must come from the remote (the tip of the tracked branch).
        assertTrue(
            text.contains("git ls-remote"),
            "git entry must derive pkgver from the remote branch tip (no clone exists when pkgver() runs)"
        )
    }

    @Test
    fun `both entries expose a space-free launcher for the desktop entry`() {
        // The app-image launcher sits in bin/ (named after the display name, which contains spaces);
        // a .desktop Exec pointing at that raw /opt path is fragile, so both entries install the app
        // under /opt/<package name> (the same dir as the .deb/.rpm), create a space-free
        // /usr/bin/<pkgname> symlink to the launcher, and point the Exec at the symlink.
        for ((file, label) in listOf(releasePkgbuild to "release", gitPkgbuild to "git")) {
            val text = file.readText()
            assertTrue(
                text.contains("Exec=/usr/bin/\${pkgname}"),
                "$label: the .desktop Exec must be the space-free /usr/bin/<pkgname> launcher"
            )
            assertTrue(
                !text.contains("Exec=/opt/"),
                "$label: the .desktop Exec must not point at the space-containing /opt path"
            )
            assertTrue(
                text.contains("ln -sf") && text.contains("usr/bin/\${pkgname}"),
                "$label: must create the /usr/bin/<pkgname> launcher symlink"
            )
            // The /opt install dir must be the lowercase package name — the same location the .deb/.rpm
            // use (jpackage --linux-package-name) — so all four Linux install paths agree.
            val optDir = if (file == releasePkgbuild) "\${pkgdir}/opt/\${pkgname}" else "\${pkgdir}/opt/\${_pkgname}"
            assertTrue(text.contains(optDir), "$label: must install under /opt/<package name> (the .deb/.rpm /opt dir)")
            // The menu category must stay Audio (matching the .deb/.rpm .desktop, fed by jpackage --linux-menu-group).
            assertTrue(text.contains("Categories=Audio;"), "$label: the .desktop must keep the Audio menu category")
        }
    }

    @Test
    fun `the shared Linux icon is committed`() {
        // jpackage requires a PNG for the .deb/.rpm; it is also the icon the AUR entries install.
        assertTrue(linuxIcon.isFile, "assets/design/icon-linux.png is missing (jpackage needs it for the .deb/.rpm)")
    }

    @Test
    fun `both entries install the declared license text`() {
        // license=(GPL-3.0-only) is declared in both entries: the actual license text must be
        // installed under /usr/share/licenses/<name>/LICENSE, or the field is cosmetic.
        for ((file, label, nameVar) in listOf(
                Triple(releasePkgbuild, "release", "pkgname"),
                Triple(gitPkgbuild, "git", "_pkgname")
            )) {
            val text = file.readText()
            assertTrue(text.contains("license=(GPL-3.0-only)"), "$label entry must declare GPL-3.0-only")
            val licensePath = "usr/share/licenses/" + "${'$'}{" + nameVar + "}" + "/LICENSE"
            assertTrue(text.contains(licensePath), "$label entry must install the LICENSE text under $licensePath")
        }
        // The portable zip does not contain the LICENSE, so the release entry fetches it from the
        // release tag as a third source (next to the icon).
        assertTrue(
            releasePkgbuild.readText().contains("raw.githubusercontent.com/N-Zik-Group/N-Zik-Desktop-Compagnon/v\${pkgver}/LICENSE"),
            "release entry must fetch the LICENSE text from the release tag (it is not in the portable zip)"
        )
    }
}
