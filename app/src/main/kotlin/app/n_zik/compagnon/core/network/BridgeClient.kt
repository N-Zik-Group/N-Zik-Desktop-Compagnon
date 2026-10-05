package app.n_zik.compagnon.core.network

import java.util.logging.Logger
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.timeout
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.head
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.prepareGet
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.encodeURLPathPart
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.readAvailable
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import app.n_zik.compagnon.bridge.library.Album
import app.n_zik.compagnon.bridge.library.AlbumLike
import app.n_zik.compagnon.bridge.library.AlbumsQuery
import app.n_zik.compagnon.bridge.library.Artist
import app.n_zik.compagnon.bridge.library.ArtistsQuery
import app.n_zik.compagnon.bridge.library.ArtistFollow
import app.n_zik.compagnon.bridge.library.CollectionRef
import app.n_zik.compagnon.bridge.library.DislikeMode
import app.n_zik.compagnon.bridge.library.LibraryCache
import app.n_zik.compagnon.bridge.library.Page
import app.n_zik.compagnon.bridge.library.Playlist
import app.n_zik.compagnon.bridge.library.PlaylistSongsQuery
import app.n_zik.compagnon.bridge.library.PlaylistsFilter
import app.n_zik.compagnon.bridge.library.PlaylistsQuery
import app.n_zik.compagnon.bridge.library.RewindState
import app.n_zik.compagnon.bridge.library.SongsQuery
import app.n_zik.compagnon.bridge.pairing.ApiError
import app.n_zik.compagnon.bridge.pairing.BridgeContract
import app.n_zik.compagnon.bridge.pairing.BridgeErrorCode
import app.n_zik.compagnon.bridge.pairing.BridgeJson
import app.n_zik.compagnon.bridge.pairing.MetaResponse
import app.n_zik.compagnon.bridge.pairing.PairingRules
import app.n_zik.compagnon.bridge.pairing.RevocationPolicy
import app.n_zik.compagnon.bridge.pairing.ValidateRequest
import app.n_zik.compagnon.bridge.pairing.ValidateResponse
import app.n_zik.compagnon.utils.coroutines.NzikDispatchers
import app.n_zik.compagnon.bridge.state.CommandResponse
import app.n_zik.compagnon.bridge.state.Track
import app.n_zik.compagnon.bridge.state.TrackLike

/** Phone address received by pairing (contract §1: never a hard-coded port). */
data class ServerAddress(val ip: String, val port: Int) {
    val apiBase: String get() = "http://$ip:$port${BridgeContract.API_PREFIX}"
}

/** Outcome of `GET /api/v1/meta` (contract §5). */
sealed interface MetaResult {
    data class Ok(val meta: MetaResponse) : MetaResult
    /** Major version other than 1: "phone version incompatible", nothing paired. */
    data class Incompatible(val contractVersion: String) : MetaResult
    data object Unreachable : MetaResult
    data class Failed(val status: Int, val code: String?) : MetaResult
}

/** Outcome of `POST /api/v1/pairing/validate` (contract §4.5). */
sealed interface ValidateResult {
    data class Ok(val response: ValidateResponse) : ValidateResult
    /** `403 PAIRING_REJECTED`: "code invalid or expired". */
    data object Rejected : ValidateResult
    /** `429 RATE_LIMITED`: wait [retryAfterMs]. */
    data class RateLimited(val retryAfterMs: Long) : ValidateResult
    data object Unreachable : ValidateResult
    data class Failed(val status: Int, val code: String?) : ValidateResult
}

/** Outcome of the light Bearer probe, `GET /api/v1/library/songs?limit=1`. */
sealed interface ProbeResult {
    data object Ok : ProbeResult
    /** `401 DEVICE_REVOKED`: to be confirmed by [RevocationPolicy] before anything is erased. */
    data object Revoked : ProbeResult
    /** `409 CONFLICT_ACTIVE_CLIENT`: another PC holds the session (contract §6.2). */
    data class OtherActive(val deviceName: String?) : ProbeResult
    data object Unreachable : ProbeResult
    /** Any other answer, including a `401` that is not `DEVICE_REVOKED`: never erases anything. */
    data class Failed(val status: Int, val code: String?) : ProbeResult
}

