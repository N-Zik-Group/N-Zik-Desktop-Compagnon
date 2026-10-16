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
        val dir = CredentialStore.resolveAppDataDir("Windows 11", "C:\\Users\\x\\AppData\\Roaming", null, "C:\\Users\\x", "N-Zik Desktop Compagnon")
        assertEquals(Paths.get("C:\\Users\\x\\AppData\\Roaming", "N-Zik Desktop Compagnon"), dir)
    }

    @Test
    fun `windows with a blank appdata falls back to the home dir`() {
        val dir = CredentialStore.resolveAppDataDir("Windows 11", "  ", null, "C:\\Users\\x", "N-Zik Desktop Compagnon")
        assertEquals(Paths.get("C:\\Users\\x", "N-Zik Desktop Compagnon"), dir)
    }

    @Test
    fun `windows uses the per-channel data dir name`() {
        // The 2026-10-16 channel separation: dev / beta carry the suffixed product name, stable
        // keeps the base — the same rule as the install dir ([AppInfo.productName])
        val dev = CredentialStore.resolveAppDataDir("Windows 11", "C:\\Users\\x\\AppData\\Roaming", null, "C:\\Users\\x", "N-Zik Desktop Compagnon DEV")
        assertEquals(Paths.get("C:\\Users\\x\\AppData\\Roaming", "N-Zik Desktop Compagnon DEV"), dev)
        val beta = CredentialStore.resolveAppDataDir("Windows 11", "C:\\Users\\x\\AppData\\Roaming", null, "C:\\Users\\x", "N-Zik Desktop Compagnon BETA")
        assertEquals(Paths.get("C:\\Users\\x\\AppData\\Roaming", "N-Zik Desktop Compagnon BETA"), beta)
        val stable = CredentialStore.resolveAppDataDir("Windows 11", "C:\\Users\\x\\AppData\\Roaming", null, "C:\\Users\\x", "N-Zik Desktop Compagnon")
        assertEquals(Paths.get("C:\\Users\\x\\AppData\\Roaming", "N-Zik Desktop Compagnon"), stable)
    }

    // The Linux expected values are built with Paths.get too (like the Windows tests above):
    // on a Windows test host the '/' base is joined with '\' so only the CHOSEN BASE is pinned;
    // on a Linux host both forms agree.

    @Test
    fun `linux uses the xdg config dir`() {
        val dir = CredentialStore.resolveAppDataDir("Linux", null, null, "/home/u", "N-Zik Desktop Compagnon")
        assertEquals(Paths.get("/home/u/.config/N-Zik Desktop Compagnon"), dir)
    }

    @Test
    fun `linux honours the xdg config home override`() {
        val dir = CredentialStore.resolveAppDataDir("Linux", null, "/custom/config", "/home/u", "N-Zik Desktop Compagnon")
        assertEquals(Paths.get("/custom/config/N-Zik Desktop Compagnon"), dir)
    }

    @Test
    fun `linux ignores the windows appdata var`() {
        val dir = CredentialStore.resolveAppDataDir("Linux", "C:\\Users\\x\\AppData\\Roaming", null, "/home/u", "N-Zik Desktop Compagnon")
        assertEquals(Paths.get("/home/u/.config/N-Zik Desktop Compagnon"), dir)
    }

    @Test
    fun `linux uses the per-channel xdg config dir`() {
        // The 2026-10-16 channel separation rides the same XDG base with the suffixed name
        val dir = CredentialStore.resolveAppDataDir("Linux", null, null, "/home/u", "N-Zik Desktop Compagnon DEV")
        assertEquals(Paths.get("/home/u/.config/N-Zik Desktop Compagnon DEV"), dir)
    }

    @Test
    fun `linux ignores a blank xdg config home`() {
        // The XDG_CONFIG_HOME override must be BLANK-free: an empty or whitespace var falls back
        // to the ~/.config default (a blank base would resolve to a CWD-relative dir)
        for (blank in listOf("", "  ")) {
            val dir = CredentialStore.resolveAppDataDir("Linux", null, blank, "/home/u", "N-Zik Desktop Compagnon")
            assertEquals(Paths.get("/home/u/.config/N-Zik Desktop Compagnon"), dir)
        }
    }

    @Test
    fun `an unsupported os uses the home dir`() {
        val dir = CredentialStore.resolveAppDataDir("Mac OS X", null, null, "/Users/u", "N-Zik Desktop Compagnon")
        assertEquals(Paths.get("/Users/u/N-Zik Desktop Compagnon"), dir)
    }

    // ---- channel-separation copy (the 2026-10-16 shared -> per-channel dir) -----------------------------------

    @Test
    fun `copySharedIntoChannel copies the files the channel dir lacks`(@TempDir temp: Path) {
        val shared = temp.resolve("config/N-Zik Desktop Compagnon")
        val channel = temp.resolve("config/N-Zik Desktop Compagnon DEV")
        Files.createDirectories(shared.resolve("cache"))
        Files.writeString(shared.resolve("pairing.json"), "shared")
        Files.writeString(shared.resolve("cache/track1.flac"), "audio")

        CredentialStore.copySharedIntoChannel(shared, channel, log)

        assertTrue(Files.isDirectory(shared), "the shared source SURVIVES the channel copy")
        assertEquals("shared", Files.readString(channel.resolve("pairing.json")))
        assertEquals("audio", Files.readString(channel.resolve("cache/track1.flac")))
    }

    @Test
    fun `copySharedIntoChannel is a no-op when the shared dir does not exist`(@TempDir temp: Path) {
        val shared = temp.resolve("config/missing")
        val channel = temp.resolve("config/N-Zik Desktop Compagnon DEV")

        CredentialStore.copySharedIntoChannel(shared, channel, log)

        assertFalse(Files.exists(channel), "nothing is created when there is no shared data to copy")
    }

    @Test
    fun `copySharedIntoChannel is a no-op when shared and channel are the same dir`(@TempDir temp: Path) {
        // base-name channels: shared == channel, the dir must not be walked into itself
        val dir = temp.resolve("config/N-Zik Desktop Compagnon")
        Files.createDirectories(dir)
        Files.writeString(dir.resolve("pairing.json"), "{}")

        CredentialStore.copySharedIntoChannel(dir, dir, log)

        assertTrue(Files.isDirectory(dir))
        assertEquals("{}", Files.readString(dir.resolve("pairing.json")))
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

        val dir = CredentialStore.appDataDirFor("Linux", null, null, home.toString(), "N-Zik Desktop Compagnon", log)

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

        val dir = CredentialStore.appDataDirFor("Windows 11", null, null, home.toString(), "N-Zik Desktop Compagnon", log)

        assertEquals(home.resolve("N-Zik Desktop Compagnon"), dir)
        assertTrue(Files.isDirectory(legacy), "off Linux the legacy dir is never touched")
    }

    @Test
    fun `appDataDirFor with a channel name copies the shared dir but keeps it`(@TempDir temp: Path) {
        // The 2026-10-16 channel separation wiring: a dev build resolves its OWN dir
        // (`<xdg>/N-Zik Desktop Compagnon DEV`) and, on first launch, COPIES the files the shared
        // base dir has that its dir lacks — the shared dir SURVIVES (it stays the live dir of the
        // base-named channels, stable / debug / -git). Dropping the copySharedIntoChannel line, or
        // gating it on the wrong name, would strand the channel dir — forced re-pair, green suite.
        val home = temp.resolve("home")
        val xdg = home.resolve(".config")
        val shared = xdg.resolve("N-Zik Desktop Compagnon")
        Files.createDirectories(shared.resolve("cache/audio"))
        Files.writeString(shared.resolve("pairing.json"), "{}")
        Files.writeString(shared.resolve("settings.json"), "shared-settings")
        Files.writeString(shared.resolve("cache/audio/track1.flac"), "audio")

        val dir = CredentialStore.appDataDirFor("Linux", null, xdg.toString(), home.toString(), "N-Zik Desktop Compagnon DEV", log)

        assertEquals(xdg.resolve("N-Zik Desktop Compagnon DEV"), dir)
        // the shared dir SURVIVES (a stable build installed side-by-side still reads it)
        assertTrue(Files.isDirectory(shared), "the shared base dir must survive the channel copy")
        assertEquals("{}", Files.readString(dir.resolve("pairing.json")))
        assertEquals("shared-settings", Files.readString(dir.resolve("settings.json")))
        assertEquals("audio", Files.readString(dir.resolve("cache/audio/track1.flac")))
    }

    @Test
    fun `appDataDirFor with the base name does no channel copy`(@TempDir temp: Path) {
        // stable / debug / -git keep the base name: the channel-separation copy must NOT run
        // (shared == channel), so the dir is exactly the shared one and nothing is duplicated
        val home = temp.resolve("home")
        val xdg = home.resolve(".config")
        val shared = xdg.resolve("N-Zik Desktop Compagnon")
        Files.createDirectories(shared)
        Files.writeString(shared.resolve("pairing.json"), "{}")

        val dir = CredentialStore.appDataDirFor("Linux", null, xdg.toString(), home.toString(), "N-Zik Desktop Compagnon", log)

        assertEquals(shared, dir)
        assertEquals(1L, Files.list(dir).count(), "no duplication: the base-name dir IS the shared dir (no channel copy ran)")
    }

    @Test
    fun `appDataDirFor channel copy never clobbers the channel dir`(@TempDir temp: Path) {
        // the channel dir's own (fresh) files win over the shared copy — a dev build that already
        // paired must not be reset to the shared pairing by a later launch
        val home = temp.resolve("home")
        val xdg = home.resolve(".config")
        val shared = xdg.resolve("N-Zik Desktop Compagnon")
        val channel = xdg.resolve("N-Zik Desktop Compagnon DEV")
        Files.createDirectories(shared)
        Files.writeString(shared.resolve("pairing.json"), "SHARED")
        Files.createDirectories(channel)
        Files.writeString(channel.resolve("pairing.json"), "FRESH")
        Files.writeString(channel.resolve("cache.flac"), "channel-audio")

        val dir = CredentialStore.appDataDirFor("Linux", null, xdg.toString(), home.toString(), "N-Zik Desktop Compagnon DEV", log)

        assertEquals("FRESH", Files.readString(dir.resolve("pairing.json")), "the channel's own pairing must win")
        assertEquals("channel-audio", Files.readString(dir.resolve("cache.flac")))
        assertTrue(Files.isDirectory(shared), "the shared dir still survives")
    }

    @Test
    fun `a dangling symlink in the legacy dir does not abort the merge`(@TempDir temp: Path) {
        // A symlink is copied AS a link (never followed): a dangling (or cross-dir) target must
        // not throw and abort the whole walk — the rest of the merge then never ran, the legacy
        // dir survived, and every launch re-tried and re-failed the migration
        // Symlink creation needs elevated rights (or developer mode) on Windows — skipped where
        // the host cannot create symlinks (the house pattern: the cross-device test below skips
        // on single-filesystem hosts)
        val probe = temp.resolve("symlink-probe")
        val symlinksCapable = runCatching { Files.createSymbolicLink(probe, temp.resolve("probe-target")) }
            .onSuccess { Files.deleteIfExists(probe) }
            .isSuccess
        Assumptions.assumeTrue(symlinksCapable, "this host cannot create symbolic links (Windows without admin/developer mode)")
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
