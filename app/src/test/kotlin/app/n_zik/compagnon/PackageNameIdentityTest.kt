package app.n_zik.compagnon

import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

/**
 * The installer name, install directory, Start-menu entry and Control-Panel (uninstall) entry all
 * come from the version catalog's `nzikPackageName`; the app's own identity is `AppInfo.NAME`
 * (window title, `%APPDATA%` data directory, Credential Manager target). They must stay aligned — a
 * silent divergence would ship an installation named differently from the running app.
 */
class PackageNameIdentityTest {

    @Test
    fun `catalog package name matches the app identity`() {
        val toml = File("../gradle/libs.versions.toml").readText()
        val match = Regex("""nzikPackageName\s*=\s*"(.*)\"""").find(toml)
        assertNotNull(match, "nzikPackageName missing from gradle/libs.versions.toml")
        assertEquals(AppInfo.NAME, match!!.groupValues[1])
    }
}