/** The pairing-side bridge calls, behind an interface so the state machine is testable without a network. */
interface BridgeApi {
    suspend fun meta(address: ServerAddress): MetaResult
    suspend fun validate(address: ServerAddress, request: ValidateRequest): ValidateResult
    suspend fun probe(address: ServerAddress, deviceToken: String): ProbeResult
}

/**
 * Ktor client of the phone's bridge. Errors are mapped from the contract §3 `code`, never from
 * `message`. The device token travels only in the `Authorization` header and is never logged.
 */
class BridgeClient(engine: HttpClientEngine = CIO.create()) : BridgeApi, PlayerApi, LibraryApi, AudioApi, AutoCloseable {
    private val log = Logger.getLogger("BridgeClient")

    private val http = HttpClient(engine) {
        expectSuccess = false
        install(ContentNegotiation) { json(BridgeJson) }
        install(HttpTimeout) {
            connectTimeoutMillis = CONNECT_TIMEOUT_MS
            requestTimeoutMillis = REQUEST_TIMEOUT_MS
            socketTimeoutMillis = REQUEST_TIMEOUT_MS
        }
    }

    override suspend fun meta(address: ServerAddress): MetaResult {
        val response = call(address, "meta") { http.get("${address.apiBase}/meta") } ?: return MetaResult.Unreachable
        if (response.status != HttpStatusCode.OK) return MetaResult.Failed(response.status.value, response.errorBody()?.code)
        val meta = response.decode(MetaResponse.serializer()) ?: return MetaResult.Failed(response.status.value, null)
        return if (PairingRules.isSupportedContractVersion(meta.contractVersion)) MetaResult.Ok(meta)
        else MetaResult.Incompatible(meta.contractVersion)
    }

    override suspend fun validate(address: ServerAddress, request: ValidateRequest): ValidateResult {
        val response = call(address, "pairing/validate") {
            http.post("${address.apiBase}/pairing/validate") {
                contentType(ContentType.Application.Json)
                setBody(BridgeJson.encodeToString(ValidateRequest.serializer(), request))
            }
        } ?: return ValidateResult.Unreachable
        if (response.status == HttpStatusCode.OK) {
            val body = response.decode(ValidateResponse.serializer()) ?: return ValidateResult.Failed(200, null)
            return ValidateResult.Ok(body)
        }
        val error = response.errorBody()
        return when (error?.code) {
            BridgeErrorCode.PAIRING_REJECTED -> ValidateResult.Rejected
            BridgeErrorCode.RATE_LIMITED -> ValidateResult.RateLimited(error.retryAfterMs ?: 0L)
            else -> ValidateResult.Failed(response.status.value, error?.code)
        }
    }

    override suspend fun probe(address: ServerAddress, deviceToken: String): ProbeResult {
        val response = call(address, "library/songs") {
            http.get("${address.apiBase}${BridgeContract.PROBE_PATH}") {
                parameter("limit", 1)
                bearerAuth(deviceToken)
            }
        } ?: return ProbeResult.Unreachable
        if (response.status == HttpStatusCode.OK) return ProbeResult.Ok
        val error = response.errorBody()
        return when (error?.code) {
            BridgeErrorCode.DEVICE_REVOKED -> ProbeResult.Revoked
            BridgeErrorCode.CONFLICT_ACTIVE_CLIENT -> ProbeResult.OtherActive(error.activeDevice?.deviceName)
            else -> ProbeResult.Failed(response.status.value, error?.code)
        }
    }

    override suspend fun command(address: ServerAddress, deviceToken: String, route: String, body: String): CommandResult {
        val response = call(address, route) {
            http.post("${address.apiBase}/$route") {
                bearerAuth(deviceToken)
                contentType(ContentType.Application.Json)
                setBody(body)
            }
        } ?: return CommandResult.Unreachable
        if (response.status == HttpStatusCode.OK) {
            val decoded = response.decode(CommandResponse.serializer()) ?: return CommandResult.Error(200, null)
            return CommandResult.Ok(decoded)
        }
        return CommandResult.Error(response.status.value, response.errorBody())
    }

