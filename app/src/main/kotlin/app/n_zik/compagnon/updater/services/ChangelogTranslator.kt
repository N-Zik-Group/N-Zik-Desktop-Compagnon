package app.n_zik.compagnon.updater.services

import app.n_zik.compagnon.utils.coroutines.NzikDispatchers
import dev.rebelonion.translator.Language
import dev.rebelonion.translator.Translator
import java.util.logging.Logger
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

/**
 * The "What's new" changelog translation (spec `spec-updater`, AD-9, loop 2 — port of the phone's
 * `getTranslatorClient` + the `Translator` usage in `UpdateScreen`): the sanctioned
 * `com.github.rebelonion:translator` (the phone's same version) over an OkHttp client with a
 * Chrome User-Agent — the phone's exact interceptor, kept verbatim (the Google endpoint is
 * finicky about non-browser user agents). One client for the app's lifetime by default (the lazy
 * [defaultClient] — the [httpClientFactory] seam is the JVM tests' only door in): the translation
 * requests are few and short, no long-lived pool is needed.
 *
 * Translation is a CONVENIENCE, never a blocker: any failure (offline, refused, bad language)
 * returns `null` and the "What's new" card falls back to the original English text.
 */
object ChangelogTranslator {

    private val log = Logger.getLogger("ChangelogTranslator")

    private const val CHROME_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    /**
     * The Chrome User-Agent client (the Google endpoint is finicky about non-browser user agents —
     * the phone's exact interceptor, kept verbatim): built once, kept for the app's lifetime.
     */
    private val defaultClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("User-Agent", CHROME_USER_AGENT)
                        .build(),
                )
            }
            .build()
    }

    /**
     * The OkHttp client factory of the [translate] calls — the JVM test seam (the test points it
     * at a client whose interceptor fakes the Google endpoint's answers: a refused request, an
     * empty translation); `null` (the default) keeps the single lazy [defaultClient] for the
     * app's lifetime.
     */
    internal var httpClientFactory: (() -> OkHttpClient)? = null

    /**
     * Translates [text] to [destination] (source [source] — [Language.AUTO] detects it). Returns
     * the `translatedText`, or `null` when the text is blank or the request failed (the caller
     * keeps the original text). Runs on the [NzikDispatchers.DATA] dispatcher (the library's
     * OkHttp call is suspending, but the work is IO-bound and the house rule names the dispatcher).
     */
    suspend fun translate(text: String, destination: Language, source: Language): String? =
        if (text.isBlank()) null else withContext(NzikDispatchers.DATA) {
            runCatching { Translator(client()).translate(text, destination, source).translatedText }
                .onFailure {
                    log.warning("Changelog translation failed: ${it::class.simpleName} (${it.message})")
                }
                .getOrNull()
                ?.takeIf { it.isNotBlank() }
        }

    /** The client of this call — the [httpClientFactory] seam (the default is the lazy [defaultClient]). */
    private fun client(): OkHttpClient = httpClientFactory?.invoke() ?: defaultClient
}
