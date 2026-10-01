package app.n_zik.compagnon.pairing

import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.WString
import com.sun.jna.ptr.PointerByReference
import java.util.logging.Logger

/**
 * `deviceToken` in the Windows Credential Manager: a generic credential named [target]
 * (`CRED_TYPE_GENERIC`, `CRED_PERSIST_LOCAL_MACHINE`, blob = the token in UTF-8), encrypted by
 * Windows for the current user. Written by `CredWrite`, read by `CredRead` then `CredFree`,
 * removed by `CredDelete`. Any failure reads as "no secret", never as a crash.
 */
class WindowsCredentialSecretStore(private val target: String) : SecretStore {
    private val log = Logger.getLogger("WindowsCredentialSecretStore")

    override fun read(): String? = withApi("read") { api ->
        val out = PointerByReference()
        if (!api.CredReadW(WString(target), CRED_TYPE_GENERIC, 0, out)) {
            val error = Native.getLastError()
            if (error != ERROR_NOT_FOUND) log.warning("CredRead failed (error $error)")
            return@withApi null
        }
        try {
            val credential = Credential(out.value)
            val size = credential.CredentialBlobSize
            if (size <= 0 || credential.CredentialBlob == null) null
            else String(credential.CredentialBlob!!.getByteArray(0, size), Charsets.UTF_8)
        } finally {
            api.CredFree(out.value)
        }
    }

    override fun write(secret: String): Boolean = withApi("write") { api ->
        val bytes = secret.toByteArray(Charsets.UTF_8)
        val blob = Memory(bytes.size.toLong()).apply { write(0, bytes, 0, bytes.size) }
        try {
            val credential = Credential().apply {
                Type = CRED_TYPE_GENERIC
                TargetName = WString(target)
                UserName = WString(USER_NAME)
                CredentialBlobSize = bytes.size
                CredentialBlob = blob
                Persist = CRED_PERSIST_LOCAL_MACHINE
            }
            api.CredWriteW(credential, 0).also { ok ->
                if (!ok) log.warning("CredWrite failed (error ${Native.getLastError()})")
            }
        } finally {
            blob.clear()
            blob.close()
        }
    } ?: false

    override fun delete() {
        withApi("delete") { api ->
            if (!api.CredDeleteW(WString(target), CRED_TYPE_GENERIC, 0)) {
                val error = Native.getLastError()
                if (error != ERROR_NOT_FOUND) log.warning("CredDelete failed (error $error)")
            }
        }
    }

    private fun <T> withApi(operation: String, block: (CredApi) -> T): T? = runCatching { block(CredApi.INSTANCE) }
        .onFailure { log.warning("Credential Manager $operation failed: ${it::class.simpleName}") }
        .getOrNull()

    /** Win32 `CREDENTIALW` (wincred.h). */
    @Suppress("PropertyName", "unused")
    @Structure.FieldOrder(
        "Flags", "Type", "TargetName", "Comment", "LastWrittenLow", "LastWrittenHigh",
        "CredentialBlobSize", "CredentialBlob", "Persist", "AttributeCount", "Attributes", "TargetAlias", "UserName",
    )
    class Credential : Structure {
        @JvmField var Flags: Int = 0
        @JvmField var Type: Int = 0
        @JvmField var TargetName: WString? = null
        @JvmField var Comment: WString? = null
        @JvmField var LastWrittenLow: Int = 0
        @JvmField var LastWrittenHigh: Int = 0
        @JvmField var CredentialBlobSize: Int = 0
        @JvmField var CredentialBlob: Pointer? = null
        @JvmField var Persist: Int = 0
        @JvmField var AttributeCount: Int = 0
        @JvmField var Attributes: Pointer? = null
        @JvmField var TargetAlias: WString? = null
        @JvmField var UserName: WString? = null

        constructor() : super()
        constructor(pointer: Pointer) : super(pointer) {
            read()
        }
    }

    /** The four `advapi32` credential functions, wide-char variants. */
    @Suppress("FunctionName")
    private interface CredApi : Library {
        fun CredWriteW(credential: Credential, flags: Int): Boolean
        fun CredReadW(targetName: WString, type: Int, flags: Int, credential: PointerByReference): Boolean
        fun CredDeleteW(targetName: WString, type: Int, flags: Int): Boolean
        fun CredFree(buffer: Pointer)

        companion object {
            val INSTANCE: CredApi by lazy { Native.load("Advapi32", CredApi::class.java) }
        }
    }

    private companion object {
        const val CRED_TYPE_GENERIC = 1
        const val CRED_PERSIST_LOCAL_MACHINE = 2
        const val ERROR_NOT_FOUND = 1168
        const val USER_NAME = "deviceToken"
    }
}
