package app.n_zik.compagnon.components.ui.screens.settings

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The pure visibility of the User-Interface tab's conditional entries (spec
 * `spec-fill-pc-ui-gaps-settings` §B): the 6 swipe-action sub-entries (the phone's
 * `UiSettings.kt` 1435-1556) follow "Swipe for action" (the PC key
 * `isSwipeToActionEnabled`) live — shown only while the toggle is on.
 */
class UiVisibleEntriesTest {

    @Test
    fun `the six swipe sub entries are hidden while swipe for action is off`() {
        assertFalse(
            uiVisibleEntries(UiVisibilityInput(isSwipeToActionEnabled = false)).swipeSubEntries,
            "the sub-entries hide with the toggle off (the phone's condition, statically false on the phone's default)",
        )
    }

    @Test
    fun `the six swipe sub entries are shown while swipe for action is on`() {
        assertTrue(
            uiVisibleEntries(UiVisibilityInput(isSwipeToActionEnabled = true)).swipeSubEntries,
            "a toggle flip shows the sub-entries live (dynamic, never static)",
        )
    }
}
