package app.n_zik.compagnon.bridge.pairing

import app.n_zik.compagnon.AppInfo
import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.ptr.PointerByReference
import java.util.logging.Level
import java.util.logging.LogRecord
import java.util.logging.Logger

/**
 * `deviceToken` in the system keyring (the GNOME Secret Service, `libsecret` loaded at runtime
 * with JNA — no new Gradle dependency): stored in the default collection under the [SCHEMA_NAME]
 * schema and [ATTRIBUTE] attribute, encrypted by the keyring daemon. Written by
 * `secret_password_storev_sync`, read by `secret_password_lookupv_sync`, removed by
 * `secret_password_clearv_sync` — the stable libsecret API, present unchanged in 0.20.x and
 * 0.21.x (the `SecretPasswordStore` object API was removed in 0.21). Any failure reads as "no
 * secret" or a failed store, never as a crash. The schema and attribute table are held for the
 * lifetime of the process (no close hook).
 */
class LinuxSecretStore private constructor(
    private val api: SecretApi,
    private val glib: GLibApi,
    private val schema: Pointer,
    private val attributes: Pointer,
    // C strings retained by the [attributes] table (g_hash_table keeps the raw pointers).
    @Suppress("unused") private val attributeCstr: Memory,
    @Suppress("unused") private val valueCstr: Memory,
) : SecretStore {
    private val log = Logger.getLogger("LinuxSecretStore")

    override fun read(): String? = withApi {
        val error = PointerByReference()
        val password = api.secret_password_lookupv_sync(schema, attributes, null, error)
        if (password == null) {
            logError("lookup", error)
            return@withApi null
        }
        try {
            password.getString(0, "UTF-8")
        } finally {
            api.secret_password_free(password)
        }
    }

    override fun write(secret: String): Boolean = withApi {
        val error = PointerByReference()
        val stored = api.secret_password_storev_sync(schema, attributes, null, LABEL, secret, null, error)
        if (!stored) logError("store", error)
        stored
    } ?: false

    override fun delete() {
        withApi {
            val error = PointerByReference()
            if (!api.secret_password_clearv_sync(schema, attributes, null, error)) {
                logError("clear", error)
            }
        }
    }

    /** Runs a keyring call, downgrading any native failure to `null` / `false` (never a crash). */
    private fun <T> withApi(block: () -> T): T? = runCatching { block() }
        .onFailure { log.warning("Keyring call failed: ${it::class.simpleName}") }
        .getOrNull()

    /** Logs a `GError` (never the token), then frees it. A "no secret" result carries no error. */
    private fun logError(operation: String, error: PointerByReference) {
        val pointer = error.value ?: return
        val detail = gErrorMessage(pointer)?.let { " ($it)" }.orEmpty()
        log.warning("Keyring $operation failed$detail")
        glib.g_error_free(pointer)
    }



    /** The stable libsecret schema/password API (`libsecret-1`). */
    @Suppress("FunctionName")
    private interface SecretApi : Library {
        fun secret_schema_newv(name: Pointer, flags: Int, attributeNamesAndTypes: Pointer): Pointer?
        fun secret_password_storev_sync(
            schema: Pointer,
            attributes: Pointer,
            collection: Pointer?,
            label: String,
            password: String,
            cancellable: Pointer?,
            error: PointerByReference,
        ): Boolean
        fun secret_password_lookupv_sync(
            schema: Pointer,
            attributes: Pointer,
            cancellable: Pointer?,
            error: PointerByReference,
        ): Pointer?
        fun secret_password_clearv_sync(
            schema: Pointer,
            attributes: Pointer,
            cancellable: Pointer?,
            error: PointerByReference,
        ): Boolean
        fun secret_password_free(password: Pointer)
    }

    /**
     * The `glib-2.0` functions used here (hash table construction and `GError` cleanup).
     * The nullable `Pointer` parameters accept C's zero pointer — JNA 5.19's `Pointer.NULL`
     * is literally Java `null`, so a non-null Kotlin parameter would reject it.
     */
    @Suppress("FunctionName")
    private interface GLibApi : Library {
        fun g_hash_table_new_full(hashFunc: Pointer, keyEqual: Pointer, valueDestroy: Pointer?, keyDestroy: Pointer?): Pointer?
        fun g_hash_table_insert(table: Pointer, key: Pointer, value: Pointer?)
        fun g_hash_table_destroy(table: Pointer)
        fun g_error_free(error: Pointer)
    }

    companion object {
        /** The dotted schema name (libsecret convention, e.g. `org.gnome.keyring.NetworkPassword`). */
        const val SCHEMA_NAME = "n-zik.desktop.compagnon"
        const val ATTRIBUTE = "deviceToken"
        const val LABEL = "N-Zik Desktop Compagnon pairing token"
        private const val VALUE = AppInfo.NAME

        /** `SECRET_SCHEMA_NONE`. */
        private const val SCHEMA_NONE = 0
        /** `SECRET_SCHEMA_ATTRIBUTE_STRING`. */
        private const val ATTRIBUTE_TYPE_STRING = 0

        private val log = Logger.getLogger("LinuxSecretStore")

        private fun cstring(text: String): Memory {
            val memory = Memory((text.length + 1) * 4L)
            memory.setString(0, text, "UTF-8")
            return memory
        }

        /**
         * The message of the GLib `GError` that libsecret failures are reported in. Read directly
         * off the pointer (`guint` domain, `int` code, then `gchar *message` at offset 8 on
         * 64-bit) instead of a JNA `Structure`: JNA 5.19 no longer makes non-final fields
         * accessible, so the field reflection of a private structure class throws on `deriveLayout`.
         */
        private fun gErrorMessage(error: Pointer): String? = error.getPointer(8L)?.getString(0, "UTF-8")

        /**
         * The keyring store, or `null` when `libsecret` cannot be loaded (JNA reports an
         * `UnsatisfiedLinkError`, which `runCatching` below catches) or the Secret Service
         * daemon cannot be reached (the probe lookup below reports a `GError`) — the caller
         * then falls back to [SessionSecretStore]. JNA resolves the soname itself
         * (`secret-1` finds `libsecret-1.so.0`, no `-dev` symlink needed).
         */
        fun create(): LinuxSecretStore? = runCatching {
            val api = Native.load("secret-1", SecretApi::class.java)
            val glib = Native.load("glib-2.0", GLibApi::class.java)
            // The glib string hash/equal used as hash-table callbacks (a `Function` is a `Pointer`
            // on the native function's address). Resolved on the already-loaded library instance:
            // JNA's two-argument `Function.getFunction(symbol, library)` is misleading — the first
            // argument is the library name (verified on the JNA 5.19.1 bytecode), so the static
            // form read as symbol-first requested `libg_str_hash.so` and failed.
            val glibNative = Native.getNativeLibrary(glib)
            val strHash = glibNative.getFunction("g_str_hash")
            val strEqual = glibNative.getFunction("g_str_equal")

            // The schema: one string attribute, [ATTRIBUTE]. The table value is the attribute type
            // enum cast to a pointer (libsecret reads it back with `GPOINTER_TO_INT`, 0.20.x and
            // 0.21.x alike); `SECRET_SCHEMA_ATTRIBUTE_STRING` is 0, i.e. the null pointer.
            val schemaName = cstring(SCHEMA_NAME)
            val attribute = cstring(ATTRIBUTE)
            val schemaTypes = glib.g_hash_table_new_full(strHash, strEqual, null, null) ?: return@runCatching null
            glib.g_hash_table_insert(schemaTypes, attribute, null)
            val schema = api.secret_schema_newv(schemaName, SCHEMA_NONE, schemaTypes)
            glib.g_hash_table_destroy(schemaTypes)
            schemaName.close()
            if (schema == null) return@runCatching null

            // The attribute table (attribute -> value) used by every store/lookup/clear call.
            val value = cstring(VALUE)
            val attributes = glib.g_hash_table_new_full(strHash, strEqual, null, null) ?: return@runCatching null
            glib.g_hash_table_insert(attributes, attribute, value)

            // Probe: a lookup on our own attributes. No daemon -> `GError` -> no keyring; a
            // "no item yet" answer is a `null` password with no error.
            val error = PointerByReference()
            val probe = api.secret_password_lookupv_sync(schema, attributes, null, error)
            if (probe != null) api.secret_password_free(probe)
            val probeError = error.value
            if (probeError != null) {
                val detail = gErrorMessage(probeError)?.let { " ($it)" }.orEmpty()
                glib.g_error_free(probeError)
                log.info("Secret Service daemon unreachable$detail: using the session store")
                return@runCatching null
            }

            LinuxSecretStore(api, glib, schema, attributes, attribute, value)
        }.onFailure {
            // JUL has no warning(msg, throwable) overload: the stack trace rides the LogRecord.
            val record = LogRecord(Level.WARNING, "Keyring unavailable: ${it::class.simpleName}: ${it.message}")
            record.thrown = it
            log.log(record)
        }.getOrNull()
    }
}
