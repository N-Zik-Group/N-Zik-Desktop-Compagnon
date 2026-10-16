package app.n_zik.compagnon

/** Static identity of the desktop companion app. */
object AppInfo {
    /** The base product name (stable / debug / -git builds — no channel suffix). */
    const val NAME = "N-Zik Desktop Compagnon"

    /**
     * The per-channel product name (AD-8: the install directory name, the window title, the
     * Linux launcher, the Flatpak `exec` — and since the 2026-10-16 channel separation, the app
     * data dir name AND the secret store target: beta / dev carry their own `… BETA` / `… DEV`
     * data dir and credential, stable / debug / -git keep the plain base — the same base every
     * pre-channel build already used, so stable needs no migration).
     */
    fun productName(channel: String): String = when (channel) {
        "beta" -> "$NAME BETA"
        "dev" -> "$NAME DEV"
        else -> NAME
    }
}
