package app.n_zik.compagnon.components.ui.screens.settings

import app.n_zik.compagnon.updater.models.UpdaterConstants
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * The GitHub release URL of the shown version ([githubReleaseUrl] — the phone's tag logic,
 * ported with its one fix): a channel-suffixed version links to its OWN release tag (the
 * phone's `substringBefore('-')` would have dropped the dev suffix and pointed at the stable
 * tag), a plain version links to its base tag.
 */
class GithubReleaseUrlTest {

    @Test
    fun `a plain version links to its own base tag`() {
        assertEquals(
            "${UpdaterConstants.REPO_URL}/releases/tag/v0.0.1",
            githubReleaseUrl("", "v0.0.1"),
        )
    }

    @Test
    fun `a dev-suffixed version links to its own dev tag`() {
        assertEquals(
            "${UpdaterConstants.REPO_URL}/releases/tag/v0.0.2-dev-20261008",
            githubReleaseUrl("-dev-20261008", "v0.0.2-dev-20261008"),
        )
    }

    @Test
    fun `a beta-suffixed version links to its own beta tag`() {
        assertEquals(
            "${UpdaterConstants.REPO_URL}/releases/tag/v0.0.2-beta",
            githubReleaseUrl("-beta", "v0.0.2-beta"),
        )
    }
}
