package app.n_zik.compagnon

import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Pins uppercase source badge text; locale copies are uppercased by the UI. */
class ChannelBadgeStringsTest {

    private fun string(name: String): String =
        Regex("""<string name="$name">(.*?)</string>""")
            .find(File("src/main/localization/source/strings_maintenance.xml").readText())?.groupValues?.get(1)
            ?: error("$name is not defined in the maintenance source catalog")

    @Test
    fun `the channel badge literals are uppercase (the phone's build-type convention)`() {
        assertEquals("BETA", string("updates_build_beta_label"))
        assertEquals("DEV", string("updates_build_dev_label"))
        assertEquals("GIT", string("git_title"))
        assertEquals("DEBUG", string("updates_build_debug_label"))
        assertEquals("STABLE", string("stable_title"))
    }
}
