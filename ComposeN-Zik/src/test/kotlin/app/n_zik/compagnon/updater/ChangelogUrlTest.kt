package app.n_zik.compagnon.updater

import app.n_zik.compagnon.updater.models.GithubRelease
import app.n_zik.compagnon.updater.models.UpdaterConstants
import app.n_zik.compagnon.updater.services.Updater
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Pins the changelog URL derivation (spec `spec-updater`, AD-3): the phone's release-body regex
 * (`CHANGELOGS_PATH/<code>.txt`) kept as-is, `dev.txt` for the dev tags, `null` when the body links
 * no changelog — the raw URL is always on the `main` branch of the desktop repo.
 */
class ChangelogUrlTest {

    private val expectedBase = UpdaterConstants.CHANGELOGS_URL

    @Test
    fun `the body regex extracts the linked version code`() {
        val body = "## Release notes\n\nFull changelog: [the 2 changes](Updater/changelogs/2.txt)\n"
        assertEquals("2", Updater.changelogVersionCodeOf(body))
    }

    @Test
    fun `the body regex takes the first link`() {
        val body = "See [Updater/changelogs/3.txt](Updater/changelogs/3.txt) then [Updater/changelogs/2.txt](Updater/changelogs/2.txt)\n"
        assertEquals("3", Updater.changelogVersionCodeOf(body))
    }

    @Test
    fun `a body without a changelog link gives null`() {
        assertNull(Updater.changelogVersionCodeOf("no link at all"))
        assertNull(Updater.changelogVersionCodeOf(""))
        // The path must be the frozen changelog path — a different path is not a changelog link
        assertNull(Updater.changelogVersionCodeOf("see docs/other/2.txt"))
    }

    @Test
    fun `a dev release always uses dev txt`() {
        val dev = release("v0.0.2-dev-20260901", "links Updater/changelogs/99.txt")
        assertEquals("$expectedBase/dev.txt", Updater.changelogUrlOf(dev))
    }

    @Test
    fun `a stable or beta release uses the linked code txt`() {
        val stable = release("v0.0.2", "links Updater/changelogs/2.txt")
        assertEquals("$expectedBase/2.txt", Updater.changelogUrlOf(stable))
        val beta = release("v0.0.2-beta", "links Updater/changelogs/3.txt")
        assertEquals("$expectedBase/3.txt", Updater.changelogUrlOf(beta))
    }

    @Test
    fun `a release without a linked changelog has no url`() {
        assertNull(Updater.changelogUrlOf(release("v0.0.2", "no link")))
        assertNull(Updater.changelogUrlOf(release("v0.0.2-beta", "")))
    }

    @Test
    fun `the running version changelog url is its code txt or dev txt`() {
        assertEquals("$expectedBase/5.txt", Updater.currentChangelogUrl(5, isDev = false))
        assertEquals("$expectedBase/dev.txt", Updater.currentChangelogUrl(5, isDev = true))
    }

    private fun release(tagName: String, body: String): GithubRelease = GithubRelease(
        id = 1u,
        tagName = tagName,
        name = tagName,
        body = body,
        builds = emptyList(),
    )
}