    override suspend fun artwork(address: ServerAddress, deviceToken: String, key: ArtworkKey): ArtworkResult {
        // Contract §1: an id in a URL path is percent-encoded.
        val id = key.id.encodeURLPathPart()
        val path = when (key.kind) {
            ArtworkKind.Track -> "artwork/$id"
            ArtworkKind.Album -> "library/albums/$id/artwork"
            ArtworkKind.Artist -> "library/artists/$id/artwork"
            ArtworkKind.Playlist -> "library/playlists/$id/artwork"
        }
        val response = call(address, "artwork") {
            http.get("${address.apiBase}/$path") {
                bearerAuth(deviceToken)
                parameter("size", ArtworkKey.bounded(key.size))
                // The phone fetches online artwork upstream before answering: allow more than a command
                timeout {
                    requestTimeoutMillis = ARTWORK_TIMEOUT_MS
                    socketTimeoutMillis = ARTWORK_TIMEOUT_MS
                }
            }
        } ?: return ArtworkResult.Unreachable
        if (response.status == HttpStatusCode.OK) {
            return runCatching { ArtworkResult.Ok(response.bodyAsBytes()) }.getOrElse { ArtworkResult.Unreachable }
        }
        val code = response.errorBody()?.code
        return if (code == BridgeErrorCode.NOT_FOUND) ArtworkResult.NotFound else ArtworkResult.Failed(response.status.value, code)
    }

    // ---- Audio (contract §8) ---------------------------------------------------------------------
    // The signed URL is a transfer credential: it is never logged (the route "audio" stands for it).

    override suspend fun forgeAudioUrl(address: ServerAddress, deviceToken: String, trackId: String, quality: String): ForgeResult {
        // Contract §1: an id in a URL path is percent-encoded.
        val response = call(address, "audio/url") {
            http.post("${address.apiBase}/audio/${trackId.encodeURLPathPart()}/url") {
                bearerAuth(deviceToken)
                contentType(ContentType.Application.Json)
                setBody(BridgeJson.encodeToString(AudioUrlRequest.serializer(), AudioUrlRequest(quality)))
            }
        } ?: return ForgeResult.Unreachable
        if (response.status == HttpStatusCode.OK) {
            val body = response.decode(AudioUrlResponse.serializer()) ?: return ForgeResult.Error(200, null)
            return ForgeResult.Ok(body)
        }
        return ForgeResult.Error(response.status.value, response.errorBody())
    }

    override suspend fun probeAudioUrl(url: String): AudioProbe {
        val head = audioCall { http.head(url) } ?: return AudioProbe.Unreachable
        return when (head.status.value) {
            in 200..299 -> AudioProbe.Ok
            401 -> AudioProbe.Revoked
            404 -> AudioProbe.NotFound
            502 -> AudioProbe.UpstreamFailed
            // A HEAD answer has no body: the code of a 403 is read from a one-byte GET.
            403 -> {
                val get = audioCall { http.get(url) { header(HttpHeaders.Range, "bytes=0-0") } } ?: return AudioProbe.Unreachable
                when (get.errorBody()?.code) {
                    BridgeErrorCode.AUDIO_URL_EXPIRED -> AudioProbe.Expired
                    BridgeErrorCode.DEVICE_REVOKED -> AudioProbe.Revoked
                    else -> if (get.status.value in 200..299) AudioProbe.Ok else AudioProbe.Invalid
                }
            }
            else -> AudioProbe.Failed(head.status.value)
        }
    }

