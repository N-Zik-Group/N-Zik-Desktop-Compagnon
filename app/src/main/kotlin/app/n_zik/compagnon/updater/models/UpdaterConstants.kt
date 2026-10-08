package app.n_zik.compagnon.updater.models

/**
 * Port of the phone's `UpdaterConstants`, adapted to the desktop (spec `spec-updater`, AD-3):
 * the releases come from the desktop repo `N-Zik-Group/N-Zik-Desktop-Compagnon` (the phone points
 * at its own repo), the changelogs live in `Updater/changelogs/` of this repo (the mobile
 * convention: `{versionCode}.txt` + `dev.txt`, English), on the `main` branch.
 *
 * Deliberate deviation (spec AD-6): the phone's short build-type suffixes (`-f` / `-b` / `-m`)
 * are NOT ported — the desktop channel suffixes are the full `-beta` / `-dev` / `-git`.
 */
object UpdaterConstants {

    const val SUFFIX_BETA = "-beta"
    const val SUFFIX_DEV = "-dev"
    const val SUFFIX_GIT = "-git"

    const val SUFFIX_CHAR_BETA = "beta"
    const val SUFFIX_CHAR_DEV = "dev"
    const val SUFFIX_CHAR_GIT = "git"

    const val TYPE_STABLE = "stable"
    const val TYPE_BETA = "beta"
    const val TYPE_DEV = "dev"
    const val TYPE_GIT = "git"
    const val TYPE_DEBUG = "debug"

    const val PREFIX_VERSION = "v"

    const val GITHUB_API = "https://api.github.com"
    const val GITHUB_RAW = "https://raw.githubusercontent.com"

    const val REPO = "N-Zik-Group/N-Zik-Desktop-Compagnon"

    /** The browser URL of the desktop repo (the phone's `Repository.REPO_URL` — the update page's GitHub link, the About page's links). */
    const val REPO_URL = "https://github.com/$REPO"

    const val CHANGELOGS_PATH = "Updater/changelogs"
    const val CHANGELOGS_URL = "$GITHUB_RAW/$REPO/main/$CHANGELOGS_PATH"

    // The changelog category keys (port of the phone's `UpdaterConstants.CHANGELOG_*`, verbatim —
    // the "What's new" cards parse the release body into these, spec AD-9).
    const val CHANGELOG_ADDED = "added"
    const val CHANGELOG_CHANGED = "changed"
    const val CHANGELOG_IMPROVED = "improved"
    const val CHANGELOG_FIXED = "fixed"
    const val CHANGELOG_REFACTOR = "refactor"
    const val CHANGELOG_OTHER = "other"
    const val CHANGELOG_REMOVED = "removed"
    const val CHANGELOG_DEPRECATED = "deprecated"
    const val CHANGELOG_DEV = "dev"
}
