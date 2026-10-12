package app.n_zik.compagnon.bridge.pairing

import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.util.logging.Logger
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * The app data dir (the 2026-10-12 XDG move of the Linux data dir off the bare home dir): the
 * [CredentialStore.resolveAppDataDir] platform matrix (pure) and the one-time migration of the
 * legacy `~/N-Zik Desktop Compagnon/` into the XDG dir (move, merge, no-op).
 */
class CredentialStoreDirTest {

    private val log = Logger.getLogger("CredentialStoreDirTest")

    // ---- resolveAppDataDir matrix ------------------------------------------------------------------------

    @Test
    fun `windows uses the appdata dir`() {
        // The expected value is built with Paths.get (not a Windows-separator string): on a
        // Linux test host the backslash base is a single component and the join uses '/',
        // so only the CHOSEN BASE is what this assertion pins.
        val dir = CredentialStore.resolveAppDataDir("Windows 11", "C:\\Users\\x\\AppData\\Roaming", null, "C:\\Users\\x")
        assertEquals(Paths.get("C:\\Users\\x\\AppData\\Roaming", "N-Zik Desktop Compagnon"), dir)
    }

    @Test
    fun `windows with a blank appdata falls back to the home dir`() {
        val dir = CredentialStore.resolveAppDataDir("Windows 11", "  ", null, "C:\\Users\\x")
        assertEquals(Paths.get("C:\\Users\\x", "N-Zik Desktop Compagnon"), dir)
    }

    @Test
    fun `linux uses the xdg config dir`() {
        val dir = CredentialStore.resolveAppDataDir("Linux", null, null, "/home/u")
        assertEquals("/home/u/.config/N-Zik Desktop Compagnon", dir.toString())
    }

    @Test
    fun `linux honours the xdg config home override`() {
        val dir = CredentialStore.resolveAppDataDir("Linux", null, "/custom/config", "/home/u")
        assertEquals("/custom/config/N-Zik Desktop Compagnon", dir.toString())
    }

    @Test
    fun `linux ignores the windows appdata var`() {
        val dir = CredentialStore.resolveAppDataDir("Linux", "C:\\Users\\x\\AppData\\Roaming", null, "/home/u")
        assertEquals("/home/u/.config/N-Zik Desktop Compagnon", dir.toString())
    }

    @Test
    fun `linux ignores a blank xdg config home`() {
        // The XDG_CONFIG_HOME override must be BLANK-free: an empty or whitespace var falls back
        // to the ~/.config default (a blank base would resolve to a CWD-relative dir)
        for (blank in listOf("", "  ")) {
            val dir = CredentialStore.resolveAppDataDir("Linux", null, blank, "/home/u")
            assertEquals("/home/u/.config/N-Zik Desktop Compagnon", dir.toString())
        }
    }

    @Test
    fun `an unsupported os uses the home dir`() {
        val dir = CredentialStore.resolveAppDataDir("Mac OS X", null, null, "/Users/u")
        assertEquals("/Users/u/N-Zik Desktop Compagnon", dir.toString())
    }

    // ---- legacy migration ----------------------------------------------------------------------------------

    @Test
    fun `the legacy dir is moved when the target does not exist yet`(@TempDir temp: Path) {
        val legacy = temp.resolve("legacy-home/N-Zik Desktop Compagnon")
        val target = temp.resolve("xdg-config/N-Zik Desktop Compagnon")
        Files.createDirectories(legacy)
        Files.writeString(legacy.resolve("pairing.json"), "{}")
        Files.createDirectories(legacy.resolve("cache/audio"))
        Files.writeString(legacy.resolve("cache/audio/track1.flac"), "audio")

        CredentialStore.migrateLegacyAppDataDir(legacy, target, log)

        assertFalse(Files.exists(legacy), "the legacy dir must be gone after the move")
        assertTrue(Files.exists(target.resolve("pairing.json")))
        assertEquals("audio", Files.readString(target.resolve("cache/audio/track1.flac")))
    }

