package app.n_zik.compagnon

import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * The frozen channel badge literals of `values/strings.xml` (the English single source of truth —
 * the `values-*` locale copies are Crowdin's, never touched by hand; they stay mixed-case until
 * the next resync, which is why the badge UI applies the case at render, spec
 * `spec-channel-version-naming` loop 2 — VG2-8): a re-edit of the source would change the badge
 * in a green build. The idiom mirrors [PackageNameIdentityTest] (reading a repo file from the
 * tests — the working dir is the `app/` module dir).
 */
class ChannelBadgeStringsTest {

    private fun string(name: String): String =
        Regex("""<string name="$name">(.*?)</string>""")
            .find(File("src/main/composeResources/values/strings.xml").readText())?.groupValues?.get(1)
            ?: error("$name is not defined in values/strings.xml")

    @Test
    fun `the channel badge literals are uppercase (the phone's build-type convention)`() {
        assertEquals("BETA", string("beta_title"))
        assertEquals("DEV", string("dev_title"))
        assertEquals("GIT", string("git_title"))
        assertEquals("DEBUG", string("debug_title"))
        assertEquals("STABLE", string("stable_title"))
    }
}
