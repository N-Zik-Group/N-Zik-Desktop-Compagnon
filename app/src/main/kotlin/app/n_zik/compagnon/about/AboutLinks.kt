package app.n_zik.compagnon.about

/**
 * The desktop repo links (spec `spec-updater` AD-10 — the phone's `Repository.REPO_URL` /
 * `REPO_URL + issuePath` swapped for the DESKTOP repo `N-Zik-Group/N-Zik-Desktop-Compagnon`, the
 * SAME issue-template paths as the phone): the "by" author link, the troubleshooting section
 * (view source / report an issue / request a feature). Pure, so they are pinned by a test.
 */
object AboutLinks {

    /** The desktop repo (the troubleshooting "view source" target). */
    const val DESKTOP_REPO_URL = "https://github.com/N-Zik-Group/N-Zik-Desktop-Compagnon"

    /** The author's GitHub (the app-info card's "by N-Zik-Group" link). */
    const val REPO_OWNER_URL = "https://github.com/N-Zik-Group"

    /**
     * The new-issue URL of a kind — `"bug"` → the phone's bug template, anything else the feature
     * template (the phone's exact query strings, kept verbatim).
     */
    fun issueUrl(kind: String): String = when (kind) {
        "bug" -> "$DESKTOP_REPO_URL/issues/new?assignees=&labels=bug&template=bug_report.yaml"
        else -> "$DESKTOP_REPO_URL/issues/new?assignees=&labels=feature_request&template=feature_request.yaml"
    }
}
