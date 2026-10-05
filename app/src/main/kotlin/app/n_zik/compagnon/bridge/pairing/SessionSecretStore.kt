package app.n_zik.compagnon.bridge.pairing

/**
 * `deviceToken` kept in memory only, for the lifetime of the process: the fallback on Linux when
 * neither the keyring library (`libsecret`) nor the Secret Service daemon is available. Pairing
 * stays usable for the session; a restart reads "not paired" and asks to pair again. Never touches
 * the disk.
 */
object SessionSecretStore : SecretStore {
    @Volatile
    private var secret: String? = null

    override fun read(): String? = secret

    override fun write(secret: String): Boolean {
        this.secret = secret
        return true
    }

    override fun delete() {
        secret = null
    }
}