    @Test
    fun `nothing happens when the legacy dir does not exist`(@TempDir temp: Path) {
        val legacy = temp.resolve("missing")
        val target = temp.resolve("target")
        Files.createDirectories(target)

        CredentialStore.migrateLegacyAppDataDir(legacy, target, log)

        assertFalse(Files.exists(legacy))
        assertEquals(0, Files.list(target).count())
    }

    @Test
    fun `when both dirs exist the legacy files the target lacks are merged in`(@TempDir temp: Path) {
        val legacy = temp.resolve("legacy-home/N-Zik Desktop Compagnon")
        val target = temp.resolve("xdg-config/N-Zik Desktop Compagnon")
        Files.createDirectories(legacy.resolve("cache"))
        Files.createDirectories(target.resolve("cache"))
        Files.writeString(legacy.resolve("settings.json"), "legacy-settings")
        Files.writeString(legacy.resolve("cache/track1.flac"), "legacy-audio")
        Files.writeString(target.resolve("settings.json"), "fresh-settings")

        CredentialStore.migrateLegacyAppDataDir(legacy, target, log)

        assertFalse(Files.exists(legacy), "the legacy dir must be gone after the merge")
        // The target's own file wins (the fresh app state is never clobbered)
        assertEquals("fresh-settings", Files.readString(target.resolve("settings.json")))
        // The legacy-only file arrives
        assertEquals("legacy-audio", Files.readString(target.resolve("cache/track1.flac")))
    }

    @Test
    fun `a legacy dir that is a file is ignored`(@TempDir temp: Path) {
        val legacy = temp.resolve("legacy-home/N-Zik Desktop Compagnon")
        Files.createDirectories(legacy.parent)
        Files.writeString(legacy, "i-am-a-file")
        val target = temp.resolve("xdg-config/N-Zik Desktop Compagnon")

        CredentialStore.migrateLegacyAppDataDir(legacy, target, log)

        assertTrue(Files.isRegularFile(legacy))
        assertFalse(Files.exists(target))
    }

    @Test
    fun `a legacy dir that is the target is a no-op`(@TempDir temp: Path) {
        // XDG_CONFIG_HOME resolving into the home dir makes legacy == target: the migration must
        // not move the dir into itself
        val dir = temp.resolve("home/.config/N-Zik Desktop Compagnon")
        Files.createDirectories(dir)
        Files.writeString(dir.resolve("pairing.json"), "{}")

        CredentialStore.migrateLegacyAppDataDir(dir, dir, log)

        assertTrue(Files.isDirectory(dir))
        assertEquals("{}", Files.readString(dir.resolve("pairing.json")))
    }

    @Test
    fun `appDataDirFor runs the one-time migration on linux only`(@TempDir temp: Path) {
        // The CALL-SITE wiring (dropping the migrateLegacyAppDataDir line, or gating it on the
        // wrong OS, would strand the legacy dir — every consumer reads the empty new dir, forced
        // re-pair, green suite): a planted legacy dir must be moved into the resolved XDG dir
        val home = temp.resolve("home")
        val legacy = home.resolve("N-Zik Desktop Compagnon")
        Files.createDirectories(legacy.resolve("cache/audio"))
        Files.writeString(legacy.resolve("pairing.json"), "{}")
        Files.writeString(legacy.resolve("cache/audio/track1.flac"), "audio")

        val dir = CredentialStore.appDataDirFor("Linux", null, null, home.toString(), log)

        assertEquals(home.resolve(".config/N-Zik Desktop Compagnon"), dir)
        assertFalse(Files.exists(legacy), "the legacy dir must be gone after the one-time migration")
        assertEquals("{}", Files.readString(dir.resolve("pairing.json")))
        assertEquals("audio", Files.readString(dir.resolve("cache/audio/track1.flac")))
    }

