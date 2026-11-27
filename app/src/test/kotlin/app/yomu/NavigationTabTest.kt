package app.yomu

import app.yomu.core.ui.components.NavTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The journal kept the internal key [NavTab.HISTORY] (2.20.0): tab orders and hidden tabs saved before the rename must
 * keep working, and Settings can never be hidden.
 */
class NavigationTabTest {
    @Test
    fun storedOrderUsesTheJournalKey() {
        val order = NavTab.ordered(listOf("HISTORY", "LIBRARY", "SETTINGS"))
        assertEquals(listOf(NavTab.HISTORY, NavTab.LIBRARY, NavTab.SETTINGS), order.take(3))
        assertEquals(NavTab.entries.size, order.size)
    }

    @Test
    fun aHiddenJournalStaysHidden() {
        val visible = NavTab.visible(NavTab.entries.map { it.name }, setOf("HISTORY", "LIBRARY"))
        assertFalse(NavTab.HISTORY in visible)
        assertFalse(NavTab.LIBRARY in visible)
        assertTrue(NavTab.SETTINGS in visible)
    }

    @Test
    fun settingsCannotBeHidden() {
        val visible = NavTab.visible(emptyList(), NavTab.entries.map { it.name }.toSet())
        assertEquals(listOf(NavTab.SETTINGS), visible)
    }

    @Test
    fun unknownStoredNamesAreDropped() {
        val order = NavTab.ordered(listOf("PROFILE", "HISTORY"))
        assertEquals(NavTab.HISTORY, order.first())
        assertEquals(NavTab.entries.size, order.size)
    }
}
