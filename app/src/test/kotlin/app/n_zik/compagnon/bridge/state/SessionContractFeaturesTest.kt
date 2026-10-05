package app.n_zik.compagnon.bridge.state

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * The contract §5 `features` wire literals: the phone advertises exactly these strings, so a
 * changed literal would silently hide its screen (the PC gates on the feature, never the version).
 */
class SessionContractFeaturesTest {

    @Test
    fun `the sort menu and toolbar features are the phone's wire literals`() {
        assertEquals("library.sortMenu", SessionContract.FEATURE_LIBRARY_SORT_MENU)
        assertEquals("library.toolbar", SessionContract.FEATURE_LIBRARY_TOOLBAR)
    }
}