    override suspend fun downloadAudio(url: String, target: Path): DownloadResult = try {
        http.prepareGet(url) {
            // A whole track: no overall deadline, only a stalled socket ends it.
            timeout {
                requestTimeoutMillis = Long.MAX_VALUE
                socketTimeoutMillis = DOWNLOAD_SOCKET_TIMEOUT_MS
            }
        }.execute { response ->
            if (response.status != HttpStatusCode.OK) return@execute DownloadResult.Failed(response.status.value)
            val channel = response.bodyAsChannel()
            var total = 0L
            withContext(NzikDispatchers.DATA) {
                Files.newOutputStream(target).use { out ->
                    val buffer = ByteArray(DOWNLOAD_BUFFER_BYTES)
                    while (true) {
                        val read = channel.readAvailable(buffer, 0, buffer.size)
                        if (read < 0) break
                        if (read > 0) {
                            out.write(buffer, 0, read)
                            total += read
                        }
                    }
                }
            }
            val expected = response.headers[HttpHeaders.ContentLength]?.toLongOrNull()
            if (expected != null && expected != total) DownloadResult.Failed(null) else DownloadResult.Done(total)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        log.info("audio download failed: ${e::class.simpleName}")
        DownloadResult.Failed(null)
    }

    /** `null` when the phone could not be reached; logged without the URL. */
    private suspend fun audioCall(block: suspend () -> HttpResponse): HttpResponse? =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.info("audio unreachable: ${e::class.simpleName}")
            null
        }

    // ---- Library (contract §10.1) ----------------------------------------------------------------

    override suspend fun songs(address: ServerAddress, deviceToken: String, offset: Int, limit: Int, query: SongsQuery): LibraryResult<Track> =
        library(address, deviceToken, "library/songs", Track.serializer(), offset, limit) {
            query.text?.let { parameter("query", it) }
            parameter("filter", query.filter.wire)
            parameter("sort", query.sort.wire)
            // Since 1.6 (`library.sort`): the phone re-sorts; a phone before 1.6 ignores the parameter
            parameter("reverse", query.reverse.toString())
            // Since 1.6: the period of the phone's Top tab; absent keeps the phone's own period
            query.period?.let { parameter("period", it.wire) }
        }

    override suspend fun playlists(address: ServerAddress, deviceToken: String, offset: Int, limit: Int, query: PlaylistsQuery): LibraryResult<Playlist> =
        library(address, deviceToken, "library/playlists", Playlist.serializer(), offset, limit) {
            parameter("filter", query.filter.wire)
            parameter("sort", query.sort.wire)
            parameter("reverse", query.reverse.toString())
            // Since 1.7.2 (feature `library.rewind`): the phone's Month / Year / All row, applied and
            // persisted by the phone; only sent on the Rewind chip
            if (query.rewind != null && query.filter == PlaylistsFilter.Rewind) parameter("rewind", query.rewind.wire)
            // Since 1.7.2: the phone's search (`total` stays pre-`text`)
            query.text?.let { parameter("text", it) }
        }

    override suspend fun albums(address: ServerAddress, deviceToken: String, offset: Int, limit: Int, query: AlbumsQuery): LibraryResult<Album> =
        library(address, deviceToken, "library/albums", Album.serializer(), offset, limit) {
            parameter("filter", query.filter.wire)
            parameter("sort", query.sort.wire)
            parameter("reverse", query.reverse.toString())
        }

    override suspend fun artists(address: ServerAddress, deviceToken: String, offset: Int, limit: Int, query: ArtistsQuery): LibraryResult<Artist> =
        library(address, deviceToken, "library/artists", Artist.serializer(), offset, limit) {
            parameter("filter", query.filter.wire)
            parameter("sort", query.sort.wire)
            parameter("reverse", query.reverse.toString())
        }

    override suspend fun collectionSongs(
        address: ServerAddress,
        deviceToken: String,
        collection: CollectionRef,
        offset: Int,
        limit: Int,
        query: PlaylistSongsQuery?,
    ): LibraryResult<Track> {
        // Contract §1: an id in a URL path is percent-encoded, never parsed.
        val route = "library/${collection.kind.segment}/${collection.id.encodeURLPathPart()}/songs"
        return library(address, deviceToken, route, Track.serializer(), offset, limit) {
            // A local playlist's `sort` and `reverse` (contract 1.6, `library.sort`); absent for
            // albums and artists, whose tracks keep the phone's fixed order
            query?.let {
                parameter("sort", it.sort.wire)
                parameter("reverse", it.reverse.toString())
                // Since 1.7.2: the phone's search (`total` stays pre-`text`)
                it.text?.let { text -> parameter("text", text) }
            }
        }
    }

