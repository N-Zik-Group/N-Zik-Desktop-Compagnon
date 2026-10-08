package app.n_zik.compagnon.updater

import app.n_zik.compagnon.generated.AppVersion
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test

/**
 * The generated [AppVersion] contract (spec `spec-updater`, AD-2): the `generateAppVersion`
 * task writes the in-app identity and the build exposes the same effective values as system
 * properties (`channel`, `appVersion.*`) — this test fails if the generated file drifts from
 * the build that produced it (a broken generator would ship the wrong version, channel or
 * updater gate without any other test noticing).
 */
class AppVersionContractTest {

    @Test
    fun `the generated identity matches the build contract`() {
        assertEquals(System.getProperty("channel"), AppVersion.channel)
        assertEquals(System.getProperty("appVersion.versionName"), AppVersion.versionName)
        assertEquals(System.getProperty("appVersion.versionCode").toInt(), AppVersion.versionCode)
        assertEquals(System.getProperty("appVersion.updaterEnabled").toBoolean(), AppVersion.updaterEnabled)
    }

    @Test
    fun `the default channel is debug with the updater off and the catalog version`() {
        // The default (absent -Pchannel) is the debug iteration build: the catalog base version,
        // no suffix, the updater off by build
        assumeTrue("debug" == System.getProperty("channel"), "the test ran with an explicit channel")
        assertEquals("debug", AppVersion.channel)
        assertFalse(AppVersion.updaterEnabled)
        assertEquals(System.getProperty("appVersion.versionCode").toInt(), AppVersion.versionCode)
    }
}