    @Test
    fun `appDataDirFor skips the migration off linux`(@TempDir temp: Path) {
        val home = temp.resolve("home")
        val legacy = home.resolve("N-Zik Desktop Compagnon")
        Files.createDirectories(legacy)
        Files.writeString(legacy.resolve("pairing.json"), "{}")

        val dir = CredentialStore.appDataDirFor("Windows 11", null, null, home.toString(), log)

        assertEquals(home.resolve("N-Zik Desktop Compagnon"), dir)
        assertTrue(Files.isDirectory(legacy), "off Linux the legacy dir is never touched")
    }

    @Test
    fun `a dangling symlink in the legacy dir does not abort the merge`(@TempDir temp: Path) {
        // A symlink is copied AS a link (never followed): a dangling (or cross-dir) target must
        // not throw and abort the whole walk — the rest of the merge then never ran, the legacy
        // dir survived, and every launch re-tried and re-failed the migration
        val legacy = temp.resolve("legacy-home/N-Zik Desktop Compagnon")
        val target = temp.resolve("xdg-config/N-Zik Desktop Compagnon")
        Files.createDirectories(legacy)
        Files.createDirectories(target)
        Files.writeString(legacy.resolve("settings.json"), "legacy-settings")
        Files.createSymbolicLink(legacy.resolve("broken.flac"), temp.resolve("does-not-exist"))
        Files.writeString(legacy.resolve("cache.flac"), "audio")

        CredentialStore.migrateLegacyAppDataDir(legacy, target, log)

        assertFalse(Files.exists(legacy), "the legacy dir must be gone even with a dangling symlink inside")
        assertEquals("legacy-settings", Files.readString(target.resolve("settings.json")))
        assertTrue(Files.isSymbolicLink(target.resolve("broken.flac")), "the symlink is copied as a link")
        assertEquals("audio", Files.readString(target.resolve("cache.flac")))
    }

    @Test
    fun `a non-empty legacy dir on another filesystem is migrated by copy`(@TempDir temp: Path) {
        // A cross-filesystem move of a NON-EMPTY dir is impossible (the rename cannot cross the
        // mount — DirectoryNotEmptyException, even for the plain `Files.move` fallback): the copy
        // fallback must migrate the data instead of leaving it behind (the invisible re-pair cost).
        // Needs two distinct filesystems on the test host (WSL: the 9P mount vs ext4; some Linux
        // boxes: a /dev/shm tmpfs) — skipped where the host provides one.
        val other = listOf(Paths.get("/dev/shm"), Paths.get("/mnt/d"))
            .firstOrNull { p ->
                Files.isDirectory(p) && Files.isWritable(p) &&
                    runCatching { Files.getFileStore(temp) != Files.getFileStore(p) }.getOrDefault(false)
            }
        Assumptions.assumeTrue(other != null, "one filesystem on this host — the cross-device fallback cannot be exercised")
        // non-null: the assumption above aborts the test otherwise (the only null path of `other`)
        val base = other!!.resolve("nzik-cross-device-${System.nanoTime()}")
        try {
            val legacy = base.resolve("N-Zik Desktop Compagnon")
            Files.createDirectories(legacy.resolve("cache"))
            Files.writeString(legacy.resolve("pairing.json"), "{}")
            Files.writeString(legacy.resolve("cache/track1.flac"), "audio")
            val target = temp.resolve("xdg-config/N-Zik Desktop Compagnon")

            CredentialStore.migrateLegacyAppDataDir(legacy, target, log)

            assertFalse(Files.exists(legacy), "the legacy dir must be gone after the cross-device copy")
            assertEquals("{}", Files.readString(target.resolve("pairing.json")))
            assertEquals("audio", Files.readString(target.resolve("cache/track1.flac")))
        } finally {
            runCatching {
                Files.walkFileTree(base, object : SimpleFileVisitor<Path>() {
                    override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                        Files.deleteIfExists(file)
                        return FileVisitResult.CONTINUE
                    }

                    override fun postVisitDirectory(directory: Path, exc: java.io.IOException?) = FileVisitResult.CONTINUE.also {
                        if (exc == null) Files.deleteIfExists(directory)
                    }
                })
            }
        }
    }
}
