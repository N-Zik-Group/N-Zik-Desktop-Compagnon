package app.n_zik.compagnon.tray

import java.io.File
import java.util.logging.Logger

/**
 * Reads values from the HOST's dconf user database (the GVDB on-disk format, dconf ≥ 0.44 — the
 * `GVariant` magic header over one global hash of key items, dconf/gvdb library).
 *
 * Why the file instead of `gsettings` / dconf / D-Bus: the freedesktop SDK runtime ships the LEGACY
 * dconf client (the "dcdb" format) — it cannot parse the host's GVDB file, and the host daemon's
 * new D-Bus protocol is write/notify-only (no read API); the DConf and Settings portals are absent
 * on hosts without them. Every probe from inside the sandbox therefore answers the schema
 * defaults instead of the host's value. The user DB lives in the XDG config dir the manifest
 * already grants (`--filesystem=xdg-config`), so reading it directly is the reliable path to the
 * host's real values — from the Flatpak sandbox as well as from a plain Linux run.
 */
internal object HostDconf {
    private val log = Logger.getLogger("HostDconf")

    /** The string value of [schema]/[key] in the host's dconf user DB (dconf key form — `org/gnome/desktop/interface` + `color-scheme`), or `null` (no readable DB, the key absent, a non-string value, or an unreadable/corrupt file). */
    fun readString(schema: String, key: String): String? {
        val file = userDbFile() ?: run {
            log.info("HostDconf: no dconf user DB — the theme tint falls back to the default")
            return null
        }
        return runCatching { file.readBytes() }
            .onFailure { log.warning("HostDconf: could not read ${file} (${it::class.simpleName}: ${it.message})") }
            .getOrNull()
            ?.let { readStringFrom(it, schema, key) }
    }

    /** [bytes] as a GVDB user DB — the string value of [schema]/[key], or `null` (see [readString]). */
    fun readStringFrom(bytes: ByteArray, schema: String, key: String): String? =
        runCatching { Gvdb(bytes).lookupString("/$schema/$key") }
            .onFailure { log.warning("HostDconf: not a readable GVDB file (${it::class.simpleName}: ${it.message})") }
            .getOrNull()

    /** The host's dconf user DB from the process's env (the candidates, see [userDbFile]). */
    private fun userDbFile(): File? =
        userDbFile(System.getenv("XDG_CONFIG_HOME"), System.getProperty("user.home"))

    /**
     * The host's dconf user DB from its inputs (pure — the tests pin the candidate order): the XDG
     * config dir first, then the default config location. In the Flatpak sandbox `XDG_CONFIG_HOME`
     * is the per-app dir (no dconf there) while `$HOME` is the real home — the second entry is the
     * one that works there; on a plain run the first is.
     */
    internal fun userDbFile(xdgConfigHome: String?, userHome: String): File? =
        listOfNotNull(
            xdgConfigHome?.takeIf { it.isNotBlank() }?.let { File(it, "dconf/user") },
            File(userHome, ".config/dconf/user"),
        ).firstOrNull { it.isFile }
}

/**
 * The GVDB user DB as parsed for reads: a 24-byte header (`GVariant` magic, `version == 0`, the
 * root hash's `[start, end)`), the root hash (bloom words, then `n_buckets` item-INDEX buckets,
 * then 24-byte items), with the key strings and the value blobs living anywhere else in the file
 * at the absolute offsets the items carry.
 */
internal class Gvdb(private val bytes: ByteArray) {
    private val rootStart: Int
    private val rootEnd: Int
    private val bucketsStart: Int
    private val itemsStart: Int
    private val nBuckets: Int
    private val nItems: Int

    init {
        require(bytes.size >= 24) { "too small for a GVDB header" }
        require(bytes.copyOfRange(0, 8).contentEquals("GVariant".toByteArray())) { "not a GVDB file" }
        require(u32(8) == 0u) { "unsupported GVDB version" }
        rootStart = u32(16).toInt()
        rootEnd = u32(20).toInt()
        require(rootStart >= 24 && rootEnd <= bytes.size) { "bad root range" }
        val nBloom = u32(rootStart).toInt() and 0x07FFFFFF // the top 5 bits carry the bloom shift
        nBuckets = u32(rootStart + 4).toInt()
        bucketsStart = rootStart + 8 + nBloom * 4
        itemsStart = bucketsStart + nBuckets * 4
        require(itemsStart <= rootEnd) { "bad hash layout" }
        nItems = (rootEnd - itemsStart) / 24
    }

