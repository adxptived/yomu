package app.yomu.feature.reader.manga

import app.yomu.domain.reader.MangaPaging
import app.yomu.domain.reader.PageSplit
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** M02/M04: a page turn follows the current layout — single pages, split halves and spreads. */
class MangaSlotMoveTest {

    private fun pages(count: Int, split: PageSplit, wide: (Int) -> Boolean = { true }) =
        Layout(count, hasPrev = false, spreads = null, virtual = MangaPaging.virtualPages(count, split, wide), split = split)

    @Test
    fun `single pages turn one slot and stop at the chapter edge`() {
        val layout = Layout(5, hasPrev = false, spreads = null)
        assertEquals(SlotMove.To(3), slotMoveFor(3, page = 2, currentSlot = 2, layout = layout))
        assertEquals(SlotMove.To(1), slotMoveFor(1, page = 2, currentSlot = 2, layout = layout))
        assertEquals(SlotMove.ChapterEdge, slotMoveFor(-1, page = 0, currentSlot = 0, layout = layout))
    }

    @Test
    fun `a previous chapter page is the step before the first page`() {
        val layout = Layout(5, hasPrev = true, spreads = null)
        // Slot 0 is the previous-chapter page, page 0 is slot 1.
        assertEquals(SlotMove.To(0), slotMoveFor(-1, page = 0, currentSlot = 1, layout = layout))
        assertEquals(SlotMove.To(0), slotMoveFor(-1, page = 0, currentSlot = 0, layout = layout))
    }

    @Test
    fun `a split page turns through both halves before the next physical page`() {
        val layout = pages(3, PageSplit.LEFT_TO_RIGHT)
        assertEquals(SlotMove.To(1), slotMoveFor(1, page = 0, currentSlot = 0, layout = layout))
        assertEquals(SlotMove.To(2), slotMoveFor(1, page = 0, currentSlot = 1, layout = layout))
        assertEquals(SlotMove.To(1), slotMoveFor(-1, page = 0, currentSlot = 2, layout = layout))
        assertEquals(SlotMove.To(0), slotMoveFor(-1, page = 0, currentSlot = 1, layout = layout))
    }

    @Test
    fun `the last half leads to the end page, not to the next chapter`() {
        val layout = pages(3, PageSplit.LEFT_TO_RIGHT)
        assertEquals(SlotMove.To(5), slotMoveFor(3, page = 2, currentSlot = 4, layout = layout))
        assertEquals(SlotMove.To(6), slotMoveFor(3, page = 2, currentSlot = 5, layout = layout))
        // The end page itself is a no-op, not a chapter edge.
        assertEquals(SlotMove.To(6), slotMoveFor(3, page = 2, currentSlot = 6, layout = layout))
    }

    @Test
    fun `right to left starts with the second half`() {
        val layout = pages(3, PageSplit.RIGHT_TO_LEFT)
        assertEquals(2, layout.slotOf(1))
        assertEquals(SlotMove.To(1), slotMoveFor(1, page = 0, currentSlot = 0, layout = layout))
        assertEquals(SlotMove.To(2), slotMoveFor(1, page = 0, currentSlot = 1, layout = layout))
        assertEquals(SlotMove.To(5), slotMoveFor(3, page = 2, currentSlot = 4, layout = layout))
    }

    @Test
    fun `a portrait page in split mode is one slot and is not cut`() {
        val layout = pages(3, PageSplit.LEFT_TO_RIGHT) { it == 1 }
        // [0], [1a], [1b], [2] + end
        assertEquals(1, layout.slotOf(1))
        assertEquals(3, layout.slotOf(2))
        assertEquals(SlotMove.To(1), slotMoveFor(1, page = 0, currentSlot = 0, layout = layout))
        assertEquals(SlotMove.To(3), slotMoveFor(2, page = 1, currentSlot = 2, layout = layout))
        assertNull(layout.halfOf(0))
        assertNull(layout.halfOf(3))
        assertEquals(1, layout.halfOf(1)?.physical)
    }

    @Test
    fun `a spread turns once per pair`() {
        val spreads = MangaPaging.spreads(5, coverAlone = true)
        val layout = Layout(5, hasPrev = false, spreads = spreads)
        // [0], [1,2], [3,4] + end
        assertEquals(SlotMove.To(2), slotMoveFor(2, page = 1, currentSlot = 1, layout = layout))
        assertEquals(SlotMove.To(1), slotMoveFor(0, page = 1, currentSlot = 2, layout = layout))
        assertEquals(SlotMove.To(3), slotMoveFor(5, page = 4, currentSlot = 3, layout = layout))
    }

    @Test
    fun `a long jump does not animate through every page`() {
        val layout = Layout(10, hasPrev = false, spreads = null)
        assertEquals(SlotMove.To(7, animate = false), slotMoveFor(7, page = 0, currentSlot = 0, layout = layout))
        assertEquals(SlotMove.To(1, animate = true), slotMoveFor(1, page = 0, currentSlot = 0, layout = layout))
    }

    @Test
    fun `a chapter boundary inside the window is a page turn of its own`() {
        // Five flat pages, a chapter change after page 2: 0 1 2 |break| 3 4 |end|
        val layout = Layout(5, hasPrev = false, spreads = null, breaks = mapOf(2 to "Chapter 2"))
        assertEquals(7, layout.total)
        assertEquals(2, layout.slotOf(2))
        assertEquals(4, layout.slotOf(3))
        assertEquals(5, layout.slotOf(4))
        assertEquals(2, layout.breakOf(3)?.after)
        assertNull(layout.pageOf(3))
        assertEquals(3, layout.pageOf(4))

        // Turning from the last page of the first chapter shows the boundary, not the next page at once.
        assertEquals(SlotMove.To(3), slotMoveFor(3, page = 2, currentSlot = 2, layout = layout))
        // Turning again enters the next chapter without a reload.
        assertEquals(SlotMove.To(4), slotMoveFor(3, page = 2, currentSlot = 3, layout = layout))
        // Backwards lands on the boundary and then on the last page of the previous chapter.
        assertEquals(SlotMove.To(3), slotMoveFor(2, page = 3, currentSlot = 4, layout = layout))
        assertEquals(SlotMove.To(2), slotMoveFor(2, page = 3, currentSlot = 3, layout = layout))
    }

    @Test
    fun `a chapter boundary follows the last half of a split page`() {
        val virtual = MangaPaging.virtualPages(3, PageSplit.LEFT_TO_RIGHT) { true }
        val layout = Layout(3, hasPrev = false, spreads = null, virtual = virtual, split = PageSplit.LEFT_TO_RIGHT, breaks = mapOf(2 to "Chapter 2"))
        // Six half-slots, then the boundary and the end page.
        assertEquals(8, layout.total)
        assertEquals(4, layout.slotOf(2))
        assertEquals(2, layout.pageOf(5))
        assertEquals(2, layout.breakOf(6)?.after)
        assertNull(layout.pageOf(6))
    }

    @Test
    fun `spreads never mix two chapters`() {
        // Chapter one has four pages, chapter two three: groups are [0,1] [2,3] |break| [4,5] [6] |end|
        val spreads = listOf(intArrayOf(0, 1), intArrayOf(2, 3), intArrayOf(4, 5), intArrayOf(6))
        val layout = Layout(7, hasPrev = false, spreads = spreads, breaks = mapOf(3 to "Chapter 2"))
        assertEquals(6, layout.total)
        assertEquals(1, layout.slotOf(2))
        assertEquals(3, layout.slotOf(4))
        assertEquals(3, layout.breakOf(2)?.after)
        assertEquals(4, layout.pageOf(3))
    }
}