    /** `GET /library/cache` (contract §10, since 1.7.1): `null` on any non-`200` answer (the UI hides its bar). */
    override suspend fun cacheSpace(address: ServerAddress, deviceToken: String): LibraryCache? {
        val response = call(address, "library/cache") {
            http.get("${address.apiBase}/library/cache") { bearerAuth(deviceToken) }
        } ?: return null
        if (response.status != HttpStatusCode.OK) return null
        return response.decode(LibraryCache.serializer())
    }

    /** `GET /library/rewind` (contract §10, since 1.7.2): `null` on any non-`200` answer (the UI hides its row). */
    override suspend fun rewindState(address: ServerAddress, deviceToken: String): RewindState? {
        val response = call(address, "library/rewind") {
            http.get("${address.apiBase}/library/rewind") { bearerAuth(deviceToken) }
        } ?: return null
        if (response.status != HttpStatusCode.OK) return null
        return response.decode(RewindState.serializer())
    }

    /** `GET /library/dislikeMode` (contract §10, since 1.7.2): `null` on any non-`200` answer (the UI keeps its pre-1.7.2 display). */
    override suspend fun dislikeMode(address: ServerAddress, deviceToken: String): DislikeMode? {
        val response = call(address, "library/dislikeMode") {
            http.get("${address.apiBase}/library/dislikeMode") { bearerAuth(deviceToken) }
        } ?: return null
        if (response.status != HttpStatusCode.OK) return null
        return response.decode(DislikeMode.serializer())
    }

    // ---- Library writes (contract §10.2, since 1.7): the explicit state, local Room only ----

    override suspend fun songLike(address: ServerAddress, deviceToken: String, songId: String, state: TrackLike): WriteResult =
        write(address, deviceToken, "library/songs/${songId.encodeURLPathPart()}/like", SongLikeAnswer.serializer(),
            """{"state":"${state.wire}"}""") { WriteResult.SongLike(it.state) }

    override suspend fun albumBookmark(address: ServerAddress, deviceToken: String, albumId: String, bookmarked: Boolean): WriteResult =
        write(address, deviceToken, "library/albums/${albumId.encodeURLPathPart()}/bookmark", AlbumBookmarkAnswer.serializer(),
            """{"bookmarked":$bookmarked}""") { WriteResult.AlbumBookmark(it.bookmarked) }

    override suspend fun artistFollow(address: ServerAddress, deviceToken: String, artistId: String, state: ArtistFollow): WriteResult =
        write(address, deviceToken, "library/artists/${artistId.encodeURLPathPart()}/follow", ArtistFollowAnswer.serializer(),
            """{"state":"${state.wire}"}""") { WriteResult.ArtistFollow(it.state) }

    override suspend fun playlistPin(address: ServerAddress, deviceToken: String, playlistId: String, pinned: Boolean): WriteResult =
        write(address, deviceToken, "library/playlists/${playlistId.encodeURLPathPart()}/pin", PlaylistPinAnswer.serializer(),
            """{"pinned":$pinned}""") { WriteResult.PlaylistPin(it.pinned) }

    override suspend fun albumLike(address: ServerAddress, deviceToken: String, albumId: String, state: AlbumLike): WriteResult =
        write(address, deviceToken, "library/albums/${albumId.encodeURLPathPart()}/like", AlbumLikeAnswer.serializer(),
            """{"state":"${state.wire}"}""") { WriteResult.AlbumLike(it.state) }

