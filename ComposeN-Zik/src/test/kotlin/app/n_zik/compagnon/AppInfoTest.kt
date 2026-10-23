package app.n_zik.compagnon

import app.n_zik.compagnon.generated.AppVersion
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AppInfoTest {

    @Test
    fun `app name matches the repository product name`() {
        assertEquals("N-Zik Desktop Compagnon", AppInfo.NAME)
    }

    @Test
    fun `the product name keeps the install identity per channel`() {
        // AD-8: the INSTALL identity (install dir, window title, Linux launcher, Flatpak exec)
        // is unchanged by the 2026-10-16 storage regrouping
        assertEquals("N-Zik Desktop Compagnon", AppInfo.productName("stable"))
        assertEquals("N-Zik Desktop Compagnon", AppInfo.productName("debug"))
        assertEquals("N-Zik Desktop Compagnon", AppInfo.productName("git"))
        assertEquals("N-Zik Desktop Compagnon BETA", AppInfo.productName("beta"))
        assertEquals("N-Zik Desktop Compagnon DEV", AppInfo.productName("dev"))
    }

    @Test
    fun `the data name is the storage name per channel (stable + beta share the base, dev, debug, git are isolated)`() {
        // The 2026-10-16 storage regrouping: the app data dir + the secret store target follow
        // THIS mapping (not the install identity): stable and beta share the base (the beta

        // tests against the stable user's real data), dev / debug / git are isolated. The
        // mapping is the storage contract — a drift forces re-pairings or strands data
        assertEquals("N-Zik Desktop Compagnon", AppInfo.dataName("stable"))
        assertEquals("N-Zik Desktop Compagnon", AppInfo.dataName("beta"))
        assertEquals("N-Zik Desktop Compagnon DEV", AppInfo.dataName("dev"))
        assertEquals("N-Zik Desktop Compagnon DEBUG", AppInfo.dataName("debug"))
        assertEquals("N-Zik Desktop Compagnon GIT", AppInfo.dataName("git"))
    }

    @Test
    fun `the build channel dataName property mirrors AppInfo dataName of the build channel`() {
        // The cross pin Gradle <-> app (spec `spec-channel-storage-portable-targz`): the NSIS
        // DATA_DIR_NAME / CRED_TARGET defines are the build mirror of `AppInfo.dataName`

        // (`channelDataName` in build.gradle.kts, exposed to the JVM tests as `channel.dataName`):
        // the installer's cleanup target and the app's own storage must agree on every channel
        val prop = System.getProperty("channel.dataName")
            ?: error("the build must expose the channel.dataName system property")
        assertEquals(AppInfo.dataName(AppVersion.channel), prop)
    }
}
