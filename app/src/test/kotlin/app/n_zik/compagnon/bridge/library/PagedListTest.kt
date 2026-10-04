package app.n_zik.compagnon.bridge.library

import app.n_zik.compagnon.core.network.LibraryResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PagedListTest {

    private data class Call(val query: String, val offset: Int, val limit: Int)

    private val calls = mutableListOf<Call>()

    /** A fake library of [total] items per query: "query:index". */
    private fun page(query: String, offset: Int, limit: Int, total: Int): LibraryResult<String> =
        LibraryResult.Ok(Page((offset until minOf(offset + limit, total)).map { "$query:$it" }, total, offset, limit))

    private fun TestScope.list(
        total: Int = 230,
        answer: suspend (Call) -> LibraryResult<String> = { page(it.query, it.offset, it.limit, total) },
    ) = PagedList("", backgroundScope) { query, offset, limit ->
        val call = Call(query, offset, limit)
        calls += call
        answer(call)
    }

    @Test
    fun `first page is offset 0 limit 100`() = runTest {
        val paged = list()
        paged.loadMore()
        runCurrent()
        assertEquals(listOf(Call("", 0, 100)), calls)
        assertEquals(100, paged.state.value.items.size)
        assertEquals(230, paged.state.value.total)
        assertFalse(paged.state.value.endReached)
    }

    @Test
    fun `next pages at 100 and 200, nothing after the total`() = runTest {
        val paged = list()
        repeat(5) {
            paged.loadMore()
            runCurrent()
        }
        assertEquals(listOf(0, 100, 200), calls.map { it.offset })
        assertEquals(230, paged.state.value.items.size)
        assertTrue(paged.state.value.endReached)
    }

    @Test
    fun `a page loading is not asked twice`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val paged = list(answer = { gate.await(); page(it.query, it.offset, it.limit, 230) })
        paged.loadMore()
        paged.loadMore()
        runCurrent()
        assertTrue(paged.state.value.loading)
        gate.complete(Unit)
        runCurrent()
        assertEquals(1, calls.size)
    }

    @Test
    fun `a new query reloads from offset 0 and an older answer never overwrites it`() = runTest {
        val old = CompletableDeferred<Unit>()
        val paged = list(answer = {
            if (it.query == "") old.await()
            page(it.query, it.offset, it.limit, 230)
        })
        paged.loadMore()
        runCurrent()
        paged.setQuery("abc")
        runCurrent()
        old.complete(Unit)
        runCurrent()
        assertEquals(listOf(Call("", 0, 100), Call("abc", 0, 100)), calls)
        assertTrue(paged.state.value.items.all { it.startsWith("abc:") })
        assertEquals(100, paged.state.value.items.size)
    }

    @Test
    fun `an older answer arriving after a newer one is ignored`() = runTest {
        val old = CompletableDeferred<Unit>()
        val paged = list(answer = {
            if (it.query == "a") old.await()
            page(it.query, it.offset, it.limit, 230)
        })
        paged.setQuery("a")
        runCurrent()
        paged.setQuery("ab")
        runCurrent()
        // The "a" request was cancelled with its generation; even if it answered, nothing would change
        old.complete(Unit)
        runCurrent()
        assertTrue(paged.state.value.items.all { it.startsWith("ab:") })
    }

    @Test
    fun `the query is observable and follows every change, even during a load`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val paged = list(answer = { gate.await(); page(it.query, it.offset, it.limit, 230) })
        assertEquals("", paged.query.value)
        paged.loadMore()
        paged.setQuery("a")
        paged.setQuery("b")
        assertEquals("b", paged.query.value)
        gate.complete(Unit)
        runCurrent()
        assertEquals("b", paged.query.value)
        assertTrue(paged.state.value.items.all { it.startsWith("b:") })
    }

    @Test
    fun `same query does nothing`() = runTest {
        val paged = list()
        paged.loadMore()
        runCurrent()
        paged.setQuery("")
        runCurrent()
        assertEquals(1, calls.size)
    }

    @Test
    fun `a failed page keeps the loaded ones and retry reads the missing page`() = runTest {
        var unreachable = false
        val paged = list(answer = { if (unreachable) LibraryResult.Unreachable else page(it.query, it.offset, it.limit, 230) })
        paged.loadMore()
        runCurrent()
        unreachable = true
        paged.loadMore()
        runCurrent()
        assertEquals(LibraryError.Unreachable, paged.state.value.error)
        assertEquals(100, paged.state.value.items.size)
        // Waits for the user: no automatic retry on scroll
        paged.loadMore()
        runCurrent()
        assertEquals(2, calls.size)

        unreachable = false
        paged.retry()
        runCurrent()
        assertEquals(listOf(0, 100, 100), calls.map { it.offset })
        assertNull(paged.state.value.error)
        assertEquals(200, paged.state.value.items.size)
    }

    @Test
    fun `409 and 5xx are errors with retry`() = runTest {
        var answer: LibraryResult<String> = LibraryResult.OtherActive("PC-BUREAU")
        val paged = list(answer = { answer })
        paged.loadMore()
        runCurrent()
        assertEquals(LibraryError.OtherActive("PC-BUREAU"), paged.state.value.error)
        answer = LibraryResult.Failed(503, "SERVER_STOPPING")
        paged.retry()
        runCurrent()
        assertEquals(LibraryError.Failed(503, "SERVER_STOPPING"), paged.state.value.error)
    }

    @Test
    fun `404 marks the list not found and stops`() = runTest {
        val paged = list(answer = { LibraryResult.NotFound })
        paged.loadMore()
        runCurrent()
        assertTrue(paged.state.value.notFound)
        paged.loadMore()
        runCurrent()
        assertEquals(1, calls.size)
    }

    @Test
    fun `reload forgets the pages`() = runTest {
        val paged = list()
        paged.loadMore()
        runCurrent()
        paged.loadMore()
        runCurrent()
        paged.reload()
        runCurrent()
        assertEquals(listOf(0, 100, 0), calls.map { it.offset })
        assertEquals(100, paged.state.value.items.size)
    }

    @Test
    fun `an empty page ends the list`() = runTest {
        val paged = list(answer = { LibraryResult.Ok(Page(emptyList(), 50, it.offset, it.limit)) })
        paged.loadMore()
        runCurrent()
        assertTrue(paged.state.value.endReached)
        assertEquals(0, paged.state.value.total)
    }

    @Test
    fun `patchItems rewrites the loaded items and shrinks the total by the dropped ones`() = runTest {
        val paged = list()
        paged.loadMore()
        runCurrent()
        assertEquals(100, paged.state.value.items.size)
        assertEquals(230, paged.state.value.total)

        // Rewrites every item, drops the ":1" one (the §10.2 optimistic update, since 1.7)
        paged.patchItems { item -> if (item == ":1") null else item.uppercase() }
        assertEquals(99, paged.state.value.items.size)
        assertEquals(229, paged.state.value.total)
        assertTrue(paged.state.value.items.none { it == ":1" })
        assertTrue(paged.state.value.items.all { it == it.uppercase() })

        // A patch on an empty list is a no-op
        val empty = list(total = 0)
        empty.patchItems { null }
        assertNull(empty.state.value.total)
        assertTrue(empty.state.value.items.isEmpty())
    }

    @Test
    fun `songs query text is trimmed and bounded`() {
        assertNull(SongsQuery.normalizeText("   "))
        assertEquals("abc", SongsQuery.normalizeText("  abc "))
        assertEquals(100, SongsQuery.normalizeText("x".repeat(150))!!.length)
    }
}
