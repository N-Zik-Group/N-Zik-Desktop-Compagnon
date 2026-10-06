package app.n_zik.compagnon

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * The upgrade UUID is the frozen identity of the installation — the foundation of the deferred
 * in-app updater: every future installer must recognize this installation and upgrade it in place.
 * The build exposes it (`systemProperty` in `app/build.gradle.kts`) so an accidental edit of the
 * `windows { }` block turns a "green build, wrong installer" into a red build.
 */
class UpgradeIdentityTest {

    @Test
    fun `upgrade uuid is the frozen installation identity`() {
        assertEquals("da55e7af-5c5a-4d1e-841e-bb2646046bc2", System.getProperty("install.upgradeUuid"))
    }

    @Test
    fun `install is per-user, so the future updater needs no elevation`() {
        assertEquals("true", System.getProperty("install.perUser"))
    }
}
