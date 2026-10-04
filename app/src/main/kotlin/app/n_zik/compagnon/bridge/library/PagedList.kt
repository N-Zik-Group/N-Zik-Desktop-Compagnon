package app.n_zik.compagnon.bridge.library

import app.n_zik.compagnon.core.network.LibraryResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Why a page could not be read; the screen shows a message and "Retry". */
sealed interface LibraryError {
    data object Unreachable : LibraryError
    data class OtherActive(val deviceName: String?) : LibraryError
    data class Failed(val status: Int, val code: String?) : LibraryError
}

/**
 * What a list screen shows. [total] is `null` until the first page; the pages already loaded stay when a
 * later one fails ([error]). [notFound]: `404` (a detail that no longer exists). [revoked]: confirmed
 * revocation, the app leaves for the re-pairing screen.
 */
data class PagedState<T>(
    val items: List<T> = emptyList(),
    val total: Int? = null,
    val loading: Boolean = false,
    val error: LibraryError? = null,
    val notFound: Boolean = false,
    val revoked: Boolean = false,
    /** Since 1.7.2: the total duration in ms of the FULL list, before pagination and before `text`
     *  (the phone's `GET /library/playlists/{id}/songs`, its header duration); `0` on every other route. */
    val totalDurationMs: Long = 0L,
) {
    val endReached: Boolean get() = total != null && items.size >= total
}

/**
 * Pagination of one list (contract §1): pages of [pageSize] by `offset` / `limit`, loaded on demand
 * ([loadMore]) up to `total`. A failed page keeps the loaded ones and is read again by [retry]. A new
 * [query] reloads from `offset = 0`; an answer to an older query (or before a [reload]) is ignored, so it
 * can never overwrite the newer list. Kept in memory only.
 */
class PagedList<Q, T>(
    initialQuery: Q,
    private val scope: CoroutineScope,
    private val pageSize: Int = LibraryContract.PAGE_SIZE,
    private val fetch: suspend (query: Q, offset: Int, limit: Int) -> LibraryResult<T>,
) {
    private val lock = Any()
    private val _state = MutableStateFlow(PagedState<T>())
    val state: StateFlow<PagedState<T>> = _state.asStateFlow()

    private val _query = MutableStateFlow(initialQuery)

    /** The current query, observable: a screen's chips and sort always follow it. */
    val query: StateFlow<Q> = _query.asStateFlow()

    private var generation = 0L
    private var job: Job? = null

    /** Changes the query: the list reloads from the first page. Same query → nothing. */
    fun setQuery(newQuery: Q) {
        synchronized(lock) {
            if (newQuery == _query.value) return
            _query.value = newQuery
        }
        reload()
    }

    /** "Refresh": forgets every page and reads the first one again. */
    fun reload() {
        synchronized(lock) {
            generation++
            job?.cancel()
            job = null
            _state.value = PagedState()
        }
        loadMore()
    }

    /** Reads the next page, unless one is loading, the end is reached, or a failed page waits for [retry]. */
    fun loadMore() {
        synchronized(lock) {
            val current = _state.value
            if (current.loading || current.error != null || current.endReached || current.notFound || current.revoked) return
            val requestGeneration = generation
            val requestQuery = _query.value
            val offset = current.items.size
            _state.value = current.copy(loading = true)
            job = scope.launch {
                val result = fetch(requestQuery, offset, pageSize)
                apply(requestGeneration, offset, result)
            }
        }
    }

    /** "Retry" after a failed page: reads that page again; the loaded ones stay. */
    fun retry() {
        synchronized(lock) {
            if (_state.value.error == null) return
            _state.value = _state.value.copy(error = null)
        }
        loadMore()
    }

    /**
     * An in-memory rewrite of the loaded items (the contract §10.2 writes, since 1.7): [transform] gives
     * each loaded item its replacement, or `null` to drop it (the phone hides it from its list, so
     * [PagedState.total] shrinks by the number of dropped items). Only the loaded items are patched:
     * not-yet-loaded pages are read from the phone anyway and already carry the new state. A later
     * [reload] re-syncs the list with the phone.
     */
    fun patchItems(transform: (T) -> T?) {
        synchronized(lock) {
            val current = _state.value
            if (current.items.isEmpty()) return
            val items = current.items.mapNotNull(transform)
            val dropped = current.items.size - items.size
            _state.value = current.copy(
                items = items,
                total = current.total?.minus(dropped)?.coerceAtLeast(0),
            )
        }
    }

    private fun apply(requestGeneration: Long, offset: Int, result: LibraryResult<T>) = synchronized(lock) {
        // An answer to an older query or before a reload never overwrites the current list
        if (requestGeneration != generation) return@synchronized
        val current = _state.value
        if (offset != current.items.size) return@synchronized
        _state.value = when (result) {
            is LibraryResult.Ok -> current.copy(
                items = current.items + result.page.items,
                // An empty page ends the list even if `total` says otherwise (library changed meanwhile)
                total = if (result.page.items.isEmpty()) current.items.size else result.page.total,
                // Since 1.7.2: the full list's duration (a later page carries the same value)
                totalDurationMs = result.page.totalDurationMs,
                loading = false,
                error = null,
            )
            LibraryResult.NotFound -> current.copy(loading = false, notFound = true)
            LibraryResult.Revoked -> current.copy(loading = false, revoked = true)
            is LibraryResult.OtherActive -> current.copy(loading = false, error = LibraryError.OtherActive(result.deviceName))
            is LibraryResult.Failed -> current.copy(loading = false, error = LibraryError.Failed(result.status, result.code))
            LibraryResult.Unreachable -> current.copy(loading = false, error = LibraryError.Unreachable)
        }
    }
}
