package app.yomu.domain.reader

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** M03a: the window of online chapters the reader holds, and the flat page layout over it. */
class ReaderWindowTest {

    private val order = listOf(10L, 20L, 30L, 40L, 50L)

    @Test
    fun `a window holds the current chapter and its neighbours`() {
        val w = ReaderWindow.open(entryId = 7, order = order, chapterId = 30)!!
        assertEquals(listOf(20L, 30L, 40L), w.ids)
        assertEquals(30L, w.currentId)
        assertEquals(20L, w.previousId)
        assertEquals(40L, w.nextId)
        assertTrue(w.hasPrevious)
        assertTrue(w.hasNext)
    }

    @Test
    fun `the window does not grow past the first and the last chapter`() {
        val first = ReaderWindow.open(1, order, 10)!!
        assertEquals(listOf(10L, 20L), first.ids)
        assertEquals(null, first.previousId)
        assertFalse(first.hasPrevious)

        val last = ReaderWindow.open(1, order, 50)!!
        assertEquals(listOf(40L, 50L), last.ids)
        assertEquals(null, last.nextId)
        assertFalse(last.hasNext)
    }

    @Test
    fun `shifting forward and back moves the window and stops at the edges`() {
        val start = ReaderWindow.open(1, order, 30)!!
        assertEquals(listOf(30L, 40L, 50L), start.shift(1).ids)
        assertEquals(listOf(10L, 20L), start.shift(-1).shift(-1).ids)
        // Out of range requests clamp instead of leaving the reading order.
        assertEquals(50L, start.shift(99).currentId)
        assertEquals(10L, start.shift(-99).currentId)
    }

    @Test
    fun `a change of scanlation group keeps the caller order`() {
        // The same chapters after switching the release read: the ids are the caller's, the window follows them.
        val switched = listOf(11L, 21L, 31L, 41L, 51L)
        val before = ReaderWindow.open(1, order, 20)!!
        val after = before.moveTo(21L)
        assertNull(after)

        val window = ReaderWindow.open(1, switched, 21)!!
        assertEquals(listOf(11L, 21L, 31L), window.ids)
        assertEquals(31L, window.nextId)
        assertFalse(window.contains(20L))
    }

    @Test
    fun `a chapter missing from the reading order opens no window`() {
        assertNull(ReaderWindow.open(1, order, 99))
        assertNull(ReaderWindow.open(1, emptyList(), 10))
        assertFalse(ReaderWindow.open(1, order, 30)!!.contains(10L))
    }

    @Test
    fun `flat pages map to a chapter and a physical page`() {
        val pages = ReaderWindowPages.of(listOf(20L to 3, 30L to 2, 40L to 4))
        assertEquals(9, pages.total)
        assertEquals(ReaderAnchor(20, 0), pages.anchorOf(0))
        assertEquals(ReaderAnchor(20, 2), pages.anchorOf(2))
        assertEquals(ReaderAnchor(30, 0), pages.anchorOf(3))
        assertEquals(ReaderAnchor(40, 3), pages.anchorOf(8))
        assertEquals(3, pages.startOf(30))
        assertEquals(2, pages.endBefore(30))
        assertEquals(null, pages.endBefore(20))
        assertEquals(30L, pages.chapterAt(4))
    }

    @Test
    fun `locating an anchor rejects a page outside its chapter`() {
        val pages = ReaderWindowPages.of(listOf(20L to 3, 30L to 2))
        assertEquals(4, pages.locate(ReaderAnchor(30, 1)))
        assertNull(pages.locate(ReaderAnchor(30, 2)))
        assertNull(pages.locate(ReaderAnchor(99, 0)))
    }

    @Test
    fun `a missing page falls back to the nearest loaded one`() {
        val pages = ReaderWindowPages.of(listOf(20L to 3, 30L to 2))
        assertEquals(ReaderAnchor(20, 0), pages.anchorOf(-5))
        assertEquals(ReaderAnchor(30, 1), pages.anchorOf(99))
        assertNull(ReaderWindowPages.of(emptyList()).sliceAt(0))
    }

    @Test
    fun `chapters without pages are not part of the layout`() {
        val pages = ReaderWindowPages.of(listOf(20L to 0, 30L to 2))
        assertEquals(2, pages.total)
        assertEquals(listOf(30L), pages.all.map { it.chapterId })
        assertEquals(0, pages.startOf(30))
        assertNull(pages.startOf(20))
    }
}
