package app.n_zik.compagnon.updater.services

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.client.engine.cio.endpoint

/**
 * The updater's Ktor CIO client (house pattern: `BridgeSession.httpClient` / `BridgeClient`):
 * plain HTTP against the GitHub API + the raw changelogs, no plugins. One client per operation —
 * the updater does a handful of requests per session, never a long-lived pool.
 *
 * Timeouts (spec `spec-updater`, loop 2 — the phone's `getClientWithTimeout` discipline, made
 * explicit on the CIO engine, review findings #12 / #24): the CONNECT is bounded (a dead network
 * must not hang the check), the SOCKET is bounded to the download's needs (a stalled chunk — no
 * data between two packets — is cut after [SOCKET_TIMEOUT_MS]; CIO's default is INFINITE), and
 * the REQUEST timeout is DISABLED (CIO's default of 15 s would abort a slow hundred-MB
 * installer; the socket timeout alone bounds the stall).
 */
fun updaterHttpClient(
    engine: HttpClientEngine = CIO.create {
        requestTimeout = 0L
        endpoint {
            connectTimeout = CONNECT_TIMEOUT_MS
            socketTimeout = SOCKET_TIMEOUT_MS
        }
    },
): HttpClient = HttpClient(engine)

private const val CONNECT_TIMEOUT_MS = 10_000L
private const val SOCKET_TIMEOUT_MS = 120_000L
