package app.n_zik.compagnon.updater.models

/**
 * The three-state update-check choice (spec `spec-updater`, AD-9, loop 2 — the phone's
 * `CheckUpdateState`, which replaces the v1 boolean `checkForUpdatesEnabled`):
 *
 *  * [On] — an automatic check at startup (non-forced — it honors the "update cancelled" flag,
 *    the phone's Enabled), the update dialog on result;
 *  * [Ask] — a confirmation dialog at startup first; only a confirmed check runs, forced (the
 *    phone's Ask — the only path that may reset the cancelled flag);
 *  * [Off] — no startup check at all (the phone's Disabled).
 *
 * All three stay gated on `AppVersion.updaterEnabled`: debug and -git are source builds and
 * never check, whatever this choice says (anti-downgrade). Persisted in `settings.json`
 * (`checkUpdateState`, the lowercase wire value).
 */
enum class CheckUpdateState(val wire: String) {

    /** Automatic check at startup. */
    On("on"),

    /** Ask the user at startup before checking (a confirmed check is forced). */
    Ask("ask"),

    /** No startup check. */
    Off("off");

    companion object {

        /** Decodes a `settings.json` value; unknown/absent values fall back to [On] (the default). */
        fun fromWire(value: String?): CheckUpdateState =
            entries.firstOrNull { it.wire == value?.lowercase() } ?: On
    }
}