    override suspend fun playlistBookmark(address: ServerAddress, deviceToken: String, playlistId: String, bookmarked: Boolean): WriteResult =
        write(address, deviceToken, "library/playlists/${playlistId.encodeURLPathPart()}/bookmark", PlaylistBookmarkAnswer.serializer(),
            """{"bookmarked":$bookmarked}""") { WriteResult.PlaylistBookmark(it.bookmarked) }

    /**
     * A §10.2 Bearer `POST`: the `200` body decoded into [WriteResult] by [onAnswer], its errors
     * decided from the contract `code`. The body is built from fixed wire values: nothing user-provided
     * travels in it.
     */
    private suspend fun <A> write(
        address: ServerAddress,
        deviceToken: String,
        route: String,
        answer: KSerializer<A>,
        body: String,
        onAnswer: (A) -> WriteResult,
    ): WriteResult {
        val response = call(address, route.split('/').take(2).joinToString("/")) {
            http.post("${address.apiBase}/$route") {
                bearerAuth(deviceToken)
                contentType(ContentType.Application.Json)
                setBody(body)
            }
        } ?: return WriteResult.Unreachable
        if (response.status == HttpStatusCode.OK) {
            return response.decode(answer)?.let(onAnswer) ?: WriteResult.Failed(200, null)
        }
        val error = response.errorBody()
        return when (error?.code) {
            BridgeErrorCode.NOT_FOUND -> WriteResult.NotFound
            BridgeErrorCode.DEVICE_REVOKED -> WriteResult.Revoked
            BridgeErrorCode.CONFLICT_ACTIVE_CLIENT -> WriteResult.OtherActive(error.activeDevice?.deviceName)
            else -> WriteResult.Failed(response.status.value, error?.code)
        }
    }

    /** A paginated Bearer `GET`, its errors decided from the contract `code`. */
    private suspend fun <T> library(
        address: ServerAddress,
        deviceToken: String,
        route: String,
        itemSerializer: KSerializer<T>,
        offset: Int,
        limit: Int,
        parameters: HttpRequestBuilder.() -> Unit = {},
    ): LibraryResult<T> {
        // Logged without the collection id
        val response = call(address, route.split('/').take(2).joinToString("/")) {
            http.get("${address.apiBase}/$route") {
                bearerAuth(deviceToken)
                parameter("offset", offset)
                parameter("limit", limit)
                parameters()
            }
        } ?: return LibraryResult.Unreachable
        if (response.status == HttpStatusCode.OK) {
            val page = response.decode(Page.serializer(itemSerializer)) ?: return LibraryResult.Failed(200, null)
            return LibraryResult.Ok(page)
        }
        val error = response.errorBody()
        return when (error?.code) {
            BridgeErrorCode.NOT_FOUND -> LibraryResult.NotFound
            BridgeErrorCode.DEVICE_REVOKED -> LibraryResult.Revoked
            BridgeErrorCode.CONFLICT_ACTIVE_CLIENT -> LibraryResult.OtherActive(error.activeDevice?.deviceName)
            else -> LibraryResult.Failed(response.status.value, error?.code)
        }
    }

    override fun close() = http.close()

    /** `null` when the phone could not be reached (refused, timeout, unknown host…). */
    private suspend fun call(address: ServerAddress, route: String, block: suspend () -> HttpResponse): HttpResponse? =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.info("${address.ip}:${address.port} $route unreachable: ${e::class.simpleName}")
            null
        }

    private suspend fun HttpResponse.errorBody(): ApiError? =
        runCatching { BridgeJson.decodeFromString(ApiError.serializer(), bodyAsText()) }.getOrNull()

    private suspend fun <T> HttpResponse.decode(serializer: KSerializer<T>): T? =
        runCatching { BridgeJson.decodeFromString(serializer, bodyAsText()) }.getOrNull()

    private companion object {
        const val CONNECT_TIMEOUT_MS = 3_000L
        const val REQUEST_TIMEOUT_MS = 8_000L
        const val ARTWORK_TIMEOUT_MS = 20_000L
        const val DOWNLOAD_SOCKET_TIMEOUT_MS = 30_000L
        const val DOWNLOAD_BUFFER_BYTES = 64 * 1_024
    }
}
