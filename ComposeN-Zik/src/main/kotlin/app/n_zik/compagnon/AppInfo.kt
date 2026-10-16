package app.n_zik.compagnon

/** Static identity of the desktop companion app. */
object AppInfo {
    /** The base product name (stable / debug / -git builds — no channel suffix). */
    const val NAME = "N-Zik Desktop Compagnon"

    /**
     * The per-channel INSTALL identity (AD-8): the install directory name, the window title,
     * the Linux launcher, the Flatpak `exec`. This drives the install identity ONLY — the
     * storage names (the app data dir, the secret store target) follow [dataName] instead
     * (the 2026-10-16 storage regrouping: stable + beta share the base storage, dev / debug /
     * git are isolated).
     */
    fun productName(channel: String): String = when (channel) {
        "beta" -> "$NAME BETA"
        "dev" -> "$NAME DEV"
        else -> NAME
    }

    /**
     * The per-channel STORAGE name (the 2026-10-16 storage regrouping, spec
     * `spec-channel-storage-portable-targz`): the app data dir name AND the secret store
     * target. Stable and beta SHARE the plain base name — the beta tests against the stable
     * user's real data — while dev / debug / git each carry their own isolated `… DEV` /
     * `… DEBUG` / `… GIT` name. Every pre-regrouping build only ever wrote the base, so
     * stable needs no migration; the legacy beta `… BETA` dir (the 2026-10-16 isolated-beta
     * convention, reversed by this regrouping) is merged back into the base once on first
     * launch — the base files win, and the `… BETA` dir survives (it is never deleted).
     */
    fun dataName(channel: String): String = when (channel) {
        "dev" -> "$NAME DEV"
        "debug" -> "$NAME DEBUG"
        "git" -> "$NAME GIT"
        else -> NAME
    }
}
