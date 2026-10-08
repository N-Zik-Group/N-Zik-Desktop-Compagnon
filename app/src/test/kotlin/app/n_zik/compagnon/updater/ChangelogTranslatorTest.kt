package app.n_zik.compagnon.updater

import app.n_zik.compagnon.updater.services.ChangelogTranslator
import dev.rebelonion.translator.Language
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * The "What's new" changelog translation (spec `spec-updater`, AD-9 — [ChangelogTranslator]):
 * translation is a CONVENIENCE, never a blocker — a blank input, a refused / failed request and
 * an EMPTY translated body all map to `null` (the card keeps the original English text). The
 * Google endpoint is faked through the [ChangelogTranslator.httpClientFactory] seam: the test
 * client's interceptor answers without the network (the response shape is the library's expected
 * `JsonArray` — the translation segments first, the source-language code third, per the
 * `Translation` parsing).
 */
class ChangelogTranslatorTest {

    /** A canned Google endpoint answer (the `Translation` parse: [segments, …, sourceLanguageCode]). */
    private fun endpointBody(translated: String) = """[ [["$translated", "hello", "fr"]], [], "en" ]"""

    private fun fakeClient(body: String, code: Int = 200, onRequest: () -> Unit = {}): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor { chain ->
                onRequest()
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(code)
                    .message(if (code == 200) "OK" else "Server Error")
                    .body(ResponseBody.create("application/json".toMediaType(), body))
                    .build()
            }
            .build()

    @AfterEach
    fun tearDown() {
        // Restore the production default (the lazy Chrome-UA client)
        ChangelogTranslator.httpClientFactory = null
    }

    @Test
    fun `a blank input is null without any request`() = runBlocking {
        val requests = AtomicInteger()
        ChangelogTranslator.httpClientFactory = { fakeClient(endpointBody("x"), onRequest = { requests.incrementAndGet() }) }

        assertNull(ChangelogTranslator.translate("", Language.FRENCH, Language.AUTO))
        assertNull(ChangelogTranslator.translate("   ", Language.FRENCH, Language.AUTO))
        assertEquals(0, requests.get(), "a blank input must not reach the endpoint")
    }

    @Test
    fun `a refused request is null`() = runBlocking {
        ChangelogTranslator.httpClientFactory = { fakeClient("error", code = 500) }
        assertNull(ChangelogTranslator.translate("New pairing screen", Language.FRENCH, Language.AUTO))
    }

    @Test
    fun `a failed connection is null`() = runBlocking {
        // The interceptor throws — the library's onFailure path (IOException → TranslationException)
        ChangelogTranslator.httpClientFactory = {
            OkHttpClient.Builder()
                .addInterceptor { throw SocketTimeoutException("timeout") }
                .build()
        }
        assertNull(ChangelogTranslator.translate("New pairing screen", Language.FRENCH, Language.AUTO))
    }

    @Test
    fun `an empty translated body is null`() = runBlocking {
        ChangelogTranslator.httpClientFactory = { fakeClient(endpointBody("")) }
        assertNull(ChangelogTranslator.translate("New pairing screen", Language.FRENCH, Language.AUTO))
    }

    @Test
    fun `a translated body comes back through the seam`() = runBlocking {
        ChangelogTranslator.httpClientFactory = { fakeClient(endpointBody("Bonjour")) }
        assertEquals("Bonjour", ChangelogTranslator.translate("New pairing screen", Language.FRENCH, Language.AUTO))
    }
}