    /** The string value stored at the full dconf [path] (`/schema/path/key`), or `null` (the key absent, not a value item, or a non-string value). */
    fun lookupString(path: String): String? {
        val item = lookup(path) ?: return null
        val start = item.valueStart
        val end = item.valueEnd
        if (start < 0 || end > bytes.size || end < start + 3) return null
        val blob = bytes.copyOfRange(start, end)
        // The value layout: raw data + padding + the type descriptor LAST (a one-byte tag for
        // simple types, the ASCII type string otherwise). Only strings are read — for a string

        // value the two zero padding bytes precede the `s` tag.
        return if (blob.last() == 's'.code.toByte()) blob.copyOfRange(0, blob.size - 3).decodeToString() else null
    }

    /**
     * The value item whose full key is [path]: djb2 of the full key, the bucket chain
     * (`buckets[hash % n_buckets]` up to the next bucket's start), the name chain verified
     * segment by segment (each item's key is a trailing segment, the parent carries the rest).
     */
    private fun lookup(path: String): Item? {
        if (nBuckets <= 0 || nItems <= 0) return null
        val hash = djb2(path)
        // the C reader hashes and buckets with an UNSIGNED 32-bit value — the modulo must run on
        // the unsigned form, a signed `hash % n` would pick the wrong bucket for hashes ≥ 2^31
        val bucket = (hash.toUInt() % nBuckets.toUInt()).toInt()
        val first = u32(bucketsStart + bucket * 4).toInt().coerceIn(0, nItems)
        val last = if (bucket + 1 < nBuckets) u32(bucketsStart + (bucket + 1) * 4).toInt().coerceIn(first, nItems) else nItems
        for (index in first until last) {
            val item = itemAt(index)
            if (item.hashValue == hash && item.type == 'v'.code && nameMatches(item, path)) return item
        }
        return null
    }

    private fun nameMatches(item: Item, path: String): Boolean {
        var rest = path
        var current = item
        // a corrupt file could self-reference its items — the chain is at most as long as the table
        for (depth in 0..nItems) {
            val segment = keyOf(current) ?: return false
            if (segment.isEmpty() || rest.length < segment.length || !rest.endsWith(segment)) return false
            rest = rest.dropLast(segment.length)
            if (rest.isEmpty()) return current.parent == PARENT_ROOT
            if (current.parent !in 0 until nItems) return false
            current = itemAt(current.parent)
        }
        return false
    }

    private fun itemAt(index: Int): Item = Item(
        hashValue = u32(itemsStart + index * 24).toInt(),
        parent = u32(itemsStart + index * 24 + 4).toInt(),
        keyStart = u32(itemsStart + index * 24 + 8).toInt(),
        keySize = u16(itemsStart + index * 24 + 12),
        type = bytes[itemsStart + index * 24 + 14].toInt() and 0xFF,
        valueStart = u32(itemsStart + index * 24 + 16).toInt(),
        valueEnd = u32(itemsStart + index * 24 + 20).toInt(),
    )

    private fun keyOf(item: Item): String? =
        if (item.keySize == 0 || item.keyStart + item.keySize > bytes.size) null
        else bytes.copyOfRange(item.keyStart, item.keyStart + item.keySize).decodeToString()

    /** The GVDB hash (djb2: 5381, ×33, signed byte — the JVM's int wraps mod 2^32 like the C one). */
    private fun djb2(key: String): Int {
        var hash = 5381
        for (byte in key.toByteArray()) hash = hash * 33 + byte
        return hash
    }

    private fun u32(offset: Int): UInt =
        (bytes[offset].toUInt() and 0xFFu) or
        ((bytes[offset + 1].toUInt() and 0xFFu) shl 8) or
        ((bytes[offset + 2].toUInt() and 0xFFu) shl 16) or
        ((bytes[offset + 3].toUInt() and 0xFFu) shl 24)

    private fun u16(offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)

    private class Item(
        val hashValue: Int,
        val parent: Int,
        val keyStart: Int,
        val keySize: Int,
        val type: Int,
        val valueStart: Int,
        val valueEnd: Int,
    )

    /** A root item's parent marker (`0xFFFFFFFF`). */
    private val PARENT_ROOT = 0xFFFFFFFF.toInt()
}
