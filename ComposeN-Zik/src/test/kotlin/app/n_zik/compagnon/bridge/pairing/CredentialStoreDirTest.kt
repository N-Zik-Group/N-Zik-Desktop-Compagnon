package app.n_zik.compagnon.bridge.pairing

import java.io.File
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.util.logging.Logger
import app.n_zik.compagnon.AppInfo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * The app data dir (the 2026-10-12 XDG move of the Linux data dir off the bare home dir, the
 * 2026-10-16 storage regrouping and the self-contained portable): the
 * [CredentialStore.resolveAppDataDir] platform matrix (pure), the one-time migration of the
 * legacy `~/N-Zik Desktop Compagnon/` into the XDG dir (move, merge, no-op), the channel copy
 * (shared base → suffixed dir, the base survives), the legacy-beta merge-back (`… BETA` → base,
 * the base wins, `… BETA` survives) and the self-contained-portable decision
 * ([CredentialStore.portableDataRootOf] — the marker matrix).
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
        // The storage name rides as the path component (whatever name the caller passes —
        // [AppInfo.dataName] in production: the base for stable / beta, the suffixed name for
        // dev / debug / git). This pins the resolution, not the mapping (pinned in AppInfoTest)
        val dev = CredentialStore.resolveAppDataDir("Windows 11", "C:\\Users\\x\\AppData\\Roaming", null, "C:\\Users\\x", "N-Zik Desktop Compagnon DEV")
        assertEquals(Paths.get("C:\\Users\\x\\AppData\\Roaming", "N-Zik Desktop Compagnon DEV"), dev)
        val git = CredentialStore.resolveAppDataDir("Windows 11", "C:\\Users\\x\\AppData\\Roaming", null, "C:\\Users\\x", "N-Zik Desktop Compagnon GIT")
        assertEquals(Paths.get("C:\\Users\\x\\AppData\\Roaming", "N-Zik Desktop Compagnon GIT"), git)
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
        // The storage name rides the same XDG base with the suffixed name (dev / debug / git —
        // [AppInfo.dataName])
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

    @Test
    fun `copySharedIntoChannel is one-time — a channel dir with its own files is never re-walked`(@TempDir temp: Path) {
        // The spec's "one-time copy": once the channel dir carries its own files, the base's
        // LATER files must not flow in (the channels are isolated after first launch — the
        // base's fresh pairing / new cache entries are the base's own), and the per-launch full
        // re-walk (a stat per base file) is skipped
        val shared = temp.resolve("config/N-Zik Desktop Compagnon")
        val channel = temp.resolve("config/N-Zik Desktop Compagnon DEV")
        Files.createDirectories(shared)
        Files.writeString(shared.resolve("pairing.json"), "base")
        Files.createDirectories(channel)
        Files.writeString(channel.resolve("pairing.json"), "channel")

        // First launch after the channel dir already has its own file → the walk is skipped
        CredentialStore.copySharedIntoChannel(shared, channel, log)
        assertEquals("channel", Files.readString(channel.resolve("pairing.json")), "the channel's own file wins")
        // The base gains a file AFTER first launch (a fresh base pairing, a new cache entry) —
        // a second launch must NOT copy it in (the channel owns its state)
        Files.writeString(shared.resolve("settings.json"), "base-later")
        CredentialStore.copySharedIntoChannel(shared, channel, log)
        assertFalse(Files.exists(channel.resolve("settings.json")), "the base's later files are the base's own, never channel data")
        // An EMPTY channel dir still gets the one-time copy (a wiped channel recovers the base files)
        val wiped = temp.resolve("config/N-Zik Desktop Compagnon DEV wiped")
        Files.createDirectories(wiped)
        CredentialStore.copySharedIntoChannel(shared, wiped, log)
        assertTrue(Files.exists(wiped.resolve("settings.json")), "an empty channel dir still receives the one-time copy")
    }

    @Test
    fun `copySharedIntoChannel re-walks a channel dir that carries subdirs but no files (an interrupted first copy)`(@TempDir temp: Path) {
        // The skip counts FILES only: the walk creates dirs only as parents of copied files, so a
        // dir whose top level holds subdirs alone is by definition a first copy that died partway
        // (it copied into cache/ before the top-level entries) — it must be re-walked on the next
        // launch, or the channel keeps a PERMANENT partial state (missing pairing.json → forced
        // re-pair). A dir that carries its own file is still skipped (the test above)
        val shared = temp.resolve("config/N-Zik Desktop Compagnon")
        val channel = temp.resolve("config/N-Zik Desktop Compagnon DEV")
        Files.createDirectories(shared)
        Files.writeString(shared.resolve("pairing.json"), "base")
        Files.writeString(shared.resolve("settings.json"), "base-settings")
        Files.createDirectories(shared.resolve("cache"))
        Files.writeString(shared.resolve("cache/track1.flac"), "audio")
        // The interrupted first copy: a subdir with a file inside (top-level listFiles sees a
        // directory only — no top-level file)
        Files.createDirectories(channel.resolve("cache"))
        Files.writeString(channel.resolve("cache/track2.flac"), "partial")

        CredentialStore.copySharedIntoChannel(shared, channel, log)

        assertTrue(Files.exists(channel.resolve("pairing.json")), "the missing top-level files arrive on the re-walk")
        assertTrue(Files.exists(channel.resolve("settings.json")), "the missing top-level files arrive on the re-walk")
        assertEquals("partial", Files.readString(channel.resolve("cache/track2.flac")), "the partial copy's own file is never clobbered")
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

        val dir = CredentialStore.appDataDirFor("Linux", null, null, home.toString(), "N-Zik Desktop Compagnon", emptyList(), log)

        assertEquals(home.resolve(".config/N-Zik Desktop Compagnon"), dir)
        assertFalse(Files.exists(legacy), "the legacy dir must be gone after the one-time migration")
        assertEquals("{}", Files.readString(dir.resolve("pairing.json")))
        assertEquals("audio", Files.readString(dir.resolve("cache/audio/track1.flac")))
    }

    @Test
    fun `appDataDirFor with a channel name does not move the legacy dir into the channel (base only)`(@TempDir temp: Path) {
        // The legacy bare-home dir is the PRE-CHANNEL home of the data: it migrates into the
        // BASE dir only. A channel-suffixed build that launches FIRST must not move it into its
        // own dir — stable / beta would then read an empty dir (forced re-pair, the data
        // stranded in the channel dir). The channel build picks the data up through the base →
        // channel copy instead, once the base has it
        val home = temp.resolve("home")
        val xdg = home.resolve(".config")
        val legacy = home.resolve("N-Zik Desktop Compagnon")
        Files.createDirectories(legacy.resolve("cache/audio"))
        Files.writeString(legacy.resolve("pairing.json"), "{}")
        Files.writeString(legacy.resolve("cache/audio/track1.flac"), "audio")

        // First launch: the DEV build — the legacy dir must SURVIVE (not moved into … DEV)
        val devDir = CredentialStore.appDataDirFor("Linux", null, xdg.toString(), home.toString(), "N-Zik Desktop Compagnon DEV", emptyList(), log)
        assertEquals(xdg.resolve("N-Zik Desktop Compagnon DEV"), devDir)
        assertTrue(Files.isDirectory(legacy), "the legacy dir must NOT move into the channel dir")
        assertFalse(Files.exists(devDir.resolve("pairing.json")), "the channel dir gets nothing yet (the base dir is still empty)")

        // Second launch: the stable build — the legacy dir migrates into the BASE dir
        val stableDir = CredentialStore.appDataDirFor("Linux", null, xdg.toString(), home.toString(), "N-Zik Desktop Compagnon", emptyList(), log)
        assertEquals(xdg.resolve("N-Zik Desktop Compagnon"), stableDir)
        assertFalse(Files.exists(legacy), "the legacy dir migrates into the base dir on the stable launch")
        assertEquals("{}", Files.readString(stableDir.resolve("pairing.json")))

        // Third launch: the DEV build again — the base → channel one-time copy now delivers the data
        val devDir2 = CredentialStore.appDataDirFor("Linux", null, xdg.toString(), home.toString(), "N-Zik Desktop Compagnon DEV", emptyList(), log)
        assertEquals("{}", Files.readString(devDir2.resolve("pairing.json")), "the channel picks up the data through the base copy")
        assertEquals("audio", Files.readString(devDir2.resolve("cache/audio/track1.flac")))
    }

    @Test
    fun `appDataDirFor with beta's base name migrates the legacy dir too (the gate is the name, not the channel)`(@TempDir temp: Path) {
        // Beta SHARES the base storage name (2026-10-16 regrouping) — a beta launch IS a
        // base-named launch: the legacy bare-home dir must migrate into the base dir on it (the
        // gate is `dataDirName == AppInfo.NAME`, so re-tightening it to "stable only" would strand
        // beta's legacy data with a green suite), and the legacy `… BETA` merge-back runs alongside
        val home = temp.resolve("home")
        val xdg = home.resolve(".config")
        val legacy = home.resolve("N-Zik Desktop Compagnon")
        Files.createDirectories(legacy.resolve("cache/audio"))
        Files.writeString(legacy.resolve("pairing.json"), "{}")
        Files.writeString(legacy.resolve("cache/audio/track1.flac"), "audio")
        // The pre-regrouping isolated beta dir (the merge-back source)
        val legacyBeta = xdg.resolve("N-Zik Desktop Compagnon BETA")
        Files.createDirectories(legacyBeta)
        Files.writeString(legacyBeta.resolve("beta-only.json"), "beta")

        val betaDir = CredentialStore.appDataDirFor("Linux", null, xdg.toString(), home.toString(), "N-Zik Desktop Compagnon", CredentialStore.legacyDataDirNamesFor("beta"), log)

        assertFalse(Files.exists(legacy), "the legacy bare-home dir migrates into the base dir on the beta launch (beta shares the base name)")
        assertEquals("{}", Files.readString(betaDir.resolve("pairing.json")))
        assertEquals("audio", Files.readString(betaDir.resolve("cache/audio/track1.flac")))
        assertEquals("beta", Files.readString(betaDir.resolve("beta-only.json")), "the legacy beta dir merges its missing files back into the base")
        assertTrue(Files.isDirectory(legacyBeta), "the legacy beta dir SURVIVES the merge-back (never deleted)")
    }

    @Test
    fun `appDataDirFor skips the migration off linux`(@TempDir temp: Path) {
        val home = temp.resolve("home")
        val legacy = home.resolve("N-Zik Desktop Compagnon")
        Files.createDirectories(legacy)
        Files.writeString(legacy.resolve("pairing.json"), "{}")

        val dir = CredentialStore.appDataDirFor("Windows 11", null, null, home.toString(), "N-Zik Desktop Compagnon", emptyList(), log)

        assertEquals(home.resolve("N-Zik Desktop Compagnon"), dir)
        assertTrue(Files.isDirectory(legacy), "off Linux the legacy dir is never touched")
    }

    @Test
    fun `appDataDirFor with a channel name copies the shared dir but keeps it`(@TempDir temp: Path) {
        // The 2026-10-16 channel separation wiring: a dev build resolves its OWN dir
        // (`<xdg>/N-Zik Desktop Compagnon DEV`) and, on first launch, COPIES the files the shared
        // base dir has that its dir lacks — the shared dir SURVIVES (it stays the live dir of the
        // base-named channels — stable AND beta; dev / debug / -git are isolated). Dropping the copySharedIntoChannel line, or
        // gating it on the wrong name, would strand the channel dir — forced re-pair, green suite.
        val home = temp.resolve("home")
        val xdg = home.resolve(".config")
        val shared = xdg.resolve("N-Zik Desktop Compagnon")
        Files.createDirectories(shared.resolve("cache/audio"))
        Files.writeString(shared.resolve("pairing.json"), "{}")
        Files.writeString(shared.resolve("settings.json"), "shared-settings")
        Files.writeString(shared.resolve("cache/audio/track1.flac"), "audio")

        val dir = CredentialStore.appDataDirFor("Linux", null, xdg.toString(), home.toString(), "N-Zik Desktop Compagnon DEV", emptyList(), log)

        assertEquals(xdg.resolve("N-Zik Desktop Compagnon DEV"), dir)
        // the shared dir SURVIVES (a stable build installed side-by-side still reads it)
        assertTrue(Files.isDirectory(shared), "the shared base dir must survive the channel copy")
        assertEquals("{}", Files.readString(dir.resolve("pairing.json")))
        assertEquals("shared-settings", Files.readString(dir.resolve("settings.json")))
        assertEquals("audio", Files.readString(dir.resolve("cache/audio/track1.flac")))
    }

    @Test
    fun `appDataDirFor with the base name does no channel copy`(@TempDir temp: Path) {
        // stable AND beta keep the base name: the channel-separation copy must NOT run
        // (shared == channel), so the dir is exactly the shared one and nothing is duplicated
        val home = temp.resolve("home")
        val xdg = home.resolve(".config")
        val shared = xdg.resolve("N-Zik Desktop Compagnon")
        Files.createDirectories(shared)
        Files.writeString(shared.resolve("pairing.json"), "{}")

        val dir = CredentialStore.appDataDirFor("Linux", null, xdg.toString(), home.toString(), "N-Zik Desktop Compagnon", emptyList(), log)

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

        val dir = CredentialStore.appDataDirFor("Linux", null, xdg.toString(), home.toString(), "N-Zik Desktop Compagnon DEV", emptyList(), log)

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

    // ---- storage regrouping (2026-10-16: stable + beta share the base, dev / debug / git isolated) ----

    @Test
    fun `the legacyDataDirNamesFor beta is the legacy BETA dir and the other channels none`() {
        // The production wiring of the merge-back: only beta had a per-channel dir before the
        // regrouping (the 2026-10-16 isolated-beta `… BETA` = the beta INSTALL identity)
        assertEquals(
            listOf("N-Zik Desktop Compagnon BETA"),
            CredentialStore.legacyDataDirNamesFor("beta"),
        )
        for (channel in listOf("stable", "dev", "debug", "git")) {
            assertTrue(
                CredentialStore.legacyDataDirNamesFor(channel).isEmpty(),
                "$channel had no legacy per-channel dir of its own"
            )
        }
    }

    @Test
    fun `appDataDirFor for beta merges the legacy BETA dir into the base (the base wins, BETA survives)`(@TempDir temp: Path) {
        // The 2026-10-16 storage regrouping, beta side: beta now SHAREDs the base storage with
        // stable. A beta build that still has the legacy isolated `… BETA` dir from the
        // 2026-10-16 channel separation merges its files back into the base — the base's own
        // files win, the legacy-only files arrive, and the `… BETA` dir SURVIVES (a copy, never
        // a move: the pairing can never be lost)
        val home = temp.resolve("home")
        val xdg = home.resolve(".config")
        val base = xdg.resolve(AppInfo.dataName("beta"))
        val legacy = xdg.resolve("N-Zik Desktop Compagnon BETA")
        Files.createDirectories(base)
        Files.writeString(base.resolve("pairing.json"), "BASE")
        Files.writeString(base.resolve("settings.json"), "BASE-SETTINGS")
        Files.createDirectories(legacy.resolve("cache/audio"))
        Files.writeString(legacy.resolve("pairing.json"), "BETA")
        Files.writeString(legacy.resolve("settings.json"), "BETA-SETTINGS")
        Files.writeString(legacy.resolve("cache/audio/track1.flac"), "beta-audio")

        val dir = CredentialStore.appDataDirFor(
            "Linux", null, xdg.toString(), home.toString(),
            AppInfo.dataName("beta"),
            CredentialStore.legacyDataDirNamesFor("beta"),
            log,
        )

        assertEquals(base, dir, "beta shares the base storage name")
        assertEquals("BASE", Files.readString(dir.resolve("pairing.json")), "the base files win the merge")
        assertEquals("BASE-SETTINGS", Files.readString(dir.resolve("settings.json")), "the base files win the merge")
        assertEquals("beta-audio", Files.readString(dir.resolve("cache/audio/track1.flac")), "the legacy-only file arrives in the base")
        assertTrue(Files.isDirectory(legacy), "the legacy BETA dir SURVIVES the merge-back (never deleted)")
        assertEquals("BETA", Files.readString(legacy.resolve("pairing.json")), "the legacy dir is untouched (a copy, never a move)")
    }

    @Test
    fun `the beta merge-back is idempotent (a second launch copies nothing, the base still wins)`(@TempDir temp: Path) {
        // The merge is a one-time effect by construction (only the files the base LACKS are
        // copied): a second launch must not re-copy a stale BETA file over a fresh base one
        val home = temp.resolve("home")
        val xdg = home.resolve(".config")
        val base = xdg.resolve("N-Zik Desktop Compagnon")
        val legacy = xdg.resolve("N-Zik Desktop Compagnon BETA")
        Files.createDirectories(base)
        Files.createDirectories(legacy)
        Files.writeString(base.resolve("pairing.json"), "BASE")
        Files.writeString(legacy.resolve("pairing.json"), "BETA")

        val first = CredentialStore.appDataDirFor(
            "Linux", null, xdg.toString(), home.toString(),
            "N-Zik Desktop Compagnon",
            listOf("N-Zik Desktop Compagnon BETA"),
            log,
        )
        assertEquals("BASE", Files.readString(first.resolve("pairing.json")))
        // Simulate a later launch after the user re-paired on the base (fresh base state)
        Files.writeString(base.resolve("pairing.json"), "FRESH-BASE")
        val second = CredentialStore.appDataDirFor(
            "Linux", null, xdg.toString(), home.toString(),
            "N-Zik Desktop Compagnon",
            listOf("N-Zik Desktop Compagnon BETA"),
            log,
        )
        assertEquals("FRESH-BASE", Files.readString(second.resolve("pairing.json")), "the fresh base state is never clobbered by the stale BETA copy")
        assertTrue(Files.isDirectory(legacy), "the legacy dir survives every launch")
    }

    @Test
    fun `appDataDirFor for dev still copies the shared base into the suffixed dir`(@TempDir temp: Path) {
        // The isolated channels keep the pre-regrouping behavior: base → `… DEV` copy (the base
        // survives) — the regrouping only touched the beta side (shared base + legacy merge-back)
        val home = temp.resolve("home")
        val xdg = home.resolve(".config")
        val shared = xdg.resolve("N-Zik Desktop Compagnon")
        Files.createDirectories(shared)
        Files.writeString(shared.resolve("pairing.json"), "SHARED")

        val dir = CredentialStore.appDataDirFor(
            "Linux", null, xdg.toString(), home.toString(),
            AppInfo.dataName("dev"),
            CredentialStore.legacyDataDirNamesFor("dev"),
            log,
        )

        assertEquals(xdg.resolve("N-Zik Desktop Compagnon DEV"), dir)
        assertEquals("SHARED", Files.readString(dir.resolve("pairing.json")))
        assertTrue(Files.isDirectory(shared), "the base dir survives (stable + beta still read it)")
    }

    @Test
    fun `appDataDirFor for stable does no channel copy and no legacy merge`(@TempDir temp: Path) {
        // stable keeps the base name: neither the channel copy (shared == channel) nor a legacy
        // merge may run — the dir is exactly the base one
        val home = temp.resolve("home")
        val xdg = home.resolve(".config")
        val base = xdg.resolve("N-Zik Desktop Compagnon")
        Files.createDirectories(base)
        Files.writeString(base.resolve("pairing.json"), "{}")

        val dir = CredentialStore.appDataDirFor(
            "Linux", null, xdg.toString(), home.toString(),
            AppInfo.dataName("stable"),
            CredentialStore.legacyDataDirNamesFor("stable"),
            log,
        )

        assertEquals(base, dir)
        assertEquals(1L, Files.list(dir).count(), "no duplication, no merge: the base dir is exactly the base dir")
    }

    // ---- self-contained portable decision (portableDataRootOf — the marker matrix) ----

    private val windowsProduct = "N-Zik Desktop Compagnon"

    /** Plants a Windows portable layout (the launcher at the image ROOT) under [temp]. */
    private fun plantWindowsPortable(temp: Path, marker: String? = CredentialStore.PORTABLE_MARKER_CONTENT): Path {
        val root = temp.resolve("portable").resolve(windowsProduct)
        Files.createDirectories(root)
        Files.writeString(root.resolve("$windowsProduct.exe"), "launcher")
        if (marker != null) Files.writeString(root.resolve(CredentialStore.PORTABLE_MARKER_FILE), marker)
        return root.resolve("$windowsProduct.exe")
    }

    /** Plants a Linux portable layout (the launcher in `bin/` at the image root) under [temp]. */
    private fun plantLinuxPortable(temp: Path, marker: String? = CredentialStore.PORTABLE_MARKER_CONTENT): Path {
        val root = temp.resolve("portable").resolve(windowsProduct)
        Files.createDirectories(root.resolve("bin"))
        Files.writeString(root.resolve("bin").resolve(windowsProduct), "launcher")
        if (marker != null) Files.writeString(root.resolve(CredentialStore.PORTABLE_MARKER_FILE), marker)
        return root.resolve("bin").resolve(windowsProduct)
    }

    @Test
    fun `a valid marker at the Windows app root resolves the portable root`(@TempDir temp: Path) {
        // Windows layout: the launcher sits at the image root — the executable's OWN dir is the
        // app root (there is no bin/ level on Windows)
        val exe = plantWindowsPortable(temp)
        val root = CredentialStore.portableDataRootOf("Windows 11", emptyMap(), exe.toString(), windowsProduct)
        assertEquals(exe.parent, root)
    }

    @Test
    fun `a valid marker at the Linux app root resolves the portable root`(@TempDir temp: Path) {
        // Linux layout: the launcher lives at `<root>/bin/<launcher>` — the parent dir named
        // `bin` → its parent IS the app root
        val exe = plantLinuxPortable(temp)
        val root = CredentialStore.portableDataRootOf("Linux", emptyMap(), exe.toString(), windowsProduct)
        assertEquals(exe.parent.parent, root)
    }

    @Test
    fun `a Linux launcher outside a bin level degrades to null`(@TempDir temp: Path) {
        // No bin/ level on a Linux path → no app root → no marker to read (safe degradation)
        val root = temp.resolve("portable").resolve(windowsProduct)
        Files.createDirectories(root)
        Files.writeString(root.resolve("launcher"), "launcher")
        Files.writeString(root.resolve(CredentialStore.PORTABLE_MARKER_FILE), CredentialStore.PORTABLE_MARKER_CONTENT)
        assertNull(CredentialStore.portableDataRootOf("Linux", emptyMap(), root.resolve("launcher").toString(), windowsProduct))
    }

    @Test
    fun `a missing marker degrades to null (run-source and legacy portables)`(@TempDir temp: Path) {
        // No marker (a run-source launch, or a pre-marker portable): the data dir stays the
        // per-channel one — nothing is relocated
        for (os in listOf("Windows 11", "Linux")) {
            val exe = if (os == "Windows 11") plantWindowsPortable(temp, marker = null) else plantLinuxPortable(temp.resolve("l"), marker = null)
            assertNull(CredentialStore.portableDataRootOf(os, emptyMap(), exe.toString(), windowsProduct))
        }
    }

    @Test
    fun `a marker with any other content degrades to null (the strict reader)`(@TempDir temp: Path) {
        // The reader is strict: a stray or forged marker with ANY other content must not
        // relocate the data dir (only the exact constant counts — the write side is equally
        // strict, the archive entry is written by the two portable tasks only)
        for (content in listOf("", "PORTABLE", "portable\n", "portable ", "portable-extra")) {
            val exe = plantWindowsPortable(temp.resolve("case-${content.hashCode()}"), marker = content)
            assertNull(CredentialStore.portableDataRootOf("Windows 11", emptyMap(), exe.toString(), windowsProduct), "content '$content' must not count as a marker")
        }
    }

    @Test
    fun `an NSIS Windows install is excluded even with a marker (the uninstaller file wins)`(@TempDir temp: Path) {
        // The same discriminator the updater's isNsisWindowsInstall uses (duplicated here —
        // bridge/ must not import updater/): the launcher folder carrying the uninstaller
        // `Uninstall <product>.exe` is an NSIS install, never a portable
        val exe = plantWindowsPortable(temp)
        Files.writeString(exe.parent.resolve("Uninstall $windowsProduct.exe"), "uninstaller")
        assertNull(CredentialStore.portableDataRootOf("Windows 11", emptyMap(), exe.toString(), windowsProduct))
    }

    @Test
    fun `a package-managed opt install is excluded (the prefix rule)`(@TempDir temp: Path) {
        // The .deb/.rpm/Arch/AUR layout: the launcher under /opt/n-zik-desktop-compagnon — the
        // same prefix rule the updater's detection reads (checked before any I/O)
        assertNull(
            CredentialStore.portableDataRootOf(
                "Linux", emptyMap(),
                "/opt/n-zik-desktop-compagnon/bin/N-Zik Desktop Compagnon",
                windowsProduct,
            )
        )
    }

    @Test
    fun `a Flatpak environment is excluded (the FLATPAK_ID and container=flatpak signals)`(@TempDir temp: Path) {
        // The sandbox data dir is the runtime-mapped per-app config dir, never a portable one —
        // the same signals the updater's detection reads
        val exe = plantLinuxPortable(temp)
        for (env in listOf(mapOf("FLATPAK_ID" to "com.nzik.desktop.compagnon"), mapOf("container" to "flatpak"))) {
            assertNull(CredentialStore.portableDataRootOf("Linux", env, exe.toString(), windowsProduct), "env $env must exclude the portable decision")
        }
        // A blank FLATPAK_ID is NOT a sandbox (the updater's rule)
        val exe2 = plantLinuxPortable(temp.resolve("blank-env"))
        assertNotNull(CredentialStore.portableDataRootOf("Linux", mapOf("FLATPAK_ID" to "  "), exe2.toString(), windowsProduct))
    }

    @Test
    fun `an empty executable path degrades to null (no launch context)`(@TempDir temp: Path) {
        for (os in listOf("Windows 11", "Linux")) {
            assertNull(CredentialStore.portableDataRootOf(os, emptyMap(), "", windowsProduct))
            assertNull(CredentialStore.portableDataRootOf(os, emptyMap(), "   ", windowsProduct))
        }
    }

    @Test
    fun `an unsupported OS degrades to null (no portable layout defined)`(@TempDir temp: Path) {
        // Neither the Windows (root) nor the Linux (bin/) layout applies → null (the run-source
        // case on any other platform)
        val root = temp.resolve("portable").resolve(windowsProduct)
        Files.createDirectories(root)
        Files.writeString(root.resolve("launcher"), "launcher")
        Files.writeString(root.resolve(CredentialStore.PORTABLE_MARKER_FILE), CredentialStore.PORTABLE_MARKER_CONTENT)
        assertNull(CredentialStore.portableDataRootOf("Mac OS X", emptyMap(), root.resolve("launcher").toString(), windowsProduct))
    }

    @Test
    fun `the portable data dir wiring resolves the root data dir with the one-time base copy`(@TempDir temp: Path) {
        // The production composition of the decision (the lazy appDataDir does exactly this —
        // root wins → <root>/data/ + the one-time copy from the base data dir): the base's
        // files the portable dir lacks arrive, the base dir SURVIVES, and a second launch is a
        // no-op (only missing files are copied)
        val home = temp.resolve("home")
        val xdg = home.resolve(".config")
        val base = xdg.resolve("N-Zik Desktop Compagnon")
        Files.createDirectories(base.resolve("cache/audio"))
        Files.writeString(base.resolve("pairing.json"), "BASE")
        Files.writeString(base.resolve("cache/audio/track1.flac"), "audio")
        val exe = plantLinuxPortable(temp.resolve("portable-case"))
        val portableRoot = CredentialStore.portableDataRootOf("Linux", emptyMap(), exe.toString(), windowsProduct)
        assertNotNull(portableRoot, "the decision must resolve the portable root")

        val portableDir = portableRoot!!.resolve(CredentialStore.PORTABLE_DATA_DIR_NAME)
        CredentialStore.copySharedIntoChannel(base, portableDir, log)

        assertEquals("BASE", Files.readString(portableDir.resolve("pairing.json")))
        assertEquals("audio", Files.readString(portableDir.resolve("cache/audio/track1.flac")))
        assertTrue(Files.isDirectory(base), "the base data dir survives the portable copy")
        // Second launch: nothing new to copy (idempotent)
        Files.writeString(portableDir.resolve("settings.json"), "portable-settings")
        CredentialStore.copySharedIntoChannel(base, portableDir, log)
        assertEquals("portable-settings", Files.readString(portableDir.resolve("settings.json")))
        assertEquals("BASE", Files.readString(portableDir.resolve("pairing.json")))
    }

    // ---- production wiring (source pin — the lazy composition is never executed in tests) ----

    @Test
    fun `the production storage wiring is pinned (source pin on the appDataDir + defaultStore lazies)`() {
        // The production composition is never executed in tests (it would resolve the REAL
        // data dir + the REAL secret store) — the source pin keeps the wiring instead: the app
        // data dir resolves through AppInfo.dataName(channel) (the storage contract, not the
        // install identity), the portable decision runs with the production inputs and wins
        // over the per-channel dir, the per-channel dir goes through appDataDirFor (the
        // migration + copy + merge-back wiring), and the default secret store targets the
        // per-channel name with the one-time shared migration
        val sourceFile = File("src/main/kotlin/app/n_zik/compagnon/bridge/pairing/CredentialStore.kt")
        if (!sourceFile.isFile) error("the source pin needs $sourceFile — run the tests with the ComposeN-Zik module dir as the working directory (cwd=${File(".").absolutePath})")
        val source = sourceFile.readText()
        assertTrue(
            source.contains("portableDataRootOf(osName, System.getenv(), liveExecutablePath(), AppInfo.productName(channel))"),
            "the portable decision must run with the production inputs and win over the per-channel dir",
        )
        assertTrue(
            source.contains("AppInfo.dataName(channel)"),
            "the app data dir must resolve under the per-channel STORAGE name (AppInfo.dataName, not the install identity)",
        )
        assertTrue(
            source.contains("?: appDataDirFor("),
            "the per-channel dir must go through the appDataDirFor CALL (the migration + copy + merge-back wiring — the function's own declaration must not satisfy this pin)",
        )
        assertTrue(
            source.contains("resolveAppDataDir(osName, appData, xdgConfigHome, home, AppInfo.NAME)"),
            "the portable branch's one-time copy must take the BASE data dir as its source (the lazy's own `home` argument — a bare copySharedIntoChannel pin would be satisfied by the appDataDirFor call site)",
        )
        assertTrue(
            source.contains("AppInfo.dataName(AppVersion.channel)"),
            "the default secret store must target the per-channel storage name",
        )
        assertTrue(
            source.contains("name != AppInfo.NAME && store.read() == null"),
            "the shared store must be constructed ONLY when the channel-suffixed name's secret does not exist yet (dropping this condition puts the second JNA load + D-Bus probe back on every launch of an isolated channel)",
        )
        assertTrue(
            source.contains("migrateSharedSecret(store, selectSecretStore(osName, AppInfo.NAME))"),
            "the one-time shared → channel secret migration must stay wired for a channel-suffixed name",
        )
    }
}
