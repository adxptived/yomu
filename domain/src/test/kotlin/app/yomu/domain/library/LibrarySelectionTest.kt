package app.yomu.domain.library

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** One selection for local books and online titles; the same number in the two tables is two different items. */
class LibrarySelectionTest {
    private val book7 = LibraryItemKey.LocalBook(7)
    private val entry7 = LibraryItemKey.OnlineEntry(7)
    private val book8 = LibraryItemKey.LocalBook(8)
    private val book9 = LibraryItemKey.LocalBook(9)

    @Test
    fun `the same numeric id in the two tables are two different keys`() {
        val selection = LibrarySelection().toggle(book7).toggle(entry7)
        assertEquals(2, selection.count)
        assertTrue(selection.contains(book7))
        assertTrue(selection.contains(entry7))
    }

    @Test
    fun `toggle adds and removes one item`() {
        val one = LibrarySelection().toggle(book7)
        assertTrue(one.contains(book7))
        val none = one.toggle(book7)
        assertFalse(none.contains(book7))
        assertFalse(none.isActive)
    }

    @Test
    fun `the first selection becomes the range anchor`() {
        val selection = LibrarySelection().toggle(book7).toggle(book8)
        assertEquals(book7, selection.anchor)
    }

    @Test
    fun `a range selects everything between the anchor and the target in list order`() {
        val visible = listOf(book7, book8, book9)
        val selection = LibrarySelection().toggle(book7).rangeTo(book9, visible)
        assertEquals(setOf(book7, book8, book9), selection.keys)
    }

    @Test
    fun `a range works backwards as well`() {
        val visible = listOf(book7, book8, book9)
        val selection = LibrarySelection().toggle(book9).rangeTo(book7, visible)
        assertEquals(setOf(book7, book8, book9), selection.keys)
    }

    @Test
    fun `a range without an anchor selects just the target`() {
        val selection = LibrarySelection().rangeTo(book8, listOf(book7, book8, book9))
        assertEquals(setOf(book8), selection.keys)
    }

    @Test
    fun `a target that is not visible falls back to selecting it alone`() {
        val selection = LibrarySelection().toggle(book7).rangeTo(book9, listOf(book7, book8))
        assertEquals(setOf(book7, book9), selection.keys)
    }

    @Test
    fun `select all replaces the set and records the query`() {
        val all = setOf(book7, book8, book9)
        val selection = LibrarySelection().replaceAll(all, snapshot = "sort=title")
        assertEquals(all, selection.keys)
        assertEquals("sort=title", selection.querySnapshot)
        assertNull(selection.anchor)
    }

    @Test
    fun `a refresh of the same query keeps the selection`() {
        val selection = LibrarySelection().replaceAll(setOf(book7), snapshot = "q")
        assertEquals(setOf(book7), selection.retainOnRefresh().keys)
    }

    @Test
    fun `a new query keeps only the intersection`() {
        val selection = LibrarySelection().replaceAll(setOf(book7, book8), snapshot = "q1")
        val narrowed = selection.intersectWith(setOf(book8, book9))
        assertEquals(setOf(book8), narrowed.keys)
        assertNull(narrowed.querySnapshot)
    }

    @Test
    fun `changing the sort keeps the keys but drops the range anchor`() {
        val selection = LibrarySelection().toggle(book7).toggle(book8)
        assertNotNull(selection.anchor)
        val sorted = selection.onSortChanged()
        assertEquals(selection.keys, sorted.keys)
        assertNull(sorted.anchor)
    }

    @Test
    fun `inverting flips only the visible items`() {
        val selection = LibrarySelection().toggle(book7)
        val flipped = selection.invert(listOf(book7, book8))
        assertEquals(setOf(book8), flipped.keys)
    }

    @Test
    fun `deleted keys are dropped without touching the rest`() {
        val selection = LibrarySelection().toggle(book7).toggle(book8)
        val kept = selection.retainExisting(setOf(book7))
        assertEquals(setOf(book7), kept.keys)
    }

    @Test
    fun `clearing a selection leaves nothing behind`() {
        val cleared = LibrarySelection().replaceAll(setOf(book7, book8), "q").clear()
        assertTrue(cleared.keys.isEmpty())
        assertNull(cleared.anchor)
        assertNull(cleared.querySnapshot)
        assertFalse(cleared.isActive)
    }

    @Test
    fun `a source result has its own identity before it is added`() {
        val a = LibraryItemKey.SourceResult(sourceId = 1, url = "/manga/x")
        val b = LibraryItemKey.SourceResult(sourceId = 2, url = "/manga/x")
        val selection = LibrarySelection().toggle(a).toggle(b)
        assertEquals(2, selection.count)
    }
}
