package app.n_zik.compagnon.updater.ui

import app.n_zik.compagnon.generated.resources.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Pins [channelTitleRes] — the channel → `*_title` string mapping of [channelLabel] (spec
 * `spec-updater-parity-fixes`, matrix rows LABEL_SCREEN / LABEL_DIALOG): the single shared
 * source both the update page and the update dialog render — the 5-branch superset
 * (beta / stable / dev / git / debug, incl. the `debug` the dialog's former private copy
 * lacked), `null` for an unknown channel (no prefix, not a blank line).
 */
class ChannelLabelTest {

    @Test
    fun `every channel maps to its own title string`() {
        assertEquals(Res.string.beta_title, channelTitleRes("beta"))
        assertEquals(Res.string.stable_title, channelTitleRes("stable"))
        assertEquals(Res.string.dev_title, channelTitleRes("dev"))
        assertEquals(Res.string.git_title, channelTitleRes("git"))
        assertEquals(Res.string.debug_title, channelTitleRes("debug"))
    }

    @Test
    fun `an unknown channel maps to no title`() {
        assertNull(channelTitleRes("release"))
        assertNull(channelTitleRes(""))
        // The mapping is case-sensitive: the build channels are lowercase by construction
        // (AppVersion.channel)
        assertNull(channelTitleRes("BETA"))
    }

    @Test
    fun `the label is the uppercased title with a trailing space, locale-independently`() {
        assertEquals("BETA ", channelLabelOf("beta"))
        assertEquals("STABLE ", channelLabelOf("stable"))
        assertEquals("DEV ", channelLabelOf("dev"))
        assertEquals("DEBUG ", channelLabelOf("debug"))
        // Locale.ROOT: a default-locale uppercase() would render the dotted capital I
        // ("GİT") under a Turkish JVM default locale
        assertEquals("GIT ", channelLabelOf("git"))
    }
}
