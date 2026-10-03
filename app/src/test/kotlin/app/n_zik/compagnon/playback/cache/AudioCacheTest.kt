package app.n_zik.compagnon.playback.cache

import app.n_zik.compagnon.core.network.DownloadResult
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir

class AudioCacheTest {

    @TempDir
    lateinit var dir: Path

    private var clock = 1_000L
    private var max: Long? = 1_000

    private fun cache() = AudioCache(dir, maxBytes = { max }, now = { clock++ })

    private fun AudioCache.put(trackId: String, size: Int): Boolean = runBlocking {
        fill(trackId) { target ->
            Files.write(target, ByteArray(size) { 7 })
            DownloadResult.Done(size.toLong())
        }
    }

    /** Files of the cache folder besides the index. */
    private fun files(): List<String> =
        Files.list(dir).use { s -> s.map { it.fileName.toString() }.filter { it != AudioCache.INDEX_FILE }.toList() }

    @Test
    fun `a complete entry is a hit, by trackId`() {
        val cache = cache()
        assertTrue(cache.put("dQw4w9WgXcQ", 100))
        val path = cache.lookup("dQw4w9WgXcQ")
        assertNotNull(path)
        assertEquals(100L, Files.size(path!!))
        assertNull(cache.lookup("other"))
    }

    @Test
    fun `an id that is not a file name is stored under its hash`() {
        val cache = cache()
        assertTrue(cache.put("local:42/é?", 10))
        assertEquals(listOf(AudioCache.fileName("local:42/é?")), files())
    }

    @Test
    fun `a failed download leaves no entry and no file`() {
        val cache = cache()
        val stored = runBlocking {
            cache.fill("a") { target ->
                Files.write(target, ByteArray(50))
                DownloadResult.Failed(403)
            }
        }
        assertFalse(stored)
        assertNull(cache.lookup("a"))
        assertTrue(files().isEmpty())
    }

    @Test
    fun `a size that does not match the download is never committed`() {
        val cache = cache()
        val stored = runBlocking {
            cache.fill("a") { target ->
                Files.write(target, ByteArray(50))
                DownloadResult.Done(80)
            }
        }
        assertFalse(stored)
        assertTrue(files().isEmpty())
    }

    @Test
    fun `a cancelled download leaves nothing behind`() {
        val cache = cache()
        assertThrows<CancellationException> {
            runBlocking {
                cache.fill("a") { target ->
                    Files.write(target, ByteArray(50))
                    throw CancellationException("track changed")
                }
            }
        }
        assertNull(cache.lookup("a"))
        assertTrue(files().isEmpty())
    }

    @Test
    fun `the least recently read entries are evicted over the ceiling`() {
        val cache = cache()
        cache.put("a", 400)
        cache.put("b", 400)
        cache.lookup("a") // b is now the least recently read
        cache.put("c", 400)
        assertNotNull(cache.lookup("a"))
        assertNull(cache.lookup("b"))
        assertNotNull(cache.lookup("c"))
        assertEquals(800L, cache.totalBytes())
    }

    @Test
    fun `a smaller ceiling evicts at once, unlimited never evicts`() {
        max = null
        val cache = cache()
        cache.put("a", 400)
        cache.put("b", 400)
        cache.put("c", 400)
        assertEquals(1_200L, cache.totalBytes())
        max = 500
        cache.trim()
        assertEquals(400L, cache.totalBytes())
        assertNotNull(cache.lookup("c"))
    }

    @Test
    fun `Disabled downloads nothing`() {
        max = 0
        val cache = cache()
        var fetched = false
        val stored = runBlocking {
            cache.fill("a") {
                fetched = true
                DownloadResult.Done(1)
            }
        }
        assertFalse(stored)
        assertFalse(fetched)
        assertFalse(cache.isEnabled)
    }

    @Test
    fun `a truncated or missing entry file is dropped on lookup`() {
        val cache = cache()
        cache.put("a", 100)
        Files.write(dir.resolve(AudioCache.fileName("a")), ByteArray(10))
        assertNull(cache.lookup("a"))
        assertFalse(cache.contains("a"))
    }

    @Test
    fun `remove drops an unreadable entry`() {
        val cache = cache()
        cache.put("a", 100)
        cache.remove("a")
        assertNull(cache.lookup("a"))
        assertTrue(files().isEmpty())
    }

    @Test
    fun `the index survives a restart, and stray files are swept`() {
        val first = cache()
        first.put("a", 100)
        first.put("b", 100)
        first.lookup("a")
        Files.write(dir.resolve("deadbeef.123.part"), ByteArray(5))

        val second = cache()
        assertNotNull(second.lookup("a"))
        assertNotNull(second.lookup("b"))
        assertFalse(Files.exists(dir.resolve("deadbeef.123.part")))
    }

    @Test
    fun `clear removes every entry`() {
        val cache = cache()
        cache.put("a", 100)
        cache.put("b", 100)
        cache.clear()
        assertEquals(0L, cache.totalBytes())
        assertTrue(files().isEmpty())
    }
}
