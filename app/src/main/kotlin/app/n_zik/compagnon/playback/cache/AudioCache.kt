package app.n_zik.compagnon.playback.cache

import app.n_zik.compagnon.bridge.pairing.CredentialStore
import app.n_zik.compagnon.core.network.DownloadResult
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.UUID
import java.util.logging.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The local audio cache of contract §8.3: one file per `trackId` in [directory], with a persistent access
 * index (`index.json`). Reading an entry needs no URL and no network call.
 *
 * - An entry is written to a temporary file and renamed only once the download is complete: only a
 *   complete entry is ever served, and nothing half-written survives a crash (orphans are swept at start).
 * - Least recently read entries are evicted as soon as the total exceeds [maxBytes] (`null` = unlimited).
 * - [maxBytes] `0` ("Disabled") downloads nothing and evicts everything.
 *
 * File names are a hash of the `trackId`: the id is never parsed (contract §1).
 */
class AudioCache(
    private val directory: Path = defaultDirectory(),
    private val maxBytes: () -> Long?,
    private val now: () -> Long = System::currentTimeMillis,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private val log = Logger.getLogger("AudioCache")

    @Serializable
    data class Entry(val file: String, val size: Long, val lastAccessMs: Long)

    @Serializable
    private data class Index(val entries: Map<String, Entry> = emptyMap())

    private val entries = LinkedHashMap<String, Entry>()
    private var loaded = false

    /** Whether new entries may be written (the ceiling is not "Disabled"). */
    val isEnabled: Boolean get() = maxBytes() != 0L

    /** Path of the complete entry of [trackId], its access time refreshed; `null` on a miss. */
    @Synchronized
    fun lookup(trackId: String): Path? {
        ensureLoaded()
        val entry = entries[trackId] ?: return null
        val path = directory.resolve(entry.file)
        val size = runCatching { Files.size(path) }.getOrNull()
        if (size != entry.size) {
            log.info("Cache entry missing or truncated: dropped")
            dropLocked(trackId)
            saveIndex()
            return null
        }
        entries[trackId] = entry.copy(lastAccessMs = now())
        saveIndex()
        return path
    }

    /** Drops [trackId] (an unreadable entry: the next play goes to the network). */
    @Synchronized
    fun remove(trackId: String) {
        ensureLoaded()
        if (dropLocked(trackId)) saveIndex()
    }

    @Synchronized
    fun totalBytes(): Long {
        ensureLoaded()
        return entries.values.sumOf { it.size }
    }

    @Synchronized
    fun contains(trackId: String): Boolean {
        ensureLoaded()
        return trackId in entries
    }

    /** Evicts the least recently read entries until the total fits the ceiling (after a smaller setting too). */
    @Synchronized
    fun trim() {
        ensureLoaded()
        if (trimLocked()) saveIndex()
    }

    /** "Clear cache": every entry removed. */
    @Synchronized
    fun clear() {
        ensureLoaded()
        entries.keys.toList().forEach { dropLocked(it) }
        saveIndex()
    }

    /**
     * Downloads [trackId] through [fetch] into a temporary file, then commits it. Nothing happens when the
     * cache is disabled or already holds the entry. A failure or a cancellation leaves no file behind.
     */
    suspend fun fill(trackId: String, fetch: suspend (Path) -> DownloadResult): Boolean {
        if (!isEnabled || contains(trackId)) return false
        val temp = withContext(io) {
            Files.createDirectories(directory)
            directory.resolve("${fileName(trackId)}.${UUID.randomUUID()}$PART_SUFFIX")
        }
        try {
            val result = fetch(temp)
            if (result !is DownloadResult.Done) return false
            return withContext(io) { commit(trackId, temp, result.bytes) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.info("Cache fill failed: ${e::class.simpleName}")
            return false
        } finally {
            withContext(NonCancellable + io) { runCatching { Files.deleteIfExists(temp) } }
        }
    }

    @Synchronized
    private fun commit(trackId: String, temp: Path, size: Long): Boolean {
        ensureLoaded()
        if (!isEnabled) return false
        val actual = runCatching { Files.size(temp) }.getOrNull()
        if (actual == null || actual != size || size <= 0) return false
        val name = fileName(trackId)
        val target = directory.resolve(name)
        try {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING)
        }
        entries[trackId] = Entry(name, size, now())
        trimLocked()
        saveIndex()
        return true
    }

    private fun trimLocked(): Boolean {
        val max = maxBytes() ?: return false
        var total = entries.values.sumOf { it.size }
        if (total <= max) return false
        val byAge = entries.entries.sortedBy { it.value.lastAccessMs }.map { it.key }
        for (trackId in byAge) {
            if (total <= max) break
            total -= entries[trackId]?.size ?: 0
            dropLocked(trackId)
        }
        return true
    }

    private fun dropLocked(trackId: String): Boolean {
        val entry = entries.remove(trackId) ?: return false
        runCatching { Files.deleteIfExists(directory.resolve(entry.file)) }
            .onFailure { log.warning("Could not delete a cache entry: ${it::class.simpleName}") }
        return true
    }

    private fun ensureLoaded() {
        if (loaded) return
        loaded = true
        val indexFile = directory.resolve(INDEX_FILE)
        val index = runCatching {
            if (Files.exists(indexFile)) JSON.decodeFromString(Index.serializer(), Files.readString(indexFile, Charsets.UTF_8)) else Index()
        }.getOrElse {
            log.warning("Cache index unreadable: cache emptied")
            Index()
        }
        index.entries.forEach { (trackId, entry) ->
            val size = runCatching { Files.size(directory.resolve(entry.file)) }.getOrNull()
            if (size == entry.size) entries[trackId] = entry
        }
        // Temporary files of an interrupted download, and files no entry refers to.
        val known = entries.values.mapTo(HashSet()) { it.file }
        runCatching {
            if (Files.isDirectory(directory)) {
                Files.list(directory).use { files ->
                    files.filter { val name = it.fileName.toString(); name != INDEX_FILE && name !in known }
                        .forEach { runCatching { Files.deleteIfExists(it) } }
                }
            }
        }
        trimLocked()
    }

    private fun saveIndex() {
        runCatching {
            Files.createDirectories(directory)
            val indexFile = directory.resolve(INDEX_FILE)
            val tmp = directory.resolve("$INDEX_FILE.tmp")
            Files.writeString(tmp, JSON.encodeToString(Index.serializer(), Index(LinkedHashMap(entries))), Charsets.UTF_8)
            try {
                Files.move(tmp, indexFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(tmp, indexFile, StandardCopyOption.REPLACE_EXISTING)
            }
        }.onFailure { log.warning("Could not write the cache index: ${it::class.simpleName}") }
    }

    companion object {
        const val INDEX_FILE = "index.json"
        private const val PART_SUFFIX = ".part"
        private val JSON = Json { ignoreUnknownKeys = true }

        /** `%APPDATA%\N-Zik Desktop Compagnon\cache\audio\`. */
        fun defaultDirectory(): Path = CredentialStore.appDirectory().resolve("cache").resolve("audio")

        /** SHA-256 of the `trackId`, hex: a safe file name whatever the id holds. */
        fun fileName(trackId: String): String =
            MessageDigest.getInstance("SHA-256").digest(trackId.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}
