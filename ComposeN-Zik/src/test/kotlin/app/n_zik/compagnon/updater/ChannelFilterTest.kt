package app.n_zik.compagnon.updater

import app.n_zik.compagnon.updater.models.GithubRelease
import app.n_zik.compagnon.updater.services.Updater
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Pins the same-channel eligibility (spec `spec-updater`, AD-1 / AD-3, port of the phone's channel
 * filter in `findBestRelease` minus its runtime beta toggle): a check never crosses channels —
 * stable sees only the plain tags, beta only `-beta`, dev only `-dev-*`; the source-build channels
 * (`debug`, `-git`) never check, so every tag is ineligible for them.
 */
class ChannelFilterTest {

    @Test
    fun `stable only sees the plain tags`() {
        assertTrue(Updater.isSameChannel("v0.0.2", "stable"))
        assertFalse(Updater.isSameChannel("v0.0.2-beta", "stable"))
        assertFalse(Updater.isSameChannel("v0.0.2-dev-20260901", "stable"))
        assertFalse(Updater.isSameChannel("v0.0.2-git", "stable"))
    }

    @Test
    fun `beta only sees the beta tags`() {
        assertTrue(Updater.isSameChannel("v0.0.2-beta", "beta"))
        assertFalse(Updater.isSameChannel("v0.0.2", "beta"))
        assertFalse(Updater.isSameChannel("v0.0.2-dev-20260901", "beta"))
        assertFalse(Updater.isSameChannel("v0.0.2-git", "beta"))
    }

    @Test
    fun `dev only sees the dated dev tags`() {
        assertTrue(Updater.isSameChannel("v0.0.2-dev-20260901", "dev"))
        assertFalse(Updater.isSameChannel("v0.0.2", "dev"))
        assertFalse(Updater.isSameChannel("v0.0.2-beta", "dev"))
        assertFalse(Updater.isSameChannel("v0.0.2-git", "dev"))
    }

    @Test
    fun `the source channels never see any tag`() {
        val tags = listOf("v0.0.1", "v0.0.1-beta", "v0.0.1-dev-20260901", "v0.0.1-git")
        for (channel in listOf("debug", "git")) {
            for (tag in tags) {
                assertFalse(
                    Updater.isSameChannel(tag, channel),
                    "channel $channel must never accept $tag (updater off by build)",
                )
            }
        }
    }

    @Test
    fun `the vlc tarball staging carrier is never same-channel`() {
        // The `vlc-*-tarball` namespace (spec `spec-vlc-tarball-ci` — the prerelease carriers of the
        // pinned Linux VLC tarball) is a staging namespace, not an app release namespace: it must
        // be excluded from every checking channel EXPLICITLY. Today that exclusion is an accident
        // of the non-empty-suffix rule (the tag's "v" prefix is not a version prefix, so the
        // extracted "suffix" is the tag's second dash segment) — this test pins it alongside the
        // `v0.0.2-git` precedent above, so a suffix-extraction change cannot re-include it.
        for (channel in listOf("stable", "beta", "dev")) {
            assertFalse(
                Updater.isSameChannel("vlc-3.0.24-tarball", channel),
                "channel $channel must never accept the tarball carrier vlc-3.0.24-tarball",
            )
        }
    }

    @Test
    fun `findBestRelease returns null for a source channel`() {
        val releases = listOf(release("v0.0.2"), release("v0.0.2-beta"), release("v0.0.2-dev-20260901"))
        assertNull(Updater.findBestRelease(releases, "debug"))
        assertNull(Updater.findBestRelease(releases, "git"))
    }

    @Test
    fun `findBestRelease returns null when no same-channel release exists`() {
        val releases = listOf(release("v0.0.2-beta"), release("v0.0.2-dev-20260901"))
        assertNull(Updater.findBestRelease(releases, "stable"))
    }

    private fun release(tagName: String): GithubRelease = GithubRelease(
        id = 1u,
        tagName = tagName,
        name = tagName,
        body = "",
        builds = emptyList(),
    )
}
