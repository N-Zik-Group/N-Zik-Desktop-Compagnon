package app.n_zik.compagnon.components

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class MenuSheetSnapTest {

    @Test
    fun `a drag released above the threshold snaps the sheet to the full height`() {
        assertEquals(1f, menuSheetSnapFraction(MENU_SHEET_FULL_THRESHOLD + 0.01f))
        assertEquals(1f, menuSheetSnapFraction(1f))
    }

    @Test
    fun `a drag released below the threshold closes the sheet`() {
        assertNull(menuSheetSnapFraction(MENU_SHEET_CLOSE_THRESHOLD - 0.01f))
        assertNull(menuSheetSnapFraction(0.15f))
    }

    @Test
    fun `a release in between snaps back to the partial half`() {
        assertEquals(MENU_SHEET_PARTIAL_FRACTION, menuSheetSnapFraction(MENU_SHEET_PARTIAL_FRACTION))
        assertEquals(MENU_SHEET_PARTIAL_FRACTION, menuSheetSnapFraction(0.7f))
        assertEquals(MENU_SHEET_PARTIAL_FRACTION, menuSheetSnapFraction(MENU_SHEET_FULL_THRESHOLD))
        assertEquals(MENU_SHEET_PARTIAL_FRACTION, menuSheetSnapFraction(MENU_SHEET_CLOSE_THRESHOLD))
    }
}
