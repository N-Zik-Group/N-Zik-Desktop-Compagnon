package app.n_zik.compagnon.tray

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * The host dconf GVDB reader (the dconf ≥ 0.44 user DB format behind the tray icon + the menu
 * icons' theme-following tint): the file is synthesized byte by byte — the 24-byte header, one
 * root hash (no bloom, one bucket), the key-segment chain, the value blobs.
 */
/** A GVDB root item's parent marker (`0xFFFFFFFF`). */
private const val PARENT_ROOT = 0xFFFFFFFF.toInt()

class HostDconfTest {

    private class Spec(val fullPath: String, val segment: String, val parent: Int, val type: Int, val value: ByteArray?)

    private val interfaceChain = listOf(
        Spec("/", "/", PARENT_ROOT, 'L'.code, null),
        Spec("/org/", "org/", 0, 'L'.code, null),
        Spec("/org/gnome/", "gnome/", 1, 'L'.code, null),
        Spec("/org/gnome/desktop/", "desktop/", 2, 'L'.code, null),
        Spec("/org/gnome/desktop/interface/", "interface/", 3, 'L'.code, null),
    )

    private fun valueSpec(key: String, value: String) =
        Spec("/org/gnome/desktop/interface/$key", key, 4, 'v'.code, value.toByteArray() + STRING_PAD)

    @Test
    fun `reads a nested string value`() {
        val bytes = gvdbFile(interfaceChain + valueSpec("color-scheme", "prefer-dark"))
        assertEquals("prefer-dark", HostDconf.readStringFrom(bytes, "org/gnome/desktop/interface", "color-scheme"))
    }

    @Test
    fun `reads an empty string value`() {
        val bytes = gvdbFile(interfaceChain + valueSpec("gtk-theme", ""))
        assertEquals("", HostDconf.readStringFrom(bytes, "org/gnome/desktop/interface", "gtk-theme"))
    }

    @Test
    fun `returns null for an unknown key`() {
        val bytes = gvdbFile(interfaceChain + valueSpec("color-scheme", "prefer-dark"))
        assertNull(HostDconf.readStringFrom(bytes, "org/gnome/desktop/interface", "other-key"))
    }

    @Test
    fun `returns null for a non-string value`() {
        // an int32 value: the raw bytes + padding + the trailing `i` tag — not a string
        val bytes = gvdbFile(interfaceChain + Spec("/org/gnome/desktop/interface/count", "count", 4, 'v'.code, byteArrayOf(8, 0, 0, 0, 0, 'i'.code.toByte())))
        assertNull(HostDconf.readStringFrom(bytes, "org/gnome/desktop/interface", "count"))
    }

    @Test
    fun `a corrupt or empty file reads as null`() {
        assertNull(HostDconf.readStringFrom(ByteArray(16), "org/gnome/desktop/interface", "color-scheme"))
        assertNull(HostDconf.readStringFrom(ByteArray(32), "org/gnome/desktop/interface", "color-scheme"))
    }

    @Test
    fun `the host user db (when present) carries a valid color scheme or nothing`() {
        val scheme = HostDconf.readString("org/gnome/desktop/interface", "color-scheme")
        assertTrue(scheme == null || scheme in setOf("default", "prefer-light", "prefer-dark"))
    }

    // userDbFile candidate order (the Flatpak-sandbox-critical path)

    @Test
    fun `the xdg config home candidate wins when it exists`(@TempDir temp: Path) {
        // A plain run: the XDG_CONFIG_HOME/dconf/user is the live user DB
        val xdg = temp.resolve("xdg-config")
        val home = temp.resolve("home")
        Files.createDirectories(xdg.resolve("dconf"))
        Files.createDirectories(home.resolve(".config/dconf"))
        Files.writeString(xdg.resolve("dconf/user"), "xdg")
        Files.writeString(home.resolve(".config/dconf/user"), "home")
        assertEquals(File(xdg.toString(), "dconf/user"), HostDconf.userDbFile(xdg.toString(), home.toString()))
    }

    @Test
    fun `the flatpak sandbox falls back to the real home when the per-app dir has no dconf`(@TempDir temp: Path) {
        // Inside the sandbox XDG_CONFIG_HOME is the per-app dir (no dconf there) while $HOME is the
        // real home — the second candidate is the one that works (the live in-sandbox probe)
        val xdg = temp.resolve("per-app-config") // exists, but carries no dconf
        val home = temp.resolve("home")
        Files.createDirectories(xdg)
        Files.createDirectories(home.resolve(".config/dconf"))
        Files.writeString(home.resolve(".config/dconf/user"), "host")
        assertEquals(File(home.toString(), ".config/dconf/user"), HostDconf.userDbFile(xdg.toString(), home.toString()))
    }

    @Test
    fun `a blank xdg config home is ignored in favour of the home default`(@TempDir temp: Path) {
        val home = temp.resolve("home")
        Files.createDirectories(home.resolve(".config/dconf"))
        Files.writeString(home.resolve(".config/dconf/user"), "host")
        assertEquals(File(home.toString(), ".config/dconf/user"), HostDconf.userDbFile("  ", home.toString()))
        assertNull(HostDconf.userDbFile("", temp.resolve("missing").toString()))
    }

    /**
     * The GVDB layout the dconf writer produces: the 24-byte header (`GVariant` magic, version 0,
     * the root hash's `[start, end)`), the hash header (bloom words, buckets) + the bucket array +
     * the 24-byte items, then the key strings and the value blobs anywhere else in the file.
     */
    private fun gvdbFile(specs: List<Spec>): ByteArray {
        val keys = specs.map { it.segment.toByteArray() }
        val values = specs.map { it.value ?: ByteArray(0) }
        val rootStart = 24
        val rootEnd = rootStart + 8 + 4 + specs.size * 24 // hash header + 1 bucket + items
        val keysStart = rootEnd
        val valuesStart = keysStart + keys.sumOf { it.size }
        val file = ByteArray(valuesStart + values.sumOf { it.size })

        "GVariant".toByteArray().copyInto(file)
        putU32(file, 8, 0) // version
        putU32(file, 12, 0) // options
        putU32(file, 16, rootStart)
        putU32(file, 20, rootEnd)
        putU32(file, rootStart, 0) // no bloom words
        putU32(file, rootStart + 4, 1) // one bucket
        putU32(file, rootStart + 8, 0) // bucket[0] -> the first item
        specs.forEachIndexed { index, spec ->
            val item = rootStart + 12 + index * 24
            putU32(file, item, djb2(spec.fullPath))
            putU32(file, item + 4, spec.parent)
            putU32(file, item + 8, keysStart + keys.take(index).sumOf { it.size })
            putU16(file, item + 12, spec.segment.length)
            file[item + 14] = spec.type.toByte()
            file[item + 15] = 0
            putU32(file, item + 16, valuesStart + values.take(index).sumOf { it.size })
            putU32(file, item + 20, valuesStart + values.take(index).sumOf { it.size } + (spec.value ?: ByteArray(0)).size)
        }
        var o = keysStart
        for (key in keys) {
            key.copyInto(file, o)
            o += key.size
        }
        o = valuesStart
        for (value in values) {
            value.copyInto(file, o)
            o += value.size
        }
        return file
    }

    private fun djb2(key: String): Int {
        var hash = 5381
        for (b in key.toByteArray()) hash = hash * 33 + b
        return hash
    }

    private fun putU32(data: ByteArray, offset: Int, value: Int) {
        data[offset] = value.toByte()
        data[offset + 1] = (value shr 8).toByte()
        data[offset + 2] = (value shr 16).toByte()
        data[offset + 3] = (value shr 24).toByte()
    }

    private fun putU16(data: ByteArray, offset: Int, value: Int) {
        data[offset] = value.toByte()
        data[offset + 1] = (value shr 8).toByte()
    }

    /** A GVDB string value: the raw bytes + the two zero padding bytes + the trailing `s` tag. */
    private val STRING_PAD = byteArrayOf(0, 0, 's'.code.toByte())
}
